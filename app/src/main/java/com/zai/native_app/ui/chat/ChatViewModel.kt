package com.zai.native_app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.native_app.data.TokenManager
import com.zai.native_app.data.api.StreamListener
import com.zai.native_app.data.api.ZaiApiClient
import com.zai.native_app.data.repo.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource
import com.google.gson.JsonParser
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentEventSource: EventSource? = null
    private var currentChatId: String? = null
    private var apiClient: ZaiApiClient? = null
    private var repository: ChatRepository? = null
    private var messageSeq = 0

    fun init(context: Context) {
        if (repository == null) {
            repository = ChatRepository(context)
        }
    }

    fun loadChat(chatId: String, context: Context) {
        currentChatId = chatId
        init(context)
        viewModelScope.launch {
            val msgs = repository?.getMessages(chatId) ?: emptyList()
            _messages.value = msgs.map { ChatMessage(it.role, it.content) }
            messageSeq = msgs.maxOfOrNull { it.seq }?.plus(1) ?: 0
        }
    }

    fun newChat() {
        currentChatId = null
        _messages.value = emptyList()
        messageSeq = 0
        _currentResponse.value = ""
        _error.value = null
    }

    fun sendMessage(text: String, tokenManager: TokenManager, context: Context) {
        if (text.isBlank()) return
        init(context)

        val userMsg = ChatMessage("user", text)
        _messages.value = _messages.value + userMsg
        _isStreaming.value = true
        _currentResponse.value = ""
        _error.value = null

        viewModelScope.launch {
            if (apiClient == null) {
                apiClient = ZaiApiClient { tokenManager.getTokenSync() }
            }

            // Create local chat if needed
            val chatId = currentChatId ?: UUID.randomUUID().toString()
            if (currentChatId == null) {
                currentChatId = chatId
                val title = text.take(40)
                repository?.createChat(chatId, title)
            }

            // Save user message locally
            repository?.addMessage(chatId, "user", text, messageSeq++)

            // Build message history
            val messageHistory = _messages.value.map { it.role to it.content }

            // Send streaming message
            currentEventSource = apiClient?.sendStreamingMessage(
                model = "glm-5.3",
                messages = messageHistory,
                chatId = null, // don't send remote chat_id for now
                listener = object : StreamListener {
                    override fun onOpen() {}

                    override fun onEvent(data: String) {
                        if (data == "[DONE]") {
                            finalizeResponse(chatId)
                            return
                        }

                        try {
                            val json = JsonParser.parseString(data).asJsonObject

                            // chat.z.ai format
                            val dataObj = json.getAsJsonObject("data")
                            if (dataObj != null) {
                                val innerData = dataObj.getAsJsonObject("data")
                                if (innerData != null) {
                                    if (innerData.has("error")) {
                                        val errorObj = innerData.getAsJsonObject("error")
                                        val errorMsg = errorObj?.get("detail")?.asString ?: "Error"
                                        _error.value = errorMsg
                                        _isStreaming.value = false
                                        return
                                    }
                                    if (innerData.has("content")) {
                                        val content = innerData.get("content").asString
                                        _currentResponse.value += content
                                    }
                                    if (innerData.has("done") && innerData.get("done").asBoolean) {
                                        finalizeResponse(chatId)
                                    }
                                    return
                                }
                            }

                            // Standard OpenAI format
                            val choices = json.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                if (delta != null && delta.has("content")) {
                                    _currentResponse.value += delta.get("content").asString
                                }
                                if (choices[0].asJsonObject.has("finish_reason")) {
                                    val fr = choices[0].asJsonObject.get("finish_reason")
                                    if (!fr.isJsonNull && fr.asString != "null") {
                                        finalizeResponse(chatId)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore parse errors
                        }
                    }

                    override fun onClosed() {
                        finalizeResponse(chatId)
                    }

                    override fun onError(message: String, code: Int) {
                        _error.value = "Error $code: $message"
                        _isStreaming.value = false
                    }
                }
            )
        }
    }

    private fun finalizeResponse(chatId: String) {
        val response = _currentResponse.value
        if (response.isNotEmpty()) {
            _messages.value = _messages.value + ChatMessage("assistant", response)
            viewModelScope.launch {
                repository?.addMessage(chatId, "assistant", response, messageSeq++)
            }
        }
        _currentResponse.value = ""
        _isStreaming.value = false
    }

    fun stopStreaming() {
        currentEventSource?.cancel()
        currentEventSource = null
        currentChatId?.let { finalizeResponse(it) }
    }

    override fun onCleared() {
        super.onCleared()
        currentEventSource?.cancel()
    }
}

data class ChatMessage(
    val role: String,
    val content: String
)
