package id.biz.arneta.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*

private data class HubMenuItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badgeCount: Int = 0
)

/**
 * LAYAR MENU HUB LENGKAP (Sesuai Pemanggilan di MainActivity.kt)
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
            key = "genieacs",
            title = "GenieACS (TR-069) Manager",
            subtitle = "Pantau ONT/Router, redaman optik, WAN & ubah WiFi pelanggan",
            icon = Icons.Filled.SettingsInputAntenna,
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
