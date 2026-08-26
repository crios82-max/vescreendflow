package com.veplayer.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.veplayer.app.BuildConfig

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen() {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(BuildConfig.PLAYER_URL)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen() {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(BuildConfig.SENSEFLOW_URL.trimEnd('/') + "/")
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
