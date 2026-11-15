package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR JARINGAN, ROUTER, IP POOL & MIKROTIK MONITOR
 */
@Composable
fun AdminNetworkAndOnlineScreen(
    initialTab: String = "live_monitor"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) }
    var isLivePolling by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var routerStatusList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedRouterId by remember { mutableIntStateOf(0) }
    var selectedInterface by remember { mutableStateOf("") }

    var rxHistoryKbps by remember { mutableStateOf(List(24) { 0f }) }
    var txHistoryKbps by remember { mutableStateOf(List(24) { 0f }) }
    var currentRxBps by remember { mutableLongStateOf(0L) }
    var currentTxBps by remember { mutableLongStateOf(0L) }
    var currentRxLabel by remember { mutableStateOf("0 bps") }
    var currentTxLabel by remember { mutableStateOf("0 bps") }
    var lastUpdatedTime by remember { mutableStateOf("-") }

    var onlineUsersList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var onlineSearchQuery by remember { mutableStateOf("") }
    var onlineServiceFilter by remember { mutableStateOf("All") }
    var kickingUserTarget by remember { mutableStateOf<JSONObject?>(null) }
    var detailUserTarget by remember { mutableStateOf<JSONObject?>(null) }

    var extraSubType by remember { mutableStateOf("logs") }
    var rosLogsList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var wlanSignalList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var logFilterText by remember { mutableStateOf("") }

    var routerList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var poolList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var showRouterDialog by remember { mutableStateOf(false) }
    var editingRouter by remember { mutableStateOf<JSONObject?>(null) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var editingPool by remember { mutableStateOf<JSONObject?>(null) }

    var confirmRebootRouterTarget by remember { mutableStateOf<JSONObject?>(null) }
    var confirmDeleteRouterTarget by remember { mutableStateOf<JSONObject?>(null) }
    var confirmDeletePoolTarget by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun fetchRoutersCatalog() {
        val res = ApiClient.request("admin_routers")
        val arr = res.dataArray ?: res.data?.optJSONArray("routers") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        routerList = list
    }

    suspend fun fetchLiveRouterStatus() {
        val params = mutableMapOf<String, Any?>()
        if (selectedRouterId > 0) params["router_id"] = selectedRouterId
        if (selectedInterface.isNotEmpty()) params["interface"] = selectedInterface

        val res = ApiClient.request("admin_router_status", params)
        val arr = res.dataArray ?: res.data?.optJSONArray("routers") ?: JSONArray()
        if (res.status && arr.length() > 0) {
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
            routerStatusList = list

            val activeRouter = list.firstOrNull { it.optInt("id") == selectedRouterId } ?: list.first()
            if (selectedRouterId == 0) {
                selectedRouterId = activeRouter.optInt("id", 0)
            }

            val monitoredIf = activeRouter.optString("monitored_interface", "")
            if (selectedInterface.isEmpty() && monitoredIf.isNotEmpty() && monitoredIf != "-") {
                selectedInterface = monitoredIf
            }

            if (activeRouter.optBoolean("online", false)) {
                val rxBps = activeRouter.optLong("rx_bps", 0L)
                val txBps = activeRouter.optLong("tx_bps", 0L)
                currentRxBps = rxBps
                currentTxBps = txBps
                currentRxLabel = activeRouter.optString("rx_speed_label", formatSpeedBps(rxBps))
                currentTxLabel = activeRouter.optString("tx_speed_label", formatSpeedBps(txBps))

                val rxKbps = (rxBps / 1000f).coerceAtLeast(0f)
                val txKbps = (txBps / 1000f).coerceAtLeast(0f)

                rxHistoryKbps = rxHistoryKbps.drop(1) + rxKbps
                txHistoryKbps = txHistoryKbps.drop(1) + txKbps
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
        val params = if (selectedRouterId > 0) mapOf("router_id" to selectedRouterId) else emptyMap()
        val res = ApiClient.request("admin_online_users", params)
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

    suspend fun fetchMikrotikExtra() {
        val res = ApiClient.request(
            "mikrotik_extra",
            mapOf("router_id" to selectedRouterId, "sub_type" to extraSubType)
        )
        val arr = res.dataArray ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        if (extraSubType == "wlan") {
            wlanSignalList = list
        } else {
            rosLogsList = list
        }
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
                "ros_logs" -> fetchMikrotikExtra()
                "routers" -> fetchRoutersCatalog()
                "pools" -> {
                    if (routerList.isEmpty()) fetchRoutersCatalog()
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

    LaunchedEffect(currentTab, isLivePolling, selectedRouterId, selectedInterface, extraSubType) {
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

    val filteredRosLogs = remember(rosLogsList, logFilterText) {
        val q = logFilterText.trim().lowercase()
        if (q.isEmpty()) rosLogsList
        else rosLogsList.filter { l ->
            l.optString("message", "").lowercase().contains(q) ||
                l.optString("topics", "").lowercase().contains(q) ||
                l.optString("time", "").lowercase().contains(q)
        }
    }

    val availableRouterNames = remember(routerList, routerStatusList) {
        val set = linkedSetOf<String>()
        routerList.forEach { r ->
            val n = r.optString("name").trim()
            if (n.isNotEmpty()) set.add(n)
        }
        routerStatusList.forEach { r ->
            val n = r.optString("name").trim()
            if (n.isNotEmpty()) set.add(n)
        }
        set.toList()
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
                "live_monitor" to "MikroTik Monitor",
                "online_users" to "User Online (${onlineUsersList.size})",
                "ros_logs" to "Log & Wireless",
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
                val activeRouterObj = routerStatusList.firstOrNull { it.optInt("id") == selectedRouterId }
                    ?: routerStatusList.firstOrNull()

                val interfacesList = remember(activeRouterObj) {
                    val arr = activeRouterObj?.optJSONArray("interfaces_list") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) list.add(arr.optString(i))
                    list
                }

                val interfacesTrafficList = remember(activeRouterObj) {
                    val arr = activeRouterObj?.optJSONArray("interfaces_traffic") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    list
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (routerStatusList.size > 1) {
                                    Text("Pilih Router MikroTik:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        routerStatusList.forEach { r ->
                                            val rid = r.optInt("id")
                                            FilterChip(
                                                selected = selectedRouterId == rid,
                                                onClick = {
                                                    selectedRouterId = rid
                                                    selectedInterface = ""
                                                    rxHistoryKbps = List(24) { 0f }
                                                    txHistoryKbps = List(24) { 0f }
                                                },
                                                label = { Text(r.optString("name"), fontSize = 11.5.sp) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Text(
                                    text = "Pilih Interface Traffic Monitor (/interface/monitor-traffic):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArnetaGoldPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                if (interfacesList.isEmpty()) {
                                    Text("Memuat daftar interface dari MikroTik...", fontSize = 11.sp)
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        interfacesList.forEach { ifName ->
                                            val isSel = selectedInterface == ifName ||
                                                (selectedInterface.isEmpty() && activeRouterObj?.optString("monitored_interface") == ifName)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    selectedInterface = ifName
                                                    rxHistoryKbps = List(24) { 0f }
                                                    txHistoryKbps = List(24) { 0f }
                                                },
                                                label = { Text(ifName, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                                leadingIcon = if (isSel) {
                                                    { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val monitoredIf = activeRouterObj
                            ?.optString("monitored_interface", selectedInterface.ifEmpty { "-" })
                            ?.ifEmpty { "-" }
                            ?: selectedInterface.ifEmpty { "-" }

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
                                                text = "Interface: $monitoredIf",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Update: $lastUpdatedTime • Ping: ${activeRouterObj?.optInt("ping_ms", 0) ?: 0} ms",
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

                                NativeMikrotikTrafficChart(
                                    rxPointsKbps = rxHistoryKbps,
                                    txPointsKbps = txHistoryKbps,
                                    rxBps = currentRxBps,
                                    txBps = currentTxBps,
                                    rxLabel = currentRxLabel,
                                    txLabel = currentTxLabel,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (activeRouterObj != null) {
                        item {
                            val r = activeRouterObj
                            val online = r.optBoolean("online", false)
                            val cpu = r.optInt("cpu_load", 0)
                            val ramPct = r.optDouble("ram_percent", 0.0).toFloat()

                            val animCpu by animateFloatAsState(
                                targetValue = (cpu / 100f).coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "cpu_anim"
                            )
                            val animRam by animateFloatAsState(
                                targetValue = (ramPct / 100f).coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "ram_anim"
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
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                        Surface(
                                            color = if (online) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (online) "ONLINE" else "OFFLINE",
                                                color = if (online) ArnetaGreenSuccess else ArnetaRedDanger,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text("CPU Load: $cpu% (${r.optString("cpu_info", "-")})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                        "Memory Used: ${r.optString("used_memory", "-")} / Free: ${r.optString("free_memory")} ($ramPct%)",
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
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Uptime: ${r.optString("uptime")}", fontSize = 11.sp)
                                            Text("Arch: ${r.optString("arch", "-")}", fontSize = 11.sp)
                                            Text("HDD Free: ${r.optString("free_hdd", "-")} / ${r.optString("total_hdd", "-")}", fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Temp: ${r.optString("temperature", "N/A")} • Volt: ${r.optString("voltage", "N/A")}", fontSize = 11.sp, color = ArnetaGoldPrimary)
                                            Text(
                                                text = "Hotspot: ${r.optInt("hotspot_online")} • PPPoE: ${r.optInt("pppoe_online")}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ArnetaGreenSuccess,
                                                modifier = Modifier.clickable { currentTab = "online_users" }
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedButton(
                                                onClick = { confirmRebootRouterTarget = r },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = ArnetaRedDanger, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reboot", fontSize = 10.5.sp, color = ArnetaRedDanger)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (interfacesTrafficList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Tabel Total Trafik Interface (Ketuk untuk pantau speed):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        items(interfacesTrafficList) { iface ->
                            val ifName = iface.optString("name")
                            val isUp = iface.optBoolean("running", false)
                            val isMonitored = (selectedInterface == ifName)

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMonitored) ArnetaGoldPrimary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedInterface = ifName
                                        rxHistoryKbps = List(24) { 0f }
                                        txHistoryKbps = List(24) { 0f }
                                    }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = if (isUp) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isUp) "UP" else "DOWN",
                                                color = if (isUp) ArnetaGreenSuccess else ArnetaRedDanger,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(ifName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                            Text(
                                                text = "TX: ${iface.optString("tx")} • RX: ${iface.optString("rx")}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = iface.optString("total"),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp,
                                            color = ArnetaGoldPrimary
                                        )
                                        Text(
                                            text = if (isMonitored) "Dipantau" else "Ketuk pantau",
                                            fontSize = 10.sp,
                                            color = if (isMonitored) ArnetaCyanIncome else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "online_users" -> {
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
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredOnlineUsers) { u ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { detailUserTarget = u }
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
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Speed,
                                                contentDescription = "Lihat Live Speed",
                                                tint = ArnetaGoldPrimary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "IP: ${u.optString("ip_address")} • MAC/Caller: ${u.optString("mac_address")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "Uptime: ${u.optString("uptime")} • ${u.optString("total_bytes")}",
                                            fontSize = 11.sp,
                                            color = ArnetaGoldPrimary
                                        )
                                        Text(
                                            text = "Ketuk kartu untuk detail & grafik live speed",
                                            fontSize = 10.sp,
                                            color = ArnetaCyanIncome.copy(alpha = 0.85f)
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { kickingUserTarget = u },
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

            "ros_logs" -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = extraSubType == "logs",
                            onClick = { extraSubType = "logs" },
                            label = { Text("Log MikroTik (/log/print)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = extraSubType == "wlan",
                            onClick = { extraSubType = "wlan" },
                            label = { Text("Sinyal Wireless (WLAN)", fontSize = 12.sp) }
                        )
                    }
                    IconButton(onClick = { loadTabContent() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (extraSubType == "logs") {
                    OutlinedTextField(
                        value = logFilterText,
                        onValueChange = { logFilterText = it },
                        placeholder = { Text("Cari isi log / topik (pppoe, hotspot, error)...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredRosLogs) { l ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = l.optString("topics"),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ArnetaCyanIncome
                                        )
                                        Text(
                                            text = l.optString("time"),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = l.optString("message"),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (wlanSignalList.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Tidak ada interface Wireless aktif pada router ini.", fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 95.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(wlanSignalList) { w ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${w.optString("mac_address")} (${w.optString("interface")})", fontWeight = FontWeight.Bold)
                                        Text("IP: ${w.optString("last_ip")} • Signal: ${w.optString("signal_strength")} • Uptime: ${w.optString("uptime")}", fontSize = 11.5.sp)
                                        Text("TX Rate: ${w.optString("tx_rate")} • RX Rate: ${w.optString("rx_rate")}", fontSize = 11.sp, color = ArnetaGoldPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "routers" -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
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

                    IconButton(onClick = { loadTabContent() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(routerList) { r ->
                        val isEnabled = r.optInt("enabled", 1) == 1
                        val descStr = r.optString("description", "").trim()
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    editingRouter = r
                                    showRouterDialog = true
                                }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(r.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Surface(
                                            color = if (isEnabled) ArnetaGreenSuccess.copy(alpha = 0.18f) else ArnetaRedDanger.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isEnabled) "ENABLED" else "DISABLED",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isEnabled) ArnetaGreenSuccess else ArnetaRedDanger,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "IP/API: ${r.optString("ip_address")} • User: ${r.optString("username")}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                    if (descStr.isNotEmpty()) {
                                        Text(
                                            text = descStr,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                    if (r.optString("coordinates").isNotEmpty()) {
                                        Text(
                                            text = "Koordinat: ${r.optString("coordinates")} (Radius ${r.optString("coverage", "350")}m)",
                                            fontSize = 11.sp,
                                            color = ArnetaCyanIncome
                                        )
                                    }
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingRouter = r
                                        showRouterDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Router", tint = ArnetaGoldPrimary)
                                    }
                                    IconButton(onClick = { confirmDeleteRouterTarget = r }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Router", tint = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "pools" -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
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

                    IconButton(onClick = { loadTabContent() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(poolList) { p ->
                        val localIpStr = p.optString("local_ip", "").trim()
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    editingPool = p
                                    showPoolDialog = true
                                }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.optString("pool_name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        text = "Range IP: ${p.optString("range_ip")}" +
                                            if (localIpStr.isNotEmpty()) " • Local IP: $localIpStr" else "",
                                        fontSize = 12.sp,
                                        color = ArnetaCyanIncome
                                    )
                                    Text(
                                        text = "Router: ${p.optString("routers").ifEmpty { "-" }}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingPool = p
                                        showPoolDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Pool", tint = ArnetaGoldPrimary)
                                    }
                                    IconButton(onClick = { confirmDeletePoolTarget = p }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Pool", tint = ArnetaRedDanger)
                                    }
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
            availableRouters = availableRouterNames,
            onDismiss = { showPoolDialog = false },
            onSaved = {
                showPoolDialog = false
                loadTabContent()
            }
        )
    }

    if (confirmRebootRouterTarget != null) {
        val targetR = confirmRebootRouterTarget!!
        AlertDialog(
            onDismissRequest = { confirmRebootRouterTarget = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text("Reboot Router ${targetR.optString("name")}?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Perintah /system/reboot akan dikirim ke Router MikroTik ${targetR.optString("name")} (${targetR.optString("ip_address")}). Seluruh sesi Hotspot & PPPoE pada router ini akan terputus sementara.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRebootRouterTarget = null
                        scope.launch {
                            val rbRes = ApiClient.request("admin_router_reboot", mapOf("id" to targetR.optInt("id")))
                            Toast.makeText(context, rbRes.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaRedDanger, contentColor = Color.White)
                ) {
                    Text("Ya, Reboot Router", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmRebootRouterTarget = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (confirmDeleteRouterTarget != null) {
        val targetR = confirmDeleteRouterTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Router MikroTik?",
            subtitle = "${targetR.optString("name")} (${targetR.optString("ip_address")})",
            description = "Data router ini akan dihapus dari daftar router PHPNuxBill. Pastikan tidak ada paket aktif yang masih terikat pada router ini.",
            onDismiss = { confirmDeleteRouterTarget = null },
            onConfirm = {
                confirmDeleteRouterTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_router_delete", mapOf("id" to targetR.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadTabContent()
                }
            }
        )
    }

    if (confirmDeletePoolTarget != null) {
        val targetP = confirmDeletePoolTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus IP Pool?",
            subtitle = "${targetP.optString("pool_name")} (${targetP.optString("range_ip")})",
            description = "Rentang IP Pool ini akan dihapus dari database.",
            onDismiss = { confirmDeletePoolTarget = null },
            onConfirm = {
                confirmDeletePoolTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_pool_delete", mapOf("id" to targetP.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadTabContent()
                }
            }
        )
    }

    if (kickingUserTarget != null) {
        ConfirmKickUserDialog(
            userObj = kickingUserTarget!!,
            onDismiss = { kickingUserTarget = null },
            onConfirmed = {
                val u = kickingUserTarget!!
                kickingUserTarget = null
                scope.launch {
                    val kRes = ApiClient.request(
                        "admin_kick_user",
                        mapOf(
                            "router" to u.optString("router"),
                            "router_id" to u.optInt("router_id"),
                            "username" to u.optString("username"),
                            "service" to u.optString("service"),
                            "ros_id" to u.optString("ros_id")
                        )
                    )
                    Toast.makeText(context, kRes.message, Toast.LENGTH_SHORT).show()
                    if (detailUserTarget?.optString("username") == u.optString("username")) {
                        detailUserTarget = null
                    }
                    fetchOnlineUsers()
                }
            }
        )
    }

    if (detailUserTarget != null) {
        UserDetailAndLiveSpeedDialog(
            initialUserObj = detailUserTarget!!,
            onDismiss = { detailUserTarget = null },
            onUserKicked = {
                detailUserTarget = null
                scope.launch { fetchOnlineUsers() }
            }
        )
    }
}

/**
 * LAYAR PENUH NATIVE: FORM TAMBAH / EDIT ROUTER MIKROTIK
 */
@Composable
internal fun RouterFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var name by remember { mutableStateOf(initial?.optString("name") ?: "") }
    var ip by remember { mutableStateOf(initial?.optString("ip_address") ?: "") }
    var user by remember { mutableStateOf(initial?.optString("username") ?: "admin") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(initial?.optString("description") ?: "") }
    var coords by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }
    var coverage by remember { mutableStateOf(initial?.optString("coverage")?.ifEmpty { null } ?: "350") }
    var enabledStatus by remember { mutableIntStateOf(initial?.optInt("enabled", 1) ?: 1) }
    var isSaving by remember { mutableStateOf(false) }

    val canSave = !isSaving && name.isNotBlank() && ip.isNotBlank() && user.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = Color(0xFF141B2D),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = if (isEdit) "Edit Router MikroTik" else "Tambah Router MikroTik",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Konfigurasi koneksi RouterOS API & cakupan peta",
                                    fontSize = 11.5.sp,
                                    color = ArnetaGoldLight
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = Color(0xFF141B2D),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.38f)
                                .height(48.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_router_save",
                                        mapOf(
                                            "id" to (initial?.optInt("id") ?: 0),
                                            "name" to name.trim(),
                                            "ip_address" to ip.trim(),
                                            "username" to user.trim(),
                                            "password" to pass,
                                            "description" to description.trim(),
                                            "coordinates" to coords.trim(),
                                            "coverage" to coverage.trim().ifEmpty { "350" },
                                            "enabled" to enabledStatus
                                        )
                                    )
                                    isSaving = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    if (res.status) onSaved()
                                }
                            },
                            modifier = Modifier
                                .weight(0.62f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else "Simpan Router",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Status & Koneksi API RouterOS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = enabledStatus == 1,
                                onClick = { enabledStatus = 1 },
                                label = { Text("Enabled (Aktif)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = enabledStatus == 0,
                                onClick = { enabledStatus = 0 },
                                label = { Text("Disabled (Nonaktif)", fontSize = 12.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Router *") },
                            placeholder = { Text("Contoh: Router-Utama") },
                            leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null, tint = ArnetaGoldPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("IP Address : Port API *") },
                            placeholder = { Text("Misal: 10.8.0.1:8728 atau 192.168.88.1") },
                            leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null, tint = ArnetaCyanIncome) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = user,
                                onValueChange = { user = it },
                                label = { Text("Username API *") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text(if (isEdit) "Password (Opsional)" else "Password API") },
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(
                                            imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Deskripsi / Lokasi Server (Opsional)") },
                            placeholder = { Text("Contoh: POP Pusat / MikroTik RB450Gx4") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Titik Koordinat & Radius Peta",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaCyanIncome
                        )

                        OutlinedTextField(
                            value = coords,
                            onValueChange = { coords = it },
                            label = { Text("Koordinat Peta (Lat, Lng)") },
                            placeholder = { Text("-6.200000, 106.816666") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ArnetaCyanIncome) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = coverage,
                            onValueChange = { coverage = it },
                            label = { Text("Radius Coverage Area (Meter)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * LAYAR PENUH NATIVE: FORM TAMBAH / EDIT IP POOL
 */
@Composable
internal fun PoolFormDialog(
    initial: JSONObject?,
    availableRouters: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var poolName by remember { mutableStateOf(initial?.optString("pool_name") ?: "") }
    var localIp by remember { mutableStateOf(initial?.optString("local_ip") ?: "") }
    var rangeIp by remember { mutableStateOf(initial?.optString("range_ip") ?: "192.168.10.2-192.168.10.254") }
    var routers by remember {
        mutableStateOf(
            initial?.optString("routers")?.ifEmpty { null }
                ?: (availableRouters.firstOrNull() ?: "")
        )
    }
    var isSaving by remember { mutableStateOf(false) }

    val canSave = !isSaving && poolName.isNotBlank() && rangeIp.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = Color(0xFF141B2D),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isEdit) "Edit IP Pool" else "Tambah IP Pool Baru",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Rentang alokasi IP untuk layanan PPPoE / VPN",
                                fontSize = 11.5.sp,
                                color = ArnetaGoldLight
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = Color(0xFF141B2D),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.38f)
                                .height(48.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_pool_save",
                                        mapOf(
                                            "id" to (initial?.optInt("id") ?: 0),
                                            "pool_name" to poolName.trim(),
                                            "local_ip" to localIp.trim(),
                                            "range_ip" to rangeIp.trim(),
                                            "routers" to routers.trim()
                                        )
                                    )
                                    isSaving = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    if (res.status) onSaved()
                                }
                            },
                            modifier = Modifier
                                .weight(0.62f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else "Simpan IP Pool",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Detail Konfigurasi IP Pool",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        OutlinedTextField(
                            value = poolName,
                            onValueChange = { poolName = it },
                            label = { Text("Nama Pool *") },
                            placeholder = { Text("Contoh: pool-pppoe-1") },
                            leadingIcon = { Icon(Icons.Filled.Lan, contentDescription = null, tint = ArnetaGoldPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = localIp,
                            onValueChange = { localIp = it },
                            label = { Text("Local IP Address / Gateway PPPoE (Opsional)") },
                            placeholder = { Text("Contoh: 192.168.10.1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = rangeIp,
                            onValueChange = { rangeIp = it },
                            label = { Text("Rentang IP (Range IP) *") },
                            placeholder = { Text("192.168.10.2-192.168.10.254") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (availableRouters.isNotEmpty()) {
                            Text(
                                text = "Pilih Router MikroTik / Radius:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArnetaCyanIncome
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                (availableRouters + "Radius").distinct().forEach { rName ->
                                    FilterChip(
                                        selected = routers.equals(rName, ignoreCase = true),
                                        onClick = { routers = rName },
                                        label = { Text(rName, fontSize = 11.5.sp) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = routers,
                            onValueChange = { routers = it },
                            label = { Text("Nama Router") },
                            leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null, tint = ArnetaCyanIncome) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
