package id.biz.arneta.admin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1. DIALOG TAMBAH / EDIT SERVER HOST GENIEACS (MENDUKUNG HTTP & HTTPS)
 */
@Composable
internal fun GenieAcsHostFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val initProto = remember(initial) {
        val raw = initial?.optString("protocol", "")?.lowercase() ?: ""
        if (raw == "https" || initial?.optInt("port", 7557) == 443) "https" else "http"
    }

    var name by remember { mutableStateOf(initial?.optString("name") ?: "GenieACS Utama") }
    var protocol by remember { mutableStateOf(initProto) }
    var host by remember {
        mutableStateOf(
            (initial?.optString("host") ?: "")
                .replace("https://", "", ignoreCase = true)
                .replace("http://", "", ignoreCase = true)
        )
    }
    var port by remember {
        mutableStateOf(
            initial?.optInt("port", if (initProto == "https") 443 else 7557)?.toString() ?: "7557"
        )
    }
    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var password by remember { mutableStateOf(initial?.optString("password") ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val previewEndpoint = remember(protocol, host, port) {
        val cleanH = host.trim().trimEnd('/')
        val pInt = port.toIntOrNull() ?: if (protocol == "https") 443 else 7557
        val portSuffix = if ((protocol == "https" && pInt == 443) || (protocol == "http" && pInt == 80) || cleanH.contains(":")) {
            ""
        } else {
            ":$pInt"
        }
        "$protocol://${cleanH.ifEmpty { "host-genieacs" }}$portSuffix"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = ArnetaGoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEdit) "Edit Host GenieACS" else "Tambah Host GenieACS",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Pilih Protokol Koneksi NBI:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = protocol == "http",
                        onClick = {
                            protocol = "http"
                            if (port == "443" || port.isBlank()) port = "7557"
                        },
                        label = { Text("🏠 HTTP (IP Lokal / 7557)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = protocol == "https",
                        onClick = {
                            protocol = "https"
                            if (port == "7557" || port.isBlank()) port = "443"
                        },
                        label = { Text("🔒 HTTPS (Domain / Tunnel)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Server (Misal: GenieACS Pusat)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(if (protocol == "https") "Domain / Host HTTPS" else "IP / Host GenieACS") },
                        placeholder = { Text(if (protocol == "https") "acs.arneta.biz.id" else "192.168.1.200") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.replace("[^0-9]".toRegex(), "") },
                        label = { Text("Port") },
                        placeholder = { Text(if (protocol == "https") "443" else "7557") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "URL NBI: $previewEndpoint",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ArnetaCyanIncome
                        )
                        Text(
                            text = if (protocol == "https") {
                                "Mendukung Cloudflare Tunnel / Reverse Proxy Nginx tanpa port :7557."
                            } else {
                                "Port default NBI REST API GenieACS adalah 7557."
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username Auth NBI (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password Auth NBI (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && name.isNotBlank() && host.isNotBlank(),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_genieacs_host_save",
                            mapOf(
                                "id" to (initial?.optString("id") ?: ""),
                                "name" to name.trim(),
                                "protocol" to protocol,
                                "host" to host.trim(),
                                "port" to (port.toIntOrNull() ?: if (protocol == "https") 443 else 7557),
                                "username" to username.trim(),
                                "password" to password
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan Host", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * 2. DIALOG DETAIL LENGKAP PERANGKAT TR-069 (INFO, OPTIK, WIFI, WAN, HP TERHUBUNG & RAW JSON)
 */
@Composable
internal fun GenieAcsDeviceDetailDialog(
    deviceId: String,
    hostId: String,
    onDismiss: () -> Unit,
    onDeviceDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detailTab by remember { mutableStateOf("overview") } // overview | wlan | wan | hosts | json
    var isLoading by remember { mutableStateOf(true) }
    var deviceInfo by remember { mutableStateOf<JSONObject?>(null) }
    var wanList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var availWanIndex by remember { mutableStateOf<Int?>(null) }
    var wlanList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var connectedHostsList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var pendingTasksList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var rawJsonText by remember { mutableStateOf("") }
    var isFullRawLoaded by remember { mutableStateOf(false) }

    // State Sub-Dialog Edit WLAN, Edit/Add WAN, Tambah Tag & Konfirmasi Berbahaya
    var editingWlan by remember { mutableStateOf<JSONObject?>(null) }
    var editingWan by remember { mutableStateOf<JSONObject?>(null) }
    var isAddWanMode by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var dangerousActionConfirm by remember { mutableStateOf<String?>(null) } // reboot | factory_reset | delete_device

    fun loadDetail(loadFullRaw: Boolean = isFullRawLoaded) {
        isLoading = true
        scope.launch {
            val res = ApiClient.request(
                "admin_genieacs_device_detail",
                mapOf(
                    "id" to deviceId,
                    "host_id" to hostId,
                    "include_raw" to if (loadFullRaw) "1" else "0"
                )
            )
            if (res.status && res.data != null) {
                deviceInfo = res.data.optJSONObject("device")

                val wArr = res.data.optJSONArray("wan_connections") ?: JSONArray()
                val wTemp = mutableListOf<JSONObject>()
                for (i in 0 until wArr.length()) wTemp.add(wArr.getJSONObject(i))
                wanList = wTemp

                availWanIndex = if (res.data.isNull("avail_wan_index")) null else res.data.optInt("avail_wan_index", 0).takeIf { it in 1..8 }

                val wlArr = res.data.optJSONArray("wlan_configs") ?: JSONArray()
                val wlTemp = mutableListOf<JSONObject>()
                for (i in 0 until wlArr.length()) wlTemp.add(wlArr.getJSONObject(i))
                wlanList = wlTemp

                val chArr = res.data.optJSONArray("connected_hosts") ?: JSONArray()
                val chTemp = mutableListOf<JSONObject>()
                for (i in 0 until chArr.length()) chTemp.add(chArr.getJSONObject(i))
                connectedHostsList = chTemp

                val tkArr = res.data.optJSONArray("pending_tasks") ?: JSONArray()
                val tkTemp = mutableListOf<JSONObject>()
                for (i in 0 until tkArr.length()) tkTemp.add(tkArr.getJSONObject(i))
                pendingTasksList = tkTemp

                rawJsonText = res.data.optString("raw_json", "{}")
                isFullRawLoaded = loadFullRaw
            } else {
                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    fun executeAction(subAction: String, extraParams: Map<String, Any?> = emptyMap()) {
        scope.launch {
            val params = mutableMapOf<String, Any?>(
                "id" to deviceId,
                "host_id" to hostId,
                "sub_action" to subAction
            )
            params.putAll(extraParams)
            val res = ApiClient.request("admin_genieacs_device_action", params)
            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
            if (res.status) {
                if (subAction == "delete_device") {
                    onDeviceDeleted()
                } else {
                    loadDetail()
                }
            }
        }
    }

    LaunchedEffect(deviceId) {
        loadDetail(false)
    }

    val isOnline = deviceInfo?.optBoolean("online", false) ?: false
    val sn = deviceInfo?.optString("serial_number")?.ifEmpty { deviceId } ?: deviceId
    val fullname = deviceInfo?.optString("fullname", "") ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                                fontSize = 15.sp,
                                color = ArnetaGoldPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${deviceInfo?.optString("manufacturer", "-")} ${deviceInfo?.optString("product_class", "-")} • Uptime: ${deviceInfo?.optString("uptime", "-")}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = { loadDetail() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaCyanIncome)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sub-Tab Navigasi di dalam Detail Perangkat
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "overview" to "Info & Optik",
                        "wlan" to "WiFi (${wlanList.size})",
                        "wan" to "WAN (${wanList.size})",
                        "hosts" to "HP Aktif (${connectedHostsList.size})",
                        "json" to "Raw JSON"
                    ).forEach { (k, lbl) ->
                        FilterChip(
                            selected = detailTab == k,
                            onClick = { detailTab = k },
                            label = { Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        },
        text = {
            if (isLoading && deviceInfo == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (detailTab) {
                        "overview" -> {
                            val d = deviceInfo ?: JSONObject()

                            // Baris Tombol Perintah Cepat TR-069 (Summon, Refresh, Reboot, Reset)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilledTonalButton(
                                    onClick = { executeAction("summon") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = ArnetaGreenSuccess, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Summon", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = { executeAction("refresh") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh Parameter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { dangerousActionConfirm = "reboot" },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = ArnetaOrangeActive, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reboot", fontSize = 11.sp, color = ArnetaOrangeActive)
                                }
                                OutlinedButton(
                                    onClick = { dangerousActionConfirm = "factory_reset" },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Factory Reset", fontSize = 11.sp, color = ArnetaRedDanger)
                                }
                                OutlinedButton(
                                    onClick = { dangerousActionConfirm = "delete_device" },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus ACS", fontSize = 11.sp, color = ArnetaRedDanger)
                                }
                            }

                            // 1. Kartu Status & Indikator Optik (Sesuai Panel Status di genieacs_device.tpl)
                            Surface(
                                color = Color(0xFF0B1120),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ArnetaCyanIncome.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text("Status & Diagnostik Optik PON", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaCyanIncome)
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GenieAcsRxPowerBadge(
                                            rxPower = d.optString("rx_power", "N/A"),
                                            rxStatus = d.optString("rx_power_status", "unknown")
                                        )
                                        GenieAcsTemperatureBadge(
                                            temperature = d.optString("temperature", "N/A"),
                                            tempStatus = d.optString("temperature_status", "unknown")
                                        )
                                        if (d.optString("tx_power", "N/A") != "N/A") {
                                            Surface(
                                                color = ArnetaIndigoAccent.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "TX: ${d.optString("tx_power")}",
                                                    fontSize = 10.5.sp,
                                                    color = ArnetaGoldLight,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    GenieInfoRow("Pelanggan", "${d.optString("fullname").ifEmpty { "-" }} (PPPoE: ${d.optString("pppoe_username", "N/A")})")
                                    GenieInfoRow("WAN IP", d.optString("wan_ip", "-"))
                                    GenieInfoRow("TR-069 IP", d.optString("tr069_ip", "-"))
                                    GenieInfoRow("Last Inform", d.optString("last_inform", "-"))
                                    GenieInfoRow("Uptime", d.optString("uptime", "-"))
                                }
                            }

                            // 2. Kartu Basic Information (Sesuai Panel Basic Information di genieacs_device.tpl)
                            Surface(
                                color = Color(0xFF0B1120),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text("Informasi Dasar Perangkat (Basic Info)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaGoldPrimary)
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    GenieInfoRow("Device ID", d.optString("id", "-"))
                                    GenieInfoRow("Serial Number", d.optString("serial_number", "-"))
                                    GenieInfoRow("Manufacturer", d.optString("manufacturer", "-"))
                                    GenieInfoRow("OUI", d.optString("oui", "-"))
                                    GenieInfoRow("Product Class", d.optString("product_class", "-"))
                                    GenieInfoRow("Software Version", d.optString("software_version", "-"))
                                    GenieInfoRow("Hardware Version", d.optString("hardware_version", "-"))
                                }
                            }

                            // 3. Kartu Tags & Antrean Task
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
                                        Text("Label Tag Perangkat (_tags)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaGreenSuccess)
                                        TextButton(
                                            onClick = {
                                                newTagInput = ""
                                                showAddTagDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Tambah Tag", fontSize = 11.sp, color = ArnetaGoldPrimary)
                                        }
                                    }

                                    val tagsArr = d.optJSONArray("tags") ?: JSONArray()
                                    if (tagsArr.length() == 0) {
                                        Text("Belum ada tag pada perangkat ini.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            for (i in 0 until tagsArr.length()) {
                                                val tg = tagsArr.optString(i)
                                                InputChip(
                                                    selected = true,
                                                    onClick = { executeAction("remove_tag", mapOf("tag" to tg)) },
                                                    label = { Text(tg, fontSize = 11.sp) },
                                                    trailingIcon = {
                                                        Icon(Icons.Filled.Close, contentDescription = "Hapus Tag", modifier = Modifier.size(13.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (pendingTasksList.isNotEmpty()) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "⚠️ ${pendingTasksList.size} Task Mengantre di ACS",
                                                fontSize = 11.5.sp,
                                                color = ArnetaOrangeActive,
                                                fontWeight = FontWeight.Bold
                                            )
                                            TextButton(onClick = { executeAction("clear_tasks") }) {
                                                Text("Bersihkan Task", fontSize = 11.sp, color = ArnetaRedDanger)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "wlan" -> {
                            if (wlanList.isEmpty()) {
                                Text("Data WLAN belum tersedia pada perangkat ini. Tekan tombol Refresh Parameter di tab Info.", fontSize = 12.sp)
                            } else {
                                wlanList.forEach { wl ->
                                    val idx = wl.optInt("index", 1)
                                    val ssid = wl.optString("ssid", "-")
                                    val pass = wl.optString("password", "")
                                    val enabled = wl.optBoolean("enable", true)
                                    val band = wl.optString("band", "2.4GHz")

                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ArnetaGreenSuccess.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = ArnetaGreenSuccess, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "WLAN #$idx ($band): $ssid",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.5.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Surface(
                                                    color = if (enabled) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (enabled) "ENABLED" else "DISABLED",
                                                        color = if (enabled) ArnetaGreenSuccess else ArnetaRedDanger,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            GenieInfoRow("Password Saat Ini", pass.ifEmpty { "(Tersembunyi / Kosong)" })
                                            GenieInfoRow("Security / Beacon", "${wl.optString("security", "-")} (${wl.optString("beacon_type", "-")})")
                                            GenieInfoRow("Channel & Standard", "Ch ${wl.optString("channel", "-")} • ${wl.optString("standard", "-")}")
                                            GenieInfoRow("BSSID & Max Rate", "${wl.optString("mac", "-")} • ${wl.optString("max_bitrate", "-")}")
                                            GenieInfoRow("Perangkat Terhubung", "${wl.optInt("connected_devices", 0)} Perangkat")

                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (pass.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = {
                                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cb.setPrimaryClip(ClipData.newPlainText("Password WiFi", pass))
                                                            Toast.makeText(context, "Password WiFi disalin", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Salin Pass", fontSize = 11.sp, color = ArnetaCyanIncome)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = { editingWlan = wl },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Edit WiFi #$idx", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "wan" -> {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Daftar Koneksi WAN (Index 1-8)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ArnetaGoldPrimary
                                )
                                Button(
                                    onClick = {
                                        val nextIdx = availWanIndex ?: ((wanList.size + 1).coerceAtMost(8))
                                        isAddWanMode = true
                                        editingWan = JSONObject().apply {
                                            put("index", nextIdx)
                                            put("type", "ppp")
                                            put("username", deviceInfo?.optString("pppoe_username")?.takeIf { it != "N/A" } ?: "")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGreenSuccess, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (availWanIndex != null) "+Add WAN (#$availWanIndex)" else "+Add WAN",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (wanList.isEmpty()) {
                                Text("Data koneksi WAN belum tersedia. Gunakan tombol +Add WAN untuk menambahkan profil WAN baru.", fontSize = 12.sp)
                            } else {
                                wanList.forEach { w ->
                                    val idx = w.optInt("index", 1)
                                    val wType = w.optString("type", "ppp").uppercase()
                                    val enabled = w.optBoolean("enable", true)

                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "WAN Connection #$idx ($wType)",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp,
                                                    color = ArnetaGoldPrimary
                                                )
                                                Surface(
                                                    color = if (enabled) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (enabled) "ENABLED" else "DISABLED",
                                                        color = if (enabled) ArnetaGreenSuccess else ArnetaRedDanger,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            GenieInfoRow("Connection Type", w.optString("connection_type", "-"))
                                            if (wType == "PPP") {
                                                GenieInfoRow("PPPoE Username", w.optString("username", "-"))
                                                if (w.optString("password").isNotEmpty()) {
                                                    GenieInfoRow("PPPoE Password", w.optString("password"))
                                                }
                                            }
                                            GenieInfoRow("External IP", w.optString("external_ip", "-"))
                                            GenieInfoRow("Gateway & Subnet", "${w.optString("gateway", "-")} / ${w.optString("subnet", "-")}")
                                            GenieInfoRow("DNS Servers", w.optString("dns", "-"))
                                            GenieInfoRow("VLAN ID & NAT", "VLAN: ${w.optString("vlan").ifEmpty { "-" }} • NAT: ${if (w.optBoolean("nat")) "Yes" else "No"}")

                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Button(
                                                    onClick = {
                                                        isAddWanMode = false
                                                        editingWan = w
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaCyanIncome, contentColor = Color.Black),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Edit WAN #$idx", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "hosts" -> {
                            if (connectedHostsList.isEmpty()) {
                                Text(
                                    text = "Belum ada daftar perangkat/HP aktif yang dilaporkan oleh tabel LAN Host ONT. Tekan tombol Refresh Parameter pada tab Info untuk memperbarui tabel.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            } else {
                                connectedHostsList.forEach { h ->
                                    val isAct = h.optBoolean("active", true)
                                    val iface = h.optString("interface_type", "WiFi")
                                    val sig = h.optString("signal", "")

                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = if (iface.contains("LAN", true)) Icons.Filled.SettingsEthernet else Icons.Filled.Smartphone,
                                                    contentDescription = null,
                                                    tint = if (isAct) ArnetaGreenSuccess else Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = h.optString("hostname", "Perangkat"),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "IP: ${h.optString("ip_address")} • MAC: ${h.optString("mac_address")}",
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Surface(
                                                    color = ArnetaCyanIncome.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = iface,
                                                        color = ArnetaCyanIncome,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (sig.isNotEmpty()) {
                                                    Text(sig, fontSize = 10.sp, color = ArnetaGoldLight)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "json" -> {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Raw Device Data (JSON)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ArnetaGoldPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!isFullRawLoaded) {
                                        OutlinedButton(
                                            onClick = { loadDetail(true) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Muat Full Tree", fontSize = 10.5.sp, color = ArnetaCyanIncome)
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("GenieACS JSON", rawJsonText))
                                            Toast.makeText(context, "JSON disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salin JSON", fontSize = 10.5.sp)
                                    }
                                }
                            }

                            Surface(
                                color = Color(0xFF0B1120),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = rawJsonText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFA7F3D0),
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .horizontalScroll(rememberScrollState())
                                )
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
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        }
    )

    // Sub-Dialog Edit WiFi (WLAN)
    if (editingWlan != null) {
        GenieAcsWlanFormDialog(
            deviceId = deviceId,
            hostId = hostId,
            wlanObj = editingWlan!!,
            onDismiss = { editingWlan = null },
            onSaved = {
                editingWlan = null
                loadDetail()
            }
        )
    }

    // Sub-Dialog Edit / Add WAN
    if (editingWan != null) {
        GenieAcsWanFormDialog(
            deviceId = deviceId,
            hostId = hostId,
            wanObj = editingWan!!,
            isAddMode = isAddWanMode,
            onDismiss = { editingWan = null },
            onSaved = {
                editingWan = null
                loadDetail()
            }
        )
    }

    // Sub-Dialog Tambah Tag
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Tambah Tag Perangkat", fontWeight = FontWeight.Bold, fontSize = 15.5.sp) },
            text = {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    label = { Text("Nama Tag (Misal: ODP-04 / VIP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newTagInput.isNotBlank(),
                    onClick = {
                        val tg = newTagInput.trim()
                        showAddTagDialog = false
                        executeAction("add_tag", mapOf("tag" to tg))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Simpan Tag", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Batal") }
            }
        )
    }

    // Sub-Dialog Konfirmasi Reboot / Factory Reset / Delete Device
    if (dangerousActionConfirm != null) {
        val act = dangerousActionConfirm!!
        val (titleTxt, descTxt) = when (act) {
            "factory_reset" -> "Factory Reset Perangkat?" to "PERINGATAN: Seluruh konfigurasi WAN dan WiFi pada ONT akan dikembalikan ke pengaturan pabrik!"
            "delete_device" -> "Hapus Perangkat dari GenieACS?" to "Perangkat ini akan dihapus dari daftar database GenieACS."
            else -> "Reboot Perangkat Sekarang?" to "Perintah Reboot akan dikirim ke ONT/Router."
        }
        AlertDialog(
            onDismissRequest = { dangerousActionConfirm = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(titleTxt, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
            text = { Text(descTxt, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f)) },
            confirmButton = {
                Button(
                    onClick = {
                        dangerousActionConfirm = null
                        executeAction(act)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaRedDanger, contentColor = Color.White)
                ) {
                    Text("Ya, Lanjutkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { dangerousActionConfirm = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * 3. DIALOG FORM EDIT WLAN (SESUAI GENIEACS_DEVICE_WLAN.TPL + CHANNEL & ENABLE RADIO)
 */
@Composable
internal fun GenieAcsWlanFormDialog(
    deviceId: String,
    hostId: String,
    wlanObj: JSONObject,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wlanIndex = wlanObj.optInt("index", 1)
    val source = wlanObj.optString("source", "tr098")

    var ssid by remember { mutableStateOf(wlanObj.optString("ssid", "")) }
    var password by remember { mutableStateOf("") }
    var securityMode by remember { mutableStateOf(wlanObj.optString("security_mode", "WPA2PSK")) }
    var channel by remember {
        mutableStateOf(
            wlanObj.optString("channel", "").takeIf { it != "-" } ?: ""
        )
    }
    var isEnabled by remember { mutableStateOf(wlanObj.optBoolean("enable", true)) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Konfigurasi WLAN #$wlanIndex (${wlanObj.optString("band", "2.4GHz")})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Radio WiFi #$wlanIndex", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Nama WiFi (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password Baru (Kosongkan jika tidak diubah)") },
                    placeholder = { Text("Minimal 8 karakter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (wlanObj.optString("password").isNotEmpty()) {
                    Text(
                        text = "Password saat ini: ${wlanObj.optString("password")}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ArnetaGoldPrimary
                    )
                }

                CompactDropdownSelector(
                    label = "Mode Keamanan (Security Mode)",
                    selectedValue = securityMode,
                    options = listOf(
                        "WPA2PSK" to "WPA2-PSK (AES - Direkomendasikan)",
                        "WPA2PSKWPAPSK" to "WPA/WPA2-PSK (Mixed)",
                        "WPAPSK" to "WPA-PSK (TKIP)",
                        "None" to "Open (Tanpa Password)"
                    ),
                    icon = Icons.Filled.Security,
                    onValueSelected = { securityMode = it },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it.replace("[^0-9]".toRegex(), "") },
                    label = { Text("Channel WiFi (Opsional, misal: 1, 6, 11)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && ssid.isNotBlank() && (securityMode == "None" || password.isEmpty() || password.length >= 8),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_genieacs_wlan_save",
                            mapOf(
                                "id" to deviceId,
                                "host_id" to hostId,
                                "wlan_index" to wlanIndex,
                                "ssid" to ssid.trim(),
                                "password" to password,
                                "security_mode" to securityMode,
                                "channel" to channel.trim(),
                                "enable" to if (isEnabled) "1" else "0",
                                "source" to source
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
            ) {
                Text(if (isSaving) "Menyimpan..." else "Update WLAN", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * 4. DIALOG FORM ADD / EDIT WAN CONNECTION (SESUAI GENIEACS_DEVICE_WAN.TPL + AUTO-FILL PELANGGAN)
 */
@Composable
internal fun GenieAcsWanFormDialog(
    deviceId: String,
    hostId: String,
    wanObj: JSONObject,
    isAddMode: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connIndex by remember { mutableIntStateOf(wanObj.optInt("index", 1)) }
    var connType by remember { mutableStateOf(wanObj.optString("type", "ppp")) } // ppp | ip
    var username by remember { mutableStateOf(wanObj.optString("username", "")) }
    var password by remember { mutableStateOf("") }
    var vlan by remember { mutableStateOf(wanObj.optString("vlan", "")) }
    var natMode by remember { mutableStateOf("") } // "" (Unchanged) | "1" (Enable) | "0" (Disable)
    var showCustomerPicker by remember { mutableStateOf(false) }
    var pickedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAddMode) "Tambah Koneksi WAN (#$connIndex)" else "Edit WAN Connection #$connIndex",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isAddMode) {
                    CompactDropdownSelector(
                        label = "Connection Index (Slot 1 - 8)",
                        selectedValue = connIndex.toString(),
                        options = (1..8).map { "$it" to "WANConnectionDevice.$it" },
                        icon = Icons.Filled.FormatListNumbered,
                        onValueSelected = { connIndex = it.toIntOrNull() ?: connIndex },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CompactDropdownSelector(
                    label = "Tipe Koneksi WAN",
                    selectedValue = connType,
                    options = listOf(
                        "ppp" to "PPP (PPPoE)",
                        "ip" to "IP (DHCP / Static)"
                    ),
                    icon = Icons.Filled.Lan,
                    onValueSelected = { connType = it },
                    modifier = Modifier.fillMaxWidth()
                )

                if (connType == "ppp") {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kredensial PPPoE:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
                        TextButton(onClick = { showCustomerPicker = !showCustomerPicker }) {
                            Icon(Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showCustomerPicker) "Tutup Pemilih" else "Ambil dari Pelanggan",
                                fontSize = 11.sp,
                                color = ArnetaGoldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCustomerPicker) {
                        CustomerLiveSearchPicker(
                            selectedCustomer = pickedCustomer,
                            onCustomerSelected = { cust ->
                                pickedCustomer = cust
                                if (cust != null) {
                                    val pppU = cust.optString("pppoe_username").ifEmpty { cust.optString("username") }
                                    val pppP = cust.optString("pppoe_password")
                                    username = pppU
                                    if (pppP.isNotEmpty()) password = pppP
                                    showCustomerPicker = false
                                }
                            },
                            label = "Pilih Pelanggan PHPNuxBill"
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("PPPoE Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("PPPoE Password Baru (Kosongkan jika tetap)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = vlan,
                    onValueChange = { vlan = it.replace("[^0-9]".toRegex(), "") },
                    label = { Text("VLAN ID (Opsional, misal: 10 atau 100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                CompactDropdownSelector(
                    label = "Status NAT (NAT Enabled)",
                    selectedValue = natMode,
                    placeholder = "Biarkan Tetap (Unchanged)",
                    options = listOf(
                        "" to "Biarkan Tetap (Leave Unchanged)",
                        "1" to "Enable NAT",
                        "0" to "Disable NAT"
                    ),
                    icon = Icons.Filled.Security,
                    onValueSelected = { natMode = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_genieacs_wan_save",
                            mapOf(
                                "id" to deviceId,
                                "host_id" to hostId,
                                "connection_index" to connIndex,
                                "connection_type" to connType,
                                "username" to username.trim(),
                                "password" to password,
                                "vlan" to vlan.trim(),
                                "nat" to natMode,
                                "customer_id" to (pickedCustomer?.optInt("id") ?: 0)
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
            ) {
                Text(
                    text = if (isSaving) "Menyimpan..." else if (isAddMode) "Tambah WAN" else "Update WAN",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun GenieInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(0.6f)
        )
    }
}
