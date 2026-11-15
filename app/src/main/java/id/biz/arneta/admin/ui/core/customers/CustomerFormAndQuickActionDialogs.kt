package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR PENUH NATIVE: TAMBAH / EDIT PELANGGAN (DENGAN QUICK-ADD PAKET & CUSTOM FIELDS)
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
    var serviceType by remember { mutableStateOf(initial?.optString("service_type")?.ifEmpty { null } ?: "Hotspot") }
    var status by remember { mutableStateOf(initial?.optString("status")?.ifEmpty { null } ?: "Active") }
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
    var selectedUsing by remember { mutableStateOf("") }
    var activatePackageOnEdit by remember { mutableStateOf(false) }

    var sendWelcomeMessage by remember { mutableStateOf(!isEdit) }
    var welcomeViaWa by remember { mutableStateOf(true) }
    var welcomeViaSms by remember { mutableStateOf(false) }
    var welcomeViaMail by remember { mutableStateOf(false) }

    var customFields by remember(initial) {
        val cfArr = initial?.optJSONArray("custom_fields")
        val initList = mutableListOf<Pair<String, String>>()
        if (cfArr != null) {
            for (i in 0 until cfArr.length()) {
                val cfObj = cfArr.optJSONObject(i) ?: continue
                val k = cfObj.optString("name", cfObj.optString("field_name", "")).trim()
                val v = cfObj.optString("value", cfObj.optString("field_value", "")).trim()
                if (k.isNotEmpty()) initList.add(k to v)
            }
        }
        mutableStateOf<List<Pair<String, String>>>(initList)
    }
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

    val existingBalance = initial?.optDouble("balance", 0.0) ?: 0.0
    val selectedPlanPrice = selectedPlan?.optDouble("price", 0.0) ?: 0.0
    val isBalanceInsufficient = !isEdit || (selectedPlanPrice > 0.0 && existingBalance < selectedPlanPrice)

    LaunchedEffect(isBalanceInsufficient, selectedUsing) {
        if (isBalanceInsufficient && selectedUsing.equals("Balance", ignoreCase = true)) {
            selectedUsing = ""
        }
    }

    val needPackageAndMethod = !isEdit || activatePackageOnEdit
    val canSubmit = !isSaving && username.isNotBlank() && fullname.isNotBlank() &&
        (!needPackageAndMethod || (selectedPlan != null && selectedUsing.isNotBlank()))

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
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isEdit) "Edit Data Pelanggan" else "Tambah Pelanggan Baru",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isEdit) "@${initial?.optString("username")} • Saldo: ${formatRupiah(existingBalance)}"
                                else "Profil, Kredensial & Aktivasi Langganan",
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
                            text = serviceType.uppercase(),
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
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (needPackageAndMethod && selectedPlan != null) {
                        val displayPrice = if (selectedUsing.equals("Recharge Zero", ignoreCase = true)) {
                            0.0
                        } else {
                            selectedPlanPrice
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Paket Dipilih: ${selectedPlan?.optString("name_plan")}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Router: ${selectedPlan?.optString("routers", "-")} • Metode: ${selectedUsing.ifEmpty { "Belum dipilih" }}",
                                    fontSize = 10.5.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                            Text(
                                text = formatRupiah(displayPrice),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                color = ArnetaGoldPrimary
                            )
                        }
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
                                .weight(0.38f)
                                .height(46.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
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
                            },
                            modifier = Modifier
                                .weight(0.62f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = if (isEdit) Icons.Filled.CheckCircle else Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Buat & Aktifkan",
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
            // SECTION 1: KATEGORI LAYANAN & STATUS AKUN
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
                        text = "1. Jenis Layanan & Status Akun",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaGoldPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Hotspot", "PPPoE", "Others").forEach { t ->
                            FilterChip(
                                selected = serviceType.equals(t, ignoreCase = true),
                                onClick = { serviceType = t },
                                label = { Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = if (serviceType.equals(t, ignoreCase = true)) {
                                    { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                        }
                    }

                    Text(
                        text = "Status Akun Pelanggan:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "Active" to "Active (Aktif)",
                            "Inactive" to "Inactive (Nonaktif)",
                            "Suspended" to "Suspended (Isolir)",
                            "Banned" to "Banned (Blokir)"
                        ).forEach { (stKey, stLabel) ->
                            FilterChip(
                                selected = status.equals(stKey, ignoreCase = true),
                                onClick = { status = stKey },
                                label = { Text(stLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // SECTION 2: IDENTITAS & KONTAK PELANGGAN
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
                        text = "2. Identitas & Kontak Pelanggan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaCyanIncome
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username Pelanggan *") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = fullname,
                        onValueChange = { fullname = it },
                        label = { Text("Nama Lengkap *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isEdit) "Password Baru (Kosongkan jika tidak diubah)" else "Password Login") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phonenumber,
                        onValueChange = { phonenumber = it },
                        label = { Text("Nomor WhatsApp") },
                        placeholder = { Text("0812xxxx / 62812xxxx") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("Kota / Area") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Alamat Pemasangan Lengkap") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = coordinates,
                        onValueChange = { coordinates = it },
                        label = { Text("Koordinat Peta (Lat, Lng)") },
                        placeholder = { Text("-6.200000, 106.816666") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ArnetaCyanIncome) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // SECTION 3: KHUSUS KREDENSIAL PPPOE
            if (serviceType.equals("PPPoE", ignoreCase = true)) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaIndigoAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
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
                                text = "3. Kredensial Khusus PPPoE (Router / ONT)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaGoldLight
                            )
                            if (username.isNotBlank() && pppoeUser.isBlank()) {
                                TextButton(
                                    onClick = {
                                        pppoeUser = username.trim()
                                        if (pppoePass.isBlank() && password.isNotBlank()) {
                                            pppoePass = password.trim()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Samakan dgn Akun", fontSize = 11.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = pppoeUser,
                            onValueChange = { pppoeUser = it },
                            label = { Text("PPPoE Username (Opsional jika sama dgn Username)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pppoePass,
                                onValueChange = { pppoePass = it },
                                label = { Text("PPPoE Password") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pppoeIp,
                                onValueChange = { pppoeIp = it },
                                label = { Text("Remote IP Static") },
                                placeholder = { Text("10.10.10.2") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SECTION 4: AKTIVASI PAKET LANGGANAN
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    if (isEdit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sekaligus Aktifkan / Perpanjang Paket?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaGoldPrimary
                                )
                                Text(
                                    text = "Aktifkan sakelar ini jika ingin langsung mengisi ulang paket pelanggan",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = activatePackageOnEdit,
                                onCheckedChange = { activatePackageOnEdit = it }
                            )
                        }
                    } else {
                        Text(
                            text = "Pilih Paket Awal & Metode Pembayaran",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )
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
                                label = "Metode Pembayaran *",
                                selectedValue = selectedUsing,
                                placeholder = "Pilih Metode...",
                                disabledOptions = if (isBalanceInsufficient) setOf("Balance") else emptySet(),
                                options = paymentUsings.map { u ->
                                    u to when {
                                        u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (${ApiClient.currencyCode} 0)"
                                        u.equals("Balance", ignoreCase = true) -> {
                                            if (isBalanceInsufficient) "💰 Potong Saldo (Saldo tidak cukup)"
                                            else "💰 Potong Saldo (${formatRupiah(existingBalance)})"
                                        }
                                        else -> u
                                    }
                                },
                                icon = Icons.Filled.Payments,
                                onValueSelected = { selectedUsing = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Daftar Paket $serviceType (${matchingPlans.size} tersedia):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaCyanIncome
                        )

                        if (matchingPlans.isEmpty()) {
                            Text(
                                text = "Tidak ada paket $serviceType yang tersedia untuk filter router ini.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                }
            }

            // SECTION 5: ATRIBUT TAMBAHAN (CUSTOM FIELDS) & PESAN SAMBUTAN
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
                        Text(
                            text = "Atribut Tambahan (Custom Fields)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        FilledTonalButton(
                            onClick = { customFields = customFields + ("" to "") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah Atribut", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                                shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kirim Pesan Sambutan Akun", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(
                                "Kirim detail username, password & link login ke pelanggan",
                                fontSize = 11.sp,
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = welcomeViaWa,
                                onClick = { welcomeViaWa = !welcomeViaWa },
                                label = { Text("WhatsApp", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = welcomeViaSms,
                                onClick = { welcomeViaSms = !welcomeViaSms },
                                label = { Text("SMS", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = welcomeViaMail,
                                onClick = { welcomeViaMail = !welcomeViaMail },
                                label = { Text("Email", fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * LAYAR PENUH NATIVE: ISI ULANG PAKET & SALDO CEPAT DARI DAFTAR PELANGGAN
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
    val isDeposit = mode == "deposit"

    var selectedCustomer by remember { mutableStateOf(initialCustomer) }
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerOptions by remember { mutableStateOf(listOf("All" to "Semua Router")) }
    var selectedRouterFilter by remember {
        mutableStateOf(
            initialCustomer?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotEmpty() } ?: "All"
        )
    }
    var paymentUsings by remember { mutableStateOf(listOf("Cash", "Balance", "Recharge Zero")) }
    var selectedUsing by remember { mutableStateOf("") }
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
                if (isDeposit && pType == "BALANCE") {
                    list.add(p)
                } else if (!isDeposit && pType != "BALANCE") {
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
            if (isDeposit) {
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

    val isReadyToSubmit = if (isDeposit) {
        !isProcessing && selectedCustomer != null && (customAmount.isNotBlank() || selectedPlan != null)
    } else {
        !isProcessing && selectedCustomer != null && selectedPlan != null && selectedUsing.isNotBlank()
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
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isDeposit) "Isi Ulang Saldo Pelanggan" else "Isi Ulang Paket Internet",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (selectedCustomer != null) {
                                    "${selectedCustomer?.optString("fullname")} (@${selectedCustomer?.optString("username")}) • Saldo: ${formatRupiah(customerBalance)}"
                                } else {
                                    "Pilih pelanggan dan paket layanan di bawah ini"
                                },
                                fontSize = 11.5.sp,
                                color = ArnetaGoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                    val summaryTotal = if (isDeposit) {
                        if (customAmount.isNotBlank()) customAmount.toDoubleOrNull() ?: 0.0 else currentPlanPrice
                    } else {
                        if (selectedUsing.equals("Recharge Zero", ignoreCase = true)) 0.0 else currentPlanPrice
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDeposit && customAmount.isNotBlank()) "Top-Up Nominal Manual"
                                else (selectedPlan?.optString("name_plan") ?: "Belum memilih paket"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isDeposit) {
                                Text(
                                    text = "Router: ${selectedPlan?.optString("routers", "-")} • Metode: ${selectedUsing.ifEmpty { "Wajib dipilih" }}",
                                    fontSize = 11.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                        }
                        Text(
                            text = formatRupiah(summaryTotal),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
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
                                .weight(0.35f)
                                .height(46.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = isReadyToSubmit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val cust = selectedCustomer ?: return@Button
                                isProcessing = true
                                scope.launch {
                                    val endpoint = if (isDeposit) "admin_deposit" else "admin_prepaid_recharge"
                                    val params = mutableMapOf<String, Any?>(
                                        "customer_id" to cust.optInt("id"),
                                        "using" to selectedUsing,
                                        "note" to depositNote.trim()
                                    )
                                    if (isDeposit && customAmount.isNotBlank()) {
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
                                            fallbackTitle = if (isDeposit) "Isi Ulang Saldo Berhasil!" else "Paket Berhasil Diaktifkan!",
                                            fallbackCustomer = cust,
                                            fallbackPlanName = if (isDeposit && customAmount.isNotBlank()) "Top-Up Saldo Manual" else (selectedPlan?.optString("name_plan") ?: "-"),
                                            fallbackPrice = if (isDeposit && customAmount.isNotBlank()) (customAmount.toDoubleOrNull() ?: 0.0) else (selectedPlan?.optDouble("price", 0.0) ?: 0.0),
                                            fallbackMethod = if (isDeposit) "Deposit" else selectedUsing,
                                            fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                                        )
                                        onSuccess()
                                    } else {
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(0.65f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeposit) Icons.Filled.AccountBalanceWallet else Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isProcessing) "Memproses..." else if (isDeposit) "Tambah Saldo Sekarang" else "Aktifkan Paket Sekarang",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            CustomerLiveSearchPicker(
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { cust ->
                    selectedCustomer = cust
                    if (cust != null && !isDeposit) {
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

            if (isDeposit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it },
                        label = { Text("Nominal Manual (${ApiClient.currencyCode})", fontSize = 11.5.sp) },
                        placeholder = { Text("Contoh: 50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = depositNote,
                        onValueChange = { depositNote = it },
                        label = { Text("Catatan Deposit", fontSize = 11.5.sp) },
                        placeholder = { Text("Setoran tunai") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    label = "Metode Pembayaran *",
                    selectedValue = selectedUsing,
                    placeholder = "-- Pilih Metode Pembayaran --",
                    disabledOptions = if (isBalanceInsufficient) setOf("Balance") else emptySet(),
                    options = paymentUsings.map { u ->
                        u to when {
                            u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (${ApiClient.currencyCode} 0)"
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

            Text(
                text = if (isDeposit) "Pilih Paket Saldo (${displayedPlans.size} tersedia):"
                else "Pilih Paket Internet (${displayedPlans.size} tersedia):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ArnetaGoldPrimary
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
    }
}
