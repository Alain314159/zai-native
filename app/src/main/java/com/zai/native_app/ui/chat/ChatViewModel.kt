package com.zai.native_app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.native_app.data.TokenManager
import com.zai.native_app.data.api.StreamListener
import com.zai.native_app.data.api.ZaiApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource
import com.google.gson.JsonParser

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private var currentEventSource: EventSource? = null
    private var currentChatId: String? = null
    private var apiClient: ZaiApiClient? = null

    fun sendMessage(text: String, tokenManager: TokenManager) {
        // Add user message
        _messages.value = _messages.value + ChatMessage("user", text)
        _isStreaming.value = true
        _currentResponse.value = ""

        viewModelScope.launch {
            val token = tokenManager.token
            val model = tokenManager.selectedModel

            if (apiClient == null) {
                apiClient = ZaiApiClient { tokenManager.getTokenSync() }
            }

            // Create chat if not exists
            if (currentChatId == null) {
                currentChatId = try {
                    apiClient?.createChat(model = "glm-5.3")
                } catch (e: Exception) {
                    null
                }
            }

            // Build message history
            val messageHistory = _messages.value.map { it.role to it.content }

            // Send streaming message
            currentEventSource = apiClient?.sendStreamingMessage(
                model = "glm-5.3",
                messages = messageHistory,
                chatId = currentChatId,
                listener = object : StreamListener {
                    override fun onOpen() {
                        // Stream started
                    }

                    override fun onEvent(data: String) {
                        if (data == "[DONE]") {
                            // Stream finished
                            val finalResponse = _currentResponse.value
                            if (finalResponse.isNotEmpty()) {
                                _messages.value = _messages.value + ChatMessage("assistant", finalResponse)
                            }
                            _currentResponse.value = ""
                            _isStreaming.value = false
                            return
                        }

                        try {
                            val json = JsonParser.parseString(data).asJsonObject

                            // chat.z.ai format: {"data":{"data":{...}},"type":"chat:completion"}
                            val dataObj = json.getAsJsonObject("data")
                            if (dataObj != null) {
                                val innerData = dataObj.getAsJsonObject("data")
                                if (innerData != null) {
                                    // Check for error
                                    if (innerData.has("error")) {
                                        val error = innerData.getAsJsonObject("error")
                                        val errorMsg = error?.get("detail")?.asString ?: "Error"
                                        _currentResponse.value = "⚠️ $errorMsg"
                                        _isStreaming.value = false
                                        return
                                    }

                                    // Check for content
                                    if (innerData.has("content")) {
                                        val content = innerData.get("content").asString
                                        _currentResponse.value += content
                                    }

                                    // Check for done
                                    if (innerData.has("done") && innerData.get("done").asBoolean) {
                                        if (_currentResponse.value.isNotEmpty()) {
                                            _messages.value = _messages.value + ChatMessage("assistant", _currentResponse.value)
                                        }
                                        _currentResponse.value = ""
                                        _isStreaming.value = false
                                    }
                                    return
                                }
                            }

                            // Standard OpenAI format: {"choices":[{"delta":{"content":"..."}}]}
                            val choices = json.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject
                                    .getAsJsonObject("delta")
                                if (delta != null && delta.has("content")) {
                                    val content = delta.get("content").asString
                                    _currentResponse.value += content
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore parse errors
                        }
                    }

                    override fun onClosed() {
                        if (_currentResponse.value.isNotEmpty()) {
                            _messages.value = _messages.value + ChatMessage("assistant", _currentResponse.value)
                        }
                        _currentResponse.value = ""
                        _isStreaming.value = false
                    }

                    override fun onError(message: String, code: Int) {
                        _currentResponse.value = "❌ Error: $message (code: $code)"
                        _isStreaming.value = false
                    }
                }
            )
        }
    }

    fun stopStreaming() {
        currentEventSource?.cancel()
        currentEventSource = null
        if (_currentResponse.value.isNotEmpty()) {
            _messages.value = _messages.value + ChatMessage("assistant", _currentResponse.value)
        }
        _currentResponse.value = ""
        _isStreaming.value = false
    }

    override fun onCleared() {
        super.onCleared()
        currentEventSource?.cancel()
    }
}
