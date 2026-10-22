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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlin.math.max

/**
 * 1. IN-APP BROWSER VIEW INTERNAL (Tanpa Keluar Aplikasi / Tanpa Buka Chrome)
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
                        "admin" to "Login Admin (?_route=admin)",
                        "dashboard" to "Dasbor Web (?_route=dashboard)",
                        "pluginmanager" to "Plugin Manager",
                        "paymentgateway" to "Payment Gateway",
                        "radius" to "FreeRADIUS",
                        "pages/Order_Voucher" to "Template Voucher",
                        "community" to "Info & Update"
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

/**
 * 2. LAYAR JARINGAN, LIVE SPEED MONITOR MIKROTIK (MBPS UPLOAD/DOWNLOAD MULUS) & USER ONLINE
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
    var isLoading by remember { mutableStateOf(false) }

    // Filter di Tab User Online
    var onlineSearchQuery by remember { mutableStateOf("") }
    var onlineServiceFilter by remember { mutableStateOf("All") } // All | Hotspot | PPPoE

    // State Kecepatan Internet Real-Time (Download & Upload dalam Mbps)
    var targetDownloadMbps by remember { mutableFloatStateOf(0f) }
    var targetUploadMbps by remember { mutableFloatStateOf(0f) }
    var downloadLabel by remember { mutableStateOf("0 Kbps") }
    var uploadLabel by remember { mutableStateOf("0 Kbps") }
    var maxCpuLoad by remember { mutableFloatStateOf(0f) }
    var avgRamPercent by remember { mutableFloatStateOf(0f) }
    var avgPingMs by remember { mutableFloatStateOf(0f) }

    // Riwayat 30 titik untuk pergerakan gelombang mulus (Smooth Bezier Spline)
    var downloadWavePoints by remember { mutableStateOf(List(28) { 0f }) }
    var uploadWavePoints by remember { mutableStateOf(List(28) { 0f }) }
    var lastUpdatedTime by remember { mutableStateOf("Baru saja") }

    var showRouterDialog by remember { mutableStateOf(false) }
    var editingRouter by remember { mutableStateOf<JSONObject?>(null) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var editingPool by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun fetchLiveRouterStatus() {
        val res = ApiClient.request("admin_router_status")
        val arr = res.dataArray ?: res.data?.optJSONArray("routers") ?: JSONArray()
        if (res.status && arr.length() > 0) {
            val list = mutableListOf<JSONObject>()
            var sumDlMbps = 0f
            var sumUlMbps = 0f
            var sumRxBps = 0L
            var sumTxBps = 0L
            var peakCpu = 0f
            var sumRam = 0f
            var sumPing = 0f
            var onlineCount = 0

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(obj)
                if (obj.optBoolean("online", false)) {
                    onlineCount++
                    sumDlMbps += obj.optDouble("download_mbps", 0.0).toFloat()
                    sumUlMbps += obj.optDouble("upload_mbps", 0.0).toFloat()
                    sumRxBps += obj.optLong("rx_bps", 0L)
                    sumTxBps += obj.optLong("tx_bps", 0L)
                    val c = obj.optDouble("cpu_load", 0.0).toFloat()
                    if (c > peakCpu) peakCpu = c
                    sumRam += obj.optDouble("ram_percent", 0.0).toFloat()
                    sumPing += obj.optDouble("ping_ms", 0.0).toFloat()
                }
            }
            routerStatusList = list
            if (onlineCount > 0) {
                targetDownloadMbps = sumDlMbps
                targetUploadMbps = sumUlMbps
                downloadLabel = formatSpeedBps(sumRxBps)
                uploadLabel = formatSpeedBps(sumTxBps)
                maxCpuLoad = peakCpu
                avgRamPercent = sumRam / onlineCount
                avgPingMs = sumPing / onlineCount
            }
            val cal = java.util.Calendar.getInstance()
            lastUpdatedTime = String.format(
                "%02d:%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND)
            )
        }
    }

    suspend fun fetchOnlineUsers() {
        val res = ApiClient.request("admin_online_users")
        val arr = res.data?.optJSONArray("users")
            ?: res.data?.optJSONArray("items")
            ?: res.dataArray
            ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            list.add(arr.getJSONObject(i))
        }
        onlineUsersList = list
    }

    fun loadTabContent() {
        scope.launch {
            isLoading = true
            when (currentTab) {
                "live_monitor" -> {
                    fetchLiveRouterStatus()
                    if (onlineUsersList.isEmpty()) fetchOnlineUsers()
                }
                "online_users" -> fetchOnlineUsers()
                "routers" -> {
                    val res = ApiClient.request("admin_routers")
                    val arr = res.dataArray ?: res.data?.optJSONArray("routers") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    routerList = list
                }
                "pools" -> {
                    val res = ApiClient.request("admin_pools")
                    val arr = res.dataArray ?: res.data?.optJSONArray("pools") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    poolList = list
                }
            }
            isLoading = false
        }
    }

    // Engine 1: Polling Data MikroTik tiap 2 detik
    LaunchedEffect(currentTab, isLivePolling) {
        loadTabContent()
        if (isLivePolling && (currentTab == "live_monitor" || currentTab == "online_users")) {
            while (isActive && isLivePolling) {
                delay(2000L)
                if (currentTab == "live_monitor") {
                    fetchLiveRouterStatus()
                } else if (currentTab == "online_users") {
                    fetchOnlineUsers()
                }
            }
        }
    }

    // Engine 2: Animator Gelombang Halus (Berjalan tiap 250ms untuk menginterpolasi naik-turun speed secara mulus)
    LaunchedEffect(isLivePolling, currentTab) {
        while (isActive && isLivePolling && currentTab == "live_monitor") {
            delay(250L)
            val prevDl = downloadWavePoints.lastOrNull() ?: 0f
            val prevUl = uploadWavePoints.lastOrNull() ?: 0f

            // Interpolasi halus menuju target speed + fluktuasi mikro alami saat ada trafik aktif
            val microJitterDl = if (targetDownloadMbps > 0.05f) ((Math.random().toFloat() - 0.5f) * targetDownloadMbps * 0.08f) else 0f
            val microJitterUl = if (targetUploadMbps > 0.05f) ((Math.random().toFloat() - 0.5f) * targetUploadMbps * 0.08f) else 0f

            val nextDl = (prevDl + (targetDownloadMbps - prevDl) * 0.35f + microJitterDl).coerceAtLeast(0f)
            val nextUl = (prevUl + (targetUploadMbps - prevUl) * 0.35f + microJitterUl).coerceAtLeast(0f)

            downloadWavePoints = (downloadWavePoints.drop(1) + nextDl)
            uploadWavePoints = (uploadWavePoints.drop(1) + nextUl)
        }
    }

    // Filter Instan 1 Huruf pada Daftar User Online
    val filteredOnlineUsers = remember(onlineUsersList, onlineSearchQuery, onlineServiceFilter) {
        val q = onlineSearchQuery.trim().lowercase()
        onlineUsersList.filter { u ->
            val matchService = onlineServiceFilter == "All" || u.optString("service", "").equals(onlineServiceFilter, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                u.optString("username", "").lowercase().contains(q) ||
                u.optString("ip_address", "").lowercase().contains(q) ||
                u.optString("mac_address", "").lowercase().contains(q) ||
                u.optString("router", "").lowercase().contains(q)
            matchService && matchQuery
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
                "live_monitor" to "Live Speed MikroTik",
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
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
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
                                                text = "Live Traffic Speed & Telemetry",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Real-time stream • Update: $lastUpdatedTime",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isLivePolling,
                                        onCheckedChange = { isLivePolling = it }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // KOMPONEN BAR & GELOMBANG SPEED INTERNET MULUS (DOWNLOAD & UPLOAD MBPS)
                                SmoothSpeedTrafficMonitor(
                                    downloadPoints = downloadWavePoints,
                                    uploadPoints = uploadWavePoints,
                                    downloadLabel = downloadLabel,
                                    uploadLabel = uploadLabel,
                                    targetDlMbps = targetDownloadMbps,
                                    targetUlMbps = targetUploadMbps,
                                    cpuLoad = maxCpuLoad,
                                    ramPercent = avgRamPercent,
                                    pingMs = avgPingMs,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Daftar Kartu Resource per Router + Speed Spesifik Router
                    items(routerStatusList) { r ->
                        val online = r.optBoolean("online", false)
                        val cpu = r.optInt("cpu_load", 0)
                        val ramPct = r.optDouble("ram_percent", 0.0).toFloat()
                        val animCpu by animateFloatAsState(
                            targetValue = (cpu / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                            label = "r_cpu"
                        )
                        val animRam by animateFloatAsState(
                            targetValue = (ramPct / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                            label = "r_ram"
                        )

                        Card(
                            shape = RoundedCornerShape(16.dp),
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
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "IP: ${r.optString("ip_address")} • RouterOS ${r.optString("version")}",
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

                                // Info Speed Download & Upload per Router
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "DL: ${r.optString("download_speed_label", "0 Kbps")}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = ArnetaCyanIncome
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "UL: ${r.optString("upload_speed_label", "0 Kbps")}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = ArnetaGoldPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("CPU Load: $cpu%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animCpu },
                                    color = if (cpu > 80) ArnetaRedDanger else ArnetaCyanIncome,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "RAM Terpakai: $ramPct% (Free: ${r.optString("free_memory")} / Total: ${r.optString("total_memory")})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animRam },
                                    color = ArnetaGoldPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Uptime: ${r.optString("uptime")} | HS: ${r.optInt("hotspot_online")} | PPPoE: ${r.optInt("pppoe_online")}",
                                        fontSize = 11.sp,
                                        color = ArnetaGreenSuccess,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { currentTab = "online_users" }
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
                // Kolom Pencarian 1 Huruf & Filter Layanan User Online
                OutlinedTextField(
                    value = onlineSearchQuery,
                    onValueChange = { onlineSearchQuery = it },
                    placeholder = { Text("Ketik 1 huruf username / IP / MAC...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        if (onlineSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onlineSearchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All" to "Semua (${onlineUsersList.size})", "Hotspot" to "Hotspot", "PPPoE" to "PPPoE").forEach { (k, lbl) ->
                            FilterChip(
                                selected = onlineServiceFilter == k,
                                onClick = { onlineServiceFilter = k },
                                label = { Text(lbl, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    IconButton(onClick = { loadTabContent() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isLoading && onlineUsersList.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (filteredOnlineUsers.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (onlineUsersList.isEmpty()) "Belum ada sesi Hotspot / PPPoE yang sedang aktif di MikroTik."
                            else "Tidak ada user online yang cocok dengan \"$onlineSearchQuery\".",
                            fontSize = 13.sp
                        )
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

private fun formatSpeedBps(bps: Long): String {
    if (bps <= 0L) return "0 Kbps"
    if (bps >= 1_000_000L) {
        return String.format("%.2f Mbps", bps / 1_000_000f)
    }
    if (bps >= 1_000L) {
        return String.format("%.1f Kbps", bps / 1_000f)
    }
    return "$bps bps"
}

/**
 * KOMPONEN SPEEDOMETER BAR & GELOMBANG TRAFIK MULUS (DOWNLOAD & UPLOAD MBPS REAL-TIME)
 */
@Composable
private fun SmoothSpeedTrafficMonitor(
    downloadPoints: List<Float>,
    uploadPoints: List<Float>,
    downloadLabel: String,
    uploadLabel: String,
    targetDlMbps: Float,
    targetUlMbps: Float,
    cpuLoad: Float,
    ramPercent: Float,
    pingMs: Float,
    modifier: Modifier = Modifier
) {
    // Skala maksimum dinamis (minimal 10 Mbps, otomatis naik jika trafik > 10 Mbps)
    val peakMbps = remember(downloadPoints, uploadPoints, targetDlMbps, targetUlMbps) {
        val maxDl = downloadPoints.maxOrNull() ?: 0f
        val maxUl = uploadPoints.maxOrNull() ?: 0f
        max(10f, max(max(maxDl, maxUl), max(targetDlMbps, targetUlMbps)) * 1.25f)
    }

    // Animasi mulus 60 FPS untuk bar kecepatan Download & Upload
    val smoothDlRatio by animateFloatAsState(
        targetValue = (targetDlMbps / peakMbps).coerceIn(0.02f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "dl_bar"
    )
    val smoothUlRatio by animateFloatAsState(
        targetValue = (targetUlMbps / peakMbps).coerceIn(0.02f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ul_bar"
    )

    Column(modifier = modifier) {
        // 2 Kartu Speedometer Berdampingan (Download & Upload dengan Bar yang Bergerak Mulus)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Kotak Download Speed
            Surface(
                color = Color(0xFF0B1120),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaCyanIncome.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download (RX)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = ArnetaCyanIncome
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { smoothDlRatio },
                        color = ArnetaCyanIncome,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            // Kotak Upload Speed
            Surface(
                color = Color(0xFF0B1120),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Upload, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload (TX)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uploadLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = ArnetaGoldPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { smoothUlRatio },
                        color = ArnetaGoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grafik Gelombang Cubic Bezier (Mulus seperti Traffic Monitor WinBox)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1120))
                .padding(10.dp)
        ) {
            val w = size.width
            val h = size.height

            for (i in 0..4) {
                val y = h * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            fun drawSmoothBezierWave(points: List<Float>, strokeColor: Color, fillAlpha: Float) {
                if (points.size < 2) return
                val strokePath = Path()
                val fillPath = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                val coords = points.mapIndexed { idx, v ->
                    val x = idx * stepX
                    val ratio = (v / peakMbps).coerceIn(0f, 1f)
                    val y = h - (ratio * (h * 0.88f)) - 4f
                    Offset(x, y)
                }

                strokePath.moveTo(coords.first().x, coords.first().y)
                fillPath.moveTo(coords.first().x, h)
                fillPath.lineTo(coords.first().x, coords.first().y)

                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val controlX1 = (p0.x + p1.x) / 2f
                    val controlY1 = p0.y
                    val controlX2 = (p0.x + p1.x) / 2f
                    val controlY2 = p1.y

                    strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                    fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                }

                fillPath.lineTo(coords.last().x, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = fillAlpha), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = strokeColor,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )
            }

            drawSmoothBezierWave(uploadPoints, ArnetaGoldPrimary, 0.22f)
            drawSmoothBezierWave(downloadPoints, ArnetaCyanIncome, 0.35f)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Baris Ringkasan CPU, RAM, Ping & Skala Puncak Grafik
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CPU: ${cpuLoad.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
            Text("RAM: ${ramPercent.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
            Text("Ping: ${pingMs.toInt()} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaGreenSuccess)
            Text("Skala: ${String.format("%.0f", peakMbps)} Mbps", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

/**
 * 3. LAYAR PAKET INTERNET (HOTSPOT NON-POSTPAID, PPPOE POSTPAID NON-HIDDEN, SALDO) & BANDWIDTH
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
    var isLoading by remember { mutableStateOf(false) }

    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<JSONObject?>(null) }
    var showBwDialog by remember { mutableStateOf(false) }

    fun loadPlansData() {
        isLoading = true
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
            isLoading = false
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
            val tabItems = listOf(
                "Hotspot" to "Hotspot (Prepaid)",
                "PPPoE" to "PPPoE (Postpaid)",
                "Balance" to "Paket Saldo",
                "Bandwidth" to "Bandwidth (${bwList.size})"
            )
            tabItems.forEach { (tabKey, tabLabel) ->
                FilterChip(
                    selected = currentTab == tabKey,
                    onClick = { currentTab = tabKey },
                    label = { Text(tabLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else if (currentTab == "Bandwidth") {
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
            if (filteredPlans.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Belum ada paket $currentTab yang aktif.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlans) { p ->
                        val isPostpaid = p.optString("prepaid", "yes").equals("no", ignoreCase = true)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (isPostpaid) ArnetaOrangeActive.copy(alpha = 0.2f) else ArnetaCyanIncome.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isPostpaid) "Postpaid" else "Prepaid",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPostpaid) ArnetaOrangeActive else ArnetaCyanIncome,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        "${p.optString("rate_label")} • Masa Aktif: ${p.optInt("validity")} ${p.optString("validity_unit")}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        "Router: ${p.optString("routers")} • Pelanggan Aktif: ${p.optInt("active_users")}",
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
 * 4. LAYAR PETA PELANGGAN, ROUTER COVERAGE & ODP INTERAKTIF (Full Marker + Nama + Auto-Focus Router)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapScreen() {
    val context = LocalContext.current
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
                        .pin-label {
                            background: rgba(15, 23, 42, 0.92); color: #FFFFFF; padding: 2px 7px; border-radius: 6px;
                            font-size: 11px; font-weight: bold; white-space: nowrap; box-shadow: 0 2px 5px rgba(0,0,0,0.45);
                            margin-bottom: 3px; border: 1px solid rgba(255,255,255,0.25);
                        }
                        .pin-label-router { background: #F59E0B; color: #0B0F19; border: 1.5px solid #FFFFFF; font-size: 11.5px; }
                        .pin-dot {
                            width: 26px; height: 26px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg);
                            display: flex; align-items: center; justify-content: center; border: 2px solid #FFFFFF;
                            box-shadow: 0 3px 6px rgba(0,0,0,0.5);
                        }
                        .pin-dot span { transform: rotate(45deg); font-size: 12px; }
                        .leaflet-popup-content-wrapper { border-radius: 12px; font-size: 12.5px; line-height: 1.45; }
                        .btn-dir {
                            display: inline-block; margin-top: 6px; padding: 5px 10px; background: #00C0EF;
                            color: #FFFFFF !important; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 11.5px;
                        }
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

                        var osmStreet = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20, attribution: '© OpenStreetMap' });
                        var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 20, attribution: '© Carto' });
                        var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20, attribution: '© Google Hybrid' });

                        var map = L.map('map', { center: [$initLat, $initLng], zoom: 16, layers: [osmStreet], zoomControl: true });
                        L.control.layers({
                            "OpenStreetMap (Default Web)": osmStreet,
                            "Peta Jalan Terang": cartoVoyager,
                            "Satelit Hybrid (Atap Rumah)": googleHybrid
                        }).addTo(map);

                        var customers = decodeB64("$cBase64");
                        var routers   = decodeB64("$rBase64");
                        var assets    = decodeB64("$aBase64");

                        var routerBounds = []; var customerBounds = []; var assetBounds = []; var allBounds = [];

                        function makePinIcon(label, bgColor, emoji, isRouter) {
                            var lblClass = isRouter ? "pin-label pin-label-router" : "pin-label";
                            var html = '<div class="pin-wrap"><div class="' + lblClass + '">' + label + '</div>' +
                                       '<div class="pin-dot" style="background:' + bgColor + ';"><span>' + emoji + '</span></div></div>';
                            return L.divIcon({ className: '', html: html, iconSize: [0, 0], iconAnchor: [0, 0], popupAnchor: [0, -34] });
                        }

                        routers.forEach(function(r) {
                            var lat = parseFloat(r.lat); var lng = parseFloat(r.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            routerBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            var cov = parseInt(r.coverage) || 350;
                            L.circle([lat, lng], { radius: cov, color: '#00C0EF', weight: 2, fillColor: '#00C0EF', fillOpacity: 0.14 }).addTo(map);
                            var icon = makePinIcon('📡 ' + (r.name || 'Router'), '#F59E0B', '📡', true);
                            L.marker([lat, lng], { icon: icon, zIndexOffset: 1000 }).addTo(map).bindPopup(
                                '<b>📡 Router: ' + (r.name || '-') + '</b><br>IP/API: ' + (r.ip_address || '-') + '<br>Radius: ' + cov + 'm<br>' +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                            );
                        });

                        customers.forEach(function(c) {
                            var lat = parseFloat(c.lat); var lng = parseFloat(c.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            customerBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            var isOn = (c.plan_status === 'on');
                            var pinColor = isOn ? '#10B981' : '#EF4444';
                            var statusBadge = isOn ? '<span style="color:#10B981;font-weight:bold;">AKTIF (ON)</span>' : '<span style="color:#EF4444;font-weight:bold;">EXPIRED (OFF)</span>';
                            var displayName = c.fullname || c.username || 'User';
                            var icon = makePinIcon(displayName, pinColor, '👤', false);
                            L.marker([lat, lng], { icon: icon }).addTo(map).bindPopup(
                                '<b>👤 ' + displayName + '</b> (@' + (c.username || '-') + ')<br>Status: ' + statusBadge + '<br>Paket: <b>' + (c.plan_name || '-') + '</b><br>' +
                                'Exp: ' + (c.expiration || '-') + '<br>Alamat: ' + (c.address || '-') + '<br>WA: ' + (c.phonenumber || '-') + '<br>' +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Direction / Rute</a>'
                            );
                        });

                        assets.forEach(function(a) {
                            var lat = parseFloat(a.lat); var lng = parseFloat(a.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            assetBounds.push([lat, lng]); allBounds.push([lat, lng]);
                            var icon = makePinIcon('📦 ' + (a.name || 'ODP'), '#8B5CF6', '📦', false);
                            L.marker([lat, lng], { icon: icon }).addTo(map).bindPopup(
                                '<b>📦 ' + (a.name || '-') + '</b><br>Kategori: ' + (a.category || '-') + '<br>Kondisi: ' + (a.condition || 'Good') + '<br>' +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                            );
                        });

                        window.focusMapTarget = function(target) {
                            map.invalidateSize();
                            if (target === 'router' && routerBounds.length > 0) {
                                if (routerBounds.length === 1) map.flyTo(routerBounds[0], 16, { duration: 0.8 });
                                else map.flyToBounds(routerBounds, { padding: [45, 45], maxZoom: 16, duration: 0.8 });
                            } else if (target === 'customers' && customerBounds.length > 0) {
                                map.flyToBounds(customerBounds, { padding: [45, 45], maxZoom: 17, duration: 0.8 });
                            } else if (target === 'assets' && assetBounds.length > 0) {
                                map.flyToBounds(assetBounds, { padding: [45, 45], maxZoom: 17, duration: 0.8 });
                            } else if (target === 'all' && allBounds.length > 0) {
                                map.flyToBounds(allBounds, { padding: [45, 45], maxZoom: 16, duration: 0.8 });
                            } else if (target === 'gps' && navigator.geolocation) {
                                navigator.geolocation.getCurrentPosition(function(pos) {
                                    map.flyTo([pos.coords.latitude, pos.coords.longitude], 17, { duration: 0.8 });
                                });
                            }
                        };

                        setTimeout(function() {
                            map.invalidateSize();
                            if (routerBounds.length > 0) {
                                map.setView(routerBounds[0], 16);
                            } else if (allBounds.length > 0) {
                                map.fitBounds(allBounds, { padding: [40, 40], maxZoom: 16 });
                            }
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
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('gps');", null) },
                label = { Text("📍 Lokasi Saya", fontSize = 11.5.sp) }
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
                        webChromeClient = object : WebChromeClient() {
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: android.webkit.GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, true, false)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.contains("google.com/maps") || url.startsWith("geo:")) {
                                    try {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Aplikasi Google Maps tidak ditemukan.", Toast.LENGTH_SHORT).show()
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
                        webView.loadDataWithBaseURL(
                            ApiClient.baseUrl,
                            mapHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
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
 * 5. LAYAR ASSET MANAGER (ODP, ODC, ONT/MODEM, ROUTER, KABEL)
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
                val arr = res.data.optJSONArray("assets") ?: JSONArray()
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
                            Text("SN/Brand: ${a.optString("serial_number")} ${a.optString("brand")} • Kondisi: ${a.optString("condition_status")}", fontSize = 12.sp)
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
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (ODP/ODC, ONT, Router, Kabel)") }, singleLine = true)
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
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP Address:Port API (Misal 10.8.0.1:8728)") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username API MikroTik") }, singleLine = true)
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password API MikroTik") }, singleLine = true)
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
    var prepaidMode by remember {
        mutableStateOf(
            initial?.optString("prepaid") ?: if (defaultType.equals("PPPoE", ignoreCase = true)) "no" else "yes"
        )
    }

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

                if (!defaultType.equals("Balance", ignoreCase = true)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prepaidMode == "yes",
                            onClick = { prepaidMode = "yes" },
                            label = { Text("Prepaid (Prabayar)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = prepaidMode == "no",
                            onClick = { prepaidMode = "no" },
                            label = { Text("Postpaid (Pascabayar)", fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = validity, onValueChange = { validity = it }, label = { Text("Masa Aktif") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = validityUnit, onValueChange = { validityUnit = it }, label = { Text("Unit (Days/Hrs/Months)") }, modifier = Modifier.weight(1f))
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
                            "prepaid" to prepaidMode,
                            "enabled" to 1
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
                OutlinedTextField(value = nameBw, onValueChange = { nameBw = it }, label = { Text("Nama Profil (Misal: 10 Mbps)") }, singleLine = true)
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
