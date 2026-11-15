package id.biz.arneta.admin.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun parseJsonArrayToList(arr: JSONArray?): List<JSONObject> {
    if (arr == null) return emptyList()
    val list = ArrayList<JSONObject>(arr.length())
    for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.let { list.add(it) }
    }
    return list
}

@Composable
fun AdminReportsAndPgScreen(
    initialTab: String = "reports"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { sdf.format(Date()) }
    val firstDayOfMonthStr = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        sdf.format(cal.time)
    }
    val firstDayOfYearStr = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        sdf.format(cal.time)
    }

    var startDate by remember { mutableStateOf(firstDayOfMonthStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var selectedDatePreset by remember { mutableStateOf("month") }
    var selectedMethodFilter by remember { mutableStateOf("All") }
    var reportSearchQuery by remember { mutableStateOf("") }

    val initReportParams = remember(startDate, endDate) {
        mapOf("start_date" to startDate, "end_date" to endDate, "limit" to 300)
    }
    val cachedReports = remember { ApiClient.getCached("admin_reports", initReportParams) }
    val cachedPg = remember { ApiClient.getCached("admin_payment_gateway", mapOf("limit" to 200)) }

    var transactionsList by remember {
        mutableStateOf(
            if (cachedReports?.status == true) {
                parseJsonArrayToList(cachedReports.data?.optJSONArray("transactions") ?: cachedReports.data?.optJSONArray("items") ?: cachedReports.dataArray)
            } else emptyList()
        )
    }
    var totalIncome by remember {
        mutableDoubleStateOf(
            cachedReports?.data?.optJSONObject("summary").optSafeDouble("total_income", cachedReports?.data.optSafeDouble("total_income", 0.0))
        )
    }

    var pgList by remember {
        mutableStateOf(
            if (cachedPg?.status == true) {
                parseJsonArrayToList(cachedPg.data?.optJSONArray("transactions") ?: cachedPg.data?.optJSONArray("items") ?: cachedPg.dataArray)
            } else emptyList()
        )
    }
    var pgStatusFilter by remember { mutableStateOf("All") }
    var pgSearchQuery by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var actionLoadingId by remember { mutableIntStateOf(0) }
    var approveConfirmTarget by remember { mutableStateOf<JSONObject?>(null) }

    fun loadReports(forceRefresh: Boolean = false) {
        if (transactionsList.isEmpty()) isLoading = true
        scope.launch {
            val params = mapOf(
                "start_date" to startDate.trim(),
                "end_date" to endDate.trim(),
                "limit" to 300
            )
            val res = ApiClient.request("admin_reports", params, forceRefresh = forceRefresh)
            if (res.status) {
                val parsed = withContext(Dispatchers.Default) {
                    parseJsonArrayToList(
                        res.data?.optJSONArray("transactions")
                            ?: res.data?.optJSONArray("items")
                            ?: res.dataArray
                    )
                }
                transactionsList = parsed
                val sumObj = res.data?.optJSONObject("summary")
                totalIncome = sumObj.optSafeDouble(
                    "total_income",
                    res.data.optSafeDouble("total_income", parsed.sumOf { it.optSafeDouble("price", 0.0) })
                )
            }
            isLoading = false
        }
    }

    fun loadPaymentGateway(forceRefresh: Boolean = false) {
        if (pgList.isEmpty()) isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_payment_gateway", mapOf("limit" to 200), forceRefresh = forceRefresh)
            if (res.status) {
                pgList = withContext(Dispatchers.Default) {
                    parseJsonArrayToList(
                        res.data?.optJSONArray("transactions")
                            ?: res.data?.optJSONArray("items")
                            ?: res.dataArray
                    )
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == "reports") {
            loadReports(forceRefresh = false)
        } else {
            loadPaymentGateway(forceRefresh = false)
        }
    }

    val methodSummaryTabs = remember(transactionsList) {
        val grouped = linkedMapOf<String, Pair<Int, Double>>()
        for (tr in transactionsList) {
            val rawM = tr.optString("method", "Lainnya").trim()
            val mKey = rawM.split("-").firstOrNull()?.trim()?.ifEmpty { "Lainnya" } ?: "Lainnya"
            val cur = grouped[mKey] ?: (0 to 0.0)
            grouped[mKey] = (cur.first + 1) to (cur.second + tr.optSafeDouble("price", 0.0))
        }
        val tabs = mutableListOf("All" to "Semua Metode (${transactionsList.size})")
        grouped.forEach { (mName, pair) ->
            tabs.add(mName to "${mName.uppercase()} (${pair.first}): ${formatRupiah(pair.second)}")
        }
        tabs
    }

    val filteredTransactions = remember(transactionsList, selectedMethodFilter, reportSearchQuery) {
        val q = reportSearchQuery.trim().lowercase()
        transactionsList.filter { tr ->
            val rawM = tr.optString("method", "")
            val matchMethod = selectedMethodFilter == "All" || rawM.startsWith(selectedMethodFilter, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                tr.optString("invoice", "").lowercase().contains(q) ||
                tr.optString("username", "").lowercase().contains(q) ||
                tr.optString("fullname", "").lowercase().contains(q) ||
                tr.optString("plan_name", "").lowercase().contains(q) ||
                rawM.lowercase().contains(q) ||
                tr.optString("routers", "").lowercase().contains(q)
            matchMethod && matchQuery
        }
    }

    val filteredTotals = remember(filteredTransactions) {
        filteredTransactions.sumOf { it.optSafeDouble("price", 0.0) }
    }

    val filteredPgList = remember(pgList, pgStatusFilter, pgSearchQuery) {
        val q = pgSearchQuery.trim().lowercase()
        pgList.filter { item ->
            val st = item.optInt("status", 1).toString()
            val matchSt = pgStatusFilter == "All" || st == pgStatusFilter
            val matchQuery = q.isEmpty() ||
                item.optString("username", "").lowercase().contains(q) ||
                item.optString("plan_name", "").lowercase().contains(q) ||
                item.optString("gateway", "").lowercase().contains(q) ||
                item.optString("payment_channel", "").lowercase().contains(q) ||
                item.optString("trx_invoice", "").lowercase().contains(q) ||
                item.optString("gateway_trx_id", "").lowercase().contains(q)
            matchSt && matchQuery
        }
    }

    fun openTransactionReceiptModal(tr: JSONObject) {
        val safeInvoice = JSONObject().apply {
            put("id", tr.optInt("id", 0))
            put("invoice", tr.optString("invoice").ifEmpty { "INV-${tr.optInt("id", 0)}" })
            put("username", tr.optString("username", "-"))
            put("fullname", tr.optString("fullname").ifEmpty { tr.optString("username", "-") })
            put("phonenumber", tr.optString("phonenumber", ""))
            put("plan_name", tr.optString("plan_name", "-"))
            put("type", tr.optString("type", "Layanan"))
            put("price", tr.optSafeDouble("price", 0.0))
            put("recharged_on", tr.optString("recharged_on", ""))
            put("recharged_time", tr.optString("recharged_time", ""))
            put("expiration", tr.optString("expiration", ""))
            put("time", tr.optString("time", ""))
            put("method", tr.optString("method", "-"))
            put("routers", tr.optString("routers", "-"))
        }
        val wrapper = JSONObject().apply {
            put("invoice", safeInvoice)
            put("balance", tr.optSafeDouble("balance", 0.0))
        }
        ArnetaTransactionToastManager.showFromApiResponse(
            resData = wrapper,
            fallbackTitle = "Detail Struk Transaksi",
            fallbackCustomer = safeInvoice,
            fallbackPlanName = safeInvoice.optString("plan_name", "-"),
            fallbackPrice = safeInvoice.optSafeDouble("price", 0.0),
            fallbackMethod = safeInvoice.optString("method", "-"),
            fallbackRouter = safeInvoice.optString("routers", "-")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        ArnetaSubTabBar(
            tabs = listOf(
                "reports" to "📊 Laporan Keuangan",
                "payment_gateway" to "💳 Audit Payment Gateway"
            ),
            selectedKey = currentTab,
            onSelect = { currentTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (currentTab == "reports") {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArnetaSubTabBar(
                    tabs = listOf(
                        "today" to "Hari Ini",
                        "month" to "Bulan Ini",
                        "year" to "Tahun Ini"
                    ),
                    selectedKey = selectedDatePreset,
                    onSelect = { preset ->
                        selectedDatePreset = preset
                        when (preset) {
                            "today" -> {
                                startDate = todayStr
                                endDate = todayStr
                            }
                            "month" -> {
                                startDate = firstDayOfMonthStr
                                endDate = todayStr
                            }
                            "year" -> {
                                startDate = firstDayOfYearStr
                                endDate = todayStr
                            }
                        }
                        loadReports(forceRefresh = false)
                    },
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { loadReports(forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {
                        startDate = it
                        selectedDatePreset = "custom"
                    },
                    label = { Text("Dari Tanggal", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {
                        endDate = it
                        selectedDatePreset = "custom"
                    },
                    label = { Text("Sampai Tanggal", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { loadReports(forceRefresh = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(50.dp)
                        .padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Tampilkan")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Total Pemasukan Periode",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatRupiah(if (selectedMethodFilter == "All" && reportSearchQuery.isBlank()) totalIncome else filteredTotals),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                color = ArnetaGoldPrimary
                            )
                        }
                        Surface(
                            color = ArnetaCyanIncome.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${filteredTransactions.size} Transaksi",
                                color = ArnetaCyanIncome,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (methodSummaryTabs.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ArnetaSubTabBar(
                            tabs = methodSummaryTabs,
                            selectedKey = selectedMethodFilter,
                            onSelect = { selectedMethodFilter = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = reportSearchQuery,
                onValueChange = { reportSearchQuery = it },
                placeholder = { Text("Cari invoice, username, paket, atau metode...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (reportSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { reportSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading && transactionsList.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (filteredTransactions.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada transaksi pada rentang tanggal/filter ini.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredTransactions,
                        key = { it.optInt("id", it.optString("invoice").hashCode()) }
                    ) { tr ->
                        val invNo = tr.optString("invoice").ifEmpty { "INV-${tr.optInt("id")}" }
                        val uname = tr.optString("username", "-")
                        val fname = tr.optString("fullname").ifEmpty { uname }
                        val pName = tr.optString("plan_name", "-")
                        val priceVal = tr.optSafeDouble("price", 0.0)
                        val methodStr = tr.optString("method", "-")
                        val routerStr = tr.optString("routers", "-")
                        val rechDateTime = "${tr.optString("recharged_on")} ${tr.optString("recharged_time")}".trim()
                        val expDateTime = "${tr.optString("expiration")} ${tr.optString("time")}".trim()

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { openTransactionReceiptModal(tr) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$uname • $pName",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.5.sp
                                        )
                                        Text(
                                            text = "$invNo • $methodStr",
                                            fontSize = 11.5.sp,
                                            color = ArnetaCyanIncome,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(priceVal),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tgl Trx: $rechDateTime • Exp: $expDateTime ($routerStr)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = { openTransactionReceiptModal(tr) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Struk", fontSize = 11.5.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                    }

                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                BluetoothPrinterHelper.printReceiptText(
                                                    context = context,
                                                    title = "STRUK TRANSAKSI ${ApiClient.companyName.uppercase()}",
                                                    lines = listOf(
                                                        "No. Invoice" to invNo,
                                                        "Pelanggan" to "$fname ($uname)",
                                                        "Paket" to pName,
                                                        "Harga" to formatRupiah(priceVal),
                                                        "Metode" to methodStr,
                                                        "Tgl Trx" to rechDateTime,
                                                        "Aktif s/d" to expDateTime,
                                                        "Router" to routerStr
                                                    )
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cetak", fontSize = 11.5.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                                    }

                                    TextButton(
                                        onClick = {
                                            val waMsg = BluetoothPrinterHelper.buildInvoiceText(
                                                invoice = invNo,
                                                username = "$fname ($uname)",
                                                planName = pName,
                                                price = priceVal,
                                                expiration = expDateTime,
                                                method = methodStr
                                            )
                                            BluetoothPrinterHelper.shareToWhatsApp(context, waMsg, tr.optString("phonenumber", ""))
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, tint = ArnetaGreenSuccess, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WA", fontSize = 11.5.sp, color = ArnetaGreenSuccess, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TAB AUDIT PAYMENT GATEWAY
            val pgStatusTabs = remember(pgList) {
                val unpaidCnt = pgList.count { it.optInt("status", 1) == 1 }
                val paidCnt = pgList.count { it.optInt("status", 1) == 2 }
                val expCnt = pgList.count { it.optInt("status", 1) >= 3 }
                listOf(
                    "All" to "Semua (${pgList.size})",
                    "1" to "⏳ Unpaid ($unpaidCnt)",
                    "2" to "✅ Paid ($paidCnt)",
                    "3" to "❌ Expired ($expCnt)"
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArnetaSubTabBar(
                    tabs = pgStatusTabs,
                    selectedKey = pgStatusFilter,
                    onSelect = { pgStatusFilter = it },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { loadPaymentGateway(forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = pgSearchQuery,
                onValueChange = { pgSearchQuery = it },
                placeholder = { Text("Cari username, invoice, gateway (Tripay/QRIS/Duitku)...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (pgSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { pgSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading && pgList.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (filteredPgList.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Belum ada tagihan Payment Gateway yang cocok.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredPgList,
                        key = { it.optInt("id", it.optString("trx_invoice").hashCode()) }
                    ) { pg ->
                        val pgId = pg.optInt("id")
                        val statusInt = pg.optInt("status", 1)
                        val (stText, stColor) = when (statusInt) {
                            2 -> "PAID / LUNAS" to ArnetaGreenSuccess
                            3 -> "EXPIRED" to ArnetaRedDanger
                            4 -> "CANCELED" to Color.Gray
                            else -> "UNPAID / PENDING" to ArnetaOrangeActive
                        }
                        val priceVal = pg.optSafeDouble("price", 0.0)
                        val checkoutUrl = pg.optString("pg_url_payment", pg.optString("checkout_url", "")).trim()
                        val payCode = pg.optString("pay_code", "").trim()

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
                                        Text(
                                            text = "@${pg.optString("username")} • ${pg.optString("plan_name")}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.5.sp
                                        )
                                        Text(
                                            text = "${pg.optString("gateway").uppercase()} (${pg.optString("payment_channel").ifEmpty { pg.optString("payment_method", "-") }})" +
                                                if (pg.optString("gateway_trx_id").isNotEmpty()) " • Ref: ${pg.optString("gateway_trx_id")}" else "",
                                            fontSize = 11.5.sp,
                                            color = ArnetaCyanIncome,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Surface(
                                        color = stColor.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = stText,
                                            color = stColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Dibuat: ${pg.optString("created_date", "-")} • Exp: ${pg.optString("expired_date", "-")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        text = formatRupiah(priceVal),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                }

                                if (payCode.isNotEmpty()) {
                                    Text(
                                        text = "Kode Bayar / VA: $payCode",
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = ArnetaGoldLight
                                    )
                                }

                                if (statusInt == 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            enabled = actionLoadingId != pgId,
                                            onClick = {
                                                actionLoadingId = pgId
                                                scope.launch {
                                                    val cRes = ApiClient.request(
                                                        "admin_pg_action",
                                                        mapOf("id" to pgId, "sub_action" to "check")
                                                    )
                                                    actionLoadingId = 0
                                                    Toast.makeText(context, cRes.message, Toast.LENGTH_LONG).show()
                                                    loadPaymentGateway(forceRefresh = true)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                        ) {
                                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Cek Status", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            enabled = actionLoadingId != pgId,
                                            onClick = { approveConfirmTarget = pg },
                                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGreenSuccess, contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .height(34.dp)
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Approve & Aktifkan", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }

                                        if (checkoutUrl.startsWith("http")) {
                                            IconButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Filled.OpenInNew, contentDescription = "Buka Link", tint = ArnetaCyanIncome, modifier = Modifier.size(18.dp))
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
    }

    if (approveConfirmTarget != null) {
        val target = approveConfirmTarget!!
        val pgId = target.optInt("id")
        AlertDialog(
            onDismissRequest = { approveConfirmTarget = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text("Setujui Manual & Aktifkan Paket?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Tagihan @${target.optString("username")} untuk paket ${target.optString("plan_name")} (${formatRupiah(target.optSafeDouble("price", 0.0))}) akan ditandai LUNAS dan paket akan langsung diaktifkan ke Router MikroTik.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        approveConfirmTarget = null
                        actionLoadingId = pgId
                        scope.launch {
                            val aRes = ApiClient.request(
                                "admin_pg_action",
                                mapOf("id" to pgId, "sub_action" to "approve")
                            )
                            actionLoadingId = 0
                            Toast.makeText(context, aRes.message, Toast.LENGTH_LONG).show()
                            if (aRes.status && aRes.data != null) {
                                ArnetaTransactionToastManager.showFromApiResponse(
                                    resData = aRes.data,
                                    fallbackTitle = "Tagihan PG Disetujui & Paket Aktif!",
                                    fallbackPlanName = target.optString("plan_name"),
                                    fallbackPrice = target.optSafeDouble("price", 0.0),
                                    fallbackMethod = target.optString("gateway", "Payment Gateway")
                                )
                            }
                            loadPaymentGateway(forceRefresh = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGreenSuccess, contentColor = Color.White)
                ) {
                    Text("Ya, Approve Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { approveConfirmTarget = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    ArnetaInteractiveTransactionToast()
}
