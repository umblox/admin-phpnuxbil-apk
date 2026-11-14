package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
 * LAYAR LAYANAN LENGKAP (PELANGGAN AKTIF, ISI ULANG PAKET/SALDO, VOUCHER, REFILL, KUPON & DATA USAGE)
 */
@Composable
fun AdminServicesScreen(
    initialSubTab: String = "active_users"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSuperAdmin = remember { ApiClient.adminRole.equals("SuperAdmin", ignoreCase = true) }

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

    var targetCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var allPlans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerOptions by remember { mutableStateOf(listOf("All" to "Semua Router")) }
    var selectedRouterFilter by remember { mutableStateOf("All") }
    var paymentUsings by remember { mutableStateOf(listOf("Cash", "Balance", "Recharge Zero")) }
    var selectedUsing by remember { mutableStateOf("") }
    var rechargePlanFilter by remember { mutableStateOf("All") } // All | Hotspot | PPPoE
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var customDepositAmount by remember { mutableStateOf("") }
    var depositNote by remember { mutableStateOf("") }
    var refillCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var showGenVoucherDialog by remember { mutableStateOf(false) }
    var showCouponDialog by remember { mutableStateOf(false) }
    var detailActiveCustomerTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deletingActivePlanTarget by remember { mutableStateOf<JSONObject?>(null) }
    var editingActivePlanTarget by remember { mutableStateOf<JSONObject?>(null) }
    var extendingActivePlanTarget by remember { mutableStateOf<JSONObject?>(null) }

    var voucherStatusFilter by remember { mutableStateOf("All") } // All | 0 | 1
    var showCleanUsedVoucherDialog by remember { mutableStateOf(false) }
    var deletingVoucherTarget by remember { mutableStateOf<JSONObject?>(null) }

    var editingCouponTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deletingCouponTarget by remember { mutableStateOf<JSONObject?>(null) }
    var couponStatusFilter by remember { mutableStateOf("All") } // All | active | inactive

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
                    routerOptions = extractRouterOptions(list, res.data?.optJSONArray("routers"))
                    selectedPlan = list.firstOrNull()

                    val uArr = res.data?.optJSONArray("usings")
                    if (uArr != null && uArr.length() > 0) {
                        val uList = mutableListOf<String>()
                        for (i in 0 until uArr.length()) uList.add(uArr.optString(i))
                        paymentUsings = uList
                    }
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
        targetCustomer = null
        selectedUsing = ""
        customDepositAmount = ""
        depositNote = ""
        loadCurrentTab()
    }

    val displayedRechargePlans = remember(allPlans, rechargePlanFilter, selectedRouterFilter, currentSubTab) {
        allPlans.filter { p ->
            if (currentSubTab == "deposit_balance") {
                true
            } else {
                val matchType = rechargePlanFilter == "All" || p.optString("type").equals(rechargePlanFilter, ignoreCase = true)
                val matchRouter = selectedRouterFilter == "All" ||
                    p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true) ||
                    (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1)
                matchType && matchRouter
            }
        }
    }

    LaunchedEffect(displayedRechargePlans) {
        if (displayedRechargePlans.isNotEmpty() && (selectedPlan == null || displayedRechargePlans.none { it.optInt("id") == selectedPlan?.optInt("id") })) {
            selectedPlan = displayedRechargePlans.first()
        }
    }

    val targetCustBalance = targetCustomer?.optDouble("balance", 0.0) ?: 0.0
    val selectedPlanPrice = selectedPlan?.optDouble("price", 0.0) ?: 0.0
    val isTargetBalanceInsufficient = targetCustomer == null || (selectedPlanPrice > 0.0 && targetCustBalance < selectedPlanPrice)

    LaunchedEffect(isTargetBalanceInsufficient, selectedUsing) {
        if (isTargetBalanceInsufficient && selectedUsing.equals("Balance", ignoreCase = true)) {
            selectedUsing = ""
        }
    }

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

    val filteredVoucherList = remember(voucherList, searchQuery, voucherStatusFilter) {
        val q = searchQuery.trim().lowercase()
        voucherList.filter { v ->
            val matchStatus = voucherStatusFilter == "All" || v.optString("status", "0") == voucherStatusFilter
            val matchQuery = q.isEmpty() ||
                v.optString("code", "").lowercase().contains(q) ||
                v.optString("name_plan", "").lowercase().contains(q) ||
                v.optString("routers", "").lowercase().contains(q) ||
                v.optString("user", "").lowercase().contains(q) ||
                v.optString("generated_by_name", "").lowercase().contains(q)
            matchStatus && matchQuery
        }
    }

    val filteredCouponList = remember(couponList, searchQuery, couponStatusFilter) {
        val q = searchQuery.trim().lowercase()
        couponList.filter { c ->
            val st = c.optString("status", "active").lowercase()
            val matchStatus = couponStatusFilter == "All" || st == couponStatusFilter.lowercase()
            val matchQuery = q.isEmpty() ||
                c.optString("code", "").lowercase().contains(q) ||
                c.optString("description", "").lowercase().contains(q) ||
                c.optString("type", "").lowercase().contains(q) ||
                c.optString("value", "").lowercase().contains(q) ||
                st.contains(q)
            matchStatus && matchQuery
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
                    placeholder = { Text("Ketik nama / username / paket...", fontSize = 13.sp) },
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
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredActiveList) { item ->
                            val isOn = item.optString("status") == "on"
                            val itemPrice = item.optDouble("price", 0.0)
                            val invoiceNo = item.optString("invoice").ifEmpty { "INV-${item.optInt("id")}" }
                            val trxDateTime = "${item.optString("recharged_on")} ${item.optString("recharged_time")}".trim()
                            val expDateTime = "${item.optString("expiration")} ${item.optString("time")}".trim()

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { detailActiveCustomerTarget = item }
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
                                                "@${item.optString("username")} • ${item.optString("namebp")} (${formatRupiah(itemPrice)})",
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
                                        text = "Tgl Trx: $trxDateTime • Exp: $expDateTime (${item.optString("routers")})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = { detailActiveCustomerTarget = item },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = null,
                                                    tint = ArnetaCyanIncome,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Detail", fontSize = 11.5.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                            }

                                            if (!isOn) {
                                                TextButton(
                                                    onClick = { extendingActivePlanTarget = item },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Update,
                                                        contentDescription = null,
                                                        tint = ArnetaOrangeActive,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Extend", fontSize = 11.5.sp, color = ArnetaOrangeActive, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        BluetoothPrinterHelper.printReceiptText(
                                                            context = context,
                                                            title = "STRUK LAYANAN ${ApiClient.companyName.uppercase()}",
                                                            lines = listOf(
                                                                "No. Invoice" to invoiceNo,
                                                                "Pelanggan" to "${item.optString("fullname")} (${item.optString("username")})",
                                                                "Paket" to item.optString("namebp"),
                                                                "Harga" to formatRupiah(itemPrice),
                                                                "Metode" to item.optString("method"),
                                                                "Tgl Trx" to trxDateTime,
                                                                "Aktif s/d" to expDateTime,
                                                                "Router" to item.optString("routers")
                                                            )
                                                        )
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Cetak", fontSize = 11.5.sp)
                                            }

                                            TextButton(
                                                onClick = {
                                                    val waMessage = buildString {
                                                        appendLine("*STRUK LAYANAN ${ApiClient.companyName.uppercase()}*")
                                                        appendLine("--------------------------------")
                                                        appendLine("No. Invoice : $invoiceNo")
                                                        appendLine("Pelanggan   : ${item.optString("fullname")} (${item.optString("username")})")
                                                        appendLine("Paket       : ${item.optString("namebp")}")
                                                        appendLine("Harga       : ${formatRupiah(itemPrice)}")
                                                        appendLine("Metode      : ${item.optString("method")}")
                                                        appendLine("Tgl Trx     : $trxDateTime")
                                                        appendLine("Aktif s/d   : $expDateTime")
                                                        appendLine("Router      : ${item.optString("routers")}")
                                                        appendLine("--------------------------------")
                                                        append("Terima kasih telah menggunakan layanan ${ApiClient.companyName}.")
                                                    }
                                                    BluetoothPrinterHelper.shareToWhatsApp(context, waMessage, item.optString("phonenumber"))
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("WA", fontSize = 11.5.sp)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { editingActivePlanTarget = item }
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit Masa Aktif / Paket", tint = ArnetaGoldPrimary, modifier = Modifier.size(19.dp))
                                            }

                                            IconButton(
                                                onClick = { deletingActivePlanTarget = item }
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger, modifier = Modifier.size(19.dp))
                                            }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 78.dp)
                ) {
                    CustomerLiveSearchPicker(
                        selectedCustomer = targetCustomer,
                        onCustomerSelected = { cust ->
                            targetCustomer = cust
                            if (cust != null && !isDeposit) {
                                if (isSuperAdmin) {
                                    rechargePlanFilter = resolveCustomerPlanCategory(cust)
                                }
                                val custRouter = cust.optJSONObject("active_package")?.optString("routers", "") ?: ""
                                if (custRouter.isNotEmpty() && routerOptions.any { it.first.equals(custRouter, ignoreCase = true) }) {
                                    selectedRouterFilter = custRouter
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeposit) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customDepositAmount,
                                onValueChange = { customDepositAmount = it },
                                label = { Text("Nominal Top-Up Manual (${ApiClient.currencyCode})", fontSize = 11.5.sp) },
                                placeholder = { Text("Contoh: 50000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = depositNote,
                                onValueChange = { depositNote = it },
                                label = { Text("Catatan", fontSize = 11.5.sp) },
                                placeholder = { Text("Setoran tunai") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CompactDropdownSelector(
                                label = "Lokasi Server / Router",
                                selectedValue = selectedRouterFilter,
                                options = routerOptions,
                                icon = Icons.Filled.Router,
                                onValueSelected = { selectedRouterFilter = it },
                                modifier = Modifier.weight(1f)
                            )

                            CompactDropdownSelector(
                                label = "Kategori Layanan",
                                selectedValue = rechargePlanFilter,
                                options = listOf(
                                    "All" to "Semua / Others",
                                    "Hotspot" to "Hotspot",
                                    "PPPoE" to "PPPoE"
                                ),
                                icon = Icons.Filled.Wifi,
                                onValueSelected = { rechargePlanFilter = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isDeposit) "Pilih Paket Saldo (${displayedRechargePlans.size}):"
                        else "Pilih Paket Internet (${displayedRechargePlans.size} tersedia):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArnetaGoldPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (isLoading) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(color = ArnetaGoldPrimary)
                            }
                        } else if (displayedRechargePlans.isEmpty()) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Tidak ada paket yang cocok dengan filter router/layanan ini.",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayedRechargePlans) { p ->
                                    val pid = p.optInt("id")
                                    val isSel = selectedPlan?.optInt("id") == pid && customDepositAmount.isBlank()
                                    SelectablePlanCard(
                                        plan = p,
                                        isSelected = isSel,
                                        onClick = {
                                            selectedPlan = p
                                            customDepositAmount = ""
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            if (!isDeposit) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CompactDropdownSelector(
                                        label = "Metode Pembayaran",
                                        selectedValue = selectedUsing,
                                        placeholder = "-- Pilih Metode Pembayaran --",
                                        disabledOptions = if (isTargetBalanceInsufficient) setOf("Balance") else emptySet(),
                                        options = paymentUsings.map { u ->
                                            u to when {
                                                u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (${ApiClient.currencyCode} 0)"
                                                u.equals("Balance", ignoreCase = true) -> {
                                                    if (isTargetBalanceInsufficient) "💰 Potong Saldo (Saldo tidak mencukupi)"
                                                    else "💰 Potong Saldo Pelanggan (${formatRupiah(targetCustBalance)})"
                                                }
                                                else -> u
                                            }
                                        },
                                        icon = Icons.Filled.Payments,
                                        onValueSelected = { selectedUsing = it },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = selectedPlan?.optString("routers", "-") ?: "-",
                                            fontSize = 10.5.sp,
                                            color = ArnetaCyanIncome,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val displayPrice = if (selectedUsing.equals("Recharge Zero", ignoreCase = true)) {
                                            0.0
                                        } else {
                                            selectedPlan?.optDouble("price", 0.0) ?: 0.0
                                        }
                                        Text(
                                            text = formatRupiah(displayPrice),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ArnetaGoldPrimary
                                        )
                                    }
                                }
                            } else {
                                val depositNominal = if (customDepositAmount.isNotBlank()) {
                                    customDepositAmount.toDoubleOrNull() ?: 0.0
                                } else {
                                    selectedPlan?.optDouble("price", 0.0) ?: 0.0
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (customDepositAmount.isNotBlank()) "Top-Up Nominal Manual"
                                        else (selectedPlan?.optString("name_plan") ?: "Pilih Paket Saldo"),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatRupiah(depositNominal),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary
                                    )
                                }
                            }

                            val isReadyToSubmit = if (isDeposit) {
                                !isSubmitting && targetCustomer != null && (customDepositAmount.isNotBlank() || selectedPlan != null)
                            } else {
                                !isSubmitting && targetCustomer != null && selectedPlan != null && selectedUsing.isNotBlank()
                            }

                            Button(
                                enabled = isReadyToSubmit,
                                onClick = {
                                    val cust = targetCustomer ?: return@Button
                                    isSubmitting = true
                                    scope.launch {
                                        val endpoint = if (isDeposit) "admin_deposit" else "admin_prepaid_recharge"
                                        val params = mutableMapOf<String, Any?>(
                                            "customer_id" to cust.optInt("id"),
                                            "using" to selectedUsing,
                                            "note" to depositNote.trim()
                                        )
                                        if (isDeposit && customDepositAmount.isNotBlank()) {
                                            params["amount"] = customDepositAmount.toDoubleOrNull() ?: 0.0
                                        } else if (selectedPlan != null) {
                                            params["plan_id"] = selectedPlan!!.optInt("id")
                                            params["server"] = selectedPlan!!.optString("routers")
                                            params["routers"] = selectedPlan!!.optString("routers")
                                        }
                                        val res = ApiClient.request(endpoint, params)
                                        isSubmitting = false
                                        if (res.status) {
                                            ArnetaTransactionToastManager.showFromApiResponse(
                                                resData = res.data,
                                                fallbackTitle = if (isDeposit) "Isi Ulang Saldo Berhasil!" else "Paket Berhasil Diaktifkan!",
                                                fallbackCustomer = cust,
                                                fallbackPlanName = if (isDeposit && customDepositAmount.isNotBlank()) "Top-Up Saldo Manual" else (selectedPlan?.optString("name_plan") ?: "-"),
                                                fallbackPrice = if (isDeposit && customDepositAmount.isNotBlank()) (customDepositAmount.toDoubleOrNull() ?: 0.0) else (selectedPlan?.optDouble("price", 0.0) ?: 0.0),
                                                fallbackMethod = if (isDeposit) "Deposit" else selectedUsing,
                                                fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                                            )
                                            targetCustomer = null
                                            selectedUsing = ""
                                            customDepositAmount = ""
                                            depositNote = ""
                                            if (!isDeposit) currentSubTab = "active_users"
                                        } else {
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArnetaGoldPrimary,
                                    contentColor = Color(0xFF0B0F19)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDeposit) Icons.Filled.AccountBalanceWallet else Icons.Filled.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSubmitting) "MEMPROSES..."
                                    else if (isDeposit) "TAMBAH SALDO SEKARANG"
                                    else "AKTIFKAN PAKET SEKARANG",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
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
                        onClick = { showCleanUsedVoucherDialog = true }
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = ArnetaRedDanger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bersihkan Terpakai", fontSize = 11.5.sp, color = ArnetaRedDanger)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val unusedCnt = voucherList.count { it.optString("status", "0") == "0" }
                    val usedCnt = voucherList.count { it.optString("status", "0") == "1" }
                    listOf(
                        "All" to "Semua (${voucherList.size})",
                        "0" to "Belum Dipakai ($unusedCnt)",
                        "1" to "Terpakai ($usedCnt)"
                    ).forEach { (stKey, stLbl) ->
                        FilterChip(
                            selected = voucherStatusFilter == stKey,
                            onClick = { voucherStatusFilter = stKey },
                            label = { Text(stLbl, fontSize = 11.5.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari kode voucher, paket, router, atau pembuat...", fontSize = 12.5.sp) },
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

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredVoucherList) { v ->
                        val used = v.optString("status") == "1"
                        val vCode = v.optString("code")
                        val vPlan = v.optString("name_plan")
                        val vType = v.optString("type", "Hotspot")
                        val vPrice = v.optDouble("price", 0.0)
                        val vRouter = v.optString("routers", "-")
                        val vUser = v.optString("user", "0")
                        val vCreator = v.optString("generated_by_name", "Admin")

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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = vCode,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = if (used) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else ArnetaGoldPrimary
                                        )
                                        Surface(
                                            color = if (used) ArnetaRedDanger.copy(alpha = 0.18f) else ArnetaGreenSuccess.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (used) "TERPAKAI" else "AKTIF / READY",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (used) ArnetaRedDanger else ArnetaGreenSuccess,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$vPlan ($vType) • ${formatRupiah(vPrice)} • Router: $vRouter",
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "Dibuat: $vCreator" +
                                            if (used && vUser != "0" && vUser.isNotEmpty()) " • Dipakai oleh: @$vUser" else "",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                BluetoothPrinterHelper.printReceiptText(
                                                    context = context,
                                                    title = "VOUCHER ${ApiClient.companyName.uppercase()}",
                                                    lines = listOf(
                                                        "ID Voucher" to v.optInt("id").toString(),
                                                        "KODE VOUCHER" to vCode,
                                                        "Nama Paket" to vPlan,
                                                        "Layanan" to vType,
                                                        "Masa Aktif" to v.optString("validity", "-"),
                                                        "Harga" to formatRupiah(vPrice),
                                                        "Sales/Admin" to vCreator
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = "Cetak Voucher", tint = ArnetaCyanIncome)
                                    }

                                    IconButton(
                                        onClick = {
                                            val waVoucherContent = buildString {
                                                appendLine("```")
                                                appendLine(ApiClient.companyName)
                                                appendLine("==============================")
                                                appendLine("ID         : ${v.optInt("id")}")
                                                appendLine("Kode       : $vCode")
                                                appendLine("Paket      : $vPlan")
                                                appendLine("Layanan    : $vType")
                                                appendLine("Harga      : ${formatRupiah(vPrice)}")
                                                appendLine("Sales      : $vCreator")
                                                appendLine("==============================")
                                                append("```")
                                            }
                                            BluetoothPrinterHelper.shareToWhatsApp(context, waVoucherContent, "")
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Kirim WA", tint = ArnetaGreenSuccess)
                                    }

                                    IconButton(
                                        onClick = { deletingVoucherTarget = v }
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
                                    if (res.status) {
                                        ArnetaTransactionToastManager.showFromApiResponse(
                                            resData = res.data,
                                            fallbackTitle = "Voucher Berhasil Diaktifkan!",
                                            fallbackCustomer = cust,
                                            fallbackPlanName = "Voucher $refillCode",
                                            fallbackPrice = 0.0,
                                            fallbackMethod = "Voucher - $refillCode"
                                        )
                                        refillCode = ""
                                        targetCustomer = null
                                    } else {
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
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
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            editingCouponTarget = null
                            showCouponDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Kupon Promo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    IconButton(onClick = { loadCurrentTab() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val activeCnt = couponList.count { it.optString("status", "active").equals("active", true) }
                    val inactiveCnt = couponList.count { !it.optString("status", "active").equals("active", true) }
                    listOf(
                        "All" to "Semua (${couponList.size})",
                        "active" to "Aktif ($activeCnt)",
                        "inactive" to "Nonaktif ($inactiveCnt)"
                    ).forEach { (stKey, stLbl) ->
                        FilterChip(
                            selected = couponStatusFilter == stKey,
                            onClick = { couponStatusFilter = stKey },
                            label = { Text(stLbl, fontSize = 11.5.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari kode kupon, deskripsi, atau nilai diskon...", fontSize = 12.5.sp) },
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

                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (filteredCouponList.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Belum ada data kupon promo yang cocok.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCouponList) { c ->
                            val cId = c.optInt("id")
                            val cCode = c.optString("code")
                            val cType = c.optString("type", "fixed") // fixed | percent
                            val cVal = c.optDouble("value", 0.0)
                            val cDesc = c.optString("description", "-")
                            val maxUsage = c.optInt("max_usage", 0)
                            val usageCount = c.optInt("usage_count", 0)
                            val minOrder = c.optDouble("min_order_amount", 0.0)
                            val maxDisc = c.optDouble("max_discount_amount", 0.0)
                            val isActive = c.optString("status", "active").equals("active", ignoreCase = true)
                            val startDate = c.optString("start_date", "-")
                            val endDate = c.optString("end_date", c.optString("expired_date", "-"))

                            val discountLabel = if (cType.equals("percent", ignoreCase = true)) {
                                "${cVal.toInt()}% (Maks: ${formatRupiah(maxDisc)})"
                            } else {
                                formatRupiah(cVal)
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        editingCouponTarget = c
                                        showCouponDialog = true
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = cCode,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isActive) ArnetaGoldPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                fontSize = 16.sp
                                            )
                                            Surface(
                                                color = if (isActive) ArnetaGreenSuccess.copy(alpha = 0.18f) else ArnetaRedDanger.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isActive) "ACTIVE" else "INACTIVE",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isActive) ArnetaGreenSuccess else ArnetaRedDanger,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                color = ArnetaCyanIncome.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (cType.equals("percent", true)) "Persen (%)" else "Nominal (${ApiClient.currencyCode})",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ArnetaCyanIncome,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = discountLabel,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ArnetaGoldPrimary,
                                            fontSize = 13.5.sp
                                        )
                                    }

                                    if (cDesc.isNotEmpty() && cDesc != "-") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = cDesc,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Min. Order: ${formatRupiah(minOrder)} • Pemakaian: $usageCount / ${if (maxUsage <= 0) "∞" else maxUsage}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                    )
                                    Text(
                                        text = "Periode Aktif: $startDate s/d $endDate",
                                        fontSize = 11.sp,
                                        color = ArnetaCyanIncome
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val nextStatus = if (isActive) "inactive" else "active"
                                                scope.launch {
                                                    val sRes = ApiClient.request(
                                                        "admin_coupon_status",
                                                        mapOf("id" to cId, "status" to nextStatus)
                                                    )
                                                    Toast.makeText(context, sRes.message, Toast.LENGTH_SHORT).show()
                                                    if (sRes.status) loadCurrentTab()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isActive) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                                                contentDescription = null,
                                                tint = if (isActive) ArnetaGreenSuccess else ArnetaOrangeActive,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isActive) "Nonaktifkan" else "Aktifkan",
                                                fontSize = 11.5.sp,
                                                color = if (isActive) ArnetaOrangeActive else ArnetaGreenSuccess,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editingCouponTarget = c
                                                    showCouponDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit Kupon", tint = ArnetaGoldPrimary)
                                            }
                                            IconButton(
                                                onClick = { deletingCouponTarget = c }
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Kupon", tint = ArnetaRedDanger)
                                            }
                                        }
                                    }
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
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(usageList) { u ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { detailActiveCustomerTarget = u }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(u.optString("username"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Speed,
                                            contentDescription = "Lihat Live Speed",
                                            tint = ArnetaGoldPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
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
            initial = editingCouponTarget,
            onDismiss = {
                showCouponDialog = false
                editingCouponTarget = null
            },
            onSaved = {
                showCouponDialog = false
                editingCouponTarget = null
                loadCurrentTab()
            }
        )
    }

    if (deletingCouponTarget != null) {
        val targetC = deletingCouponTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Kupon Promo?",
            subtitle = "${targetC.optString("code")} • ${targetC.optString("description")}",
            description = "Kupon promo ini akan dihapus secara permanen dari database.",
            onDismiss = { deletingCouponTarget = null },
            onConfirm = {
                deletingCouponTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_coupon_delete", mapOf("id" to targetC.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadCurrentTab()
                }
            }
        )
    }

    if (detailActiveCustomerTarget != null) {
        UserDetailAndLiveSpeedDialog(
            initialUserObj = detailActiveCustomerTarget!!,
            onDismiss = { detailActiveCustomerTarget = null },
            onUserKicked = { loadCurrentTab() }
        )
    }

    if (deletingActivePlanTarget != null) {
        val target = deletingActivePlanTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Paket Pelanggan Aktif",
            subtitle = "${target.optString("fullname")} (@${target.optString("username")}) • ${target.optString("namebp")}",
            description = "Paket aktif ini akan dihapus dari daftar langganan serta dicabut dari Router MikroTik. Lanjutkan?",
            onDismiss = { deletingActivePlanTarget = null },
            onConfirm = {
                deletingActivePlanTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_prepaid_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadCurrentTab()
                }
            }
        )
    }

    if (editingActivePlanTarget != null) {
        EditActiveCustomerPlanDialog(
            activeItem = editingActivePlanTarget!!,
            onDismiss = { editingActivePlanTarget = null },
            onSaved = {
                editingActivePlanTarget = null
                loadCurrentTab()
            }
        )
    }

    if (extendingActivePlanTarget != null) {
        ExtendCustomerPlanDialog(
            activeItem = extendingActivePlanTarget!!,
            onDismiss = { extendingActivePlanTarget = null },
            onExtended = {
                extendingActivePlanTarget = null
                loadCurrentTab()
            }
        )
    }

    if (deletingVoucherTarget != null) {
        val targetV = deletingVoucherTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Kode Voucher?",
            subtitle = "${targetV.optString("code")} • ${targetV.optString("name_plan")}",
            description = "Kode voucher ini akan dihapus permanen dari database.",
            onDismiss = { deletingVoucherTarget = null },
            onConfirm = {
                deletingVoucherTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_voucher_delete", mapOf("id" to targetV.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadCurrentTab()
                }
            }
        )
    }

    if (showCleanUsedVoucherDialog) {
        AlertDialog(
            onDismissRequest = { showCleanUsedVoucherDialog = false },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Bersihkan Voucher Terpakai", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Pilih metode pembersihan voucher yang sudah berstatus Terpakai (Status = 1):",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showCleanUsedVoucherDialog = false
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_voucher_delete",
                                    mapOf("delete_used" to 1, "older_than_3months" to 1)
                                )
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                loadCurrentTab()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hapus Terpakai > 3 Bulan (Standar Web)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            showCleanUsedVoucherDialog = false
                            scope.launch {
                                val res = ApiClient.request("admin_voucher_delete", mapOf("delete_used" to 1))
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                loadCurrentTab()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaRedDanger, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hapus Seluruh Voucher Terpakai", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = { showCleanUsedVoucherDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Batal", color = Color.White)
                    }
                }
            }
        )
    }
    ArnetaInteractiveTransactionToast()
}
