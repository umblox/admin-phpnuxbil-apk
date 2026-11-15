package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * TAB DAFTAR PROFIL BANDWIDTH & LAYAR PENUH FORM BANDWIDTH + BURST PRESET
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bandwidths) { bw ->
                val burstStr = bw.optString("burst", "").trim()
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
                                text = "↓ Download: ${bw.optInt("rate_down")} ${bw.optString("rate_down_unit")} • ↑ Upload: ${bw.optInt("rate_up")} ${bw.optString("rate_up_unit")}",
                                fontSize = 12.sp,
                                color = ArnetaCyanIncome,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (burstStr.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "⚡ Burst: $burstStr",
                                    fontSize = 11.sp,
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
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var nameBw by remember { mutableStateOf(initial?.optString("name_bw") ?: "") }
    var rateDown by remember { mutableStateOf(initial?.optInt("rate_down", 10)?.toString() ?: "10") }
    var rateDownUnit by remember { mutableStateOf(initial?.optString("rate_down_unit")?.ifEmpty { null } ?: "Mbps") }
    var rateUp by remember { mutableStateOf(initial?.optInt("rate_up", 10)?.toString() ?: "10") }
    var rateUpUnit by remember { mutableStateOf(initial?.optString("rate_up_unit")?.ifEmpty { null } ?: "Mbps") }

    val initBurstParts = remember(initial) {
        val raw = initial?.optString("burst", "")?.trim() ?: ""
        if (raw.isNotEmpty()) raw.split("\\s+".toRegex()) else emptyList()
    }
    var showBurstConfig by remember { mutableStateOf(initBurstParts.isNotEmpty()) }
    var burstLimit by remember { mutableStateOf(initBurstParts.getOrNull(0) ?: "") }
    var burstThreshold by remember { mutableStateOf(initBurstParts.getOrNull(1) ?: "") }
    var burstTime by remember { mutableStateOf(initBurstParts.getOrNull(2) ?: "") }
    var burstPriority by remember { mutableStateOf(initBurstParts.getOrNull(3) ?: "") }
    var burstLimitAt by remember { mutableStateOf(initBurstParts.getOrNull(4) ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    fun applyBurstPreset(multiplier: Int) {
        val dl = rateDown.toIntOrNull() ?: 0
        val ul = rateUp.toIntOrNull() ?: 0
        if (dl <= 0 || ul <= 0) return

        val dlSuffix = if (rateDownUnit == "Kbps") "K" else "M"
        val ulSuffix = if (rateUpUnit == "Kbps") "K" else "M"

        val bDl = dl * multiplier
        val bUl = ul * multiplier
        val tDl = (dl * 3) / 4
        val tUl = (ul * 3) / 4
        val limDl = (dl / 2).coerceAtLeast(1)
        val limUl = (ul / 2).coerceAtLeast(1)

        burstLimit = "${bUl}${ulSuffix}/${bDl}${dlSuffix}"
        burstThreshold = "${tUl.coerceAtLeast(1)}${ulSuffix}/${tDl.coerceAtLeast(1)}${dlSuffix}"
        burstTime = "16/16"
        burstPriority = "8"
        burstLimitAt = "${limUl}${ulSuffix}/${limDl}${dlSuffix}"
    }

    val isBurstValid = !showBurstConfig || (
        burstLimit.isNotBlank() &&
            burstThreshold.isNotBlank() &&
            burstTime.isNotBlank() &&
            burstPriority.isNotBlank()
        )

    val canSave = !isSaving &&
        nameBw.isNotBlank() &&
        (rateDown.toIntOrNull() ?: 0) > 0 &&
        (rateUp.toIntOrNull() ?: 0) > 0 &&
        isBurstValid

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
                                text = if (isEdit) "Edit Profil Bandwidth" else "Tambah Profil Bandwidth",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Atur batas kecepatan Download, Upload & MikroTik Burst",
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
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = nameBw.ifBlank { "Nama Bandwidth" },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "↓ ${rateDown.ifEmpty { "0" }} $rateDownUnit / ↑ ${rateUp.ifEmpty { "0" }} $rateUpUnit",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaCyanIncome
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
                                            "admin_bandwidth_save",
                                            mapOf(
                                                "id" to (initial?.optInt("id") ?: 0),
                                                "name" to nameBw.trim(),
                                                "name_bw" to nameBw.trim(),
                                                "rate_down" to (rateDown.toIntOrNull() ?: 0),
                                                "rate_down_unit" to rateDownUnit,
                                                "rate_up" to (rateUp.toIntOrNull() ?: 0),
                                                "rate_up_unit" to rateUpUnit,
                                                "burst_limit" to if (showBurstConfig) burstLimit.trim() else "",
                                                "burst_threshold" to if (showBurstConfig) burstThreshold.trim() else "",
                                                "burst_time" to if (showBurstConfig) burstTime.trim() else "",
                                                "burst_priority" to if (showBurstConfig) burstPriority.trim() else "",
                                                "burst_limit_at" to if (showBurstConfig) burstLimitAt.trim() else ""
                                            )
                                        )
                                        isSaving = false
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
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
                                    text = if (isSaving) "Menyimpan..." else "Simpan Bandwidth",
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
                            text = "1. Kecepatan Utama (Rate Limit)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        OutlinedTextField(
                            value = nameBw,
                            onValueChange = { nameBw = it },
                            label = { Text("Nama Profil Bandwidth *") },
                            placeholder = { Text("Misal: 10 Mbps atau Paket Hemat 5M") },
                            leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, tint = ArnetaGoldPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rateDown,
                                onValueChange = { rateDown = it },
                                label = { Text("Rate Download *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            CompactDropdownSelector(
                                label = "Satuan DL",
                                selectedValue = rateDownUnit,
                                options = listOf("Mbps" to "Mbps", "Kbps" to "Kbps"),
                                icon = Icons.Filled.ArrowDownward,
                                onValueSelected = { rateDownUnit = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rateUp,
                                onValueChange = { rateUp = it },
                                label = { Text("Rate Upload *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            CompactDropdownSelector(
                                label = "Satuan UL",
                                selectedValue = rateUpUnit,
                                options = listOf("Mbps" to "Mbps", "Kbps" to "Kbps"),
                                icon = Icons.Filled.ArrowUpward,
                                onValueSelected = { rateUpUnit = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. MikroTik Burst Limit (Opsional)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaCyanIncome
                                )
                                Text(
                                    text = "Berikan lonjakan kecepatan awal saat pelanggan membuka web/video",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = showBurstConfig,
                                onCheckedChange = {
                                    showBurstConfig = it
                                    if (!it) {
                                        burstLimit = ""
                                        burstThreshold = ""
                                        burstTime = ""
                                        burstPriority = ""
                                        burstLimitAt = ""
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = showBurstConfig) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text("Isi Otomatis:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                                    AssistChip(
                                        onClick = { applyBurstPreset(2) },
                                        label = { Text("⚡ Preset Burst 2x Speed", fontSize = 11.5.sp) }
                                    )
                                    AssistChip(
                                        onClick = { applyBurstPreset(3) },
                                        label = { Text("🚀 Preset Burst 3x Speed", fontSize = 11.5.sp) }
                                    )
                                }

                                Text(
                                    text = "Format MikroTik: Upload/Download (Keempat kolom bertanda * wajib terisi agar Burst aktif)",
                                    fontSize = 11.sp,
                                    color = if (isBurstValid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else ArnetaRedDanger
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = burstLimit,
                                        onValueChange = { burstLimit = it },
                                        label = { Text("Burst Limit * (2M/5M)", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = burstThreshold,
                                        onValueChange = { burstThreshold = it },
                                        label = { Text("Threshold * (1M/3M)", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = burstTime,
                                        onValueChange = { burstTime = it },
                                        label = { Text("Burst Time * (16/16)", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = burstPriority,
                                        onValueChange = { burstPriority = it },
                                        label = { Text("Priority * (1-8)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedTextField(
                                    value = burstLimitAt,
                                    onValueChange = { burstLimitAt = it },
                                    label = { Text("Limit-At / MIR (Opsional, misal 512K/1M)", fontSize = 11.sp) },
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
    }
}
