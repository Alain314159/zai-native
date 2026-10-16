package com.zai.native_app.ui.login

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.zai.native_app.data.TokenManager

/**
 * Pantalla de login que carga chat.z.ai en un WebView.
 *
 * El usuario resuelve el captcha y se loguea (Google/Email).
 * Cuando el login completa, interceptamos el token JWT de las cookies
 * o de las responses de la API.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    tokenManager: TokenManager,
    onLoginSuccess: () -> Unit
) {
    val TAG = "LoginScreen"
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Cargando...") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                        ) {
                            loading = true
                            status = "Cargando: ${url?.takeLast(40)}"
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            Log.i(TAG, "Page finished: $url")

                            // After page loads, try to extract the token from localStorage
                            view?.evaluateJavascript("""
                                (function() {
                                    try {
                                        var token = localStorage.getItem('token');
                                        if (token) {
                                            window.__ZAI_TOKEN__ = token;
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent()) { result ->
                                if (result != null && result != "null" && result.length > 10) {
                                    val token = result.trim('"')
                                    if (token.length > 50) {
                                        Log.i(TAG, "Token captured from localStorage: ${token.take(30)}...")
                                        kotlinx.coroutines.MainScope().launch {
                                            tokenManager.saveToken(token)
                                            onLoginSuccess()
                                        }
                                    }
                                }
                            }

                            // Also check cookies
                            val cookies = CookieManager.getInstance().getCookie("https://chat.z.ai")
                            if (cookies != null && cookies.contains("token=")) {
                                val tokenStart = cookies.indexOf("token=") + 6
                                val tokenEnd = cookies.indexOf(";", tokenStart)
                                val token = if (tokenEnd > 0) {
                                    cookies.substring(tokenStart, tokenEnd)
                                } else {
                                    cookies.substring(tokenStart)
                                }
                                if (token.length > 50) {
                                    Log.i(TAG, "Token from cookie: ${token.take(30)}...")
                                    kotlinx.coroutines.MainScope().launch {
                                        tokenManager.saveToken(token)
                                        onLoginSuccess()
                                    }
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Let everything load in the WebView (including OAuth redirects)
                            return false
                        }
                    }

                    // Intercept console.log to detect token
                    // Also inject a JS bridge that periodically checks for token
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onTokenFound(token: String) {
                            Log.i(TAG, "Token found via JS bridge: ${token.take(30)}...")
                            kotlinx.coroutines.MainScope().launch {
                                tokenManager.saveToken(token)
                                onLoginSuccess()
                            }
                        }
                    }, "AndroidBridge")

                    loadUrl("https://chat.z.ai/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(dp(16))
                )
            }
        }
    }
}

