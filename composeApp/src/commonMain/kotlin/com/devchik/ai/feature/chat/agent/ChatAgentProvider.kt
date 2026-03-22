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

/**
 * Factory that creates configured Koog [AIAgent] instances for the chat feature.
 *
 * Architecture overview:
 * - Uses DeepSeek API via Koog's [DeepSeekLLMClient].
 * - Agent strategy is a graph: user input → LLM streaming request → either tool execution
 *   or assistant text response, looping back for multi-turn dialogue.
 * - [ChatMemory] feature restores conversation context from [chatHistoryProvider] on agent start.
 *   Note: [RoomChatHistoryProvider.store] is a no-op — persistence is handled incrementally
 *   by [ChatRepositoryImpl] to avoid overwriting system/error messages and
 *   re-inserting tool_call/tool_result entries that break DeepSeek API context.
 * - Callbacks (onToolCallEvent, onAssistantMessage, etc.) bridge agent events to the ViewModel/UI.
 * - [onAssistantMessage] is a **blocking** callback: the agent suspends until the user types
 *   the next message (returned as String), enabling multi-turn conversation within a single
 *   agent.run() invocation.
 * - Token usage is captured from [StreamFrame.End.metaInfo] and forwarded via [onTokenUsage].
 */
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

    /**
     * Creates and returns a new [AIAgent] wired to the given callbacks.
     *
     * @param onToolCallEvent  called when the agent invokes a tool (e.g., ExitTool).
     * @param onErrorEvent     called on agent execution failure.
     * @param onAssistantMessage suspending callback that receives the full assistant response text
     *                           and must return the next user message (blocks agent until user replies).
     * @param onStreamingDelta called for each streaming text chunk from the LLM.
     * @param onTokenUsage     called once per LLM response with input/output/total token counts.
     */
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

        // ExitTool зарегистрирован для валидации графа (Koog требует путь к nodeFinish),
        // но system prompt не упоминает его — LLM не будет вызывать его самостоятельно.
        val toolRegistry = ToolRegistry {
            tool(ExitTool)
        }

        // Agent strategy graph (бесконечный цикл диалога):
        //   nodeStart → mapStringToRequests → applyRequestToSession → nodeStreaming
        //   nodeStreaming → [tool calls] → nodeExecuteTools → [exit?] → nodeFinish (формально)
        //                                                   → [else] → loop back
        //   nodeStreaming → [no tool calls] → extractTextFromResponse → nodeAssistantMessage → loop back
        //
        // На практике ExitTool никогда не вызывается — LLM не знает о нём.
        // Завершение диалога — через отмену Job пользователем (stopGeneration).
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

            // ExitTool → nodeFinish (формальный edge для валидации графа Koog)
            edge(
                nodeExecuteTools forwardTo nodeFinish
                    onCondition { it.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.single().content }
            )

            // Все остальные tool calls возвращаются обратно в LLM
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
                    """.trimIndent()
                )
            },
            model = model,
            maxAgentIterations = 1000,
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            // ChatMemory loads conversation history on agent start via chatHistoryProvider.load().
            // store() is a no-op — see RoomChatHistoryProvider for details.
            install(ChatMemory) {
                chatHistoryProvider = this@ChatAgentProvider.chatHistoryProvider
                windowSize(50)
            }

            handleEvents {
                onToolCallStarting { ctx ->
                    onToolCallEvent("Tool ${ctx.toolName}, args ${ctx.toolArgs}")
                }

                // Token usage is only available in StreamFrame.End (after full response is received)
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
                    if (ctx.throwable is kotlinx.coroutines.CancellationException) return@onAgentExecutionFailed
                    onErrorEvent("${ctx.throwable.message}")
                }

                onAgentCompleted { _ -> }
            }
        }
    }
}
