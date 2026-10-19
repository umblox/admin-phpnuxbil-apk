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
 * 1. IN-APP BROWSER VIEW INTERNAL (Tanpa Keluar Aplikasi / Tanpa Buka Chrome)
 * Mendukung Auto-Login SSO langsung ke https://phpnuxbill.arneta.biz.id/?_route=admin & ?_route=dashboard
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapScreen() {
    var customersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assetsGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mapHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

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

            val centerLat = rList.firstOrNull()?.optDouble("lat")
                ?: cList.firstOrNull()?.optDouble("lat")
                ?: -6.200000
            val centerLng = rList.firstOrNull()?.optDouble("lng")
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
                        .leaflet-popup-content-wrapper { border-radius: 10px; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        var osmStreet = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                            maxZoom: 20,
                            attribution: '© OpenStreetMap & Carto • Arneta.ID'
                        });
                        var googleSat = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                            maxZoom: 20,
                            attribution: '© Google Hybrid • Arneta.ID'
                        });

                        var map = L.map('map', {
                            center: [$centerLat, $centerLng],
                            zoom: 15,
                            layers: [osmStreet],
                            zoomControl: true
                        });

                        L.control.layers({
                            "Peta Jalan (Terang)": osmStreet,
                            "Satelit Hybrid": googleSat
                        }).addTo(map);

                        var bounds = [];
                        var customers = ${cArr.toString()};
                        var routers = ${rArr.toString()};
                        var assets = ${aArr.toString()};

                        routers.forEach(function(r) {
                            if (!r.lat || !r.lng) return;
                            bounds.push([r.lat, r.lng]);
                            L.circle([r.lat, r.lng], {
                                radius: r.coverage || 350,
                                color: '#00C0EF',
                                weight: 1.5,
                                fillColor: '#00C0EF',
                                fillOpacity: 0.14
                            }).addTo(map);

                            L.circleMarker([r.lat, r.lng], {
                                radius: 10,
                                fillColor: '#F59E0B',
                                color: '#000000',
                                weight: 2.5,
                                fillOpacity: 1
                            }).addTo(map).bindPopup(
                                '<b>📡 Router: ' + r.name + '</b><br>IP: ' + r.ip_address + '<br>Coverage: ' + (r.coverage || 350) + 'm'
                            );
                        });

                        customers.forEach(function(c) {
                            if (!c.lat || !c.lng) return;
                            bounds.push([c.lat, c.lng]);
                            var isOn = (c.plan_status === 'on');
                            var fillCol = isOn ? '#10B981' : '#EF4444';
                            var badge = isOn ? '<span style="color:#10B981;font-weight:bold;">AKTIF</span>' : '<span style="color:#EF4444;font-weight:bold;">EXPIRED</span>';

                            L.circleMarker([c.lat, c.lng], {
                                radius: 8,
                                fillColor: fillCol,
                                color: '#FFFFFF',
                                weight: 2,
                                fillOpacity: 0.95
                            }).addTo(map).bindPopup(
                                '<b>👤 ' + c.fullname + '</b> (@' + c.username + ')<br>' +
                                'Status: ' + badge + '<br>' +
                                'Paket: ' + c.plan_name + '<br>' +
                                'Exp: ' + c.expiration + '<br>' +
                                'WA: ' + (c.phonenumber || '-')
                            );
                        });

                        assets.forEach(function(a) {
                            if (!a.lat || !a.lng) return;
                            bounds.push([a.lat, a.lng]);
                            L.circleMarker([a.lat, a.lng], {
                                radius: 7,
                                fillColor: '#A855F7',
                                color: '#FFFFFF',
                                weight: 2,
                                fillOpacity: 0.95
                            }).addTo(map).bindPopup(
                                '<b>📦 ' + a.name + '</b> (' + a.category + ')<br>Kondisi: ' + a.condition
                            );
                        });

                        setTimeout(function() {
                            map.invalidateSize();
                            if (bounds.length > 1) {
                                map.fitBounds(bounds, { padding: [35, 35], maxZoom: 17 });
                            } else if (bounds.length === 1) {
                                map.setView(bounds[0], 16);
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
        // Bar Legenda di Atas Peta
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text("🟢 Aktif (${customersGeo.count { it.optString("plan_status") == "on" }})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("🔴 Expired (${customersGeo.count { it.optString("plan_status") != "on" }})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("🟡 Router (${routersGeo.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("📦 Aset/ODP (${assetsGeo.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 15; ArnetaAdminApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        tag = ""
                    }
                },
                update = { webView ->
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

        // Komponen WebView Internal (Terkunci di dalam aplikasi)
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
                            // Tetap buka semua link HTTP/HTTPS di dalam WebView internal aplikasi
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
 * 2. LAYAR JARINGAN, ROUTER MIKROTIK, LIVE DIAGRAM REAL-TIME DINAMIS & ONLINE USERS (KICK USER)
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

    // Riwayat titik untuk Live Diagram Real-Time Dinamis (20 titik waktu terakhir)
    var cpuHistory by remember { mutableStateOf(listOf(10f, 12f, 15f, 11f, 14f, 18f, 15f, 13f)) }
    var ramHistory by remember { mutableStateOf(listOf(35f, 35f, 36f, 36f, 35f, 37f, 36f, 36f)) }
    var pingHistory by remember { mutableStateOf(listOf(4f, 5f, 3f, 6f, 4f, 4f, 5f, 4f)) }
    var lastUpdatedTime by remember { mutableStateOf("Baru saja") }

    // Dialog Tambah/Edit Router & Pool
    var showRouterDialog by remember { mutableStateOf(false) }
    var editingRouter by remember { mutableStateOf<JSONObject?>(null) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var editingPool by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun fetchLiveRouterStatus() {
        val res = ApiClient.request("admin_router_status")
        if (res.status && res.dataArray != null) {
            val list = mutableListOf<JSONObject>()
            var maxCpu = 0f
            var avgRam = 0f
            var avgPing = 0f
            var onlineCount = 0

            for (i in 0 until res.dataArray.length()) {
                val obj = res.dataArray.getJSONObject(i)
                list.add(obj)
                if (obj.optBoolean("online", false)) {
                    onlineCount++
                    val c = obj.optDouble("cpu_load", 0.0).toFloat()
                    if (c > maxCpu) maxCpu = c
                    avgRam += obj.optDouble("ram_percent", 0.0).toFloat()
                    avgPing += obj.optDouble("ping_ms", 0.0).toFloat()
                }
            }
            routerStatusList = list
            if (onlineCount > 0) {
                avgRam /= onlineCount
                avgPing /= onlineCount
                cpuHistory = (cpuHistory + maxCpu).takeLast(20)
                ramHistory = (ramHistory + avgRam).takeLast(20)
                pingHistory = (pingHistory + avgPing).takeLast(20)
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

    fun loadTabContent() {
        isLoading = true
        scope.launch {
            when (currentTab) {
                "live_monitor" -> fetchLiveRouterStatus()
                "online_users" -> {
                    val res = ApiClient.request("admin_online_users")
                    if (res.status && res.data != null) {
                        val arr = res.data.optJSONArray("users") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        onlineUsersList = list
                    }
                }
                "routers" -> {
                    val res = ApiClient.request("admin_routers")
                    if (res.status && res.dataArray != null) {
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until res.dataArray.length()) list.add(res.dataArray.getJSONObject(i))
                        routerList = list
                    }
                }
                "pools" -> {
                    val res = ApiClient.request("admin_pools")
                    if (res.status && res.dataArray != null) {
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until res.dataArray.length()) list.add(res.dataArray.getJSONObject(i))
                        poolList = list
                    }
                }
            }
            isLoading = false
        }
    }

    // Engine Real-Time Auto-Polling tiap 3.5 detik saat tab Live Monitor / Online Users aktif
    LaunchedEffect(currentTab, isLivePolling) {
        loadTabContent()
        if (isLivePolling && (currentTab == "live_monitor" || currentTab == "online_users")) {
            while (isActive && isLivePolling) {
                delay(3500L)
                if (currentTab == "live_monitor") {
                    fetchLiveRouterStatus()
                } else if (currentTab == "online_users") {
                    val res = ApiClient.request("admin_online_users")
                    if (res.status && res.data != null) {
                        val arr = res.data.optJSONArray("users") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        onlineUsersList = list
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Navigasi Sub-Tab Jaringan
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "live_monitor" to "Live Monitor MikroTik",
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
                    // Header Status Real-Time + Sakelar Live Stream
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                                text = "Live Telemetry MikroTik RouterOS",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Update otomatis tiap 3 detik • Terakhir: $lastUpdatedTime",
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

                                Spacer(modifier = Modifier.height(14.dp))

                                // LIVE DIAGRAM DINAMIS (Gelombang CPU Load & RAM Usage Real-Time)
                                LiveDynamicWaveDiagram(
                                    cpuPoints = cpuHistory,
                                    ramPoints = ramHistory,
                                    pingPoints = pingHistory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                )
                            }
                        }
                    }

                    // Daftar Kartu Resource per Router + Tombol Reboot
                    items(routerStatusList) { r ->
                        val online = r.optBoolean("online", false)
                        val cpu = r.optInt("cpu_load", 0)
                        val ramPct = r.optDouble("ram_percent", 0.0).toFloat()

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

                                Spacer(modifier = Modifier.height(12.dp))

                                // Progress Bar Dinamis CPU & RAM
                                Text("CPU Load: $cpu%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (cpu / 100f).coerceIn(0f, 1f) },
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
                                    progress = { (ramPct / 100f).coerceIn(0f, 1f) },
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
                                        fontWeight = FontWeight.Bold
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
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(onlineUsersList) { u ->
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

                                // Tombol KICK / Putuskan Koneksi Sesi
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
                                            loadTabContent()
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
 * Komponen Canvas Live Diagram Real-Time Dinamis (CPU Load, RAM %, & Ping ms)
 */
@Composable
private fun LiveDynamicWaveDiagram(
    cpuPoints: List<Float>,
    ramPoints: List<Float>,
    pingPoints: List<Float>,
    modifier: Modifier = Modifier
) {
    val latestCpu by animateFloatAsState(targetValue = cpuPoints.lastOrNull() ?: 0f, animationSpec = tween(500), label = "cpu")
    val latestRam by animateFloatAsState(targetValue = ramPoints.lastOrNull() ?: 0f, animationSpec = tween(500), label = "ram")
    val latestPing by animateFloatAsState(targetValue = pingPoints.lastOrNull() ?: 0f, animationSpec = tween(500), label = "ping")

    Column(modifier = modifier) {
        // Legend Bar Real-Time
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ArnetaCyanIncome))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CPU: ${latestCpu.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ArnetaGoldPrimary))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RAM: ${latestRam.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ArnetaGreenSuccess))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ping: ${latestPing.toInt()} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaGreenSuccess)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1120))
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height

            // Garis Grid Horizontal
            for (i in 0..4) {
                val y = h * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            fun drawWave(points: List<Float>, color: Color, fillAlpha: Float) {
                if (points.size < 2) return
                val path = Path()
                val fillPath = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                points.forEachIndexed { idx, value ->
                    val x = idx * stepX
                    val y = h - (value.coerceIn(0f, 100f) / 100f) * h
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
                        colors = listOf(color.copy(alpha = fillAlpha), Color.Transparent)
                    )
                )
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            // Render Gelombang RAM & CPU
            drawWave(ramPoints, ArnetaGoldPrimary, 0.18f)
            drawWave(cpuPoints, ArnetaCyanIncome, 0.30f)
        }
    }
}

/**
 * 3. LAYAR PAKET INTERNET (HOTSPOT, PPPOE, BALANCE) & PROFIL BANDWIDTH
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
 * 4. LAYAR PETA PELANGGAN, ROUTER COVERAGE & ODP INTERAKTIF (Leaflet OpenStreetMap In-App)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapScreen() {
    val scope = rememberCoroutineScope()
    var customersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routersGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assetsGeo by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mapHtml by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
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

            val centerLat = cList.firstOrNull()?.optDouble("lat") ?: rList.firstOrNull()?.optDouble("lat") ?: -6.200000
            val centerLng = cList.firstOrNull()?.optDouble("lng") ?: rList.firstOrNull()?.optDouble("lng") ?: 106.816666

            mapHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>body,html,#map{margin:0;padding:0;width:100%;height:100%;background:#0B1120;}</style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        var map = L.map('map').setView([$centerLat, $centerLng], 14);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '© OpenStreetMap • Arneta.ID'
                        }).addTo(map);
                        var customers = $cArr;
                        customers.forEach(function(c) {
                            var color = c.plan_status === 'on' ? '#10B981' : '#EF4444';
                            L.circleMarker([c.lat, c.lng], {radius: 8, fillColor: color, color: '#fff', weight: 2, fillOpacity: 0.9})
                             .addTo(map)
                             .bindPopup('<b>' + c.fullname + ' (@' + c.username + ')</b><br>Paket: ' + c.plan_name + '<br>Exp: ' + c.expiration);
                        });
                        var routers = $rArr;
                        routers.forEach(function(r) {
                            L.circle([r.lat, r.lng], {radius: r.coverage || 300, color: '#00C0EF', fillOpacity: 0.12}).addTo(map);
                            L.circleMarker([r.lat, r.lng], {radius: 10, fillColor: '#F59E0B', color: '#000', weight: 2, fillOpacity: 1})
                             .addTo(map)
                             .bindPopup('<b>Router: ' + r.name + '</b><br>IP: ' + r.ip_address);
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Legenda Peta
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text("🟢 Aktif (${customersGeo.count { it.optString("plan_status") == "on" }})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("🔴 Expired (${customersGeo.count { it.optString("plan_status") != "on" }})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("🟡 Router (${routersGeo.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("📦 Aset/ODP (${assetsGeo.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (mapHtml.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://unpkg.com", mapHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 86.dp)
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
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
    var coverage by remember { mutableStateOf(initial?.optString("coverage") ?: "500") }

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
                            "pool" to poolName
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
