package id.biz.arneta.admin.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AdminSettingsAndToolsScreen(
    initialTab: String = "general",
    onCustomShortcutsChanged: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    val cachedSettings = remember { ApiClient.getCached("admin_settings")?.data }
    val cachedNotif = remember { ApiClient.getCached("admin_notifications_get")?.data }
    val cachedStaff = remember { ApiClient.getCached("admin_staff_list") }
    val cachedLogs = remember { ApiClient.getCached("admin_logs") }

    var isLoading by remember { mutableStateOf(cachedSettings == null) }
    var isSaving by remember { mutableStateOf(false) }

    // State Tab 1: Umum, Gateway Notifikasi & Payment Gateway
    var companyName by remember { mutableStateOf(cachedSettings?.optString("CompanyName") ?: ApiClient.companyName) }
    var companyAddress by remember { mutableStateOf(cachedSettings?.optString("address") ?: "") }
    var companyPhone by remember { mutableStateOf(cachedSettings?.optString("phone") ?: "") }
    var companyFooter by remember { mutableStateOf(cachedSettings?.optString("note") ?: "") }
    var voucherPrefix by remember { mutableStateOf(cachedSettings?.optString("voucher_prefix") ?: "") }
    var enableBalance by remember { mutableStateOf((cachedSettings?.optString("enable_balance", "yes") ?: "yes") == "yes") }
    var allowBalanceTransfer by remember { mutableStateOf((cachedSettings?.optString("allow_balance_transfer", "yes") ?: "yes") == "yes") }
    var minimumTransfer by remember { mutableStateOf(cachedSettings?.optString("minimum_transfer", "10000") ?: "10000") }

    var tgBotToken by remember { mutableStateOf(cachedSettings?.optString("telegram_bot") ?: "") }
    var tgTargetId by remember { mutableStateOf(cachedSettings?.optString("telegram_target_id") ?: "") }
    var waGatewayUrl by remember { mutableStateOf(cachedSettings?.optString("wa_url") ?: "") }
    var smsGatewayUrl by remember { mutableStateOf(cachedSettings?.optString("sms_url") ?: "") }

    var userNotifExpired by remember { mutableStateOf(cachedSettings?.optString("user_notification_expired", "wa") ?: "wa") }
    var userNotifPayment by remember { mutableStateOf(cachedSettings?.optString("user_notification_payment", "wa") ?: "wa") }
    var userNotifReminder by remember { mutableStateOf(cachedSettings?.optString("user_notification_reminder", "wa") ?: "wa") }

    var activePaymentGateways by remember { mutableStateOf(cachedSettings?.optString("payment_gateway") ?: "") }
    var showSecretTokens by remember { mutableStateOf(false) }
    var testNotifChannel by remember { mutableStateOf<String?>(null) }

    // State Tab 2: Lokalisasi & Pajak
    var currencyCode by remember { mutableStateOf(cachedSettings?.optString("currency_code") ?: ApiClient.currencyCode) }
    var countryCodePhone by remember { mutableStateOf(cachedSettings?.optString("country_code_phone", "62") ?: "62") }
    var timezoneStr by remember { mutableStateOf(cachedSettings?.optString("timezone", "Asia/Jakarta") ?: "Asia/Jakarta") }
    var enableTax by remember { mutableStateOf((cachedSettings?.optString("enable_tax", "no") ?: "no") == "yes") }
    var taxRate by remember { mutableStateOf(cachedSettings?.optString("tax_rate", "11") ?: "11") }

    // State Tab 3: Template Notifikasi JSON
    var notifWelcome by remember { mutableStateOf(cachedNotif?.optString("welcome_message") ?: "") }
    var notifInvoicePaid by remember { mutableStateOf(cachedNotif?.optString("invoice_paid") ?: "") }
    var notifInvoiceBalance by remember { mutableStateOf(cachedNotif?.optString("invoice_balance") ?: "") }
    var notifReminder7 by remember { mutableStateOf(cachedNotif?.optString("reminder_7_days") ?: "") }
    var notifReminder3 by remember { mutableStateOf(cachedNotif?.optString("reminder_3_days") ?: "") }
    var notifReminder1 by remember { mutableStateOf(cachedNotif?.optString("reminder_1_day") ?: "") }
    var notifExpired by remember { mutableStateOf(cachedNotif?.optString("expired") ?: "") }

    // State Tab 4: Mode Maintenance
    var maintenanceMode by remember { mutableStateOf((cachedSettings?.optString("maintenance_mode", "0") ?: "0") == "1") }
    var maintenanceMessage by remember { mutableStateOf(cachedSettings?.optString("maintenance_message") ?: "Sistem sedang dalam pemeliharaan berkala.") }

    // State Tab 5: Staf & Password
    var staffList by remember {
        mutableStateOf(
            if (cachedStaff?.status == true) {
                val arr = cachedStaff.dataArray ?: cachedStaff.data?.optJSONArray("staff") ?: JSONArray()
                List(arr.length()) { i -> arr.getJSONObject(i) }
            } else emptyList()
        )
    }
    var showStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<JSONObject?>(null) }
    var deletingStaffTarget by remember { mutableStateOf<JSONObject?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    // State Tab 6: Database & Log Aktivitas
    var logsList by remember {
        mutableStateOf(
            if (cachedLogs?.status == true) {
                val arr = cachedLogs.dataArray ?: cachedLogs.data?.optJSONArray("logs") ?: JSONArray()
                List(arr.length()) { i -> arr.getJSONObject(i) }
            } else emptyList()
        )
    }
    var logSearchQuery by remember { mutableStateOf("") }

    // State Tab 7: Shortcut Cepat
    var selectedShortcuts by remember { mutableStateOf(ApiClient.customQuickShortcuts) }

    fun applySettingsJson(d: JSONObject) {
        companyName = d.optString("CompanyName", companyName)
        companyAddress = d.optString("address", companyAddress)
        companyPhone = d.optString("phone", companyPhone)
        companyFooter = d.optString("note", companyFooter)
        voucherPrefix = d.optString("voucher_prefix", voucherPrefix)
        enableBalance = d.optString("enable_balance", "yes") == "yes"
        allowBalanceTransfer = d.optString("allow_balance_transfer", "yes") == "yes"
        minimumTransfer = d.optString("minimum_transfer", minimumTransfer)

        tgBotToken = d.optString("telegram_bot", tgBotToken)
        tgTargetId = d.optString("telegram_target_id", tgTargetId)
        waGatewayUrl = d.optString("wa_url", waGatewayUrl)
        smsGatewayUrl = d.optString("sms_url", smsGatewayUrl)

        userNotifExpired = d.optString("user_notification_expired", userNotifExpired)
        userNotifPayment = d.optString("user_notification_payment", userNotifPayment)
        userNotifReminder = d.optString("user_notification_reminder", userNotifReminder)
        activePaymentGateways = d.optString("payment_gateway", activePaymentGateways)

        currencyCode = d.optString("currency_code", currencyCode)
        countryCodePhone = d.optString("country_code_phone", countryCodePhone)
        timezoneStr = d.optString("timezone", timezoneStr)
        enableTax = d.optString("enable_tax", "no") == "yes"
        taxRate = d.optString("tax_rate", taxRate)

        maintenanceMode = d.optString("maintenance_mode", "0") == "1" || d.optString("maintenance_mode") == "yes"
        maintenanceMessage = d.optString("maintenance_message", maintenanceMessage)
    }

    fun loadSettingsData(forceRefresh: Boolean = false) {
        scope.launch {
            val res = ApiClient.request("admin_settings", emptyMap(), forceRefresh = forceRefresh)
            if (res.status && res.data != null) {
                applySettingsJson(res.data)
            }
            isLoading = false
        }
    }

    fun loadNotificationsTemplate(forceRefresh: Boolean = false) {
        scope.launch {
            val res = ApiClient.request("admin_notifications_get", emptyMap(), forceRefresh = forceRefresh)
            if (res.status && res.data != null) {
                val d = res.data
                notifWelcome = d.optString("welcome_message", notifWelcome)
                notifInvoicePaid = d.optString("invoice_paid", notifInvoicePaid)
                notifInvoiceBalance = d.optString("invoice_balance", notifInvoiceBalance)
                notifReminder7 = d.optString("reminder_7_days", notifReminder7)
                notifReminder3 = d.optString("reminder_3_days", notifReminder3)
                notifReminder1 = d.optString("reminder_1_day", notifReminder1)
                notifExpired = d.optString("expired", notifExpired)
            }
        }
    }

    fun loadStaffList(forceRefresh: Boolean = false) {
        scope.launch {
            val res = ApiClient.request("admin_staff_list", emptyMap(), forceRefresh = forceRefresh)
            if (res.status) {
                val arr = res.dataArray ?: res.data?.optJSONArray("staff") ?: JSONArray()
                staffList = withContext(Dispatchers.Default) {
                    List(arr.length()) { i -> arr.getJSONObject(i) }
                }
            }
        }
    }

    fun loadSystemLogs(forceRefresh: Boolean = false) {
        scope.launch {
            val res = ApiClient.request("admin_logs", mapOf("limit" to 150), forceRefresh = forceRefresh)
            if (res.status) {
                val arr = res.dataArray ?: res.data?.optJSONArray("logs") ?: JSONArray()
                logsList = withContext(Dispatchers.Default) {
                    List(arr.length()) { i -> arr.getJSONObject(i) }
                }
            }
        }
    }

    LaunchedEffect(currentTab) {
        when (currentTab) {
            "general", "localisation", "maintenance" -> loadSettingsData(forceRefresh = false)
            "notifications" -> loadNotificationsTemplate(forceRefresh = false)
            "staff" -> loadStaffList(forceRefresh = false)
            "db_logs" -> loadSystemLogs(forceRefresh = false)
        }
    }

    fun saveAppSettings() {
        isSaving = true
        scope.launch {
            val res = ApiClient.request(
                "admin_settings_save",
                mapOf(
                    "CompanyName" to companyName.trim(),
                    "address" to companyAddress.trim(),
                    "phone" to companyPhone.trim(),
                    "note" to companyFooter.trim(),
                    "voucher_prefix" to voucherPrefix.trim(),
                    "enable_balance" to if (enableBalance) "yes" else "no",
                    "allow_balance_transfer" to if (allowBalanceTransfer) "yes" else "no",
                    "minimum_transfer" to minimumTransfer.trim(),
                    "telegram_bot" to tgBotToken.trim(),
                    "telegram_target_id" to tgTargetId.trim(),
                    "wa_url" to waGatewayUrl.trim(),
                    "sms_url" to smsGatewayUrl.trim(),
                    "user_notification_expired" to userNotifExpired,
                    "user_notification_payment" to userNotifPayment,
                    "user_notification_reminder" to userNotifReminder,
                    "payment_gateway" to activePaymentGateways.trim(),
                    "currency_code" to currencyCode.trim(),
                    "country_code_phone" to countryCodePhone.trim(),
                    "timezone" to timezoneStr.trim(),
                    "enable_tax" to if (enableTax) "yes" else "no",
                    "tax_rate" to taxRate.trim(),
                    "maintenance_mode" to if (maintenanceMode) "1" else "0",
                    "maintenance_message" to maintenanceMessage.trim()
                )
            )
            isSaving = false
            if (res.status) {
                ApiClient.companyName = companyName.trim()
                ApiClient.currencyCode = currencyCode.trim()
                onCustomShortcutsChanged()
            }
            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
        }
    }

    val filteredLogs = remember(logsList, logSearchQuery) {
        val q = logSearchQuery.trim().lowercase()
        if (q.isEmpty()) logsList
        else logsList.filter { l ->
            l.optString("description", "").lowercase().contains(q) ||
                l.optString("type", "").lowercase().contains(q) ||
                l.optString("date", "").lowercase().contains(q)
        }
    }

    val agentsForStaffForm = remember(staffList) {
        staffList.filter { it.optString("user_type").equals("Agent", ignoreCase = true) }
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Pengaturan & Alat Sistem", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    text = "Login: @${ApiClient.adminUsername} (${ApiClient.adminRole})",
                    fontSize = 11.5.sp,
                    color = ArnetaGoldPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showChangePasswordDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Password", fontSize = 11.5.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onLogout,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = ArnetaRedDanger, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Keluar", fontSize = 11.5.sp, color = ArnetaRedDanger, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ArnetaSubTabBar(
            tabs = listOf(
                "general" to "Umum & Gateway",
                "localisation" to "Lokalisasi & Pajak",
                "notifications" to "Template Notif",
                "maintenance" to "Maintenance",
                "staff" to "Staf (${staffList.size})",
                "db_logs" to "Database & Log",
                "shortcuts" to "Shortcut Cepat"
            ),
            selectedKey = currentTab,
            onSelect = { currentTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (currentTab) {
            "general" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("1. Identitas Perusahaan & Fitur Saldo", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = ArnetaGoldPrimary)

                            OutlinedTextField(
                                value = companyName,
                                onValueChange = { companyName = it },
                                label = { Text("Nama Perusahaan / ISP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = companyPhone,
                                onValueChange = { companyPhone = it },
                                label = { Text("Nomor Telepon / CS WhatsApp") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = companyAddress,
                                onValueChange = { companyAddress = it },
                                label = { Text("Alamat Kantor / Server") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = companyFooter,
                                onValueChange = { companyFooter = it },
                                label = { Text("Catatan Kaki Struk (Footer Note)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            SettingSwitchRow(
                                title = "Aktifkan Fitur Saldo Deposit Pelanggan",
                                checked = enableBalance,
                                onCheckedChange = { enableBalance = it }
                            )
                            SettingSwitchRow(
                                title = "Izinkan Transfer Saldo Antar Pelanggan",
                                checked = allowBalanceTransfer,
                                onCheckedChange = { allowBalanceTransfer = it }
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("2. Gateway Notifikasi (Telegram, WA, SMS)", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = ArnetaCyanIncome)
                                IconButton(onClick = { showSecretTokens = !showSecretTokens }) {
                                    Icon(
                                        imageVector = if (showSecretTokens) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Tampilkan Token"
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = tgBotToken,
                                onValueChange = { tgBotToken = it },
                                label = { Text("Telegram Bot Token") },
                                visualTransformation = if (showSecretTokens) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tgTargetId,
                                onValueChange = { tgTargetId = it },
                                label = { Text("Telegram Chat ID Admin") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = waGatewayUrl,
                                onValueChange = { waGatewayUrl = it },
                                label = { Text("WhatsApp Gateway URL ([number] & [text])") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = smsGatewayUrl,
                                onValueChange = { smsGatewayUrl = it },
                                label = { Text("SMS Gateway URL (Opsional)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { testNotifChannel = "tg" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tes Telegram", fontSize = 11.5.sp)
                                }
                                OutlinedButton(
                                    onClick = { testNotifChannel = "wa" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tes WhatsApp", fontSize = 11.5.sp)
                                }
                            }

                            val notifChannels = listOf("none" to "Nonaktif (None)", "wa" to "WhatsApp", "sms" to "SMS", "email" to "Email")
                            CompactDropdownSelector(
                                label = "Channel Notifikasi Pembayaran / Struk",
                                selectedValue = userNotifPayment,
                                options = notifChannels,
                                icon = Icons.Filled.Receipt,
                                onValueSelected = { userNotifPayment = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            CompactDropdownSelector(
                                label = "Channel Notifikasi Paket Kedaluwarsa",
                                selectedValue = userNotifExpired,
                                options = notifChannels,
                                icon = Icons.Filled.EventBusy,
                                onValueSelected = { userNotifExpired = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            CompactDropdownSelector(
                                label = "Channel Notifikasi Pengingat Jatuh Tempo",
                                selectedValue = userNotifReminder,
                                options = notifChannels,
                                icon = Icons.Filled.NotificationsActive,
                                onValueSelected = { userNotifReminder = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        enabled = !isSaving,
                        onClick = { saveAppSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Menyimpan..." else "Simpan Pengaturan Umum", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            "localisation" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("Lokalisasi, Mata Uang & Pajak", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = ArnetaGoldPrimary)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = currencyCode,
                                    onValueChange = { currencyCode = it },
                                    label = { Text("Simbol Mata Uang (Rp.)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = countryCodePhone,
                                    onValueChange = { countryCodePhone = it },
                                    label = { Text("Kode Negara HP (62)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = timezoneStr,
                                onValueChange = { timezoneStr = it },
                                label = { Text("Zona Waktu Server (Asia/Jakarta)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = voucherPrefix,
                                onValueChange = { voucherPrefix = it },
                                label = { Text("Prefix Default Kode Voucher") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            SettingSwitchRow(
                                title = "Aktifkan PPN / Pajak Transaksi",
                                checked = enableTax,
                                onCheckedChange = { enableTax = it }
                            )

                            if (enableTax) {
                                OutlinedTextField(
                                    value = taxRate,
                                    onValueChange = { taxRate = it },
                                    label = { Text("Persentase Pajak (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Button(
                        enabled = !isSaving,
                        onClick = { saveAppSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Menyimpan..." else "Simpan Lokalisasi & Pajak", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            "notifications" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("Template Notifikasi Resmi (notifications.json)", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = ArnetaGoldPrimary)

                            OutlinedTextField(
                                value = notifInvoicePaid,
                                onValueChange = { notifInvoicePaid = it },
                                label = { Text("Template Struk Pembayaran Paket (invoice_paid)") },
                                minLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notifInvoiceBalance,
                                onValueChange = { notifInvoiceBalance = it },
                                label = { Text("Template Struk Isi Ulang Saldo (invoice_balance)") },
                                minLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notifWelcome,
                                onValueChange = { notifWelcome = it },
                                label = { Text("Template Pesan Sambutan Pelanggan Baru (welcome_message)") },
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notifExpired,
                                onValueChange = { notifExpired = it },
                                label = { Text("Template Notifikasi Paket Habis / Isolir (expired)") },
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notifReminder3,
                                onValueChange = { notifReminder3 = it },
                                label = { Text("Pengingat H-3 Sebelum Expired (reminder_3_days)") },
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notifReminder1,
                                onValueChange = { notifReminder1 = it },
                                label = { Text("Pengingat H-1 Sebelum Expired (reminder_1_day)") },
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_notifications_save",
                                    mapOf(
                                        "welcome_message" to notifWelcome,
                                        "invoice_paid" to notifInvoicePaid,
                                        "invoice_balance" to notifInvoiceBalance,
                                        "reminder_7_days" to notifReminder7,
                                        "reminder_3_days" to notifReminder3,
                                        "reminder_1_day" to notifReminder1,
                                        "expired" to notifExpired
                                    )
                                )
                                isSaving = false
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Menyimpan..." else "Simpan Template Notifikasi", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            "maintenance" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("Mode Pemeliharaan Portal Pelanggan", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = ArnetaOrangeActive)
                            SettingSwitchRow(
                                title = "Aktifkan Mode Maintenance",
                                checked = maintenanceMode,
                                onCheckedChange = { maintenanceMode = it }
                            )
                            OutlinedTextField(
                                value = maintenanceMessage,
                                onValueChange = { maintenanceMessage = it },
                                label = { Text("Pesan Informasi Maintenance untuk Pelanggan") },
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        enabled = !isSaving,
                        onClick = { saveAppSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Menyimpan..." else "Simpan Status Maintenance", fontWeight = FontWeight.ExtraBold)
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
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Staf / Agent", fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = { loadStaffList(forceRefresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = staffList,
                        key = { it.optInt("id", it.optString("username").hashCode()) }
                    ) { st ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    editingStaff = st
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
                                        Text(st.optString("fullname"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Surface(
                                            color = ArnetaCyanIncome.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = st.optString("user_type"),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ArnetaCyanIncome,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "@${st.optString("username")} • WA: ${st.optString("phone").ifEmpty { "-" }} • Status: ${st.optString("status", "Active")}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingStaff = st
                                        showStaffDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                    }
                                    IconButton(onClick = { deletingStaffTarget = st }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "db_logs" -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request("admin_system_tools", mapOf("tool" to "clear_cache"))
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Cache", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val res = ApiClient.request("admin_system_tools", mapOf("tool" to "run_cron"))
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Cron", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val backupUrl = "${ApiClient.baseUrl}/system/api.php?r=plugin/mobile_api/admin_db_backup&token=${ApiClient.token}"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(backupUrl)))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup SQL", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = logSearchQuery,
                        onValueChange = { logSearchQuery = it },
                        placeholder = { Text("Cari isi log aktivitas sistem...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { loadSystemLogs(forceRefresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan Log", tint = ArnetaGoldPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { l ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${l.optString("type")} • IP: ${l.optString("ip", "-")}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArnetaCyanIncome
                                    )
                                    Text(
                                        text = l.optString("date"),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = l.optString("description"),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            "shortcuts" -> {
                val allShortcutOptions = listOf(
                    "add_customer" to "Tambah Pelanggan Baru",
                    "recharge_plan" to "Isi Ulang Paket Internet",
                    "deposit_balance" to "Isi Ulang Saldo Deposit",
                    "generate_voucher" to "Generate & Cetak Voucher",
                    "online_users" to "User Online & Kick Sesi",
                    "audit_qris" to "Audit Payment Gateway",
                    "live_monitor" to "Live Speed MikroTik",
                    "broadcast_wa" to "Broadcast WhatsApp",
                    "webview_admin" to "Web Admin Panel (SSO)"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 95.dp)
                ) {
                    Text(
                        text = "Pilih menu yang ditampilkan saat tombol kilat (⚡) di tengah navigasi bawah ditekan:",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )

                    allShortcutOptions.forEach { (sKey, sTitle) ->
                        val isChecked = selectedShortcuts.contains(sKey)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = selectedShortcuts.toMutableSet()
                                    if (isChecked) next.remove(sKey) else next.add(sKey)
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
                                Text(sTitle, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val next = selectedShortcuts.toMutableSet()
                                        if (checked) next.add(sKey) else next.remove(sKey)
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

    if (showStaffDialog) {
        StaffFormDialog(
            initial = editingStaff,
            agentsList = agentsForStaffForm,
            onDismiss = { showStaffDialog = false },
            onSaved = {
                showStaffDialog = false
                loadStaffList(forceRefresh = true)
            }
        )
    }

    if (deletingStaffTarget != null) {
        val target = deletingStaffTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Akun Staf?",
            subtitle = "${target.optString("fullname")} (@${target.optString("username")})",
            description = "Akun staf ini akan dihapus dari sistem.",
            onDismiss = { deletingStaffTarget = null },
            onConfirm = {
                deletingStaffTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_staff_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadStaffList(forceRefresh = true)
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(onDismiss = { showChangePasswordDialog = false })
    }

    if (testNotifChannel != null) {
        TestNotificationDialog(
            channel = testNotifChannel!!,
            onDismiss = { testNotifChannel = null }
        )
    }
}
