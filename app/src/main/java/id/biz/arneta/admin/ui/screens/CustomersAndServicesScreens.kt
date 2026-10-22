package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * KOMPONEN REUSABLE: PENCARIAN & PEMILIH PELANGGAN OTOMATIS (LIVE SEARCH 1 HURUF + SCROLL)
 * Dipakai di: Isi Ulang Paket, Isi Ulang Saldo, Isi Ulang Voucher, dan Kirim Pesan Single
 */
@Composable
fun CustomerLiveSearchPicker(
    selectedCustomer: JSONObject?,
    onCustomerSelected: (JSONObject) -> Unit,
    label: String = "Cari & Pilih Pelanggan (Ketik 1 huruf / Scroll)"
) {
    var allCustomers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(selectedCustomer == null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val res = ApiClient.request("admin_customers", mapOf("limit" to 300))
        val arr = res.data?.optJSONArray("customers") ?: res.dataArray ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            list.add(arr.getJSONObject(i))
        }
        allCustomers = list
        isLoading = false
    }

    // Filter instan sejak ketik 1 huruf
    val filteredCustomers = remember(allCustomers, searchText) {
        val q = searchText.trim().lowercase()
        if (q.isEmpty()) {
            allCustomers
        } else {
            allCustomers.filter { c ->
                c.optString("fullname", "").lowercase().contains(q) ||
                    c.optString("username", "").lowercase().contains(q) ||
                    c.optString("phonenumber", "").lowercase().contains(q) ||
                    c.optString("pppoe_username", "").lowercase().contains(q) ||
                    c.optString("address", "").lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Jika sudah ada pelanggan yang dipilih, tampilkan kartu ringkasnya + tombol ganti
        if (selectedCustomer != null && !isExpanded) {
            Surface(
                color = ArnetaGoldPrimary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ArnetaGoldPrimary, RoundedCornerShape(12.dp))
                    .clickable { isExpanded = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ArnetaGoldPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${selectedCustomer.optString("fullname")} (@${selectedCustomer.optString("username")})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${selectedCustomer.optString("service_type")} • Saldo: ${formatRupiah(selectedCustomer.optDouble("balance", 0.0))} • WA: ${selectedCustomer.optString("phonenumber", "-")}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    TextButton(onClick = { isExpanded = true }) {
                        Text("Ganti", color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    isExpanded = true
                },
                label = { Text(label, fontSize = 12.sp) },
                placeholder = { Text("Ketik nama / username / WA...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                        }
                    } else if (selectedCustomer != null) {
                        IconButton(onClick = { isExpanded = false }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Tutup")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Daftar Scrollable Pelanggan yang langsung bereaksi saat diketik 1 huruf
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 210.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary, modifier = Modifier.size(24.dp))
                    }
                } else if (filteredCustomers.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Pelanggan \"$searchText\" tidak ditemukan.", fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCustomers) { cust ->
                            val isSel = selectedCustomer?.optInt("id") == cust.optInt("id")
                            Surface(
                                color = if (isSel) ArnetaGoldPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCustomerSelected(cust)
                                        isExpanded = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cust.optString("fullname"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${cust.optString("username")} • ${cust.optString("service_type")} • ${cust.optString("phonenumber", "-")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(cust.optDouble("balance", 0.0)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaGoldPrimary
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
 * LAYAR MANAJEMEN PELANGGAN + LIVE SEARCH 1 HURUF + ADD CUSTOMER + ISI PAKET / SALDO
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
    var allCustomers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    // Dialog Tambah / Edit Pelanggan
    var showFormDialog by remember { mutableStateOf(openAddDialogInitially) }
    var editingCustomer by remember { mutableStateOf<JSONObject?>(null) }

    // Dialog Cepat Isi Ulang Paket / Saldo
    var showRechargeDialog by remember { mutableStateOf(false) }
    var quickActionCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var quickActionMode by remember { mutableStateOf("recharge") } // recharge | deposit

    fun loadCustomers(query: String = "") {
        scope.launch {
            if (allCustomers.isEmpty()) isLoading = true
            val res = ApiClient.request(
                "admin_customers",
                mapOf(
                    "search" to query,
                    "service_type" to if (selectedType == "All") "" else selectedType,
                    "limit" to 300
                )
            )
            if (res.status && res.data != null) {
                val arr = res.data.optJSONArray("customers") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                allCustomers = list
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedType) {
        loadCustomers(searchQuery)
    }

    // Trigger pencarian ke server secara halus jika user mengetik
    LaunchedEffect(searchQuery) {
        delay(220)
        loadCustomers(searchQuery)
    }

    // Filter Instan 0ms sejak huruf ke-1 diketik
    val displayedCustomers = remember(allCustomers, searchQuery, selectedType) {
        val q = searchQuery.trim().lowercase()
        allCustomers.filter { c ->
            val matchType = selectedType == "All" || c.optString("service_type", "").equals(selectedType, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                c.optString("fullname", "").lowercase().contains(q) ||
                c.optString("username", "").lowercase().contains(q) ||
                c.optString("phonenumber", "").lowercase().contains(q) ||
                c.optString("pppoe_username", "").lowercase().contains(q) ||
                c.optString("pppoe_ip", "").lowercase().contains(q) ||
                c.optString("address", "").lowercase().contains(q)
            matchType && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header + Tombol Add Customer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Pelanggan (${displayedCustomers.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ketik 1 huruf untuk mencari otomatis",
                    fontSize = 11.5.sp,
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

        Spacer(modifier = Modifier.height(8.dp))

        // Pintasan Cepat Isi Ulang Paket & Isi Ulang Saldo
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = {
                    quickActionCustomer = null
                    quickActionMode = "recharge"
                    showRechargeDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Isi Ulang Paket", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = {
                    quickActionCustomer = null
                    quickActionMode = "deposit"
                    showRechargeDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Isi Ulang Saldo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Kolom Pencarian Otomatis (Langsung memfilter sejak 1 huruf)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Ketik nama, username, WA, atau alamat...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

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
        } else if (displayedCustomers.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Tidak ada pelanggan yang cocok dengan \"$searchQuery\"", fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedCustomers) { cust ->
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

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AssistChip(
                                    onClick = {
                                        quickActionCustomer = cust
                                        quickActionMode = "recharge"
                                        showRechargeDialog = true
                                    },
                                    label = { Text("Isi Paket", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                )

                                AssistChip(
                                    onClick = {
                                        quickActionCustomer = cust
                                        quickActionMode = "deposit"
                                        showRechargeDialog = true
                                    },
                                    label = { Text("+Saldo", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                )

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

                                IconButton(
                                    onClick = {
                                        editingCustomer = cust
                                        showFormDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val dRes = ApiClient.request("admin_customer_delete", mapOf("id" to cust.optInt("id")))
                                            Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                            if (dRes.status) loadCustomers(searchQuery)
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

    if (showFormDialog) {
        CustomerFormDialog(
            initial = editingCustomer,
            onDismiss = { showFormDialog = false },
            onSaved = {
                showFormDialog = false
                loadCustomers(searchQuery)
            }
        )
    }

    if (showRechargeDialog) {
        QuickRechargeOrDepositDialog(
            initialCustomer = quickActionCustomer,
            mode = quickActionMode,
            onDismiss = { showRechargeDialog = false },
            onSuccess = {
                showRechargeDialog = false
                loadCustomers(searchQuery)
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

/**
 * DIALOG ISI ULANG PAKET & ISI ULANG SALDO (DENGAN LIVE SEARCH PELANGGAN 1 HURUF)
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

    var selectedCustomer by remember { mutableStateOf(initialCustomer) }
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var filterPlanType by remember { mutableStateOf("All") } // All | Hotspot | PPPoE
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
            if (list.isNotEmpty()) selectedPlan = list.first()
        }
    }

    val displayedPlans = remember(plans, filterPlanType, mode) {
        if (mode == "deposit" || filterPlanType == "All") {
            plans
        } else {
            plans.filter { it.optString("type").equals(filterPlanType, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (mode == "deposit") "💰 Isi Ulang Saldo Pelanggan" else "⚡ Isi Ulang Paket Internet",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Pemilih Pelanggan Otomatis (Ketik 1 Huruf / Scroll)
                CustomerLiveSearchPicker(
                    selectedCustomer = selectedCustomer,
                    onCustomerSelected = { cust -> selectedCustomer = cust }
                )

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
                    if (displayedPlans.isNotEmpty()) {
                        Text("Atau pilih Paket Saldo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All" to "Semua Paket", "Hotspot" to "Hotspot", "PPPoE" to "PPPoE").forEach { (k, lbl) ->
                            FilterChip(
                                selected = filterPlanType == k,
                                onClick = { filterPlanType = k },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 2. Daftar Paket yang Tersedia
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayedPlans.forEach { p ->
                        val pid = p.optInt("id")
                        val isSel = selectedPlan?.optInt("id") == pid && customAmount.isBlank()
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
                                    selectedPlan = p
                                    customAmount = ""
                                }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "${p.optString("type")} (${p.optString("billing_label", p.optString("prepaid"))}) • Router: ${p.optString("routers")}",
                                        fontSize = 10.5.sp,
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
                enabled = !isProcessing && selectedCustomer != null,
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    isProcessing = true
                    scope.launch {
                        val endpoint = if (mode == "deposit") "admin_deposit" else "admin_prepaid_recharge"
                        val params = mutableMapOf<String, Any?>(
                            "customer_id" to cust.optInt("id")
                        )
                        if (mode == "deposit" && customAmount.isNotBlank()) {
                            params["amount"] = customAmount.toDoubleOrNull() ?: 0.0
                        } else if (selectedPlan != null) {
                            params["plan_id"] = selectedPlan!!.optInt("id")
                            params["routers"] = selectedPlan!!.optString("routers")
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
 * LAYAR LAYANAN LENGKAP (Mencakup Seluruh Sub-Menu "Layanan" di Sidebar Web):
 * 1. Pelanggan Aktif (Live Search 1 Huruf)
 * 2. Isi Ulang Paket (Live Search Pelanggan + Pilih Paket Hotspot/PPPoE)
 * 3. Isi Ulang Saldo (Live Search Pelanggan + Pilih Paket Saldo / Nominal)
 * 4. Voucher & Recharge Cards (Live Search 1 Huruf + Generate Massal)
 * 5. Isi Ulang Voucher (Live Search Pelanggan + Input Kode Voucher)
 * 6. Kupon Promo
 * 7. User Data Usage (Live Trafik MikroTik)
 */
@Composable
fun AdminServicesScreen(
    initialSubTab: String = "active_users"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val subTabs = listOf(
        "active_users" to "Pelanggan Aktif",
        "recharge_plan" to "Isi Ulang Paket",
        "deposit_balance" to "Isi Ulang Saldo",
        "vouchers" to "Voucher & Cards",
        "refill_voucher" to "Isi Ulang Voucher",
        "coupons" to "Kupon",
        "data_usage" to "User Data Usage"
    )
    var currentSubTab by remember { mutableStateOf(initialSubTab) }

    var isLoading by remember { mutableStateOf(false) }
    var activeList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var voucherList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var usageList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var usageSummary by remember { mutableStateOf<JSONObject?>(null) }
    var couponList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // State untuk Tab Isi Ulang Paket, Saldo & Refill Voucher
    var targetCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var allPlans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var customDepositAmount by remember { mutableStateOf("") }
    var refillCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var showGenVoucherDialog by remember { mutableStateOf(false) }
    var showCouponDialog by remember { mutableStateOf(false) }

    fun loadCurrentTab() {
        isLoading = true
        scope.launch {
            when (currentSubTab) {
                "active_users" -> {
                    val res = ApiClient.request("admin_prepaid_list", mapOf("limit" to 250))
                    val arr = res.data?.optJSONArray("items") ?: res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    activeList = list
                }
                "recharge_plan", "deposit_balance" -> {
                    val res = ApiClient.request("admin_plans")
                    val arr = res.data?.optJSONArray("plans") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val pType = p.optString("type").uppercase()
                        if (currentSubTab == "deposit_balance" && pType == "BALANCE") {
                            list.add(p)
                        } else if (currentSubTab == "recharge_plan" && pType != "BALANCE") {
                            list.add(p)
                        }
                    }
                    allPlans = list
                    selectedPlan = list.firstOrNull()
                }
                "vouchers" -> {
                    val res = ApiClient.request("admin_vouchers", mapOf("limit" to 200))
                    val arr = res.data?.optJSONArray("vouchers") ?: res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    voucherList = list
                }
                "data_usage" -> {
                    val res = ApiClient.request("admin_data_usage")
                    if (res.status && res.data != null) {
                        usageSummary = res.data.optJSONObject("summary")
                        val arr = res.data.optJSONArray("items") ?: res.data.optJSONArray("users") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        usageList = list
                    }
                }
                "coupons" -> {
                    val res = ApiClient.request("admin_coupons")
                    val arr = res.dataArray ?: res.data?.optJSONArray("coupons") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    couponList = list
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentSubTab) {
        loadCurrentTab()
    }

    // Filter Instan 1 Huruf untuk Pelanggan Aktif
    val filteredActiveList = remember(activeList, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) activeList
        else activeList.filter { item ->
            item.optString("fullname", "").lowercase().contains(q) ||
                item.optString("username", "").lowercase().contains(q) ||
                item.optString("namebp", "").lowercase().contains(q) ||
                item.optString("routers", "").lowercase().contains(q)
        }
    }

    // Filter Instan 1 Huruf untuk Voucher
    val filteredVoucherList = remember(voucherList, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) voucherList
        else voucherList.filter { v ->
            v.optString("code", "").lowercase().contains(q) ||
                v.optString("name_plan", "").lowercase().contains(q) ||
                v.optString("routers", "").lowercase().contains(q) ||
                v.optString("user", "").lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ketik 1 huruf nama / username / paket...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (filteredActiveList.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Belum ada data pelanggan aktif yang cocok.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredActiveList) { item ->
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

            "recharge_plan", "deposit_balance" -> {
                val isDeposit = currentSubTab == "deposit_balance"
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 110.dp)
                ) {
                    Text(
                        text = if (isDeposit) "Isi Ulang Saldo Pelanggan" else "Isi Ulang Paket Internet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    CustomerLiveSearchPicker(
                        selectedCustomer = targetCustomer,
                        onCustomerSelected = { targetCustomer = it }
                    )

                    if (isDeposit) {
                        OutlinedTextField(
                            value = customDepositAmount,
                            onValueChange = { customDepositAmount = it },
                            label = { Text("Nominal Top-Up Manual (Rp)") },
                            placeholder = { Text("Contoh: 50000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (allPlans.isNotEmpty()) {
                        Text(
                            text = if (isDeposit) "Atau Pilih Paket Saldo:" else "Pilih Paket Internet (Hotspot / PPPoE):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        allPlans.forEach { p ->
                            val pid = p.optInt("id")
                            val isSel = selectedPlan?.optInt("id") == pid && customDepositAmount.isBlank()
                            Surface(
                                color = if (isSel) ArnetaGoldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) ArnetaGoldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedPlan = p
                                        customDepositAmount = ""
                                    }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            "${p.optString("type")} • ${p.optString("billing_label", "")} • Router: ${p.optString("routers")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(p.optDouble("price", 0.0)),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        enabled = !isSubmitting && targetCustomer != null,
                        onClick = {
                            val cust = targetCustomer ?: return@Button
                            isSubmitting = true
                            scope.launch {
                                val endpoint = if (isDeposit) "admin_deposit" else "admin_prepaid_recharge"
                                val params = mutableMapOf<String, Any?>("customer_id" to cust.optInt("id"))
                                if (isDeposit && customDepositAmount.isNotBlank()) {
                                    params["amount"] = customDepositAmount.toDoubleOrNull() ?: 0.0
                                } else if (selectedPlan != null) {
                                    params["plan_id"] = selectedPlan!!.optInt("id")
                                    params["routers"] = selectedPlan!!.optString("routers")
                                }
                                val res = ApiClient.request(endpoint, params)
                                isSubmitting = false
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                if (res.status) {
                                    customDepositAmount = ""
                                    if (!isDeposit) currentSubTab = "active_users"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isSubmitting) "MEMPROSES..." else if (isDeposit) "TAMBAH SALDO SEKARANG" else "AKTIFKAN PAKET SEKARANG",
                            fontWeight = FontWeight.ExtraBold
                        )
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari kode voucher / nama paket...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredVoucherList) { v ->
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
                                Column(modifier = Modifier.weight(1f)) {
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

                        CustomerLiveSearchPicker(
                            selectedCustomer = targetCustomer,
                            onCustomerSelected = { targetCustomer = it }
                        )

                        OutlinedTextField(
                            value = refillCode,
                            onValueChange = { refillCode = it },
                            label = { Text("Masukkan Kode Voucher") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = targetCustomer != null && refillCode.isNotBlank(),
                            onClick = {
                                val cust = targetCustomer ?: return@Button
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_voucher_refill",
                                        mapOf("customer_id" to cust.optInt("id"), "code" to refillCode)
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
                                    Text("Diskon: ${formatRupiah(c.optDouble("value", 0.0))} • Exp: ${c.optString("expired_date")}", fontSize = 12.sp)
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

            "data_usage" -> {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Pilih Paket Hotspot:", fontSize = 12.sp)
                plans.forEach { p ->
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
