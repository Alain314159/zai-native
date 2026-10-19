package com.zai.native_app.ui.login

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    AndroidView(
        factory = { ctx ->
            Log.i(TAG, "=== Creating WebView ===")
            val wv = WebView(ctx)

            // Layout params - CRITICAL: must match parent
            wv.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Settings
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                // Real browser user agent
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                // Allow mixed content for OAuth redirects
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // Allow file access
                allowFileAccess = true
                allowContentAccess = true
            }

            // Cookies
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            // WebViewClient
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    Log.i(TAG, ">>> Page started: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.i(TAG, ">>> Page finished: $url")

                    // Check for token
                    view?.evaluateJavascript(
                        "(function(){try{return localStorage.getItem('token')}catch(e){return null}})();"
                    ) { result ->
                        Log.i(TAG, ">>> Token check: ${result?.take(50)}")
                        if (result != null && result != "null" && result.length > 10) {
                            val token = result.trim('"').replace("\\\"", "\"")
                            if (token.length > 50) {
                                Log.i(TAG, ">>> TOKEN FOUND!")
                                kotlinx.coroutines.MainScope().launch {
                                    tokenManager.saveToken(token)
                                    onLoginSuccess()
                                }
                            }
                        }
                    }

                    // Check cookies
                    val cookies = CookieManager.getInstance().getCookie("https://chat.z.ai")
                    Log.i(TAG, ">>> Cookies: ${cookies?.take(100)}")
                    if (cookies != null && cookies.contains("token=")) {
                        val start = cookies.indexOf("token=") + 6
                        val end = cookies.indexOf(";", start)
                        val token = if (end > 0) cookies.substring(start, end) else cookies.substring(start)
                        if (token.length > 50) {
                            Log.i(TAG, ">>> TOKEN FROM COOKIE!")
                            kotlinx.coroutines.MainScope().launch {
                                tokenManager.saveToken(token)
                                onLoginSuccess()
                            }
                        }
                    }

                    // Inject watcher
                    view?.evaluateJavascript("""
                        (function(){
                            if(window.__zaiW) return;
                            window.__zaiW = true;
                            setInterval(function(){
                                try {
                                    var t = localStorage.getItem('token');
                                    if(t && t.length > 50 && !window.__zaiSent){
                                        window.__zaiSent = true;
                                    }
                                } catch(e){}
                            }, 1000);
                        })();
                    """.trimIndent(), null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    Log.e(TAG, ">>> Error: ${error?.description} (code: ${error?.errorCode})")
                }
            }

            // Enable debugging
            WebView.setWebContentsDebuggingEnabled(true)

            // LOAD THE URL
            Log.i(TAG, ">>> Loading: https://chat.z.ai/")
            wv.loadUrl("https://chat.z.ai/")

            webView = wv
            wv
        },
        modifier = Modifier.fillMaxSize()
    )
}
