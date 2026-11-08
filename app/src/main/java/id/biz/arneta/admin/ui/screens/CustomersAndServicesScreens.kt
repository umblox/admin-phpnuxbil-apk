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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * KOMPONEN REUSABLE: DROPDOWN PEMILIH KOMPAK (DENGAN PLACEHOLDER & OPSI DISABLED SALDO KURANG)
 */
@Composable
fun CompactDropdownSelector(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    icon: ImageVector,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih...",
    disabledOptions: Set<String> = emptySet()
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = remember(selectedValue, options, placeholder) {
        if (selectedValue.isBlank()) {
            placeholder
        } else {
            options.firstOrNull { it.first.equals(selectedValue, ignoreCase = true) }?.second ?: selectedValue
        }
    }
    val isPlaceholder = selectedValue.isBlank()

    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isPlaceholder) ArnetaGoldPrimary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ArnetaGoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = selectedLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaceholder) ArnetaGoldLight else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = ArnetaGoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, title) ->
                val isSel = key.equals(selectedValue, ignoreCase = true)
                val isEnabled = disabledOptions.none { it.equals(key, ignoreCase = true) }

                DropdownMenuItem(
                    enabled = isEnabled,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSel,
                                onClick = null,
                                enabled = isEnabled,
                                colors = RadioButtonDefaults.colors(selectedColor = ArnetaGoldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    },
                    onClick = {
                        if (isEnabled) {
                            onValueSelected(key)
                            expanded = false
                        }
                    }
                )
            }
        }
    }
}

/**
 * KOMPONEN REUSABLE: PENCARIAN & PEMILIH PELANGGAN OTOMATIS
 * - Saat klik "Ganti", pilihan pelanggan langsung di-reset (null) agar tombol eksekusi nonaktif
 *   sampai Admin benar-benar memilih pelanggan baru.
 */
@Composable
fun CustomerLiveSearchPicker(
    selectedCustomer: JSONObject?,
    onCustomerSelected: (JSONObject?) -> Unit,
    label: String = "Cari & Pilih Pelanggan"
) {
    var allCustomers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(selectedCustomer == null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCustomer) {
        if (selectedCustomer == null) {
            isExpanded = true
        }
    }

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
                    c.optString("address", "").lowercase().contains(q) ||
                    c.optString("city", "").lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (selectedCustomer != null && !isExpanded) {
            val activePkg = selectedCustomer.optJSONObject("active_package")
            val activeRouter = activePkg?.optString("routers", "") ?: ""

            Surface(
                color = ArnetaGoldPrimary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ArnetaGoldPrimary, RoundedCornerShape(12.dp))
                    .clickable {
                        onCustomerSelected(null)
                        searchText = ""
                        isExpanded = true
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ArnetaGoldPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${selectedCustomer.optString("fullname")} (@${selectedCustomer.optString("username")})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedCustomer.optString("service_type", "Hotspot")}" +
                                    (if (activeRouter.isNotEmpty()) " • Router: $activeRouter" else "") +
                                    " • Saldo: ${formatRupiah(selectedCustomer.optDouble("balance", 0.0))}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            onCustomerSelected(null)
                            searchText = ""
                            isExpanded = true
                        }
                    ) {
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
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp, max = 185.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary, modifier = Modifier.size(24.dp))
                    }
                } else if (filteredCustomers.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(14.dp)) {
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
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

private fun resolveCustomerPlanCategory(customer: JSONObject?): String {
    if (customer == null) return "All"
    return when (customer.optString("service_type", "").trim().uppercase()) {
        "HOTSPOT" -> "Hotspot"
        "PPPOE" -> "PPPoE"
        else -> "All"
    }
}

private fun extractRouterOptions(plans: List<JSONObject>, routersArray: JSONArray?): List<Pair<String, String>> {
    val set = linkedSetOf<String>()
    if (routersArray != null) {
        for (i in 0 until routersArray.length()) {
            val rName = routersArray.optJSONObject(i)?.optString("name")?.trim() ?: ""
            if (rName.isNotEmpty()) set.add(rName)
        }
    }
    plans.forEach { p ->
        val rName = p.optString("routers", "").trim()
        if (rName.isNotEmpty() && !rName.equals("balance", ignoreCase = true)) {
            set.add(rName)
        }
    }
    val result = mutableListOf("All" to "Semua Router (${set.size})")
    set.forEach { r ->
        val label = if (r.equals("radius", ignoreCase = true)) "Radius Server" else r
        result.add(r to label)
    }
    return result
}

/**
 * KARTU PILIHAN PAKET DENGAN RADIO BUTTON + BADGE LOKASI ROUTER, PREPAID/POSTPAID, HARGA CORET & HIDDEN
 */
@Composable
private fun SelectablePlanCard(
    plan: JSONObject,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isPostpaid = plan.optString("prepaid", "yes").equals("no", ignoreCase = true)
    val isHidden = plan.optBoolean("is_hidden", false) || plan.optInt("enabled", 1) == 0
    val isRadius = plan.optInt("is_radius", 0) == 1
    val pType = plan.optString("type", "Hotspot")
    val planCategory = plan.optString("plan_type", "Personal")
    val routerName = if (isRadius) "Radius Server" else plan.optString("routers", "-")
    val validityStr = "${plan.optInt("validity", 30)} ${plan.optString("validity_unit", "Days")}"
    val priceVal = plan.optDouble("price", 0.0)
    val priceOldVal = plan.optString("price_old", "").toDoubleOrNull() ?: 0.0

    Surface(
        color = if (isSelected) ArnetaGoldPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) ArnetaGoldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ArnetaGoldPrimary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = plan.optString("name_plan"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!pType.equals("Balance", ignoreCase = true)) {
                            Surface(
                                color = if (isPostpaid) ArnetaOrangeActive.copy(alpha = 0.2f) else ArnetaCyanIncome.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isPostpaid) "Postpaid" else "Prepaid",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPostpaid) ArnetaOrangeActive else ArnetaCyanIncome,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }

                            if (planCategory.equals("Business", ignoreCase = true)) {
                                Surface(
                                    color = ArnetaIndigoAccent.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Business",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaGoldLight,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        if (isHidden) {
                            Surface(
                                color = ArnetaRedDanger.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "HIDDEN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaRedDanger,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (pType.equals("Balance", ignoreCase = true)) {
                            "Paket Deposit Saldo"
                        } else {
                            "📡 $routerName • $pType • ${plan.optString("rate_label", "-")} ($validityStr)"
                        },
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (priceOldVal > priceVal) {
                    Text(
                        text = formatRupiah(priceOldVal),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
                Text(
                    text = formatRupiah(priceVal),
                    fontWeight = FontWeight.ExtraBold,
                    color = ArnetaGoldPrimary,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

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

/**
 * DIALOG TAMBAH / EDIT PELANGGAN
 */
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

/**
 * LAYAR LAYANAN LENGKAP
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
                                label = { Text("Nominal Top-Up Manual (Rp)", fontSize = 11.5.sp) },
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
                                                u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (Rp 0)"
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
                                            // Format persis case 'voucher-view' di system/controllers/plan.php
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
                                                    text = if (cType.equals("percent", true)) "Persen (%)" else "Nominal (Rp)",
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

@Composable
private fun ConfirmDeleteArnetaDialog(
    title: String,
    subtitle: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ArnetaRedDanger.copy(alpha = 0.2f))
                        .border(1.dp, ArnetaRedDanger, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = ArnetaRedDanger,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = ArnetaGoldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaRedDanger,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ya, Hapus", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Batal", fontSize = 12.5.sp)
            }
        }
    )
}

@Composable
private fun GenerateVoucherDialog(
    onDismiss: () -> Unit,
    onGenerated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("Hotspot") } // Hotspot | PPPOE (Sesuai add-voucher di plan.php)
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
private fun EditActiveCustomerPlanDialog(
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
private fun ExtendCustomerPlanDialog(
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

@Composable
private fun AddCouponDialog(
    initial: JSONObject? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val defaultStart = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
    val defaultEnd = remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Edit else Icons.Filled.LocalOffer,
                    contentDescription = null,
                    tint = ArnetaGoldPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEdit) "Edit Kupon: $code" else "Tambah Kupon Promo",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = status.equals("active", true),
                        onClick = { status = "active" },
                        label = { Text("Status: Active", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = status.equals("inactive", true),
                        onClick = { status = "inactive" },
                        label = { Text("Status: Inactive", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().replace("[^A-Z0-9]".toRegex(), "") },
                    label = { Text("Kode Kupon (Alfanumerik, Misal: ARNETA10)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipe Potongan Kupon:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "fixed",
                        onClick = { type = "fixed" },
                        label = { Text("Fixed (Nominal Rp)", fontSize = 11.5.sp) }
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
                        label = { Text(if (type == "percent") "Nilai Diskon (%)" else "Nominal Diskon (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxDiscountAmount,
                        onValueChange = { maxDiscountAmount = it },
                        label = { Text("Maks. Diskon (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minOrderAmount,
                        onValueChange = { minOrderAmount = it },
                        label = { Text("Min. Pembelian (Rp)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxUsage,
                        onValueChange = { maxUsage = it },
                        label = { Text("Maks. Pakai (0=Bebas)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi Kupon (Wajib)") },
                    placeholder = { Text("Contoh: Diskon pelanggan baru bulan ini") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Mulai (YYYY-MM-DD)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Berakhir (YYYY-MM-DD)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            val canSave = !isSaving && code.isNotBlank() && (value.toDoubleOrNull() ?: 0.0) > 0.0 &&
                startDate.isNotBlank() && endDate.isNotBlank()

            Button(
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                ),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val valNum = value.toDoubleOrNull() ?: 0.0
                        val maxDiscNum = maxDiscountAmount.toIntOrNull()?.takeIf { it > 0 } ?: valNum.toInt()
                        val minOrdNum = minOrderAmount.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0

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
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Simpan Kupon", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

object ArnetaTransactionToastManager {
    var currentReceipt by mutableStateOf<JSONObject?>(null)

    fun showFromApiResponse(
        resData: JSONObject?,
        fallbackTitle: String,
        fallbackCustomer: JSONObject? = null,
        fallbackPlanName: String = "-",
        fallbackPrice: Double = 0.0,
        fallbackMethod: String = "-",
        fallbackRouter: String = "-"
    ) {
        val receiptObj = resData?.optJSONObject("receipt")
        if (receiptObj != null) {
            receiptObj.put("toast_title", fallbackTitle)
            currentReceipt = receiptObj
            return
        }
        val invObj = resData?.optJSONObject("invoice")
        val constructed = JSONObject().apply {
            put("toast_title", fallbackTitle)
            put("trx_id", invObj?.optInt("id") ?: 0)
            put("invoice", invObj?.optString("invoice")?.ifEmpty { null } ?: "INV-${System.currentTimeMillis() % 10000}")
            put("username", invObj?.optString("username")?.ifEmpty { null } ?: fallbackCustomer?.optString("username", "-"))
            put("fullname", fallbackCustomer?.optString("fullname")?.ifEmpty { null } ?: invObj?.optString("username", "-"))
            put("phonenumber", fallbackCustomer?.optString("phonenumber", "") ?: "")
            put("plan_name", invObj?.optString("plan_name")?.ifEmpty { null } ?: fallbackPlanName)
            put("price", invObj?.optDouble("price") ?: fallbackPrice)
            put("method", invObj?.optString("method")?.ifEmpty { null } ?: fallbackMethod)
            put("routers", invObj?.optString("routers")?.ifEmpty { null } ?: fallbackRouter)
            put(
                "recharged_at",
                if (invObj != null) "${invObj.optString("recharged_on")} ${invObj.optString("recharged_time")}".trim()
                else "Baru saja"
            )
            put(
                "expiration",
                if (invObj != null) "${invObj.optString("expiration")} ${invObj.optString("time")}".trim()
                else "-"
            )
            put("balance_after", resData?.optDouble("balance") ?: (fallbackCustomer?.optDouble("balance", 0.0) ?: 0.0))
        }
        currentReceipt = constructed
    }

    fun dismiss() {
        currentReceipt = null
    }
}

@Composable
fun ArnetaInteractiveTransactionToast() {
    val receipt = ArnetaTransactionToastManager.currentReceipt ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResendingWa by remember { mutableStateOf(false) }

    val title = receipt.optString("toast_title", "Transaksi Berhasil!")
    val trxId = receipt.optInt("trx_id", 0)
    val invoiceNo = receipt.optString("invoice", "-")
    val fullname = receipt.optString("fullname", "-")
    val username = receipt.optString("username", "-")
    val phone = receipt.optString("phonenumber", "")
    val planName = receipt.optString("plan_name", "-")
    val typeStr = receipt.optString("type", "Layanan")
    val priceVal = receipt.optDouble("price", 0.0)
    val methodStr = receipt.optString("method", "-")
    val routerStr = receipt.optString("routers", "-")
    val rechargedAt = receipt.optString("recharged_at", "-")
    val expiration = receipt.optString("expiration", "-")
    val isBalanceTrx = typeStr.equals("Balance", ignoreCase = true) || routerStr.equals("balance", ignoreCase = true)
    val balanceAfter = receipt.optDouble("balance_after", 0.0)

    AlertDialog(
        onDismissRequest = { ArnetaTransactionToastManager.dismiss() },
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ArnetaGreenSuccess.copy(alpha = 0.2f))
                            .border(1.5.dp, ArnetaGreenSuccess, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ArnetaGreenSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = ArnetaGreenSuccess
                        )
                        Text(
                            text = "No. Invoice: $invoiceNo",
                            fontSize = 11.5.sp,
                            color = ArnetaGoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = { ArnetaTransactionToastManager.dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        ToastDetailRow("Pelanggan", "$fullname (@$username)")
                        ToastDetailRow("Paket / Layanan", planName)
                        ToastDetailRow("Nominal Harga", formatRupiah(priceVal), valueColor = ArnetaGoldPrimary)
                        ToastDetailRow("Metode Bayar", methodStr)
                        if (rechargedAt.isNotEmpty() && rechargedAt != "-") {
                            ToastDetailRow("Waktu Trx", rechargedAt)
                        }
                        if (isBalanceTrx) {
                            ToastDetailRow("Saldo Saat Ini", formatRupiah(balanceAfter), valueColor = ArnetaCyanIncome)
                        } else {
                            if (expiration.isNotEmpty() && expiration != "-") {
                                ToastDetailRow("Aktif s/d", expiration, valueColor = ArnetaGreenSuccess)
                            }
                            if (routerStr.isNotEmpty() && routerStr != "-") {
                                ToastDetailRow("Router / Server", routerStr)
                            }
                        }
                    }
                }
                Text(
                    text = "Pilih aksi cetak struk, kirim ulang WA (Template Web), atau tekan Selesai.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val lines = mutableListOf(
                                    "No. Invoice" to invoiceNo,
                                    "Pelanggan" to "$fullname ($username)",
                                    "Paket" to planName,
                                    "Harga" to formatRupiah(priceVal),
                                    "Metode" to methodStr,
                                    "Tgl Trx" to rechargedAt
                                )
                                if (isBalanceTrx) {
                                    lines.add("Saldo Baru" to formatRupiah(balanceAfter))
                                } else {
                                    lines.add("Aktif s/d" to expiration)
                                    lines.add("Router" to routerStr)
                                }
                                BluetoothPrinterHelper.printReceiptText(
                                    context = context,
                                    title = "STRUK LAYANAN ${ApiClient.companyName.uppercase()}",
                                    lines = lines
                                )
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ArnetaCyanIncome.copy(alpha = 0.18f),
                            contentColor = ArnetaCyanIncome
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Struk", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }

                    FilledTonalButton(
                        enabled = !isResendingWa,
                        onClick = {
                            isResendingWa = true
                            scope.launch {
                                val wRes = ApiClient.request(
                                    "admin_resend_invoice_wa",
                                    mapOf(
                                        "trx_id" to trxId,
                                        "invoice" to invoiceNo,
                                        "username" to username,
                                        "phonenumber" to phone
                                    )
                                )
                                isResendingWa = false
                                val webWaText = wRes.data?.optString("wa_web_text")?.ifEmpty { null }
                                    ?: receipt.optString("wa_web_text", "")
                                val targetPhone = wRes.data?.optString("phonenumber")?.ifEmpty { null } ?: phone
                                val sentByServer = wRes.data?.optBoolean("sent_via_gateway", false) ?: false

                                if (sentByServer) {
                                    Toast.makeText(context, wRes.message, Toast.LENGTH_SHORT).show()
                                } else if (webWaText.isNotEmpty()) {
                                    BluetoothPrinterHelper.shareToWhatsApp(context, webWaText, targetPhone)
                                } else {
                                    val fallbackWa = BluetoothPrinterHelper.buildInvoiceText(
                                        invoice = invoiceNo,
                                        username = "$fullname ($username)",
                                        planName = planName,
                                        price = priceVal,
                                        expiration = expiration,
                                        method = methodStr
                                    )
                                    BluetoothPrinterHelper.shareToWhatsApp(context, fallbackWa, targetPhone)
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ArnetaGreenSuccess.copy(alpha = 0.2f),
                            contentColor = ArnetaGreenSuccess
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isResendingWa) "Mengirim..." else "Kirim Ulang WA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = { ArnetaTransactionToastManager.dismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArnetaGoldPrimary,
                        contentColor = Color(0xFF0B0F19)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesai", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
private fun ToastDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}

