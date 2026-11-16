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

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B1120"))
        )

        setContent {
            var isDarkMode by remember { mutableStateOf(ApiClient.isDarkMode) }
            var isLoggedIn by remember { mutableStateOf(ApiClient.isLoggedIn()) }

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

    var currentBottomTab by remember { mutableStateOf(AdminNavTab.DASHBOARD) }
    var activeScreenKey by remember { mutableStateOf("dashboard") }

    var customerSearchParam by remember { mutableStateOf("") }
    var customerTypeParam by remember { mutableStateOf("All") }
    var openAddCustomerDialog by remember { mutableStateOf(false) }

    var servicesSubTabParam by remember { mutableStateOf("active_users") }
    var plansSubTabParam by remember { mutableStateOf("Hotspot") }
    var networkSubTabParam by remember { mutableStateOf("live_monitor") }
    var genieSubTabParam by remember { mutableStateOf("dashboard") }
    var settingsSubTabParam by remember { mutableStateOf("general") }
    var webViewRouteParam by remember { mutableStateOf("dashboard") }

    var companyTitle by remember { mutableStateOf(ApiClient.companyName) }
    var unpaidBadgeCount by remember { mutableIntStateOf(0) }
    var showQuickActionSheet by remember { mutableStateOf(false) }
    var quickShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }

    // Grup menu yang sedang diekspansi di Sidebar
    var expandedSidebarGroup by remember { mutableStateOf("layanan") }

    fun toggleGroup(groupKey: String) {
        expandedSidebarGroup = if (expandedSidebarGroup == groupKey) "" else groupKey
    }

    fun navigateTo(screenKey: String) {
        if (!ApiClient.isLoggedIn()) {
            onLogout()
            return
        }
        companyTitle = ApiClient.companyName

        when {
            screenKey == "dashboard" -> {
                activeScreenKey = "dashboard"
                currentBottomTab = AdminNavTab.DASHBOARD
            }
            screenKey == "customers" || screenKey == "add_customer" || screenKey.startsWith("customers_") -> {
                activeScreenKey = if (screenKey == "add_customer") "add_customer" else "customers"
                currentBottomTab = AdminNavTab.CUSTOMERS
                openAddCustomerDialog = (screenKey == "add_customer")
                customerTypeParam = when (screenKey) {
                    "customers_hotspot" -> "Hotspot"
                    "customers_pppoe" -> "PPPoE"
                    else -> "All"
                }
            }
            screenKey == "services" || screenKey in setOf("active_users", "recharge_plan", "deposit_balance", "vouchers", "data_usage", "coupons", "refill_voucher") -> {
                activeScreenKey = "services"
                currentBottomTab = AdminNavTab.SERVICES
                servicesSubTabParam = if (screenKey == "services") "active_users" else screenKey
            }
            screenKey == "plans" || screenKey.startsWith("plans_") -> {
                activeScreenKey = "plans"
                currentBottomTab = AdminNavTab.MENU_HUB
                plansSubTabParam = when (screenKey) {
                    "plans_pppoe" -> "PPPOE"
                    "plans_balance" -> "Balance"
                    "plans_vpn" -> "VPN"
                    "plans_bandwidth" -> "Bandwidth"
                    else -> "Hotspot"
                }
            }
            screenKey == "network" || screenKey in setOf("live_monitor", "online_users", "ros_logs", "routers", "pools") -> {
                activeScreenKey = "network"
                currentBottomTab = AdminNavTab.MENU_HUB
                networkSubTabParam = if (screenKey == "network") "live_monitor" else screenKey
            }
            screenKey == "genieacs" || screenKey.startsWith("genieacs_") -> {
                activeScreenKey = "genieacs"
                currentBottomTab = AdminNavTab.MENU_HUB
                genieSubTabParam = when (screenKey) {
                    "genieacs_devices" -> "devices"
                    "genieacs_hosts" -> "hosts"
                    else -> "dashboard"
                }
            }
            screenKey == "settings" || screenKey.startsWith("settings_") -> {
                activeScreenKey = "settings"
                currentBottomTab = AdminNavTab.MENU_HUB
                settingsSubTabParam = screenKey.removePrefix("settings_").takeIf { it != "settings" } ?: "general"
            }
            else -> {
                activeScreenKey = screenKey
                currentBottomTab = AdminNavTab.MENU_HUB
            }
        }
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isOpen || activeScreenKey != "dashboard") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            navigateTo("dashboard")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF141B2D),
                drawerContentColor = Color.White,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 10.dp)
                ) {
                    // 1. DASBOR
                    SidebarMainItem(
                        icon = Icons.Outlined.DesktopWindows,
                        label = "Dasbor",
                        selected = activeScreenKey == "dashboard",
                        onClick = { navigateTo("dashboard") }
                    )

                    // 2. PELANGGAN (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.People,
                        label = "Pelanggan",
                        isExpanded = expandedSidebarGroup == "pelanggan",
                        isGroupActive = activeScreenKey == "customers" || activeScreenKey == "add_customer",
                        onToggle = { toggleGroup("pelanggan") },
                        subItems = listOf(
                            Triple("customers", "Daftar Pelanggan", activeScreenKey == "customers" && customerTypeParam == "All" && !openAddCustomerDialog),
                            Triple("add_customer", "Tambah Pelanggan Baru", openAddCustomerDialog),
                            Triple("customers_hotspot", "Pelanggan Hotspot", activeScreenKey == "customers" && customerTypeParam == "Hotspot"),
                            Triple("customers_pppoe", "Pelanggan PPPoE", activeScreenKey == "customers" && customerTypeParam == "PPPoE")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 3. LAYANAN & VOUCHER (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.ConfirmationNumber,
                        label = "Layanan",
                        isExpanded = expandedSidebarGroup == "layanan",
                        isGroupActive = activeScreenKey == "services" || currentBottomTab == AdminNavTab.SERVICES,
                        onToggle = { toggleGroup("layanan") },
                        subItems = listOf(
                            Triple("active_users", "Pelanggan Aktif", activeScreenKey == "services" && servicesSubTabParam == "active_users"),
                            Triple("recharge_plan", "Isi Ulang Paket", activeScreenKey == "services" && servicesSubTabParam == "recharge_plan"),
                            Triple("deposit_balance", "Isi Ulang Saldo", activeScreenKey == "services" && servicesSubTabParam == "deposit_balance"),
                            Triple("vouchers", "Voucher & Cards", activeScreenKey == "services" && servicesSubTabParam == "vouchers"),
                            Triple("refill_voucher", "Isi Ulang Voucher", activeScreenKey == "services" && servicesSubTabParam == "refill_voucher"),
                            Triple("coupons", "Kupon Promo", activeScreenKey == "services" && servicesSubTabParam == "coupons"),
                            Triple("data_usage", "User Data Usage", activeScreenKey == "services" && servicesSubTabParam == "data_usage")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 4. PAKET INTERNET & BANDWIDTH (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.ViewInAr,
                        label = "Paket Internet",
                        isExpanded = expandedSidebarGroup == "paket",
                        isGroupActive = activeScreenKey == "plans",
                        onToggle = { toggleGroup("paket") },
                        subItems = listOf(
                            Triple("plans_hotspot", "Paket Hotspot", activeScreenKey == "plans" && plansSubTabParam == "Hotspot"),
                            Triple("plans_pppoe", "Paket PPPoE", activeScreenKey == "plans" && plansSubTabParam == "PPPOE"),
                            Triple("plans_balance", "Paket Saldo", activeScreenKey == "plans" && plansSubTabParam == "Balance"),
                            Triple("plans_vpn", "Paket VPN", activeScreenKey == "plans" && plansSubTabParam == "VPN"),
                            Triple("plans_bandwidth", "Profil Bandwidth", activeScreenKey == "plans" && plansSubTabParam == "Bandwidth")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 5. LAPORAN & AUDIT PAYMENT GATEWAY (DIGABUNG & EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.Assessment,
                        label = "Laporan & Payment Gateway",
                        isExpanded = expandedSidebarGroup == "laporan",
                        isGroupActive = activeScreenKey == "reports" || activeScreenKey == "payment_gateway",
                        onToggle = { toggleGroup("laporan") },
                        subItems = listOf(
                            Triple("reports", "Laporan Keuangan", activeScreenKey == "reports"),
                            Triple("payment_gateway", "Audit Payment Gateway", activeScreenKey == "payment_gateway")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 6. JARINGAN & MIKROTIK (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.Hub,
                        label = "Jaringan & MikroTik",
                        isExpanded = expandedSidebarGroup == "jaringan",
                        isGroupActive = activeScreenKey == "network",
                        onToggle = { toggleGroup("jaringan") },
                        subItems = listOf(
                            Triple("live_monitor", "MikroTik Monitor", activeScreenKey == "network" && networkSubTabParam == "live_monitor"),
                            Triple("online_users", "User Online & Kick", activeScreenKey == "network" && networkSubTabParam == "online_users"),
                            Triple("ros_logs", "Log & Wireless", activeScreenKey == "network" && networkSubTabParam == "ros_logs"),
                            Triple("routers", "Daftar Router", activeScreenKey == "network" && networkSubTabParam == "routers"),
                            Triple("pools", "IP Pool", activeScreenKey == "network" && networkSubTabParam == "pools")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 7. GENIEACS TR-069 (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.SettingsInputAntenna,
                        label = "GenieACS (TR-069)",
                        isExpanded = expandedSidebarGroup == "genieacs",
                        isGroupActive = activeScreenKey == "genieacs",
                        onToggle = { toggleGroup("genieacs") },
                        subItems = listOf(
                            Triple("genieacs_dashboard", "Dasbor TR-069", activeScreenKey == "genieacs" && genieSubTabParam == "dashboard"),
                            Triple("genieacs_devices", "Daftar Perangkat", activeScreenKey == "genieacs" && genieSubTabParam == "devices"),
                            Triple("genieacs_hosts", "Server GenieACS", activeScreenKey == "genieacs" && genieSubTabParam == "hosts")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 8. PETA & INVENTARIS ASET (DIGABUNG & EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.LocationOn,
                        label = "Peta & Aset Jaringan",
                        isExpanded = expandedSidebarGroup == "peta_aset",
                        isGroupActive = activeScreenKey == "map" || activeScreenKey == "assets",
                        onToggle = { toggleGroup("peta_aset") },
                        subItems = listOf(
                            Triple("map", "Peta Jaringan & Radius", activeScreenKey == "map"),
                            Triple("assets", "Asset Manager (ODP/ONT)", activeScreenKey == "assets")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 9. PESAN & HALAMAN STATIS (DIGABUNG & EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.ChatBubble,
                        label = "Pesan & Halaman",
                        isExpanded = expandedSidebarGroup == "pesan_halaman",
                        isGroupActive = activeScreenKey == "broadcast" || activeScreenKey == "pages",
                        onToggle = { toggleGroup("pesan_halaman") },
                        subItems = listOf(
                            Triple("broadcast", "Mengirim Pesan (WA/SMS)", activeScreenKey == "broadcast"),
                            Triple("pages", "Halaman Statis", activeScreenKey == "pages")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 10. PENGATURAN SISTEM (EXPANDABLE)
                    SidebarExpandableGroup(
                        icon = Icons.Filled.Settings,
                        label = "Pengaturan",
                        isExpanded = expandedSidebarGroup == "pengaturan",
                        isGroupActive = activeScreenKey == "settings",
                        onToggle = { toggleGroup("pengaturan") },
                        subItems = listOf(
                            Triple("settings_general", "Umum & Gateway", activeScreenKey == "settings" && settingsSubTabParam == "general"),
                            Triple("settings_localisation", "Lokalisasi & Label", activeScreenKey == "settings" && settingsSubTabParam == "localisation"),
                            Triple("settings_notifications", "Template Notifikasi", activeScreenKey == "settings" && settingsSubTabParam == "notifications"),
                            Triple("settings_maintenance", "Mode Maintenance", activeScreenKey == "settings" && settingsSubTabParam == "maintenance"),
                            Triple("settings_staff", "Staf & Password", activeScreenKey == "settings" && settingsSubTabParam == "staff"),
                            Triple("settings_db_logs", "Database & Log", activeScreenKey == "settings" && settingsSubTabParam == "db_logs"),
                            Triple("settings_shortcuts", "Shortcut Cepat", activeScreenKey == "settings" && settingsSubTabParam == "shortcuts")
                        ),
                        onSubItemClick = { navigateTo(it) }
                    )

                    // 11. WEB ADMIN PANEL
                    SidebarMainItem(
                        icon = Icons.Filled.Language,
                        label = "Web Admin Panel",
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
                        companyName = companyTitle,
                        isDarkMode = isDarkMode,
                        onMenuClick = {
                            companyTitle = ApiClient.companyName
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        onSearchClick = {
                            customerTypeParam = "All"
                            openAddCustomerDialog = false
                            navigateTo("customers")
                        },
                        onToggleTheme = onToggleTheme,
                        onProfileClick = { navigateTo("settings_general") }
                    )
                }
            },
            bottomBar = {
                ArnetaBottomBar(
                    currentTab = currentBottomTab,
                    unpaidBadgeCount = unpaidBadgeCount,
                    onTabSelected = { tab ->
                        if (!ApiClient.isLoggedIn()) {
                            onLogout()
                            return@ArnetaBottomBar
                        }
                        companyTitle = ApiClient.companyName
                        currentBottomTab = tab
                        activeScreenKey = when (tab) {
                            AdminNavTab.DASHBOARD -> "dashboard"
                            AdminNavTab.CUSTOMERS -> {
                                openAddCustomerDialog = false
                                customerTypeParam = "All"
                                "customers"
                            }
                            AdminNavTab.SERVICES -> {
                                servicesSubTabParam = "active_users"
                                "services"
                            }
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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 26.dp)
            ) {
                when (activeScreenKey) {
                    "dashboard" -> AdminDashboardScreen(
                        onNavigateToCustomers = { q ->
                            customerSearchParam = q
                            customerTypeParam = "All"
                            openAddCustomerDialog = false
                            navigateTo("customers")
                        },
                        onNavigateToPrepaid = { sub -> navigateTo(sub) },
                        onNavigateToSubScreen = { target -> navigateTo(target) },
                        onUnpaidCountLoaded = { cnt ->
                            unpaidBadgeCount = cnt
                            companyTitle = ApiClient.companyName
                        }
                    )
                    "customers", "add_customer" -> AdminCustomersScreen(
                        initialSearch = customerSearchParam,
                        initialType = customerTypeParam,
                        openAddDialogInitially = openAddCustomerDialog
                    )
                    "services", "active_users", "recharge_plan", "deposit_balance", "vouchers", "data_usage", "coupons", "refill_voucher" -> AdminServicesScreen(
                        initialSubTab = servicesSubTabParam
                    )
                    "menu_hub" -> AdminMenuHubScreen(
                        unpaidQrisCount = unpaidBadgeCount,
                        onOpenScreen = { key -> navigateTo(key) }
                    )
                    "network", "live_monitor", "online_users", "ros_logs", "routers", "pools" -> AdminNetworkAndOnlineScreen(
                        initialTab = networkSubTabParam
                    )
                    "genieacs" -> GenieAcsScreen(
                        initialTab = genieSubTabParam
                    )
                    "plans" -> AdminPlansScreen(
                        initialTab = plansSubTabParam
                    )
                    "map" -> AdminMapScreen()
                    "assets" -> AdminAssetsScreen()
                    "reports", "payment_gateway" -> AdminReportsAndPgScreen(
                        initialTab = activeScreenKey
                    )
                    "broadcast" -> AdminBroadcastMessageScreen()
                    "pages" -> AdminStaticPagesScreen()
                    "settings" -> AdminSettingsAndToolsScreen(
                        initialTab = settingsSubTabParam,
                        onCustomShortcutsChanged = {
                            quickShortcuts = ApiClient.customQuickShortcuts
                            companyTitle = ApiClient.companyName
                        },
                        onLogout = onLogout
                    )
                    "webview_admin" -> AdminInAppWebViewScreen(
                        initialRouteOrUrl = webViewRouteParam,
                        title = "${ApiClient.companyName} Web Admin",
                        onBack = { navigateTo("dashboard") }
                    )
                }
            }
        }
    }

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
                        navigateTo("settings_shortcuts")
                    }) {
                        Text("Atur Shortcut", fontSize = 12.sp, color = ArnetaGoldPrimary)
                    }
                }

                val shortcutDefinitions = listOf(
                    Triple("add_customer", "Add Customer Baru", "Tambah pelanggan Hotspot / PPPoE") to { navigateTo("add_customer") },
                    Triple("recharge_plan", "Isi Ulang Paket", "Aktifkan paket pelanggan & cetak struk") to { navigateTo("recharge_plan") },
                    Triple("deposit_balance", "Isi Ulang Saldo", "Top-up saldo deposit pelanggan") to { navigateTo("deposit_balance") },
                    Triple("generate_voucher", "Generate & Cetak Voucher", "Buat kode voucher massal") to { navigateTo("vouchers") },
                    Triple("online_users", "User Online & Kick Sesi", "Pantau & putuskan sesi MikroTik") to { navigateTo("online_users") },
                    Triple("audit_qris", "Audit Payment Gateway", "Verifikasi atau setujui manual tagihan PG") to { navigateTo("payment_gateway") },
                    Triple("live_monitor", "Live Speed MikroTik", "Pantau Mbps Download/Upload & CPU real-time") to { navigateTo("live_monitor") },
                    Triple("broadcast_wa", "Broadcast WhatsApp", "Kirim pesan ke pelanggan") to { navigateTo("broadcast") },
                    Triple("webview_admin", "Web Admin Panel", "Buka halaman web asli dengan SSO otomatis") to {
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
                            Icon(
                                imageVector = Icons.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = ArnetaGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SidebarExpandableGroup(
    icon: ImageVector,
    label: String,
    isExpanded: Boolean,
    isGroupActive: Boolean,
    onToggle: () -> Unit,
    subItems: List<Triple<String, String, Boolean>>,
    onSubItemClick: (String) -> Unit
) {
    val headerBg = when {
        isExpanded -> Color(0xFF283548)
        isGroupActive -> Color(0xFF312E81)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(headerBg)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isGroupActive || isExpanded) ArnetaGoldPrimary else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isGroupActive || isExpanded) FontWeight.Bold else FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronLeft,
            contentDescription = null,
            tint = if (isExpanded) Color(0xFF10B981) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp)
        )
    }

    AnimatedVisibility(visible = isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .background(Color(0xFF0F172A))
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF10B981))
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                subItems.forEach { (key, subLabel, isSelected) ->
                    SidebarSubItem(
                        label = subLabel,
                        selected = isSelected,
                        onClick = { onSubItemClick(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarMainItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF312E81) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = if (selected) ArnetaGoldPrimary else Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun SidebarSubItem(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF1E293B) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ArnetaGoldPrimary else Color.White.copy(alpha = 0.9f),
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}
