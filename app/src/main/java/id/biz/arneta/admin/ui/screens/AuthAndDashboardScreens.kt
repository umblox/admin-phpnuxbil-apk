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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_ARNETA_URL = "https://phpnuxbill.arneta.biz.id"

// Warna 4 Kartu Dasbor
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
 * Helper untuk merapikan input Domain / IP Lokal agar user tidak salah ketik format URL
 */
private fun normalizeServerUrlInput(rawInput: String, preferHttpLocal: Boolean): String {
    var clean = rawInput.trim()
    if (clean.isEmpty()) return DEFAULT_ARNETA_URL

    // Hapus bagian path yang tidak sengaja ikut ter-copy (seperti /?_route=... atau /system/api.php)
    val routeIdx = clean.indexOf("?_route=")
    if (routeIdx > 0) clean = clean.substring(0, routeIdx)
    clean = clean
        .replace("/system/api.php", "", ignoreCase = true)
        .replace("/index.php", "", ignoreCase = true)
        .trimEnd('/', '?')

    if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
        // Deteksi otomatis apakah input berupa IP Lokal (misal 192.168.x.x, 10.x.x.x, 172.x.x.x, localhost)
        val looksLikeLocalIp = preferHttpLocal ||
            clean.startsWith("localhost", ignoreCase = true) ||
            clean.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}.*"))
        clean = if (looksLikeLocalIp) "http://$clean" else "https://$clean"
    }
    return clean.trimEnd('/')
}

/**
 * Header Atas Persis Web Arneta.ID
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
 * Layar Login Admin dengan Tombol & Dialog Ganti Domain / Server Lokal
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

    // Deteksi apakah user masih menggunakan server default atau sudah mengganti ke server sendiri
    val isCustomServer = !serverUrl.equals(DEFAULT_ARNETA_URL, ignoreCase = true)
    val isLocalHttp = serverUrl.startsWith("http://", ignoreCase = true)
    val cleanHostDisplay = serverUrl
        .replace("https://", "", ignoreCase = true)
        .replace("http://", "", ignoreCase = true)

    // Teks Badge Utama: "ARNETA ID" jika default, atau nama host/perusahaan jika sudah diganti user lain
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
                // Logo Perisai Emas Wi-Fi
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
                        contentDescription = "Arneta Shield",
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

                // TOMBOL GANTI DOMAIN / SERVER LOKAL (Tampilan Berbeda Sebelum & Sesudah Disetting)
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
                                mapOf("username" to username.trim(), "password" to password)
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

/**
 * Dialog Pop-Up Pengaturan Domain / IP Server Lokal PHPNuxBill
 * Dilengkapi fitur Pilih Protokol (HTTPS / HTTP Lokal), Auto-Format URL, Tes Koneksi, & Reset Default
 */
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

                // Pilihan Jenis Koneksi: Domain HTTPS vs IP Lokal HTTP
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

                // Preview URL Lengkap
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "URL Tujuan: $previewFinalUrl",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaCyanIncome
                        )
                        Text(
                            text = "Pastikan file apiv1.php sudah diunggah ke folder /system/controllers/ di server Anda.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }

                // Hasil Tes Koneksi
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

                // Baris Tombol Tes Koneksi & Reset ke Default Arneta.ID
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

/**
 * Layar Dasbor Utama Admin
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

    // Data Kartu 2x2
    var incomeToday by remember { mutableDoubleStateOf(0.0) }
    var incomeMonth by remember { mutableDoubleStateOf(0.0) }
    var activeExpiredLabel by remember { mutableStateOf("0/0") }
    var totalCustomers by remember { mutableIntStateOf(0) }
    var totalCustomerBalance by remember { mutableDoubleStateOf(0.0) }
    var datePeriod by remember { mutableStateOf("01 Oct 2026 / 20 Oct 2026") }
    var unpaidQrisCount by remember { mutableIntStateOf(0) }

    // Data Grafik & Tabel
    var regChartList by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var salesChartList by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var stockList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var expiringList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var routerStatusList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var collapseRegChart by remember { mutableStateOf(false) }
    var collapseSalesChart by remember { mutableStateOf(false) }
    var collapseStocks by remember { mutableStateOf(false) }

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
                    text = "Dasbor",
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

        // GRID 2x2 KARTU UTAMA
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
                                    text = "$unpaidQrisCount Tagihan QRIS Menunggu Pembayaran",
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
