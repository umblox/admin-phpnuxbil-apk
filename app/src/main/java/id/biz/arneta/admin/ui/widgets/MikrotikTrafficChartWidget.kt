package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

internal fun formatSpeedBps(bps: Long): String {
    if (bps <= 0L) return "0 bps"
    if (bps >= 1_000_000L) {
        return String.format("%.2f Mbps", bps / 1_000_000f)
    }
    if (bps >= 1_000L) {
        return String.format("%.1f Kbps", bps / 1_000f)
    }
    return "$bps bps"
}

/**
 * KOMPONEN GRAFIK & BAR SPEEDOMETER MURNI DARI /interface/monitor-traffic
 */
@Composable
internal fun NativeMikrotikTrafficChart(
    rxPointsKbps: List<Float>,
    txPointsKbps: List<Float>,
    rxBps: Long,
    txBps: Long,
    rxLabel: String,
    txLabel: String,
    modifier: Modifier = Modifier
) {
    val currentRxKbps = rxBps / 1000f
    val currentTxKbps = txBps / 1000f

    val peakScaleKbps = remember(rxPointsKbps, txPointsKbps, currentRxKbps, currentTxKbps) {
        val maxRx = rxPointsKbps.maxOrNull() ?: 0f
        val maxTx = txPointsKbps.maxOrNull() ?: 0f
        max(512f, max(max(maxRx, maxTx), max(currentRxKbps, currentTxKbps)) * 1.25f)
    }

    val animRxRatio by animateFloatAsState(
        targetValue = (currentRxKbps / peakScaleKbps).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "rx_bar_anim"
    )
    val animTxRatio by animateFloatAsState(
        targetValue = (currentTxKbps / peakScaleKbps).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "tx_bar_anim"
    )

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = Color(0xFF0B1120),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaCyanIncome.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RX (rx-bits-per-second)", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rxLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = ArnetaCyanIncome
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animRxRatio },
                        color = ArnetaCyanIncome,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Surface(
                color = Color(0xFF0B1120),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TX (tx-bits-per-second)", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = txLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = ArnetaGoldPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animTxRatio },
                        color = ArnetaGoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1120))
                .padding(10.dp)
        ) {
            val w = size.width
            val h = size.height

            for (i in 0..4) {
                val y = h * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            fun drawTrafficLine(points: List<Float>, strokeColor: Color, fillAlpha: Float) {
                if (points.size < 2) return
                val strokePath = Path()
                val fillPath = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                val coords = points.mapIndexed { idx, v ->
                    val x = idx * stepX
                    val ratio = (v / peakScaleKbps).coerceIn(0f, 1f)
                    val y = h - (ratio * (h * 0.88f)) - 2f
                    Offset(x, y)
                }

                strokePath.moveTo(coords.first().x, coords.first().y)
                fillPath.moveTo(coords.first().x, h)
                fillPath.lineTo(coords.first().x, coords.first().y)

                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    strokePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(coords.last().x, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = fillAlpha), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = strokeColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            drawTrafficLine(txPointsKbps, ArnetaGoldPrimary, 0.22f)
            drawTrafficLine(rxPointsKbps, ArnetaCyanIncome, 0.32f)
        }

        Spacer(modifier = Modifier.height(6.dp))

        val scaleLabel = if (peakScaleKbps >= 1000f) {
            String.format("%.1f Mbps", peakScaleKbps / 1000f)
        } else {
            String.format("%.0f Kbps", peakScaleKbps)
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔵 Garis Biru: RX Traffic", fontSize = 11.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.SemiBold)
            Text("🟡 Garis Emas: TX Traffic", fontSize = 11.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.SemiBold)
            Text("Skala Grafik: $scaleLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

/**
 * DIALOG KONFIRMASI KICK USER ONLINE
 */
@Composable
fun ConfirmKickUserDialog(
    userObj: JSONObject,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val uname = userObj.optString("username", "-")
    val service = userObj.optString("service", userObj.optString("service_type", "Hotspot"))
    val router = userObj.optString("router", userObj.optString("routers", "-"))
    val ipAddr = userObj.optString("ip_address", "-")
    val macAddr = userObj.optString("mac_address", "-")
    val uptime = userObj.optString("uptime", "-")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ArnetaRedDanger.copy(alpha = 0.2f))
                        .border(1.dp, ArnetaRedDanger, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = null,
                        tint = ArnetaRedDanger,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Konfirmasi Putus Sesi",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Tindakan ini akan memutus koneksi aktif",
                        fontSize = 11.sp,
                        color = ArnetaGoldLight
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Apakah Anda yakin ingin memutus (Kick) sesi koneksi internet pelanggan berikut dari MikroTik?",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaRedDanger.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "@$uname",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = ArnetaGoldPrimary
                            )
                            Surface(
                                color = if (service.equals("PPPoE", ignoreCase = true)) ArnetaIndigoAccent else ArnetaCyanIncome,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = service,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Text("Router: $router", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("IP Address: $ipAddr", fontSize = 11.5.sp, color = ArnetaCyanIncome)
                        Text("MAC / Caller: $macAddr", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.75f))
                        Text("Uptime Sesi: $uptime", fontSize = 11.5.sp, color = ArnetaGreenSuccess)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaRedDanger,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ya, Kick Sekarang", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Batal", fontSize = 12.5.sp)
            }
        }
    )
}

/**
 * DIALOG DETAIL LENGKAP PELANGGAN & GRAFIK LIVE SPEED REAL-TIME
 */
@Composable
fun UserDetailAndLiveSpeedDialog(
    initialUserObj: JSONObject,
    onDismiss: () -> Unit,
    onUserKicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initUsername = initialUserObj.optString("username", "")
    val initCustId = initialUserObj.optInt("customer_id", 0)
    val initRouter = initialUserObj.optString("router", initialUserObj.optString("routers", ""))
    val initService = initialUserObj.optString("service", initialUserObj.optString("type", ""))

    var liveData by remember { mutableStateOf<JSONObject?>(null) }
    var isLoadingInitial by remember { mutableStateOf(true) }
    var isPollingSpeed by remember { mutableStateOf(true) }
    var showKickConfirm by remember { mutableStateOf(false) }

    var dlHistoryKbps by remember { mutableStateOf(List(24) { 0f }) }
    var ulHistoryKbps by remember { mutableStateOf(List(24) { 0f }) }
    var currentDlBps by remember { mutableLongStateOf(0L) }
    var currentUlBps by remember { mutableLongStateOf(0L) }
    var currentDlLabel by remember { mutableStateOf("0 bps") }
    var currentUlLabel by remember { mutableStateOf("0 bps") }
    var lastPollTime by remember { mutableStateOf("-") }

    suspend fun fetchUserLiveDetail() {
        val res = ApiClient.request(
            "admin_user_live_traffic",
            mapOf(
                "username" to initUsername,
                "customer_id" to initCustId,
                "router" to initRouter,
                "service" to initService
            )
        )
        if (res.status && res.data != null) {
            val d = res.data
            liveData = d
            val dlBps = d.optLong("tx_bps", 0L)
            val ulBps = d.optLong("rx_bps", 0L)
            currentDlBps = dlBps
            currentUlBps = ulBps
            currentDlLabel = d.optString("download_speed_label", formatSpeedBps(dlBps))
            currentUlLabel = d.optString("upload_speed_label", formatSpeedBps(ulBps))

            val dlKbps = (dlBps / 1000f).coerceAtLeast(0f)
            val ulKbps = (ulBps / 1000f).coerceAtLeast(0f)
            dlHistoryKbps = dlHistoryKbps.drop(1) + dlKbps
            ulHistoryKbps = ulHistoryKbps.drop(1) + ulKbps

            val cal = java.util.Calendar.getInstance()
            lastPollTime = String.format(
                "%02d:%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND)
            )
        }
        isLoadingInitial = false
    }

    LaunchedEffect(initUsername, initCustId, isPollingSpeed) {
        fetchUserLiveDetail()
        while (isActive && isPollingSpeed) {
            delay(1600L)
            fetchUserLiveDetail()
        }
    }

    val custObj = liveData?.optJSONObject("customer")
    val pkgObj = liveData?.optJSONObject("active_package")
    val customFieldsArr = liveData?.optJSONArray("custom_fields") ?: JSONArray()
    val recentTrxArr = liveData?.optJSONArray("recent_transactions") ?: JSONArray()

    val isOnline = liveData?.optBoolean("online", initialUserObj.has("ros_id")) ?: initialUserObj.has("ros_id")
    val displayUsername = liveData?.optString("username")?.ifEmpty { initUsername } ?: initUsername
    val displayFullname = custObj?.optString("fullname")?.ifEmpty { null }
        ?: initialUserObj.optString("fullname").ifEmpty { displayUsername }
    val displayService = liveData?.optString("service")?.ifEmpty { null }
        ?: initialUserObj.optString("service", initialUserObj.optString("service_type", "Hotspot"))
    val displayRouter = liveData?.optString("router")?.ifEmpty { null }
        ?: initialUserObj.optString("router", initialUserObj.optString("routers", "-"))
    val displayIp = liveData?.optString("ip_address")?.ifEmpty { null }
        ?: initialUserObj.optString("ip_address", initialUserObj.optString("pppoe_ip", "-"))
    val displayMac = liveData?.optString("mac_address")?.ifEmpty { null }
        ?: initialUserObj.optString("mac_address", "-")
    val displayUptime = liveData?.optString("uptime")?.ifEmpty { null }
        ?: initialUserObj.optString("uptime", if (isOnline) "0s" else "Offline")
    val displaySessionLeft = liveData?.optString("session_time", "-") ?: "-"
    val displayUploadBytes = liveData?.optString("upload_bytes")?.ifEmpty { null }
        ?: initialUserObj.optString("upload", "0 B")
    val displayDownloadBytes = liveData?.optString("download_bytes")?.ifEmpty { null }
        ?: initialUserObj.optString("download", "0 B")
    val displayTotalBytes = liveData?.optString("total_bytes")?.ifEmpty { null }
        ?: initialUserObj.optString("total", "0 B")

    val phoneStr = custObj?.optString("phonenumber")?.takeIf { it != "-" && it.isNotEmpty() }
        ?: initialUserObj.optString("phonenumber", "")
    val emailStr = custObj?.optString("email")?.takeIf { it != "-" && it.isNotEmpty() }
        ?: initialUserObj.optString("email", "-")
    val addressStr = custObj?.optString("address")?.takeIf { it != "-" && it.isNotEmpty() }
        ?: initialUserObj.optString("address", "-")
    val cityStr = custObj?.optString("city")?.takeIf { it != "-" && it.isNotEmpty() }
        ?: initialUserObj.optString("city", "-")
    val balanceVal = custObj?.optDouble("balance")
        ?: initialUserObj.optDouble("balance", 0.0)
    val pppoeUserStr = custObj?.optString("pppoe_username")?.ifEmpty { null }
        ?: initialUserObj.optString("pppoe_username", "")
    val pppoeIpStr = custObj?.optString("pppoe_ip")?.ifEmpty { null }
        ?: initialUserObj.optString("pppoe_ip", "")
    val coordsStr = custObj?.optString("coordinates")?.ifEmpty { null }
        ?: initialUserObj.optString("coordinates", "")

    val planName = pkgObj?.optString("namebp")?.ifEmpty { null }
        ?: initialUserObj.optString("namebp", "-")
    val planStatus = pkgObj?.optString("status")?.ifEmpty { null }
        ?: initialUserObj.optString("status", "-")
    val planPrice = pkgObj?.optDouble("price")
        ?: initialUserObj.optDouble("price", 0.0)
    val planRate = pkgObj?.optString("rate_label", "-") ?: "-"
    val planMethod = pkgObj?.optString("method")?.ifEmpty { null }
        ?: initialUserObj.optString("method", "-")
    val planRecharged = if (pkgObj != null) {
        "${pkgObj.optString("recharged_on")} ${pkgObj.optString("recharged_time")}".trim()
    } else {
        "${initialUserObj.optString("recharged_on")} ${initialUserObj.optString("recharged_time")}".trim()
    }
    val planExpiration = if (pkgObj != null) {
        "${pkgObj.optString("expiration")} ${pkgObj.optString("time")}".trim()
    } else {
        "${initialUserObj.optString("expiration")} ${initialUserObj.optString("time")}".trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) ArnetaGreenSuccess.copy(alpha = 0.2f)
                                else ArnetaGoldPrimary.copy(alpha = 0.2f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isOnline) ArnetaGreenSuccess else ArnetaGoldPrimary,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = displayFullname.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOnline) ArnetaGreenSuccess else ArnetaGoldPrimary,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayFullname,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "@$displayUsername",
                                fontSize = 11.5.sp,
                                color = ArnetaGoldLight
                            )
                            Surface(
                                color = if (isOnline) ArnetaGreenSuccess.copy(alpha = 0.2f) else ArnetaRedDanger.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isOnline) "ONLINE" else "OFFLINE",
                                    color = if (isOnline) ArnetaGreenSuccess else ArnetaRedDanger,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Surface(
                                color = ArnetaCyanIncome.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = displayService,
                                    color = ArnetaCyanIncome,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline && isPollingSpeed) ArnetaGreenSuccess else ArnetaOrangeActive)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Grafik Live Speed Pelanggan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = ArnetaGoldPrimary
                                    )
                                    Text(
                                        text = if (isLoadingInitial) "Menghubungkan ke MikroTik..."
                                        else "Router: $displayRouter • Update: $lastPollTime",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                            Switch(
                                checked = isPollingSpeed,
                                onCheckedChange = { isPollingSpeed = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        CustomerLiveSpeedChart(
                            dlPointsKbps = dlHistoryKbps,
                            ulPointsKbps = ulHistoryKbps,
                            dlBps = currentDlBps,
                            ulBps = currentUlBps,
                            dlLabel = currentDlLabel,
                            ulLabel = currentUlLabel
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "↓ DL: $displayDownloadBytes",
                                fontSize = 10.5.sp,
                                color = ArnetaCyanIncome,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "↑ UL: $displayUploadBytes",
                                fontSize = 10.5.sp,
                                color = ArnetaGoldLight,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Total: $displayTotalBytes",
                                fontSize = 10.5.sp,
                                color = ArnetaGreenSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Detail Koneksi & Sesi MikroTik",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = ArnetaCyanIncome
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        DetailInfoRow("Status Koneksi", if (isOnline) "Aktif di MikroTik ($displayUptime)" else "Sedang Offline")
                        DetailInfoRow("IP Address", displayIp)
                        DetailInfoRow("MAC / Caller ID", displayMac)
                        DetailInfoRow("Router / Server", "$displayRouter (${liveData?.optString("server", "-") ?: "-"})")
                        if (displaySessionLeft != "-" && displaySessionLeft.isNotEmpty()) {
                            DetailInfoRow("Sisa Waktu Sesi", displaySessionLeft)
                        }
                        if (!pppoeUserStr.isNullOrEmpty()) {
                            DetailInfoRow("PPPoE Username", pppoeUserStr)
                        }
                        if (!pppoeIpStr.isNullOrEmpty()) {
                            DetailInfoRow("Remote IP PPPoE", pppoeIpStr)
                        }
                    }
                }

                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Informasi Paket & Profil Pelanggan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = ArnetaGoldPrimary
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        DetailInfoRow(
                            "Paket Langganan",
                            "$planName (${if (planStatus.equals("on", true)) "AKTIF" else "KEDALUWARSA"})"
                        )
                        DetailInfoRow("Harga & Metode", "${formatRupiah(planPrice)} • $planMethod")
                        if (planRate != "-") {
                            DetailInfoRow("Limit Bandwidth", planRate)
                        }
                        if (planRecharged.isNotEmpty()) {
                            DetailInfoRow("Tgl Transaksi", planRecharged)
                        }
                        if (planExpiration.isNotEmpty()) {
                            DetailInfoRow("Masa Aktif s/d", planExpiration)
                        }
                        DetailInfoRow("Saldo Deposit", formatRupiah(balanceVal))
                        DetailInfoRow("No. WhatsApp", phoneStr.ifEmpty { "-" })
                        if (emailStr != "-" && emailStr.isNotEmpty()) {
                            DetailInfoRow("Email", emailStr)
                        }
                        if (cityStr != "-" && cityStr.isNotEmpty()) {
                            DetailInfoRow("Kota / Area", cityStr)
                        }
                        if (addressStr != "-" && addressStr.isNotEmpty()) {
                            DetailInfoRow("Alamat", addressStr)
                        }
                        if (!coordsStr.isNullOrEmpty()) {
                            DetailInfoRow("Koordinat", coordsStr)
                        }

                        if (customFieldsArr.length() > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Atribut Tambahan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldLight)
                            for (i in 0 until customFieldsArr.length()) {
                                val cf = customFieldsArr.optJSONObject(i) ?: continue
                                DetailInfoRow(cf.optString("name", "-"), cf.optString("value", "-"))
                            }
                        }
                    }
                }

                if (recentTrxArr.length() > 0) {
                    Surface(
                        color = Color(0xFF0B1120),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Riwayat Transaksi Terakhir",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = ArnetaGreenSuccess
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            for (i in 0 until recentTrxArr.length()) {
                                val tr = recentTrxArr.optJSONObject(i) ?: continue
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${tr.optString("invoice")} • ${tr.optString("plan_name")}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${tr.optString("recharged_on")} ${tr.optString("recharged_time")} (${tr.optString("method")})",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = formatRupiah(tr.optDouble("price", 0.0)),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaGoldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (phoneStr.isNotEmpty() && phoneStr != "-") {
                    FilledTonalButton(
                        onClick = {
                            val msg = "Halo *$displayFullname* (@$displayUsername),\nInformasi layanan ${ApiClient.companyName}:\nPaket: *$planName*\nMasa Aktif s/d: *$planExpiration*"
                            id.biz.arneta.admin.util.BluetoothPrinterHelper.shareToWhatsApp(context, msg, phoneStr)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ArnetaGreenSuccess.copy(alpha = 0.2f),
                            contentColor = ArnetaGreenSuccess
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WA", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }

                if (isOnline) {
                    Button(
                        onClick = { showKickConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArnetaRedDanger,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kick Sesi", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaGoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tutup", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    )

    if (showKickConfirm) {
        val targetJson = liveData ?: initialUserObj
        ConfirmKickUserDialog(
            userObj = targetJson,
            onDismiss = { showKickConfirm = false },
            onConfirmed = {
                showKickConfirm = false
                scope.launch {
                    val kRes = ApiClient.request(
                        "admin_kick_user",
                        mapOf(
                            "router" to displayRouter,
                            "router_id" to (liveData?.optInt("router_id") ?: initialUserObj.optInt("router_id")),
                            "username" to displayUsername,
                            "service" to displayService,
                            "ros_id" to (liveData?.optString("ros_id") ?: initialUserObj.optString("ros_id"))
                        )
                    )
                    Toast.makeText(context, kRes.message, Toast.LENGTH_SHORT).show()
                    onUserKicked()
                    fetchUserLiveDetail()
                }
            }
        )
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun CustomerLiveSpeedChart(
    dlPointsKbps: List<Float>,
    ulPointsKbps: List<Float>,
    dlBps: Long,
    ulBps: Long,
    dlLabel: String,
    ulLabel: String
) {
    val currentDlKbps = dlBps / 1000f
    val currentUlKbps = ulBps / 1000f

    val peakScaleKbps = remember(dlPointsKbps, ulPointsKbps, currentDlKbps, currentUlKbps) {
        val maxDl = dlPointsKbps.maxOrNull() ?: 0f
        val maxUl = ulPointsKbps.maxOrNull() ?: 0f
        max(256f, max(max(maxDl, maxUl), max(currentDlKbps, currentUlKbps)) * 1.25f)
    }

    val animDlRatio by animateFloatAsState(
        targetValue = (currentDlKbps / peakScaleKbps).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "cust_dl_anim"
    )
    val animUlRatio by animateFloatAsState(
        targetValue = (currentUlKbps / peakScaleKbps).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "cust_ul_anim"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = Color(0xFF141B2D),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaCyanIncome.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DOWNLOAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dlLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = ArnetaCyanIncome
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { animDlRatio },
                        color = ArnetaCyanIncome,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Surface(
                color = Color(0xFF141B2D),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UPLOAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ulLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = ArnetaGoldPrimary
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { animUlRatio },
                        color = ArnetaGoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141B2D))
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height

            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            fun drawLineSeries(points: List<Float>, color: Color, alphaFill: Float) {
                if (points.size < 2) return
                val strokePath = Path()
                val fillPath = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                val coords = points.mapIndexed { idx, v ->
                    val x = idx * stepX
                    val ratio = (v / peakScaleKbps).coerceIn(0f, 1f)
                    val y = h - (ratio * (h * 0.86f)) - 2f
                    Offset(x, y)
                }

                strokePath.moveTo(coords.first().x, coords.first().y)
                fillPath.moveTo(coords.first().x, h)
                fillPath.lineTo(coords.first().x, coords.first().y)

                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    strokePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(coords.last().x, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = alphaFill), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = color,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
            }

            drawLineSeries(ulPointsKbps, ArnetaGoldPrimary, 0.22f)
            drawLineSeries(dlPointsKbps, ArnetaCyanIncome, 0.32f)
        }

        Spacer(modifier = Modifier.height(4.dp))

        val scaleText = if (peakScaleKbps >= 1000f) {
            String.format("%.1f Mbps", peakScaleKbps / 1000f)
        } else {
            String.format("%.0f Kbps", peakScaleKbps)
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔵 Download", fontSize = 10.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.SemiBold)
            Text("🟡 Upload", fontSize = 10.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.SemiBold)
            Text("Skala: $scaleText", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}
