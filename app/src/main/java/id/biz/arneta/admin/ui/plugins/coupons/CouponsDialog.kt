package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
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
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * DIALOG FORM TAMBAH & EDIT KUPON PROMO (Sesuai system/controllers/coupons.php)
 */
@Composable
internal fun AddCouponDialog(
    initial: JSONObject? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val defaultStart = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
    val defaultEnd = remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    var code by remember { mutableStateOf(initial?.optString("code") ?: "") }
    var type by remember { mutableStateOf(initial?.optString("type")?.ifEmpty { null } ?: "fixed") } // fixed | percent
    var value by remember {
        mutableStateOf(
            if (initial != null) initial.optDouble("value", 0.0).toLong().toString() else "5000"
        )
    }
    var description by remember { mutableStateOf(initial?.optString("description") ?: "") }
    var maxUsage by remember { mutableStateOf(initial?.optInt("max_usage", 0)?.toString() ?: "0") }
    var minOrderAmount by remember {
        mutableStateOf(
            if (initial != null) initial.optDouble("min_order_amount", 0.0).toLong().toString() else "10000"
        )
    }
    var maxDiscountAmount by remember {
        mutableStateOf(
            if (initial != null) initial.optInt("max_discount_amount", 0).toString() else "5000"
        )
    }
    var status by remember { mutableStateOf(initial?.optString("status")?.ifEmpty { null } ?: "active") } // active | inactive
    var startDate by remember {
        mutableStateOf(initial?.optString("start_date")?.ifEmpty { null } ?: defaultStart)
    }
    var endDate by remember {
        mutableStateOf(
            initial?.optString("end_date")?.ifEmpty { null }
                ?: initial?.optString("expired_date")?.ifEmpty { null }
                ?: defaultEnd
        )
    }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Edit else Icons.Filled.LocalOffer,
                    contentDescription = null,
                    tint = ArnetaGoldPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEdit) "Edit Kupon: $code" else "Tambah Kupon Promo",
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = status.equals("active", true),
                        onClick = { status = "active" },
                        label = { Text("Status: Active", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = status.equals("inactive", true),
                        onClick = { status = "inactive" },
                        label = { Text("Status: Inactive", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().replace("[^A-Z0-9]".toRegex(), "") },
                    label = { Text("Kode Kupon (Alfanumerik, Misal: ARNETA10)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipe Potongan Kupon:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "fixed",
                        onClick = { type = "fixed" },
                        label = { Text("Fixed (Nominal Rp)", fontSize = 11.5.sp) }
                    )
                    FilterChip(
                        selected = type == "percent",
                        onClick = { type = "percent" },
                        label = { Text("Percent (Persentase %)", fontSize = 11.5.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            if (type == "fixed") maxDiscountAmount = it
                        },
                        label = { Text(if (type == "percent") "Nilai Diskon (%)" else "Nominal Diskon (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxDiscountAmount,
                        onValueChange = { maxDiscountAmount = it },
                        label = { Text("Maks. Diskon (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minOrderAmount,
                        onValueChange = { minOrderAmount = it },
                        label = { Text("Min. Pembelian (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxUsage,
                        onValueChange = { maxUsage = it },
                        label = { Text("Maks. Pakai (0=Bebas)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi Kupon (Wajib)") },
                    placeholder = { Text("Contoh: Diskon pelanggan baru bulan ini") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Mulai (YYYY-MM-DD)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Berakhir (YYYY-MM-DD)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            val canSave = !isSaving && code.isNotBlank() && (value.toDoubleOrNull() ?: 0.0) > 0.0 &&
                startDate.isNotBlank() && endDate.isNotBlank()

            Button(
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val valNum = value.toDoubleOrNull() ?: 0.0
                        val maxDiscNum = maxDiscountAmount.toIntOrNull()?.takeIf { it > 0 } ?: valNum.toInt()
                        val minOrdNum = minOrderAmount.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0

                        val res = ApiClient.request(
                            "admin_coupon_save",
                            mapOf(
                                "id" to (initial?.optInt("id") ?: 0),
                                "code" to code.trim(),
                                "type" to type,
                                "value" to valNum,
                                "description" to description.trim().ifEmpty { "Promo Diskon $code" },
                                "max_usage" to (maxUsage.toIntOrNull() ?: 0),
                                "min_order_amount" to minOrdNum,
                                "max_discount_amount" to maxDiscNum,
                                "status" to status,
                                "start_date" to startDate.trim(),
                                "end_date" to endDate.trim()
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Simpan Kupon", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
