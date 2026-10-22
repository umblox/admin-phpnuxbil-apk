package id.biz.arneta.admin.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 1. IN-APP BROWSER VIEW (WEBVIEW INTERNAL ADMIN DENGAN AUTO-LOGIN SSO)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminInAppWebViewScreen(
    initialRouteOrUrl: String = "dashboard",
    title: String = "Web Admin Arneta.ID",
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
        val encodedToken = URLEncoder.encode(ApiClient.token, "UTF-8")
        val encodedRoute = URLEncoder.encode(cleanRoute, "UTF-8")
        return "${ApiClient.baseUrl}/system/api.php?r=apiv1/webview_sso&token=$encodedToken&redirect=$encodedRoute"
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
                        "admin" to "Login Asli (?_route=admin)",
                        "pluginmanager" to "Plugin Manager",
                        "paymentgateway" to "Payment Gateway",
                        "radius" to "FreeRADIUS",
                        "pages/Order_Voucher" to "Template Voucher"
                    )
                    webRoutes.forEach { (rKey, rLabel) ->
                        AssistChip(
                            onClick = {
                                selectedQuickRoute = rKey
                                webViewRef?.loadUrl(buildTargetUrl(rKey))
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

                    loadUrl(buildTargetUrl(initialRouteOrUrl))
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/**
 * 2. LIVE SPEED MONITOR MIKROTIK (MBPS/KBPS REAL-TIME) & USER ONLINE (KICK SESI)
 */
@Composable
fun AdminNetworkAndOnlineScreen(
    initialTab: String = "live_monitor"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) } // live_monitor | online_users | routers | pools
    var isLivePolling by remember { mutableStateOf(true) }
    var routerStatusList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var onlineUsersList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var poolList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Riwayat gelombang kecepatan trafik berjalan (Mbps)
    var downloadHistory by remember { mutableStateOf(listOf(0f, 0.5f, 1.2f, 2.5f, 1.8f, 3.2f, 4.0f, 2.1f)) }
    var uploadHistory by remember { mutableStateOf(listOf(0f, 0.2f, 0.4f, 0.8f, 0.6f, 1.1f, 0.9f, 0.7f)) }
    var currentDownMbps by remember { mutableFloatStateOf(0f) }
    var currentUpMbps by remember { mutableFloatStateOf(0f) }
    var downSpeedLabel by remember { mutableStateOf("0 Kbps") }
    var upSpeedLabel by remember { mutableStateOf("0 Kbps") }
    var lastUpdatedTime by remember { mutableStateOf("Menghubungkan...") }

    // Dialog Tambah/Edit Router & Pool
    var showRouterDialog by remember { mutableStateOf(false) }
    var editingRouter by remember { mutableStateOf<JSONObject?>(null) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var editingPool by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun fetchLiveRouterStatus() {
        val res = ApiClient.request("admin_router_status")
        val arr = res.dataArray ?: res.data?.optJSONArray("result") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        var sumDl = 0f
        var sumUl = 0f
        var topDlLabel = "0 Kbps"
        var topUlLabel = "0 Kbps"

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(obj)
            if (obj.optBoolean("online", false)) {
                val dl = obj.optDouble("download_mbps", 0.0).toFloat()
                val ul = obj.optDouble("upload_mbps", 0.0).toFloat()
                sumDl += dl
                sumUl += ul
                topDlLabel = obj.optString("download_speed_label", "0 Kbps")
                topUlLabel = obj.optString("upload_speed_label", "0 Kbps")
            }
        }
        routerStatusList = list
        currentDownMbps = sumDl
        currentUpMbps = sumUl
        downSpeedLabel = if (list.size > 1) "${String.format("%.2f", sumDl)} Mbps" else topDlLabel
        upSpeedLabel = if (list.size > 1) "${String.format("%.2f", sumUl)} Mbps" else topUlLabel

        downloadHistory = (downloadHistory + sumDl).takeLast(24)
        uploadHistory = (uploadHistory + sumUl).takeLast(24)

        val cal = java.util.Calendar.getInstance()
        lastUpdatedTime = String.format(
            "%02d:%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }

    suspend fun fetchOnlineUsers() {
        val res = ApiClient.request("admin_online_users")
        val arr = res.data?.optJSONArray("users") ?: res.data?.optJSONArray("items") ?: res.dataArray ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            list.add(arr.getJSONObject(i))
        }
        onlineUsersList = list
    }

    fun loadTabContent() {
        scope.launch {
            when (currentTab) {
                "live_monitor" -> fetchLiveRouterStatus()
                "online_users" -> fetchOnlineUsers()
                "routers" -> {
                    val res = ApiClient.request("admin_routers")
                    val arr = res.dataArray ?: res.data?.optJSONArray("d") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    routerList = list
                }
                "pools" -> {
                    val res = ApiClient.request("admin_pools")
                    val arr = res.dataArray ?: res.data?.optJSONArray("d") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    poolList = list
                }
            }
        }
    }

    // Auto-polling halus setiap 2.5 detik untuk Live Speed Monitor & User Online
    LaunchedEffect(currentTab, isLivePolling) {
        loadTabContent()
        if (isLivePolling && (currentTab == "live_monitor" || currentTab == "online_users")) {
            while (isActive && isLivePolling) {
                delay(2500L)
                if (currentTab == "live_monitor") {
                    fetchLiveRouterStatus()
                } else if (currentTab == "online_users") {
                    fetchOnlineUsers()
                }
            }
        }
    }

    // Filter instan 1 huruf untuk User Online
    val filteredOnlineUsers = remember(onlineUsersList, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) onlineUsersList
        else onlineUsersList.filter { u ->
            u.optString("username", "").lowercase().contains(q) ||
                u.optString("ip_address", "").lowercase().contains(q) ||
                u.optString("service", "").lowercase().contains(q) ||
                u.optString("router", "").lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "live_monitor" to "Live Speed Monitor",
                "online_users" to "User Online (${onlineUsersList.size})",
                "routers" to "Daftar Router",
                "pools" to "IP Pool"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = currentTab == key,
                    onClick = { currentTab = key },
                    label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (currentTab) {
            "live_monitor" -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // KARTU SPEEDOMETER & GRAFIK REAL-TIME SPEED MIKROTIK
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isLivePolling) ArnetaGreenSuccess else ArnetaOrangeActive)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "MikroTik Live Speed & Telemetry",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Update: $lastUpdatedTime",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isLivePolling) "Live" else "Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isLivePolling,
                                            onCheckedChange = { isLivePolling = it }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // KOTAK KECEPATAN UPLOAD & DOWNLOAD REAL-TIME DENGAN WARNA MENYALA
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = ArnetaCyanIncome.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Download Speed", fontSize = 11.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = downSpeedLabel,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ArnetaCyanIncome
                                            )
                                        }
                                    }

                                    Surface(
                                        color = ArnetaGoldPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Upload Speed", fontSize = 11.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = upSpeedLabel,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ArnetaGoldPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // GRAFIK GELOMBANG SPEED REAL-TIME (ANIMASI MULUS 60 FPS)
                                LiveSpeedWaveDiagram(
                                    downloadPoints = downloadHistory,
                                    uploadPoints = uploadHistory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                            }
                        }
                    }

                    // DAFTAR STATUS ROUTER MIKROTIK
                    items(routerStatusList) { r ->
                        val online = r.optBoolean("online", false)
                        val cpu = r.optInt("cpu_load", 0)
                        val ramPct = r.optDouble("ram_percent", 0.0).toFloat()

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${r.optString("name")} (${r.optString("board_name")})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "IP: ${r.optString("ip_address")} • Uptime: ${r.optString("uptime")}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }

                                    Surface(
                                        color = if (online) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (online) "${r.optInt("ping_ms")} ms • ONLINE" else "OFFLINE",
                                            color = if (online) ArnetaGreenSuccess else ArnetaRedDanger,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("CPU Load: $cpu%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Text("RAM: $ramPct% (${r.optString("free_memory")} Free)", fontSize = 11.5.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (cpu / 100f).coerceIn(0f, 1f) },
                                    color = if (cpu > 80) ArnetaRedDanger else ArnetaCyanIncome,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Trafik Aktif: ↓ ${r.optString("download_speed_label")} • ↑ ${r.optString("upload_speed_label")}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaGoldPrimary
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val rbRes = ApiClient.request("admin_router_reboot", mapOf("id" to r.optInt("id")))
                                                Toast.makeText(context, rbRes.message, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = ArnetaRedDanger, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reboot", fontSize = 11.sp, color = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "online_users" -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ketik 1 huruf username / IP user online...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredOnlineUsers.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Tidak ada user online yang cocok.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredOnlineUsers) { u ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (u.optString("service") == "PPPoE") ArnetaIndigoAccent else ArnetaCyanIncome,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = u.optString("service"),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = u.optString("username"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "IP: ${u.optString("ip_address")} • MAC: ${u.optString("mac_address")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "Uptime: ${u.optString("uptime")} • Trafik: ${u.optString("total_bytes")} (${u.optString("router")})",
                                            fontSize = 11.sp,
                                            color = ArnetaGoldPrimary
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val kRes = ApiClient.request(
                                                    "admin_kick_user",
                                                    mapOf(
                                                        "router" to u.optString("router"),
                                                        "username" to u.optString("username"),
                                                        "service" to u.optString("service"),
                                                        "ros_id" to u.optString("ros_id")
                                                    )
                                                )
                                                Toast.makeText(context, kRes.message, Toast.LENGTH_SHORT).show()
                                                fetchOnlineUsers()
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = ArnetaRedDanger.copy(alpha = 0.2f),
                                            contentColor = ArnetaRedDanger
                                        )
                                    ) {
                                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kick", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "routers" -> {
                Button(
                    onClick = {
                        editingRouter = null
                        showRouterDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Router MikroTik", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(routerList) { r ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("IP/API: ${r.optString("ip_address")} • User: ${r.optString("username")}", fontSize = 12.sp)
                                    if (r.optString("coordinates").isNotEmpty()) {
                                        Text("Koordinat: ${r.optString("coordinates")} (Radius ${r.optString("coverage")}m)", fontSize = 11.sp, color = ArnetaCyanIncome)
                                    }
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingRouter = r
                                        showRouterDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, tint = ArnetaGoldPrimary)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            ApiClient.request("admin_router_delete", mapOf("id" to r.optInt("id")))
                                            loadTabContent()
                                        }
                                    }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "pools" -> {
                Button(
                    onClick = {
                        editingPool = null
                        showPoolDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah IP Pool", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(poolList) { p ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(p.optString("pool_name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Range IP: ${p.optString("range_ip")} • Router: ${p.optString("routers")}", fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        ApiClient.request("admin_pool_delete", mapOf("id" to p.optInt("id")))
                                        loadTabContent()
                                    }
                                }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRouterDialog) {
        RouterFormDialog(
            initial = editingRouter,
            onDismiss = { showRouterDialog = false },
            onSaved = {
                showRouterDialog = false
                loadTabContent()
            }
        )
    }

    if (showPoolDialog) {
        PoolFormDialog(
            initial = editingPool,
            onDismiss = { showPoolDialog = false },
            onSaved = {
                showPoolDialog = false
                loadTabContent()
            }
        )
    }
}

/**
 * KOMPONEN CANVAS: GRAFIK GELOMBANG SPEED REAL-TIME (ANIMASI MULUS 60 FPS DENGAN DUA JALUR MBPS)
 */
@Composable
private fun LiveSpeedWaveDiagram(
    downloadPoints: List<Float>,
    uploadPoints: List<Float>,
    modifier: Modifier = Modifier
) {
    val maxSpeed = (maxOf(
        downloadPoints.maxOrNull() ?: 1f,
        uploadPoints.maxOrNull() ?: 1f
    ) * 1.25f).coerceAtLeast(2.0f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B1120))
            .padding(10.dp)
    ) {
        val w = size.width
        val h = size.height

        // Garis Grid Horizontal
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = h * (i / gridSteps.toFloat())
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        fun drawSmoothWave(points: List<Float>, lineColor: Color, fillAlpha: Float) {
            if (points.size < 2) return
            val path = Path()
            val fillPath = Path()
            val stepX = w / (points.size - 1).coerceAtLeast(1)

            points.forEachIndexed { idx, speed ->
                val x = idx * stepX
                val y = h - ((speed / maxSpeed).coerceIn(0f, 1f) * h)
                if (idx == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (idx == points.lastIndex) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = fillAlpha), Color.Transparent)
                )
            )
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }

        // Render Gelombang Upload (Gold) & Download (Cyan)
        drawSmoothWave(uploadPoints, ArnetaGoldPrimary, 0.20f)
        drawSmoothWave(downloadPoints, ArnetaCyanIncome, 0.35f)
    }
}

/**
 * 3. LAYAR PAKET INTERNET (HOTSPOT PREPAID, PPPOE POSTPAID NON-HIDDEN, SALDO) & BANDWIDTH
 */
@Composable
fun AdminPlansScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("Hotspot") } // Hotspot | PPPoE | Balance | Bandwidth
    var plansList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var bwList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routersList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var poolsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<JSONObject?>(null) }
    var showBwDialog by remember { mutableStateOf(false) }

    fun loadPlansData() {
        scope.launch {
            val res = ApiClient.request("admin_plans")
            if (res.status && res.data != null) {
                val pArr = res.data.optJSONArray("plans") ?: JSONArray()
                val pTemp = mutableListOf<JSONObject>()
                for (i in 0 until pArr.length()) pTemp.add(pArr.getJSONObject(i))
                plansList = pTemp

                val bArr = res.data.optJSONArray("bandwidths") ?: JSONArray()
                val bTemp = mutableListOf<JSONObject>()
                for (i in 0 until bArr.length()) bTemp.add(bArr.getJSONObject(i))
                bwList = bTemp

                val rArr = res.data.optJSONArray("routers") ?: JSONArray()
                val rTemp = mutableListOf<JSONObject>()
                for (i in 0 until rArr.length()) rTemp.add(rArr.getJSONObject(i))
                routersList = rTemp

                val poolArr = res.data.optJSONArray("pools") ?: JSONArray()
                val poolTemp = mutableListOf<JSONObject>()
                for (i in 0 until poolArr.length()) poolTemp.add(poolArr.getJSONObject(i))
                poolsList = poolTemp
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPlansData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Paket Internet & Bandwidth", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = {
                    if (currentTab == "Bandwidth") {
                        showBwDialog = true
                    } else {
                        editingPlan = null
                        showPlanDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (currentTab == "Bandwidth") "+Bandwidth" else "+Paket", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("Hotspot", "PPPoE", "Balance", "Bandwidth").forEach { tab ->
                FilterChip(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    label = { Text(tab, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentTab == "Bandwidth") {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bwList) { bw ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(bw.optString("name_bw"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Download: ${bw.optInt("rate_down")} ${bw.optString("rate_down_unit")} • Upload: ${bw.optInt("rate_up")} ${bw.optString("rate_up_unit")}",
                                    fontSize = 12.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        ApiClient.request("admin_bandwidth_delete", mapOf("id" to bw.optInt("id")))
                                        loadPlansData()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                            }
                        }
                    }
                }
            }
        } else {
            val filteredPlans = plansList.filter { it.optString("type").equals(currentTab, ignoreCase = true) }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlans) { p ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "${p.optString("rate_label")} • Masa: ${p.optInt("validity")} ${p.optString("validity_unit")}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    "${p.optString("billing_label")} • Router: ${p.optString("routers")} • User Aktif: ${p.optInt("active_users")}",
                                    fontSize = 11.sp,
                                    color = ArnetaGreenSuccess
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatRupiah(p.optDouble("price", 0.0)),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaGoldPrimary,
                                    fontSize = 14.sp
                                )
                                Row {
                                    IconButton(onClick = {
                                        editingPlan = p
                                        showPlanDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, tint = ArnetaCyanIncome)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            ApiClient.request("admin_plan_delete", mapOf("id" to p.optInt("id")))
                                            loadPlansData()
                                        }
                                    }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlanDialog) {
        PlanFormDialog(
            initial = editingPlan,
            defaultType = if (currentTab == "Bandwidth") "Hotspot" else currentTab,
            bandwidths = bwList,
            routers = routersList,
            pools = poolsList,
            onDismiss = { showPlanDialog = false },
            onSaved = {
                showPlanDialog = false
                loadPlansData()
            }
        )
    }

    if (showBwDialog) {
        BandwidthFormDialog(
            onDismiss = { showBwDialog = false },
            onSaved = {
                showBwDialog = false
                loadPlansData()
            }
        )
    }
}

/**
 * 4. PETA PELANGGAN, ROUTER COVERAGE & ODP (LEAFLET OSM / SATELIT HYBRID DENGAN BASE64 & PIN NAMA)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapScreen() {
    var customersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assetsGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mapHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var mapWebViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val res = ApiClient.request("admin_map_data")
        if (res.status && res.data != null) {
            val cArr = res.data.optJSONArray("customers") ?: JSONArray()
            val rArr = res.data.optJSONArray("routers") ?: JSONArray()
            val aArr = res.data.optJSONArray("assets") ?: JSONArray()

            val cList = mutableListOf<JSONObject>()
            for (i in 0 until cArr.length()) cList.add(cArr.getJSONObject(i))
            customersGeo = cList

            val rList = mutableListOf<JSONObject>()
            for (i in 0 until rArr.length()) rList.add(rArr.getJSONObject(i))
            routersGeo = rList

            val aList = mutableListOf<JSONObject>()
            for (i in 0 until aArr.length()) aList.add(aArr.getJSONObject(i))
            assetsGeo = aList

            val cBase64 = android.util.Base64.encodeToString(
                cArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val rBase64 = android.util.Base64.encodeToString(
                rArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val aBase64 = android.util.Base64.encodeToString(
                aArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            val initLat = rList.firstOrNull()?.optDouble("lat")
                ?: cList.firstOrNull()?.optDouble("lat")
                ?: -6.200000
            val initLng = rList.firstOrNull()?.optDouble("lng")
                ?: cList.firstOrNull()?.optDouble("lng")
                ?: 106.816666

            mapHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css" />
                    <script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        html, body { width: 100%; height: 100vh; overflow: hidden; background: #0B1120; font-family: sans-serif; }
                        #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; width: 100%; height: 100vh; z-index: 1; }
                        .pin-wrap { display: flex; flex-direction: column; align-items: center; transform: translate(-50%, -100%); }
                        .pin-label { background: rgba(15, 23, 42, 0.92); color: #FFFFFF; padding: 2px 7px; border-radius: 6px; font-size: 11px; font-weight: bold; white-space: nowrap; box-shadow: 0 2px 5px rgba(0,0,0,0.45); margin-bottom: 3px; border: 1px solid rgba(255,255,255,0.25); }
                        .pin-label-router { background: #F59E0B; color: #0B0F19; border: 1.5px solid #FFFFFF; font-size: 11.5px; }
                        .pin-dot { width: 26px; height: 26px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); display: flex; align-items: center; justify-content: center; border: 2px solid #FFFFFF; box-shadow: 0 3px 6px rgba(0,0,0,0.5); }
                        .pin-dot span { transform: rotate(45deg); font-size: 12px; }
                        .leaflet-popup-content-wrapper { border-radius: 12px; font-size: 12.5px; line-height: 1.45; }
                        .btn-dir { display: inline-block; margin-top: 6px; padding: 5px 10px; background: #00C0EF; color: #FFFFFF !important; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 11.5px; }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        function decodeB64(b64) {
                            try {
                                return JSON.parse(decodeURIComponent(Array.prototype.map.call(window.atob(b64), function(c) {
                                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                                }).join('')));
                            } catch (e) { return []; }
                        }

                        var osmStreet = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20, attribution: '© OpenStreetMap • Arneta.ID' });
                        var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 20, attribution: '© Carto • Arneta.ID' });
                        var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20, attribution: '© Google Hybrid • Arneta.ID' });

                        var map = L.map('map', { center: [$initLat, $initLng], zoom: 16, layers: [osmStreet], zoomControl: true });
                        L.control.layers({ "Peta Jalan Terang": cartoVoyager, "OpenStreetMap": osmStreet, "Satelit Hybrid": googleHybrid }).addTo(map);

                        var customers = decodeB64("$cBase64");
                        var routers   = decodeB64("$rBase64");
                        var assets    = decodeB64("$aBase64");

                        var routerBounds = [];
                        var customerBounds = [];
                        var assetBounds = [];
                        var allBounds = [];

                        function makePinIcon(label, bgColor, emoji, isRouter) {
                            var lblClass = isRouter ? "pin-label pin-label-router" : "pin-label";
                            var html = '<div class="pin-wrap">' +
                                       '<div class="' + lblClass + '">' + label + '</div>' +
                                       '<div class="pin-dot" style="background:' + bgColor + ';"><span>' + emoji + '</span></div>' +
                                       '</div>';
                            return L.divIcon({ className: '', html: html, iconSize: [0, 0], iconAnchor: [0, 0], popupAnchor: [0, -34] });
                        }

                        routers.forEach(function(r) {
                            var lat = parseFloat(r.lat); var lng = parseFloat(r.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            routerBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            var cov = parseInt(r.coverage) || 350;
                            L.circle([lat, lng], { radius: cov, color: '#00C0EF', weight: 2, fillColor: '#00C0EF', fillOpacity: 0.14 }).addTo(map);
                            L.marker([lat, lng], { icon: makePinIcon('📡 ' + (r.name || 'Router'), '#F59E0B', '📡', true), zIndexOffset: 1000 })
                             .addTo(map)
                             .bindPopup('<b>📡 Router: ' + (r.name || '-') + '</b><br>IP/API: ' + (r.ip_address || '-') + '<br>Coverage: ' + cov + ' Meter<br><a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>');
                        });

                        customers.forEach(function(c) {
                            var lat = parseFloat(c.lat); var lng = parseFloat(c.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            customerBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            var isOn = (c.plan_status === 'on');
                            var pinColor = isOn ? '#10B981' : '#EF4444';
                            var statusBadge = isOn ? '<span style="color:#10B981;font-weight:bold;">AKTIF (ON)</span>' : '<span style="color:#EF4444;font-weight:bold;">EXPIRED (OFF)</span>';
                            var displayName = c.fullname || c.username || 'User';
                            L.marker([lat, lng], { icon: makePinIcon(displayName, pinColor, '👤', false) })
                             .addTo(map)
                             .bindPopup('<b>👤 ' + displayName + '</b> (@' + (c.username || '-') + ')<br>Status: ' + statusBadge + '<br>Paket: <b>' + (c.plan_name || '-') + '</b><br>Exp: ' + (c.expiration || '-') + '<br>WA: ' + (c.phonenumber || '-') + '<br><a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>');
                        });

                        assets.forEach(function(a) {
                            var lat = parseFloat(a.lat); var lng = parseFloat(a.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            assetBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            L.marker([lat, lng], { icon: makePinIcon('📦 ' + (a.name || 'ODP'), '#8B5CF6', '📦', false) })
                             .addTo(map)
                             .bindPopup('<b>📦 ' + (a.name || '-') + '</b> (' + (a.category || '-') + ')<br>Kondisi: ' + (a.condition || 'Good') + '<br><a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>');
                        });

                        window.focusMapTarget = function(target) {
                            map.invalidateSize();
                            if (target === 'router' && routerBounds.length > 0) map.setView(routerBounds[0], 16);
                            else if (target === 'customers' && customerBounds.length > 0) map.fitBounds(customerBounds, { padding: [45, 45], maxZoom: 17 });
                            else if (target === 'assets' && assetBounds.length > 0) map.fitBounds(assetBounds, { padding: [45, 45], maxZoom: 17 });
                            else if (target === 'all' && allBounds.length > 0) map.fitBounds(allBounds, { padding: [45, 45], maxZoom: 16 });
                        };

                        setTimeout(function() {
                            map.invalidateSize();
                            if (routerBounds.length > 0) map.setView(routerBounds[0], 16);
                            else if (allBounds.length > 0) map.fitBounds(allBounds, { padding: [40, 40], maxZoom: 16 });
                        }, 300);
                    </script>
                </body>
                </html>
            """.trimIndent()
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 84.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('router');", null) },
                label = { Text("🟡 Fokus Router (${routersGeo.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('customers');", null) },
                label = {
                    val activeCnt = customersGeo.count { it.optString("plan_status") == "on" }
                    val expCnt = customersGeo.count { it.optString("plan_status") != "on" }
                    Text("🟢 Aktif ($activeCnt) • 🔴 Exp ($expCnt)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('assets');", null) },
                label = { Text("📦 Aset/ODP (${assetsGeo.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('all');", null) },
                label = { Text("🗺️ Lihat Semua", fontSize = 11.5.sp) }
            )
        }

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setGeolocationEnabled(true)
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 15; ArnetaAdminApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.contains("google.com/maps") || url.startsWith("geo:")) {
                                    try {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Aplikasi Maps tidak ditemukan.", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        tag = ""
                        mapWebViewRef = this
                    }
                },
                update = { webView ->
                    mapWebViewRef = webView
                    if (mapHtml.isNotEmpty() && webView.tag != mapHtml) {
                        webView.tag = mapHtml
                        webView.loadDataWithBaseURL(ApiClient.baseUrl, mapHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * 5. ASSET MANAGER (ODP, ODC, ONT/MODEM, ROUTER, KABEL)
 */
@Composable
fun AdminAssetsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assetList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var totalValue by remember { mutableDoubleStateOf(0.0) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun loadAssets() {
        scope.launch {
            val res = ApiClient.request("admin_assets")
            if (res.status && res.data != null) {
                totalValue = res.data.optJSONObject("summary")?.optDouble("total_value", 0.0) ?: 0.0
                val arr = res.data.optJSONArray("assets") ?: res.dataArray ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                assetList = list
            }
        }
    }

    LaunchedEffect(Unit) { loadAssets() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ArnetaIndigoAccent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    Text("Total Nilai Inventaris Aset", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    Text(formatRupiah(totalValue), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ArnetaGoldLight)
                    Text("${assetList.size} Unit Perangkat Terdaftar", fontSize = 11.sp, color = Color.White)
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Tambah Aset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(assetList) { a ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.optString("name")} (${a.optString("category")})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("SN/Merk: ${a.optString("serial_number")} ${a.optString("brand")} • Kondisi: ${a.optString("condition_status")}", fontSize = 12.sp)
                            Text("Nilai: ${formatRupiah(a.optDouble("cost", 0.0))}", fontSize = 12.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                ApiClient.request("admin_asset_delete", mapOf("id" to a.optInt("id")))
                                loadAssets()
                            }
                        }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("ODP/ODC") }
        var serial by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("") }
        var coords by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Inventaris Asset", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Perangkat / ODP") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (ODP, ONT, Router, Kabel)") }, singleLine = true)
                    OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number / Merk") }, singleLine = true)
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Harga Perolehan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = coords, onValueChange = { coords = it }, label = { Text("Koordinat Peta (Lat, Lng)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_asset_save",
                            mapOf(
                                "name" to name,
                                "category" to category,
                                "serial_number" to serial,
                                "cost" to (cost.toDoubleOrNull() ?: 0.0),
                                "coordinates" to coords
                            )
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) {
                            showAddDialog = false
                            loadAssets()
                        }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun RouterFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.optString("name") ?: "") }
    var ip by remember { mutableStateOf(initial?.optString("ip_address") ?: "") }
    var user by remember { mutableStateOf(initial?.optString("username") ?: "admin") }
    var pass by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }
    var coverage by remember { mutableStateOf(initial?.optString("coverage") ?: "350") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Router MikroTik" else "Tambah Router MikroTik", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Router") }, singleLine = true)
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP Address:Port API") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username API") }, singleLine = true)
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password API") }, singleLine = true)
                OutlinedTextField(value = coords, onValueChange = { coords = it }, label = { Text("Koordinat (Lat, Lng)") }, singleLine = true)
                OutlinedTextField(value = coverage, onValueChange = { coverage = it }, label = { Text("Radius Coverage (Meter)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val res = ApiClient.request(
                        "admin_router_save",
                        mapOf(
                            "id" to (initial?.optInt("id") ?: 0),
                            "name" to name,
                            "ip_address" to ip,
                            "username" to user,
                            "password" to pass,
                            "coordinates" to coords,
                            "coverage" to coverage
                        )
                    )
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    if (res.status) onSaved()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun PoolFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var poolName by remember { mutableStateOf(initial?.optString("pool_name") ?: "") }
    var rangeIp by remember { mutableStateOf(initial?.optString("range_ip") ?: "192.168.10.2-192.168.10.254") }
    var routers by remember { mutableStateOf(initial?.optString("routers") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah / Edit IP Pool", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = poolName, onValueChange = { poolName = it }, label = { Text("Nama Pool") }, singleLine = true)
                OutlinedTextField(value = rangeIp, onValueChange = { rangeIp = it }, label = { Text("Rentang IP") }, singleLine = true)
                OutlinedTextField(value = routers, onValueChange = { routers = it }, label = { Text("Nama Router") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val res = ApiClient.request(
                        "admin_pool_save",
                        mapOf("id" to (initial?.optInt("id") ?: 0), "pool_name" to poolName, "range_ip" to rangeIp, "routers" to routers)
                    )
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    if (res.status) onSaved()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun PlanFormDialog(
    initial: JSONObject?,
    defaultType: String,
    bandwidths: List<JSONObject>,
    routers: List<JSONObject>,
    pools: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var namePlan by remember { mutableStateOf(initial?.optString("name_plan") ?: "") }
    var price by remember { mutableStateOf(initial?.optDouble("price", 0.0)?.toInt()?.toString() ?: "") }
    var validity by remember { mutableStateOf(initial?.optInt("validity", 30)?.toString() ?: "30") }
    var validityUnit by remember { mutableStateOf(initial?.optString("validity_unit") ?: "Days") }
    var routerName by remember { mutableStateOf(initial?.optString("routers") ?: (routers.firstOrNull()?.optString("name") ?: "radius")) }
    var bwId by remember { mutableIntStateOf(initial?.optInt("id_bw") ?: (bandwidths.firstOrNull()?.optInt("id") ?: 1)) }
    var poolName by remember { mutableStateOf(initial?.optString("pool") ?: (pools.firstOrNull()?.optString("pool_name") ?: "")) }
    // Default: PPPoE = Postpaid ('no'), Hotspot = Prepaid ('yes')
    var isPrepaid by remember { mutableStateOf(initial?.optString("prepaid") ?: if (defaultType == "PPPoE") "no" else "yes") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paket $defaultType", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = namePlan, onValueChange = { namePlan = it }, label = { Text("Nama Paket") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Harga Paket (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = validity, onValueChange = { validity = it }, label = { Text("Masa Aktif") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = validityUnit, onValueChange = { validityUnit = it }, label = { Text("Unit (Days/Mins)") }, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isPrepaid == "yes",
                        onClick = { isPrepaid = "yes" },
                        label = { Text("Prabayar (Prepaid)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = isPrepaid == "no",
                        onClick = { isPrepaid = "no" },
                        label = { Text("Pascabayar (Postpaid)", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(value = routerName, onValueChange = { routerName = it }, label = { Text("Nama Router") }, singleLine = true)
                if (defaultType == "PPPoE") {
                    OutlinedTextField(value = poolName, onValueChange = { poolName = it }, label = { Text("Nama IP Pool") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val res = ApiClient.request(
                        "admin_plan_save",
                        mapOf(
                            "id" to (initial?.optInt("id") ?: 0),
                            "name_plan" to namePlan,
                            "type" to defaultType,
                            "price" to (price.toDoubleOrNull() ?: 0.0),
                            "validity" to (validity.toIntOrNull() ?: 30),
                            "validity_unit" to validityUnit,
                            "routers" to routerName,
                            "id_bw" to bwId,
                            "pool" to poolName,
                            "prepaid" to isPrepaid
                        )
                    )
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    if (res.status) onSaved()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun BandwidthFormDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nameBw by remember { mutableStateOf("") }
    var rateDown by remember { mutableStateOf("10") }
    var rateUp by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf("Mbps") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Profil Bandwidth", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nameBw, onValueChange = { nameBw = it }, label = { Text("Nama Profil") }, singleLine = true)
                OutlinedTextField(value = rateDown, onValueChange = { rateDown = it }, label = { Text("Rate Download") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = rateUp, onValueChange = { rateUp = it }, label = { Text("Rate Upload") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Satuan (Mbps / Kbps)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val res = ApiClient.request(
                        "admin_bandwidth_save",
                        mapOf("name_bw" to nameBw, "rate_down" to rateDown, "rate_up" to rateUp, "rate_down_unit" to unit, "rate_up_unit" to unit)
                    )
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    if (res.status) onSaved()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
