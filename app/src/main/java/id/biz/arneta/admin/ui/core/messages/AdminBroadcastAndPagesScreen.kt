package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.ArnetaCyanIncome
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1. LAYAR KIRIM PESAN & BROADCAST WHATSAPP / SMS / INBOX (Sesuai MainActivity.kt)
 */
@Composable
fun AdminBroadcastMessageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("single") }
    var selectedCustomer by remember { mutableStateOf<JSONObject?>(null) }
    var targetGroup by remember { mutableStateOf("all") }
    var viaChannel by remember { mutableStateOf("wa") }
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
 * 2. LAYAR TEMPLATE & HALAMAN STATIS (Sesuai MainActivity.kt)
 */
@Composable
fun AdminStaticPagesScreen() {
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
