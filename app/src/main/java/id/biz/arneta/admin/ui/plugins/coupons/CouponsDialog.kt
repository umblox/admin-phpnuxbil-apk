package id.biz.arneta.admin.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * LAYAR PENUH NATIVE: FORM TAMBAH & EDIT KUPON PROMO (Sesuai system/controllers/coupons.php)
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
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val defaultEnd = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 30)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
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

    fun applyDurationPreset(days: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val parsedStart = runCatching { sdf.parse(startDate.trim()) }.getOrNull()
        if (parsedStart != null) {
            cal.time = parsedStart
        }
        cal.add(Calendar.DAY_OF_YEAR, days)
        endDate = sdf.format(cal.time)
    }

    fun generateRandomPromoCode() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomSuffix = (1..4).map { chars.random() }.joinToString("")
        code = "PROMO$randomSuffix"
    }

    val valNum = value.toDoubleOrNull() ?: 0.0
    val canSave = !isSaving && code.isNotBlank() && valNum > 0.0 &&
        startDate.isNotBlank() && endDate.isNotBlank()

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
                                    text = if (isEdit) "Edit Kupon Promo" else "Tambah Kupon Promo",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Potongan harga otomatis untuk transaksi pelanggan",
                                    fontSize = 11.5.sp,
                                    color = ArnetaGoldLight
                                )
                            }
                        }

                        Surface(
                            color = if (status.equals("active", true)) ArnetaGreenSuccess.copy(alpha = 0.2f)
                            else ArnetaRedDanger.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = status.uppercase(),
                                color = if (status.equals("active", true)) ArnetaGreenSuccess else ArnetaRedDanger,
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
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = code.ifBlank { "KODE-KUPON" },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = ArnetaGoldPrimary
                            )
                            Text(
                                text = if (type == "percent") {
                                    "Diskon ${valNum.toInt()}% (Maks ${formatRupiah(maxDiscountAmount.toDoubleOrNull() ?: 0.0)})"
                                } else {
                                    "Potongan ${formatRupiah(valNum)}"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
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
                                        val maxDiscNum = maxDiscountAmount.toIntOrNull()?.takeIf { it > 0 } ?: valNum.toInt()
                                        val minOrdNum = minOrderAmount.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: 0.0

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
                                },
                                modifier = Modifier
                                    .weight(0.64f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Simpan Kupon",
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
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "1. Kode & Status Kupon",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaGoldPrimary
                            )
                            TextButton(
                                onClick = { generateRandomPromoCode() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(15.dp), tint = ArnetaCyanIncome)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Acak Kode", fontSize = 11.5.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = status.equals("active", true),
                                onClick = { status = "active" },
                                label = { Text("Status: Active", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = status.equals("inactive", true),
                                onClick = { status = "inactive" },
                                label = { Text("Status: Inactive", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().replace("[^A-Z0-9]".toRegex(), "") },
                            label = { Text("Kode Kupon * (Alfanumerik)") },
                            placeholder = { Text("Contoh: PROMO10 / DISKON5K") },
                            leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = ArnetaGoldPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Deskripsi Promo") },
                            placeholder = { Text("Contoh: Diskon pelanggan baru bulan ini") },
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
                            text = "2. Nilai Potongan & Syarat Pemakaian",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaCyanIncome
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = type == "fixed",
                                onClick = { type = "fixed" },
                                label = { Text("Fixed (Nominal ${ApiClient.currencyCode})", fontSize = 11.5.sp) }
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
                                label = {
                                    Text(
                                        text = if (type == "percent") "Nilai Diskon (%) *" else "Nominal Diskon (${ApiClient.currencyCode}) *",
                                        fontSize = 11.sp
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxDiscountAmount,
                                onValueChange = { maxDiscountAmount = it },
                                label = { Text("Maks. Diskon (${ApiClient.currencyCode})", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minOrderAmount,
                                onValueChange = { minOrderAmount = it },
                                label = { Text("Min. Order (${ApiClient.currencyCode})", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxUsage,
                                onValueChange = { maxUsage = it },
                                label = { Text("Batas Pakai (0=Bebas)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
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
                        Text(
                            text = "3. Periode Masa Berlaku Kupon",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                7 to "+7 Hari",
                                14 to "+14 Hari",
                                30 to "+30 Hari",
                                90 to "+3 Bulan"
                            ).forEach { (days, lbl) ->
                                AssistChip(
                                    onClick = { applyDurationPreset(days) },
                                    label = { Text(lbl, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startDate,
                                onValueChange = { startDate = it },
                                label = { Text("Mulai (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { endDate = it },
                                label = { Text("Berakhir (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
