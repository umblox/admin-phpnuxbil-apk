package id.biz.arneta.admin.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.ArnetaGoldLight
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary

/**
 * IN-APP BROWSER VIEW INTERNAL (Menggunakan SSO Plugin Mobile API)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminInAppWebViewScreen(
    initialRouteOrUrl: String = "dashboard",
    title: String = "Web Admin Panel",
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    var selectedQuickRoute by remember { mutableStateOf(initialRouteOrUrl) }

    fun buildTargetUrl(routeOrUrl: String): String {
        if (routeOrUrl.startsWith("http://") || routeOrUrl.startsWith("https://")) {
            return routeOrUrl
        }
        val cleanRoute = routeOrUrl.removePrefix("?_route=").removePrefix("/")
        return ApiClient.buildWebViewSsoUrl(cleanRoute)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF141B2D),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl.ifEmpty { "${ApiClient.baseUrl}/?_route=$selectedQuickRoute" },
                                color = ArnetaGoldLight.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = {
                                if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                            }
                        ) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Mundur", tint = Color.White)
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val webRoutes = listOf(
                        "dashboard" to "Dasbor Web (?_route=dashboard)",
                        "plugin/mobile_api_config" to "Status Mobile API",
                        "pluginmanager" to "Plugin Manager",
                        "paymentgateway" to "Payment Gateway",
                        "radius" to "FreeRADIUS",
                        "pages/Order_Voucher" to "Template Voucher",
                        "community" to "Info & Update",
                        "admin" to "Halaman Login (?_route=admin)"
                    )
                    webRoutes.forEach { (rKey, rLabel) ->
                        AssistChip(
                            onClick = {
                                selectedQuickRoute = rKey
                                val target = if (rKey == "admin") {
                                    "${ApiClient.baseUrl}/?_route=admin"
                                } else {
                                    buildTargetUrl(rKey)
                                }
                                webViewRef?.loadUrl(target)
                            },
                            label = { Text(rLabel, fontSize = 11.sp, color = Color.White) }
                        )
                    }
                }

                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        color = ArnetaGoldPrimary,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                return false
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            currentUrl = url ?: ""
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    val startUrl = if (initialRouteOrUrl == "admin") {
                        "${ApiClient.baseUrl}/?_route=admin"
                    } else {
                        buildTargetUrl(initialRouteOrUrl)
                    }
                    loadUrl(startUrl)
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
