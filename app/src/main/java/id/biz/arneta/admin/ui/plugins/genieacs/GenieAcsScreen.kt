package id.biz.arneta.admin.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/**
 * LAYAR UTAMA GENIEACS TR-069 PRO (Multi-Host HTTP/HTTPS, Dasbor Optik, Filter/Sort Perangkat)
 */
@Composable
fun GenieAcsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("dashboard") } // dashboard | devices | hosts
    var isLoading by remember { mutableStateOf(false) }

    // State Host GenieACS
    var hostsList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedHostId by remember { mutableStateOf("") }
    var activeHostObj by remember { mutableStateOf<JSONObject?>(null) }
    var connectionError by remember { mutableStateOf("") }

    // State Statistik Dasbor
    var totalCount by remember { mutableIntStateOf(0) }
    var onlineCount by remember { mutableIntStateOf(0) }
    var offlineCount by remember { mutableIntStateOf(0) }
    var badRxCount by remember { mutableIntStateOf(0) }
    var recentDevices by remember { mutableStateOf(emptyList<JSONObject>()) }
    var offlineDevices by remember { mutableStateOf(emptyList<JSONObject>()) }

    // State Daftar Perangkat, Filter, Sort & Paginasi
    var devicesList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") } // all | online | offline | bad_rx
    var sortBy by remember { mutableStateOf("last_inform") } // last_inform | rx_power | temp | connected | serial
    var sortDir by remember { mutableStateOf("desc") } // asc | desc
    var currentPage by remember { mutableIntStateOf(1) }
    var totalFilteredDevices by remember { mutableIntStateOf(0) }
    val pageSize = 50

    // State Dialog & Konfirmasi
    var selectedDeviceIdForDetail by remember { mutableStateOf<String?>(null) }
    var showHostDialog by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<JSONObject?>(null) }
    var confirmRebootDeviceTarget by remember { mutableStateOf<JSONObject?>(null) }
    var confirmDeleteHostTarget by remember { mutableStateOf<JSONObject?>(null) }

    fun loadDashboard() {
        isLoading = true
        scope.launch {
            val params = if (selectedHostId.isNotEmpty()) mapOf("host_id" to selectedHostId) else emptyMap()
            val res = ApiClient.request("admin_genieacs_dashboard", params)
            if (res.status && res.data != null) {
                val hArr = res.data.optJSONArray("hosts") ?: JSONArray()
                val hTemp = mutableListOf<JSONObject>()
                for (i in 0 until hArr.length()) hTemp.add(hArr.getJSONObject(i))
                hostsList = hTemp

                activeHostObj = res.data.optJSONObject("active_host")
                if (selectedHostId.isEmpty() && activeHostObj != null) {
                    selectedHostId = activeHostObj!!.optString("id", "")
                }
                connectionError = res.data.optString("connection_error", "")

                val st = res.data.optJSONObject("stats")
                totalCount = st?.optInt("total", 0) ?: 0
                onlineCount = st?.optInt("online", 0) ?: 0
                offlineCount = st?.optInt("offline", 0) ?: 0
                badRxCount = st?.optInt("bad_rx_count", 0) ?: 0

                val recArr = res.data.optJSONArray("recent_devices") ?: JSONArray()
                val recTemp = mutableListOf<JSONObject>()
                for (i in 0 until recArr.length()) recTemp.add(recArr.getJSONObject(i))
                recentDevices = recTemp

                val offArr = res.data.optJSONArray("offline_devices") ?: JSONArray()
                val offTemp = mutableListOf<JSONObject>()
                for (i in 0 until offArr.length()) offTemp.add(offArr.getJSONObject(i))
                offlineDevices = offTemp
            } else {
                connectionError = res.message
            }
            isLoading = false
        }
    }

    fun loadDevices(page: Int = currentPage) {
        isLoading = true
        currentPage = page
        scope.launch {
            val res = ApiClient.request(
                "admin_genieacs_devices",
                mapOf(
                    "host_id" to selectedHostId,
                    "search" to searchQuery.trim(),
                    "status_filter" to statusFilter,
                    "sort_by" to sortBy,
                    "sort_dir" to sortDir,
                    "page" to page,
                    "limit" to pageSize
                )
            )
            if (res.status && res.data != null) {
                connectionError = ""
                totalFilteredDevices = res.data.optInt("total", 0)
                val dArr = res.data.optJSONArray("devices") ?: JSONArray()
                val dTemp = mutableListOf<JSONObject>()
                for (i in 0 until dArr.length()) dTemp.add(dArr.getJSONObject(i))
                devicesList = dTemp
            } else {
                connectionError = res.message
            }
            isLoading = false
        }
    }

    fun loadHosts() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_genieacs_hosts")
            if (res.status && res.data != null) {
                val hArr = res.data.optJSONArray("hosts") ?: JSONArray()
                val hTemp = mutableListOf<JSONObject>()
                for (i in 0 until hArr.length()) hTemp.add(hArr.getJSONObject(i))
                hostsList = hTemp
                activeHostObj = res.data.optJSONObject("active_host")
            }
            isLoading = false
        }
    }

    fun triggerDeviceAction(deviceId: String, subAction: String) {
        scope.launch {
            val aRes = ApiClient.request(
                "admin_genieacs_device_action",
                mapOf(
                    "id" to deviceId,
                    "host_id" to selectedHostId,
                    "sub_action" to subAction
                )
            )
            Toast.makeText(context, aRes.message, Toast.LENGTH_SHORT).show()
            if (aRes.status) {
                if (currentTab == "dashboard") loadDashboard() else loadDevices(currentPage)
            }
        }
    }

    LaunchedEffect(currentTab, selectedHostId, statusFilter, sortBy, sortDir) {
        when (currentTab) {
            "dashboard" -> loadDashboard()
            "devices" -> loadDevices(1)
            "hosts" -> loadHosts()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header Atas & Info Server Aktif
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = activeHostObj?.optString("name")?.ifEmpty { "GenieACS TR-069" } ?: "GenieACS TR-069",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    if (activeHostObj != null) {
                        val proto = activeHostObj!!.optString("protocol", "http").uppercase()
                        Surface(
                            color = if (proto == "HTTPS") ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaCyanIncome.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = proto,
                                color = if (proto == "HTTPS") ArnetaGreenSuccess else ArnetaCyanIncome,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = activeHostObj?.optString("full_url")?.ifEmpty {
                        "Manajemen ONT / Router CWMP (HTTP & HTTPS)"
                    } ?: "Manajemen ONT / Router CWMP (HTTP & HTTPS)",
                    fontSize = 11.sp,
                    color = ArnetaCyanIncome,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = { currentTab = "hosts" }) {
                    Icon(Icons.Filled.Dns, contentDescription = "Pengaturan Host", tint = ArnetaCyanIncome)
                }
                IconButton(
                    onClick = {
                        when (currentTab) {
                            "dashboard" -> loadDashboard()
                            "devices" -> loadDevices(currentPage)
                            "hosts" -> loadHosts()
                        }
                    }
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Pemilih Host jika terdapat lebih dari 1 Server GenieACS
        if (hostsList.size > 1 && currentTab != "hosts") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp)
            ) {
                Text("Host:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                hostsList.forEach { h ->
                    val hid = h.optString("id")
                    FilterChip(
                        selected = selectedHostId == hid,
                        onClick = { selectedHostId = hid },
                        label = { Text(h.optString("name"), fontSize = 11.5.sp) },
                        leadingIcon = if (selectedHostId == hid) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }
        }

        // Navigasi Tab Utama GenieACS
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "dashboard" to "Dasbor TR-069",
                "devices" to "Daftar Perangkat ($totalCount)",
                "hosts" to "Server GenieACS (${hostsList.size})"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = currentTab == key,
                    onClick = { currentTab = key },
                    label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Banner Peringatan jika Koneksi GenieACS Gagal
        if (connectionError.isNotEmpty() && hostsList.isNotEmpty() && currentTab != "hosts") {
            Surface(
                color = ArnetaRedDanger.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, ArnetaRedDanger.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ArnetaRedDanger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Koneksi GenieACS Bermasalah", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFFFCA5A5))
                            Text(connectionError, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                    TextButton(onClick = { currentTab = "hosts" }) {
                        Text("Cek Host", color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }

        when (currentTab) {
            "dashboard" -> {
                if (hostsList.isEmpty() && !isLoading) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SettingsInputAntenna,
                                contentDescription = null,
                                tint = ArnetaGoldPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Belum Ada Server GenieACS Terdaftar", fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tambahkan IP Lokal (HTTP port 7557) atau Domain Tunnel (HTTPS) server GenieACS Anda untuk memantau redaman optik, WAN, dan mengatur WiFi ONT pelanggan.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    editingHost = null
                                    showHostDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Konfigurasi Host Sekarang", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                } else if (isLoading && recentDevices.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 4 Kartu Statistik Utama (Sesuai genieacs_dashboard.tpl + Kartu Redaman Buruk)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    GenieAcsStatCard(
                                        value = "$totalCount",
                                        label = "Total Perangkat",
                                        subLabel = "Ketuk lihat semua",
                                        accentColor = ArnetaCyanIncome,
                                        onClick = {
                                            statusFilter = "all"
                                            currentTab = "devices"
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    GenieAcsStatCard(
                                        value = "$onlineCount",
                                        label = "Online (<5m)",
                                        subLabel = "Aktif terhubung",
                                        accentColor = ArnetaGreenSuccess,
                                        onClick = {
                                            statusFilter = "online"
                                            currentTab = "devices"
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    GenieAcsStatCard(
                                        value = "$offlineCount",
                                        label = "Offline",
                                        subLabel = "Tidak melapor >5m",
                                        accentColor = ArnetaRedDanger,
                                        onClick = {
                                            statusFilter = "offline"
                                            currentTab = "devices"
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    GenieAcsStatCard(
                                        value = "$badRxCount",
                                        label = "Redaman Kritis / LOS",
                                        subLabel = "RX < -28 dBm",
                                        accentColor = ArnetaOrangeActive,
                                        onClick = {
                                            statusFilter = "bad_rx"
                                            currentTab = "devices"
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (recentDevices.isNotEmpty()) {
                            item {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Perangkat Terbaru (${recentDevices.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    TextButton(onClick = {
                                        statusFilter = "all"
                                        currentTab = "devices"
                                    }) {
                                        Text("Lihat Semua →", fontSize = 12.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            items(recentDevices) { d ->
                                GenieAcsDeviceCard(
                                    device = d,
                                    onClickDetail = { selectedDeviceIdForDetail = d.optString("id") },
                                    onSummon = { triggerDeviceAction(d.optString("id"), "summon") },
                                    onReboot = { confirmRebootDeviceTarget = d }
                                )
                            }
                        }

                        if (offlineDevices.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Perangkat Offline (${offlineDevices.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ArnetaRedDanger
                                )
                            }
                            items(offlineDevices) { d ->
                                GenieAcsDeviceCard(
                                    device = d,
                                    onClickDetail = { selectedDeviceIdForDetail = d.optString("id") },
                                    onSummon = { triggerDeviceAction(d.optString("id"), "summon") },
                                    onReboot = { confirmRebootDeviceTarget = d }
                                )
                            }
                        }
                    }
                }
            }

            "devices" -> {
                // Kolom Pencarian
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari SN, Device ID, PPPoE, atau Nama Pelanggan...", fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    loadDevices(1)
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                                }
                            }
                            IconButton(onClick = { loadDevices(1) }) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "Cari", tint = ArnetaCyanIncome)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Baris Filter Status & Urutan (Sorting sesuai genieacs_devices.tpl)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "all" to "Semua",
                        "online" to "🟢 Online",
                        "offline" to "🔴 Offline",
                        "bad_rx" to "⚠️ Redaman Buruk"
                    ).forEach { (fKey, fLabel) ->
                        FilterChip(
                            selected = statusFilter == fKey,
                            onClick = { statusFilter = fKey },
                            label = { Text(fLabel, fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    listOf(
                        "last_inform" to "Urut: Inform",
                        "rx_power" to "Urut: RX Optik",
                        "temp" to "Urut: Suhu",
                        "connected" to "Urut: User WiFi",
                        "serial" to "Urut: SN"
                    ).forEach { (sKey, sLabel) ->
                        val isSel = sortBy == sKey
                        AssistChip(
                            onClick = {
                                if (sortBy == sKey) {
                                    sortDir = if (sortDir == "asc") "desc" else "asc"
                                } else {
                                    sortBy = sKey
                                    sortDir = if (sKey == "rx_power") "asc" else "desc"
                                }
                            },
                            label = {
                                Text(
                                    text = if (isSel) "$sLabel (${if (sortDir == "asc") "▲" else "▼"})" else sLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSel) ArnetaGoldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (devicesList.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Tidak ada perangkat TR-069 yang cocok dengan filter.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devicesList) { d ->
                            GenieAcsDeviceCard(
                                device = d,
                                onClickDetail = { selectedDeviceIdForDetail = d.optString("id") },
                                onSummon = { triggerDeviceAction(d.optString("id"), "summon") },
                                onReboot = { confirmRebootDeviceTarget = d }
                            )
                        }

                        // Paginasi Halaman (Jika total > 50 perangkat)
                        if (totalFilteredDevices > pageSize) {
                            item {
                                val totalPages = ceil(totalFilteredDevices.toDouble() / pageSize).toInt().coerceAtLeast(1)
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    OutlinedButton(
                                        enabled = currentPage > 1,
                                        onClick = { loadDevices(currentPage - 1) }
                                    ) {
                                        Text("← Sebelumnya", fontSize = 12.sp)
                                    }
                                    Text(
                                        text = "Hal $currentPage dari $totalPages ($totalFilteredDevices ONT)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        enabled = currentPage < totalPages,
                                        onClick = { loadDevices(currentPage + 1) }
                                    ) {
                                        Text("Berikutnya →", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "hosts" -> {
                Button(
                    onClick = {
                        editingHost = null
                        showHostDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Server GenieACS (HTTP / HTTPS)", fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (hostsList.isEmpty() && !isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Belum ada host GenieACS yang dikonfigurasi.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hostsList) { h ->
                            val hid = h.optString("id")
                            val isAct = h.optBoolean("active", false)
                            val proto = h.optString("protocol", "http").uppercase()
                            val fullUrl = h.optString("full_url", "${proto.lowercase()}://${h.optString("host")}:${h.optInt("port", 7557)}")

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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(h.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Surface(
                                                    color = if (proto == "HTTPS") ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaCyanIncome.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = proto,
                                                        color = if (proto == "HTTPS") ArnetaGreenSuccess else ArnetaCyanIncome,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (isAct) {
                                                    Surface(
                                                        color = ArnetaGoldPrimary.copy(alpha = 0.22f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            color = ArnetaGoldPrimary,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Endpoint NBI: $fullUrl",
                                                fontSize = 12.sp,
                                                color = ArnetaCyanIncome,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (h.optString("username").isNotEmpty()) {
                                                Text(
                                                    text = "Auth User: ${h.optString("username")}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                                )
                                            }
                                        }

                                        Row {
                                            IconButton(onClick = {
                                                editingHost = h
                                                showHostDialog = true
                                            }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                            }
                                            IconButton(onClick = { confirmDeleteHostTarget = h }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val tRes = ApiClient.request(
                                                        "admin_genieacs_host_action",
                                                        mapOf("id" to hid, "sub_action" to "test")
                                                    )
                                                    Toast.makeText(context, tRes.message, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Test Koneksi", fontSize = 11.5.sp)
                                        }

                                        if (!isAct) {
                                            FilledTonalButton(
                                                onClick = {
                                                    scope.launch {
                                                        val sRes = ApiClient.request(
                                                            "admin_genieacs_host_action",
                                                            mapOf("id" to hid, "sub_action" to "setactive")
                                                        )
                                                        Toast.makeText(context, sRes.message, Toast.LENGTH_SHORT).show()
                                                        selectedHostId = hid
                                                        loadHosts()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Set Active", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
    }

    // Dialog Tambah / Edit Host GenieACS (Di GenieAcsDialogs.kt)
    if (showHostDialog) {
        GenieAcsHostFormDialog(
            initial = editingHost,
            onDismiss = { showHostDialog = false },
            onSaved = {
                showHostDialog = false
                loadHosts()
                loadDashboard()
            }
        )
    }

    // Dialog Detail Perangkat, WAN, WLAN, Connected Hosts & Raw JSON (Di GenieAcsDialogs.kt)
    if (selectedDeviceIdForDetail != null) {
        GenieAcsDeviceDetailDialog(
            deviceId = selectedDeviceIdForDetail!!,
            hostId = selectedHostId,
            onDismiss = { selectedDeviceIdForDetail = null },
            onDeviceDeleted = {
                selectedDeviceIdForDetail = null
                if (currentTab == "dashboard") loadDashboard() else loadDevices(currentPage)
            }
        )
    }

    // Konfirmasi Reboot Perangkat
    if (confirmRebootDeviceTarget != null) {
        val target = confirmRebootDeviceTarget!!
        val devId = target.optString("id")
        val sn = target.optString("serial_number").ifEmpty { devId }
        AlertDialog(
            onDismissRequest = { confirmRebootDeviceTarget = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Reboot Perangkat $sn?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Perintah Reboot TR-069 akan dikirim ke ONT/Router ${target.optString("fullname").ifEmpty { sn }}. Koneksi internet pelanggan akan terputus sekitar 1-2 menit.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRebootDeviceTarget = null
                        triggerDeviceAction(devId, "reboot")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaOrangeActive, contentColor = Color.White)
                ) {
                    Text("Ya, Reboot Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmRebootDeviceTarget = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // Konfirmasi Hapus Host GenieACS
    if (confirmDeleteHostTarget != null) {
        val h = confirmDeleteHostTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Server GenieACS?",
            subtitle = "${h.optString("name")} (${h.optString("host")})",
            description = "Konfigurasi host GenieACS ini akan dihapus dari daftar server.",
            onDismiss = { confirmDeleteHostTarget = null },
            onConfirm = {
                val hid = h.optString("id")
                confirmDeleteHostTarget = null
                scope.launch {
                    val dRes = ApiClient.request(
                        "admin_genieacs_host_action",
                        mapOf("id" to hid, "sub_action" to "delete")
                    )
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    loadHosts()
                }
            }
        )
    }
}

@Composable
private fun GenieAcsStatCard(
    value: String,
    label: String,
    subLabel: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.16f)),
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                color = accentColor
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subLabel,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * KARTU PERANGKAT GENIEACS (Menampilkan Badge Warna RX Power, Suhu, Pelanggan, Tags & Aksi Cepat)
 */
@Composable
internal fun GenieAcsDeviceCard(
    device: JSONObject,
    onClickDetail: () -> Unit,
    onSummon: () -> Unit,
    onReboot: () -> Unit
) {
    val isOnline = device.optBoolean("online", false)
    val devId = device.optString("id")
    val sn = device.optString("serial_number").ifEmpty { devId }
    val pppoe = device.optString("pppoe_username", "N/A")
    val fullname = device.optString("fullname", "")
    val tr069Ip = device.optString("tr069_ip", "N/A")
    val wanIp = device.optString("wan_ip", "N/A")
    val temp = device.optString("temperature", "N/A")
    val tempStatus = device.optString("temperature_status", "unknown")
    val rxPower = device.optString("rx_power", "N/A")
    val rxStatus = device.optString("rx_power_status", "unknown")
    val connected = device.optInt("connected_devices", 0)
    val lastInform = device.optString("last_inform", "-")
    val tagsArr = device.optJSONArray("tags") ?: JSONArray()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClickDetail() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) ArnetaGreenSuccess else ArnetaRedDanger)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fullname.isNotEmpty()) "$fullname ($sn)" else sn,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${device.optString("manufacturer", "-")} ${device.optString("product_class", "-")} • PPPoE: $pppoe",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = if (isOnline) ArnetaGreenSuccess.copy(alpha = 0.18f) else ArnetaRedDanger.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) ArnetaGreenSuccess else ArnetaRedDanger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Baris Badge Indikator Optik RX Power, Suhu & Jumlah Perangkat Terhubung
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                GenieAcsRxPowerBadge(rxPower = rxPower, rxStatus = rxStatus)
                GenieAcsTemperatureBadge(temperature = temp, tempStatus = tempStatus)

                Surface(
                    color = ArnetaCyanIncome.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "📱 $connected Perangkat",
                        color = ArnetaCyanIncome,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                val ipDisplay = if (tr069Ip != "N/A") tr069Ip else wanIp
                if (ipDisplay != "N/A") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "IP: $ipDisplay",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (tagsArr.length() > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    for (i in 0 until tagsArr.length()) {
                        val tagStr = tagsArr.optString(i)
                        if (tagStr.isNotEmpty()) {
                            Surface(
                                color = ArnetaIndigoAccent.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "#$tagStr",
                                    fontSize = 9.5.sp,
                                    color = ArnetaGoldLight,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Last Inform: $lastInform • Uptime: ${device.optString("uptime", "-")}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            // Tombol Aksi Cepat (Detail/WiFi/WAN, Summon, Reboot)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onClickDetail,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(32.dp)
                ) {
                    Icon(Icons.Filled.SettingsRemote, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detail / WiFi & WAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onSummon,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(32.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = ArnetaGreenSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Summon", fontSize = 11.sp, color = ArnetaGreenSuccess, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onReboot,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(32.dp)
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = ArnetaOrangeActive, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reboot", fontSize = 11.sp, color = ArnetaOrangeActive, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * BADGE INDIKATOR WARNA REDAMAN OPTIK (RX POWER) SESUAI STANDAR GENIEACS_DEVICES.TPL
 */
@Composable
internal fun GenieAcsRxPowerBadge(
    rxPower: String,
    rxStatus: String
) {
    val (bgColor, textColor) = when (rxStatus) {
        "good" -> ArnetaGreenSuccess.copy(alpha = 0.2f) to ArnetaGreenSuccess
        "warning" -> ArnetaGoldPrimary.copy(alpha = 0.2f) to ArnetaGoldPrimary
        "critical" -> ArnetaRedDanger.copy(alpha = 0.2f) to ArnetaRedDanger
        "over" -> Color(0xFFE91E63).copy(alpha = 0.22f) to Color(0xFFF48FB1)
        "los" -> Color.Gray.copy(alpha = 0.25f) to Color.LightGray
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "⚡ RX: $rxPower",
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * BADGE INDIKATOR WARNA SUHU ONT SESUAI STANDAR GENIEACS_DEVICES.TPL
 */
@Composable
internal fun GenieAcsTemperatureBadge(
    temperature: String,
    tempStatus: String
) {
    val (bgColor, textColor) = when (tempStatus) {
        "good" -> ArnetaGreenSuccess.copy(alpha = 0.18f) to ArnetaGreenSuccess
        "warning" -> ArnetaGoldPrimary.copy(alpha = 0.18f) to ArnetaGoldPrimary
        "critical" -> ArnetaRedDanger.copy(alpha = 0.2f) to ArnetaRedDanger
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "🌡️ $temperature",
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
