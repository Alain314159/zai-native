package com.zai.native_app.ui.login

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.zai.native_app.data.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    tokenManager: TokenManager,
    onLoginSuccess: () -> Unit
) {
    val TAG = "LoginScreen"
    var status by remember { mutableStateOf("Cargando chat.z.ai...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Poll for token every 2 seconds after page loads
    LaunchedEffect(webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            try {
                val tokenResult = wv.evaluateJavascript(
                    "(function(){try{var t=localStorage.getItem('token');return t}catch(e){return null}})();",
                    null
                )
                // evaluateJavascript is async, use the sync version
                wv.post {
                    wv.evaluateJavascript(
                        "(function(){try{return localStorage.getItem('token')}catch(e){return null}})();"
                    ) { result ->
                        if (result != null && result != "null" && result.length > 10) {
                            val token = result.trim('"').replace("\\\"", "\"")
                            if (token.length > 50) {
                                Log.i(TAG, "Token found via polling: ${token.take(30)}...")
                                status = "Token capturado! Entrando..."
                                kotlinx.coroutines.MainScope().launch {
                                    tokenManager.saveToken(token)
                                    onLoginSuccess()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll error: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
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
                    settings.userAgentString = settings.userAgentString + " ZaiNative/1.0"

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            status = "Cargando: ${url?.takeLast(40)}"
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.i(TAG, "Page finished: $url")
                            status = "Resuelve el captcha e inicia sesión"

                            // Check for token immediately after page load
                            view?.evaluateJavascript(
                                "(function(){try{return localStorage.getItem('token')}catch(e){return null}})();"
                            ) { result ->
                                Log.i(TAG, "Token check result: ${result?.take(50)}")
                                if (result != null && result != "null" && result.length > 10) {
                                    val token = result.trim('"').replace("\\\"", "\"")
                                    if (token.length > 50) {
                                        Log.i(TAG, "Token found on page finish!")
                                        status = "Token capturado! Entrando..."
                                        kotlinx.coroutines.MainScope().launch {
                                            tokenManager.saveToken(token)
                                            onLoginSuccess()
                                        }
                                    }
                                }
                            }

                            // Also check cookies
                            val cookies = CookieManager.getInstance().getCookie("https://chat.z.ai")
                            if (cookies != null) {
                                Log.i(TAG, "Cookies present: ${cookies.take(100)}")
                                if (cookies.contains("token=")) {
                                    val tokenStart = cookies.indexOf("token=") + 6
                                    val tokenEnd = cookies.indexOf(";", tokenStart)
                                    val token = if (tokenEnd > 0) {
                                        cookies.substring(tokenStart, tokenEnd)
                                    } else {
                                        cookies.substring(tokenStart)
                                    }
                                    if (token.length > 50) {
                                        Log.i(TAG, "Token from cookie!")
                                        status = "Token capturado! Entrando..."
                                        kotlinx.coroutines.MainScope().launch {
                                            tokenManager.saveToken(token)
                                            onLoginSuccess()
                                        }
                                    }
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Let everything load in the WebView including OAuth
                            return false
                        }
                    }

                    // Add a JS interface for more reliable token detection
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onToken(token: String) {
                            Log.i(TAG, "JS bridge token: ${token.take(30)}...")
                            kotlinx.coroutines.MainScope().launch {
                                tokenManager.saveToken(token)
                                onLoginSuccess()
                            }
                        }
                    }, "ZaiNative")

                    // Inject a script that watches for token changes
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.i(TAG, "Page finished: $url")
                            status = "Resuelve el captcha e inicia sesión"

                            // Inject a watcher that calls Android bridge when token appears
                            view?.evaluateJavascript("""
                                (function(){
                                    if(window.__zaiWatcher) return;
                                    window.__zaiWatcher = true;
                                    var checkToken = function(){
                                        try {
                                            var t = localStorage.getItem('token');
                                            if(t && t.length > 50 && !window.__zaiSent){
                                                window.__zaiSent = true;
                                                if(window.ZaiNative && window.ZaiNative.onToken){
                                                    ZaiNative.onToken(t);
                                                }
                                            }
                                        } catch(e){}
                                    };
                                    checkToken();
                                    setInterval(checkToken, 1500);
                                })();
                            """.trimIndent(), null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    loadUrl("https://chat.z.ai/")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status overlay at top
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (status.startsWith("Cargando")) {
                Text(
                    text = status,
                    color = Color.White,
                    modifier = Modifier.background(Color(0xAA000000))
                )
            }
        }
    }
}
