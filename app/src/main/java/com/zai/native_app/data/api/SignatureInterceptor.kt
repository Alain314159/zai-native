package com.zai.native_app.data.api

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Interceptor que calcula y añade X-Signature a las requests de chat completions.
 *
 * Algoritmo (extraído del bundle JS de chat.z.ai):
 * 1. base64Prompt = base64(prompt_text)
 * 2. payload = model + "|" + base64Prompt + "|" + timestamp
 * 3. timeBucket = floor(timestamp / (5 * 60 * 1000))
 * 4. derivedKey = HMAC-SHA256(SECRET_KEY, str(timeBucket))
 * 5. signature = HMAC-SHA256(derivedKey, payload)
 */
class SignatureInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    companion object {
        private const val SECRET_KEY = "key-@@@@)))()((9))-xxxx&&&%%%%%"
        private const val SIGNATURE_HEADER = "X-Signature"
        private const val TIMESTAMP_PARAM = "signature_timestamp"
        private const val FE_VERSION = "prod-fe-1.1.107"
        private const val COMPLETIONS_PATH = "/api/agent/v2/chat/completions"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Solo firmar requests a /api/agent/v2/chat/completions
        if (!path.contains("chat/completions")) {
            return chain.proceed(request)
        }

        // Extraer el body para calcular la signature
        val body = request.body
        if (body == null) {
            return chain.proceed(request)
        }

        // Leer el body (es un problema con OkHttp porque solo se puede leer una vez)
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        val bodyStr = buffer.readUtf8()

        // Parsear el JSON para extraer model y messages
        val gson = com.google.gson.Gson()
        val bodyJson = gson.fromJson(bodyStr, Map::class.java)
        val model = bodyJson?.get("model") as? String ?: "glm-5.3"
        val messages = bodyJson?.get("messages") as? List<*>
        val lastMessage = messages?.lastOrNull() as? Map<*, *>
        val content = lastMessage?.get("content") as? String ?: ""

        // Calcular signature
        val timestamp = System.currentTimeMillis().toString()
        val signature = computeSignature(model, content, timestamp)

        // Reconstruir la request con los headers y query param adicionales
        val newUrl = request.url.newBuilder()
            .addQueryParameter(TIMESTAMP_PARAM, timestamp)
            .build()

        val newRequest = request.newBuilder()
            .url(newUrl)
            .header(SIGNATURE_HEADER, signature)
            .header("X-FE-Version", FE_VERSION)
            .header("Accept-Language", "en-US")
            .header("Origin", "https://chat.z.ai")
            .header("Referer", "https://chat.z.ai/")
            .post(okhttp3.RequestBody.create(body.contentType(), bodyStr))
            .build()

        return chain.proceed(newRequest)
    }

    private fun computeSignature(model: String, content: String, timestamp: String): String {
        // 1. base64 of prompt
        val promptB64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)

        // 2. payload
        val payload = "$model|$promptB64|$timestamp"

        // 3. time bucket (5 min intervals)
        val tsNum = timestamp.toLong()
        val timeBucket = tsNum / (5 * 60 * 1000)

        // 4. derived key = HMAC-SHA256(SECRET_KEY, str(timeBucket))
        val derivedKey = hmacSha256(SECRET_KEY.toByteArray(), timeBucket.toString().toByteArray())

        // 5. signature = HMAC-SHA256(derivedKey, payload)
        return hmacSha256(derivedKey.toByteArray(), payload.toByteArray())
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
