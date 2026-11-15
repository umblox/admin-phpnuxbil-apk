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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.network.optSafeDouble
import id.biz.arneta.admin.ui.theme.*
import id.biz.arneta.admin.util.BluetoothPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun parseCustomerArray(arr: JSONArray?): List<JSONObject> {
    if (arr == null) return emptyList()
    val list = ArrayList<JSONObject>(arr.length())
    for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.let { list.add(it) }
    }
    return list
}

@Composable
fun AdminCustomersScreen(
    initialSearch: String = "",
    initialType: String = "All",
    openAddDialogInitially: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultParams = remember { mapOf("search" to "", "service_type" to "", "limit" to 300) }
    val initCached = remember {
        ApiClient.getCached("admin_customers", defaultParams)
            ?: ApiClient.getCached("admin_customers", mapOf("limit" to 300))
    }

    var allCustomers by remember {
        mutableStateOf(
            if (initCached?.status == true) {
                parseCustomerArray(initCached.data?.optJSONArray("customers") ?: initCached.dataArray)
            } else emptyList()
        )
    }

    var searchQuery by remember { mutableStateOf(initialSearch) }
    var selectedServiceType by remember { mutableStateOf(initialType) }
    var isLoading by remember { mutableStateOf(allCustomers.isEmpty()) }
    var isSyncingAll by remember { mutableStateOf(false) }

    var showFormDialog by remember { mutableStateOf(openAddDialogInitially) }
    var editingCustomer by remember { mutableStateOf<JSONObject?>(null) }

    var quickActionCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var quickActionMode by remember { mutableStateOf("recharge") } // recharge | deposit

    var detailCustomerTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deletingCustomerTarget by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(initialSearch) {
        if (initialSearch.isNotEmpty()) searchQuery = initialSearch
    }

    LaunchedEffect(initialType) {
        selectedServiceType = initialType
    }

    LaunchedEffect(openAddDialogInitially) {
        if (openAddDialogInitially) {
            editingCustomer = null
            showFormDialog = true
        }
    }

    fun loadCustomers(forceRefresh: Boolean = false) {
        if (allCustomers.isEmpty()) isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_customers", defaultParams, forceRefresh = forceRefresh)
            if (res.status) {
                allCustomers = withContext(Dispatchers.Default) {
                    parseCustomerArray(res.data?.optJSONArray("customers") ?: res.dataArray)
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCustomers(forceRefresh = false)
    }

    val serviceTypeTabs = remember(allCustomers) {
        var hsCnt = 0
        var pppCnt = 0
        var otherCnt = 0
        for (c in allCustomers) {
            when (c.optString("service_type", "Hotspot").uppercase()) {
                "HOTSPOT" -> hsCnt++
                "PPPOE" -> pppCnt++
                else -> otherCnt++
            }
        }
        listOf(
            "All" to "Semua (${allCustomers.size})",
            "Hotspot" to "Hotspot ($hsCnt)",
            "PPPoE" to "PPPoE ($pppCnt)",
            "Others" to "Others ($otherCnt)"
        )
    }

    val filteredCustomers = remember(allCustomers, searchQuery, selectedServiceType) {
        val q = searchQuery.trim().lowercase()
        allCustomers.filter { c ->
            val sType = c.optString("service_type", "Hotspot")
            val matchType = selectedServiceType == "All" || sType.equals(selectedServiceType, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                c.optString("fullname", "").lowercase().contains(q) ||
                c.optString("username", "").lowercase().contains(q) ||
                c.optString("phonenumber", "").lowercase().contains(q) ||
                c.optString("pppoe_username", "").lowercase().contains(q) ||
                c.optString("address", "").lowercase().contains(q) ||
                c.optString("city", "").lowercase().contains(q)
            matchType && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manajemen Pelanggan", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    text = "${filteredCustomers.size} dari ${allCustomers.size} pelanggan terdaftar",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { loadCustomers(forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                }

                FilledTonalButton(
                    enabled = !isSyncingAll,
                    onClick = {
                        isSyncingAll = true
                        scope.launch {
                            val sRes = ApiClient.request("admin_customer_sync", mapOf("sync_all" to 1))
                            isSyncingAll = false
                            Toast.makeText(context, sRes.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSyncingAll) "Sync..." else "Sync All", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        editingCustomer = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+Pelanggan", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ArnetaSubTabBar(
            tabs = serviceTypeTabs,
            selectedKey = selectedServiceType,
            onSelect = { selectedServiceType = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama, username, No. WA, PPPoE, atau alamat...", fontSize = 12.5.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && allCustomers.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else if (filteredCustomers.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Tidak ada pelanggan yang cocok dengan pencarian.", fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 95.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredCustomers,
                    key = { it.optInt("id", it.optString("username").hashCode()) }
                ) { c ->
                    val cid = c.optInt("id")
                    val uname = c.optString("username")
                    val fname = c.optString("fullname").ifEmpty { uname }
                    val phone = c.optString("phonenumber", "")
                    val sType = c.optString("service_type", "Hotspot")
                    val accStatus = c.optString("status", "Active")
                    val balance = c.optSafeDouble("balance", 0.0)
                    val activePkg = c.optJSONObject("active_package")
                    val isPkgActive = activePkg?.optString("status") == "on"

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { detailCustomerTarget = c }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPkgActive) ArnetaGreenSuccess.copy(alpha = 0.18f)
                                                else ArnetaGoldPrimary.copy(alpha = 0.18f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isPkgActive) ArnetaGreenSuccess else ArnetaGoldPrimary,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Text(
                                            text = fname.take(1).uppercase(),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = if (isPkgActive) ArnetaGreenSuccess else ArnetaGoldPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = fname,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = if (sType.equals("PPPoE", true)) ArnetaIndigoAccent.copy(alpha = 0.25f)
                                                else ArnetaCyanIncome.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(5.dp)
                                            ) {
                                                Text(
                                                    text = sType,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sType.equals("PPPoE", true)) ArnetaGoldLight else ArnetaCyanIncome,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (!accStatus.equals("Active", true)) {
                                                Surface(
                                                    color = ArnetaRedDanger.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(5.dp)
                                                ) {
                                                    Text(
                                                        text = accStatus.uppercase(),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = ArnetaRedDanger,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "@$uname" + if (phone.isNotEmpty()) " • $phone" else "",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatRupiah(balance),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                    if (activePkg != null) {
                                        Text(
                                            text = if (isPkgActive) "🟢 ${activePkg.optString("namebp")}"
                                            else "🔴 Exp: ${activePkg.optString("namebp")}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPkgActive) ArnetaGreenSuccess else ArnetaRedDanger
                                        )
                                    } else {
                                        Text(
                                            text = "Belum ada paket",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            quickActionCustomer = c
                                            quickActionMode = "recharge"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Isi Paket", fontSize = 11.5.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                                    }

                                    TextButton(
                                        onClick = {
                                            quickActionCustomer = c
                                            quickActionMode = "deposit"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+Saldo", fontSize = 11.5.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                    }

                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val sRes = ApiClient.request("admin_customer_sync", mapOf("id" to cid))
                                                Toast.makeText(context, sRes.message, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.Sync, contentDescription = null, tint = ArnetaGreenSuccess, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Sync", fontSize = 11.5.sp, color = ArnetaGreenSuccess)
                                    }

                                    if (phone.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                BluetoothPrinterHelper.shareToWhatsApp(
                                                    context = context,
                                                    message = "Halo *$fname* (@$uname), informasi layanan ${ApiClient.companyName}:",
                                                    phone = phone
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("WA", fontSize = 11.5.sp)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingCustomer = c
                                            showFormDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Pelanggan", tint = ArnetaGoldPrimary, modifier = Modifier.size(19.dp))
                                    }
                                    IconButton(
                                        onClick = { deletingCustomerTarget = c }
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Pelanggan", tint = ArnetaRedDanger, modifier = Modifier.size(19.dp))
                                    }
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
            onDismiss = {
                showFormDialog = false
                editingCustomer = null
            },
            onSaved = {
                showFormDialog = false
                editingCustomer = null
                loadCustomers(forceRefresh = true)
            }
        )
    }

    if (quickActionCustomer != null) {
        QuickRechargeOrDepositDialog(
            initialCustomer = quickActionCustomer,
            mode = quickActionMode,
            onDismiss = { quickActionCustomer = null },
            onSuccess = {
                quickActionCustomer = null
                loadCustomers(forceRefresh = true)
            }
        )
    }

    if (detailCustomerTarget != null) {
        UserDetailAndLiveSpeedDialog(
            initialUserObj = detailCustomerTarget!!,
            onDismiss = { detailCustomerTarget = null },
            onUserKicked = { loadCustomers(forceRefresh = true) }
        )
    }

    if (deletingCustomerTarget != null) {
        val target = deletingCustomerTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Akun Pelanggan?",
            subtitle = "${target.optString("fullname")} (@${target.optString("username")})",
            description = "Seluruh data profil dan riwayat paket aktif pelanggan ini akan dihapus dari sistem.",
            onDismiss = { deletingCustomerTarget = null },
            onConfirm = {
                deletingCustomerTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_customer_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadCustomers(forceRefresh = true)
                }
            }
        )
    }

    ArnetaInteractiveTransactionToast()
}
