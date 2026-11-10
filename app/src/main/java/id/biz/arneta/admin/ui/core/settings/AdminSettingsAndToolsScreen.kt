package id.biz.arneta.admin.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR PENGATURAN SISTEM, LOKALISASI, MAINTENANCE, TEMPLATE NOTIFIKASI, STAF, DB & SHORTCUT
 * (100% Sesuai system/controllers/settings.php)
 */
@Composable
fun AdminSettingsAndToolsScreen(
    onCustomShortcutsChanged: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("general") }
    // Pilihan tab: general | localisation | notifications | maintenance | staff | db_logs | shortcuts
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Map seluruh key-value dari tbl_appconfig
    var configMap by remember {
        mutableStateOf(
            mapOf(
                "CompanyName" to ApiClient.companyName,
                "currency_code" to ApiClient.currencyCode,
                "payment_usings" to "Cash,Transfer Bank",
                "enable_balance" to "yes"
            )
        )
    }

    // State Shortcut Cepat
    var selectedShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }
    var notifMap by remember {
        mutableStateOf(mapOf("init" to "").filterKeys { it != "init" })
    }
    var defaultNotifMap by remember {
        mutableStateOf(mapOf("init" to "").filterKeys { it != "init" })
    }
    var selectedNotifKey by remember { mutableStateOf("invoice_paid") }

    // State Staf Admin (tbl_users)
    var staffList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var staffSearchQuery by remember { mutableStateOf("") }
    var showStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<JSONObject?>(null) }
    var deletingStaffTarget by remember { mutableStateOf<JSONObject?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    // State Dialog Tes Pengiriman (WA, SMS, Email, Telegram)
    var testChannelTarget by remember { mutableStateOf<String?>(null) } // wa | sms | email | tg

    // State Database Status & Log Aktivitas
    var dbTablesList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var dbTotalTables by remember { mutableIntStateOf(0) }
    var dbTotalRows by remember { mutableIntStateOf(0) }
    var dbTotalSize by remember { mutableStateOf("0 B") }
    var logsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logSearchQuery by remember { mutableStateOf("") }
    var dbSubView by remember { mutableStateOf("dbstatus") } // dbstatus | logs

    fun updateConfigField(key: String, value: String) {
        val mutable = configMap.toMutableMap()
        mutable[key] = value
        configMap = mutable
    }

    fun loadTabData() {
        isLoading = true
        scope.launch {
            when (currentTab) {
                "general", "localisation", "maintenance" -> {
                    val res = ApiClient.request("admin_settings")
                    if (res.status && res.data != null) {
                        val map = mutableMapOf<String, String>()
                        val keys = res.data.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = res.data.optString(k, "")
                        }
                        configMap = map
                    }
                }
                "notifications" -> {
                    val res = ApiClient.request("admin_notifications_get")
                    if (res.status && res.data != null) {
                        val nObj = res.data.optJSONObject("notifications") ?: JSONObject()
                        val dObj = res.data.optJSONObject("defaults") ?: JSONObject()

                        val nMap = linkedMapOf<String, String>()
                        val dMap = linkedMapOf<String, String>()

                        val dKeys = dObj.keys()
                        while (dKeys.hasNext()) {
                            val k = dKeys.next()
                            dMap[k] = dObj.optString(k, "")
                            nMap[k] = nObj.optString(k, dObj.optString(k, ""))
                        }
                        val nKeys = nObj.keys()
                        while (nKeys.hasNext()) {
                            val k = nKeys.next()
                            if (!nMap.containsKey(k)) {
                                nMap[k] = nObj.optString(k, "")
                            }
                        }
                        defaultNotifMap = dMap
                        notifMap = nMap
                        if (nMap.isNotEmpty() && !nMap.containsKey(selectedNotifKey)) {
                            selectedNotifKey = nMap.keys.first()
                        }
                    }
                }
                "staff" -> {
                    val res = ApiClient.request("admin_staff_list", mapOf("search" to staffSearchQuery))
                    val arr = res.dataArray ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    staffList = list
                }
                "db_logs" -> {
                    val dbRes = ApiClient.request("admin_db_status")
                    if (dbRes.status && dbRes.data != null) {
                        dbTotalTables = dbRes.data.optInt("total_tables", 0)
                        dbTotalRows = dbRes.data.optInt("total_rows", 0)
                        dbTotalSize = dbRes.data.optString("total_size", "0 B")
                        val tArr = dbRes.data.optJSONArray("tables") ?: JSONArray()
                        val tList = mutableListOf<JSONObject>()
                        for (i in 0 until tArr.length()) tList.add(tArr.getJSONObject(i))
                        dbTablesList = tList
                    }

                    val lRes = ApiClient.request("admin_logs", mapOf("limit" to 150))
                    val lArr = lRes.dataArray ?: JSONArray()
                    val lList = mutableListOf<JSONObject>()
                    for (i in 0 until lArr.length()) lList.add(lArr.getJSONObject(i))
                    logsList = lList
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab != "shortcuts") {
            loadTabData()
        }
    }

    fun saveCurrentConfigSettings() {
        isSaving = true
        scope.launch {
            val res = ApiClient.request("admin_settings_save", configMap)
            isSaving = false
            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
            if (res.status) {
                val newComp = configMap["CompanyName"]?.trim() ?: ""
                val newCurr = configMap["currency_code"]?.trim() ?: ""
                if (newComp.isNotEmpty()) ApiClient.companyName = newComp
                if (newCurr.isNotEmpty()) ApiClient.currencyCode = newCurr
            }
        }
    }

    val filteredStaff = remember(staffList, staffSearchQuery) {
        val q = staffSearchQuery.trim().lowercase()
        if (q.isEmpty()) staffList
        else staffList.filter { s ->
            s.optString("username", "").lowercase().contains(q) ||
                s.optString("fullname", "").lowercase().contains(q) ||
                s.optString("user_type", "").lowercase().contains(q) ||
                s.optString("phone", "").lowercase().contains(q)
        }
    }

    val filteredLogs = remember(logsList, logSearchQuery) {
        val q = logSearchQuery.trim().lowercase()
        if (q.isEmpty()) logsList
        else logsList.filter { l ->
            l.optString("description", "").lowercase().contains(q) ||
                l.optString("userid", "").lowercase().contains(q) ||
                l.optString("date", "").lowercase().contains(q)
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "general" to "Umum & Gateway",
                "localisation" to "Lokalisasi & Label",
                "notifications" to "Template Notifikasi",
                "maintenance" to "Mode Maintenance",
                "staff" to "Staf & Password",
                "db_logs" to "Database & Log",
                "shortcuts" to "Shortcut Cepat"
            ).forEach { (tabKey, tabLabel) ->
                FilterChip(
                    selected = currentTab == tabKey,
                    onClick = { currentTab = tabKey },
                    label = { Text(tabLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && staffList.isEmpty() && dbTablesList.isEmpty() && notifMap.isEmpty() && currentTab != "general" && currentTab != "shortcuts") {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
            when (currentTab) {
                "general" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val r = ApiClient.request("admin_system_tools", mapOf("tool" to "clear_cache"))
                                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bersihkan Cache", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val r = ApiClient.request("admin_system_tools", mapOf("tool" to "run_cron"))
                                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Jalankan Cron", fontSize = 12.sp)
                            }
                        }

                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Identitas Perusahaan & Struk (General)", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGoldPrimary)
                                OutlinedTextField(
                                    value = configMap["CompanyName"] ?: "",
                                    onValueChange = { updateConfigField("CompanyName", it) },
                                    label = { Text("Nama Perusahaan / WiFi (CompanyName)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["phone"] ?: "",
                                    onValueChange = { updateConfigField("phone", it) },
                                    label = { Text("Nomor Telepon / CS WhatsApp") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["address"] ?: "",
                                    onValueChange = { updateConfigField("address", it) },
                                    label = { Text("Alamat Perusahaan") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["note"] ?: "",
                                    onValueChange = { updateConfigField("note", it) },
                                    label = { Text("Catatan Kaki Struk / Invoice (Footer Note)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["payment_usings"] ?: "Cash,Bank Transfer",
                                    onValueChange = { updateConfigField("payment_usings", it) },
                                    label = { Text("Metode Pembayaran Kasir (Pisahkan dengan koma)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = configMap["payment_gateway"] ?: "",
                                    onValueChange = { updateConfigField("payment_gateway", it) },
                                    label = { Text("Payment Gateway Aktif") },
                                    placeholder = { Text("viaqris,tripay,duitku,xendit,paypal") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Sistem Saldo, Pajak & Reminder Tagihan", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaCyanIncome)

                                SettingSwitchRow(
                                    title = "Aktifkan Fitur Saldo Pelanggan (enable_balance)",
                                    checked = (configMap["enable_balance"] ?: "yes").equals("yes", true),
                                    onCheckedChange = { updateConfigField("enable_balance", if (it) "yes" else "no") }
                                )

                                SettingSwitchRow(
                                    title = "Izinkan Transfer Saldo Antar Pelanggan",
                                    checked = (configMap["allow_balance_transfer"] ?: "yes").equals("yes", true),
                                    onCheckedChange = { updateConfigField("allow_balance_transfer", if (it) "yes" else "no") }
                                )

                                SettingSwitchRow(
                                    title = "Aktifkan Pajak Transaksi (enable_tax)",
                                    checked = (configMap["enable_tax"] ?: "no").equals("yes", true),
                                    onCheckedChange = { updateConfigField("enable_tax", if (it) "yes" else "no") }
                                )

                                if ((configMap["enable_tax"] ?: "no").equals("yes", true)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = configMap["tax_rate"] ?: "custom",
                                            onValueChange = { updateConfigField("tax_rate", it) },
                                            label = { Text("Mode Pajak (custom / angka)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = configMap["custom_tax_rate"] ?: "11",
                                            onValueChange = { updateConfigField("custom_tax_rate", it.replace("[^0-9.]".toRegex(), "")) },
                                            label = { Text("Tarif Pajak Custom (%)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text("Pengingat Jatuh Tempo Otomatis:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldLight)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "notification_reminder_7days" to "H-7 Hari",
                                        "notification_reminder_3days" to "H-3 Hari",
                                        "notification_reminder_1day" to "H-1 Hari"
                                    ).forEach { (rKey, rLbl) ->
                                        val isOn = (configMap[rKey] ?: "yes").equals("yes", true)
                                        FilterChip(
                                            selected = isOn,
                                            onClick = { updateConfigField(rKey, if (isOn) "no" else "yes") },
                                            label = { Text(rLbl, fontSize = 11.5.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Gateway WhatsApp, SMS & Telegram", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGreenSuccess)

                                OutlinedTextField(
                                    value = configMap["wa_url"] ?: "",
                                    onValueChange = { updateConfigField("wa_url", it) },
                                    label = { Text("WhatsApp Gateway URL (wa_url)") },
                                    placeholder = { Text("https://api.wa.../send?phone=[number]&text=[text]") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = configMap["sms_url"] ?: "",
                                    onValueChange = { updateConfigField("sms_url", it) },
                                    label = { Text("SMS Gateway URL / MikroTik SMS") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = configMap["mikrotik_sms_command"] ?: "/tool sms send",
                                    onValueChange = { updateConfigField("mikrotik_sms_command", it) },
                                    label = { Text("Command SMS RouterOS (mikrotik_sms_command)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["telegram_bot"] ?: "",
                                        onValueChange = { updateConfigField("telegram_bot", it) },
                                        label = { Text("Telegram Bot Token", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["telegram_target_id"] ?: "",
                                        onValueChange = { updateConfigField("telegram_target_id", it) },
                                        label = { Text("Telegram Chat ID", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text("Uji Coba Pengiriman Gateway (Sesuai fitur Test di Web):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ArnetaGoldPrimary)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    FilledTonalButton(onClick = { testChannelTarget = "wa" }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes WA", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "tg" }) {
                                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes Telegram", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "sms" }) {
                                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes SMS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { testChannelTarget = "email" }) {
                                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tes Email", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = { saveCurrentConfigSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSaving) "MENYIMPAN..." else "SIMPAN PENGATURAN UMUM", fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArnetaRedDanger),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keluar dari Akun Admin", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "localisation" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Format Regional, Mata Uang & Telepon", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaGoldPrimary)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["timezone"] ?: "Asia/Jakarta",
                                        onValueChange = { updateConfigField("timezone", it) },
                                        label = { Text("Zona Waktu (timezone)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["date_format"] ?: "d M Y",
                                        onValueChange = { updateConfigField("date_format", it) },
                                        label = { Text("Format Tanggal") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["currency_code"] ?: "Rp.",
                                        onValueChange = { updateConfigField("currency_code", it) },
                                        label = { Text("Simbol Mata Uang") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["country_code_phone"] ?: "62",
                                        onValueChange = { updateConfigField("country_code_phone", it) },
                                        label = { Text("Kode Negara WA (62)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["dec_point"] ?: ",",
                                        onValueChange = { if (it.length <= 1) updateConfigField("dec_point", it) },
                                        label = { Text("Pemisah Desimal (,)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["thousands_sep"] ?: ".",
                                        onValueChange = { if (it.length <= 1) updateConfigField("thousands_sep", it) },
                                        label = { Text("Pemisah Ribuan (.)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["language"] ?: "indonesia",
                                        onValueChange = { updateConfigField("language", it) },
                                        label = { Text("Bahasa") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }
                        }

                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Label Nama Layanan (Tampil di Portal Pelanggan)", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ArnetaCyanIncome)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["hotspot_plan"] ?: "Hotspot Plans",
                                        onValueChange = { updateConfigField("hotspot_plan", it) },
                                        label = { Text("Label Paket Hotspot") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["pppoe_plan"] ?: "PPPOE Plans",
                                        onValueChange = { updateConfigField("pppoe_plan", it) },
                                        label = { Text("Label Paket PPPoE") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = configMap["vpn_plan"] ?: "VPN Plans",
                                        onValueChange = { updateConfigField("vpn_plan", it) },
                                        label = { Text("Label Paket VPN") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = configMap["radius_plan"] ?: "Radius Plans",
                                        onValueChange = { updateConfigField("radius_plan", it) },
                                        label = { Text("Label Paket Radius") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = { saveCurrentConfigSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSaving) "MENYIMPAN..." else "SIMPAN LOKALISASI", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                "notifications" -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "Template Notifikasi Web (notifications.json)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = ArnetaGoldPrimary
                                )
                                Text(
                                    text = "Pilih jenis pesan di bawah untuk mengedit template WhatsApp/SMS yang juga dipakai oleh fitur Kirim Ulang WA:",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                val notifKeysList = remember(notifMap) { notifMap.keys.toList() }
                                if (notifKeysList.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        notifKeysList.forEach { k ->
                                            FilterChip(
                                                selected = selectedNotifKey == k,
                                                onClick = { selectedNotifKey = k },
                                                label = { Text(k, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = notifMap[selectedNotifKey] ?: "",
                                        onValueChange = { newText ->
                                            val mutable = notifMap.toMutableMap()
                                            mutable[selectedNotifKey] = newText
                                            notifMap = mutable
                                        },
                                        label = { Text("Isi Template: $selectedNotifKey") },
                                        minLines = 8,
                                        maxLines = 15,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(
                                        text = "Tag variabel yang didukung: [[company_name]], [[name]], [[user_name]], [[user_password]], [[plan_name]], [[plan_price]], [[invoice]], [[date]], [[expired_date]], [[current_balance]], [[payment_gateway]], [[payment_channel]], [[footer]]",
                                        fontSize = 10.5.sp,
                                        color = ArnetaCyanIncome
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val defVal = defaultNotifMap[selectedNotifKey]
                                                if (defVal != null) {
                                                    val mutable = notifMap.toMutableMap()
                                                    mutable[selectedNotifKey] = defVal
                                                    notifMap = mutable
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset Default", fontSize = 12.sp)
                                        }

                                        Button(
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                                            onClick = {
                                                isSaving = true
                                                scope.launch {
                                                    val res = ApiClient.request("admin_notifications_save", notifMap)
                                                    isSaving = false
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isSaving) "Menyimpan..." else "Simpan Semua Template", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Text("Memuat file notifications.json...", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                "maintenance" -> {
                    val isMaintOn = (configMap["maintenance_mode"] ?: "0") == "1" ||
                        (configMap["maintenance_mode"] ?: "").equals("yes", true)
                    val isForceLogout = (configMap["maintenance_mode_logout"] ?: "0") == "1" ||
                        (configMap["maintenance_mode_logout"] ?: "").equals("yes", true)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text("Mode Pemeliharaan Sistem (Maintenance Mode)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = ArnetaGoldPrimary)
                                Text(
                                    text = "Saat diaktifkan, portal & aplikasi pelanggan akan menampilkan halaman pemberitahuan pemeliharaan jaringan.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                SettingSwitchRow(
                                    title = "Aktifkan Maintenance Mode",
                                    checked = isMaintOn,
                                    onCheckedChange = { updateConfigField("maintenance_mode", if (it) "1" else "0") }
                                )

                                SettingSwitchRow(
                                    title = "Paksa Logout Sesi Pelanggan (Force Logout)",
                                    checked = isForceLogout,
                                    onCheckedChange = { updateConfigField("maintenance_mode_logout", if (it) "1" else "0") }
                                )

                                OutlinedTextField(
                                    value = configMap["maintenance_date"] ?: "",
                                    onValueChange = { updateConfigField("maintenance_date", it) },
                                    label = { Text("Estimasi Tanggal Selesai (YYYY-MM-DD HH:MM)") },
                                    placeholder = { Text("2026-11-10 18:00") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    enabled = !isSaving,
                                    onClick = { saveCurrentConfigSettings() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color(0xFF0B0F19)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isSaving) "MENYIMPAN..." else "SIMPAN PENGATURAN MAINTENANCE", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                "staff" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                editingStaff = null
                                showStaffDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Staf", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { showChangePasswordDialog = true }
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = ArnetaCyanIncome, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ganti Password Saya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = staffSearchQuery,
                        onValueChange = { staffSearchQuery = it },
                        placeholder = { Text("Cari username, nama lengkap, atau role staf...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                        trailingIcon = {
                            if (staffSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { staffSearchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 95.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredStaff) { u ->
                            val uType = u.optString("user_type", "Agent")
                            val isActive = u.optString("status", "Active").equals("Active", true)
                            val rootName = u.optString("root_name", "")

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        editingStaff = u
                                        showStaffDialog = true
                                    }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(u.optString("fullname"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Surface(
                                                color = when (uType) {
                                                    "SuperAdmin" -> ArnetaGoldPrimary.copy(alpha = 0.22f)
                                                    "Admin" -> ArnetaCyanIncome.copy(alpha = 0.22f)
                                                    else -> ArnetaIndigoAccent.copy(alpha = 0.22f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = uType,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = when (uType) {
                                                        "SuperAdmin" -> ArnetaGoldPrimary
                                                        "Admin" -> ArnetaCyanIncome
                                                        else -> ArnetaGoldLight
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (!isActive) {
                                                Surface(
                                                    color = ArnetaRedDanger.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "INACTIVE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ArnetaRedDanger,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "@${u.optString("username")} • WA: ${u.optString("phone").ifEmpty { "-" }}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        )
                                        if (uType == "Sales" && rootName.isNotEmpty()) {
                                            Text(
                                                text = "Upline Agent: $rootName",
                                                fontSize = 11.sp,
                                                color = ArnetaCyanIncome
                                            )
                                        }
                                        Text(
                                            text = "Login Terakhir: ${u.optString("last_login").ifEmpty { "Belum pernah" }}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingStaff = u
                                                showStaffDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Staf", tint = ArnetaGoldPrimary)
                                        }
                                        IconButton(
                                            onClick = { deletingStaffTarget = u }
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Staf", tint = ArnetaRedDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "db_logs" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = dbSubView == "dbstatus",
                                onClick = { dbSubView = "dbstatus" },
                                label = { Text("Status Database ($dbTotalTables Tabel)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = dbSubView == "logs",
                                onClick = { dbSubView = "logs" },
                                label = { Text("Log Sistem (${logsList.size})", fontSize = 12.sp) }
                            )
                        }

                        IconButton(onClick = { loadTabData() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (dbSubView == "dbstatus") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArnetaIndigoAccent),
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
                                    Text("Ringkasan Database MySQL/MariaDB", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
                                    Text(
                                        text = "$dbTotalTables Tabel • $dbTotalRows Baris • $dbTotalSize",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = ArnetaGoldLight
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val bRes = ApiClient.request("admin_db_backup")
                                            Toast.makeText(
                                                context,
                                                if (bRes.status) "Backup Database berhasil diekspor (${bRes.data?.optString("filename")})" else bRes.message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Backup", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 95.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(dbTablesList) { tbl ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = tbl.optString("name"),
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Engine: ${tbl.optString("engine", "InnoDB")} • Ukuran: ${tbl.optString("size_label", "0 B")}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                        Surface(
                                            color = ArnetaCyanIncome.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${tbl.optInt("rows", 0)} rows",
                                                color = ArnetaCyanIncome,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = logSearchQuery,
                            onValueChange = { logSearchQuery = it },
                            placeholder = { Text("Cari isi log aktivitas...", fontSize = 12.5.sp) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 95.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLogs) { l ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = l.optString("type", "Admin"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ArnetaGoldPrimary
                                            )
                                            Text(
                                                text = "${l.optString("date")} • ${l.optString("ip")}",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(l.optString("description"), fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                "shortcuts" -> {
                    val allShortcuts = listOf(
                        "add_customer" to "Add Customer Baru",
                        "recharge_plan" to "Isi Ulang Paket",
                        "deposit_balance" to "Isi Ulang Saldo",
                        "generate_voucher" to "Generate & Cetak Voucher",
                        "online_users" to "User Online & Kick Sesi",
                        "audit_qris" to "Audit Payment Gateway",
                        "live_monitor" to "Live Speed MikroTik",
                        "broadcast_wa" to "Broadcast WhatsApp",
                        "webview_admin" to "Web Admin Panel"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 95.dp)
                    ) {
                        Text(
                            text = "Pilih menu yang ditampilkan saat tombol petir di tengah navigasi bawah ditekan:",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        allShortcuts.forEach { (sKey, sTitle) ->
                            val checked = selectedShortcuts.contains(sKey)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = selectedShortcuts.toMutableSet()
                                        if (checked) next.remove(sKey) else next.add(sKey)
                                        selectedShortcuts = next
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
                                    Text(sTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            val next = selectedShortcuts.toMutableSet()
                                            if (isChecked) next.add(sKey) else next.remove(sKey)
                                            selectedShortcuts = next
                                            ApiClient.customQuickShortcuts = next
                                            onCustomShortcutsChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStaffDialog) {
        val agentsOnly = remember(staffList) {
            staffList.filter { it.optString("user_type").equals("Agent", true) }
        }
        StaffFormDialog(
            initial = editingStaff,
            agentsList = agentsOnly,
            onDismiss = {
                showStaffDialog = false
                editingStaff = null
            },
            onSaved = {
                showStaffDialog = false
                editingStaff = null
                loadTabData()
            }
        )
    }

    if (deletingStaffTarget != null) {
        val target = deletingStaffTarget!!
        AlertDialog(
            onDismissRequest = { deletingStaffTarget = null },
            containerColor = Color(0xFF141B2D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Hapus Akun Staf @${target.optString("username")}?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus akun staf ${target.optString("fullname")} (${target.optString("user_type")})?",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingStaffTarget = null
                        scope.launch {
                            val dRes = ApiClient.request("admin_staff_delete", mapOf("id" to target.optInt("id")))
                            Toast.makeText(context, dRes.message, Toast.LENGTH_LONG).show()
                            if (dRes.status) loadTabData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaRedDanger, contentColor = Color.White)
                ) {
                    Text("Ya, Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deletingStaffTarget = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    if (testChannelTarget != null) {
        TestNotificationDialog(
            channel = testChannelTarget!!,
            onDismiss = { testChannelTarget = null }
        )
    }
}
