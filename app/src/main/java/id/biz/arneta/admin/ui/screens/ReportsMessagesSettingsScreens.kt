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
 * 1. LAYAR MENU HUB LENGKAP
 */
@Composable
fun AdminMenuHubScreen(
    unpaidQrisCount: Int,
    onOpenSubScreen: (String) -> Unit,
    onOpenInAppWeb: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val menuItems = listOf(
        HubMenuItem(
            key = "network",
            title = "MikroTik Monitor & Jaringan",
            subtitle = "Trafik interface, health, user online, log & router",
            icon = Icons.Filled.Router,
            accent = ArnetaCyanIncome
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
            title = "Inventaris & Aset Jaringan",
            subtitle = "Data ODP, ODC, ONT/Modem, kabel & nilai investasi",
            icon = Icons.Filled.Inventory2,
            accent = ArnetaPurpleUsers
        ),
        HubMenuItem(
            key = "messages",
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
            subtitle = "Konfigurasi aplikasi, akun staf, cronjob & bersihkan cache",
            icon = Icons.Filled.Settings,
            accent = ArnetaIndigoAccent
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

                    OutlinedButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaRedDanger)
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keluar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    .clickable { onOpenSubScreen(item.key) }
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
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Panel Web Internal (SSO Otomatis)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "dashboard" to "Dasbor Web",
                    "pluginmanager" to "Plugin Manager",
                    "paymentgateway" to "Payment Gateway",
                    "radius" to "FreeRADIUS"
                ).forEach { (route, label) ->
                    FilledTonalButton(
                        onClick = { onOpenInAppWeb(route, label) }
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
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
fun AdminReportsAndQrisScreen(
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(initialTab) }

    // State Tab 0: Laporan Keuangan
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

    // State Tab 1: Audit Payment Gateway (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
    var pgStatusFilter by remember { mutableStateOf("All") } // All | 1 | 2 | 3 | 4
    var pgGatewayFilter by remember { mutableStateOf("All") } // All | viaqris | tripay | duitku | xendit | paypal
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
            // =================================================================
            // TAB 0: LAPORAN TRANSAKSI & REKAP PER METODE / GATEWAY
            // =================================================================
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

            // Kartu Ringkasan Total Pendapatan & Breakdown Metode Pembayaran
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
                            modifier = Modifier.fillMaxWidth()
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
            // =================================================================
            // TAB 1: AUDIT SEMUA PAYMENT GATEWAY (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
            // =================================================================
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

            // Baris Filter Gateway (Semua, ViaQRIS, Tripay, Duitku, Xendit, PayPal)
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

            // Baris Filter Status Tagihan
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

                                // Jika ada Kode Bayar / Nomor Virtual Account (Tripay / Duitku)
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

                                // Tombol Aksi untuk Tagihan Berstatus Menunggu Pembayaran (status == 1)
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
}

/**
 * 3. LAYAR KIRIM PESAN & BROADCAST WHATSAPP / SMS / INBOX
 */
@Composable
fun AdminMessagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("single") } // single | broadcast
    var selectedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var targetGroup by remember { mutableStateOf("all") } // all | active | expired | hotspot | pppoe
    var viaChannel by remember { mutableStateOf("wa") } // wa | sms | inbox | both
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
 * 4. LAYAR TEMPLATE & HALAMAN STATIS
 */
@Composable
fun AdminPagesScreen() {
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
 * 5. LAYAR PENGATURAN SISTEM, STAF ADMIN, SYSTEM TOOLS & LOG AKTIVITAS
 */
@Composable
fun AdminSettingsAndLogsScreen(
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(initialTab) }

    // State Pengaturan Umum
    var companyName by remember { mutableStateOf(ApiClient.companyName) }
    var companyPhone by remember { mutableStateOf("") }
    var companyAddress by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf(ApiClient.currencyCode) }
    var paymentUsings by remember { mutableStateOf("Cash,Transfer Bank") }
    var activeGateways by remember { mutableStateOf("") }
    var enableBalance by remember { mutableStateOf("yes") }
    var invoiceNote by remember { mutableStateOf("") }
    var isSavingSettings by remember { mutableStateOf(false) }

    // State Staf & Log
    var staffList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }

    fun loadTabData() {
        isLoading = true
        scope.launch {
            when (currentTab) {
                0 -> {
                    val res = ApiClient.request("admin_settings")
                    if (res.status && res.data != null) {
                        companyName = res.data.optString("CompanyName", companyName)
                        companyPhone = res.data.optString("phone", "")
                        companyAddress = res.data.optString("address", "")
                        currencyCode = res.data.optString("currency_code", currencyCode)
                        paymentUsings = res.data.optString("payment_usings", "Cash")
                        activeGateways = res.data.optString("payment_gateway", "")
                        enableBalance = res.data.optString("enable_balance", "yes")
                        invoiceNote = res.data.optString("note", "")
                    }
                }
                1 -> {
                    val res = ApiClient.request("admin_staff_list")
                    val arr = res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    staffList = list
                }
                2 -> {
                    val res = ApiClient.request("admin_logs")
                    val arr = res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    logsList = list
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentTab) {
        loadTabData()
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
                0 to "Pengaturan Umum",
                1 to "Manajemen Staf",
                2 to "Log Aktivitas"
            ).forEach { (idx, title) ->
                FilterChip(
                    selected = currentTab == idx,
                    onClick = { currentTab = idx },
                    label = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (currentTab) {
            0 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    // System Tools Cepat (Bersihkan Cache & Jalankan Cron)
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

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Nama Perusahaan / WiFi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = companyPhone,
                        onValueChange = { companyPhone = it },
                        label = { Text("Nomor Telepon / CS WhatsApp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = companyAddress,
                        onValueChange = { companyAddress = it },
                        label = { Text("Alamat Perusahaan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currencyCode,
                            onValueChange = { currencyCode = it },
                            label = { Text("Mata Uang") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = enableBalance,
                            onValueChange = { enableBalance = it },
                            label = { Text("Fitur Saldo (yes/no)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = paymentUsings,
                        onValueChange = { paymentUsings = it },
                        label = { Text("Metode Pembayaran Kasir (Pisahkan dengan koma)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = activeGateways,
                        onValueChange = { activeGateways = it },
                        label = { Text("Payment Gateway Aktif") },
                        placeholder = { Text("viaqris,tripay,duitku,xendit,paypal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoiceNote,
                        onValueChange = { invoiceNote = it },
                        label = { Text("Catatan Kaki Struk / Invoice") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !isSavingSettings,
                        onClick = {
                            isSavingSettings = true
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_settings_save",
                                    mapOf(
                                        "settings" to mapOf(
                                            "CompanyName" to companyName.trim(),
                                            "phone" to companyPhone.trim(),
                                            "address" to companyAddress.trim(),
                                            "currency_code" to currencyCode.trim(),
                                            "payment_usings" to paymentUsings.trim(),
                                            "payment_gateway" to activeGateways.trim(),
                                            "enable_balance" to enableBalance.trim(),
                                            "note" to invoiceNote.trim()
                                        )
                                    )
                                )
                                isSavingSettings = false
                                if (res.status) {
                                    ApiClient.companyName = companyName.trim()
                                    ApiClient.currencyCode = currencyCode.trim()
                                }
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
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
                        Text(if (isSavingSettings) "Menyimpan..." else "Simpan Pengaturan", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            1 -> {
                Button(
                    onClick = { showAddStaffDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Staf / Agen", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(staffList) { st ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    Text(st.optString("fullname"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "@${st.optString("username")} • ${st.optString("user_type")} • Login: ${st.optString("last_login", "-")}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val dRes = ApiClient.request("admin_staff_delete", mapOf("id" to st.optInt("id")))
                                            Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                            if (dRes.status) loadTabData()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logsList) { lg ->
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
                                        text = lg.optString("type", "Admin"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaGoldPrimary
                                    )
                                    Text(
                                        text = "${lg.optString("date")} • ${lg.optString("ip")}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(lg.optString("description"), fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStaffDialog) {
        var sUser by remember { mutableStateOf("") }
        var sName by remember { mutableStateOf("") }
        var sPass by remember { mutableStateOf("") }
        var sRole by remember { mutableStateOf("Agent") }

        AlertDialog(
            onDismissRequest = { showAddStaffDialog = false },
            title = { Text("Tambah Staf / Agen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sUser, onValueChange = { sUser = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("Nama Lengkap") }, singleLine = true)
                    OutlinedTextField(value = sPass, onValueChange = { sPass = it }, label = { Text("Password") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Admin", "Agent", "Sales").forEach { r ->
                            FilterChip(
                                selected = sRole == r,
                                onClick = { sRole = r },
                                label = { Text(r, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val res = ApiClient.request(
                                "admin_staff_save",
                                mapOf("username" to sUser, "fullname" to sName, "password" to sPass, "user_type" to sRole)
                            )
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            if (res.status) {
                                showAddStaffDialog = false
                                loadTabData()
                            }
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStaffDialog = false }) { Text("Batal") }
            }
        }
    }
}
