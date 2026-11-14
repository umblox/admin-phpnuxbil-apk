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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * LAYAR PENUH NATIVE: GENERATE VOUCHER MASSAL (Sesuai add-voucher di plan.php)
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
    var prefix by remember { mutableStateOf("") }
    var lengthCode by remember { mutableStateOf("6") }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans")
        if (res.status && res.data != null) {
            prefix = res.data.optString("voucher_prefix", "")
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

    val countInt = (count.toIntOrNull() ?: 0).coerceAtLeast(0)
    val unitPrice = selectedPlan?.optDouble("price", 0.0) ?: 0.0
    val totalValue = countInt * unitPrice
    val canGenerate = !isGenerating && selectedPlan != null && countInt > 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
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
                            .windowInsetsPadding(WindowInsets.statusBars)
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
                                    text = "Generate Voucher Massal",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Buat kode voucher siap cetak & kirim WhatsApp",
                                    fontSize = 11.5.sp,
                                    color = ArnetaGoldLight
                                )
                            }
                        }

                        Surface(
                            color = ArnetaGoldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = selectedType.uppercase(),
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
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$countInt Voucher × ${selectedPlan?.optString("name_plan") ?: "-"}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Estimasi Nilai Total Voucher",
                                    fontSize = 11.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                            Text(
                                text = formatRupiah(totalValue),
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
                                enabled = canGenerate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArnetaGoldPrimary,
                                    contentColor = Color(0xFF0B0F19)
                                ),
                                shape = RoundedCornerShape(12.dp),
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
                                                "count" to countInt.coerceAtLeast(1),
                                                "numbervoucher" to countInt.coerceAtLeast(1),
                                                "prefix" to prefix.trim(),
                                                "length_code" to (lengthCode.toIntOrNull() ?: 6),
                                                "lengthcode" to (lengthCode.toIntOrNull() ?: 6)
                                            )
                                        )
                                        isGenerating = false
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        if (res.status) onGenerated()
                                    }
                                },
                                modifier = Modifier
                                    .weight(0.64f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isGenerating) "Membuat..." else "Generate Voucher",
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
                            text = "1. Pengaturan Karakter & Jumlah Voucher",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        CompactDropdownSelector(
                            label = "Format Karakter Kode Voucher",
                            selectedValue = voucherFormat,
                            options = listOf(
                                "up" to "UPPERCASE (HURUF BESAR & ANGKA)",
                                "low" to "lowercase (huruf kecil & angka)",
                                "rand" to "Random Case (Kombinasi Besar & Kecil)",
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("5", "10", "20", "50", "100").forEach { presetCnt ->
                                FilterChip(
                                    selected = count == presetCnt,
                                    onClick = { count = presetCnt },
                                    label = { Text("$presetCnt Vcr", fontSize = 11.5.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = count,
                                onValueChange = { count = it },
                                label = { Text("Jumlah Voucher *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lengthCode,
                                onValueChange = { lengthCode = it },
                                label = { Text("Panjang Kode *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Voucher Prefix (Opsional, Mengikuti Pengaturan Web)") },
                            placeholder = { Text("Kosongkan jika tanpa awalan") },
                            singleLine = true,
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
                            text = "2. Pilih Kategori, Router & Paket Voucher",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaCyanIncome
                        )

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
                            text = "Daftar Paket $selectedType (${filteredPlans.size} tersedia):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaGoldPrimary
                        )

                        if (filteredPlans.isEmpty()) {
                            Text(
                                text = "Tidak ada paket $selectedType aktif pada router ini.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                filteredPlans.forEach { p ->
                                    SelectablePlanCard(
                                        plan = p,
                                        isSelected = selectedPlan?.optInt("id") == p.optInt("id"),
                                        onClick = { selectedPlan = p }
                                    )
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

/**
 * LAYAR PENUH NATIVE: EDIT PAKET & MASA AKTIF PELANGGAN AKTIF (Sesuai case 'edit-post' di plan.php)
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

    fun addDaysToExpiration(daysToAdd: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val parsed = runCatching { sdf.parse(expirationDate.trim()) }.getOrNull()
        if (parsed != null && parsed.time > System.currentTimeMillis()) {
            cal.time = parsed
        }
        cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
        expirationDate = sdf.format(cal.time)
    }

    val canSave = !isSaving && selectedPlanId > 0 && expirationDate.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
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
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Edit Langganan @${activeItem.optString("username")}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${activeItem.optString("fullname")} • Router: ${activeItem.optString("routers", "-")}",
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                            },
                            modifier = Modifier
                                .weight(0.64f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else "Simpan Perubahan",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
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
                            text = "1. Tanggal & Jam Berakhir Langganan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                1 to "+1 Hari",
                                3 to "+3 Hari",
                                7 to "+7 Hari",
                                14 to "+14 Hari",
                                30 to "+30 Hari"
                            ).forEach { (days, lbl) ->
                                AssistChip(
                                    onClick = { addDaysToExpiration(days) },
                                    label = { Text(lbl, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = expirationDate,
                                onValueChange = { expirationDate = it },
                                label = { Text("Tanggal Berakhir (YYYY-MM-DD)") },
                                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = ArnetaGoldPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f)
                            )

                            OutlinedTextField(
                                value = expirationTime,
                                onValueChange = { expirationTime = it },
                                label = { Text("Jam (HH:MM:SS)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.8f)
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
                            text = "2. Paket Langganan ($currentType)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaCyanIncome
                        )
                        Text(
                            text = "Mengubah paket akan otomatis memperbarui profil kecepatan pelanggan di Router MikroTik.",
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

                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                availablePlans.forEach { p ->
                                    val pid = p.optInt("id")
                                    SelectablePlanCard(
                                        plan = p,
                                        isSelected = selectedPlanId == pid,
                                        onClick = { selectedPlanId = pid }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * LAYAR PENUH NATIVE: EXTEND / PERPANJANG SEMENTARA PAKET EXPIRED (Sesuai case 'extend' di plan.php)
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

    val daysInt = (extendDays.toIntOrNull() ?: 0).coerceAtLeast(0)
    val previewDate = remember(daysInt) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, daysInt)
        sdf.format(cal.time)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
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
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Extend Masa Aktif @${activeItem.optString("username")}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Paket: ${activeItem.optString("namebp")} • Router: ${activeItem.optString("routers", "-")}",
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                            enabled = !isProcessing && daysInt > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_prepaid_extend",
                                        mapOf(
                                            "id" to rechargeId,
                                            "days" to daysInt
                                        )
                                    )
                                    isProcessing = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    if (res.status) onExtended()
                                }
                            },
                            modifier = Modifier
                                .weight(0.64f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isProcessing) "Memproses..." else "Extend Sekarang ($daysInt Hari)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
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
                            text = "Perpanjang Masa Aktif Sementara",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )
                        Text(
                            text = "Fitur ini akan mengaktifkan kembali akun pelanggan di Router MikroTik untuk sementara waktu sesuai jumlah hari yang dipilih.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf("1", "2", "3", "7", "14", "30").forEach { d ->
                                FilterChip(
                                    selected = extendDays == d,
                                    onClick = { extendDays = d },
                                    label = { Text("$d Hari", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = extendDays,
                            onValueChange = { extendDays = it },
                            label = { Text("Jumlah Hari Extend") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            color = ArnetaCyanIncome.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text("Estimasi Tanggal Berakhir Baru:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "$previewDate ${activeItem.optString("time", "23:59:59")}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaCyanIncome
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
