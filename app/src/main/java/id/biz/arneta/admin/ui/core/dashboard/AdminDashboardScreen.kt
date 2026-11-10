package id.biz.arneta.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val CardCyanTop = Color(0xFF00C0EF)
private val CardCyanBottom = Color(0xFF00A7D0)

private val CardEmeraldTop = Color(0xFF00CFA1)
private val CardEmeraldBottom = Color(0xFF00B285)

private val CardOrangeTop = Color(0xFFF38137)
private val CardOrangeBottom = Color(0xFFDA7331)

private val CardCoralTop = Color(0xFFF95F72)
private val CardCoralBottom = Color(0xFFDF5566)

private val WidgetTealBtn = Color(0xFF22C5B8)

/**
 * Layar Dasbor Utama Khusus Admin (100% Sesuai dashboard.php & autoload.php Admin)
 */
@Composable
fun AdminDashboardScreen(
    onNavigateToCustomers: (String) -> Unit,
    onNavigateToPrepaid: (String) -> Unit,
    onNavigateToSubScreen: (String) -> Unit,
    onUnpaidCountLoaded: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    var incomeToday by remember { mutableDoubleStateOf(0.0) }
    var incomeMonth by remember { mutableDoubleStateOf(0.0) }
    var activeExpiredLabel by remember { mutableStateOf("0/0") }
    var totalCustomers by remember { mutableIntStateOf(0) }
    var totalCustomerBalance by remember { mutableDoubleStateOf(0.0) }
    var datePeriod by remember { mutableStateOf("01 Nov 2026 / 10 Nov 2026") }
    var unpaidQrisCount by remember { mutableIntStateOf(0) }

    var regChartList by remember { mutableStateOf(emptyList<Pair<String, Float>>()) }
    var salesChartList by remember { mutableStateOf(emptyList<Pair<String, Float>>()) }
    var stockList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var expiringList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var routerStatusList by remember { mutableStateOf(emptyList<JSONObject>()) }

    var collapseRegChart by remember { mutableStateOf(false) }
    var collapseSalesChart by remember { mutableStateOf(false) }
    var collapseStocks by remember { mutableStateOf(false) }
    var collapseExpiring by remember { mutableStateOf(false) }

    fun loadDashboard() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_dashboard")
            if (res.status && res.data != null) {
                val comp = res.data.optJSONObject("company")
                if (comp != null) {
                    ApiClient.companyName = comp.optString("name", "Arneta.ID")
                    ApiClient.currencyCode = comp.optString("currency", "Rp.")
                    datePeriod = comp.optString("date_Period", datePeriod)
                }

                val cards = res.data.optJSONObject("cards")
                if (cards != null) {
                    incomeToday = cards.optDouble("income_today", 0.0)
                    incomeMonth = cards.optDouble("income_month", 0.0)
                    activeExpiredLabel = cards.optString("active_expired_label", "0/0")
                    totalCustomers = cards.optInt("total_customers", 0)
                    totalCustomerBalance = cards.optDouble("total_customer_balance", 0.0)
                    unpaidQrisCount = cards.optInt("unpaid_qris_count", 0)
                    onUnpaidCountLoaded(unpaidQrisCount)
                }

                val charts = res.data.optJSONObject("charts")
                if (charts != null) {
                    val regArr = charts.optJSONArray("monthly_registered") ?: JSONArray()
                    val rList = mutableListOf<Pair<String, Float>>()
                    for (i in 0 until regArr.length()) {
                        val o = regArr.getJSONObject(i)
                        rList.add(o.optString("month") to o.optDouble("count", 0.0).toFloat())
                    }
                    regChartList = rList

                    val salesArr = charts.optJSONArray("monthly_sales") ?: JSONArray()
                    val sList = mutableListOf<Pair<String, Float>>()
                    for (i in 0 until salesArr.length()) {
                        val o = salesArr.getJSONObject(i)
                        sList.add(o.optString("month") to o.optDouble("total", 0.0).toFloat())
                    }
                    salesChartList = sList
                }

                val stArr = res.data.optJSONArray("stocks") ?: JSONArray()
                val stTemp = mutableListOf<JSONObject>()
                for (i in 0 until stArr.length()) stTemp.add(stArr.getJSONObject(i))
                stockList = stTemp

                val expArr = res.data.optJSONArray("expiring_users") ?: JSONArray()
                val expTemp = mutableListOf<JSONObject>()
                for (i in 0 until expArr.length()) expTemp.add(expArr.getJSONObject(i))
                expiringList = expTemp
            }
            isLoading = false

            val rRes = ApiClient.request("admin_router_status")
            if (rRes.status && rRes.dataArray != null) {
                val rTemp = mutableListOf<JSONObject>()
                for (i in 0 until rRes.dataArray.length()) {
                    rTemp.add(rRes.dataArray.getJSONObject(i))
                }
                routerStatusList = rTemp
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDashboard()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 95.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dasbor Admin",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ArnetaGoldPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    IconButton(onClick = { loadDashboard() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }
            }
        }

        // GRID 2x2 KARTU UTAMA ADMIN (Sesuai dashboard.tpl)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WebStyleMetricCard(
                        superscript = ApiClient.currencyCode,
                        mainValue = String.format("%,.0f", incomeToday).replace(',', '.'),
                        footerLabel = "Pendapatan hari ini",
                        topColor = CardCyanTop,
                        bottomColor = CardCyanBottom,
                        onClick = { onNavigateToSubScreen("reports") },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleMetricCard(
                        superscript = ApiClient.currencyCode,
                        mainValue = String.format("%,.0f", incomeMonth).replace(',', '.'),
                        footerLabel = "Penghasilan bulan ini",
                        topColor = CardEmeraldTop,
                        bottomColor = CardEmeraldBottom,
                        onClick = { onNavigateToSubScreen("reports") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WebStyleMetricCard(
                        superscript = null,
                        mainValue = activeExpiredLabel,
                        footerLabel = "Aktif/Kedaluwarsa",
                        topColor = CardOrangeTop,
                        bottomColor = CardOrangeBottom,
                        onClick = { onNavigateToPrepaid("active_users") },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleMetricCard(
                        superscript = null,
                        mainValue = totalCustomers.toString(),
                        footerLabel = "Pelanggan",
                        topColor = CardCoralTop,
                        bottomColor = CardCoralBottom,
                        onClick = { onNavigateToCustomers("") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // BARIS BREADCRUMB TANGGAL & SALDO PELANGGAN
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    val formattedBal = String.format("%,.0f", totalCustomerBalance).replace(',', '.')
                    Text(
                        text = "${datePeriod.replace("/", "  /  ")}  /  Saldo Pelanggan ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = ApiClient.currencyCode,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formattedBal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (unpaidQrisCount > 0) {
            item {
                Surface(
                    color = ArnetaGoldPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToSubScreen("payment_gateway") }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = ArnetaGoldPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$unpaidQrisCount Transaksi Payment Gateway Menunggu Pembayaran",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Ketuk untuk cek mutasi / setujui manual",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ArnetaGoldPrimary)
                    }
                }
            }
        }

        // WIDGET 1: Pendaftaran Pelanggan perbulan
        item {
            WebDashboardWidgetCard(
                icon = Icons.Filled.GridOn,
                title = "Pendaftaran Pelanggan perbulan",
                isCollapsed = collapseRegChart,
                onToggleCollapse = { collapseRegChart = !collapseRegChart },
                onRefresh = { loadDashboard() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 12.dp)
                                .background(Color(0xFF6366F1).copy(alpha = 0.75f))
                                .border(1.5.dp, Color(0xFF4338CA))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Registered Members",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    WebBarChartCanvas(
                        data = regChartList,
                        barFillColor = Color(0xFF6366F1).copy(alpha = 0.78f),
                        barBorderColor = Color(0xFF3730A3),
                        isCurrency = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
        }

        // WIDGET 2: Total penjualan perbulan
        item {
            WebDashboardWidgetCard(
                icon = Icons.Filled.Inbox,
                title = "Total penjualan perbulan",
                isCollapsed = collapseSalesChart,
                onToggleCollapse = { collapseSalesChart = !collapseSalesChart },
                onRefresh = { loadDashboard() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 12.dp)
                                .background(Color(0xFF0011FF))
                                .border(1.5.dp, Color(0xFF1E3A8A))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Monthly Sales",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    WebBarChartCanvas(
                        data = salesChartList,
                        barFillColor = Color(0xFF0011FF),
                        barBorderColor = Color(0xFF1E3A8A),
                        isCurrency = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
        }

        // WIDGET 3: Live Status Router MikroTik
        if (routerStatusList.isNotEmpty()) {
            item {
                WebDashboardWidgetCard(
                    icon = Icons.Filled.Router,
                    title = "Status Live Router MikroTik",
                    isCollapsed = false,
                    onToggleCollapse = {},
                    onRefresh = { loadDashboard() }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        routerStatusList.forEach { r ->
                            val online = r.optBoolean("online", false)
                            val cpu = r.optInt("cpu_load", 0)
                            val ramPct = r.optDouble("ram_percent", 0.0)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSubScreen("online_users") }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(if (online) ArnetaGreenSuccess else ArnetaRedDanger)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${r.optString("name")} (${r.optString("board_name")})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Uptime: ${r.optString("uptime")} • Ping: ${r.optInt("ping_ms")} ms",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "CPU $cpu% | RAM $ramPct%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cpu > 80) ArnetaRedDanger else ArnetaCyanIncome
                                        )
                                        Text(
                                            text = "HS: ${r.optInt("hotspot_online")} • PPPoE: ${r.optInt("pppoe_online")}",
                                            fontSize = 11.sp,
                                            color = ArnetaGreenSuccess
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // WIDGET 4: Stok Voucher & Paket Aktif
        if (stockList.isNotEmpty()) {
            item {
                WebDashboardWidgetCard(
                    icon = Icons.Filled.ConfirmationNumber,
                    title = "Stok Voucher & Paket Aktif",
                    isCollapsed = collapseStocks,
                    onToggleCollapse = { collapseStocks = !collapseStocks },
                    onRefresh = { loadDashboard() }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(8.dp)
                        ) {
                            Text("Nama Paket", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                            Text("Aktif", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                            Text("Sisa Vcr", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                            Text("Terpakai", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                        }
                        stockList.forEach { st ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = st.optString("name_plan"),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    text = st.optInt("active_users").toString(),
                                    fontSize = 12.sp,
                                    color = ArnetaCyanIncome,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.8f)
                                )
                                Text(
                                    text = st.optInt("unused").toString(),
                                    fontSize = 12.sp,
                                    color = ArnetaGreenSuccess,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.8f)
                                )
                                Text(
                                    text = st.optInt("used").toString(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        // WIDGET 5: Pelanggan Kedaluwarsa / Segera Habis Masa Aktif (Sesuai Widget User Expired di dashboard.tpl)
        if (expiringList.isNotEmpty()) {
            item {
                WebDashboardWidgetCard(
                    icon = Icons.Filled.WarningAmber,
                    title = "Pelanggan Kedaluwarsa / Segera Habis",
                    isCollapsed = collapseExpiring,
                    onToggleCollapse = { collapseExpiring = !collapseExpiring },
                    onRefresh = { loadDashboard() }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        expiringList.forEach { exp ->
                            val uname = exp.optString("username", "-")
                            val planName = exp.optString("namebp", "-")
                            val expDate = exp.optString("expiration", "-")
                            val expTime = exp.optString("time", "")
                            val isOff = exp.optString("status") == "off"

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToCustomers(uname) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$uname • $planName",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = "Exp: $expDate $expTime",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Surface(
                                        color = if (isOff) ArnetaRedDanger.copy(alpha = 0.2f) else ArnetaGoldPrimary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isOff) "EXPIRED" else "SEGERA",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOff) ArnetaRedDanger else ArnetaGoldPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
}

@Composable
private fun WebStyleMetricCard(
    superscript: String?,
    mainValue: String,
    footerLabel: String,
    topColor: Color,
    bottomColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topColor)
                    .padding(vertical = 18.dp, horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (!superscript.isNullOrEmpty()) {
                        Text(
                            text = superscript,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 3.dp, top = 1.dp)
                        )
                    }
                    Text(
                        text = mainValue,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomColor)
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = footerLabel,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WebDashboardWidgetCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(WidgetTealBtn)
                            .clickable { onToggleCollapse() }
                    ) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Filled.Add else Icons.Filled.Remove,
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(WidgetTealBtn)
                            .clickable { onRefresh() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun WebBarChartCanvas(
    data: List<Pair<String, Float>>,
    barFillColor: Color,
    barBorderColor: Color,
    isCurrency: Boolean,
    modifier: Modifier = Modifier
) {
    val months = if (data.isNotEmpty()) data else listOf(
        "Jan" to 0f, "Feb" to 0f, "Mar" to 0f, "Apr" to 0f,
        "May" to 0f, "Jun" to 0f, "Jul" to 0f, "Aug" to 0f,
        "Sep" to 0f, "Oct" to 0f, "Nov" to 0f, "Dec" to 0f
    )
    val maxVal = (months.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(if (isCurrency) 100000f else 4f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 6.dp, bottom = 4.dp)
            ) {
                val steps = 4
                for (i in steps downTo 0) {
                    val v = (maxVal / steps) * i
                    val txt = if (isCurrency) {
                        String.format("%,.0f", v)
                    } else {
                        String.format("%.1f", v)
                    }
                    Text(text = txt, fontSize = 9.sp, color = labelColor)
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val w = size.width
                val h = size.height
                val steps = 4

                for (i in 0..steps) {
                    val y = h - (h / steps) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                val count = months.size.coerceAtLeast(1)
                val slotWidth = w / count
                val barWidth = (slotWidth * 0.62f).coerceAtLeast(6f)

                months.forEachIndexed { index, pair ->
                    val ratio = (pair.second / maxVal).coerceIn(0f, 1f)
                    val barHeight = h * ratio
                    val left = index * slotWidth + (slotWidth - barWidth) / 2f
                    val top = h - barHeight

                    if (barHeight > 1f) {
                        drawRoundRect(
                            color = barFillColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                        drawRoundRect(
                            color = barBorderColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2f, 2f),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp)
        ) {
            months.forEach { (mName, _) ->
                Text(
                    text = mName,
                    fontSize = 9.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
