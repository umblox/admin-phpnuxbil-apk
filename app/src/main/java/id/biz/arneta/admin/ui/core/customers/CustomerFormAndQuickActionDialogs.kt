package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * DIALOG TAMBAH / EDIT PELANGGAN (DENGAN QUICK-ADD PAKET & CUSTOM FIELDS)
 */
@Composable
internal fun CustomerFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var fullname by remember { mutableStateOf(initial?.optString("fullname") ?: "") }
    var password by remember { mutableStateOf(if (isEdit) "" else "123456") }
    var phonenumber by remember { mutableStateOf(initial?.optString("phonenumber") ?: "") }
    var email by remember { mutableStateOf(initial?.optString("email") ?: "") }
    var city by remember { mutableStateOf(initial?.optString("city") ?: "") }
    var address by remember { mutableStateOf(initial?.optString("address") ?: "") }
    var serviceType by remember { mutableStateOf(initial?.optString("service_type") ?: "Hotspot") }
    var status by remember { mutableStateOf(initial?.optString("status") ?: "Active") }
    var pppoeUser by remember { mutableStateOf(initial?.optString("pppoe_username") ?: "") }
    var pppoePass by remember { mutableStateOf(initial?.optString("pppoe_password") ?: "") }
    var pppoeIp by remember { mutableStateOf(initial?.optString("pppoe_ip") ?: "") }
    var coordinates by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }

    var allInternetPlans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerOptions by remember { mutableStateOf(listOf("All" to "Semua Router")) }
    var selectedRouterFilter by remember {
        mutableStateOf(
            initial?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotEmpty() } ?: "All"
        )
    }
    var paymentUsings by remember { mutableStateOf(listOf("Cash", "Recharge Zero")) }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var selectedUsing by remember { mutableStateOf("") } // Kosong di awal agar Admin memilih manual
    var activatePackageOnEdit by remember { mutableStateOf(false) }

    var sendWelcomeMessage by remember { mutableStateOf(!isEdit) }
    var welcomeViaWa by remember { mutableStateOf(true) }
    var welcomeViaSms by remember { mutableStateOf(false) }
    var welcomeViaMail by remember { mutableStateOf(false) }

    var customFields by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans")
        if (res.status && res.data != null) {
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val pList = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                if (!p.optString("type").equals("Balance", ignoreCase = true)) {
                    pList.add(p)
                }
            }
            allInternetPlans = pList
            routerOptions = extractRouterOptions(pList, res.data.optJSONArray("routers"))

            val uArr = res.data.optJSONArray("usings")
            if (uArr != null && uArr.length() > 0) {
                val uList = mutableListOf<String>()
                for (i in 0 until uArr.length()) {
                    val item = uArr.optString(i)
                    if (!isEdit && item.equals("Balance", ignoreCase = true)) continue
                    uList.add(item)
                }
                paymentUsings = uList
            }
        }
    }

    val matchingPlans = remember(allInternetPlans, serviceType, selectedRouterFilter) {
        allInternetPlans.filter { p ->
            val matchType = when (serviceType.uppercase()) {
                "HOTSPOT" -> p.optString("type").equals("Hotspot", ignoreCase = true)
                "PPPOE" -> p.optString("type").equals("PPPoE", ignoreCase = true)
                else -> true
            }
            val matchRouter = selectedRouterFilter == "All" ||
                p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true) ||
                (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1)
            matchType && matchRouter
        }
    }

    LaunchedEffect(matchingPlans, isEdit, activatePackageOnEdit) {
        if ((!isEdit || activatePackageOnEdit) && matchingPlans.isNotEmpty()) {
            if (selectedPlan == null || matchingPlans.none { it.optInt("id") == selectedPlan?.optInt("id") }) {
                selectedPlan = matchingPlans.first()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Edit else Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = ArnetaGoldPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEdit) "Edit Pelanggan" else "Tambah Pelanggan Baru",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.5.sp
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
                Text("Jenis Layanan Pelanggan:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hotspot", "PPPoE", "Others").forEach { t ->
                        FilterChip(
                            selected = serviceType == t,
                            onClick = { serviceType = t },
                            label = { Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullname,
                    onValueChange = { fullname = it },
                    label = { Text("Nama Lengkap") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isEdit) "Password Baru (Kosongkan jika tetap)" else "Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phonenumber,
                    onValueChange = { phonenumber = it },
                    label = { Text("Nomor WhatsApp") },
                    placeholder = { Text("0812xxxx / 62812xxxx") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Kota / Area") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (serviceType == "PPPoE") {
                    OutlinedTextField(
                        value = pppoeUser,
                        onValueChange = { pppoeUser = it },
                        label = { Text("PPPoE Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pppoePass,
                        onValueChange = { pppoePass = it },
                        label = { Text("PPPoE Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pppoeIp,
                        onValueChange = { pppoeIp = it },
                        label = { Text("Remote IP PPPoE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat Pemasangan") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coordinates,
                    onValueChange = { coordinates = it },
                    label = { Text("Koordinat Peta (Lat, Lng)") },
                    placeholder = { Text("-6.200000, 106.816666") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (isEdit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sekaligus Aktifkan / Isi Ulang Paket?", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = activatePackageOnEdit,
                            onCheckedChange = { activatePackageOnEdit = it }
                        )
                    }
                }

                if (!isEdit || activatePackageOnEdit) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CompactDropdownSelector(
                            label = "Lokasi Router",
                            selectedValue = selectedRouterFilter,
                            options = routerOptions,
                            icon = Icons.Filled.Router,
                            onValueSelected = { selectedRouterFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                        CompactDropdownSelector(
                            label = "Metode Pembayaran",
                            selectedValue = selectedUsing,
                            placeholder = "Pilih Metode...",
                            options = paymentUsings.map { u ->
                                u to if (u.equals("Recharge Zero", ignoreCase = true)) "⚡ Recharge Zero (Rp 0)" else u
                            },
                            icon = Icons.Filled.Payments,
                            onValueSelected = { selectedUsing = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Pilih Paket $serviceType (${matchingPlans.size} tersedia):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArnetaGoldPrimary
                    )

                    if (matchingPlans.isEmpty()) {
                        Text("Tidak ada paket $serviceType pada router ini.", fontSize = 11.5.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            matchingPlans.forEach { p ->
                                SelectablePlanCard(
                                    plan = p,
                                    isSelected = selectedPlan?.optInt("id") == p.optInt("id"),
                                    onClick = { selectedPlan = p }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Atribut Tambahan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { customFields = customFields + ("" to "") }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Atribut", fontSize = 11.5.sp, color = ArnetaGoldPrimary)
                    }
                }

                customFields.forEachIndexed { idx, pair ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pair.first,
                            onValueChange = { newKey ->
                                val mutable = customFields.toMutableList()
                                mutable[idx] = newKey to pair.second
                                customFields = mutable
                            },
                            label = { Text("Nama Atribut", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pair.second,
                            onValueChange = { newVal ->
                                val mutable = customFields.toMutableList()
                                mutable[idx] = pair.first to newVal
                                customFields = mutable
                            },
                            label = { Text("Nilai", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val mutable = customFields.toMutableList()
                                mutable.removeAt(idx)
                                customFields = mutable
                            }
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kirim Pesan Sambutan", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text(
                                    "Kirim detail akun & link login ke pelanggan",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = sendWelcomeMessage,
                                onCheckedChange = { sendWelcomeMessage = it }
                            )
                        }

                        AnimatedVisibility(visible = sendWelcomeMessage) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                FilterChip(
                                    selected = welcomeViaWa,
                                    onClick = { welcomeViaWa = !welcomeViaWa },
                                    label = { Text("WhatsApp", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = welcomeViaSms,
                                    onClick = { welcomeViaSms = !welcomeViaSms },
                                    label = { Text("SMS", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = welcomeViaMail,
                                    onClick = { welcomeViaMail = !welcomeViaMail },
                                    label = { Text("Email", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val needPackageAndMethod = !isEdit || activatePackageOnEdit
            val canSubmit = !isSaving && username.isNotBlank() && fullname.isNotBlank() &&
                (!needPackageAndMethod || (selectedPlan != null && selectedUsing.isNotBlank()))

            Button(
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val cfNames = customFields.map { it.first.trim() }.filter { it.isNotEmpty() }
                        val cfValues = customFields.map { it.second.trim() }.filter { it.isNotEmpty() }

                        val params = mutableMapOf<String, Any?>(
                            "id" to (initial?.optInt("id") ?: 0),
                            "username" to username.trim(),
                            "fullname" to fullname.trim(),
                            "password" to password.trim(),
                            "phonenumber" to phonenumber.trim(),
                            "email" to email.trim(),
                            "city" to city.trim(),
                            "address" to address.trim(),
                            "service_type" to serviceType,
                            "status" to status,
                            "pppoe_username" to pppoeUser.trim(),
                            "pppoe_password" to pppoePass.trim(),
                            "pppoe_ip" to pppoeIp.trim(),
                            "coordinates" to coordinates.trim(),
                            "using" to selectedUsing,
                            "send_welcome_message" to sendWelcomeMessage,
                            "wa" to welcomeViaWa,
                            "sms" to welcomeViaSms,
                            "mail" to welcomeViaMail
                        )

                        if (needPackageAndMethod) {
                            params["ppln"] = selectedPlan?.optInt("id") ?: 0
                        }
                        if (cfNames.isNotEmpty() && cfNames.size == cfValues.size) {
                            params["custom_field_name"] = cfNames
                            params["custom_field_value"] = cfValues
                        }

                        val res = ApiClient.request("admin_customer_save", params)
                        isSaving = false
                        if (res.status) {
                            if (needPackageAndMethod && selectedPlan != null) {
                                ArnetaTransactionToastManager.showFromApiResponse(
                                    resData = res.data,
                                    fallbackTitle = if (isEdit) "Data & Paket Diperbarui!" else "Pelanggan & Paket Aktif!",
                                    fallbackCustomer = res.data,
                                    fallbackPlanName = selectedPlan?.optString("name_plan") ?: "-",
                                    fallbackPrice = selectedPlan?.optDouble("price", 0.0) ?: 0.0,
                                    fallbackMethod = selectedUsing,
                                    fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                                )
                            } else {
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            }
                            onSaved()
                        } else {
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text(
                    text = if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Buat & Aktifkan",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * DIALOG CEPAT ISI ULANG PAKET & SALDO DARI DAFTAR PELANGGAN
 */
@Composable
fun QuickRechargeOrDepositDialog(
    initialCustomer: JSONObject?,
    mode: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSuperAdmin = remember { ApiClient.adminRole.equals("SuperAdmin", ignoreCase = true) }

    var selectedCustomer by remember { mutableStateOf(initialCustomer) }
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerOptions by remember { mutableStateOf(listOf("All" to "Semua Router")) }
    var selectedRouterFilter by remember {
        mutableStateOf(
            initialCustomer?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotEmpty() } ?: "All"
        )
    }
    var paymentUsings by remember { mutableStateOf(listOf("Cash", "Balance", "Recharge Zero")) }
    var selectedUsing by remember { mutableStateOf("") } // Wajib dipilih manual oleh Admin
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var depositNote by remember { mutableStateOf("") }
    var filterPlanType by remember {
        mutableStateOf(
            if (isSuperAdmin && initialCustomer != null) resolveCustomerPlanCategory(initialCustomer) else "All"
        )
    }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        val res = ApiClient.request("admin_plans")
        if (res.status && res.data != null) {
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val pType = p.optString("type").uppercase()
                if (mode == "deposit" && pType == "BALANCE") {
                    list.add(p)
                } else if (mode != "deposit" && pType != "BALANCE") {
                    list.add(p)
                }
            }
            plans = list
            routerOptions = extractRouterOptions(list, res.data.optJSONArray("routers"))

            val uArr = res.data.optJSONArray("usings")
            if (uArr != null && uArr.length() > 0) {
                val uList = mutableListOf<String>()
                for (i in 0 until uArr.length()) uList.add(uArr.optString(i))
                paymentUsings = uList
            }
        }
    }

    val displayedPlans = remember(plans, filterPlanType, selectedRouterFilter, mode) {
        plans.filter { p ->
            if (mode == "deposit") {
                true
            } else {
                val matchType = filterPlanType == "All" || p.optString("type").equals(filterPlanType, ignoreCase = true)
                val matchRouter = selectedRouterFilter == "All" ||
                    p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true) ||
                    (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1)
                matchType && matchRouter
            }
        }
    }

    LaunchedEffect(displayedPlans) {
        if (displayedPlans.isNotEmpty() && (selectedPlan == null || displayedPlans.none { it.optInt("id") == selectedPlan?.optInt("id") })) {
            selectedPlan = displayedPlans.first()
        }
    }

    val customerBalance = selectedCustomer?.optDouble("balance", 0.0) ?: 0.0
    val currentPlanPrice = selectedPlan?.optDouble("price", 0.0) ?: 0.0
    val isBalanceInsufficient = selectedCustomer == null || (currentPlanPrice > 0.0 && customerBalance < currentPlanPrice)

    LaunchedEffect(isBalanceInsufficient, selectedUsing) {
        if (isBalanceInsufficient && selectedUsing.equals("Balance", ignoreCase = true)) {
            selectedUsing = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (mode == "deposit") "💰 Isi Ulang Saldo Pelanggan" else "⚡ Isi Ulang Paket Internet",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CustomerLiveSearchPicker(
                    selectedCustomer = selectedCustomer,
                    onCustomerSelected = { cust ->
                        selectedCustomer = cust
                        if (cust != null && mode != "deposit") {
                            if (isSuperAdmin) {
                                filterPlanType = resolveCustomerPlanCategory(cust)
                            }
                            val custRouter = cust.optJSONObject("active_package")?.optString("routers", "") ?: ""
                            if (custRouter.isNotEmpty() && routerOptions.any { it.first.equals(custRouter, ignoreCase = true) }) {
                                selectedRouterFilter = custRouter
                            }
                        }
                    }
                )

                if (mode == "deposit") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customAmount,
                            onValueChange = { customAmount = it },
                            label = { Text("Nominal Manual (Rp)", fontSize = 11.sp) },
                            placeholder = { Text("50000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = depositNote,
                            onValueChange = { depositNote = it },
                            label = { Text("Catatan", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (displayedPlans.isNotEmpty()) {
                        Text("Atau Pilih Paket Saldo:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CompactDropdownSelector(
                            label = "Server / Router",
                            selectedValue = selectedRouterFilter,
                            options = routerOptions,
                            icon = Icons.Filled.Router,
                            onValueSelected = { selectedRouterFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                        CompactDropdownSelector(
                            label = "Jenis Paket",
                            selectedValue = filterPlanType,
                            options = listOf(
                                "All" to "Semua Paket",
                                "Hotspot" to "Hotspot",
                                "PPPoE" to "PPPoE"
                            ),
                            icon = Icons.Filled.Wifi,
                            onValueSelected = { filterPlanType = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CompactDropdownSelector(
                        label = "Metode Pembayaran",
                        selectedValue = selectedUsing,
                        placeholder = "-- Pilih Metode Pembayaran --",
                        disabledOptions = if (isBalanceInsufficient) setOf("Balance") else emptySet(),
                        options = paymentUsings.map { u ->
                            u to when {
                                u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (Rp 0)"
                                u.equals("Balance", ignoreCase = true) -> {
                                    if (isBalanceInsufficient) "💰 Potong Saldo (Saldo tidak mencukupi)"
                                    else "💰 Potong Saldo Pelanggan (${formatRupiah(customerBalance)})"
                                }
                                else -> u
                            }
                        },
                        icon = Icons.Filled.Payments,
                        onValueSelected = { selectedUsing = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 235.dp)
                ) {
                    items(displayedPlans) { p ->
                        val pid = p.optInt("id")
                        val isSel = selectedPlan?.optInt("id") == pid && customAmount.isBlank()
                        SelectablePlanCard(
                            plan = p,
                            isSelected = isSel,
                            onClick = {
                                selectedPlan = p
                                customAmount = ""
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isReadyToSubmit = if (mode == "deposit") {
                !isProcessing && selectedCustomer != null && (customAmount.isNotBlank() || selectedPlan != null)
            } else {
                !isProcessing && selectedCustomer != null && selectedPlan != null && selectedUsing.isNotBlank()
            }

            Button(
                enabled = isReadyToSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    isProcessing = true
                    scope.launch {
                        val endpoint = if (mode == "deposit") "admin_deposit" else "admin_prepaid_recharge"
                        val params = mutableMapOf<String, Any?>(
                            "customer_id" to cust.optInt("id"),
                            "using" to selectedUsing,
                            "note" to depositNote.trim()
                        )
                        if (mode == "deposit" && customAmount.isNotBlank()) {
                            params["amount"] = customAmount.toDoubleOrNull() ?: 0.0
                        } else if (selectedPlan != null) {
                            params["plan_id"] = selectedPlan!!.optInt("id")
                            params["server"] = selectedPlan!!.optString("routers")
                            params["routers"] = selectedPlan!!.optString("routers")
                        }

                        val res = ApiClient.request(endpoint, params)
                        isProcessing = false
                        if (res.status) {
                            ArnetaTransactionToastManager.showFromApiResponse(
                                resData = res.data,
                                fallbackTitle = if (mode == "deposit") "Isi Ulang Saldo Berhasil!" else "Paket Berhasil Diaktifkan!",
                                fallbackCustomer = cust,
                                fallbackPlanName = if (mode == "deposit" && customAmount.isNotBlank()) "Top-Up Saldo Manual" else (selectedPlan?.optString("name_plan") ?: "-"),
                                fallbackPrice = if (mode == "deposit" && customAmount.isNotBlank()) (customAmount.toDoubleOrNull() ?: 0.0) else (selectedPlan?.optDouble("price", 0.0) ?: 0.0),
                                fallbackMethod = if (mode == "deposit") "Deposit" else selectedUsing,
                                fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                            )
                            onSuccess()
                        } else {
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text(
                    text = if (isProcessing) "Memproses..." else if (mode == "deposit") "Tambah Saldo" else "Aktifkan Paket",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
