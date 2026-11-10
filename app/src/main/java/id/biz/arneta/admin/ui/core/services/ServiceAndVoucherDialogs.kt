package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.ArnetaCyanIncome
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * DIALOG GENERATE VOUCHER MASSAL (Sesuai add-voucher di plan.php)
 */
@Composable
internal fun GenerateVoucherDialog(
    onDismiss: () -> Unit,
    onGenerated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("Hotspot") } // Hotspot | PPPOE
    var allPlans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerOptions by remember { mutableStateOf(listOf("All" to "Semua Router")) }
    var selectedRouter by remember { mutableStateOf("All") }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }

    var voucherFormat by remember { mutableStateOf("up") } // up | low | rand | numbers
    var count by remember { mutableStateOf("10") }
    var prefix by remember { mutableStateOf("ARN-") }
    var lengthCode by remember { mutableStateOf("6") }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans")
        if (res.status && res.data != null) {
            val savedPrefix = res.data.optString("voucher_prefix", "")
            if (savedPrefix.isNotEmpty()) {
                prefix = savedPrefix
            }
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                if (!p.optString("type").equals("Balance", ignoreCase = true) && p.optInt("enabled", 1) == 1) {
                    list.add(p)
                }
            }
            allPlans = list
            routerOptions = extractRouterOptions(list, res.data.optJSONArray("routers"))
        }
    }

    val filteredPlans = remember(allPlans, selectedType, selectedRouter) {
        allPlans.filter { p ->
            val matchType = p.optString("type", "").equals(selectedType, ignoreCase = true)
            val matchRouter = selectedRouter == "All" ||
                (selectedRouter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1) ||
                p.optString("routers", "").equals(selectedRouter, ignoreCase = true)
            matchType && matchRouter
        }
    }

    LaunchedEffect(filteredPlans) {
        if (filteredPlans.isNotEmpty() && (selectedPlan == null || filteredPlans.none { it.optInt("id") == selectedPlan?.optInt("id") })) {
            selectedPlan = filteredPlans.first()
        } else if (filteredPlans.isEmpty()) {
            selectedPlan = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Generate Voucher ($selectedType)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Tipe Layanan Voucher:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hotspot" to "Hotspot", "PPPOE" to "PPPoE").forEach { (tKey, tLbl) ->
                        FilterChip(
                            selected = selectedType.equals(tKey, ignoreCase = true),
                            onClick = { selectedType = tKey },
                            label = { Text(tLbl, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                CompactDropdownSelector(
                    label = "Lokasi Server / Router",
                    selectedValue = selectedRouter,
                    options = routerOptions,
                    icon = Icons.Filled.Router,
                    onValueSelected = { selectedRouter = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pilih Paket $selectedType (${filteredPlans.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArnetaCyanIncome
                )

                if (filteredPlans.isEmpty()) {
                    Text("Tidak ada paket $selectedType aktif pada router ini.", fontSize = 11.5.sp)
                } else {
                    filteredPlans.forEach { p ->
                        SelectablePlanCard(
                            plan = p,
                            isSelected = selectedPlan?.optInt("id") == p.optInt("id"),
                            onClick = { selectedPlan = p }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                CompactDropdownSelector(
                    label = "Format Karakter Voucher",
                    selectedValue = voucherFormat,
                    options = listOf(
                        "up" to "UPPERCASE (HURUF BESAR & ANGKA)",
                        "low" to "lowercase (huruf kecil & angka)",
                        "rand" to "Random Case (Acak Besar & Kecil)",
                        "numbers" to "Numbers (Angka Unik, Min. 6 Digit)"
                    ),
                    icon = Icons.Filled.Password,
                    onValueSelected = {
                        voucherFormat = it
                        if (it == "numbers" && (lengthCode.toIntOrNull() ?: 6) < 6) {
                            lengthCode = "6"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it },
                        label = { Text("Jumlah Voucher") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lengthCode,
                        onValueChange = { lengthCode = it },
                        label = { Text("Panjang Kode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Voucher Prefix (Otomatis Disimpan ke Web)") },
                    placeholder = { Text("Misal: ARN-") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isGenerating && selectedPlan != null && (count.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    val p = selectedPlan ?: return@Button
                    isGenerating = true
                    scope.launch {
                        val srv = if (p.optInt("is_radius", 0) == 1) "radius" else p.optString("routers")
                        val res = ApiClient.request(
                            "admin_voucher_generate",
                            mapOf(
                                "type" to selectedType,
                                "plan_id" to p.optInt("id"),
                                "plan" to p.optInt("id"),
                                "server" to srv,
                                "voucher_format" to voucherFormat,
                                "count" to (count.toIntOrNull() ?: 10),
                                "numbervoucher" to (count.toIntOrNull() ?: 10),
                                "prefix" to prefix,
                                "length_code" to (lengthCode.toIntOrNull() ?: 6),
                                "lengthcode" to (lengthCode.toIntOrNull() ?: 6)
                            )
                        )
                        isGenerating = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onGenerated()
                    }
                }
            ) {
                Text(if (isGenerating) "Membuat..." else "Generate Voucher", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * DIALOG EDIT PAKET & MASA AKTIF PELANGGAN AKTIF (Sesuai case 'edit-post' di plan.php)
 */
@Composable
internal fun EditActiveCustomerPlanDialog(
    activeItem: JSONObject,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rechargeId = activeItem.optInt("id")
    val currentType = activeItem.optString("type", "Hotspot")
    var selectedPlanId by remember { mutableIntStateOf(activeItem.optInt("plan_id", 0)) }
    var expirationDate by remember { mutableStateOf(activeItem.optString("expiration", "")) }
    var expirationTime by remember { mutableStateOf(activeItem.optString("time", "23:59:59")) }
    var availablePlans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans", mapOf("type" to currentType))
        val arr = res.data?.optJSONArray("plans") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        availablePlans = list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Langganan @${activeItem.optString("username")}",
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
                Text(
                    text = "Mengubah paket akan otomatis memindahkan profil pelanggan di Router MikroTik.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (availablePlans.isNotEmpty()) {
                    CompactDropdownSelector(
                        label = "Pilih Paket Langganan ($currentType)",
                        selectedValue = selectedPlanId.toString(),
                        options = availablePlans.map { p ->
                            p.optInt("id").toString() to "${p.optString("name_plan")} (${formatRupiah(p.optDouble("price", 0.0))})"
                        },
                        icon = Icons.Filled.Speed,
                        onValueSelected = { selectedPlanId = it.toIntOrNull() ?: selectedPlanId },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = expirationDate,
                    onValueChange = { expirationDate = it },
                    label = { Text("Tanggal Berakhir (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = expirationTime,
                    onValueChange = { expirationTime = it },
                    label = { Text("Jam Berakhir (HH:MM:SS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && selectedPlanId > 0 && expirationDate.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_prepaid_edit",
                            mapOf(
                                "id" to rechargeId,
                                "id_plan" to selectedPlanId,
                                "expiration" to expirationDate.trim(),
                                "time" to expirationTime.trim()
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan Perubahan", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * DIALOG EXTEND / PERPANJANG SEMENTARA PAKET EXPIRED (Sesuai case 'extend' di plan.php)
 */
@Composable
internal fun ExtendCustomerPlanDialog(
    activeItem: JSONObject,
    onDismiss: () -> Unit,
    onExtended: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rechargeId = activeItem.optInt("id")
    var extendDays by remember { mutableStateOf("3") }
    var isProcessing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Extend Masa Aktif @${activeItem.optString("username")}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Perpanjang masa aktif paket ${activeItem.optString("namebp")} dan aktifkan kembali akun di Router MikroTik:",
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1", "2", "3", "7", "14", "30").forEach { d ->
                        FilterChip(
                            selected = extendDays == d,
                            onClick = { extendDays = d },
                            label = { Text("$d Hari", fontSize = 11.5.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = extendDays,
                    onValueChange = { extendDays = it },
                    label = { Text("Jumlah Hari Extend") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing && (extendDays.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isProcessing = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_prepaid_extend",
                            mapOf(
                                "id" to rechargeId,
                                "days" to (extendDays.toIntOrNull() ?: 3)
                            )
                        )
                        isProcessing = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onExtended()
                    }
                }
            ) {
                Text(if (isProcessing) "Memproses..." else "Extend Sekarang", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
