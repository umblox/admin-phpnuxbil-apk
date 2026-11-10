package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * TAB DAFTAR PROFIL BANDWIDTH & DIALOG FORM BANDWIDTH + BURST
 * Sesuai system/controllers/bandwidth.php
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Profil Bandwidth" else "Tambah Profil Bandwidth",
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
                OutlinedTextField(
                    value = nameBw,
                    onValueChange = { nameBw = it },
                    label = { Text("Nama Bandwidth (Misal: 10 Mbps)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateDown,
                        onValueChange = { rateDown = it },
                        label = { Text("Rate Download") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
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
                        label = { Text("Rate Upload") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Burst Limit (Opsional)?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
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

                if (showBurstConfig) {
                    Text(
                        text = "Format MikroTik: Upload/Download (Contoh: 2M/5M, 1500K/3M, 16/16, 8)",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = burstLimit,
                            onValueChange = { burstLimit = it },
                            label = { Text("Burst Limit (2M/5M)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = burstThreshold,
                            onValueChange = { burstThreshold = it },
                            label = { Text("Threshold (1M/3M)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = burstTime,
                            onValueChange = { burstTime = it },
                            label = { Text("Burst Time (16/16)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = burstPriority,
                            onValueChange = { burstPriority = it },
                            label = { Text("Priority (1-8)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = burstLimitAt,
                        onValueChange = { burstLimitAt = it },
                        label = { Text("Limit-At (Opsional, misal 512K/1M)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && nameBw.isNotBlank() && rateDown.isNotBlank() && rateUp.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
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
                                "burst_limit" to burstLimit.trim(),
                                "burst_threshold" to burstThreshold.trim(),
                                "burst_time" to burstTime.trim(),
                                "burst_priority" to burstPriority.trim(),
                                "burst_limit_at" to burstLimitAt.trim()
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan Bandwidth", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
