package com.zai.native_app.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.zai.native_app.data.TokenManager
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    tokenManager: TokenManager,
    onLoginSuccess: () -> Unit
) {
    val TAG = "LoginScreen"
    var status by remember { mutableStateOf("Iniciando...") }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
    ) {
        AndroidView(
            factory = { ctx ->
                Log.i(TAG, "Creating WebView")
                WebView(ctx).apply {
                    // Enable everything
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                    // Cookies
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // Single webViewClient - NO duplicates
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            Log.i(TAG, "Page started: $url")
                            status = "Cargando..."
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.i(TAG, "Page finished: $url")
                            status = "Inicia sesión para continuar"

                            // Check localStorage for token
                            view?.evaluateJavascript(
                                "(function(){try{return localStorage.getItem('token')}catch(e){return null}})();"
                            ) { result ->
                                Log.i(TAG, "Token check: ${result?.take(50)}")
                                if (result != null && result != "null" && result.length > 10) {
                                    val token = result.trim('"').replace("\\\"", "\"")
                                    if (token.length > 50) {
                                        Log.i(TAG, "TOKEN FOUND! Saving...")
                                        status = "¡Conectado! Entrando..."
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
                                val start = cookies.indexOf("token=") + 6
                                val end = cookies.indexOf(";", start)
                                val token = if (end > 0) cookies.substring(start, end) else cookies.substring(start)
                                if (token.length > 50) {
                                    Log.i(TAG, "TOKEN FROM COOKIE!")
                                    status = "¡Conectado! Entrando..."
                                    kotlinx.coroutines.MainScope().launch {
                                        tokenManager.saveToken(token)
                                        onLoginSuccess()
                                    }
                                }
                            }

                            // Inject token watcher
                            view?.evaluateJavascript("""
                                (function(){
                                    if(window.__zaiWatcher) return;
                                    window.__zaiWatcher = true;
                                    setInterval(function(){
                                        try {
                                            var t = localStorage.getItem('token');
                                            if(t && t.length > 50 && !window.__zaiSent){
                                                window.__zaiSent = true;
                                                // Force a page check
                                                console.log('TOKEN_FOUND:' + t.substring(0, 20));
                                            }
                                        } catch(e){}
                                    }, 1000);
                                })();
                            """.trimIndent(), null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Let everything load inside the WebView
                            return false
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            Log.e(TAG, "WebView error: ${error?.description}")
                            status = "Error de conexión. Reintentando..."
                        }
                    }

                    // Enable Chrome devtools support for debugging
                    WebView.setWebContentsDebuggingEnabled(true)

                    Log.i(TAG, "Loading URL: https://chat.z.ai/")
                    loadUrl("https://chat.z.ai/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status text at top
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = status,
                color = Color.White,
                modifier = Modifier.background(Color(0xAA000000))
            )
        }
    }
}
