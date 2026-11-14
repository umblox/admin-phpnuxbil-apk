package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * DIALOG FORM TAMBAH & EDIT PAKET (HOTSPOT, PPPOE, SALDO, VPN)
 * Sesuai system/controllers/services.php
 */
@Composable
internal fun PlanFormDialog(
    initial: JSONObject?,
    defaultType: String,
    allPlans: List<JSONObject>,
    bandwidths: List<JSONObject>,
    routers: List<JSONObject>,
    pools: List<JSONObject>,
    devices: List<String>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val currentType = remember(initial, defaultType) {
        val raw = initial?.optString("type")?.ifEmpty { null } ?: defaultType
        when (raw.uppercase()) {
            "PPPOE" -> "PPPOE"
            "BALANCE" -> "Balance"
            "VPN" -> "VPN"
            else -> "Hotspot"
        }
    }
    val isBalance = currentType.equals("Balance", ignoreCase = true)
    val isHotspot = currentType.equals("Hotspot", ignoreCase = true)
    val isPppoeOrVpn = currentType.equals("PPPOE", ignoreCase = true) || currentType.equals("VPN", ignoreCase = true)

    var namePlan by remember { mutableStateOf(initial?.optString("name_plan") ?: "") }
    var price by remember {
        mutableStateOf(
            if (initial != null) initial.optDouble("price", 0.0).toLong().toString() else ""
        )
    }
    var priceOld by remember { mutableStateOf(initial?.optString("price_old", "") ?: "") }
    var enabledStatus by remember { mutableIntStateOf(initial?.optInt("enabled", 1) ?: 1) }
    var prepaidMode by remember {
        mutableStateOf(
            initial?.optString("prepaid")?.ifEmpty { null }
                ?: if (currentType == "PPPOE") "no" else "yes"
        )
    }
    var planType by remember { mutableStateOf(initial?.optString("plan_type")?.ifEmpty { null } ?: "Personal") }

    // Khusus Hotspot (Unlimited vs Limited)
    var typebp by remember { mutableStateOf(initial?.optString("typebp")?.ifEmpty { null } ?: "Unlimited") }
    var limitType by remember { mutableStateOf(initial?.optString("limit_type")?.ifEmpty { null } ?: "Time_Limit") }
    var timeLimit by remember { mutableStateOf(initial?.optInt("time_limit", 4)?.toString() ?: "4") }
    var timeUnit by remember { mutableStateOf(initial?.optString("time_unit")?.ifEmpty { null } ?: "Hrs") }
    var dataLimit by remember { mutableStateOf(initial?.optInt("data_limit", 1)?.toString() ?: "1") }
    var dataUnit by remember { mutableStateOf(initial?.optString("data_unit")?.ifEmpty { null } ?: "GB") }
    var sharedUsers by remember { mutableStateOf(initial?.optInt("shared_users", 1)?.toString() ?: "1") }

    // Bandwidth, Masa Aktif, Router, Radius, Pool & Device
    var bwId by remember {
        mutableIntStateOf(
            initial?.optInt("id_bw")?.takeIf { it > 0 } ?: (bandwidths.firstOrNull()?.optInt("id") ?: 0)
        )
    }
    var validity by remember { mutableStateOf(initial?.optInt("validity", 30)?.toString() ?: "30") }
    var validityUnit by remember {
        mutableStateOf(
            initial?.optString("validity_unit")?.ifEmpty { null } ?: "Days"
        )
    }
    var isRadius by remember { mutableStateOf((initial?.optInt("is_radius", 0) ?: 0) == 1) }
    var routerName by remember {
        mutableStateOf(
            initial?.optString("raw_routers", initial.optString("routers", ""))?.takeIf {
                it.isNotEmpty() && !it.equals("Radius", true)
            } ?: (routers.firstOrNull()?.optString("name") ?: "")
        )
    }
    var poolName by remember {
        mutableStateOf(
            initial?.optString("pool")?.ifEmpty { null }
                ?: (pools.firstOrNull()?.optString("pool_name") ?: "")
        )
    }

    val defaultDevice = remember(currentType, isRadius) {
        if (isRadius) "Radius"
        else when (currentType) {
            "PPPOE" -> "MikrotikPppoe"
            "VPN" -> "MikrotikVpn"
            else -> "MikrotikHotspot"
        }
    }
    var deviceName by remember {
        mutableStateOf(initial?.optString("device")?.ifEmpty { null } ?: defaultDevice)
    }
    var expiredDate by remember {
        mutableStateOf(initial?.optInt("expired_date", 20)?.takeIf { it in 1..28 }?.toString() ?: "20")
    }
    var planExpiredId by remember { mutableIntStateOf(initial?.optInt("plan_expired", 0) ?: 0) }
    var showAdvancedScripts by remember { mutableStateOf(false) }
    var onLoginScript by remember { mutableStateOf(initial?.optString("on_login", "") ?: "") }
    var onLogoutScript by remember { mutableStateOf(initial?.optString("on_logout", "") ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val bwOptions = remember(bandwidths) {
        bandwidths.map { b ->
            b.optInt("id").toString() to "${b.optString("name_bw")} (${b.optInt("rate_down")}${b.optString("rate_down_unit")}/${b.optInt("rate_up")}${b.optString("rate_up_unit")})"
        }
    }

    val routerDropdownOptions = remember(routers) {
        routers.map { r -> r.optString("name") to r.optString("name") }
    }

    val poolDropdownOptions = remember(pools, routerName, isRadius) {
        val filtered = pools.filter { p ->
            val pRouter = p.optString("routers", "")
            pRouter.isEmpty() ||
                (isRadius && pRouter.equals("radius", true)) ||
                (!isRadius && pRouter.equals(routerName, true))
        }
        val source = if (filtered.isNotEmpty()) filtered else pools
        source.map { p -> p.optString("pool_name") to "${p.optString("pool_name")} (${p.optString("range_ip")})" }
    }

    LaunchedEffect(poolDropdownOptions) {
        if (isPppoeOrVpn && poolDropdownOptions.isNotEmpty() && poolDropdownOptions.none { it.first == poolName }) {
            poolName = poolDropdownOptions.first().first
        }
    }

    val expiredPlanOptions = remember(allPlans, currentType, isRadius, routerName, initial) {
        val list = mutableListOf("0" to "Default (Putus Koneksi / Nonaktif)")
        allPlans.filter { p ->
            p.optString("type").equals(currentType, true) &&
                p.optInt("id") != (initial?.optInt("id") ?: 0) &&
                ((isRadius && p.optInt("is_radius", 0) == 1) || (!isRadius && p.optString("routers").equals(routerName, true)))
        }.forEach { p ->
            list.add(p.optInt("id").toString() to p.optString("name_plan"))
        }
        list
    }

    val validityUnitOptions = remember(prepaidMode) {
        if (prepaidMode == "no") {
            listOf("Period" to "Period (Bulanan Jatuh Tempo)", "Months" to "Months (Bulan)", "Days" to "Days (Hari)")
        } else {
            listOf("Mins" to "Mins (Menit)", "Hrs" to "Hrs (Jam)", "Days" to "Days (Hari)", "Months" to "Months (Bulan)")
        }
    }

    LaunchedEffect(validityUnitOptions) {
        if (validityUnitOptions.none { it.first == validityUnit }) {
            validityUnit = "Days"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Edit else Icons.Filled.AddCircle,
                    contentDescription = null,
                    tint = ArnetaGoldPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEdit) "Edit Paket $currentType" else "Tambah Paket $currentType",
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
                Text("Status Paket:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = enabledStatus == 1,
                        onClick = { enabledStatus = 1 },
                        label = { Text("Aktif (Visible)", fontSize = 11.5.sp) }
                    )
                    FilterChip(
                        selected = enabledStatus == 0,
                        onClick = { enabledStatus = 0 },
                        label = { Text("Nonaktif (Hidden)", fontSize = 11.5.sp) }
                    )
                }

                if (!isBalance) {
                    Text("Sistem Tagihan & Kategori:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Personal" to "Personal", "Business" to "Business").forEach { (ptKey, ptLbl) ->
                            FilterChip(
                                selected = planType.equals(ptKey, true),
                                onClick = { planType = ptKey },
                                label = { Text(ptLbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = namePlan,
                    onValueChange = { namePlan = it },
                    label = { Text("Nama Paket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Harga Paket (${ApiClient.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEdit) {
                        OutlinedTextField(
                            value = priceOld,
                            onValueChange = { priceOld = it },
                            label = { Text("Harga Coret (Opsional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!isBalance) {
                    if (isHotspot) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Text("Tipe Kuota Hotspot (Plan Type):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = typebp.equals("Unlimited", true),
                                onClick = { typebp = "Unlimited" },
                                label = { Text("Unlimited", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = typebp.equals("Limited", true),
                                onClick = { typebp = "Limited" },
                                label = { Text("Limited (Waktu / Kuota)", fontSize = 11.5.sp) }
                            )
                        }

                        if (typebp.equals("Limited", true)) {
                            CompactDropdownSelector(
                                label = "Jenis Limit",
                                selectedValue = limitType,
                                options = listOf(
                                    "Time_Limit" to "Time Limit (Batas Waktu)",
                                    "Data_Limit" to "Data Limit (Batas Kuota)",
                                    "Both_Limit" to "Both Limit (Waktu + Kuota)"
                                ),
                                icon = Icons.Filled.Timer,
                                onValueSelected = { limitType = it },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (limitType == "Time_Limit" || limitType == "Both_Limit") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = timeLimit,
                                        onValueChange = { timeLimit = it },
                                        label = { Text("Batas Waktu") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactDropdownSelector(
                                        label = "Satuan Waktu",
                                        selectedValue = timeUnit,
                                        options = listOf("Hrs" to "Hrs (Jam)", "Mins" to "Mins (Menit)"),
                                        icon = Icons.Filled.Schedule,
                                        onValueSelected = { timeUnit = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (limitType == "Data_Limit" || limitType == "Both_Limit") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = dataLimit,
                                        onValueChange = { dataLimit = it },
                                        label = { Text("Batas Kuota") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactDropdownSelector(
                                        label = "Satuan Kuota",
                                        selectedValue = dataUnit,
                                        options = listOf("MB" to "MB", "GB" to "GB"),
                                        icon = Icons.Filled.DataUsage,
                                        onValueSelected = { dataUnit = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = sharedUsers,
                            onValueChange = { sharedUsers = it },
                            label = { Text("Shared Users (Jumlah Perangkat Simultan)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    if (bwOptions.isNotEmpty()) {
                        CompactDropdownSelector(
                            label = "Profil Kecepatan Bandwidth",
                            selectedValue = bwId.toString(),
                            options = bwOptions,
                            icon = Icons.Filled.Speed,
                            onValueSelected = { bwId = it.toIntOrNull() ?: bwId },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("⚠️ Buat profil Bandwidth terlebih dahulu di tab Bandwidth.", fontSize = 11.sp, color = ArnetaRedDanger)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = validity,
                            onValueChange = { validity = it },
                            label = { Text("Masa Aktif") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        CompactDropdownSelector(
                            label = "Satuan Masa Aktif",
                            selectedValue = validityUnit,
                            options = validityUnitOptions,
                            icon = Icons.Filled.DateRange,
                            onValueSelected = { validityUnit = it },
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    if (prepaidMode == "no") {
                        OutlinedTextField(
                            value = expiredDate,
                            onValueChange = { expiredDate = it },
                            label = { Text("Tanggal Jatuh Tempo Bulanan (1 - 28, Default: 20)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isEdit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gunakan FreeRADIUS Server?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = isRadius,
                                onCheckedChange = {
                                    isRadius = it
                                    deviceName = if (it) "Radius" else defaultDevice
                                }
                            )
                        }
                    }

                    if (!isRadius) {
                        if (routerDropdownOptions.isNotEmpty()) {
                            CompactDropdownSelector(
                                label = "Lokasi Router MikroTik",
                                selectedValue = routerName,
                                options = routerDropdownOptions,
                                icon = Icons.Filled.Router,
                                onValueSelected = { routerName = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = routerName,
                                onValueChange = { routerName = it },
                                label = { Text("Nama Router") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isPppoeOrVpn) {
                        if (poolDropdownOptions.isNotEmpty()) {
                            CompactDropdownSelector(
                                label = "IP Pool ($currentType)",
                                selectedValue = poolName,
                                options = poolDropdownOptions,
                                icon = Icons.Filled.Lan,
                                onValueSelected = { poolName = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = poolName,
                                onValueChange = { poolName = it },
                                label = { Text("Nama IP Pool (Wajib)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    CompactDropdownSelector(
                        label = "Device Driver",
                        selectedValue = deviceName,
                        options = devices.map { it to it },
                        icon = Icons.Filled.DeveloperBoard,
                        onValueSelected = { deviceName = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isEdit) {
                        CompactDropdownSelector(
                            label = "Paket Saat Expired (Plan Expired)",
                            selectedValue = planExpiredId.toString(),
                            options = expiredPlanOptions,
                            icon = Icons.Filled.EventBusy,
                            onValueSelected = { planExpiredId = it.toIntOrNull() ?: 0 },
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            onClick = { showAdvancedScripts = !showAdvancedScripts }
                        ) {
                            Icon(
                                imageVector = if (showAdvancedScripts) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Script Custom On-Login & On-Logout (Opsional)", fontSize = 11.5.sp, color = ArnetaGoldPrimary)
                        }

                        if (showAdvancedScripts) {
                            OutlinedTextField(
                                value = onLoginScript,
                                onValueChange = { onLoginScript = it },
                                label = { Text("On-Login Script RouterOS") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = onLogoutScript,
                                onValueChange = { onLogoutScript = it },
                                label = { Text("On-Logout Script RouterOS") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = !isSaving && namePlan.isNotBlank() && price.isNotBlank() &&
                (isBalance || (bwId > 0 && validity.isNotBlank() && (isRadius || routerName.isNotBlank()) && (!isPppoeOrVpn || poolName.isNotBlank())))

            Button(
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_plan_save",
                            mapOf(
                                "id" to (initial?.optInt("id") ?: 0),
                                "type" to currentType,
                                "name_plan" to namePlan.trim(),
                                "name" to namePlan.trim(),
                                "price" to (price.toDoubleOrNull() ?: 0.0),
                                "price_old" to priceOld.trim(),
                                "enabled" to enabledStatus,
                                "prepaid" to prepaidMode,
                                "plan_type" to planType,
                                "typebp" to typebp,
                                "limit_type" to limitType,
                                "time_limit" to (timeLimit.toIntOrNull() ?: 0),
                                "time_unit" to timeUnit,
                                "data_limit" to (dataLimit.toIntOrNull() ?: 0),
                                "data_unit" to dataUnit,
                                "sharedusers" to (sharedUsers.toIntOrNull() ?: 1),
                                "id_bw" to bwId,
                                "validity" to (validity.toIntOrNull() ?: 30),
                                "validity_unit" to validityUnit,
                                "radius" to if (isRadius) 1 else 0,
                                "routers" to if (isRadius) "" else routerName.trim(),
                                "pool_name" to poolName.trim(),
                                "pool" to poolName.trim(),
                                "device" to deviceName,
                                "expired_date" to (expiredDate.toIntOrNull() ?: 20),
                                "plan_expired" to planExpiredId,
                                "on_login" to onLoginScript,
                                "on_logout" to onLogoutScript
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan Paket", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
