package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Konversi nilai kecepatan (Kbps / Mbps) ke format MikroTik (k / M)
 * Menggunakan basis biner 1024 sesuai system/controllers/bandwidth.php
 */
private fun formatMikrotikRateValue(kbpsValue: Double): String {
    val roundedKbps = kbpsValue.roundToInt().coerceAtLeast(1)
    return if (roundedKbps >= 1024 && roundedKbps % 1024 == 0) {
        "${roundedKbps / 1024}M"
    } else {
        "${roundedKbps}k"
    }
}

private fun toKbps(rate: Double, unit: String): Double {
    return if (unit.equals("Kbps", ignoreCase = true)) rate else rate * 1024.0
}

/**
 * TAB DAFTAR PROFIL BANDWIDTH & FORM BANDWIDTH SESUAI system/controllers/bandwidth.php
 */
@Composable
internal fun BandwidthTabContent(
    bandwidths: List<JSONObject>,
    onEdit: (JSONObject) -> Unit,
    onDelete: (JSONObject) -> Unit
) {
    if (bandwidths.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Belum ada profil Bandwidth yang cocok.", fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 95.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(bandwidths) { bw ->
                val burstStr = bw.optString("burst", "").trim()
                val rateDown = bw.optString("rate_down", "")
                val rateDownUnit = bw.optString("rate_down_unit", "")
                val rateUp = bw.optString("rate_up", "")
                val rateUpUnit = bw.optString("rate_up_unit", "")

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onEdit(bw) }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bw.optString("name_bw"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Rate Download: $rateDown $rateDownUnit • Rate Upload: $rateUp $rateUpUnit",
                                fontSize = 12.sp,
                                color = ArnetaCyanIncome,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (burstStr.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Burst: $burstStr",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ArnetaGoldPrimary
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onEdit(bw) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Bandwidth", tint = ArnetaGoldPrimary)
                            }
                            IconButton(onClick = { onDelete(bw) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Bandwidth", tint = ArnetaRedDanger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BandwidthFormDialog(
    initial: JSONObject? = null,
    rateUnits: List<String> = emptyList(),
    burstPresets: List<JSONObject> = emptyList(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val unitOptions = remember(rateUnits) {
        val src = rateUnits.ifEmpty { listOf("Kbps", "Mbps") }
        src.map { it to it }
    }

    var nameBw by remember(initial) { mutableStateOf(initial?.optString("name_bw", "") ?: "") }
    var rateDown by remember(initial) { mutableStateOf(initial?.optString("rate_down", "") ?: "") }
    var rateDownUnit by remember(initial, unitOptions) {
        mutableStateOf(
            initial?.optString("rate_down_unit", "")?.takeIf { it.isNotEmpty() }
                ?: unitOptions.first().first
        )
    }
    var rateUp by remember(initial) { mutableStateOf(initial?.optString("rate_up", "") ?: "") }
    var rateUpUnit by remember(initial, unitOptions) {
        mutableStateOf(
            initial?.optString("rate_up_unit", "")?.takeIf { it.isNotEmpty() }
                ?: unitOptions.first().first
        )
    }

    // Membaca array burst_parts (hasil explode(" ", $d['burst']) di bandwidth.php) tanpa hardcode
    val initBurstParts = remember(initial) {
        val arr = initial?.optJSONArray("burst_parts")
        if (arr != null && arr.length() > 0) {
            List(arr.length()) { i -> arr.optString(i, "") }
        } else {
            val raw = initial?.optString("burst", "")?.trim() ?: ""
            if (raw.isNotEmpty()) raw.split("\\s+".toRegex()) else emptyList()
        }
    }

    var burstLimit by remember(initial) { mutableStateOf(initBurstParts.getOrNull(0) ?: "") }
    var burstThreshold by remember(initial) { mutableStateOf(initBurstParts.getOrNull(1) ?: "") }
    var burstTime by remember(initial) { mutableStateOf(initBurstParts.getOrNull(2) ?: "") }
    var burstPriority by remember(initial) { mutableStateOf(initBurstParts.getOrNull(3) ?: "") }
    var burstLimitAt by remember(initial) { mutableStateOf(initBurstParts.getOrNull(4) ?: "") }

    var selectedPresetGroup by remember { mutableStateOf("2x") }
    var isSaving by remember { mutableStateOf(false) }

    /**
     * Sama persis dengan fungsi JavaScript autoBurst() di ui/ui/admin/bandwidth/add.tpl
     */
    fun applyWebBurstPreset(preset: JSONObject) {
        val presetName = preset.optString("name_bw", "").trim()
        if (nameBw.isBlank() && presetName.isNotEmpty()) {
            nameBw = presetName
        }
        rateDown = preset.optInt("rate_down", 0).takeIf { it > 0 }?.toString() ?: preset.optString("rate_down", "")
        rateDownUnit = preset.optString("rate_down_unit", rateDownUnit).ifEmpty { rateDownUnit }
        rateUp = preset.optInt("rate_up", 0).takeIf { it > 0 }?.toString() ?: preset.optString("rate_up", "")
        rateUpUnit = preset.optString("rate_up_unit", rateUpUnit).ifEmpty { rateUpUnit }

        val bArr = preset.optJSONArray("burst")
        if (bArr != null) {
            burstLimit = bArr.optString(0, "")
            burstThreshold = bArr.optString(1, "")
            burstTime = bArr.optString(2, "")
            burstPriority = bArr.optString(3, "")
            burstLimitAt = bArr.optString(4, "")
        }
    }

    /**
     * Menghitung otomatis parameter Burst dari Rate Download & Rate Upload yang sedang diisi
     * menggunakan basis biner 1024 sesuai rumus PHPNuxBill (Threshold = 3/4 Max, Limit-At = 1/2 Max)
     */
    fun calculateBurstFromCurrentRate(mode: String) {
        val dlVal = rateDown.trim().toDoubleOrNull() ?: 0.0
        val ulVal = rateUp.trim().toDoubleOrNull() ?: 0.0
        if (dlVal <= 0.0 || ulVal <= 0.0) {
            Toast.makeText(context, "Isi Rate Download dan Rate Upload terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val dlKbps = toKbps(dlVal, rateDownUnit)
        val ulKbps = toKbps(ulVal, rateUpUnit)

        val burstDlKbps = if (mode == "2x") dlKbps * 2.0 else dlKbps + 1024.0
        val burstUlKbps = if (mode == "2x") ulKbps * 2.0 else ulKbps + 1024.0

        val threshDlKbps = dlKbps * 0.75
        val threshUlKbps = ulKbps * 0.75

        val limitAtDlKbps = dlKbps * 0.5
        val limitAtUlKbps = ulKbps * 0.5

        val calcTime = ((threshDlKbps / burstDlKbps) * 32.0).roundToInt().coerceIn(8, 16)

        burstLimit = "${formatMikrotikRateValue(burstUlKbps)}/${formatMikrotikRateValue(burstDlKbps)}"
        burstThreshold = "${formatMikrotikRateValue(threshUlKbps)}/${formatMikrotikRateValue(threshDlKbps)}"
        if (burstTime.isBlank()) burstTime = "$calcTime/$calcTime"
        if (burstPriority.isBlank()) burstPriority = "8"
        burstLimitAt = "${formatMikrotikRateValue(limitAtUlKbps)}/${formatMikrotikRateValue(limitAtDlKbps)}"
    }

    val burstArrayValues = listOf(
        burstLimit.trim(),
        burstThreshold.trim(),
        burstTime.trim(),
        burstPriority.trim(),
        burstLimitAt.trim()
    )
    val anyBurstFilled = burstArrayValues.any { it.isNotEmpty() }
    val allBurstFilled = burstArrayValues.all { it.isNotEmpty() }

    val canSave = !isSaving &&
        nameBw.trim().length in 1..255 &&
        (rateDown.trim().toDoubleOrNull() ?: 0.0) > 0.0 &&
        (rateUp.trim().toDoubleOrNull() ?: 0.0) > 0.0 &&
        (!anyBurstFilled || allBurstFilled)

    val presetGroups = remember(burstPresets) {
        burstPresets.map { it.optString("group", "2x") }.distinct()
    }
    val displayedPresets = remember(burstPresets, selectedPresetGroup) {
        burstPresets.filter { it.optString("group", "2x").equals(selectedPresetGroup, ignoreCase = true) }
    }

    ArnetaFullScreenDialog(
        onDismissRequest = onDismiss,
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
                            text = if (isEdit) "Edit Bandwidth" else "Add New Bandwidth",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Sesuai struktur resmi system/controllers/bandwidth.php",
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val previewRate = if (rateUp.isNotBlank() && rateDown.isNotBlank()) {
                        val uUnit = if (rateUpUnit.equals("Kbps", true)) "K" else "M"
                        val dUnit = if (rateDownUnit.equals("Kbps", true)) "K" else "M"
                        val baseRate = "${rateUp.trim()}$uUnit/${rateDown.trim()}$dUnit"
                        if (allBurstFilled) "$baseRate ${burstArrayValues.joinToString(" ")}" else baseRate
                    } else {
                        "-"
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Mikrotik-Rate-Limit:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = previewRate,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
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
                                .height(46.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
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
                                    // Mengirimkan array burst[] persis seperti $_POST['burst'] di bandwidth.php
                                    val burstPayload = if (allBurstFilled) burstArrayValues else emptyList()
                                    val res = ApiClient.request(
                                        "admin_bandwidth_save",
                                        mapOf(
                                            "id" to (initial?.optInt("id") ?: 0),
                                            "name" to nameBw.trim(),
                                            "rate_down" to rateDown.trim(),
                                            "rate_down_unit" to rateDownUnit,
                                            "rate_up" to rateUp.trim(),
                                            "rate_up_unit" to rateUpUnit,
                                            "burst" to burstPayload
                                        )
                                    )
                                    isSaving = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    if (res.status) onSaved()
                                }
                            },
                            modifier = Modifier
                                .weight(0.64f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Saving..." else "Save Changes",
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
            // CARD 1: BANDWIDTH NAME & RATE DOWNLOAD / UPLOAD
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
                        text = "Bandwidth Plans",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaGoldPrimary
                    )

                    OutlinedTextField(
                        value = nameBw,
                        onValueChange = { nameBw = it },
                        label = { Text("Bandwidth Name") },
                        leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rateDown,
                            onValueChange = { rateDown = it },
                            label = { Text("Rate Download") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )
                        CompactDropdownSelector(
                            label = "Unit",
                            selectedValue = rateDownUnit,
                            options = unitOptions,
                            icon = Icons.Filled.ArrowDownward,
                            onValueSelected = { rateDownUnit = it },
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rateUp,
                            onValueChange = { rateUp = it },
                            label = { Text("Rate Upload") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )
                        CompactDropdownSelector(
                            label = "Unit",
                            selectedValue = rateUpUnit,
                            options = unitOptions,
                            icon = Icons.Filled.ArrowUpward,
                            onValueSelected = { rateUpUnit = it },
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }

            // CARD 2: OPTIONAL BURST LIMIT & PRESET RESMI WEB PHPNUXBILL
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Optional Burst Limit",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaCyanIncome
                            )
                            Text(
                                text = if (anyBurstFilled && !allBurstFilled) {
                                    "Semua 5 kolom Burst wajib diisi agar tersimpan (sesuai bandwidth.php)"
                                } else {
                                    "Kosongkan semua kolom Burst jika tidak menggunakan Burst Limit"
                                },
                                fontSize = 11.sp,
                                color = if (anyBurstFilled && !allBurstFilled) ArnetaRedDanger
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }

                        if (anyBurstFilled) {
                            TextButton(
                                onClick = {
                                    burstLimit = ""
                                    burstThreshold = ""
                                    burstTime = ""
                                    burstPriority = ""
                                    burstLimitAt = ""
                                }
                            ) {
                                Text("Clear Burst", fontSize = 11.5.sp, color = ArnetaRedDanger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // KALKULATOR OTOMATIS DARI RATE YANG DIISI USER
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        AssistChip(
                            onClick = { calculateBurstFromCurrentRate("2x") },
                            label = {
                                Text(
                                    text = "Hitung 2x dari Rate",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArnetaGoldPrimary
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        AssistChip(
                            onClick = { calculateBurstFromCurrentRate("upto") },
                            label = {
                                Text(
                                    text = "Hitung UpTo (+1M) dari Rate",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArnetaCyanIncome
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // DAFTAR BURST PRESET DARI SERVER (ui/ui/admin/bandwidth/add.tpl)
                    if (burstPresets.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Text(
                            text = "Burst Limit Preset (Web PHPNuxBill):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaGoldPrimary
                        )

                        if (presetGroups.size > 1) {
                            ArnetaSubTabBar(
                                tabs = presetGroups.map { g ->
                                    g to if (g.equals("2x", true)) "2x Speed Preset" else "UpTo (+1M) Preset"
                                },
                                selectedKey = selectedPresetGroup,
                                onSelect = { selectedPresetGroup = it }
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            displayedPresets.forEach { preset ->
                                AssistChip(
                                    onClick = { applyWebBurstPreset(preset) },
                                    label = {
                                        Text(
                                            text = preset.optString("label"),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // 5 INPUT ARRAY $_POST['burst'][] SESUAI URUTAN bandwidth.php
                    OutlinedTextField(
                        value = burstLimit,
                        onValueChange = { burstLimit = it },
                        label = { Text("Burst Limit — burst[0]") },
                        placeholder = { Text("[Upload]/[Download]") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = burstThreshold,
                        onValueChange = { burstThreshold = it },
                        label = { Text("Burst Threshold — burst[1]") },
                        placeholder = { Text("[Upload]/[Download]") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = burstTime,
                            onValueChange = { burstTime = it },
                            label = { Text("Burst Time — burst[2]") },
                            placeholder = { Text("[Upload]/[Download]") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = burstPriority,
                            onValueChange = { burstPriority = it },
                            label = { Text("Priority — burst[3]") },
                            placeholder = { Text("8") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = burstLimitAt,
                        onValueChange = { burstLimitAt = it },
                        label = { Text("Limit At — burst[4]") },
                        placeholder = { Text("[Upload]/[Download]") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
