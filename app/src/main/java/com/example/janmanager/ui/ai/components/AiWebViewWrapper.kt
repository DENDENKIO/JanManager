package com.example.janmanager.ui.ai.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
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
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Remove WebView identifier to prevent some mobile login blocking (e.g. Google)
                    userAgentString = userAgentString.replace("; wv", "")
                    // Allow mixed content if any
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                
                webViewClient = WebViewClient()
                
                // Keep cookies & third party cookies configured
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            // If URL changes, load new URL
            if (webView.url != url && webView.url?.contains(url) != true) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
