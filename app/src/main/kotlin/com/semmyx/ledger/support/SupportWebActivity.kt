package com.semmyx.ledger.support

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.UserPreferences
import com.semmyx.ledger.theme.LedgerTheme
import com.semmyx.ledger.theme.LocalizedContent

/** Non-exported HTTPS-only fallback. No JS bridge, file access, SSL bypass, or arbitrary URL input. */
class SupportWebActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by (application as LedgerApplication).container.preferences.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            LocalizedContent(prefs.language) {
                LedgerTheme(prefs.theme) {
                    var error by remember { mutableStateOf(false) }
                    var loading by remember { mutableStateOf(true) }
                    var webView by remember { mutableStateOf<WebView?>(null) }
                    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else finish() }
                    DisposableEffect(Unit) {
                        onDispose {
                            webView?.let { view ->
                                view.stopLoading()
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.destroy()
                            }
                            webView = null
                        }
                    }
                    Scaffold(topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.telegram_support)) }, navigationIcon = {
                            IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        })
                    }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if (loading && !error) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (error) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(stringResource(R.string.support_network_error))
                                    Button(onClick = { error = false; loading = true; webView?.loadUrl(SupportNavigator.WEB_URL) }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.domStorageEnabled = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.safeBrowsingEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val uri = request.url
                                            if (uri.scheme == "tg" && request.hasGesture()) {
                                                SupportNavigator.openTelegram(this@SupportWebActivity)
                                                return true
                                            }
                                            return uri.scheme != "https" || uri.host != "t.me"
                                        }
                                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                            if (request.isForMainFrame) { error = true; loading = false }
                                        }
                                    }
                                    webView = this
                                    loadUrl(SupportNavigator.WEB_URL)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
