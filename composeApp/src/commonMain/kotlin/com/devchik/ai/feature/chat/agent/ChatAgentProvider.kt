package com.devchik.ai.feature.chat.agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame

class ChatAgentProvider(
    private val apiKey: String,
    private val chatHistoryProvider: ChatHistoryProvider,
) {
    val title: String = "Koog Chat"
    val description: String = "Привет! Я AI-агент на базе Koog + DeepSeek. Задайте мне вопрос."

    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
    )

    suspend fun provideAgent(
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
        onStreamingDelta: suspend (String) -> Unit,
        onTokenUsage: suspend (TokenUsage) -> Unit = {},
    ): AIAgent<String, String> {
        val llmClient = DeepSeekLLMClient(apiKey)
        val executor = MultiLLMPromptExecutor(llmClient)
        val model = DeepSeekModels.DeepSeekChat

        val toolRegistry = ToolRegistry {
            tool(ExitTool)
        }

        val agentStrategy = strategy<String, String>(title) {
            val nodeStreaming by nodeLLMRequestStreamingAndSendResults<List<Message.Request>>()
            val nodeExecuteTools by nodeExecuteMultipleTools(parallelTools = true)

            val mapStringToRequests by node<String, List<Message.Request>> { input ->
                listOf(Message.User(content = input, metaInfo = RequestMetaInfo.Empty))
            }

            val applyRequestToSession by node<List<Message.Request>, List<Message.Request>> { input ->
                llm.writeSession {
                    appendPrompt {
                        input.filterIsInstance<Message.User>().forEach { user(it.content) }
                        tool {
                            input.filterIsInstance<Message.Tool.Result>().forEach { result(it) }
                        }
                    }
                    input
                }
            }

            val mapToolCallsToRequests by node<List<ReceivedToolResult>, List<Message.Request>> { input ->
                input.map { it.toMessage() }
            }

            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }

            val extractTextFromResponse by node<List<Message.Response>, String> { responses ->
                responses.filterIsInstance<Message.Assistant>().joinToString("") { it.content }
            }

            edge(nodeStart forwardTo mapStringToRequests)
            edge(mapStringToRequests forwardTo applyRequestToSession)
            edge(applyRequestToSession forwardTo nodeStreaming)

            edge(nodeStreaming forwardTo nodeExecuteTools onMultipleToolCalls { true })

            edge(
                nodeExecuteTools forwardTo nodeFinish
                    onCondition { it.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.single().content }
            )

            edge(
                nodeExecuteTools forwardTo mapToolCallsToRequests
                    onCondition { it.singleOrNull()?.tool != ExitTool.name }
            )

            edge(mapToolCallsToRequests forwardTo applyRequestToSession)

            edge(
                nodeStreaming forwardTo extractTextFromResponse
                    onCondition { it.filterIsInstance<Message.Tool.Call>().isEmpty() }
            )

            edge(extractTextFromResponse forwardTo nodeAssistantMessage)
            edge(nodeAssistantMessage forwardTo mapStringToRequests)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("chat") {
                system(
                    """
                    You are a helpful AI assistant. Answer user questions clearly and concisely.
                    Support both Russian and English languages.
                    If the user asks to stop, call the exit tool to finish the conversation politely.
                    """.trimIndent()
                )
            },
            model = model,
            maxAgentIterations = 50,
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = this@ChatAgentProvider.chatHistoryProvider
                windowSize(50)
            }

            handleEvents {
                onToolCallStarting { ctx ->
                    onToolCallEvent("Tool ${ctx.toolName}, args ${ctx.toolArgs}")
                }

                onLLMStreamingFrameReceived { ctx ->
                    when (val frame = ctx.streamFrame) {
                        is StreamFrame.TextDelta -> onStreamingDelta(frame.text)
                        is StreamFrame.End -> {
                            val meta = frame.metaInfo
                            val input = meta.inputTokensCount ?: 0
                            val output = meta.outputTokensCount ?: 0
                            val total = meta.totalTokensCount ?: (input + output)
                            if (input > 0 || output > 0 || total > 0) {
                                onTokenUsage(TokenUsage(input, output, total))
                            }
                        }
                        else -> Unit
                    }
                }

                onAgentExecutionFailed { ctx ->
                    onErrorEvent("${ctx.throwable.message}")
                }

                onAgentCompleted { _ -> }
            }
        }
    }
}
