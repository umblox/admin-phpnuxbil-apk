package id.biz.arneta.admin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HubMenuItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val badgeCount: Int = 0
)

/**
 * 1. MENU HUB (Pusat Navigasi Seluruh Fitur Admin PHPNuxBill)
 */
@Composable
fun AdminMoreMenuScreen(
    unpaidQrisCount: Int = 0,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val menuGroups = listOf(
        "Keuangan & Payment Gateway" to listOf(
            HubMenuItem(
                route = "reports",
                title = "Laporan Keuangan",
                subtitle = "Rekap pendapatan harian, bulanan & per metode bayar",
                icon = Icons.Filled.Assessment,
                tint = ArnetaCyanIncome
            ),
            HubMenuItem(
                route = "payment_gateway",
                title = "Audit Payment Gateway",
                subtitle = "Pantau transaksi ViaQRIS, Tripay, Duitku, Xendit & PayPal",
                icon = Icons.Filled.Payments,
                tint = ArnetaGoldPrimary,
                badgeCount = unpaidQrisCount
            )
        ),
        "Jaringan, Paket & Infrastruktur" to listOf(
            HubMenuItem(
                route = "online_users",
                title = "MikroTik Monitor & User Online",
                subtitle = "Trafik interface live, sinyal wireless, log & putus sesi",
                icon = Icons.Filled.Router,
                tint = ArnetaGreenSuccess
            ),
            HubMenuItem(
                route = "plans",
                title = "Paket Internet & Bandwidth",
                subtitle = "Kelola paket Hotspot, PPPoE, Saldo & limit kecepatan",
                icon = Icons.Filled.Speed,
                tint = ArnetaOrangeActive
            ),
            HubMenuItem(
                route = "routers",
                title = "Router MikroTik & IP Pool",
                subtitle = "Konfigurasi router API, koordinat coverage & rentang IP",
                icon = Icons.Filled.Dns,
                tint = ArnetaCyanIncome
            ),
            HubMenuItem(
                route = "map",
                title = "Peta Lokasi Pelanggan & ODP",
                subtitle = "Pemetaan titik koordinat pelanggan, router & jangkauan",
                icon = Icons.Filled.Map,
                tint = ArnetaGreenSuccess
            ),
            HubMenuItem(
                route = "assets",
                title = "Inventaris Aset Jaringan",
                subtitle = "Kelola data ODP, ODC, ONT/Modem, kabel & nilai aset",
                icon = Icons.Filled.Inventory2,
                tint = ArnetaGoldLight
            )
        ),
        "Komunikasi, Halaman & Sistem" to listOf(
            HubMenuItem(
                route = "messages",
                title = "Kirim Pesan & Broadcast",
                subtitle = "Kirim notifikasi WhatsApp, SMS & Inbox ke pelanggan",
                icon = Icons.Filled.Send,
                tint = ArnetaGreenSuccess
            ),
            HubMenuItem(
                route = "pages",
                title = "Editor Halaman Statis",
                subtitle = "Ubah template Voucher, Info Tagihan & Pengumuman",
                icon = Icons.Filled.Description,
                tint = ArnetaCyanIncome
            ),
            HubMenuItem(
                route = "webview",
                title = "Panel Web Admin Internal",
                subtitle = "Buka Plugin Manager & pengaturan lanjutan dengan SSO",
                icon = Icons.Filled.Language,
                tint = ArnetaGoldPrimary
            ),
            HubMenuItem(
                route = "settings",
                title = "Pengaturan, Staf & Log Sistem",
                subtitle = "Konfigurasi aplikasi, kelola staf, bersihkan cache & cron",
                icon = Icons.Filled.Settings,
                tint = ArnetaCoralCustomer
            )
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Kartu Profil Admin Aktif
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ArnetaGoldPrimary.copy(alpha = 0.2f))
                                .border(1.5.dp, ArnetaGoldPrimary, CircleShape)
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = ArnetaGoldPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = ApiClient.adminName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "@${ApiClient.adminUsername} • ${ApiClient.adminRole}",
                                fontSize = 12.sp,
                                color = ArnetaGoldLight
                            )
                            Text(
                                text = ApiClient.baseUrl.replace("https://", "").replace("http://", ""),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaRedDanger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keluar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        menuGroups.forEach { (groupTitle, items) ->
            item {
                Text(
                    text = groupTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ArnetaGoldPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(items) { m ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(m.route) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(m.tint.copy(alpha = 0.15f))
                            ) {
                                Icon(m.icon, contentDescription = null, tint = m.tint, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = m.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    if (m.badgeCount > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = ArnetaRedDanger,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "${m.badgeCount} Pending",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = m.subtitle,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. LAYAR LAPORAN KEUANGAN & AUDIT SEMUA PAYMENT GATEWAY (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
 */
@Composable
fun AdminReportsScreen(
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(initialTab) }

    // State Tab 0: Laporan Transaksi (tbl_transactions)
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val firstDayMonthStr = remember { SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date()) }

    var startDate by remember { mutableStateOf(firstDayMonthStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var reportSearch by remember { mutableStateOf("") }
    var selectedReportMethod by remember { mutableStateOf("All") }
    var isLoadingReport by remember { mutableStateOf(false) }
    var totalIncome by remember { mutableDoubleStateOf(0.0) }
    var totalTrxCount by remember { mutableIntStateOf(0) }
    var gatewayBreakdown by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var transactionsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    // State Tab 1: Audit Payment Gateway (tbl_payment_gateway: ViaQRIS, Tripay, Duitku, Xendit, PayPal)
    var pgStatusFilter by remember { mutableStateOf("all") } // all | 1 | 2 | 3 | 4
    var pgGatewayFilter by remember { mutableStateOf("All") } // All | viaqris | tripay | duitku | xendit | paypal
    var pgSearch by remember { mutableStateOf("") }
    var isLoadingPg by remember { mutableStateOf(false) }
    var pgItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var pgGatewayStats by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var availableGateways by remember { mutableStateOf(listOf("All", "viaqris", "tripay", "duitku", "xendit", "paypal")) }
    var selectedPgDetail by remember { mutableStateOf<JSONObject?>(null) }

    fun loadFinancialReports() {
        isLoadingReport = true
        scope.launch {
            val res = ApiClient.request(
                "admin_reports",
                mapOf(
                    "start_date" to startDate.trim(),
                    "end_date" to endDate.trim(),
                    "search" to reportSearch.trim(),
                    "gateway" to if (selectedReportMethod == "All") "" else selectedReportMethod,
                    "limit" to 200
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
                transactionsList = tList
            }
            isLoadingReport = false
        }
    }

    fun loadPaymentGatewayAudit() {
        isLoadingPg = true
        scope.launch {
            val res = ApiClient.request(
                "admin_payment_gateways",
                mapOf(
                    "sub_action" to "list",
                    "status" to pgStatusFilter,
                    "gateway" to if (pgGatewayFilter == "All") "" else pgGatewayFilter,
                    "search" to pgSearch.trim(),
                    "limit" to 100
                )
            )
            if (res.status && res.data != null) {
                val arr = res.data.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                pgItems = list

                val sum = res.data.optJSONObject("summary")
                val byGwArr = sum?.optJSONArray("by_gateway") ?: JSONArray()
                val stList = mutableListOf<JSONObject>()
                for (i in 0 until byGwArr.length()) stList.add(byGwArr.getJSONObject(i))
                pgGatewayStats = stList

                val gwArr = res.data.optJSONArray("gateways")
                if (gwArr != null && gwArr.length() > 0) {
                    val gList = mutableListOf("All")
                    for (i in 0 until gwArr.length()) {
                        val g = gwArr.optString(i).trim()
                        if (g.isNotEmpty() && gList.none { it.equals(g, ignoreCase = true) }) {
                            gList.add(g)
                        }
                    }
                    availableGateways = gList
                }
            }
            isLoadingPg = false
        }
    }

    LaunchedEffect(currentTab, selectedReportMethod) {
        if (currentTab == 0) loadFinancialReports()
    }

    LaunchedEffect(currentTab, pgStatusFilter, pgGatewayFilter) {
        if (currentTab == 1) loadPaymentGatewayAudit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Tab Switcher: Laporan Keuangan vs Audit Payment Gateway
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                label = { Text("Laporan Keuangan", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentTab == 1,
                onClick = { currentTab = 1 },
                label = { Text("Audit Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentTab == 0) {
            // =================================================================
            // TAB 0: LAPORAN KEUANGAN & REKAP METODE PEMBAYARAN
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
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
                        Text(
                            text = if (selectedReportMethod == "All") "Total Pendapatan Bersih"
                            else "Pendapatan Metode: $selectedReportMethod",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Text(
                            text = formatRupiah(totalIncome),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )
                        Text(
                            text = "$totalTrxCount transaksi tercatat pada periode ini",
                            fontSize = 11.sp,
                            color = ArnetaCyanIncome
                        )
                    }
                    IconButton(onClick = { loadFinancialReports() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Baris Filter Tanggal & Tombol Cari
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Dari (YYYY-MM-DD)", fontSize = 10.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("Sampai (YYYY-MM-DD)", fontSize = 10.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { loadFinancialReports() },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Filter")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Chip Rekap Per Metode / Payment Gateway (Bisa diklik untuk filter)
            if (gatewayBreakdown.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedReportMethod == "All",
                        onClick = { selectedReportMethod = "All" },
                        label = { Text("Semua Metode", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    gatewayBreakdown.forEach { bd ->
                        val gwKey = bd.optString("gateway", "LAINNYA")
                        val cnt = bd.optInt("count", 0)
                        val tot = bd.optDouble("total", 0.0)
                        FilterChip(
                            selected = selectedReportMethod.equals(gwKey, ignoreCase = true),
                            onClick = {
                                selectedReportMethod = if (selectedReportMethod.equals(gwKey, ignoreCase = true)) "All" else gwKey
                            },
                            label = {
                                Text(
                                    text = "$gwKey ($cnt) • ${formatRupiah(tot)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            OutlinedTextField(
                value = reportSearch,
                onValueChange = { reportSearch = it },
                placeholder = { Text("Cari invoice, username, paket, atau metode...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (reportSearch.isNotEmpty()) {
                        IconButton(onClick = {
                            reportSearch = ""
                            loadFinancialReports()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
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
            } else if (transactionsList.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada transaksi pada filter ini.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transactionsList) { trx ->
                        val inv = trx.optString("invoice").ifEmpty { "INV-${trx.optInt("id")}" }
                        val price = trx.optDouble("price", 0.0)
                        val trxDate = "${trx.optString("recharged_on")} ${trx.optString("recharged_time")}".trim()
                        val expDate = "${trx.optString("expiration")} ${trx.optString("time")}".trim()
                        val methodStr = trx.optString("method", "-")

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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = trx.optString("username"),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.5.sp
                                            )
                                            Surface(
                                                color = ArnetaCyanIncome.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = inv,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ArnetaCyanIncome,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${trx.optString("plan_name")} • $methodStr",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(price),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tgl Trx: $trxDate • Exp: $expDate (${trx.optString("routers")})",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
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
                                                        "Pelanggan" to trx.optString("username"),
                                                        "Paket" to trx.optString("plan_name"),
                                                        "Harga" to formatRupiah(price),
                                                        "Metode" to methodStr,
                                                        "Tgl Trx" to trxDate,
                                                        "Aktif s/d" to expDate,
                                                        "Router" to trx.optString("routers")
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
                                            val waMsg = buildString {
                                                appendLine("*STRUK TRANSAKSI ${ApiClient.companyName.uppercase()}*")
                                                appendLine("--------------------------------")
                                                appendLine("No. Invoice : $inv")
                                                appendLine("Pelanggan   : ${trx.optString("username")}")
                                                appendLine("Paket       : ${trx.optString("plan_name")}")
                                                appendLine("Harga       : ${formatRupiah(price)}")
                                                appendLine("Metode      : $methodStr")
                                                appendLine("Tgl Trx     : $trxDate")
                                                appendLine("Aktif s/d   : $expDate")
                                                appendLine("Router      : ${trx.optString("routers")}")
                                            }
                                            BluetoothPrinterHelper.shareToWhatsApp(context, waMsg, "")
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bagikan WA", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // =================================================================
            // TAB 1: AUDIT MULTI-PAYMENT GATEWAY (ViaQRIS, Tripay, Duitku, Xendit, PayPal)
            // =================================================================
            if (pgGatewayStats.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    pgGatewayStats.forEach { st ->
                        val gwName = st.optString("gateway", "PG")
                        val paidCnt = st.optInt("paid_count", 0)
                        val paidTot = st.optDouble("paid_total", 0.0)
                        val unpaidCnt = st.optInt("unpaid_count", 0)
                        val isSelectedGw = pgGatewayFilter.equals(gwName, ignoreCase = true)

                        Surface(
                            color = if (isSelectedGw) ArnetaGoldPrimary.copy(alpha = 0.2f) else Color(0xFF141B2D),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (isSelectedGw) ArnetaGoldPrimary else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    pgGatewayFilter = if (isSelectedGw) "All" else gwName.lowercase()
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = gwName,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                    if (unpaidCnt > 0) {
                                        Surface(
                                            color = ArnetaOrangeActive.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "$unpaidCnt Pending",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ArnetaOrangeActive,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Lunas ($paidCnt): ${formatRupiah(paidTot)}",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Filter Pilihan Payment Gateway (Semua, ViaQRIS, Tripay, Duitku, Xendit, PayPal)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                availableGateways.forEach { gw ->
                    val label = if (gw == "All") "Semua Gateway" else gw.uppercase()
                    FilterChip(
                        selected = pgGatewayFilter.equals(gw, ignoreCase = true),
                        onClick = { pgGatewayFilter = gw },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Filter Status Transaksi PG
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "all" to "Semua Status",
                    "1" to "Menunggu Bayar",
                    "2" to "Lunas",
                    "3" to "Expired",
                    "4" to "Dibatalkan"
                ).forEach { (stKey, stLabel) ->
                    FilterChip(
                        selected = pgStatusFilter == stKey,
                        onClick = { pgStatusFilter = stKey },
                        label = { Text(stLabel, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = pgSearch,
                    onValueChange = { pgSearch = it },
                    placeholder = { Text("Cari username, channel, invoice, ref ID...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        if (pgSearch.isNotEmpty()) {
                            IconButton(onClick = {
                                pgSearch = ""
                                loadPaymentGatewayAudit()
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { loadPaymentGatewayAudit() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingPg) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (pgItems.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada data transaksi Payment Gateway pada filter ini.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pgItems) { item ->
                        val statusInt = item.optInt("status", 1)
                        val statusLabel = item.optString("status_label", "MENUNGGU PEMBAYARAN")
                        val gwUpper = item.optString("gateway", "PG").uppercase()
                        val channelName = item.optString("payment_channel", gwUpper)
                        val methodCode = item.optString("payment_method", "")
                        val currency = item.optString("currency", ApiClient.currencyCode)
                        val price = item.optDouble("price", 0.0)
                        val payCode = item.optString("pay_code", "")
                        val checkoutUrl = item.optString("checkout_url", item.optString("pg_url_payment", ""))
                        val refId = item.optString("gateway_trx_id", "")

                        val statusColor = when (statusInt) {
                            2 -> ArnetaGreenSuccess
                            3, 4 -> ArnetaRedDanger
                            else -> ArnetaOrangeActive
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPgDetail = item }
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
                                            color = ArnetaIndigoAccent,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (channelName.isNotEmpty() && !channelName.equals(gwUpper, ignoreCase = true)) {
                                                    "$gwUpper • $channelName"
                                                } else if (methodCode.isNotEmpty() && !methodCode.equals(gwUpper, ignoreCase = true)) {
                                                    "$gwUpper • $methodCode"
                                                } else {
                                                    gwUpper
                                                },
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }

                                        Text(
                                            text = "@${item.optString("username")}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            color = statusColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Paket: ${item.optString("plan_name")} (${item.optString("trx_invoice")})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp
                                        )
                                        if (refId.isNotEmpty()) {
                                            Text(
                                                text = "Ref Merchant: $refId",
                                                fontSize = 11.sp,
                                                color = ArnetaCyanIncome
                                            )
                                        }
                                        if (payCode.isNotEmpty()) {
                                            Text(
                                                text = "Kode Bayar / VA: $payCode",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = ArnetaGoldLight
                                            )
                                        }
                                        Text(
                                            text = "Dibuat: ${item.optString("created_date")} • Exp: ${item.optString("expired_date")}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    Text(
                                        text = formatRupiah(price, currency),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = { selectedPgDetail = item }) {
                                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Detail", fontSize = 11.5.sp)
                                    }

                                    if (statusInt == 1) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val cRes = ApiClient.request(
                                                            "admin_pg_action",
                                                            mapOf("sub_action" to "check", "id" to item.optInt("id"))
                                                        )
                                                        Toast.makeText(context, cRes.message, Toast.LENGTH_SHORT).show()
                                                        loadPaymentGatewayAudit()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Cek Mutasi", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val aRes = ApiClient.request(
                                                            "admin_pg_action",
                                                            mapOf("sub_action" to "approve_manual", "id" to item.optInt("id"))
                                                        )
                                                        Toast.makeText(context, aRes.message, Toast.LENGTH_LONG).show()
                                                        loadPaymentGatewayAudit()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ArnetaGreenSuccess,
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Setujui", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        val xRes = ApiClient.request(
                                                            "admin_pg_action",
                                                            mapOf("sub_action" to "cancel", "id" to item.optInt("id"))
                                                        )
                                                        Toast.makeText(context, xRes.message, Toast.LENGTH_SHORT).show()
                                                        loadPaymentGatewayAudit()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Filled.Cancel, contentDescription = "Batalkan", tint = ArnetaRedDanger)
                                            }
                                        }
                                    } else if (checkoutUrl.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Tidak dapat membuka tautan", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Buka Link", fontSize = 11.5.sp)
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

    // Dialog Detail Transaksi Payment Gateway (Mendukung ViaQRIS, Tripay, Duitku, Xendit, PayPal)
    if (selectedPgDetail != null) {
        PaymentGatewayDetailDialog(
            trx = selectedPgDetail!!,
            onDismiss = { selectedPgDetail = null },
            onRefreshList = {
                selectedPgDetail = null
                loadPaymentGatewayAudit()
            }
        )
    }
}

@Composable
fun AdminPaymentGatewayScreen() {
    AdminReportsScreen(initialTab = 1)
}

@Composable
private fun PaymentGatewayDetailDialog(
    trx: JSONObject,
    onDismiss: () -> Unit,
    onRefreshList: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gwUpper = trx.optString("gateway", "PG").uppercase()
    val channelName = trx.optString("payment_channel", gwUpper)
    val methodCode = trx.optString("payment_method", gwUpper)
    val currency = trx.optString("currency", ApiClient.currencyCode)
    val price = trx.optDouble("price", 0.0)
    val basePrice = trx.optDouble("base_price", price)
    val payCode = trx.optString("pay_code", "")
    val qrisString = trx.optString("qris_string", "")
    val checkoutUrl = trx.optString("checkout_url", trx.optString("pg_url_payment", ""))
    val refId = trx.optString("gateway_trx_id", "")
    val statusInt = trx.optInt("status", 1)
    val instructionsArr = trx.optJSONArray("instructions") ?: JSONArray()

    fun copyToClipboard(label: String, value: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label berhasil disalin", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = ArnetaGoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$gwUpper • $channelName",
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("Pelanggan: @${trx.optString("username")}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("Paket: ${trx.optString("plan_name")}", fontSize = 12.5.sp)
                        Text("Invoice: ${trx.optString("trx_invoice")}", fontSize = 12.sp)
                        if (refId.isNotEmpty()) {
                            Text("Ref Merchant: $refId", fontSize = 12.sp, color = ArnetaCyanIncome)
                        }
                        Text("Metode/Channel: $methodCode ($channelName)", fontSize = 12.sp)
                        Text("Total Tagihan: ${formatRupiah(price, currency)}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ArnetaGoldPrimary)
                        if (price != basePrice) {
                            Text("Harga Dasar Paket: ${formatRupiah(basePrice, currency)}", fontSize = 11.sp)
                        }
                        Text("Status: ${trx.optString("status_label")}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Dibuat: ${trx.optString("created_date")}", fontSize = 11.sp)
                        Text("Kedaluwarsa: ${trx.optString("expired_date")}", fontSize = 11.sp)
                        if (trx.optString("paid_date", "-") != "-") {
                            Text("Dibayar Pada: ${trx.optString("paid_date")}", fontSize = 11.sp, color = ArnetaGreenSuccess)
                        }
                    }
                }

                // Tampilkan Kode Virtual Account / Kode Bayar (Tripay / Duitku)
                if (payCode.isNotEmpty()) {
                    Surface(
                        color = ArnetaGoldPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ArnetaGoldPrimary, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Nomor Virtual Account / Kode Bayar:", fontSize = 10.5.sp)
                                Text(
                                    text = payCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = ArnetaGoldPrimary
                                )
                            }
                            TextButton(onClick = { copyToClipboard("Kode Bayar", payCode) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin", fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                // Tampilkan Tombol Salin String QRIS jika mode QRIS
                if (qrisString.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { copyToClipboard("Payload QRIS", qrisString) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin String QRIS Dinamis", fontSize = 12.sp)
                    }
                }

                // Tampilkan Tombol Buka Link Checkout (Xendit / PayPal / E-Wallet DeepLink)
                if (checkoutUrl.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                            } catch (e: Exception) {
                                copyToClipboard("URL Pembayaran", checkoutUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Halaman Pembayaran ($gwUpper)", fontSize = 12.sp)
                    }
                }

                // Tampilkan Instruksi Pembayaran jika tersedia (misal dari Tripay)
                if (instructionsArr.length() > 0) {
                    Text("Instruksi Pembayaran:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ArnetaGoldPrimary)
                    for (i in 0 until instructionsArr.length()) {
                        val ins = instructionsArr.optJSONObject(i) ?: continue
                        Text(ins.optString("title"), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                        val steps = ins.optJSONArray("steps") ?: JSONArray()
                        for (j in 0 until steps.length()) {
                            Text("• ${steps.optString(j)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (statusInt == 1) {
                Button(
                    onClick = {
                        scope.launch {
                            val res = ApiClient.request(
                                "admin_pg_action",
                                mapOf("sub_action" to "approve_manual", "id" to trx.optInt("id"))
                            )
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            onRefreshList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGreenSuccess, contentColor = Color.White)
                ) {
                    Text("Setujui Lunas", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

/**
 * 3. LAYAR KIRIM PESAN & BROADCAST WA / SMS / INBOX PELANGGAN
 */
@Composable
fun AdminSendMessageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("single") } // single | broadcast
    var selectedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var targetGroup by remember { mutableStateOf("all") } // all | hotspot | pppoe | active | expired
    var viaChannel by remember { mutableStateOf("wa") } // wa | sms | inbox | both
    var subject by remember { mutableStateOf("Informasi Layanan ${ApiClient.companyName}") }
    var messageBody by remember {
        mutableStateOf("Halo *[[name]]* (@[[user_name]]),\n\nTerima kasih telah menggunakan layanan internet *[[company_name]]*.")
    }
    var isSending by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp)
    ) {
        Text("Kirim Pesan & Broadcast", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "single",
                onClick = { mode = "single" },
                label = { Text("Pelanggan Tunggal", fontWeight = FontWeight.SemiBold) }
            )
            FilterChip(
                selected = mode == "broadcast",
                onClick = { mode = "broadcast" },
                label = { Text("Broadcast Massal", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (mode == "single") {
            CustomerLiveSearchPicker(
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { selectedCustomer = it }
            )
        } else {
            Text("Pilih Kelompok Tujuan:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "all" to "Semua Pelanggan",
                    "active" to "Pelanggan Aktif",
                    "expired" to "Pelanggan Kedaluwarsa",
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

        Text("Jalur Pengiriman:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "wa" to "WhatsApp Gateway",
                "inbox" to "Inbox Aplikasi/Web",
                "both" to "WA + Inbox Sekaligus",
                "sms" to "SMS Gateway"
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            label = { Text("Isi Pesan") },
            minLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Placeholder otomatis: [[name]], [[user_name]], [[phone]], [[balance]], [[company_name]]",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )

        Button(
            enabled = !isSending && messageBody.isNotBlank() && (mode == "broadcast" || selectedCustomer != null),
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
                            "message" to messageBody
                        )
                    )
                    isSending = false
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSending) "MENGIRIM PESAN..." else "KIRIM PESAN SEKARANG",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun AdminMessagesScreen() {
    AdminSendMessageScreen()
}

/**
 * 4. EDITOR HALAMAN STATIS (pages/*.html)
 */
@Composable
fun AdminPagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var pages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPageName by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    fun loadPages() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_pages")
            val arr = res.dataArray ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            pages = list
            if (list.isNotEmpty() && selectedPageName.isEmpty()) {
                val first = list.first()
                selectedPageName = first.optString("name")
                contentText = first.optString("content")
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
        Text("Editor Halaman Statis & Template", fontSize = 19.sp, fontWeight = FontWeight.Bold)

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
                    val name = p.optString("name")
                    FilterChip(
                        selected = selectedPageName == name,
                        onClick = {
                            selectedPageName = name
                            contentText = p.optString("content")
                        },
                        label = { Text(p.optString("title", name), fontSize = 11.5.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                label = { Text("Kode HTML ($selectedPageName.html)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(
                enabled = !isSaving && selectedPageName.isNotEmpty(),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_page_save",
                            mapOf("name" to selectedPageName, "content" to contentText)
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) loadPages()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "MENYIMPAN..." else "SIMPAN HALAMAN", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * 5. PENGATURAN SISTEM, MANAJEMEN STAF, SYSTEM TOOLS & LOG AKTIVITAS
 */
@Composable
fun AdminSettingsScreen(
    initialTab: Int = 0,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(initialTab) }
    var isLoading by remember { mutableStateOf(false) }

    // State Pengaturan Umum
    var companyName by remember { mutableStateOf(ApiClient.companyName) }
    var companyPhone by remember { mutableStateOf("") }
    var companyAddress by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf(ApiClient.currencyCode) }
    var paymentUsings by remember { mutableStateOf("Cash,Transfer") }
    var activeGateways by remember { mutableStateOf("") }
    var enableBalance by remember { mutableStateOf("yes") }

    // State Staf & Logs
    var staffList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
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
                        currencyCode = res.data.optString("currency_code", "Rp.")
                        paymentUsings = res.data.optString("payment_usings", "Cash,Transfer")
                        activeGateways = res.data.optString("payment_gateway", "viaqris")
                        enableBalance = res.data.optString("enable_balance", "yes")
                    }
                }
                1 -> {
                    val res = ApiClient.request("admin_staff_list")
                    val arr = res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    staffList = list
                }
                3 -> {
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                0 to "Pengaturan Umum",
                1 to "Staf / Admin",
                2 to "Alat Sistem",
                3 to "Log Aktivitas"
            ).forEach { (idx, title) ->
                FilterChip(
                    selected = currentTab == idx,
                    onClick = { currentTab = idx },
                    label = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
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
                        .padding(bottom = 100.dp)
                ) {
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
                            label = { Text("Kode Mata Uang") },
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
                        label = { Text("Metode Pembayaran Kasir (Pisahkan koma)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = activeGateways,
                        onValueChange = { activeGateways = it },
                        label = { Text("Payment Gateway Aktif (viaqris,tripay,duitku,xendit,paypal)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
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
                                            "enable_balance" to enableBalance.trim()
                                        )
                                    )
                                )
                                if (res.status) {
                                    ApiClient.companyName = companyName.trim()
                                    ApiClient.currencyCode = currencyCode.trim()
                                }
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIMPAN PENGATURAN", fontWeight = FontWeight.ExtraBold)
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
                    Text("Tambah Akun Staf", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(staffList) { st ->
                        Card(modifier = Modifier.fillMaxWidth()) {
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
                                        "@${st.optString("username")} • ${st.optString("user_type")}",
                                        fontSize = 12.sp,
                                        color = ArnetaGoldPrimary
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bersihkan Cache Sistem", fontWeight = FontWeight.Bold)
                                Text("Hapus file cache template Smarty & sistem", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = ApiClient.request("admin_system_tools", mapOf("tool" to "clear_cache"))
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Jalankan")
                            }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jalankan Cronjob Kedaluwarsa", fontWeight = FontWeight.Bold)
                                Text("Periksa & nonaktifkan paket pelanggan yang sudah lewat masa aktif", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = ApiClient.request("admin_system_tools", mapOf("tool" to "run_cron"))
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Jalankan")
                            }
                        }
                    }
                }
            }

            3 -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logsList) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = log.optString("type", "System"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                    Text(
                                        text = log.optString("date"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(text = log.optString("description"), fontSize = 12.5.sp)
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
            title = { Text("Tambah Akun Staf Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sUser, onValueChange = { sUser = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("Nama Lengkap") }, singleLine = true)
                    OutlinedTextField(value = sPass, onValueChange = { sPass = it }, label = { Text("Password") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Admin", "Agent", "Sales").forEach { r ->
                            FilterChip(selected = sRole == r, onClick = { sRole = r }, label = { Text(r, fontSize = 11.sp) })
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
        )
    }
}

@Composable
fun AdminSettingsAndLogsScreen(
    initialTab: Int = 0,
    onLogout: () -> Unit = {}
) {
    AdminSettingsScreen(initialTab = initialTab, onLogout = onLogout)
}

@Composable
fun AdminLogsScreen() {
    AdminSettingsScreen(initialTab = 3)
}
