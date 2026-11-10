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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR PLUGIN GENIEACS TR-069 (Multi-Server Port 7557, Device Monitor, WAN & WLAN Config)
 * Terhubung ke mobile_api_modules/plugin_genieacs.php
 */
@Composable
fun GenieAcsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("dashboard") } // dashboard | devices | hosts
    var isLoading by remember { mutableStateOf(false) }

    var hostsList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedHostId by remember { mutableStateOf("") }
    var activeHostObj by remember { mutableStateOf<JSONObject?>(null) }

    var totalCount by remember { mutableIntStateOf(0) }
    var onlineCount by remember { mutableIntStateOf(0) }
    var offlineCount by remember { mutableIntStateOf(0) }
    var recentDevices by remember { mutableStateOf(emptyList<JSONObject>()) }
    var offlineDevices by remember { mutableStateOf(emptyList<JSONObject>()) }

    var devicesList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedDeviceIdForDetail by remember { mutableStateOf<String?>(null) }
    var showHostDialog by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<JSONObject?>(null) }

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

                val st = res.data.optJSONObject("stats")
                totalCount = st?.optInt("total", 0) ?: 0
                onlineCount = st?.optInt("online", 0) ?: 0
                offlineCount = st?.optInt("offline", 0) ?: 0

                val recArr = res.data.optJSONArray("recent_devices") ?: JSONArray()
                val recTemp = mutableListOf<JSONObject>()
                for (i in 0 until recArr.length()) recTemp.add(recArr.getJSONObject(i))
                recentDevices = recTemp

                val offArr = res.data.optJSONArray("offline_devices") ?: JSONArray()
                val offTemp = mutableListOf<JSONObject>()
                for (i in 0 until offArr.length()) offTemp.add(offArr.getJSONObject(i))
                offlineDevices = offTemp
            }
            isLoading = false
        }
    }

    fun loadDevices() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request(
                "admin_genieacs_devices",
                mapOf(
                    "host_id" to selectedHostId,
                    "search" to searchQuery.trim(),
                    "limit" to 100
                )
            )
            if (res.status && res.data != null) {
                val dArr = res.data.optJSONArray("devices") ?: JSONArray()
                val dTemp = mutableListOf<JSONObject>()
                for (i in 0 until dArr.length()) dTemp.add(dArr.getJSONObject(i))
                devicesList = dTemp
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

    LaunchedEffect(currentTab, selectedHostId) {
        when (currentTab) {
            "dashboard" -> loadDashboard()
            "devices" -> loadDevices()
            "hosts" -> loadHosts()
        }
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
            Column(modifier = Modifier.weight(1f)) {
                Text("GenieACS TR-069 Manager", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    text = if (activeHostObj != null) {
                        "Host Aktif: ${activeHostObj!!.optString("name")} (${activeHostObj!!.optString("host")}:${activeHostObj!!.optInt("port", 7557)})"
                    } else {
                        "Manajemen ONT / Router via CWMP & REST API Port 7557"
                    },
                    fontSize = 11.sp,
                    color = ArnetaCyanIncome
                )
            }

            IconButton(
                onClick = {
                    when (currentTab) {
                        "dashboard" -> loadDashboard()
                        "devices" -> loadDevices()
                        "hosts" -> loadHosts()
                    }
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "dashboard" to "Dasbor TR-069",
                "devices" to "Perangkat ($totalCount)",
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
                                .padding(20.dp)
                        ) {
                            Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Belum Ada Server GenieACS Terdaftar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tambahkan IP / Domain server GenieACS (port default 7557) untuk mulai memantau dan mengatur WiFi router/ONT pelanggan dari aplikasi.",
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
                                Text("Tambah Host GenieACS", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hostsList.size > 1) {
                            item {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    hostsList.forEach { h ->
                                        val hid = h.optString("id")
                                        FilterChip(
                                            selected = selectedHostId == hid,
                                            onClick = { selectedHostId = hid },
                                            label = { Text(h.optString("name"), fontSize = 11.5.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Kartu Statistik Total / Online / Offline
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ArnetaCyanIncome.copy(alpha = 0.18f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { currentTab = "devices" }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text("$totalCount", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ArnetaCyanIncome)
                                        Text("Total ONT/Router", fontSize = 11.sp)
                                    }
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ArnetaGreenSuccess.copy(alpha = 0.18f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { currentTab = "devices" }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text("$onlineCount", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ArnetaGreenSuccess)
                                        Text("Online (<5m)", fontSize = 11.sp)
                                    }
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ArnetaRedDanger.copy(alpha = 0.18f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text("$offlineCount", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ArnetaRedDanger)
                                        Text("Offline", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (recentDevices.isNotEmpty()) {
                            item {
                                Text("Perangkat Terbaru:", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            items(recentDevices) { d ->
                                GenieAcsDeviceCard(
                                    device = d,
                                    onClickDetail = { selectedDeviceIdForDetail = d.optString("id") },
                                    onAction = { subAction ->
                                        scope.launch {
                                            val aRes = ApiClient.request(
                                                "admin_genieacs_device_action",
                                                mapOf("id" to d.optString("id"), "host_id" to selectedHostId, "sub_action" to subAction)
                                            )
                                            Toast.makeText(context, aRes.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        if (offlineDevices.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Perangkat Offline (${offlineDevices.size}):", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ArnetaRedDanger)
                            }
                            items(offlineDevices) { d ->
                                GenieAcsDeviceCard(
                                    device = d,
                                    onClickDetail = { selectedDeviceIdForDetail = d.optString("id") },
                                    onAction = { subAction ->
                                        scope.launch {
                                            val aRes = ApiClient.request(
                                                "admin_genieacs_device_action",
                                                mapOf("id" to d.optString("id"), "host_id" to selectedHostId, "sub_action" to subAction)
                                            )
                                            Toast.makeText(context, aRes.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            "devices" -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari Serial Number, PPPoE User, atau Nama Pelanggan...", fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    loadDevices()
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                                }
                            }
                            IconButton(onClick = { loadDevices() }) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "Cari", tint = ArnetaCyanIncome)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (devicesList.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Tidak ada perangkat TR-069 yang ditemukan.", fontSize = 13.sp)
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
                                onAction = { subAction ->
                                    scope.launch {
                                        val aRes = ApiClient.request(
                                            "admin_genieacs_device_action",
                                            mapOf("id" to d.optString("id"), "host_id" to selectedHostId, "sub_action" to subAction)
                                        )
                                        Toast.makeText(context, aRes.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
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
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Server GenieACS (Port 7557)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(hostsList) { h ->
                        val hid = h.optString("id")
                        val isAct = h.optBoolean("active", false)
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(h.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (isAct) {
                                                Surface(
                                                    color = ArnetaGreenSuccess.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = ArnetaGreenSuccess,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "URL NBI: http://${h.optString("host")}:${h.optInt("port", 7557)}",
                                            fontSize = 12.sp,
                                            color = ArnetaCyanIncome
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            editingHost = h
                                            showHostDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                val dRes = ApiClient.request(
                                                    "admin_genieacs_host_action",
                                                    mapOf("id" to hid, "sub_action" to "delete")
                                                )
                                                Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                                loadHosts()
                                            }
                                        }) {
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
                                            Text("Jadikan Host Aktif", fontSize = 11.5.sp)
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

    if (selectedDeviceIdForDetail != null) {
        GenieAcsDeviceDetailDialog(
            deviceId = selectedDeviceIdForDetail!!,
            hostId = selectedHostId,
            onDismiss = { selectedDeviceIdForDetail = null }
        )
    }
}

@Composable
private fun GenieAcsDeviceCard(
    device: JSONObject,
    onClickDetail: () -> Unit,
    onAction: (String) -> Unit
) {
    val isOnline = device.optBoolean("online", false)
    val sn = device.optString("serial_number").ifEmpty { device.optString("id") }
    val pppoe = device.optString("pppoe_username", "N/A")
    val fullname = device.optString("fullname", "")
    val tr069Ip = device.optString("tr069_ip", "N/A")
    val temp = device.optString("temperature", "N/A")
    val rxPower = device.optString("rx_power", "N/A")
    val connected = device.optInt("connected_devices", 0)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) ArnetaGreenSuccess else ArnetaRedDanger)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fullname.isNotEmpty()) "$fullname ($sn)" else sn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${device.optString("manufacturer", "-")} ${device.optString("product_class", "-")} • PPPoE/User: $pppoe",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Surface(
                    color = if (isOnline) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) ArnetaGreenSuccess else ArnetaRedDanger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "IP: $tr069Ip • Temp: $temp • RX: $rxPower • 📱 $connected HP",
                    fontSize = 11.sp,
                    color = ArnetaCyanIncome
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onClickDetail,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detail / Edit WiFi & WAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onAction("summon") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Summon", fontSize = 11.sp, color = ArnetaGreenSuccess)
                }
                OutlinedButton(
                    onClick = { onAction("reboot") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Reboot", fontSize = 11.sp, color = ArnetaRedDanger)
                }
            }
        }
    }
}

@Composable
private fun GenieAcsHostFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.optString("name") ?: "GenieACS Utama") }
    var host by remember { mutableStateOf(initial?.optString("host") ?: "") }
    var port by remember { mutableStateOf(initial?.optInt("port", 7557)?.toString() ?: "7557") }
    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var password by remember { mutableStateOf(initial?.optString("password") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial != null) "Edit Host GenieACS" else "Tambah Host GenieACS",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Server") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP / Domain GenieACS (Misal: 192.168.1.200)") }, singleLine = true)
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port NBI REST API (Default: 7557)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username Auth (Opsional)") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password Auth (Opsional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_genieacs_host_save",
                            mapOf(
                                "id" to (initial?.optString("id") ?: ""),
                                "name" to name.trim(),
                                "host" to host.trim(),
                                "port" to (port.toIntOrNull() ?: 7557),
                                "username" to username.trim(),
                                "password" to password
                            )
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
            ) {
                Text("Simpan Host", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun GenieAcsDeviceDetailDialog(
    deviceId: String,
    hostId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var deviceInfo by remember { mutableStateOf<JSONObject?>(null) }
    var wanList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var wlanList by remember { mutableStateOf(emptyList<JSONObject>()) }

    var editingWlan by remember { mutableStateOf<JSONObject?>(null) }
    var editingWan by remember { mutableStateOf<JSONObject?>(null) }

    fun loadDetail() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request(
                "admin_genieacs_device_detail",
                mapOf("id" to deviceId, "host_id" to hostId)
            )
            if (res.status && res.data != null) {
                deviceInfo = res.data.optJSONObject("device")
                val wArr = res.data.optJSONArray("wan_connections") ?: JSONArray()
                val wTemp = mutableListOf<JSONObject>()
                for (i in 0 until wArr.length()) wTemp.add(wArr.getJSONObject(i))
                wanList = wTemp

                val wlArr = res.data.optJSONArray("wlan_configs") ?: JSONArray()
                val wlTemp = mutableListOf<JSONObject>()
                for (i in 0 until wlArr.length()) wlTemp.add(wlArr.getJSONObject(i))
                wlanList = wlTemp
            }
            isLoading = false
        }
    }

    LaunchedEffect(deviceId) { loadDetail() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceInfo?.optString("serial_number")?.ifEmpty { deviceId } ?: deviceId,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                        color = ArnetaGoldPrimary
                    )
                    Text(
                        text = "${deviceInfo?.optString("manufacturer", "-")} ${deviceInfo?.optString("product_class", "-")} • Uptime: ${deviceInfo?.optString("uptime", "-")}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { loadDetail() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = ArnetaCyanIncome)
                }
            }
        },
        text = {
            if (isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Info Dasar & Sensor
                    Surface(
                        color = Color(0xFF0B1120),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Informasi Perangkat & Sensor", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaCyanIncome)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Text("Pelanggan: ${deviceInfo?.optString("fullname")?.ifEmpty { "-" } ?: "-"} (${deviceInfo?.optString("pppoe_username", "N/A")})", fontSize = 11.5.sp)
                            Text("WAN IP: ${deviceInfo?.optString("wan_ip", "-")} • TR-069 IP: ${deviceInfo?.optString("tr069_ip", "-")}", fontSize = 11.5.sp)
                            Text("Suhu: ${deviceInfo?.optString("temperature", "N/A")} • Redaman RX: ${deviceInfo?.optString("rx_power", "N/A")}", fontSize = 11.5.sp, color = ArnetaGoldLight)
                            Text("Software: ${deviceInfo?.optString("software_version", "-")} • Hardware: ${deviceInfo?.optString("hardware_version", "-")}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    // Konfigurasi WiFi (WLAN)
                    Surface(
                        color = Color(0xFF0B1120),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Jaringan WiFi (WLAN Configurations)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaGreenSuccess)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            if (wlanList.isEmpty()) {
                                Text("Data WLAN belum tersedia.", fontSize = 11.5.sp)
                            } else {
                                wlanList.forEach { wl ->
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SSID #${wl.optInt("index")}: ${wl.optString("ssid")}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Pass: ${wl.optString("password").ifEmpty { "(Tersembunyi)" }} • Ch: ${wl.optString("channel")} • 📱 ${wl.optInt("connected_devices")} HP",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { editingWlan = wl },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Ubah WiFi", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Konfigurasi WAN
                    Surface(
                        color = Color(0xFF0B1120),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Koneksi WAN (PPPoE / IP)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaGoldPrimary)
                                TextButton(
                                    onClick = {
                                        editingWan = JSONObject().apply {
                                            put("index", (wanList.size + 1).coerceAtMost(8))
                                            put("type", "ppp")
                                        }
                                    }
                                ) {
                                    Text("+Tambah WAN", fontSize = 11.sp, color = ArnetaCyanIncome)
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            if (wanList.isEmpty()) {
                                Text("Data koneksi WAN belum tersedia.", fontSize = 11.5.sp)
                            } else {
                                wanList.forEach { w ->
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "WAN #${w.optInt("index")} (${w.optString("type").uppercase()}) • IP: ${w.optString("external_ip")}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "User: ${w.optString("username", "-")} • VLAN: ${w.optString("vlan").ifEmpty { "-" }}",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { editingWan = w },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Edit WAN", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaGoldPrimary)
            ) {
                Text("Tutup")
            }
        }
    )

    if (editingWlan != null) {
        val wl = editingWlan!!
        var ssid by remember { mutableStateOf(wl.optString("ssid", "")) }
        var password by remember { mutableStateOf("") }
        var secMode by remember { mutableStateOf("WPA2PSK") }

        AlertDialog(
            onDismissRequest = { editingWlan = null },
            title = { Text("Ubah WiFi (WLAN #${wl.optInt("index")})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("Nama WiFi (SSID)") }, singleLine = true)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password Baru (Kosongkan jika tetap)") },
                        singleLine = true
                    )
                    CompactDropdownSelector(
                        label = "Keamanan WiFi",
                        selectedValue = secMode,
                        options = listOf(
                            "WPA2PSK" to "WPA2-PSK (Direkomendasikan)",
                            "WPA2PSKWPAPSK" to "WPA/WPA2-PSK",
                            "WPAPSK" to "WPA-PSK",
                            "None" to "Open (Tanpa Password)"
                        ),
                        icon = Icons.Filled.Security,
                        onValueSelected = { secMode = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val sRes = ApiClient.request(
                                "admin_genieacs_wlan_save",
                                mapOf(
                                    "id" to deviceId,
                                    "host_id" to hostId,
                                    "wlan_index" to wl.optInt("index", 1),
                                    "ssid" to ssid.trim(),
                                    "password" to password,
                                    "security_mode" to secMode,
                                    "source" to wl.optString("source", "tr098")
                                )
                            )
                            Toast.makeText(context, sRes.message, Toast.LENGTH_LONG).show()
                            if (sRes.status) {
                                editingWlan = null
                                loadDetail()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Simpan WiFi", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWlan = null }) { Text("Batal") }
            }
        )
    }

    if (editingWan != null) {
        val w = editingWan!!
        var connType by remember { mutableStateOf(w.optString("type", "ppp")) }
        var username by remember { mutableStateOf(w.optString("username", "")) }
        var password by remember { mutableStateOf("") }
        var vlan by remember { mutableStateOf(w.optString("vlan", "")) }

        AlertDialog(
            onDismissRequest = { editingWan = null },
            title = { Text("Konfigurasi WAN #${w.optInt("index", 1)}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactDropdownSelector(
                        label = "Tipe Koneksi WAN",
                        selectedValue = connType,
                        options = listOf("ppp" to "PPP (PPPoE)", "ip" to "IP (DHCP / Static)"),
                        icon = Icons.Filled.Lan,
                        onValueSelected = { connType = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("PPPoE Username") }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("PPPoE Password Baru") }, singleLine = true)
                    OutlinedTextField(
                        value = vlan,
                        onValueChange = { vlan = it },
                        label = { Text("VLAN ID (Opsional, misal: 10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val sRes = ApiClient.request(
                                "admin_genieacs_wan_save",
                                mapOf(
                                    "id" to deviceId,
                                    "host_id" to hostId,
                                    "connection_index" to w.optInt("index", 1),
                                    "connection_type" to connType,
                                    "username" to username.trim(),
                                    "password" to password,
                                    "vlan" to vlan.trim()
                                )
                            )
                            Toast.makeText(context, sRes.message, Toast.LENGTH_LONG).show()
                            if (sRes.status) {
                                editingWan = null
                                loadDetail()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Simpan WAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWan = null }) { Text("Batal") }
            }
        )
    }
}
