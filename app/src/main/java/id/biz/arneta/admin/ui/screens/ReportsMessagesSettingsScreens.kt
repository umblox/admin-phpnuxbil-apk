package id.biz.arneta.admin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HubMenuItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badgeCount: Int = 0
)

/**
 * 1. LAYAR MENU HUB LENGKAP (Sesuai Pemanggilan di MainActivity.kt)
 */
@Composable
fun AdminMenuHubScreen(
    unpaidQrisCount: Int = 0,
    onOpenScreen: (String) -> Unit
) {
    val menuItems = listOf(
        HubMenuItem(
            key = "live_monitor",
            title = "MikroTik Monitor & Jaringan",
            subtitle = "Trafik interface, health, user online, log & router",
            icon = Icons.Filled.Router,
            accent = ArnetaCyanIncome
        ),
        HubMenuItem(
            key = "online_users",
            title = "User Online & Kick Sesi",
            subtitle = "Pantau pengguna Hotspot & PPPoE yang sedang aktif",
            icon = Icons.Filled.WifiTethering,
            accent = ArnetaGreenSuccess
        ),
        HubMenuItem(
            key = "plans",
            title = "Paket Internet & Bandwidth",
            subtitle = "Kelola paket Hotspot, PPPoE, saldo & limit kecepatan",
            icon = Icons.Filled.Speed,
            accent = ArnetaGoldPrimary
        ),
        HubMenuItem(
            key = "reports",
            title = "Laporan Keuangan",
            subtitle = "Rekap pendapatan harian, bulanan & per metode bayar",
            icon = Icons.Filled.Assessment,
            accent = ArnetaGreenSuccess
        ),
        HubMenuItem(
            key = "payment_gateway",
            title = "Audit Payment Gateway",
            subtitle = "ViaQRIS, Tripay, Duitku, Xendit & PayPal",
            icon = Icons.Filled.Payments,
            accent = ArnetaOrangeActive,
            badgeCount = unpaidQrisCount
        ),
        HubMenuItem(
            key = "map",
            title = "Peta Pelanggan & Jaringan",
            subtitle = "Titik koordinat pelanggan, router & jangkauan sinyal",
            icon = Icons.Filled.Map,
            accent = ArnetaCyanIncome
        ),
        HubMenuItem(
            key = "assets",
            title = "Asset Manager & Inventaris",
            subtitle = "Data ODP, ODC, ONT/Modem, kabel & nilai investasi",
            icon = Icons.Filled.AccountTree,
            accent = Color(0xFF8B5CF6)
        ),
        HubMenuItem(
            key = "broadcast",
            title = "Kirim Pesan & Broadcast",
            subtitle = "Kirim pesan WhatsApp, SMS & Inbox ke pelanggan",
            icon = Icons.Filled.Campaign,
            accent = ArnetaGreenSuccess
        ),
        HubMenuItem(
            key = "pages",
            title = "Template & Halaman Statis",
            subtitle = "Edit template voucher, pengumuman & info tagihan",
            icon = Icons.Filled.Description,
            accent = ArnetaGoldLight
        ),
        HubMenuItem(
            key = "settings",
            title = "Pengaturan, Staf & Log Sistem",
            subtitle = "Konfigurasi aplikasi, shortcut, staf, cronjob & cache",
            icon = Icons.Filled.Settings,
            accent = ArnetaIndigoAccent
        ),
        HubMenuItem(
            key = "webview_admin",
            title = "Web Admin Panel",
            subtitle = "Buka halaman web PHPNuxBill dengan login SSO otomatis",
            icon = Icons.Filled.Language,
            accent = ArnetaGoldPrimary
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 95.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(ArnetaGoldPrimary.copy(alpha = 0.2f))
                                .border(1.5.dp, ArnetaGoldPrimary, CircleShape)
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = ArnetaGoldPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = ApiClient.adminName,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "@${ApiClient.adminUsername} • ${ApiClient.adminRole}",
                                color = ArnetaGoldLight,
                                fontSize = 12.sp
                            )
                            Text(
                                text = ApiClient.baseUrl.replace("https://", "").replace("http://", ""),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = { onOpenScreen("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = ArnetaGoldPrimary)
                    }
                }
            }
        }

        item {
            Text(
                text = "Menu Administrasi",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(menuItems) { item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenScreen(item.key) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(item.accent.copy(alpha = 0.16f))
                        ) {
                            Icon(item.icon, contentDescription = null, tint = item.accent)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                if (item.badgeCount > 0) {
                                    Surface(
                                        color = ArnetaRedDanger,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "${item.badgeCount} Pending",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.subtitle,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

/**
 * 2. LAYAR LAPORAN KEUANGAN & AUDIT SEMUA PAYMENT GATEWAY
 *    (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
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

/**
 * 3. LAYAR KIRIM PESAN & BROADCAST WHATSAPP / SMS / INBOX (Sesuai MainActivity.kt)
 */
@Composable
fun AdminBroadcastMessageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("single") }
    var selectedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var targetGroup by remember { mutableStateOf("all") }
    var viaChannel by remember { mutableStateOf("wa") }
    var subject by remember { mutableStateOf("Informasi Layanan ${ApiClient.companyName}") }
    var messageText by remember {
        mutableStateOf("Halo *[[name]]* (@[[user_name]]),\n\nTerima kasih telah menggunakan layanan internet *[[company_name]]*.")
    }
    var isSending by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 95.dp)
    ) {
        Text("Kirim Pesan Pelanggan", fontWeight = FontWeight.Bold, fontSize = 19.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "single",
                onClick = { mode = "single" },
                label = { Text("Per Pelanggan") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = mode == "broadcast",
                onClick = { mode = "broadcast" },
                label = { Text("Broadcast Massal") },
                leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        if (mode == "single") {
            CustomerLiveSearchPicker(
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { selectedCustomer = it }
            )
        } else {
            Text("Kelompok Penerima Broadcast:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "all" to "Semua Pelanggan",
                    "active" to "Pelanggan Aktif",
                    "expired" to "Kedaluwarsa",
                    "hotspot" to "Khusus Hotspot",
                    "pppoe" to "Khusus PPPoE"
                ).forEach { (gKey, gLabel) ->
                    FilterChip(
                        selected = targetGroup == gKey,
                        onClick = { targetGroup = gKey },
                        label = { Text(gLabel, fontSize = 11.5.sp) }
                    )
                }
            }
        }

        Text("Jalur Pengiriman:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "wa" to "WhatsApp",
                "inbox" to "Inbox Aplikasi",
                "sms" to "SMS",
                "both" to "WA + Inbox"
            ).forEach { (vKey, vLabel) ->
                FilterChip(
                    selected = viaChannel == vKey,
                    onClick = { viaChannel = vKey },
                    label = { Text(vLabel, fontSize = 11.5.sp) }
                )
            }
        }

        if (viaChannel == "inbox" || viaChannel == "both") {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Judul Pesan Inbox") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Isi Pesan") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Variabel otomatis: [[name]], [[user_name]], [[phone]], [[balance]], [[company_name]]",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )

        val canSend = !isSending && messageText.isNotBlank() && (mode == "broadcast" || selectedCustomer != null)

        Button(
            enabled = canSend,
            onClick = {
                isSending = true
                scope.launch {
                    val res = ApiClient.request(
                        "admin_send_message",
                        mapOf(
                            "mode" to mode,
                            "customer_id" to (selectedCustomer?.optInt("id") ?: 0),
                            "group" to targetGroup,
                            "via" to viaChannel,
                            "subject" to subject.trim(),
                            "message" to messageText.trim()
                        )
                    )
                    isSending = false
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
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
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSending) "MENGIRIM PESAN..." else "KIRIM PESAN SEKARANG",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 4. LAYAR TEMPLATE & HALAMAN STATIS (Sesuai MainActivity.kt)
 */
@Composable
fun AdminStaticPagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPage by remember { mutableStateOf<JSONObject?>(null) }
    var contentText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    fun loadPages() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_pages")
            val arr = res.dataArray ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            pages = list
            if (list.isNotEmpty() && selectedPage == null) {
                selectedPage = list.first()
                contentText = list.first().optString("content", "")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPages()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 95.dp)
    ) {
        Text("Template & Halaman Statis", fontWeight = FontWeight.Bold, fontSize = 19.sp)

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                pages.forEach { p ->
                    val pName = p.optString("name")
                    FilterChip(
                        selected = selectedPage?.optString("name") == pName,
                        onClick = {
                            selectedPage = p
                            contentText = p.optString("content", "")
                        },
                        label = { Text(p.optString("title", pName), fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                label = { Text("Kode HTML (${selectedPage?.optString("title") ?: "-"})") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(
                enabled = !isSaving && selectedPage != null,
                onClick = {
                    val p = selectedPage ?: return@Button
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_page_save",
                            mapOf("name" to p.optString("name"), "content" to contentText)
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) loadPages()
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
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "Menyimpan..." else "Simpan Halaman", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * 5. LAYAR PENGATURAN SISTEM, LOKALISASI, MAINTENANCE, TEMPLATE NOTIFIKASI, STAF, DB & SHORTCUT
 *    (Mempertahankan parameter onCustomShortcutsChanged & onLogout untuk MainActivity.kt +
 *     100% Sesuai system/controllers/settings.php)
 */
@Composable
fun AdminSettingsAndToolsScreen(
    onCustomShortcutsChanged: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("general") }
    // Pilihan tab: general | localisation | notifications | maintenance | staff | db_logs | shortcuts
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Map seluruh key-value dari tbl_appconfig
    var configMap by remember {
        mutableStateOf(
            mapOf(
                "CompanyName" to ApiClient.companyName,
                "currency_code" to ApiClient.currencyCode,
                "payment_usings" to "Cash,Transfer Bank",
                "enable_balance" to "yes"
            )
        )
    }

    // State Shortcut Cepat
    var selectedShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }
    var notifMap by remember {
        mutableStateOf(mapOf("init" to "").filterKeys { it != "init" })
    }
    var defaultNotifMap by remember {
        mutableStateOf(mapOf("init" to "").filterKeys { it != "init" })
    }
    var selectedNotifKey by remember { mutableStateOf("invoice_paid") }

    // State Staf Admin (tbl_users)
    var staffList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var staffSearchQuery by remember { mutableStateOf("") }
    var showStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<JSONObject?>(null) }
    var deletingStaffTarget by remember { mutableStateOf<JSONObject?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    // State Dialog Tes Pengiriman (WA, SMS, Email, Telegram)
    var testChannelTarget by remember { mutableStateOf<String?>(null) } // wa | sms | email | tg

    // State Database Status & Log Aktivitas
    var dbTablesList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var dbTotalTables by remember { mutableIntStateOf(0) }
    var dbTotalRows by remember { mutableIntStateOf(0) }
    var dbTotalSize by remember { mutableStateOf("0 B") }
    var logsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logSearchQuery by remember { mutableStateOf("") }
    var dbSubView by remember { mutableStateOf("dbstatus") } // dbstatus | logs

    fun updateConfigField(key: String, value: String) {
        val mutable = configMap.toMutableMap()
        mutable[key] = value
        configMap = mutable
    }

    fun loadTabData() {
        isLoading = true
        scope.launch {
            when (currentTab) {
                "general", "localisation", "maintenance" -> {
                    val res = ApiClient.request("admin_settings")
                    if (res.status && res.data != null) {
                        val map = mutableMapOf<String, String>()
                        val keys = res.data.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = res.data.optString(k, "")
                        }
                        configMap = map
                    }
                }
                "notifications" -> {
                    val res = ApiClient.request("admin_notifications_get")
                    if (res.status && res.data != null) {
                        val nObj = res.data.optJSONObject("notifications") ?: JSONObject()
                        val dObj = res.data.optJSONObject("defaults") ?: JSONObject()

                        val nMap = linkedMapOf<String, String>()
                        val dMap = linkedMapOf<String, String>()

                        val dKeys = dObj.keys()
                        while (dKeys.hasNext()) {
                            val k = dKeys.next()
                            dMap[k] = dObj.optString(k, "")
                            nMap[k] = nObj.optString(k, dObj.optString(k, ""))
                        }
                        val nKeys = nObj.keys()
                        while (nKeys.hasNext()) {
                            val k = nKeys.next()
                            if (!nMap.containsKey(k)) {
                                nMap[k] = nObj.optString(k, "")
                            }
                        }
                        defaultNotifMap = dMap
                        notifMap = nMap
                        if (nMap.isNotEmpty() && !nMap.containsKey(selectedNotifKey)) {
                            selectedNotifKey = nMap.keys.first()
                        }
                    }
                }
                "staff" -> {
                    val res = ApiClient.request("admin_staff_list", mapOf("search" to staffSearchQuery))
                    val arr = res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    staffList = list
                }
                "db_logs" -> {
                    val dbRes = ApiClient.request("admin_db_status")
                    if (dbRes.status && dbRes.data != null) {
                        dbTotalTables = dbRes.data.optInt("total_tables", 0)
                        dbTotalRows = dbRes.data.optInt("total_rows", 0)
                        dbTotalSize = dbRes.data.optString("total_size", "0 B")
                        val tArr = dbRes.data.optJSONArray("tables") ?: JSONArray()
                        val tList = mutableListOf<JSONObject>()
                        for (i in 0 until tArr.length()) tList.add(tArr.getJSONObject(i))
                        dbTablesList = tList
                    }

                    val lRes = ApiClient.request("admin_logs", mapOf("limit" to 150))
                    val lArr = lRes.dataArray ?: JSONArray()
                    val lList = mutableListOf<JSONObject>()
                    for (i in 0 until lArr.length()) lList.add(lArr.getJSONObject(i))
                    logsList = lList
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab != "shortcuts") {
            loadTabData()
        }
    }

    fun saveCurrentConfigSettings() {
        isSaving = true
        scope.launch {
            val res = ApiClient.request("admin_settings_save", configMap)
            isSaving = false
            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
            if (res.status) {
                val newComp = configMap["CompanyName"]?.trim() ?: ""
                val newCurr = configMap["currency_code"]?.trim() ?: ""
                if (newComp.isNotEmpty()) ApiClient.companyName = newComp
                if (newCurr.isNotEmpty()) ApiClient.currencyCode = newCurr
            }
        }
    }

    val filteredStaff = remember(staffList, staffSearchQuery) {
        val q = staffSearchQuery.trim().lowercase()
        if (q.isEmpty()) staffList
        else staffList.filter { s ->
            s.optString("username", "").lowercase().contains(q) ||
                s.optString("fullname", "").lowercase().contains(q) ||
                s.optString("user_type", "").lowercase().contains(q) ||
                s.optString("phone", "").lowercase().contains(q)
        }
    }

    val filteredLogs = remember(logsList, logSearchQuery) {
        val q = logSearchQuery.trim().lowercase()
        if (q.isEmpty()) logsList
        else logsList.filter { l ->
            l.optString("description", "").lowercase().contains(q) ||
                l.optString("userid", "").lowercase().contains(q) ||
                l.optString("date", "").lowercase().contains(q)
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
            listOf(
                "general" to "Umum & Gateway",
                "localisation" to "Lokalisasi & Label",
                "notifications" to "Template Notifikasi",
                "maintenance" to "Mode Maintenance",
                "staff" to "Staf & Password",
                "db_logs" to "Database & Log",
                "shortcuts" to "Shortcut Cepat"
            ).forEach { (tabKey, tabLabel) ->
                FilterChip(
                    selected = currentTab == tabKey,
                    onClick = { currentTab = tabKey },
                    label = { Text(tabLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && staffList.isEmpty() && dbTablesList.isEmpty() && notifMap.isEmpty() && currentTab != "general" && currentTab != "shortcuts") {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
            when (currentTab) {
                "general" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        // Tombol Cepat System Tools (Bersihkan Cache & Jalankan Cron)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val r = ApiClient.request("admin_system_tools", mapOf("tool" to "clear_cache"))
                                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bersihkan Cache", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val r = ApiClient.request("admin_system_tools", mapOf("tool" to "run_cron"))
                                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Jalankan Cron", fontSize = 12.sp)
                            }
                        }

                        // 1. Identitas Perusahaan & Struk
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Identitas Perusahaan & Struk (General)", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGoldPrimary)
                                OutlinedTextField(
                                    value = configMap["CompanyName"] ?: "",
                                    onValueChange = { updateConfigField("CompanyName", it) },
                                    label = { Text("Nama Perusahaan / WiFi (CompanyName)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["phone"] ?: "",
                                    onValueChange = { updateConfigField("phone", it) },
                                    label = { Text("Nomor Telepon / CS WhatsApp") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["address"] ?: "",
                                    onValueChange = { updateConfigField("address", it) },
                                    label = { Text("Alamat Perusahaan") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["note"] ?: "",
                                    onValueChange = { updateConfigField("note", it) },
                                    label = { Text("Catatan Kaki Struk / Invoice (Footer Note)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["payment_usings"] ?: "Cash,Bank Transfer",
                                    onValueChange = { updateConfigField("payment_usings", it) },
                                    label = { Text("Metode Pembayaran Kasir (Pisahkan dengan koma)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["payment_gateway"] ?: "",
                                    onValueChange = { updateConfigField("payment_gateway", it) },
                                    label = { Text("Payment Gateway Aktif") },
                                    placeholder = { Text("viaqris,tripay,duitku,xendit,paypal") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 2. Pengaturan Saldo, Pajak & Pengingat Tagihan
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Sistem Saldo, Pajak & Reminder Tagihan", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaCyanIncome)

                                SettingSwitchRow(
                                    title = "Aktifkan Fitur Saldo Pelanggan (enable_balance)",
                                    checked = (configMap["enable_balance"] ?: "yes").equals("yes", true),
                                    onCheckedChange = { updateConfigField("enable_balance", if (it) "yes" else "no") }
                                )

                                SettingSwitchRow(
                                    title = "Izinkan Transfer Saldo Antar Pelanggan",
                                    checked = (configMap["allow_balance_transfer"] ?: "yes").equals("yes", true),
                                    onCheckedChange = { updateConfigField("allow_balance_transfer", if (it) "yes" else "no") }
                                )

                                SettingSwitchRow(
                                    title = "Aktifkan Pajak Transaksi (enable_tax)",
                                    checked = (configMap["enable_tax"] ?: "no").equals("yes", true),
                                    onCheckedChange = { updateConfigField("enable_tax", if (it) "yes" else "no") }
                                )

                                if ((configMap["enable_tax"] ?: "no").equals("yes", true)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = configMap["tax_rate"] ?: "custom",
                                            onValueChange = { updateConfigField("tax_rate", it) },
                                            label = { Text("Mode Pajak (custom / angka)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = configMap["custom_tax_rate"] ?: "11",
                                            onValueChange = { updateConfigField("custom_tax_rate", it.replace("[^0-9.]".toRegex(), "")) },
                                            label = { Text("Tarif Pajak Custom (%)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text("Pengingat Jatuh Tempo Otomatis:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldLight)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "notification_reminder_7days" to "H-7 Hari",
                                        "notification_reminder_3days" to "H-3 Hari",
                                        "notification_reminder_1day" to "H-1 Hari"
                                    ).forEach { (rKey, rLbl) ->
                                        val isOn = (configMap[rKey] ?: "yes").equals("yes", true)
                                        FilterChip(
                                            selected = isOn,
                                            onClick = { updateConfigField(rKey, if (isOn) "no" else "yes") },
                                            label = { Text(rLbl, fontSize = 11.5.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Gateway Notifikasi (WA, SMS, Telegram, Email) + Tombol Test Langsung
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Gateway WhatsApp, SMS & Telegram", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGreenSuccess)

                                OutlinedTextField(
                                    value = configMap["wa_url"] ?: "",
                                    onValueChange = { updateConfigField("wa_url", it) },
                                    label = { Text("WhatsApp Gateway URL (wa_url)") },
                                    placeholder = { Text("https://api.wa.../send?phone=[number]&text=[text]") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = configMap["sms_url"] ?: "",
                                    onValueChange = { updateConfigField("sms_url", it) },
                                    label = { Text("SMS Gateway URL / MikroTik SMS") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = configMap["mikrotik_sms_command"] ?: "/tool sms send",
                                    onValueChange = { updateConfigField("mikrotik_sms_command", it) },
                                    label = { Text("Command SMS RouterOS (mikrotik_sms_command)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["telegram_bot"] ?: "",
                                        onValueChange = { updateConfigField("telegram_bot", it) },
                                        label = { Text("Telegram Bot Token", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["telegram_target_id"] ?: "",
                                        onValueChange = { updateConfigField("telegram_target_id", it) },
                                        label = { Text("Telegram Chat ID", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text("Uji Coba Pengiriman Gateway (Sesuai fitur Test di Web):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    FilledTonalButton(onClick = { testChannelTarget = "wa" }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes WA", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "tg" }) {
                                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes Telegram", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "sms" }) {
                                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes SMS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "email" }) {
                                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes Email", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = { saveCurrentConfigSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSaving) "MENYIMPAN..." else "SIMPAN PENGATURAN UMUM", fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaRedDanger),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keluar dari Akun Admin", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "localisation" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Format Regional, Mata Uang & Telepon", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGoldPrimary)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["timezone"] ?: "Asia/Jakarta",
                                        onValueChange = { updateConfigField("timezone", it) },
                                        label = { Text("Zona Waktu (timezone)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["date_format"] ?: "d M Y",
                                        onValueChange = { updateConfigField("date_format", it) },
                                        label = { Text("Format Tanggal") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["currency_code"] ?: "Rp.",
                                        onValueChange = { updateConfigField("currency_code", it) },
                                        label = { Text("Simbol Mata Uang") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["country_code_phone"] ?: "62",
                                        onValueChange = { updateConfigField("country_code_phone", it) },
                                        label = { Text("Kode Negara WA (62)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["dec_point"] ?: ",",
                                        onValueChange = { if (it.length <= 1) updateConfigField("dec_point", it) },
                                        label = { Text("Pemisah Desimal (,)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["thousands_sep"] ?: ".",
                                        onValueChange = { if (it.length <= 1) updateConfigField("thousands_sep", it) },
                                        label = { Text("Pemisah Ribuan (.)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["language"] ?: "indonesia",
                                        onValueChange = { updateConfigField("language", it) },
                                        label = { Text("Bahasa") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }
                        }

                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Label Nama Layanan (Tampil di Portal Pelanggan)", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaCyanIncome)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["hotspot_plan"] ?: "Hotspot Plans",
                                        onValueChange = { updateConfigField("hotspot_plan", it) },
                                        label = { Text("Label Paket Hotspot") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["pppoe_plan"] ?: "PPPOE Plans",
                                        onValueChange = { updateConfigField("pppoe_plan", it) },
                                        label = { Text("Label Paket PPPoE") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["vpn_plan"] ?: "VPN Plans",
                                        onValueChange = { updateConfigField("vpn_plan", it) },
                                        label = { Text("Label Paket VPN") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["radius_plan"] ?: "Radius Plans",
                                        onValueChange = { updateConfigField("radius_plan", it) },
                                        label = { Text("Label Paket Radius") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = { saveCurrentConfigSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSaving) "MENYIMPAN..." else "SIMPAN LOKALISASI", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                "notifications" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "Template Notifikasi Web (notifications.json)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = ArnetaGoldPrimary
                                )
                                Text(
                                    text = "Pilih jenis pesan di bawah untuk mengedit template WhatsApp/SMS yang juga dipakai oleh fitur Kirim Ulang WA:",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                val notifKeysList = remember(notifMap) { notifMap.keys.toList() }
                                if (notifKeysList.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        notifKeysList.forEach { k ->
                                            FilterChip(
                                                selected = selectedNotifKey == k,
                                                onClick = { selectedNotifKey = k },
                                                label = { Text(k, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = notifMap[selectedNotifKey] ?: "",
                                        onValueChange = { newText ->
                                            val mutable = notifMap.toMutableMap()
                                            mutable[selectedNotifKey] = newText
                                            notifMap = mutable
                                        },
                                        label = { Text("Isi Template: $selectedNotifKey") },
                                        minLines = 8,
                                        maxLines = 15,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(
                                        text = "Tag variabel yang didukung: [[company_name]], [[name]], [[user_name]], [[user_password]], [[plan_name]], [[plan_price]], [[invoice]], [[date]], [[expired_date]], [[current_balance]], [[payment_gateway]], [[payment_channel]], [[footer]]",
                                        fontSize = 10.5.sp,
                                        color = ArnetaCyanIncome
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val defVal = defaultNotifMap[selectedNotifKey]
                                                if (defVal != null) {
                                                    val mutable = notifMap.toMutableMap()
                                                    mutable[selectedNotifKey] = defVal
                                                    notifMap = mutable
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset Default", fontSize = 12.sp)
                                        }

                                        Button(
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                                            onClick = {
                                                isSaving = true
                                                scope.launch {
                                                    val res = ApiClient.request("admin_notifications_save", notifMap)
                                                    isSaving = false
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isSaving) "Menyimpan..." else "Simpan Semua Template", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Text("Memuat file notifications.json...", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                "maintenance" -> {
                    val isMaintOn = (configMap["maintenance_mode"] ?: "0") == "1" ||
                        (configMap["maintenance_mode"] ?: "").equals("yes", true)
                    val isForceLogout = (configMap["maintenance_mode_logout"] ?: "0") == "1" ||
                        (configMap["maintenance_mode_logout"] ?: "").equals("yes", true)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Mode Pemeliharaan Sistem (Maintenance Mode)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = ArnetaGoldPrimary)
                                Text(
                                    text = "Saat diaktifkan, portal & aplikasi pelanggan akan menampilkan halaman pemberitahuan pemeliharaan jaringan.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                SettingSwitchRow(
                                    title = "Aktifkan Maintenance Mode",
                                    checked = isMaintOn,
                                    onCheckedChange = { updateConfigField("maintenance_mode", if (it) "1" else "0") }
                                )

                                SettingSwitchRow(
                                    title = "Paksa Logout Sesi Pelanggan (Force Logout)",
                                    checked = isForceLogout,
                                    onCheckedChange = { updateConfigField("maintenance_mode_logout", if (it) "1" else "0") }
                                )

                                OutlinedTextField(
                                    value = configMap["maintenance_date"] ?: "",
                                    onValueChange = { updateConfigField("maintenance_date", it) },
                                    label = { Text("Estimasi Tanggal Selesai (YYYY-MM-DD HH:MM)") },
                                    placeholder = { Text("2026-11-10 18:00") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    enabled = !isSaving,
                                    onClick = { saveCurrentConfigSettings() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isSaving) "MENYIMPAN..." else "SIMPAN PENGATURAN MAINTENANCE", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                "staff" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                editingStaff = null
                                showStaffDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Staf", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { showChangePasswordDialog = true }
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ganti Password Saya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = staffSearchQuery,
                        onValueChange = { staffSearchQuery = it },
                        placeholder = { Text("Cari username, nama lengkap, atau role staf...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                        trailingIcon = {
                            if (staffSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { staffSearchQuery = "" }) {
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
                        items(filteredStaff) { u ->
                            val uType = u.optString("user_type", "Agent")
                            val isActive = u.optString("status", "Active").equals("Active", true)
                            val rootName = u.optString("root_name", "")

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        editingStaff = u
                                        showStaffDialog = true
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
                                            Text(u.optString("fullname"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Surface(
                                                color = when (uType) {
                                                    "SuperAdmin" -> ArnetaGoldPrimary.copy(alpha = 0.22f)
                                                    "Admin" -> ArnetaCyanIncome.copy(alpha = 0.22f)
                                                    else -> ArnetaIndigoAccent.copy(alpha = 0.22f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = uType,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = when (uType) {
                                                        "SuperAdmin" -> ArnetaGoldPrimary
                                                        "Admin" -> ArnetaCyanIncome
                                                        else -> ArnetaGoldLight
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (!isActive) {
                                                Surface(
                                                    color = ArnetaRedDanger.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "INACTIVE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ArnetaRedDanger,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "@${u.optString("username")} • WA: ${u.optString("phone").ifEmpty { "-" }}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        )
                                        if (uType == "Sales" && rootName.isNotEmpty()) {
                                            Text(
                                                text = "Upline Agent: $rootName",
                                                fontSize = 11.sp,
                                                color = ArnetaCyanIncome
                                            )
                                        }
                                        Text(
                                            text = "Login Terakhir: ${u.optString("last_login").ifEmpty { "Belum pernah" }}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingStaff = u
                                                showStaffDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Staf", tint = ArnetaGoldPrimary)
                                        }
                                        IconButton(
                                            onClick = { deletingStaffTarget = u }
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Staf", tint = ArnetaRedDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "db_logs" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = dbSubView == "dbstatus",
                                onClick = { dbSubView = "dbstatus" },
                                label = { Text("Status Database ($dbTotalTables Tabel)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = dbSubView == "logs",
                                onClick = { dbSubView = "logs" },
                                label = { Text("Log Sistem (${logsList.size})", fontSize = 12.sp) }
                            )
                        }

                        IconButton(onClick = { loadTabData() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (dbSubView == "dbstatus") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArnetaIndigoAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("Ringkasan Database MySQL/MariaDB", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
                                    Text(
                                        text = "$dbTotalTables Tabel • $dbTotalRows Baris • $dbTotalSize",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = ArnetaGoldLight
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val bRes = ApiClient.request("admin_db_backup")
                                            Toast.makeText(
                                                context,
                                                if (bRes.status) "Backup Database berhasil diekspor (${bRes.data?.optString("filename")})" else bRes.message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Backup", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 95.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(dbTablesList) { tbl ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = tbl.optString("name"),
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Engine: ${tbl.optString("engine", "InnoDB")} • Ukuran: ${tbl.optString("size_label", "0 B")}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                        Surface(
                                            color = ArnetaCyanIncome.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${tbl.optInt("rows", 0)} rows",
                                                color = ArnetaCyanIncome,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = logSearchQuery,
                            onValueChange = { logSearchQuery = it },
                            placeholder = { Text("Cari isi log aktivitas...", fontSize = 12.5.sp) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 95.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLogs) { l ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = l.optString("type", "Admin"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ArnetaGoldPrimary
                                            )
                                            Text(
                                                text = "${l.optString("date")} • ${l.optString("ip")}",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(l.optString("description"), fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                "shortcuts" -> {
                    val allShortcuts = listOf(
                        "add_customer" to "Add Customer Baru",
                        "recharge_plan" to "Isi Ulang Paket",
                        "deposit_balance" to "Isi Ulang Saldo",
                        "generate_voucher" to "Generate & Cetak Voucher",
                        "online_users" to "User Online & Kick Sesi",
                        "audit_qris" to "Audit Payment Gateway",
                        "live_monitor" to "Live Speed MikroTik",
                        "broadcast_wa" to "Broadcast WhatsApp",
                        "webview_admin" to "Web Admin Panel"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Text(
                            text = "Pilih menu yang ditampilkan saat tombol petir di tengah navigasi bawah ditekan:",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        allShortcuts.forEach { (sKey, sTitle) ->
                            val checked = selectedShortcuts.contains(sKey)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = selectedShortcuts.toMutableSet()
                                        if (checked) next.remove(sKey) else next.add(sKey)
                                        selectedShortcuts = next
                                        ApiClient.customQuickShortcuts = next
                                        onCustomShortcutsChanged()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(sTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            val next = selectedShortcuts.toMutableSet()
                                            if (isChecked) next.add(sKey) else next.remove(sKey)
                                            selectedShortcuts = next
                                            ApiClient.customQuickShortcuts = next
                                            onCustomShortcutsChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStaffDialog) {
        val agentsOnly = remember(staffList) {
            staffList.filter { it.optString("user_type").equals("Agent", true) }
        }
        StaffFormDialog(
            initial = editingStaff,
            agentsList = agentsOnly,
            onDismiss = {
                showStaffDialog = false
                editingStaff = null
            },
            onSaved = {
                showStaffDialog = false
                editingStaff = null
                loadTabData()
            }
        )
    }

    if (deletingStaffTarget != null) {
        val target = deletingStaffTarget!!
        AlertDialog(
            onDismissRequest = { deletingStaffTarget = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Hapus Akun Staf @${target.optString("username")}?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus akun staf ${target.optString("fullname")} (${target.optString("user_type")})?",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingStaffTarget = null
                        scope.launch {
                            val dRes = ApiClient.request("admin_staff_delete", mapOf("id" to target.optInt("id")))
                            Toast.makeText(context, dRes.message, Toast.LENGTH_LONG).show()
                            if (dRes.status) loadTabData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaRedDanger, contentColor = Color.White)
                ) {
                    Text("Ya, Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deletingStaffTarget = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    if (testChannelTarget != null) {
        TestNotificationDialog(
            channel = testChannelTarget!!,
            onDismiss = { testChannelTarget = null }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun StaffFormDialog(
    initial: JSONObject?,
    agentsList: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var fullname by remember { mutableStateOf(initial?.optString("fullname") ?: "") }
    var password by remember { mutableStateOf("") }
    var cpassword by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf(initial?.optString("user_type")?.ifEmpty { null } ?: "Agent") }
    var rootAgentId by remember {
        mutableIntStateOf(
            initial?.optInt("root", 0)?.takeIf { it > 0 } ?: (agentsList.firstOrNull()?.optInt("id") ?: 0)
        )
    }
    var phone by remember { mutableStateOf(initial?.optString("phone") ?: "") }
    var email by remember { mutableStateOf(initial?.optString("email") ?: "") }
    var city by remember { mutableStateOf(initial?.optString("city") ?: "") }
    var subdistrict by remember { mutableStateOf(initial?.optString("subdistrict") ?: "") }
    var ward by remember { mutableStateOf(initial?.optString("ward") ?: "") }
    var status by remember { mutableStateOf(initial?.optString("status")?.ifEmpty { null } ?: "Active") }
    var sendNotif by remember { mutableStateOf("") } // "" | "wa" | "sms"
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Staf: @$username" else "Tambah Akun Staf Baru",
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
                Text("Level / Role Akun (user_type):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("SuperAdmin", "Admin", "Report", "Agent", "Sales").forEach { role ->
                        FilterChip(
                            selected = userType.equals(role, true),
                            onClick = { userType = role },
                            label = { Text(role, fontSize = 11.5.sp) }
                        )
                    }
                }

                if (userType.equals("Sales", true) && agentsList.isNotEmpty()) {
                    CompactDropdownSelector(
                        label = "Pilih Upline Agent (Induk Sales)",
                        selectedValue = rootAgentId.toString(),
                        options = agentsList.map { ag ->
                            ag.optInt("id").toString() to "${ag.optString("fullname")} (@${ag.optString("username")})"
                        },
                        icon = Icons.Filled.SupervisorAccount,
                        onValueSelected = { rootAgentId = it.toIntOrNull() ?: rootAgentId },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Active" to "Status: Active", "Inactive" to "Status: Inactive").forEach { (stKey, stLbl) ->
                            FilterChip(
                                selected = status.equals(stKey, true),
                                onClick = { status = stKey },
                                label = { Text(stLbl, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (3 - 45 karakter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullname,
                    onValueChange = { fullname = it },
                    label = { Text("Nama Lengkap (3 - 45 karakter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isEdit) "Password Baru (Opsional)" else "Password (Min. 6)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cpassword,
                        onValueChange = { cpassword = it },
                        label = { Text("Ulangi Password", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("No. WhatsApp", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Kota", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subdistrict,
                        onValueChange = { subdistrict = it },
                        label = { Text("Kecamatan", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ward,
                        onValueChange = { ward = it },
                        label = { Text("Kelurahan", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isEdit) {
                    Text("Kirim Detail Akun ke Staf:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "" to "Jangan Kirim",
                            "wa" to "Via WhatsApp",
                            "sms" to "Via SMS"
                        ).forEach { (nKey, nLbl) ->
                            FilterChip(
                                selected = sendNotif == nKey,
                                onClick = { sendNotif = nKey },
                                label = { Text(nLbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = !isSaving && username.trim().length >= 3 && fullname.trim().length >= 3 &&
                (if (isEdit) (password.isEmpty() || (password.length >= 6 && password == cpassword)) else password.length >= 6)

            Button(
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_staff_save",
                            mapOf(
                                "id" to (initial?.optInt("id") ?: 0),
                                "username" to username.trim(),
                                "fullname" to fullname.trim(),
                                "password" to password.trim(),
                                "cpassword" to cpassword.trim().ifEmpty { password.trim() },
                                "user_type" to userType,
                                "root" to rootAgentId,
                                "phone" to phone.trim(),
                                "email" to email.trim(),
                                "city" to city.trim(),
                                "subdistrict" to subdistrict.trim(),
                                "ward" to ward.trim(),
                                "status" to status,
                                "send_notif" to sendNotif
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onSaved()
                    }
                }
            ) {
                Text(if (isSaving) "Menyimpan..." else "Simpan Staf", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmNewPass by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti Password Akun Saya", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sesuai aturan sistem web, password baru harus terdiri dari 6 hingga 14 karakter.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = currentPass,
                    onValueChange = { currentPass = it },
                    label = { Text("Password Saat Ini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Password Baru (6 - 14 Karakter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmNewPass,
                    onValueChange = { confirmNewPass = it },
                    label = { Text("Konfirmasi Password Baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && currentPass.isNotBlank() && newPass.length in 6..14 && newPass == confirmNewPass,
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_change_password",
                            mapOf(
                                "password" to currentPass,
                                "npass" to newPass,
                                "cnpass" to confirmNewPass
                            )
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onDismiss()
                    }
                }
            ) {
                Text(if (isSaving) "Memproses..." else "Ubah Password", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun TestNotificationDialog(
    channel: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetInput by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    val channelTitle = when (channel) {
        "wa" -> "WhatsApp Gateway"
        "sms" -> "SMS Gateway"
        "email" -> "SMTP / Email"
        else -> "Bot Telegram"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tes Kirim $channelTitle", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (channel == "tg") {
                    Text(
                        text = "Pesan uji coba 'PHPNuxBill Test Telegram' akan langsung dikirim ke Chat ID Telegram yang tersimpan di sistem.",
                        fontSize = 12.sp
                    )
                } else {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = {
                            Text(if (channel == "email") "Alamat Email Tujuan Tes" else "Nomor HP / WA Tujuan Tes")
                        },
                        placeholder = {
                            Text(if (channel == "email") "admin@arneta.biz.id" else "08123456789")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isTesting && (channel == "tg" || targetInput.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                onClick = {
                    isTesting = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_test_notif",
                            mapOf("channel" to channel, "target" to targetInput.trim())
                        )
                        isTesting = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onDismiss()
                    }
                }
            ) {
                Text(if (isTesting) "Mengirim..." else "Kirim Tes Sekarang", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
