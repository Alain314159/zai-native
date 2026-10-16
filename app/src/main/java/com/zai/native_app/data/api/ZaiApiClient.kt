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

/**
 * Cliente HTTP para la API de chat.z.ai
 *
 * Endpoints:
 *  - GET  /api/v1/auths/           → guest token
 *  - GET  /api/config               → config pública
 *  - GET  /api/models               → modelos (con token)
 *  - POST /api/v1/chats/new         → crear conversación
 *  - GET  /api/v1/chats/            → listar conversaciones
 *  - POST /api/agent/v2/chat/completions → streaming del chat
 */
class ZaiApiClient(
    private val tokenProvider: () -> String?
) {
    companion object {
        private const val BASE_URL = "https://chat.z.ai"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // sin timeout para SSE
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(SignatureInterceptor(tokenProvider))
            .build()
    }

    /**
     * Obtiene un guest token automático (sin login).
     */
    fun getGuestToken(): String? {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/auths/")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            return json.get("token")?.asString
        }
    }

    /**
     * Obtiene la lista de modelos disponibles.
     */
    fun getModels(): List<ModelInfo> {
        val request = Request.Builder()
            .url("$BASE_URL/api/models")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: return emptyList()
            return data.map { item ->
                val obj = item.asJsonObject
                ModelInfo(
                    id = obj.get("id")?.asString ?: "",
                    name = obj.get("name")?.asString ?: ""
                )
            }
        }
    }

    /**
     * Crea una nueva conversación.
     */
    fun createChat(title: String = "New Chat", model: String = "glm-5.3"): String? {
        val body = """
            {"chat":{"title":"$title","models":["$model"]}}
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/chats/new")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val json = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
            return json.get("id")?.asString
        }
    }

    /**
     * Envía un mensaje y devuelve un EventSource para streaming SSE.
     *
     * @param model ID del modelo (ej: "glm-5.3", "x-preview-l")
     * @param messages Lista de mensajes [{role, content}]
     * @param chatId ID de la conversación
     * @param listener Listener para recibir los eventos SSE
     * @return EventSource (llamar cancel() para detener)
     */
    fun sendStreamingMessage(
        model: String,
        messages: List<Pair<String, String>>,
        chatId: String?,
        listener: StreamListener
    ): EventSource {
        val messagesJson = messages.joinToString(",") { (role, content) ->
            """{"role":"$role","content":${com.google.gson.Gson().toJson(content)}}"""
        }

        val bodyStr = """
            {"model":"$model","messages":[$messagesJson],"stream":true${if (chatId != null) """"chat_id":"$chatId"""" else ""}}
        """.trimIndent()

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

    /**
     * Lista las conversaciones del usuario.
     */
    fun listChats(): List<ChatInfo> {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/chats/?page=1&size=50")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val chatsArray = json.getAsJsonArray("data") ?: return emptyList()
            return chatsArray.map { item ->
                val obj = item.asJsonObject
                ChatInfo(
                    id = obj.get("id")?.asString ?: "",
                    title = obj.get("title")?.asString ?: "",
                    updatedAt = obj.get("updated_at")?.asLong ?: 0L
                )
            }
        }
    }
}

data class ModelInfo(val id: String, val name: String)
data class ChatInfo(val id: String, val title: String, val updatedAt: Long)

interface StreamListener {
    fun onOpen()
    fun onEvent(data: String)
    fun onClosed()
    fun onError(message: String, code: Int)
}
