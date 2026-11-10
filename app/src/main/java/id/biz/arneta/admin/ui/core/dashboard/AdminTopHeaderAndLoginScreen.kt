package id.biz.arneta.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch

private const val DEFAULT_ARNETA_URL = "https://phpnuxbill.arneta.biz.id"

/**
 * Helper untuk merapikan input Domain / IP Lokal agar user tidak salah ketik format URL
 */
internal fun normalizeServerUrlInput(rawInput: String, preferHttpLocal: Boolean): String {
    var clean = rawInput.trim()
    if (clean.isEmpty()) return DEFAULT_ARNETA_URL

    val routeIdx = clean.indexOf("?_route=")
    if (routeIdx > 0) clean = clean.substring(0, routeIdx)
    val apiIdx = clean.indexOf("/system/api.php", ignoreCase = true)
    if (apiIdx > 0) clean = clean.substring(0, apiIdx)

    clean = clean
        .replace("/index.php", "", ignoreCase = true)
        .removeSuffix("/system")
        .trimEnd('/', '?')

    if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
        val looksLikeLocalIp = preferHttpLocal ||
            clean.startsWith("localhost", ignoreCase = true) ||
            clean.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}.*"))
        clean = if (looksLikeLocalIp) "http://$clean" else "https://$clean"
    }
    return clean.trimEnd('/')
}

/**
 * Header Atas Panel Admin
 */
@Composable
fun ArnetaTopHeader(
    companyName: String,
    isDarkMode: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = Color(0xFF141B2D),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = companyName,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(86.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF312E81))
                        .border(1.dp, Color(0xFF4F46E5).copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { onSearchClick() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Cari Pelanggan",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                            contentDescription = "Mode Tema",
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onProfileClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profil Admin",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Layar Login Khusus Admin (Sesuai system/controllers/admin.php)
 */
@Composable
fun AdminLoginScreen(
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(ApiClient.baseUrl) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val isCustomServer = !serverUrl.equals(DEFAULT_ARNETA_URL, ignoreCase = true)
    val isLocalHttp = serverUrl.startsWith("http://", ignoreCase = true)
    val cleanHostDisplay = serverUrl
        .replace("https://", "", ignoreCase = true)
        .replace("http://", "", ignoreCase = true)

    val brandBadgeText = if (!isCustomServer) {
        "ARNETA ID"
    } else if (ApiClient.companyName != "Arneta.ID" && ApiClient.companyName.isNotBlank()) {
        ApiClient.companyName.uppercase()
    } else {
        cleanHostDisplay.substringBefore("/").substringBefore(":").uppercase().take(18)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF111827), Color(0xFF1E1B4B))
                )
            )
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33).copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(ArnetaGoldLight, ArnetaGoldPrimary, Color(0xFF312E81))),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(ArnetaGoldPrimary.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        .border(2.dp, ArnetaGoldPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Shield",
                        tint = ArnetaGoldPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = "WiFi",
                        tint = ArnetaGoldLight,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = ArnetaGoldPrimary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = brandBadgeText,
                        color = Color(0xFF0B0F19),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "PHPNuxBill Admin Control Center",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (errorMessage.isNotEmpty()) {
                    Surface(
                        color = ArnetaRedDanger.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ArnetaRedDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = errorMessage, color = Color(0xFFFCA5A5), fontSize = 12.5.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username Admin") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = ArnetaGoldPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password Admin") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = ArnetaGoldPrimary) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Lihat Password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                val borderColor = if (isCustomServer) ArnetaGreenSuccess.copy(alpha = 0.65f) else ArnetaGoldPrimary.copy(alpha = 0.45f)
                val containerBg = if (isCustomServer) ArnetaGreenSuccess.copy(alpha = 0.10f) else Color(0xFF0F172A).copy(alpha = 0.75f)

                Surface(
                    color = containerBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showServerDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isCustomServer) Icons.Filled.CheckCircle else Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = if (isCustomServer) ArnetaGreenSuccess else ArnetaGoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Server: $cleanHostDisplay",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCustomServer) Color.White else ArnetaGoldLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        color = when {
                                            !isCustomServer -> ArnetaGoldPrimary.copy(alpha = 0.22f)
                                            isLocalHttp -> ArnetaCyanIncome.copy(alpha = 0.25f)
                                            else -> ArnetaGreenSuccess.copy(alpha = 0.25f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                !isCustomServer -> "DEFAULT"
                                                isLocalHttp -> "LOKAL"
                                                else -> "KUSTOM"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when {
                                                !isCustomServer -> ArnetaGoldLight
                                                isLocalHttp -> ArnetaCyanIncome
                                                else -> ArnetaGreenSuccess
                                            },
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (!isCustomServer) {
                                        "Ketuk untuk ganti Domain / IP Server Lokal"
                                    } else {
                                        "Server kustom aktif (${if (isLocalHttp) "HTTP" else "HTTPS"}) • Ketuk untuk ubah"
                                    },
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Ubah Server",
                            tint = if (isCustomServer) ArnetaGreenSuccess else ArnetaGoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Masukkan Username dan Password Admin."
                            return@Button
                        }
                        isLoading = true
                        errorMessage = ""
                        ApiClient.baseUrl = serverUrl.trim()
                        scope.launch {
                            val res = ApiClient.request(
                                "admin_login",
                                mapOf("username" to username.trim(), "password" to password, "role" to "admin")
                            )
                            isLoading = false
                            if (res.status && res.data != null) {
                                ApiClient.token = res.data.optString("token")
                                val adm = res.data.optJSONObject("admin")
                                if (adm != null) {
                                    ApiClient.adminName = adm.optString("fullname", username)
                                    ApiClient.adminUsername = adm.optString("username", username)
                                    ApiClient.adminRole = adm.optString("user_type", "SuperAdmin")
                                }
                                val comp = res.data.optJSONObject("company")
                                if (comp != null) {
                                    ApiClient.companyName = comp.optString("name", "Arneta.ID")
                                    ApiClient.currencyCode = comp.optString("currency", "Rp.")
                                }
                                onLoginSuccess()
                            } else {
                                errorMessage = res.message
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArnetaGoldPrimary,
                        contentColor = Color(0xFF0B0F19)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF0B0F19),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MASUK DASHBOARD ADMIN", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showServerDialog) {
        ServerConfigDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerDialog = false },
            onSaveUrl = { newNormalizedUrl ->
                serverUrl = newNormalizedUrl
                ApiClient.baseUrl = newNormalizedUrl
                if (newNormalizedUrl.equals(DEFAULT_ARNETA_URL, ignoreCase = true)) {
                    ApiClient.companyName = "Arneta.ID"
                }
                errorMessage = ""
                showServerDialog = false
            }
        )
    }
}

@Composable
private fun ServerConfigDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialIsHttp = currentUrl.startsWith("http://", ignoreCase = true)

    var useLocalHttp by remember { mutableStateOf(initialIsHttp) }
    var hostInput by remember {
        mutableStateOf(
            currentUrl
                .replace("https://", "", ignoreCase = true)
                .replace("http://", "", ignoreCase = true)
        )
    }
    var isTesting by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<Boolean?>(null) }
    var testResultMessage by remember { mutableStateOf("") }

    val previewFinalUrl = remember(hostInput, useLocalHttp) {
        normalizeServerUrlInput(hostInput, useLocalHttp)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = ArnetaGoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Atur Server PHPNuxBill", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Gunakan aplikasi ini pada server PHPNuxBill Anda sendiri (Domain Publik maupun IP Server Lokal).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = !useLocalHttp,
                        onClick = {
                            useLocalHttp = false
                            testResultStatus = null
                        },
                        label = { Text("🌐 Domain (HTTPS)", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = useLocalHttp,
                        onClick = {
                            useLocalHttp = true
                            testResultStatus = null
                        },
                        label = { Text("🏠 IP Lokal (HTTP)", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = {
                        hostInput = it
                        testResultStatus = null
                    },
                    label = {
                        Text(if (useLocalHttp) "IP Server Lokal / Port / Folder" else "Nama Domain Billing")
                    },
                    placeholder = {
                        Text(
                            if (useLocalHttp) "192.168.1.100 atau 10.8.0.1/billing"
                            else "billing.wifianda.id",
                            fontSize = 12.sp
                        )
                    },
                    prefix = {
                        Text(
                            text = if (useLocalHttp) "http://" else "https://",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaGoldPrimary
                        )
                    },
                    trailingIcon = {
                        if (hostInput.isNotEmpty()) {
                            IconButton(onClick = {
                                hostInput = ""
                                testResultStatus = null
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Endpoint: $previewFinalUrl/system/api.php?r=plugin/mobile_api",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaCyanIncome
                        )
                        Text(
                            text = "Pastikan plugin phpnuxbill-mobile-api-plugin sudah terpasang di menu Plugin Manager server Anda.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }

                if (testResultStatus != null) {
                    val ok = testResultStatus == true
                    Surface(
                        color = if (ok) ArnetaGreenSuccess.copy(alpha = 0.15f) else ArnetaRedDanger.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (ok) ArnetaGreenSuccess else ArnetaRedDanger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = testResultMessage,
                                fontSize = 11.5.sp,
                                color = if (ok) ArnetaGreenSuccess else ArnetaRedDanger,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        enabled = !isTesting,
                        onClick = {
                            isTesting = true
                            testResultStatus = null
                            val previousUrl = ApiClient.baseUrl
                            ApiClient.baseUrl = previewFinalUrl
                            scope.launch {
                                val res = ApiClient.request("index")
                                isTesting = false
                                testResultStatus = res.status
                                testResultMessage = if (res.status) {
                                    "Terhubung! ${res.message}"
                                } else {
                                    "Gagal terhubung: ${res.message}"
                                }
                                ApiClient.baseUrl = previousUrl
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                color = ArnetaGoldPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mengecek...", fontSize = 11.5.sp)
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tes Koneksi", fontSize = 11.5.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            useLocalHttp = false
                            hostInput = "phpnuxbill.arneta.biz.id"
                            testResultStatus = null
                        }
                    ) {
                        Text("Reset Default", fontSize = 11.5.sp, color = ArnetaGoldPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveUrl(previewFinalUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaGoldPrimary,
                    contentColor = Color(0xFF0B0F19)
                )
            ) {
                Text("Simpan Server", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
