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

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true

                    // スクロール・ズーム有効化
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // Remove "; wv" from UserAgent as requested
                    val originalUserAgent = userAgentString
                    userAgentString = originalUserAgent.replace("; wv", "")

                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                // スクロールバー表示
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true

                // Composeの親スクロールとの競合を防ぐ
                isNestedScrollingEnabled = false

                // Cookie settings
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { _ ->
            // Update logic if needed
        }
    )
}
