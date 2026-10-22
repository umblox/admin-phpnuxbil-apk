package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

data class HubMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * 1. MENU HUB (BENTO GRID SELURUH FITUR ADMIN ARNETA.ID)
 */
@Composable
fun AdminMenuHubScreen(
    unpaidQrisCount: Int,
    onOpenScreen: (String) -> Unit
) {
    val hubItems = listOf(
        HubMenuItem("online_users", "User Online & Kick", "Pantau Hotspot/PPPoE aktif & putus sesi", Icons.Filled.WifiTethering, ArnetaGreenSuccess),
        HubMenuItem("live_monitor", "Live Speed MikroTik", "Monitor Mbps Download/Upload, CPU & RAM", Icons.Filled.Speed, ArnetaCyanIncome),
        HubMenuItem("payment_gateway", "Audit QRIS ($unpaidQrisCount)", "Cek mutasi, approve manual & batal QRIS", Icons.Filled.QrCodeScanner, ArnetaGoldPrimary),
        HubMenuItem("reports", "Laporan Keuangan", "Rekap pendapatan harian & periode", Icons.Filled.Assessment, Color(0xFF10B981)),
        HubMenuItem("plans", "Paket Internet & BW", "Hotspot, PPPoE Postpaid, Saldo & Bandwidth", Icons.Filled.Inventory2, Color(0xFF8B5CF6)),
        HubMenuItem("map", "Peta Pelanggan & ODP", "Visualisasi titik koordinat & radius router", Icons.Filled.Map, ArnetaOrangeActive),
        HubMenuItem("broadcast", "Mengirim Pesan (WA)", "Broadcast WhatsApp, SMS & Inbox pelanggan", Icons.Filled.Forum, Color(0xFF06B6D4)),
        HubMenuItem("assets", "Asset Manager", "Inventaris ODP, ODC, ONT, Router & Kabel", Icons.Filled.Hub, Color(0xFFEC4899)),
        HubMenuItem("routers", "Jaringan & IP Pool", "Konfigurasi Router MikroTik & Rentang IP", Icons.Filled.Router, Color(0xFF3B82F6)),
        HubMenuItem("pages", "Halaman Statis", "Edit HTML Template Voucher & Pengumuman", Icons.Filled.Description, Color(0xFFF59E0B)),
        HubMenuItem("webview_admin", "Web Admin Internal", "Buka ?_route=admin & dashboard tanpa keluar", Icons.Filled.Language, Color(0xFF6366F1)),
        HubMenuItem("settings", "Pengaturan & Tools", "Sistem, Staf, Log, Cronjob & Shortcut Bawah", Icons.Filled.SettingsSuggest, Color(0xFF94A3B8))
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Pusat Kendali Admin Arneta.ID", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(
                "Semua modul PHPNuxBill & fitur ekstra dalam satu genggaman",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }

        items(hubItems.chunked(2)) { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pair.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenScreen(item.id) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(item.accentColor.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 2. LAYAR LAPORAN KEUANGAN (LIVE SEARCH 1 HURUF) & AUDIT PAYMENT GATEWAY / QRIS
 */
@Composable
fun AdminReportsAndPgScreen(
    initialTab: String = "reports"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) } // reports | payment_gateway
    var isLoading by remember { mutableStateOf(false) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val firstMonthStr = remember { SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date()) }
    var startDate by remember { mutableStateOf(firstMonthStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var searchReport by remember { mutableStateOf("") }
    var totalIncome by remember { mutableDoubleStateOf(0.0) }
    var reportItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var pgStatusFilter by remember { mutableStateOf("all") }
    var pgItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun loadData() {
        isLoading = true
        scope.launch {
            if (currentTab == "reports") {
                val res = ApiClient.request(
                    "admin_reports",
                    mapOf(
                        "start_date" to startDate,
                        "end_date" to endDate,
                        "limit" to 250
                    )
                )
                if (res.status) {
                    val sum = res.data?.optJSONObject("summary")
                    val arr = res.data?.optJSONArray("transactions") ?: res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    var calcSum = 0.0
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(obj)
                        calcSum += obj.optDouble("price", 0.0)
                    }
                    reportItems = list
                    totalIncome = sum?.optDouble("total_income", calcSum) ?: calcSum
                }
            } else {
                val res = ApiClient.request(
                    "admin_payment_gateways",
                    mapOf("status" to pgStatusFilter, "sub_action" to "list")
                )
                val arr = res.data?.optJSONArray("items") ?: res.dataArray ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                pgItems = list
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentTab, pgStatusFilter) {
        loadData()
    }

    // Filter Instan 1 Huruf pada Daftar Transaksi Laporan
    val filteredReports = remember(reportItems, searchReport) {
        val q = searchReport.trim().lowercase()
        if (q.isEmpty()) reportItems
        else reportItems.filter { trx ->
            trx.optString("username", "").lowercase().contains(q) ||
                trx.optString("invoice", "").lowercase().contains(q) ||
                trx.optString("plan_name", "").lowercase().contains(q) ||
                trx.optString("method", "").lowercase().contains(q) ||
                trx.optString("routers", "").lowercase().contains(q)
        }
    }

    val filteredTotalIncome = remember(filteredReports, searchReport, totalIncome) {
        if (searchReport.isBlank()) totalIncome
        else filteredReports.sumOf { it.optDouble("price", 0.0) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = currentTab == "reports",
                onClick = { currentTab = "reports" },
                label = { Text("Laporan Transaksi", fontWeight = FontWeight.Bold) }
            )
            FilterChip(
                selected = currentTab == "payment_gateway",
                onClick = { currentTab = "payment_gateway" },
                label = { Text("Audit Payment Gateway / QRIS", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentTab == "reports") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Dari (YYYY-MM-DD)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("Sampai (YYYY-MM-DD)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(onClick = { loadData() }) {
                    Icon(Icons.Filled.FilterAlt, contentDescription = "Filter Tanggal")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pencarian Otomatis 1 Huruf di Laporan
            OutlinedTextField(
                value = searchReport,
                onValueChange = { searchReport = it },
                placeholder = { Text("Ketik 1 huruf username / paket / invoice...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                trailingIcon = {
                    if (searchReport.isNotEmpty()) {
                        IconButton(onClick = { searchReport = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00CFA1)),
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
                        Text("Total Pendapatan ($startDate s/d $endDate)", fontSize = 11.sp, color = Color.White)
                        Text(formatRupiah(filteredTotalIncome), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Surface(color = Color.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${filteredReports.size} Trx",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = ArnetaGoldPrimary)
                }
            } else if (filteredReports.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Tidak ada transaksi ditemukan pada periode ini.", fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredReports) { trx ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${trx.optString("username")} • ${trx.optString("plan_name")}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${trx.optString("invoice")} • ${trx.optString("recharged_on")} ${trx.optString("recharged_time")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        "Metode: ${trx.optString("method")} (${trx.optString("routers")})",
                                        fontSize = 11.sp,
                                        color = ArnetaCyanIncome
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatRupiah(trx.optDouble("price", 0.0)),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 14.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                BluetoothPrinterHelper.printReceiptText(
                                                    context = context,
                                                    title = "STRUK PEMBAYARAN",
                                                    lines = listOf(
                                                        "No. Invoice" to trx.optString("invoice"),
                                                        "Pelanggan" to trx.optString("username"),
                                                        "Paket" to trx.optString("plan_name"),
                                                        "Harga" to formatRupiah(trx.optDouble("price", 0.0)),
                                                        "Tanggal" to trx.optString("recharged_on"),
                                                        "Aktif s/d" to trx.optString("expiration")
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = "Cetak", tint = ArnetaCyanIncome)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Semua", "1" to "Belum Bayar (Unpaid)", "2" to "Lunas (Paid)").forEach { (k, label) ->
                    FilterChip(
                        selected = pgStatusFilter == k,
                        onClick = { pgStatusFilter = k },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pgItems) { item ->
                    val st = item.optInt("status", 1)
                    val stColor = when (st) {
                        2 -> ArnetaGreenSuccess
                        1 -> ArnetaGoldPrimary
                        else -> ArnetaRedDanger
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${item.optString("username")} • ${item.optString("plan_name")}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Gateway: ${item.optString("gateway").uppercase()} • Dibuat: ${item.optString("created_date")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatRupiah(item.optDouble("price", 0.0)),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ArnetaGoldPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        item.optString("status_label"),
                                        color = stColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (st == 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = ApiClient.request(
                                                    "admin_pg_action",
                                                    mapOf("id" to item.optInt("id"), "sub_action" to "check")
                                                )
                                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                loadData()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cek Status", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = ApiClient.request(
                                                    "admin_pg_action",
                                                    mapOf("id" to item.optInt("id"), "sub_action" to "approve_manual")
                                                )
                                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                loadData()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGreenSuccess, contentColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val res = ApiClient.request(
                                                    "admin_pg_action",
                                                    mapOf("id" to item.optInt("id"), "sub_action" to "cancel")
                                                )
                                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                loadData()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Cancel, contentDescription = "Batalkan", tint = ArnetaRedDanger)
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
 * 3. LAYAR MENGIRIM PESAN (SINGLE DENGAN LIVE SEARCH PELANGGAN 1 HURUF & BULK MASSAL)
 */
@Composable
fun AdminBroadcastMessageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("single") } // single | bulk
    var selectedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var group by remember { mutableStateOf("all") } // all | active | expired | hotspot | pppoe
    var via by remember { mutableStateOf("wa") } // wa | inbox | sms | both
    var subject by remember { mutableStateOf("Informasi Layanan Internet ${ApiClient.companyName}") }
    var message by remember {
        mutableStateOf("Halo Kak *[[name]]* (@[[user_name]]),\n\nTerima kasih telah menggunakan layanan *[[company_name]]*. Saldo akun Anda saat ini: Rp [[balance]].\n\nSalam hangat,\nAdmin ${ApiClient.companyName}")
    }
    var isSending by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp)
    ) {
        Text("Mengirim Pesan & Broadcast", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "single",
                onClick = { mode = "single" },
                label = { Text("Pelanggan Tertentu (Cari Nama)", fontWeight = FontWeight.SemiBold) }
            )
            FilterChip(
                selected = mode == "bulk",
                onClick = { mode = "bulk" },
                label = { Text("Broadcast Massal (Grup)", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (mode == "single") {
            // Pemilih Pelanggan Otomatis dengan Live Search 1 Huruf & Scroll
            CustomerLiveSearchPicker(
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { cust -> selectedCustomer = cust },
                label = "Ketik 1 huruf nama/username pelanggan atau scroll daftar"
            )
        } else {
            Text("Pilih Kelompok Penerima Broadcast:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "all" to "Semua Pelanggan",
                    "active" to "Pelanggan Aktif",
                    "expired" to "Pelanggan Expired",
                    "hotspot" to "Khusus Hotspot",
                    "pppoe" to "Khusus PPPoE"
                ).forEach { (gKey, gLabel) ->
                    FilterChip(
                        selected = group == gKey,
                        onClick = { group = gKey },
                        label = { Text(gLabel, fontSize = 12.sp) }
                    )
                }
            }
        }

        Text("Jalur Pengiriman:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "wa" to "WhatsApp Gateway",
                "inbox" to "Inbox Aplikasi Pelanggan",
                "both" to "WA + Inbox Sekaligus",
                "sms" to "SMS Gateway"
            ).forEach { (vKey, vLabel) ->
                FilterChip(
                    selected = via == vKey,
                    onClick = { via = vKey },
                    label = { Text(vLabel, fontSize = 12.sp) }
                )
            }
        }

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Judul / Subjek Pesan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Ketuk untuk menyisipkan tag otomatis:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("[[name]]", "[[user_name]]", "[[phone]]", "[[balance]]", "[[company_name]]").forEach { tag ->
                AssistChip(
                    onClick = { message += " $tag" },
                    label = { Text(tag, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                )
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Isi Pesan") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = !isSending && (mode == "bulk" || selectedCustomer != null),
            onClick = {
                if (mode == "single" && selectedCustomer == null) {
                    Toast.makeText(context, "Pilih pelanggan terlebih dahulu.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isSending = true
                scope.launch {
                    val res = ApiClient.request(
                        "admin_send_message",
                        mapOf(
                            "mode" to mode,
                            "group" to group,
                            "via" to via,
                            "customer_id" to (selectedCustomer?.optInt("id") ?: 0),
                            "username" to (selectedCustomer?.optString("username") ?: ""),
                            "subject" to subject,
                            "message" to message
                        )
                    )
                    isSending = false
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSending) "MENGIRIM PESAN..."
                else if (mode == "single") "KIRIM KE ${selectedCustomer?.optString("fullname")?.uppercase() ?: "PELANGGAN"}"
                else "KIRIM BROADCAST MASSAL",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 4. LAYAR HALAMAN STATIS (EDIT TEMPLATE HTML DI FOLDER pages/*.html)
 */
@Composable
fun AdminStaticPagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pagesList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedPageName by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    fun loadStaticPages() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_pages")
            val arr = res.dataArray ?: res.data?.optJSONArray("pages") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
            pagesList = list
            if (list.isNotEmpty() && selectedPageName.isEmpty()) {
                selectedPageName = list.first().optString("name")
                editorContent = list.first().optString("content")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadStaticPages()
    }

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
            Text("Editor Halaman Statis (pages/*.html)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = { loadStaticPages() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", tint = ArnetaGoldPrimary)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else if (pagesList.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Tidak ada file template .html di folder pages/", fontSize = 13.sp)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                pagesList.forEach { p ->
                    val name = p.optString("name")
                    FilterChip(
                        selected = selectedPageName == name,
                        onClick = {
                            selectedPageName = name
                            editorContent = p.optString("content")
                        },
                        label = { Text(p.optString("title", name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = editorContent,
                onValueChange = { editorContent = it },
                label = { Text("Kode HTML: $selectedPageName.html") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                enabled = !isSaving && selectedPageName.isNotEmpty(),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_page_save",
                            mapOf("name" to selectedPageName, "content" to editorContent)
                        )
                        isSaving = false
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) loadStaticPages()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 100.dp)
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "MENYIMPAN..." else "SIMPAN HALAMAN HTML", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 5. LAYAR PENGATURAN SISTEM, KUSTOMISASI SHORTCUT BAWAH, STAF, LOGS & CRONJOB
 */
@Composable
fun AdminSettingsAndToolsScreen(
    onCustomShortcutsChanged: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentSubTab by remember { mutableStateOf("general") } // general | shortcuts | staff | logs

    var companyName by remember { mutableStateOf(ApiClient.companyName) }
    var companyPhone by remember { mutableStateOf("") }
    var companyAddress by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf(ApiClient.currencyCode) }
    var paymentGateway by remember { mutableStateOf("viaqris") }
    var waGatewayUrl by remember { mutableStateOf("") }

    var activeShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }

    var staffList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logSearchQuery by remember { mutableStateOf("") }
    var isLoadingLogs by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }

    fun loadSettingsSubTab() {
        scope.launch {
            when (currentSubTab) {
                "general" -> {
                    val res = ApiClient.request("admin_settings")
                    if (res.status && res.data != null) {
                        companyName = res.data.optString("CompanyName", companyName)
                        companyPhone = res.data.optString("phone", "")
                        companyAddress = res.data.optString("address", "")
                        currencyCode = res.data.optString("currency_code", "Rp.")
                        paymentGateway = res.data.optString("payment_gateway", "viaqris")
                        waGatewayUrl = res.data.optString("wa_url", "")
                    }
                }
                "staff" -> {
                    val res = ApiClient.request("admin_staff_list")
                    val arr = res.dataArray ?: res.data?.optJSONArray("staff") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    staffList = list
                }
                "logs" -> {
                    isLoadingLogs = true
                    val res = ApiClient.request("admin_logs")
                    val arr = res.dataArray ?: res.data?.optJSONArray("logs") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    logsList = list
                    isLoadingLogs = false
                }
            }
        }
    }

    LaunchedEffect(currentSubTab) {
        loadSettingsSubTab()
    }

    val filteredLogs = remember(logsList, logSearchQuery) {
        val q = logSearchQuery.trim().lowercase()
        if (q.isEmpty()) logsList
        else logsList.filter { log ->
            log.optString("description", "").lowercase().contains(q) ||
                log.optString("type", "").lowercase().contains(q) ||
                log.optString("ip", "").lowercase().contains(q) ||
                log.optString("date", "").lowercase().contains(q)
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
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "general" to "Pengaturan Umum",
                "shortcuts" to "Shortcut Navigasi Bawah",
                "staff" to "Akun Staf/Admin",
                "logs" to "System Tools & Logs"
            ).forEach { (k, label) ->
                FilterChip(
                    selected = currentSubTab == k,
                    onClick = { currentSubTab = k },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (currentSubTab) {
            "general" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 110.dp)
                ) {
                    OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("Nama Perusahaan / Billing") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = companyPhone, onValueChange = { companyPhone = it }, label = { Text("Nomor Telepon / CS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = companyAddress, onValueChange = { companyAddress = it }, label = { Text("Alamat Kantor") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = currencyCode, onValueChange = { currencyCode = it }, label = { Text("Simbol Mata Uang (Rp.)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = paymentGateway, onValueChange = { paymentGateway = it }, label = { Text("Payment Gateway Aktif (viaqris,duitku,dll)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = waGatewayUrl, onValueChange = { waGatewayUrl = it }, label = { Text("URL WhatsApp Gateway") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_settings_save",
                                    mapOf(
                                        "settings" to mapOf(
                                            "CompanyName" to companyName,
                                            "phone" to companyPhone,
                                            "address" to companyAddress,
                                            "currency_code" to currencyCode,
                                            "payment_gateway" to paymentGateway,
                                            "wa_url" to waGatewayUrl
                                        )
                                    )
                                )
                                ApiClient.companyName = companyName
                                ApiClient.currencyCode = currencyCode
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SIMPAN PENGATURAN", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            ApiClient.logout()
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = ArnetaRedDanger)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("KELUAR SESI ADMIN (LOGOUT)", color = ArnetaRedDanger, fontWeight = FontWeight.Bold)
                    }
                }
            }

            "shortcuts" -> {
                val allOptions = listOf(
                    "add_customer" to "➕ Add Customer (Tambah Pelanggan Baru)",
                    "recharge_plan" to "⚡ Isi Ulang Paket Internet",
                    "deposit_balance" to "💰 Isi Ulang Saldo Pelanggan",
                    "generate_voucher" to "🎟️ Buat / Cetak Voucher Massal",
                    "online_users" to "📡 Cek & Kick User Online MikroTik",
                    "audit_qris" to "🔍 Audit Pembayaran QRIS Masuk",
                    "live_monitor" to "📈 Live Speed Monitor Mbps MikroTik",
                    "broadcast_wa" to "💬 Kirim Broadcast WhatsApp",
                    "webview_admin" to "🌐 Buka Web Admin (?_route=admin)"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 110.dp)
                ) {
                    Text("Kustomisasi Shortcut Tombol Tengah Emas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Pilih fitur yang ingin ditampilkan saat menekan tombol kilat emas di navigasi bawah:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    allOptions.forEach { (key, title) ->
                        val checked = activeShortcuts.contains(key)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = activeShortcuts.toMutableSet()
                                    if (checked && next.size > 1) next.remove(key) else next.add(key)
                                    activeShortcuts = next
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
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }

            "staff" -> {
                Button(
                    onClick = { showAddStaffDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.PersonAddAlt1, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Staf / Agent", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(staffList) { s ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("${s.optString("fullname")} (@${s.optString("username")})", fontWeight = FontWeight.Bold)
                                    Text("Role: ${s.optString("user_type")} • Login terakhir: ${s.optString("last_login")}", fontSize = 11.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val dRes = ApiClient.request("admin_staff_delete", mapOf("id" to s.optInt("id")))
                                        Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                                        loadSettingsSubTab()
                                    }
                                }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }

            "logs" -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request("admin_system_tools", mapOf("tool" to "run_cron"))
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                loadSettingsSubTab()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaCyanIncome, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Jalankan Cronjob", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request("admin_system_tools", mapOf("tool" to "clear_cache"))
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaOrangeActive, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bersihkan Cache", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = logSearchQuery,
                    onValueChange = { logSearchQuery = it },
                    placeholder = { Text("Cari aktivitas log...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingLogs) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = ArnetaGoldPrimary)
                    }
                } else if (filteredLogs.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Belum ada catatan log aktivitas sistem.", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs) { log ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(log.optString("description"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${log.optString("date")} • ${log.optString("type")} • IP: ${log.optString("ip")}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStaffDialog) {
        var uName by remember { mutableStateOf("") }
        var fName by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("Agent") }

        AlertDialog(
            onDismissRequest = { showAddStaffDialog = false },
            title = { Text("Tambah Staf / Agent Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uName, onValueChange = { uName = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = fName, onValueChange = { fName = it }, label = { Text("Nama Lengkap") }, singleLine = true)
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true)
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role (SuperAdmin / Admin / Agent / Sales)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_staff_save",
                            mapOf("username" to uName, "fullname" to fName, "password" to pass, "user_type" to role)
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) {
                            showAddStaffDialog = false
                            loadSettingsSubTab()
                        }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddStaffDialog = false }) { Text("Batal") } }
        )
    }
}
