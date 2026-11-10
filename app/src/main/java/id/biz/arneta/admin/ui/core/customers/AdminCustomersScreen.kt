package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR MANAJEMEN PELANGGAN
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

    var showFormDialog by remember { mutableStateOf(openAddDialogInitially) }
    var editingCustomer by remember { mutableStateOf<JSONObject?>(null) }

    var showRechargeDialog by remember { mutableStateOf(false) }
    var quickActionCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var quickActionMode by remember { mutableStateOf("recharge") } // recharge | deposit

    var detailCustomerTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deletingCustomerTarget by remember { mutableStateOf<JSONObject?>(null) }

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

    LaunchedEffect(searchQuery) {
        delay(220)
        loadCustomers(searchQuery)
    }

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
                    text = "Ketik Nama Pelanggan",
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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Ketik nama, username, WA, kota, atau alamat...", fontSize = 13.sp) },
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
                contentPadding = PaddingValues(bottom = 95.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedCustomers) { cust ->
                    val pkg = cust.optJSONObject("active_package")
                    val pkgStatus = pkg?.optString("status", "off") ?: "none"
                    val isOnlineOrActive = pkgStatus == "on"
                    val cityStr = cust.optString("city", "")

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val enriched = JSONObject(cust.toString()).apply {
                                    put("customer_id", cust.optInt("id"))
                                    if (pkg != null) {
                                        put("routers", pkg.optString("routers"))
                                        put("namebp", pkg.optString("namebp"))
                                    }
                                }
                                detailCustomerTarget = enriched
                            }
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
                                            text = "@${cust.optString("username")} • ${cust.optString("service_type")}" +
                                                if (cityStr.isNotEmpty()) " • $cityStr" else "",
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
                                verticalAlignment = Alignment.CenterVertically,
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
                                        val enriched = JSONObject(cust.toString()).apply {
                                            put("customer_id", cust.optInt("id"))
                                            if (pkg != null) {
                                                put("routers", pkg.optString("routers"))
                                                put("namebp", pkg.optString("namebp"))
                                            }
                                        }
                                        detailCustomerTarget = enriched
                                    }
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = "Detail & Live Speed", tint = ArnetaCyanIncome)
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val sRes = ApiClient.request("admin_customer_sync", mapOf("id" to cust.optInt("id")))
                                            Toast.makeText(context, sRes.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = "Sync Router", tint = ArnetaGreenSuccess)
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
                                    onClick = { deletingCustomerTarget = cust }
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

    if (detailCustomerTarget != null) {
        UserDetailAndLiveSpeedDialog(
            initialUserObj = detailCustomerTarget!!,
            onDismiss = { detailCustomerTarget = null },
            onUserKicked = { loadCustomers(searchQuery) }
        )
    }

    if (deletingCustomerTarget != null) {
        val target = deletingCustomerTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Akun Pelanggan",
            subtitle = "${target.optString("fullname")} (@${target.optString("username")})",
            description = "Menghapus pelanggan ini akan menghapus data profil dan paket aktifnya dari sistem. Lanjutkan?",
            onDismiss = { deletingCustomerTarget = null },
            onConfirm = {
                deletingCustomerTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_customer_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadCustomers(searchQuery)
                }
            }
        )
    }
    ArnetaInteractiveTransactionToast()
}
