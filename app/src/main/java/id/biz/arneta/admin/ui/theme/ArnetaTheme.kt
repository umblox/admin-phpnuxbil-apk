package id.biz.arneta.admin.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ArnetaNavyDark = Color(0xFF0B1120)
val ArnetaSidebarDark = Color(0xFF141B2D)
val ArnetaCardDark = Color(0xFF1E293B)
val ArnetaIndigoAccent = Color(0xFF3730A3)
val ArnetaPurpleChart = Color(0xFF6366F1)

val ArnetaGoldPrimary = Color(0xFFF59E0B)
val ArnetaGoldLight = Color(0xFFFDE047)
val ArnetaCyanIncome = Color(0xFF00B4D8)
val ArnetaOrangeActive = Color(0xFFF38137)
val ArnetaGreenSuccess = Color(0xFF10B981)
val ArnetaRedDanger = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = ArnetaGoldPrimary,
    onPrimary = Color(0xFF0B0F19),
    secondary = ArnetaCyanIncome,
    tertiary = ArnetaOrangeActive,
    background = ArnetaNavyDark,
    surface = ArnetaCardDark,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF283548),
    outline = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    secondary = ArnetaCyanIncome,
    tertiary = ArnetaOrangeActive,
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1)
)

enum class AdminNavTab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dasbor", Icons.Outlined.SpaceDashboard),
    CUSTOMERS("customers", "Pelanggan", Icons.Outlined.PeopleAlt),
    SERVICES("services", "Layanan", Icons.Outlined.ConfirmationNumber),
    MENU_HUB("menu_hub", "Menu Hub", Icons.Outlined.GridView)
}

@Composable
fun ArnetaAdminTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val currentDensity = LocalDensity.current
    val configuration = LocalConfiguration.current

    val safeFontScale = currentDensity.fontScale.coerceIn(0.85f, 1.08f)
    val screenWidthDp = configuration.screenWidthDp
    val adaptiveDensity = if (screenWidthDp in 1..339) {
        currentDensity.density * (screenWidthDp / 360f)
    } else {
        currentDensity.density
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = adaptiveDensity,
            fontScale = safeFontScale
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

/**
 * Floating Safe-Area Bottom Navigation Bar
 * Menggunakan Box non-clipping setinggi 74.dp sehingga lingkaran emas & ikon petir tidak pernah terpotong
 */
@Composable
fun ArnetaBottomBar(
    currentTab: AdminNavTab,
    onTabSelected: (AdminNavTab) -> Unit,
    onQuickActionClick: () -> Unit,
    unpaidBadgeCount: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(74.dp)
    ) {
        // Dock Latar Belakang (Rata Bawah setinggi 62.dp)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 12.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(62.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            ArnetaGoldPrimary.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            ArnetaGoldPrimary.copy(alpha = 0.35f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    tab = AdminNavTab.DASHBOARD,
                    selected = currentTab == AdminNavTab.DASHBOARD,
                    onClick = { onTabSelected(AdminNavTab.DASHBOARD) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    tab = AdminNavTab.CUSTOMERS,
                    selected = currentTab == AdminNavTab.CUSTOMERS,
                    onClick = { onTabSelected(AdminNavTab.CUSTOMERS) },
                    modifier = Modifier.weight(1f)
                )

                // Ruang Tengah untuk Tombol Shortcut Petir Emas
                Spacer(modifier = Modifier.width(62.dp))

                BottomNavItem(
                    tab = AdminNavTab.SERVICES,
                    selected = currentTab == AdminNavTab.SERVICES,
                    onClick = { onTabSelected(AdminNavTab.SERVICES) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    tab = AdminNavTab.MENU_HUB,
                    selected = currentTab == AdminNavTab.MENU_HUB,
                    badgeCount = unpaidBadgeCount,
                    onClick = { onTabSelected(AdminNavTab.MENU_HUB) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tombol Tengah Menonjol: Aksi Cepat (Berada di Alignment.TopCenter di dalam ruang 74.dp sehingga 100% utuh tidak terpotong)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(56.dp)
                .shadow(10.dp, CircleShape, spotColor = ArnetaGoldPrimary)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(ArnetaGoldLight, ArnetaGoldPrimary, Color(0xFFD97706))
                    )
                )
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                .clickable { onQuickActionClick() }
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Aksi Cepat",
                tint = Color(0xFF0B0F19),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: AdminNavTab,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "nav_scale"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) ArnetaGoldPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        label = "nav_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = ArnetaRedDanger, contentColor = Color.White) {
                        Text(text = badgeCount.toString(), fontSize = 9.sp)
                    }
                }
            }
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
