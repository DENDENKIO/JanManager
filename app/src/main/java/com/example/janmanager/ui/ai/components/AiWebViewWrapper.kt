package com.example.janmanager.ui.ai.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiWebViewWrapper(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true

                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // GikobunAIと同一のブラウザUA
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36"

                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true
                isNestedScrollingEnabled = false

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // SPA遷移後もBridgeが確実に使えるようにする
                        // （通常は不要だが保険として）
                    }
                }

                // ★ onWebViewCreated を loadUrl の前に呼ぶ
                // ★ これにより addJavascriptInterface が loadUrl より先に実行される
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { _ -> }
    )
}
