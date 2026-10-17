package com.zai.native_app.ui.login

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.unit.dp
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
    var status by remember { mutableStateOf("Cargando chat.z.ai...") }

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
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        status = "Cargando: ${url?.takeLast(40)}"
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.i(TAG, "Page finished: $url")
                        // Extract token from localStorage
                        view?.evaluateJavascript(
                            "(function(){try{return localStorage.getItem('token')}catch(e){return null}})();"
                        ) { result ->
                            if (result != null && result != "null" && result.length > 10) {
                                val token = result.trim('"')
                                if (token.length > 50) {
                                    Log.i(TAG, "Token from localStorage: ${token.take(30)}...")
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

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }
                }

                loadUrl("https://chat.z.ai/")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
