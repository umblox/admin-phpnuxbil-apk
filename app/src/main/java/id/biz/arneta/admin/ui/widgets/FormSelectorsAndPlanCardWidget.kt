package id.biz.arneta.admin.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
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

internal fun resolveCustomerPlanCategory(customer: JSONObject?): String {
    if (customer == null) return "All"
    return when (customer.optString("service_type", "").trim().uppercase()) {
        "HOTSPOT" -> "Hotspot"
        "PPPOE" -> "PPPoE"
        else -> "All"
    }
}

internal fun extractRouterOptions(plans: List<JSONObject>, routersArray: JSONArray?): List<Pair<String, String>> {
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
internal fun SelectablePlanCard(
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
