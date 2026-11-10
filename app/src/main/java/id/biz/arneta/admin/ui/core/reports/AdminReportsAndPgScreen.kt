package id.biz.arneta.admin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LAYAR LAPORAN KEUANGAN & AUDIT SEMUA PAYMENT GATEWAY
 * (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
 */
@Composable
fun AdminReportsAndPgScreen(
    initialTab: String = "reports"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember {
        mutableIntStateOf(if (initialTab.equals("payment_gateway", ignoreCase = true)) 1 else 0)
    }

    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val monthStartDate = remember { SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date()) }
    var startDate by remember { mutableStateOf(monthStartDate) }
    var endDate by remember { mutableStateOf(todayDate) }
    var reportSearch by remember { mutableStateOf("") }
    var selectedReportMethod by remember { mutableStateOf("All") }
    var isLoadingReport by remember { mutableStateOf(false) }
    var totalIncome by remember { mutableDoubleStateOf(0.0) }
    var totalTrxCount by remember { mutableIntStateOf(0) }
    var gatewayBreakdown by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var pgStatusFilter by remember { mutableStateOf("All") }
    var pgGatewayFilter by remember { mutableStateOf("All") }
    var pgSearch by remember { mutableStateOf("") }
    var isLoadingPg by remember { mutableStateOf(false) }
    var pgItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var pgGatewayOptions by remember { mutableStateOf(listOf("viaqris", "tripay", "duitku", "xendit", "paypal")) }
    var pgByGatewayStats by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var pgTotalPaidAmount by remember { mutableDoubleStateOf(0.0) }
    var pgTotalUnpaidCount by remember { mutableIntStateOf(0) }

    fun loadReports() {
        isLoadingReport = true
        scope.launch {
            val res = ApiClient.request(
                "admin_reports",
                mapOf(
                    "start_date" to startDate.trim(),
                    "end_date" to endDate.trim(),
                    "search" to reportSearch.trim(),
                    "gateway" to if (selectedReportMethod == "All") "" else selectedReportMethod
                )
            )
            if (res.status && res.data != null) {
                val sum = res.data.optJSONObject("summary")
                totalIncome = sum?.optDouble("total_income", 0.0) ?: 0.0
                totalTrxCount = sum?.optInt("total_transactions", 0) ?: 0

                val bdArr = res.data.optJSONArray("gateway_breakdown") ?: JSONArray()
                val bdList = mutableListOf<JSONObject>()
                for (i in 0 until bdArr.length()) bdList.add(bdArr.getJSONObject(i))
                gatewayBreakdown = bdList

                val tArr = res.data.optJSONArray("transactions") ?: JSONArray()
                val tList = mutableListOf<JSONObject>()
                for (i in 0 until tArr.length()) tList.add(tArr.getJSONObject(i))
                transactions = tList
            }
            isLoadingReport = false
        }
    }

    fun loadPaymentGateways() {
        isLoadingPg = true
        scope.launch {
            val res = ApiClient.request(
                "admin_payment_gateways",
                mapOf(
                    "sub_action" to "list",
                    "status" to if (pgStatusFilter == "All") "" else pgStatusFilter,
                    "gateway" to if (pgGatewayFilter == "All") "" else pgGatewayFilter,
                    "search" to pgSearch.trim()
                )
            )
            if (res.status && res.data != null) {
                val sum = res.data.optJSONObject("summary")
                pgTotalPaidAmount = sum?.optDouble("total_paid_amount", 0.0) ?: 0.0
                pgTotalUnpaidCount = sum?.optInt("total_unpaid", 0) ?: 0

                val byGwArr = sum?.optJSONArray("by_gateway") ?: JSONArray()
                val statList = mutableListOf<JSONObject>()
                for (i in 0 until byGwArr.length()) statList.add(byGwArr.getJSONObject(i))
                pgByGatewayStats = statList

                val gwArr = res.data.optJSONArray("gateways")
                if (gwArr != null && gwArr.length() > 0) {
                    val gList = mutableListOf<String>()
                    for (i in 0 until gwArr.length()) {
                        val g = gwArr.optString(i).trim()
                        if (g.isNotEmpty()) gList.add(g)
                    }
                    if (gList.isNotEmpty()) pgGatewayOptions = gList
                }

                val arr = res.data.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                pgItems = list
            }
            isLoadingPg = false
        }
    }

    LaunchedEffect(currentTab, selectedReportMethod) {
        if (currentTab == 0) loadReports()
    }

    LaunchedEffect(reportSearch) {
        if (currentTab == 0) {
            delay(250)
            loadReports()
        }
    }

    LaunchedEffect(currentTab, pgStatusFilter, pgGatewayFilter) {
        if (currentTab == 1) loadPaymentGateways()
    }

    LaunchedEffect(pgSearch) {
        if (currentTab == 1) {
            delay(250)
            loadPaymentGateways()
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
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                label = { Text("Laporan Keuangan", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentTab == 1,
                onClick = { currentTab = 1 },
                label = { Text("Payment Gateway", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentTab == 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Dari Tanggal", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("Sampai Tanggal", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { loadReports() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ArnetaGoldPrimary)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Filter", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
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
                                text = if (selectedReportMethod == "All") "Total Pemasukan Periode"
                                else "Pemasukan ($selectedReportMethod)",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = formatRupiah(totalIncome),
                                color = ArnetaGoldPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }
                        Surface(
                            color = ArnetaCyanIncome.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$totalTrxCount Transaksi",
                                color = ArnetaCyanIncome,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (gatewayBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = selectedReportMethod == "All",
                                onClick = { selectedReportMethod = "All" },
                                label = { Text("Semua Metode", fontSize = 11.sp) }
                            )
                            gatewayBreakdown.forEach { bd ->
                                val gwName = bd.optString("gateway", "LAINNYA")
                                val gwCnt = bd.optInt("count", 0)
                                val gwTot = bd.optDouble("total", 0.0)
                                FilterChip(
                                    selected = selectedReportMethod.equals(gwName, ignoreCase = true),
                                    onClick = {
                                        selectedReportMethod = if (selectedReportMethod.equals(gwName, ignoreCase = true)) "All" else gwName
                                    },
                                    label = {
                                        Text(
                                            text = "$gwName ($gwCnt): ${formatRupiah(gwTot)}",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = reportSearch,
                onValueChange = { reportSearch = it },
                placeholder = { Text("Cari invoice, username, paket, atau metode...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (reportSearch.isNotEmpty()) {
                        IconButton(onClick = { reportSearch = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingReport) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (transactions.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada data transaksi pada periode ini.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transactions) { trx ->
                        val inv = trx.optString("invoice", "INV-${trx.optInt("id")}")
                        val uname = trx.optString("username", "-")
                        val planName = trx.optString("plan_name", "-")
                        val price = trx.optDouble("price", 0.0)
                        val method = trx.optString("method", "-")
                        val trxDate = "${trx.optString("recharged_on")} ${trx.optString("recharged_time")}".trim()
                        val expDate = "${trx.optString("expiration")} ${trx.optString("time")}".trim()

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    ArnetaTransactionToastManager.showFromApiResponse(
                                        resData = JSONObject().put("invoice", trx),
                                        fallbackTitle = "Detail Invoice $inv"
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$uname • $planName",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$inv • $method",
                                            fontSize = 11.5.sp,
                                            color = ArnetaCyanIncome
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(price),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tgl Trx: $trxDate • Exp: $expDate (${trx.optString("routers", "-")})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = {
                                            ArnetaTransactionToastManager.showFromApiResponse(
                                                resData = JSONObject().put("invoice", trx),
                                                fallbackTitle = "Detail Invoice $inv"
                                            )
                                        }
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
                                                        "No. Invoice" to inv,
                                                        "Username" to uname,
                                                        "Paket" to planName,
                                                        "Total" to formatRupiah(price),
                                                        "Metode" to method,
                                                        "Tgl Trx" to trxDate,
                                                        "Aktif s/d" to expDate
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cetak", fontSize = 11.5.sp)
                                    }

                                    TextButton(
                                        onClick = {
                                            val msg = BluetoothPrinterHelper.buildInvoiceText(
                                                invoice = inv,
                                                username = uname,
                                                planName = planName,
                                                price = price,
                                                expiration = expDate,
                                                method = method
                                            )
                                            BluetoothPrinterHelper.shareToWhatsApp(context, msg, "")
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WA", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
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
                                text = "Total Pembayaran Lunas (PG)",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = formatRupiah(pgTotalPaidAmount),
                                color = ArnetaGreenSuccess,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (pgTotalUnpaidCount > 0) ArnetaOrangeActive.copy(alpha = 0.2f) else ArnetaGreenSuccess.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$pgTotalUnpaidCount Menunggu",
                                    color = if (pgTotalUnpaidCount > 0) ArnetaOrangeActive else ArnetaGreenSuccess,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            IconButton(onClick = { loadPaymentGateways() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                            }
                        }
                    }

                    if (pgByGatewayStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            pgByGatewayStats.forEach { st ->
                                val gwName = st.optString("gateway", "-")
                                val pCnt = st.optInt("paid_count", 0)
                                val uCnt = st.optInt("unpaid_count", 0)
                                val pTot = st.optDouble("paid_total", 0.0)
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$gwName • Lunas: $pCnt (${formatRupiah(pTot)}) • Pending: $uCnt",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = pgGatewayFilter == "All",
                    onClick = { pgGatewayFilter = "All" },
                    label = { Text("Semua Gateway", fontSize = 11.5.sp) }
                )
                pgGatewayOptions.forEach { gw ->
                    val label = when (gw.lowercase()) {
                        "viaqris" -> "ViaQRIS"
                        "tripay" -> "Tripay"
                        "duitku" -> "Duitku"
                        "xendit" -> "Xendit"
                        "paypal" -> "PayPal"
                        else -> gw.uppercase()
                    }
                    FilterChip(
                        selected = pgGatewayFilter.equals(gw, ignoreCase = true),
                        onClick = { pgGatewayFilter = gw },
                        label = { Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "All" to "Semua Status",
                    "1" to "Menunggu",
                    "2" to "Lunas",
                    "3" to "Expired",
                    "4" to "Dibatalkan"
                ).forEach { (stKey, stLabel) ->
                    FilterChip(
                        selected = pgStatusFilter == stKey,
                        onClick = { pgStatusFilter = stKey },
                        label = { Text(stLabel, fontSize = 11.5.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = pgSearch,
                onValueChange = { pgSearch = it },
                placeholder = { Text("Cari username, paket, channel, atau nomor referensi...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (pgSearch.isNotEmpty()) {
                        IconButton(onClick = { pgSearch = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingPg) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (pgItems.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada transaksi Payment Gateway yang cocok.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pgItems) { item ->
                        val trxId = item.optInt("id")
                        val st = item.optInt("status", 1)
                        val stLabel = item.optString("status_label", "MENUNGGU PEMBAYARAN")
                        val gwName = item.optString("gateway", "-").uppercase()
                        val chName = item.optString("payment_channel", item.optString("payment_method", "-"))
                        val curCode = item.optString("currency", ApiClient.currencyCode)
                        val priceVal = item.optDouble("price", 0.0)
                        val payCode = item.optString("pay_code", "")
                        val checkoutUrl = item.optString("checkout_url", item.optString("pg_url_payment", ""))
                        val refId = item.optString("gateway_trx_id", "")
                        val invNo = item.optString("trx_invoice", "INV-$trxId")

                        val statusColor = when (st) {
                            2 -> ArnetaGreenSuccess
                            3, 4 -> ArnetaRedDanger
                            else -> ArnetaOrangeActive
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = ArnetaIndigoAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "$gwName • $chName",
                                                color = ArnetaCyanIncome,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.5.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = invNo,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = stLabel,
                                            color = statusColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${item.optString("username")} — ${item.optString("plan_name")}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp
                                        )
                                        Text(
                                            text = "Dibuat: ${item.optString("created_date")}" +
                                                if (st == 2) " • Lunas: ${item.optString("paid_date")}"
                                                else " • Batas: ${item.optString("expired_date")}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                        )
                                        if (refId.isNotEmpty()) {
                                            Text(
                                                text = "Ref Merchant: $refId",
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Text(
                                        text = formatRupiah(priceVal, curCode),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 15.sp
                                    )
                                }

                                if (payCode.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Column {
                                                Text("Kode Bayar / Virtual Account:", fontSize = 10.sp)
                                                Text(
                                                    text = payCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp,
                                                    color = ArnetaGoldPrimary
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cb.setPrimaryClip(ClipData.newPlainText("Kode Bayar", payCode))
                                                    Toast.makeText(context, "Kode bayar disalin", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Salin", fontSize = 11.5.sp)
                                            }
                                        }
                                    }
                                }

                                if (st == 1) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                scope.launch {
                                                    val cRes = ApiClient.request(
                                                        "admin_pg_action",
                                                        mapOf("sub_action" to "check", "id" to trxId)
                                                    )
                                                    Toast.makeText(context, cRes.message, Toast.LENGTH_LONG).show()
                                                    loadPaymentGateways()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Cek Status", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val aRes = ApiClient.request(
                                                        "admin_pg_action",
                                                        mapOf("sub_action" to "approve_manual", "id" to trxId)
                                                    )
                                                    Toast.makeText(context, aRes.message, Toast.LENGTH_LONG).show()
                                                    loadPaymentGateways()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ArnetaGreenSuccess,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Setujui Manual", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (checkoutUrl.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Link Bayar", fontSize = 11.5.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val xRes = ApiClient.request(
                                                        "admin_pg_action",
                                                        mapOf("sub_action" to "cancel", "id" to trxId)
                                                    )
                                                    Toast.makeText(context, xRes.message, Toast.LENGTH_SHORT).show()
                                                    loadPaymentGateways()
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaRedDanger)
                                        ) {
                                            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Batalkan", fontSize = 11.5.sp)
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

    ArnetaInteractiveTransactionToast()
}
