package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * LAYAR PENUH NATIVE: FORM TAMBAH & EDIT PAKET (HOTSPOT, PPPOE, SALDO, VPN)
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

    var typebp by remember { mutableStateOf(initial?.optString("typebp")?.ifEmpty { null } ?: "Unlimited") }
    var limitType by remember { mutableStateOf(initial?.optString("limit_type")?.ifEmpty { null } ?: "Time_Limit") }
    var timeLimit by remember { mutableStateOf(initial?.optInt("time_limit", 4)?.toString() ?: "4") }
    var timeUnit by remember { mutableStateOf(initial?.optString("time_unit")?.ifEmpty { null } ?: "Hrs") }
    var dataLimit by remember { mutableStateOf(initial?.optInt("data_limit", 1)?.toString() ?: "1") }
    var dataUnit by remember { mutableStateOf(initial?.optString("data_unit")?.ifEmpty { null } ?: "GB") }
    var sharedUsers by remember { mutableStateOf(initial?.optInt("shared_users", 1)?.toString() ?: "1") }

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
    var showAdvancedScripts by remember {
        mutableStateOf(
            !initial?.optString("on_login").isNullOrBlank() || !initial?.optString("on_logout").isNullOrBlank()
        )
    }
    var onLoginScript by remember { mutableStateOf(initial?.optString("on_login", "") ?: "") }
    var onLogoutScript by remember { mutableStateOf(initial?.optString("on_logout", "") ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(bandwidths) {
        if (bwId <= 0 && bandwidths.isNotEmpty()) {
            bwId = bandwidths.first().optInt("id", 0)
        }
    }

    LaunchedEffect(routers, isRadius) {
        if (!isRadius && routerName.isBlank() && routers.isNotEmpty()) {
            routerName = routers.first().optString("name", "")
        }
    }

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

    val canSave = !isSaving && namePlan.isNotBlank() && price.isNotBlank() &&
        (isBalance || (bwId > 0 && validity.isNotBlank() && (isRadius || routerName.isNotBlank()) && (!isPppoeOrVpn || poolName.isNotBlank())))

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = if (isEdit) "Edit Paket $currentType" else "Tambah Paket $currentType",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isBalance) "Konfigurasi nominal paket deposit saldo"
                                    else "Sinkron otomatis ke profil Router MikroTik / Radius",
                                    fontSize = 11.5.sp,
                                    color = ArnetaGoldLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = ArnetaGoldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = currentType.uppercase(),
                                color = ArnetaGoldPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val parsedPrice = price.toDoubleOrNull() ?: 0.0
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = namePlan.ifBlank { "Nama Paket Belum Diisi" },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isBalance) "Paket Deposit Saldo • ${if (enabledStatus == 1) "Visible" else "Hidden"}"
                                    else "${if (prepaidMode == "no") "Postpaid" else "Prepaid"} • ${if (isRadius) "Radius" else routerName.ifEmpty { "-" }} • $validity $validityUnit",
                                    fontSize = 11.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                            Text(
                                text = formatRupiah(parsedPrice),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.5.sp,
                                color = ArnetaGoldPrimary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.36f)
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
                                },
                                modifier = Modifier
                                    .weight(0.64f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSaving) "Menyimpan..." else "Simpan Paket",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
                            }
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
                // SECTION 1: STATUS VISIBILITAS & SISTEM TAGIHAN
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "1. Status Paket & Sistem Tagihan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = enabledStatus == 1,
                                onClick = { enabledStatus = 1 },
                                label = { Text("Aktif (Visible di Pelanggan)", fontSize = 11.5.sp) },
                                leadingIcon = if (enabledStatus == 1) {
                                    { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = enabledStatus == 0,
                                onClick = { enabledStatus = 0 },
                                label = { Text("Nonaktif (Hidden)", fontSize = 11.5.sp) },
                                leadingIcon = if (enabledStatus == 0) {
                                    { Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                        }

                        if (!isBalance) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Text(
                                text = "Metode Tagihan & Kategori Pelanggan:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = prepaidMode == "yes",
                                    onClick = { prepaidMode = "yes" },
                                    label = { Text("Prepaid (Prabayar)", fontSize = 11.5.sp) }
                                )
                                FilterChip(
                                    selected = prepaidMode == "no",
                                    onClick = { prepaidMode = "no" },
                                    label = { Text("Postpaid (Pascabayar)", fontSize = 11.5.sp) }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Personal" to "Personal (Rumahan)", "Business" to "Business (Bisnis)").forEach { (ptKey, ptLbl) ->
                                    FilterChip(
                                        selected = planType.equals(ptKey, true),
                                        onClick = { planType = ptKey },
                                        label = { Text(ptLbl, fontSize = 11.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 2: NAMA & HARGA PAKET
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
                            text = "2. Informasi Nama & Harga Paket",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaCyanIncome
                        )

                        OutlinedTextField(
                            value = namePlan,
                            onValueChange = { namePlan = it },
                            label = { Text("Nama Paket *") },
                            placeholder = { Text(if (isBalance) "Contoh: Saldo 50.000" else "Contoh: Paket Rumahan 20 Mbps") },
                            leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = ArnetaGoldPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = price,
                                onValueChange = { price = it },
                                label = { Text("Harga Paket (${ApiClient.currencyCode}) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            if (isEdit) {
                                OutlinedTextField(
                                    value = priceOld,
                                    onValueChange = { priceOld = it },
                                    label = { Text("Harga Coret (Opsional)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (!isBalance) {
                    // SECTION 3: BANDWIDTH, MASA AKTIF & KUOTA HOTSPOT
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
                                text = "3. Profil Kecepatan, Masa Aktif & Kuota",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaGoldPrimary
                            )

                            if (bwOptions.isNotEmpty()) {
                                CompactDropdownSelector(
                                    label = "Profil Kecepatan Bandwidth *",
                                    selectedValue = bwId.toString(),
                                    options = bwOptions,
                                    icon = Icons.Filled.Speed,
                                    onValueSelected = { bwId = it.toIntOrNull() ?: bwId },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Surface(
                                    color = ArnetaRedDanger.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⚠️ Belum ada profil Bandwidth. Silakan buat profil Bandwidth terlebih dahulu di tab Bandwidth.",
                                        fontSize = 11.5.sp,
                                        color = ArnetaRedDanger,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = validity,
                                    onValueChange = { validity = it },
                                    label = { Text("Masa Aktif *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                CompactDropdownSelector(
                                    label = "Satuan Masa Aktif",
                                    selectedValue = validityUnit,
                                    options = validityUnitOptions,
                                    icon = Icons.Filled.DateRange,
                                    onValueSelected = { validityUnit = it },
                                    modifier = Modifier.weight(1.25f)
                                )
                            }

                            if (prepaidMode == "no") {
                                OutlinedTextField(
                                    value = expiredDate,
                                    onValueChange = { expiredDate = it },
                                    label = { Text("Tanggal Jatuh Tempo Bulanan (1 - 28, Default: 20)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (isHotspot) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Text(
                                    text = "Tipe Kuota Hotspot & Shared Users:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArnetaCyanIncome
                                )

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
                                        label = "Jenis Limit Hotspot",
                                        selectedValue = limitType,
                                        options = listOf(
                                            "Time_Limit" to "Time Limit (Batas Waktu Online)",
                                            "Data_Limit" to "Data Limit (Batas Kuota Data)",
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
                                                shape = RoundedCornerShape(12.dp),
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
                                                shape = RoundedCornerShape(12.dp),
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
                                    label = { Text("Shared Users (Jumlah Perangkat Login Simultan)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // SECTION 4: ROUTER MIKROTIK, RADIUS, IP POOL & DEVICE DRIVER
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
                                text = "4. Lokasi Router, IP Pool & Device Driver",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaCyanIncome
                            )

                            if (!isEdit) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Gunakan FreeRADIUS Server?", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Aktifkan jika paket dikelola oleh server Radius terpusat",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
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
                                        label = "Lokasi Router MikroTik *",
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
                                        label = { Text("Nama Router *") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (isPppoeOrVpn) {
                                if (poolDropdownOptions.isNotEmpty()) {
                                    CompactDropdownSelector(
                                        label = "IP Pool ($currentType) *",
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
                                        label = { Text("Nama IP Pool (Wajib) *") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            CompactDropdownSelector(
                                label = "Device Driver Sistem",
                                selectedValue = deviceName,
                                options = devices.map { it to it },
                                icon = Icons.Filled.DeveloperBoard,
                                onValueSelected = { deviceName = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // SECTION 5: PENGATURAN LANJUTAN SAAT EDIT (PLAN EXPIRED & CUSTOM SCRIPT)
                    if (isEdit) {
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
                                    text = "5. Aksi Saat Kedaluwarsa & Custom Script",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaGoldPrimary
                                )

                                CompactDropdownSelector(
                                    label = "Paket Pengganti Saat Expired (Plan Expired)",
                                    selectedValue = planExpiredId.toString(),
                                    options = expiredPlanOptions,
                                    icon = Icons.Filled.EventBusy,
                                    onValueSelected = { planExpiredId = it.toIntOrNull() ?: 0 },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                TextButton(
                                    onClick = { showAdvancedScripts = !showAdvancedScripts },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showAdvancedScripts) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = ArnetaCyanIncome
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Script Custom On-Login & On-Logout RouterOS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaCyanIncome
                                    )
                                }

                                AnimatedVisibility(visible = showAdvancedScripts) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = onLoginScript,
                                            onValueChange = { onLoginScript = it },
                                            label = { Text("On-Login Script RouterOS") },
                                            minLines = 2,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = onLogoutScript,
                                            onValueChange = { onLogoutScript = it },
                                            label = { Text("On-Logout Script RouterOS") },
                                            minLines = 2,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
