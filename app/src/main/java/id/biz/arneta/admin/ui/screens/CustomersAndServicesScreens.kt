package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import id.biz.arneta.admin.util.BluetoothPrinterHelper
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR MANAJEMEN PELANGGAN + ADD CUSTOMER + DETAIL + SYNC MIKROTIK
 */
@Composable
fun AdminCustomersScreen(
    initialSearch: String = "",
    openAddDialogInitially: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(initialSearch) }
    var selectedType by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }
    var totalCustomers by remember { mutableIntStateOf(0) }
    var customerList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    // Dialog Tambah / Edit Pelanggan
    var showFormDialog by remember { mutableStateOf(openAddDialogInitially) }
    var editingCustomer by remember { mutableStateOf<JSONObject?>(null) }

    // Dialog Cepat Isi Ulang Paket / Saldo dari Kartu Pelanggan
    var quickActionCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var quickActionMode by remember { mutableStateOf("recharge") } // recharge | deposit

    fun loadCustomers() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request(
                "admin_customers",
                mapOf(
                    "search" to searchQuery,
                    "service_type" to if (selectedType == "All") "" else selectedType,
                    "limit" to 60
                )
            )
            if (res.status && res.data != null) {
                totalCustomers = res.data.optInt("total", 0)
                val arr = res.data.optJSONArray("customers") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                customerList = list
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedType) {
        loadCustomers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header + Tombol Add Customer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Pelanggan ($totalCustomers)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Kelola akun Hotspot, PPPoE & Saldo",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            Button(
                onClick = {
                    editingCustomer = null
                    showFormDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Customer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bar Pencarian & Filter Tipe Layanan
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama, username, WA, IP...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            loadCustomers()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )

            FilledIconButton(
                onClick = { loadCustomers() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ArnetaIndigoAccent)
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Cari", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips: Semua | Hotspot | PPPoE
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("All" to "Semua", "Hotspot" to "Hotspot", "PPPoE" to "PPPoE").forEach { (key, label) ->
                FilterChip(
                    selected = selectedType == key,
                    onClick = { selectedType = key },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(customerList) { cust ->
                    val pkg = cust.optJSONObject("active_package")
                    val pkgStatus = pkg?.optString("status", "off") ?: "none"
                    val isOnlineOrActive = pkgStatus == "on"

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isOnlineOrActive) ArnetaGreenSuccess.copy(alpha = 0.18f)
                                                else ArnetaRedDanger.copy(alpha = 0.18f)
                                            )
                                    ) {
                                        Text(
                                            text = cust.optString("fullname", "U").take(1).uppercase(),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isOnlineOrActive) ArnetaGreenSuccess else ArnetaRedDanger
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = cust.optString("fullname"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${cust.optString("username")} • ${cust.optString("service_type")}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatRupiah(cust.optDouble("balance", 0.0)),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                    Surface(
                                        color = if (isOnlineOrActive) ArnetaGreenSuccess.copy(alpha = 0.15f)
                                        else ArnetaOrangeActive.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (pkg != null) "${pkg.optString("namebp")} (${pkgStatus.uppercase()})" else "Non-Paket",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOnlineOrActive) ArnetaGreenSuccess else ArnetaOrangeActive,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (pkg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Exp: ${pkg.optString("expiration")} ${pkg.optString("time")} • Router: ${pkg.optString("routers")}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Baris Tombol Tindakan Cepat per Pelanggan
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. Isi Ulang Paket
                                AssistChip(
                                    onClick = {
                                        quickActionCustomer = cust
                                        quickActionMode = "recharge"
                                    },
                                    label = { Text("Isi Paket", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                )

                                // 2. Top-up Saldo
                                AssistChip(
                                    onClick = {
                                        quickActionCustomer = cust
                                        quickActionMode = "deposit"
                                    },
                                    label = { Text("+Saldo", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                )

                                // 3. Sync ke Router MikroTik
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val sRes = ApiClient.request("admin_customer_sync", mapOf("id" to cust.optInt("id")))
                                            Toast.makeText(context, sRes.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = "Sync Router", tint = ArnetaCyanIncome)
                                }

                                // 4. Edit Pelanggan
                                IconButton(
                                    onClick = {
                                        editingCustomer = cust
                                        showFormDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                }

                                // 5. Hapus Pelanggan
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val dRes = ApiClient.request("admin_customer_delete", mapOf("id" to cust.optInt("id")))
                                            Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                            if (dRes.status) loadCustomers()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Add / Edit Customer
    if (showFormDialog) {
        CustomerFormDialog(
            initial = editingCustomer,
            onDismiss = { showFormDialog = false },
            onSaved = {
                showFormDialog = false
                loadCustomers()
            }
        )
    }

    // Dialog Cepat Isi Paket / Isi Saldo
    if (quickActionCustomer != null) {
        QuickRechargeOrDepositDialog(
            customer = quickActionCustomer!!,
            mode = quickActionMode,
            onDismiss = { quickActionCustomer = null },
            onSuccess = {
                quickActionCustomer = null
                loadCustomers()
            }
        )
    }
}

@Composable
private fun CustomerFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var fullname by remember { mutableStateOf(initial?.optString("fullname") ?: "") }
    var password by remember { mutableStateOf("") }
    var phonenumber by remember { mutableStateOf(initial?.optString("phonenumber") ?: "") }
    var email by remember { mutableStateOf(initial?.optString("email") ?: "") }
    var address by remember { mutableStateOf(initial?.optString("address") ?: "") }
    var serviceType by remember { mutableStateOf(initial?.optString("service_type") ?: "Hotspot") }
    var status by remember { mutableStateOf(initial?.optString("status") ?: "Active") }
    var pppoeUser by remember { mutableStateOf(initial?.optString("pppoe_username") ?: "") }
    var pppoePass by remember { mutableStateOf(initial?.optString("pppoe_password") ?: "") }
    var pppoeIp by remember { mutableStateOf(initial?.optString("pppoe_ip") ?: "") }
    var coordinates by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Pelanggan" else "Add Customer Baru",
                fontWeight = FontWeight.Bold
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
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username Hotspot / Login") },
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
                    label = { Text(if (isEdit) "Password Baru (Kosongkan jika tetap)" else "Password (Default: 123456)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phonenumber,
                    onValueChange = { phonenumber = it },
                    label = { Text("Nomor WhatsApp (628xxx)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pilihan Tipe Layanan: Hotspot | PPPoE | Others
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hotspot", "PPPoE", "Others").forEach { t ->
                        FilterChip(
                            selected = serviceType == t,
                            onClick = { serviceType = t },
                            label = { Text(t, fontSize = 12.sp) }
                        )
                    }
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
                        label = { Text("Remote IP PPPoE (Opsional)") },
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
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_customer_save",
                            mapOf(
                                "id" to (initial?.optInt("id") ?: 0),
                                "username" to username,
                                "fullname" to fullname,
                                "password" to password,
                                "phonenumber" to phonenumber,
                                "email" to email,
                                "address" to address,
                                "service_type" to serviceType,
                                "status" to status,
                                "pppoe_username" to pppoeUser,
                                "pppoe_password" to pppoePass,
                                "pppoe_ip" to pppoeIp,
                                "coordinates" to coordinates
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun QuickRechargeOrDepositDialog(
    customer: JSONObject,
    mode: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPlanId by remember { mutableIntStateOf(0) }
    var customAmount by remember { mutableStateOf("") }
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
            if (list.isNotEmpty()) selectedPlanId = list.first().optInt("id")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (mode == "deposit") "Isi Ulang Saldo: ${customer.optString("fullname")}"
                else "Isi Ulang Paket: ${customer.optString("fullname")}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == "deposit") {
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it },
                        label = { Text("Nominal Top-Up Manual (Rp)") },
                        placeholder = { Text("Contoh: 50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Atau pilih paket saldo:", fontSize = 12.sp)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    plans.forEach { p ->
                        val pid = p.optInt("id")
                        val isSel = selectedPlanId == pid && customAmount.isBlank()
                        Surface(
                            color = if (isSel) ArnetaGoldPrimary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSel) 1.5.dp else 0.dp,
                                    color = if (isSel) ArnetaGoldPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedPlanId = pid
                                    customAmount = ""
                                }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "${p.optString("type")} • ${p.optString("routers")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Text(
                                    text = formatRupiah(p.optDouble("price", 0.0)),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaGoldPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing,
                onClick = {
                    isProcessing = true
                    scope.launch {
                        val endpoint = if (mode == "deposit") "admin_deposit" else "admin_prepaid_recharge"
                        val params = mutableMapOf<String, Any?>(
                            "customer_id" to customer.optInt("id")
                        )
                        if (mode == "deposit" && customAmount.isNotBlank()) {
                            params["amount"] = customAmount.toDoubleOrNull() ?: 0.0
                        } else {
                            params["plan_id"] = selectedPlanId
                        }

                        val res = ApiClient.request(endpoint, params)
                        isProcessing = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSuccess()
                    }
                }
            ) {
                Text(if (isProcessing) "Memproses..." else "Proses Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * LAYAR LAYANAN LENGKAP (Mencakup Semua Sub-Menu "Layanan" di Sidebar Screenshot):
 * 1. Pelanggan Aktif
 * 2. Voucher & Recharge Cards (Generate, Filter, Cetak, Hapus)
 * 3. Isi Ulang Voucher (Refill)
 * 4. Kupon Diskon
 * 5. User Data Usage (Live Pemakaian Kuota dari MikroTik)
 */
@Composable
fun AdminServicesScreen(
    initialSubTab: String = "active_users"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val subTabs = listOf(
        "active_users" to "Pelanggan Aktif",
        "vouchers" to "Voucher & Cards",
        "data_usage" to "User Data Usage",
        "coupons" to "Kupon",
        "refill_voucher" to "Isi Ulang Voucher"
    )
    var currentSubTab by remember { mutableStateOf(initialSubTab) }

    // State Data
    var isLoading by remember { mutableStateOf(false) }
    var activeList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var voucherList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var usageList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var usageSummary by remember { mutableStateOf<JSONObject?>(null) }
    var couponList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog Generate Voucher & Buat Kupon
    var showGenVoucherDialog by remember { mutableStateOf(false) }
    var showCouponDialog by remember { mutableStateOf(false) }

    // Form Refill Voucher
    var refillCustomerId by remember { mutableStateOf("") }
    var refillCode by remember { mutableStateOf("") }

    fun loadCurrentTab() {
        isLoading = true
        scope.launch {
            when (currentSubTab) {
                "active_users" -> {
                    val res = ApiClient.request("admin_prepaid_list", mapOf("search" to searchQuery, "limit" to 60))
                    if (res.status && res.data != null) {
                        val arr = res.data.optJSONArray("items") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        activeList = list
                    }
                }
                "vouchers" -> {
                    val res = ApiClient.request("admin_vouchers", mapOf("search" to searchQuery, "limit" to 80))
                    if (res.status && res.data != null) {
                        val arr = res.data.optJSONArray("vouchers") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        voucherList = list
                    }
                }
                "data_usage" -> {
                    val res = ApiClient.request("admin_data_usage")
                    if (res.status && res.data != null) {
                        usageSummary = res.data.optJSONObject("summary")
                        val arr = res.data.optJSONArray("items") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        usageList = list
                    }
                }
                "coupons" -> {
                    val res = ApiClient.request("admin_coupons")
                    if (res.status && res.dataArray != null) {
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until res.dataArray.length()) list.add(res.dataArray.getJSONObject(i))
                        couponList = list
                    }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentSubTab) {
        loadCurrentTab()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Tab Horizontal untuk 5 Sub-Menu Layanan
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            subTabs.forEach { (key, title) ->
                FilterChip(
                    selected = currentSubTab == key,
                    onClick = { currentSubTab = key },
                    label = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (currentSubTab) {
            "active_users" -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari pelanggan aktif / paket...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(onClick = { loadCurrentTab() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Cari")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeList) { item ->
                            val isOn = item.optString("status") == "on"
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.optString("fullname"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(
                                                "@${item.optString("username")} • ${item.optString("namebp")}",
                                                fontSize = 12.sp,
                                                color = ArnetaCyanIncome
                                            )
                                        }
                                        Surface(
                                            color = if (isOn) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isOn) "AKTIF" else "KEDALUWARSA",
                                                color = if (isOn) ArnetaGreenSuccess else ArnetaRedDanger,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Aktif: ${item.optString("recharged_on")} s/d ${item.optString("expiration")} ${item.optString("time")} (${item.optString("routers")})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Cetak Struk Bluetooth
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    BluetoothPrinterHelper.printReceiptText(
                                                        context = context,
                                                        title = "BUKTI LAYANAN AKTIF",
                                                        lines = listOf(
                                                            "Pelanggan" to item.optString("username"),
                                                            "Paket" to item.optString("namebp"),
                                                            "Router" to item.optString("routers"),
                                                            "Expired" to "${item.optString("expiration")} ${item.optString("time")}"
                                                        )
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Cetak", fontSize = 12.sp)
                                        }

                                        // Kirim WA
                                        TextButton(
                                            onClick = {
                                                val msg = BluetoothPrinterHelper.buildInvoiceText(
                                                    invoice = "INV-${item.optInt("id")}",
                                                    username = item.optString("username"),
                                                    planName = item.optString("namebp"),
                                                    price = 0.0,
                                                    expiration = "${item.optString("expiration")} ${item.optString("time")}",
                                                    method = item.optString("method")
                                                )
                                                BluetoothPrinterHelper.shareToWhatsApp(context, msg, item.optString("phonenumber"))
                                            }
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("WA", fontSize = 12.sp)
                                        }

                                        // Hapus Paket
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val dRes = ApiClient.request("admin_prepaid_delete", mapOf("id" to item.optInt("id")))
                                                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                                    if (dRes.status) loadCurrentTab()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "vouchers" -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showGenVoucherDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Filled.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buat Voucher Massal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request("admin_voucher_delete", mapOf("delete_used" to 1))
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                loadCurrentTab()
                            }
                        }
                    ) {
                        Text("Hapus Terpakai", fontSize = 12.sp, color = ArnetaRedDanger)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(voucherList) { v ->
                        val used = v.optString("status") == "1"
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = v.optString("code"),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = if (used) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else ArnetaGoldPrimary
                                    )
                                    Text(
                                        text = "${v.optString("name_plan")} • ${formatRupiah(v.optDouble("price", 0.0))} (${v.optString("routers")})",
                                        fontSize = 12.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                BluetoothPrinterHelper.printReceiptText(
                                                    context = context,
                                                    title = "VOUCHER HOTSPOT",
                                                    lines = listOf(
                                                        "KODE VOUCHER" to v.optString("code"),
                                                        "Paket" to v.optString("name_plan"),
                                                        "Masa Aktif" to v.optString("validity"),
                                                        "Harga" to formatRupiah(v.optDouble("price", 0.0))
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = "Cetak Voucher", tint = ArnetaCyanIncome)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                ApiClient.request("admin_voucher_delete", mapOf("id" to v.optInt("id")))
                                                loadCurrentTab()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "data_usage" -> {
                // User Data Usage Live dari MikroTik
                if (usageSummary != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArnetaIndigoAccent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Upload", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                Text(usageSummary!!.optString("total_upload"), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Download", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                Text(usageSummary!!.optString("total_download"), fontWeight = FontWeight.Bold, color = ArnetaGoldLight)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Traffic", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                Text(usageSummary!!.optString("total_traffic"), fontWeight = FontWeight.ExtraBold, color = ArnetaGreenSuccess)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(usageList) { u ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.optString("username"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${u.optString("service")} • IP: ${u.optString("ip_address")} • Uptime: ${u.optString("uptime")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = u.optString("total"),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "↑ ${u.optString("upload")} | ↓ ${u.optString("download")}",
                                        fontSize = 10.sp,
                                        color = ArnetaCyanIncome
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "coupons" -> {
                Button(
                    onClick = { showCouponDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.LocalOffer, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Kupon Promo", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(couponList) { c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(c.optString("code"), fontWeight = FontWeight.ExtraBold, color = ArnetaGoldPrimary, fontSize = 15.sp)
                                    Text("Diskon: ${c.optString("value")} (${c.optString("type")}) • Exp: ${c.optString("expired_date")}", fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            ApiClient.request("admin_coupon_delete", mapOf("id" to c.optInt("id")))
                                            loadCurrentTab()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }

            "refill_voucher" -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Isi Ulang Voucher ke Pelanggan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedTextField(
                            value = refillCustomerId,
                            onValueChange = { refillCustomerId = it },
                            label = { Text("ID Pelanggan") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = refillCode,
                            onValueChange = { refillCode = it },
                            label = { Text("Kode Voucher") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_voucher_refill",
                                        mapOf("customer_id" to refillCustomerId, "code" to refillCode)
                                    )
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    if (res.status) refillCode = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aktifkan Voucher Sekarang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showGenVoucherDialog) {
        GenerateVoucherDialog(
            onDismiss = { showGenVoucherDialog = false },
            onGenerated = {
                showGenVoucherDialog = false
                loadCurrentTab()
            }
        )
    }

    if (showCouponDialog) {
        AddCouponDialog(
            onDismiss = { showCouponDialog = false },
            onSaved = {
                showCouponDialog = false
                loadCurrentTab()
            }
        )
    }
}

@Composable
private fun GenerateVoucherDialog(
    onDismiss: () -> Unit,
    onGenerated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPlanId by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf("10") }
    var prefix by remember { mutableStateOf("ARN-") }
    var lengthCode by remember { mutableStateOf("6") }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans", mapOf("type" to "Hotspot"))
        if (res.status && res.data != null) {
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            plans = list
            if (list.isNotEmpty()) selectedPlanId = list.first().optInt("id")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Voucher Hotspot", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pilih Paket:", fontSize = 12.sp)
                plans.take(5).forEach { p ->
                    val pid = p.optInt("id")
                    FilterChip(
                        selected = selectedPlanId == pid,
                        onClick = { selectedPlanId = pid },
                        label = { Text("${p.optString("name_plan")} (${formatRupiah(p.optDouble("price", 0.0))})") }
                    )
                }
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it },
                    label = { Text("Jumlah Voucher") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Prefix Kode (Opsional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = lengthCode,
                    onValueChange = { lengthCode = it },
                    label = { Text("Panjang Karakter Acak") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_voucher_generate",
                            mapOf(
                                "plan_id" to selectedPlanId,
                                "count" to (count.toIntOrNull() ?: 10),
                                "prefix" to prefix,
                                "length_code" to (lengthCode.toIntOrNull() ?: 6)
                            )
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onGenerated()
                    }
                }
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun AddCouponDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("5000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Kupon Promo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Kode Kupon (Misal: ARNETA10)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Nominal Potongan (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_coupon_save",
                            mapOf("code" to code, "value" to (value.toDoubleOrNull() ?: 0.0), "type" to "fixed")
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
