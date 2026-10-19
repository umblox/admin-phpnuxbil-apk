package id.biz.arneta.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.screens.*
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)

        // Wajib untuk Android 15 (API 35) & Android 16 (API 36) agar Edge-to-Edge & Safe Area akurat
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B1120"))
        )

        setContent {
            var isDarkMode by remember { mutableStateOf(ApiClient.isDarkMode) }
            var isLoggedIn by remember { mutableStateOf(ApiClient.token.isNotEmpty()) }

            ArnetaAdminTheme(darkTheme = isDarkMode) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isLoggedIn) {
                        AdminLoginScreen(
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    } else {
                        ArnetaAdminMainShell(
                            isDarkMode = isDarkMode,
                            onToggleTheme = {
                                isDarkMode = !isDarkMode
                                ApiClient.isDarkMode = isDarkMode
                            },
                            onLogout = {
                                ApiClient.logout()
                                isLoggedIn = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArnetaAdminMainShell(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Navigasi Utama & Sub-Screen
    var currentBottomTab by remember { mutableStateOf(AdminNavTab.DASHBOARD) }
    var activeScreenKey by remember { mutableStateOf("dashboard") }
    var customerSearchParam by remember { mutableStateOf("") }
    var openAddCustomerDialog by remember { mutableStateOf(false) }
    var servicesSubTabParam by remember { mutableStateOf("active_users") }
    var webViewRouteParam by remember { mutableStateOf("dashboard") }

    var unpaidBadgeCount by remember { mutableIntStateOf(0) }
    var showQuickActionSheet by remember { mutableStateOf(false) }
    var quickShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }

    // Dropdown state di Sidebar Drawer (Persis Screenshot_20261019-175119)
    var expandLayananMenu by remember { mutableStateOf(true) }

    fun navigateTo(screenKey: String) {
        activeScreenKey = screenKey
        when (screenKey) {
            "dashboard" -> currentBottomTab = AdminNavTab.DASHBOARD
            "customers", "add_customer" -> {
                currentBottomTab = AdminNavTab.CUSTOMERS
                openAddCustomerDialog = (screenKey == "add_customer")
            }
            "active_users", "vouchers", "data_usage", "coupons", "refill_voucher" -> {
                currentBottomTab = AdminNavTab.SERVICES
                servicesSubTabParam = screenKey
            }
            else -> currentBottomTab = AdminNavTab.MENU_HUB
        }
        scope.launch { drawerState.close() }
    }

    // Tangani tombol Back HP agar kembali ke Dasbor terlebih dahulu sebelum menutup aplikasi
    BackHandler(enabled = activeScreenKey != "dashboard") {
        navigateTo("dashboard")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // SIDEBAR DRAWER PERSIS TAMPILAN WEB ARNETA.ID (Screenshot 1)
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF141B2D),
                drawerContentColor = Color.White,
                modifier = Modifier.width(275.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    // Item 1: Dasbor (Kotak Biru-Ungu Aktif seperti di Screenshot)
                    SidebarMainItem(
                        icon = Icons.Outlined.DesktopWindows,
                        label = "Dasbor",
                        selected = activeScreenKey == "dashboard",
                        onClick = { navigateTo("dashboard") }
                    )

                    // Item 2: Pelanggan
                    SidebarMainItem(
                        icon = Icons.Filled.Person,
                        label = "Pelanggan",
                        selected = activeScreenKey == "customers" && !openAddCustomerDialog,
                        onClick = { navigateTo("customers") }
                    )

                    // Item 3: Add Customer
                    SidebarMainItem(
                        icon = Icons.Filled.PersonAdd,
                        label = "Add Customer",
                        selected = activeScreenKey == "add_customer",
                        onClick = { navigateTo("add_customer") }
                    )

                    // Item 4: Layanan (Expandable dengan Garis Hijau di Kiri Persis Screenshot)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (expandLayananMenu) Color(0xFF283548) else Color.Transparent)
                            .clickable { expandLayananMenu = !expandLayananMenu }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Layanan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = if (expandLayananMenu) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronLeft,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = expandLayananMenu) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                        ) {
                            // Garis Hijau Vertikal di Sisi Kiri Sub-Menu Layanan (Persis Screenshot)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(320.dp)
                                    .background(Color(0xFF10B981))
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SidebarSubItem("Pelanggan Aktif") { navigateTo("active_users") }
                                SidebarSubItem("Isi Ulang Voucher") { navigateTo("refill_voucher") }
                                SidebarSubItem("Voucher") { navigateTo("vouchers") }
                                SidebarSubItem("Kupon") { navigateTo("coupons") }
                                SidebarSubItem("Isi Ulang Paket") { navigateTo("customers") }
                                SidebarSubItem("Isi Ulang Saldo") { navigateTo("customers") }
                                SidebarSubItem("User Data Usage") { navigateTo("data_usage") }
                                SidebarSubItem("Recharge Cards") { navigateTo("vouchers") }
                            }
                        }
                    }

                    // Item 5: Paket Internet
                    SidebarMainItem(
                        icon = Icons.Filled.ViewInAr,
                        label = "Paket Internet",
                        selected = activeScreenKey == "plans",
                        hasArrow = true,
                        onClick = { navigateTo("plans") }
                    )

                    // Item 6: Peta
                    SidebarMainItem(
                        icon = Icons.Filled.LocationOn,
                        label = "Peta",
                        selected = activeScreenKey == "map",
                        hasArrow = true,
                        onClick = { navigateTo("map") }
                    )

                    // Item 7: Laporan
                    SidebarMainItem(
                        icon = Icons.Filled.Assignment,
                        label = "Laporan",
                        selected = activeScreenKey == "reports",
                        hasArrow = true,
                        onClick = { navigateTo("reports") }
                    )

                    // Item 8: Mengirim pesan
                    SidebarMainItem(
                        icon = Icons.Filled.ChatBubble,
                        label = "Mengirim pesan",
                        selected = activeScreenKey == "broadcast",
                        hasArrow = true,
                        onClick = { navigateTo("broadcast") }
                    )

                    // Item 9: Asset Manager
                    SidebarMainItem(
                        icon = Icons.Filled.AccountTree,
                        label = "Asset Manager",
                        selected = activeScreenKey == "assets",
                        onClick = { navigateTo("assets") }
                    )

                    // Item 10: Jaringan
                    SidebarMainItem(
                        icon = Icons.Filled.Hub,
                        label = "Jaringan",
                        selected = activeScreenKey == "live_monitor" || activeScreenKey == "online_users" || activeScreenKey == "routers",
                        hasArrow = true,
                        onClick = { navigateTo("live_monitor") }
                    )

                    // Item 11: Halaman Statis
                    SidebarMainItem(
                        icon = Icons.Filled.Description,
                        label = "Halaman Statis",
                        selected = activeScreenKey == "pages",
                        hasArrow = true,
                        onClick = { navigateTo("pages") }
                    )

                    // Item 12: Pengaturan
                    SidebarMainItem(
                        icon = Icons.Filled.Settings,
                        label = "Pengaturan",
                        selected = activeScreenKey == "settings",
                        hasArrow = true,
                        onClick = { navigateTo("settings") }
                    )

                    // Item 13: Buka Web Asli (?_route=admin) di Dalam Aplikasi
                    SidebarMainItem(
                        icon = Icons.Filled.Language,
                        label = "Web Admin (?_route=admin)",
                        selected = activeScreenKey == "webview_admin",
                        onClick = {
                            webViewRouteParam = "dashboard"
                            navigateTo("webview_admin")
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (activeScreenKey != "webview_admin") {
                    ArnetaTopHeader(
                        companyName = ApiClient.companyName,
                        isDarkMode = isDarkMode,
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        onSearchClick = { navigateTo("customers") },
                        onToggleTheme = onToggleTheme,
                        onProfileClick = { navigateTo("settings") }
                    )
                }
            },
            bottomBar = {
                // Floating Bottom Bar dengan Safe Area di atas Navigasi 3 Tombol (||| O <)
                ArnetaBottomBar(
                    currentTab = currentBottomTab,
                    unpaidBadgeCount = unpaidBadgeCount,
                    onTabSelected = { tab ->
                        currentBottomTab = tab
                        activeScreenKey = when (tab) {
                            AdminNavTab.DASHBOARD -> "dashboard"
                            AdminNavTab.CUSTOMERS -> {
                                openAddCustomerDialog = false
                                "customers"
                            }
                            AdminNavTab.SERVICES -> "active_users"
                            AdminNavTab.MENU_HUB -> "menu_hub"
                        }
                    },
                    onQuickActionClick = {
                        quickShortcuts = ApiClient.customQuickShortcuts
                        showQuickActionSheet = true
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                when (activeScreenKey) {
                    "dashboard" -> AdminDashboardScreen(
                        onNavigateToCustomers = { q ->
                            customerSearchParam = q
                            openAddCustomerDialog = false
                            navigateTo("customers")
                        },
                        onNavigateToPrepaid = { sub -> navigateTo(sub) },
                        onNavigateToSubScreen = { target -> navigateTo(target) },
                        onUnpaidCountLoaded = { cnt -> unpaidBadgeCount = cnt }
                    )
                    "customers", "add_customer" -> AdminCustomersScreen(
                        initialSearch = customerSearchParam,
                        openAddDialogInitially = openAddCustomerDialog
                    )
                    "active_users", "vouchers", "data_usage", "coupons", "refill_voucher" -> AdminServicesScreen(
                        initialSubTab = servicesSubTabParam
                    )
                    "menu_hub" -> AdminMenuHubScreen(
                        unpaidQrisCount = unpaidBadgeCount,
                        onOpenScreen = { key -> navigateTo(key) }
                    )
                    "live_monitor", "online_users", "routers", "pools" -> AdminNetworkAndOnlineScreen(
                        initialTab = activeScreenKey
                    )
                    "plans" -> AdminPlansScreen()
                    "map" -> AdminMapScreen()
                    "assets" -> AdminAssetsScreen()
                    "reports", "payment_gateway" -> AdminReportsAndPgScreen(
                        initialTab = activeScreenKey
                    )
                    "broadcast" -> AdminBroadcastMessageScreen()
                    "pages" -> AdminStaticPagesScreen()
                    "settings" -> AdminSettingsAndToolsScreen(
                        onCustomShortcutsChanged = { quickShortcuts = ApiClient.customQuickShortcuts },
                        onLogout = onLogout
                    )
                    "webview_admin" -> AdminInAppWebViewScreen(
                        initialRouteOrUrl = webViewRouteParam,
                        title = "Arneta.ID Web Admin",
                        onBack = { navigateTo("dashboard") }
                    )
                }
            }
        }
    }

    // BOTTOM SHEET AKSI CEPAT (SAAT TOMBOL TENGAH EMAS DITEKAN)
    if (showQuickActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⚡ Shortcut Aksi Cepat Admin", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    TextButton(onClick = {
                        showQuickActionSheet = false
                        navigateTo("settings")
                    }) {
                        Text("Atur Shortcut", fontSize = 12.sp, color = ArnetaGoldPrimary)
                    }
                }

                val shortcutDefinitions = listOf(
                    Triple("add_customer", "Add Customer Baru", "Tambah pelanggan Hotspot / PPPoE") to { navigateTo("add_customer") },
                    Triple("recharge_plan", "Isi Ulang Paket", "Aktifkan paket pelanggan & cetak struk") to { navigateTo("customers") },
                    Triple("deposit_balance", "Isi Ulang Saldo", "Top-up saldo deposit pelanggan") to { navigateTo("customers") },
                    Triple("generate_voucher", "Generate & Cetak Voucher", "Buat kode voucher massal") to { navigateTo("vouchers") },
                    Triple("online_users", "User Online & Kick Sesi", "Pantau & putuskan sesi MikroTik") to { navigateTo("online_users") },
                    Triple("audit_qris", "Cek Pembayaran QRIS", "Verifikasi atau approve tagihan QRIS") to { navigateTo("payment_gateway") },
                    Triple("live_monitor", "Live Diagram MikroTik", "Pantau CPU Load, RAM & Ping real-time") to { navigateTo("live_monitor") },
                    Triple("broadcast_wa", "Broadcast WhatsApp", "Kirim pesan massal ke pelanggan") to { navigateTo("broadcast") },
                    Triple("webview_admin", "Web Admin (?_route=admin)", "Buka halaman web asli di dalam aplikasi") to {
                        webViewRouteParam = "dashboard"
                        navigateTo("webview_admin")
                    }
                )

                shortcutDefinitions.filter { quickShortcuts.contains(it.first.first) }.forEach { (info, action) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showQuickActionSheet = false
                                action()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(info.second, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(info.third, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                            }
                            Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SidebarMainItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    hasArrow: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF312E81) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
        if (hasArrow) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SidebarSubItem(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
