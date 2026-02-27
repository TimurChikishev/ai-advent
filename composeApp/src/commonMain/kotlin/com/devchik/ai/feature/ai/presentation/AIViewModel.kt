package com.devchik.ai.feature.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.domain.model.AIMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AIUiState(
    val messages: List<AIMessage> = emptyList(),
    val streamingContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AIViewModel(
    private val repository: AIRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = AIMessage(role = AIMessage.Role.User, content = text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                streamingContent = "",
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            repository.sendMessage(_uiState.value.messages)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .onCompletion {
                    val content = _uiState.value.streamingContent
                    if (content.isNotEmpty()) {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + AIMessage(
                                    role = AIMessage.Role.Assistant,
                                    content = content,
                                ),
                                streamingContent = "",
                                isLoading = false,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                .collect { chunk ->
                    _uiState.update { it.copy(streamingContent = it.streamingContent + chunk) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

private val MOCK_MESSAGES = listOf(
    AIMessage(AIMessage.Role.User, "Привет! Расскажи про Kotlin"),
    AIMessage(
        AIMessage.Role.Assistant,
        """# Kotlin

Kotlin — это современный язык программирования от **JetBrains**.

## Основные преимущества

- **Null safety** — защита от NullPointerException
- **Корутины** — удобная асинхронность
- **Extension functions** — расширение существующих классов
- Совместимость с Java

## Пример кода

```kotlin
fun main() {
    val list = listOf(1, 2, 3, 4, 5)
    list.filter { it % 2 == 0 }
        .map { it * it }
        .forEach { println(it) }
}
```

> Kotlin является официальным языком для Android-разработки с 2017 года."""
    ),
    AIMessage(AIMessage.Role.User, "А что такое корутины?"),
    AIMessage(
        AIMessage.Role.Assistant,
        """## Корутины в Kotlin

Корутины — это легковесные потоки для асинхронного программирования.

### Ключевые концепции

1. **suspend** функции — могут приостанавливаться без блокировки потока
2. **CoroutineScope** — область жизни корутин
3. **Dispatchers** — определяют поток выполнения

### Пример

```kotlin
suspend fun fetchData(): String {
    delay(1000)
    return "Данные получены"
}

viewModelScope.launch {
    val data = fetchData()
    _uiState.value = data
}
```

| Dispatcher | Описание |
|---|---|
| `Main` | UI поток |
| `IO` | Сетевые/дисковые операции |
| `Default` | CPU-интенсивные задачи |

Корутины **не создают** новых потоков — они переиспользуют существующие."""
    ),
    AIMessage(AIMessage.Role.User, "Чем отличается val от var?"),
    AIMessage(
        AIMessage.Role.Assistant,
        """### `val` vs `var`

- `val` — **неизменяемая** ссылка (аналог `final` в Java)
- `var` — **изменяемая** ссылка

```kotlin
val name = "Kotlin"  // нельзя переприсвоить
var counter = 0      // можно менять
counter++
```

*Рекомендуется* использовать `val` везде, где это возможно."""
    ),
    AIMessage(AIMessage.Role.User, "Спасибо! А что такое data class?"),
    AIMessage(
        AIMessage.Role.Assistant,
        """## Data Classes

`data class` автоматически генерирует:
- `equals()` / `hashCode()`
- `toString()`
- `copy()`
- `componentN()` функции

```kotlin
data class User(
    val name: String,
    val age: Int,
    val email: String,
)

val user = User("Тимур", 25, "timur@example.com")
val copy = user.copy(age = 26)
println(user) // User(name=Тимур, age=25, email=timur@example.com)
```

> Очень удобно для моделей данных — не нужно писать бойлерплейт."""
    ),
    AIMessage(AIMessage.Role.User, "Как работает sealed class?"),
    AIMessage(
        AIMessage.Role.Assistant,
        """## Sealed Classes

`sealed class` ограничивает иерархию наследования — все наследники известны на этапе компиляции.

### Пример

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

### Использование с `when`

```kotlin
fun handle(result: Result<String>) = when (result) {
    is Result.Success -> println(result.data)
    is Result.Error -> println(result.message)
    Result.Loading -> println("Загрузка...")
}
```

Компилятор проверяет что обработаны **все** варианты — не нужен `else`."""
    ),
)
