package com.zai.native_app.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonObject

class ZaiApiClient(
    private val tokenProvider: () -> String?
) {
    companion object {
        private const val BASE_URL = "https://chat.z.ai"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(SignatureInterceptor(tokenProvider))
            .build()
    }

    fun getGuestToken(): String? {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/auths/")
                .header("Accept", "application/json")
                .get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JsonParser.parseString(body).asJsonObject
                return json.get("token")?.asString
            }
        } catch (e: Exception) { return null }
    }

    fun createChat(title: String = "New Chat", model: String = "glm-5.3"): String? {
        try {
            val json = JsonObject().apply {
                add("chat", JsonObject().apply {
                    addProperty("title", title)
                    add("models", com.google.gson.JsonArray().apply { add(model) })
                })
            }
            val body = Gson().toJson(json)
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/chats/new")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val respJson = JsonParser.parseString(responseBody).asJsonObject
                return respJson.get("id")?.asString
            }
        } catch (e: Exception) { return null }
    }

    fun sendStreamingMessage(
        model: String,
        messages: List<Pair<String, String>>,
        chatId: String?,
        listener: StreamListener
    ): EventSource {
        val gson = Gson()
        val messagesArray = com.google.gson.JsonArray()
        for ((role, content) in messages) {
            val msgObj = JsonObject().apply {
                addProperty("role", role)
                addProperty("content", content)
            }
            messagesArray.add(msgObj)
        }

        val bodyJson = JsonObject().apply {
            addProperty("model", model)
            add("messages", messagesArray)
            addProperty("stream", true)
            if (chatId != null) addProperty("chat_id", chatId)
        }

        val bodyStr = gson.toJson(bodyJson)

        val request = Request.Builder()
            .url("$BASE_URL/api/agent/v2/chat/completions")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .build()

        val factory = EventSources.createFactory(client)
        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                listener.onOpen()
            }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                listener.onEvent(data)
            }
            override fun onClosed(eventSource: EventSource) {
                listener.onClosed()
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                listener.onError(t?.message ?: "Unknown error", response?.code ?: 0)
            }
        })
    }
}

interface StreamListener {
    fun onOpen()
    fun onEvent(data: String)
    fun onClosed()
    fun onError(message: String, code: Int)
}
