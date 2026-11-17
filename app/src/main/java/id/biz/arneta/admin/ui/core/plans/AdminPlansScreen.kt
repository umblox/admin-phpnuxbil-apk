package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.network.optSafeDouble
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class PlansDataBundle(
    val plans: List<JSONObject>,
    val bandwidths: List<JSONObject>,
    val rateUnits: List<String>,
    val burstPresets: List<JSONObject>,
    val routers: List<JSONObject>,
    val pools: List<JSONObject>,
    val devices: List<String>
)

private fun parsePlansJsonBundle(data: JSONObject?): PlansDataBundle {
    val defaultDevs = listOf("MikrotikHotspot", "MikrotikPppoe", "MikrotikVpn", "Radius", "Dummy")
    if (data == null) {
        return PlansDataBundle(
            plans = emptyList(),
            bandwidths = emptyList(),
            rateUnits = listOf("Kbps", "Mbps"),
            burstPresets = emptyList(),
            routers = emptyList(),
            pools = emptyList(),
            devices = defaultDevs
        )
    }

    val pArr = data.optJSONArray("plans") ?: JSONArray()
    val pList = ArrayList<JSONObject>(pArr.length())
    for (i in 0 until pArr.length()) {
        pArr.optJSONObject(i)?.let { pList.add(it) }
    }

    val bArr = data.optJSONArray("bandwidths") ?: JSONArray()
    val bList = ArrayList<JSONObject>(bArr.length())
    for (i in 0 until bArr.length()) {
        bArr.optJSONObject(i)?.let { bList.add(it) }
    }

    val uArr = data.optJSONArray("rate_units")
    val uList = mutableListOf<String>()
    if (uArr != null) {
        for (i in 0 until uArr.length()) {
            val u = uArr.optString(i).trim()
            if (u.isNotEmpty()) uList.add(u)
        }
    }

    val bpArr = data.optJSONArray("burst_presets") ?: JSONArray()
    val bpList = ArrayList<JSONObject>(bpArr.length())
    for (i in 0 until bpArr.length()) {
        bpArr.optJSONObject(i)?.let { bpList.add(it) }
    }

    val rArr = data.optJSONArray("routers") ?: JSONArray()
    val rList = ArrayList<JSONObject>(rArr.length())
    for (i in 0 until rArr.length()) {
        rArr.optJSONObject(i)?.let { rList.add(it) }
    }

    val poolArr = data.optJSONArray("pools") ?: JSONArray()
    val poolList = ArrayList<JSONObject>(poolArr.length())
    for (i in 0 until poolArr.length()) {
        poolArr.optJSONObject(i)?.let { poolList.add(it) }
    }

    val devArr = data.optJSONArray("devices")
    val dList = mutableListOf<String>()
    if (devArr != null) {
        for (i in 0 until devArr.length()) {
            val dv = devArr.optString(i).trim()
            if (dv.isNotEmpty()) dList.add(dv)
        }
    }

    return PlansDataBundle(
        plans = pList,
        bandwidths = bList,
        rateUnits = if (uList.isNotEmpty()) uList else listOf("Kbps", "Mbps"),
        burstPresets = bpList,
        routers = rList,
        pools = poolList,
        devices = if (dList.isNotEmpty()) dList else defaultDevs
    )
}

/**
 * LAYAR PAKET INTERNET (HOTSPOT, PPPOE, SALDO, VPN) & BANDWIDTH
 * 100% Mengikuti Struktur & Logika system/controllers/services.php & bandwidth.php
 */
@Composable
fun AdminPlansScreen(
    initialTab: String = "Hotspot"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) } // Hotspot | PPPOE | Balance | VPN | Bandwidth
    var searchQuery by remember { mutableStateOf("") }
    var selectedRouterFilter by remember { mutableStateOf("All") }

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    val initBundle = remember {
        val cached = ApiClient.getCached("admin_plans")
        if (cached?.status == true && cached.data != null) {
            parsePlansJsonBundle(cached.data)
        } else null
    }

    var plansList by remember { mutableStateOf(initBundle?.plans ?: emptyList()) }
    var bwList by remember { mutableStateOf(initBundle?.bandwidths ?: emptyList()) }
    var rateUnitsList by remember { mutableStateOf(initBundle?.rateUnits ?: listOf("Kbps", "Mbps")) }
    var burstPresetsList by remember { mutableStateOf(initBundle?.burstPresets ?: emptyList()) }
    var routersList by remember { mutableStateOf(initBundle?.routers ?: emptyList()) }
    var poolsList by remember { mutableStateOf(initBundle?.pools ?: emptyList()) }
    var devicesList by remember {
        mutableStateOf(
            initBundle?.devices ?: listOf("MikrotikHotspot", "MikrotikPppoe", "MikrotikVpn", "Radius", "Dummy")
        )
    }

    var isLoading by remember { mutableStateOf(plansList.isEmpty() && bwList.isEmpty()) }
    var isSyncing by remember { mutableStateOf(false) }

    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<JSONObject?>(null) }

    var showBwDialog by remember { mutableStateOf(false) }
    var editingBw by remember { mutableStateOf<JSONObject?>(null) }

    var deletingPlanTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deletingBwTarget by remember { mutableStateOf<JSONObject?>(null) }

    fun loadPlansData(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = ApiClient.getCached("admin_plans")
            if (cached?.status == true && cached.data != null && plansList.isEmpty() && bwList.isEmpty()) {
                val cBundle = withContext(Dispatchers.Default) {
                    parsePlansJsonBundle(cached.data)
                }
                plansList = cBundle.plans
                bwList = cBundle.bandwidths
                rateUnitsList = cBundle.rateUnits
                burstPresetsList = cBundle.burstPresets
                routersList = cBundle.routers
                poolsList = cBundle.pools
                devicesList = cBundle.devices
            } else if (plansList.isEmpty() && bwList.isEmpty()) {
                isLoading = true
            }

            val res = ApiClient.request("admin_plans", emptyMap(), forceRefresh = forceRefresh)
            if (res.status && res.data != null) {
                val parsed = withContext(Dispatchers.Default) {
                    parsePlansJsonBundle(res.data)
                }
                plansList = parsed.plans
                bwList = parsed.bandwidths
                rateUnitsList = parsed.rateUnits
                burstPresetsList = parsed.burstPresets
                routersList = parsed.routers
                poolsList = parsed.pools
                devicesList = parsed.devices
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPlansData(forceRefresh = false)
    }

    val tabItems = remember(plansList, bwList) {
        val hsCount = plansList.count { it.optString("type").equals("Hotspot", true) }
        val pppCount = plansList.count { it.optString("type").equals("PPPOE", true) }
        val balCount = plansList.count { it.optString("type").equals("Balance", true) }
        val vpnCount = plansList.count { it.optString("type").equals("VPN", true) }

        listOf(
            "Hotspot" to "Hotspot ($hsCount)",
            "PPPOE" to "PPPoE ($pppCount)",
            "Balance" to "Paket Saldo ($balCount)",
            "VPN" to "VPN ($vpnCount)",
            "Bandwidth" to "Bandwidth (${bwList.size})"
        )
    }

    val routerFilterOptions = remember(routersList, plansList) {
        val set = linkedSetOf<String>()
        routersList.forEach { r ->
            val n = r.optString("name").trim()
            if (n.isNotEmpty()) set.add(n)
        }
        plansList.forEach { p ->
            if (p.optInt("is_radius", 0) == 1) {
                set.add("Radius")
            } else {
                val n = p.optString("routers").trim()
                if (n.isNotEmpty() && !n.equals("balance", ignoreCase = true)) set.add(n)
            }
        }
        val list = mutableListOf("All" to "Semua Router")
        set.forEach { r -> list.add(r to r) }
        list
    }

    val filteredBandwidths = remember(bwList, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) bwList
        else bwList.filter { bw ->
            bw.optString("name_bw", "").lowercase().contains(q) ||
                bw.optString("burst", "").lowercase().contains(q)
        }
    }

    val filteredPlans = remember(plansList, currentTab, searchQuery, selectedRouterFilter) {
        val q = searchQuery.trim().lowercase()
        plansList.filter { p ->
            val matchTab = p.optString("type", "").equals(currentTab, ignoreCase = true)
            val matchRouter = selectedRouterFilter == "All" ||
                (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1) ||
                p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                p.optString("name_plan", "").lowercase().contains(q) ||
                p.optString("name_bw", "").lowercase().contains(q) ||
                p.optString("routers", "").lowercase().contains(q) ||
                p.optString("pool", "").lowercase().contains(q)
            matchTab && (currentTab.equals("Balance", ignoreCase = true) || matchRouter) && matchQuery
        }
    }

    // SEMENTARA
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
    Spacer(Modifier.height(10.dp))
    ArnetaSubTabBar(
        tabs = listOf(
            "Hotspot" to "Hotspot", "PPPOE" to "PPPoE", "Balance" to "Paket Saldo",
            "VPN" to "VPN", "Bandwidth" to "Bandwidth"
        ),
        selectedKey = currentTab,
        onSelect = { currentTab = it }
    )
    Spacer(Modifier.height(8.dp))
    Text("STUB tab=$currentTab")
}
return
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
                Text("Paket Layanan & Bandwidth", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "Sinkron otomatis dengan Router MikroTik & Database Web",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { loadPlansData(forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
                }

                if (currentTab.equals("Hotspot", true) || currentTab.equals("PPPOE", true) || currentTab.equals("VPN", true)) {
                    FilledTonalButton(
                        enabled = !isSyncing,
                        onClick = {
                            isSyncing = true
                            scope.launch {
                                val sRes = ApiClient.request(
                                    "admin_plan_sync",
                                    mapOf("type" to currentTab.lowercase())
                                )
                                isSyncing = false
                                Toast.makeText(context, sRes.message, Toast.LENGTH_LONG).show()
                                loadPlansData(forceRefresh = true)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sync ke Router",
                            tint = ArnetaCyanIncome,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSyncing) "Sync..." else "Sync", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (currentTab.equals("Bandwidth", ignoreCase = true)) {
                            editingBw = null
                            showBwDialog = true
                        } else {
                            editingPlan = null
                            showPlanDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            currentTab.equals("Bandwidth", ignoreCase = true) -> "+Bandwidth"
                            currentTab.equals("Balance", ignoreCase = true) -> "+Paket Saldo"
                            currentTab.equals("PPPOE", ignoreCase = true) -> "+Paket PPPoE"
                            else -> "+Paket $currentTab"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ArnetaSubTabBar(
            tabs = tabItems,
            selectedKey = currentTab,
            onSelect = { currentTab = it }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (currentTab.equals("Bandwidth", ignoreCase = true)) "Cari nama bandwidth..." else "Cari nama paket / router...",
                        fontSize = 12.5.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = ArnetaGoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            if (!currentTab.equals("Bandwidth", ignoreCase = true) &&
                !currentTab.equals("Balance", ignoreCase = true) &&
                routerFilterOptions.size > 2
            ) {
                CompactDropdownSelector(
                    label = "Filter Router",
                    selectedValue = selectedRouterFilter,
                    options = routerFilterOptions,
                    icon = Icons.Filled.Router,
                    onValueSelected = { selectedRouterFilter = it },
                    modifier = Modifier.width(138.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && plansList.isEmpty() && bwList.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else if (currentTab.equals("Bandwidth", ignoreCase = true)) {
            BandwidthTabContent(
                bandwidths = filteredBandwidths,
                onEdit = { bw ->
                    editingBw = bw
                    showBwDialog = true
                },
                onDelete = { bw ->
                    deletingBwTarget = bw
                }
            )
        } else {
            if (filteredPlans.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Belum ada paket $currentTab yang cocok.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPlans) { p ->
                        val pType = p.optString("type", "Hotspot")
                        val isBalance = pType.equals("Balance", ignoreCase = true)
                        val isPostpaid = p.optString("prepaid", "yes").equals("no", ignoreCase = true)
                        val isHidden = p.optBoolean("is_hidden", false) || p.optInt("enabled", 1) == 0
                        val isRadius = p.optInt("is_radius", 0) == 1
                        val planTypeBadge = p.optString("plan_type", "Personal")
                        val priceOldStr = p.optString("price_old", "").trim()
                        val priceOldVal = priceOldStr.toDoubleOrNull() ?: 0.0
                        val priceVal = p.optSafeDouble("price", 0.0)
                        val typebp = p.optString("typebp", "Unlimited")
                        val poolStr = p.optString("pool", "")
                        val deviceStr = p.optString("device", "")

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    editingPlan = p
                                    showPlanDialog = true
                                }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(p.optString("name_plan"), fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                        if (!isBalance) {
                                            Surface(
                                                color = if (isPostpaid) ArnetaOrangeActive.copy(alpha = 0.2f) else ArnetaCyanIncome.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isPostpaid) "Postpaid" else "Prepaid",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPostpaid) ArnetaOrangeActive else ArnetaCyanIncome,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Surface(
                                                color = ArnetaIndigoAccent.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = planTypeBadge,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ArnetaGoldLight,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (isHidden) {
                                            Surface(
                                                color = ArnetaRedDanger.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Nonaktif / Hidden",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = ArnetaRedDanger,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (isBalance) {
                                        Text(
                                            text = "Paket Deposit Saldo Pelanggan",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        val limitDetail = if (pType.equals("Hotspot", true) && typebp.equals("Limited", true)) {
                                            val lType = p.optString("limit_type", "Time_Limit")
                                            val tLim = "${p.optInt("time_limit")} ${p.optString("time_unit", "Hrs")}"
                                            val dLim = "${p.optInt("data_limit")} ${p.optString("data_unit", "MB")}"
                                            when (lType) {
                                                "Time_Limit" -> " • Limit Waktu: $tLim"
                                                "Data_Limit" -> " • Limit Kuota: $dLim"
                                                else -> " • Limit: $tLim & $dLim"
                                            }
                                        } else if (pType.equals("Hotspot", true)) {
                                            " • Unlimited (${p.optInt("shared_users", 1)} Shared)"
                                        } else {
                                            if (poolStr.isNotEmpty()) " • Pool: $poolStr" else ""
                                        }

                                        Text(
                                            text = "${p.optString("name_bw")} (${p.optString("rate_label")}) • ${p.optInt("validity")} ${p.optString("validity_unit")}$limitDetail",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            text = "📡 ${if (isRadius) "Radius Server" else p.optString("routers", "-")}" +
                                                (if (deviceStr.isNotEmpty()) " ($deviceStr)" else "") +
                                                (if (isPostpaid) " • Tgl Jatuh Tempo: ${p.optInt("expired_date", 20)}" else "") +
                                                " • Pelanggan Aktif: ${p.optInt("active_users")}",
                                            fontSize = 11.sp,
                                            color = ArnetaGreenSuccess
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (priceOldVal > priceVal) {
                                        Text(
                                            text = formatRupiah(priceOldVal),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(priceVal),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 14.5.sp
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingPlan = p
                                                showPlanDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Paket", tint = ArnetaCyanIncome)
                                        }
                                        IconButton(
                                            onClick = { deletingPlanTarget = p }
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Paket", tint = ArnetaRedDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlanDialog) {
        PlanFormDialog(
            initial = editingPlan,
            defaultType = if (currentTab.equals("Bandwidth", ignoreCase = true)) "Hotspot" else currentTab,
            allPlans = plansList,
            bandwidths = bwList,
            routers = routersList,
            pools = poolsList,
            devices = devicesList,
            onDismiss = { showPlanDialog = false },
            onSaved = {
                showPlanDialog = false
                loadPlansData(forceRefresh = true)
            }
        )
    }

    if (showBwDialog) {
        BandwidthFormDialog(
            initial = editingBw,
            rateUnits = rateUnitsList,
            burstPresets = burstPresetsList,
            onDismiss = { showBwDialog = false },
            onSaved = {
                showBwDialog = false
                loadPlansData(forceRefresh = true)
            }
        )
    }

    if (deletingPlanTarget != null) {
        val target = deletingPlanTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Paket ${target.optString("name_plan")}?",
            subtitle = "${target.optString("type")} • ${formatRupiah(target.optSafeDouble("price", 0.0))}",
            description = "Paket ini akan dihapus dari database dan profilnya akan dicabut dari Router MikroTik / Device terkait.",
            onDismiss = { deletingPlanTarget = null },
            onConfirm = {
                deletingPlanTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_plan_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadPlansData(forceRefresh = true)
                }
            }
        )
    }

    if (deletingBwTarget != null) {
        val target = deletingBwTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Bandwidth ${target.optString("name_bw")}?",
            subtitle = "↓ ${target.optInt("rate_down")} ${target.optString("rate_down_unit")} / ↑ ${target.optInt("rate_up")} ${target.optString("rate_up_unit")}",
            description = "Profil kecepatan bandwidth ini akan dihapus permanen dari database.",
            onDismiss = { deletingBwTarget = null },
            onConfirm = {
                deletingBwTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_bandwidth_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadPlansData(forceRefresh = true)
                }
            }
        )
    }
}
