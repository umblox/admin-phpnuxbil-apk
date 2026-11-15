package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * LAYAR PENUH NATIVE: TAMBAH & EDIT AKUN STAF (SuperAdmin, Admin, Report, Agent, Sales)
 */
@Composable
internal fun StaffFormDialog(
    initial: JSONObject?,
    agentsList: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var fullname by remember { mutableStateOf(initial?.optString("fullname") ?: "") }
    var password by remember { mutableStateOf("") }
    var cpassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var userType by remember { mutableStateOf(initial?.optString("user_type")?.ifEmpty { null } ?: "Agent") }
    var rootAgentId by remember {
        mutableIntStateOf(
            initial?.optInt("root", 0)?.takeIf { it > 0 } ?: (agentsList.firstOrNull()?.optInt("id") ?: 0)
        )
    }
    var phone by remember { mutableStateOf(initial?.optString("phone") ?: "") }
    var email by remember { mutableStateOf(initial?.optString("email") ?: "") }
    var city by remember { mutableStateOf(initial?.optString("city") ?: "") }
    var subdistrict by remember { mutableStateOf(initial?.optString("subdistrict") ?: "") }
    var ward by remember { mutableStateOf(initial?.optString("ward") ?: "") }
    var status by remember { mutableStateOf(initial?.optString("status")?.ifEmpty { null } ?: "Active") }
    var sendNotif by remember { mutableStateOf("") } // "" | "wa" | "sms"
    var isSaving by remember { mutableStateOf(false) }

    val isPasswordValid = if (isEdit) {
        password.isEmpty() || (password.length >= 6 && (cpassword.isEmpty() || password == cpassword))
    } else {
        password.length >= 6 && (cpassword.isEmpty() || password == cpassword)
    }

    val canSubmit = !isSaving &&
        username.trim().length >= 3 &&
        fullname.trim().length >= 3 &&
        isPasswordValid

    ArnetaFullScreenDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = Color(0xFF141B2D),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isEdit) "Edit Staf: @$username" else "Tambah Akun Staf Baru",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Manajemen hak akses Admin, Agent & Sales",
                                fontSize = 11.5.sp,
                                color = ArnetaGoldLight
                            )
                        }
                    }

                    Surface(
                        color = ArnetaGoldPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = userType.uppercase(),
                            color = ArnetaGoldPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF141B2D),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.36f)
                            .height(46.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArnetaGoldPrimary,
                            contentColor = Color(0xFF0B0F19)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_staff_save",
                                    mapOf(
                                        "id" to (initial?.optInt("id") ?: 0),
                                        "username" to username.trim(),
                                        "fullname" to fullname.trim(),
                                        "password" to password.trim(),
                                        "cpassword" to cpassword.trim().ifEmpty { password.trim() },
                                        "user_type" to userType,
                                        "root" to rootAgentId,
                                        "phone" to phone.trim(),
                                        "email" to email.trim(),
                                        "city" to city.trim(),
                                        "subdistrict" to subdistrict.trim(),
                                        "ward" to ward.trim(),
                                        "status" to status,
                                        "send_notif" to sendNotif
                                    )
                                )
                                isSaving = false
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                if (res.status) onSaved()
                            }
                        },
                        modifier = Modifier
                            .weight(0.64f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaving) "Menyimpan..." else "Simpan Staf",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "1. Level Akses (Role) & Status Akun",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaGoldPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("SuperAdmin", "Admin", "Report", "Agent", "Sales").forEach { role ->
                            FilterChip(
                                selected = userType.equals(role, true),
                                onClick = { userType = role },
                                label = { Text(role, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (userType.equals("Sales", true)) {
                        if (agentsList.isNotEmpty()) {
                            CompactDropdownSelector(
                                label = "Pilih Upline Agent (Induk Sales)",
                                selectedValue = rootAgentId.toString(),
                                options = agentsList.map { ag ->
                                    ag.optInt("id").toString() to "${ag.optString("fullname")} (@${ag.optString("username")})"
                                },
                                icon = Icons.Filled.SupervisorAccount,
                                onValueSelected = { rootAgentId = it.toIntOrNull() ?: rootAgentId },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "ℹ️ Belum ada akun berlevel Agent. Buat akun Agent terlebih dahulu jika ingin menetapkan Upline untuk Sales.",
                                fontSize = 11.sp,
                                color = ArnetaCyanIncome
                            )
                        }
                    }

                    if (isEdit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Active" to "Status: Active", "Inactive" to "Status: Inactive").forEach { (stKey, stLbl) ->
                                FilterChip(
                                    selected = status.equals(stKey, true),
                                    onClick = { status = stKey },
                                    label = { Text(stLbl, fontSize = 11.5.sp) }
                                )
                            }
                        }
                    }
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
                    Text(
                        text = "2. Kredensial & Profil Staf",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaCyanIncome
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username * (Min. 3 karakter)") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fullname,
                        onValueChange = { fullname = it },
                        label = { Text("Nama Lengkap * (Min. 3 karakter)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isEdit) "Password Baru (Opsional)" else "Password * (Min. 6)", fontSize = 11.sp) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cpassword,
                            onValueChange = { cpassword = it },
                            label = { Text("Ulangi Password", fontSize = 11.sp) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isPasswordValid && password.isNotEmpty()) {
                        Text(
                            text = if (password.length < 6) "⚠️ Password minimal 6 karakter."
                            else "⚠️ Konfirmasi password belum cocok.",
                            fontSize = 11.sp,
                            color = ArnetaRedDanger
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("No. WhatsApp", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("Kota", fontSize = 10.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = subdistrict,
                            onValueChange = { subdistrict = it },
                            label = { Text("Kecamatan", fontSize = 10.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ward,
                            onValueChange = { ward = it },
                            label = { Text("Kelurahan", fontSize = 10.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isEdit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Text("Kirim Detail Akun ke Staf:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ArnetaCyanIncome)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "" to "Jangan Kirim",
                                "wa" to "Via WhatsApp",
                                "sms" to "Via SMS"
                            ).forEach { (nKey, nLbl) ->
                                FilterChip(
                                    selected = sendNotif == nKey,
                                    onClick = { sendNotif = nKey },
                                    label = { Text(nLbl, fontSize = 11.5.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * LAYAR PENUH NATIVE: GANTI PASSWORD AKUN ADMIN SAAT INI
 */
@Composable
internal fun ChangePasswordDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmNewPass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val isLengthOk = newPass.length in 6..14
    val isMatchOk = newPass.isNotEmpty() && newPass == confirmNewPass
    val canSave = !isSaving && currentPass.isNotBlank() && isLengthOk && isMatchOk

    ArnetaFullScreenDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = Color(0xFF141B2D),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Ganti Password Akun Saya",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Akun aktif: @${ApiClient.adminUsername} (${ApiClient.adminRole})",
                            fontSize = 11.5.sp,
                            color = ArnetaGoldLight
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF141B2D),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.36f)
                            .height(46.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArnetaGoldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = ApiClient.request(
                                    "admin_change_password",
                                    mapOf(
                                        "password" to currentPass,
                                        "npass" to newPass,
                                        "cnpass" to confirmNewPass
                                    )
                                )
                                isSaving = false
                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                if (res.status) onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(0.64f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaving) "Memproses..." else "Ubah Password",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
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
                    Text(
                        text = "Keamanan Password Admin",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaGoldPrimary
                    )
                    Text(
                        text = "Sesuai aturan sistem web PHPNuxBill, password baru harus terdiri dari 6 hingga 14 karakter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Password Saat Ini *") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Password Baru (6 - 14 Karakter) *") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmNewPass,
                        onValueChange = { confirmNewPass = it },
                        label = { Text("Konfirmasi Password Baru *") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (newPass.isNotEmpty() && !isLengthOk) {
                        Text(
                            text = "⚠️ Panjang password baru harus antara 6 hingga 14 karakter (saat ini: ${newPass.length}).",
                            fontSize = 11.5.sp,
                            color = ArnetaRedDanger
                        )
                    } else if (confirmNewPass.isNotEmpty() && !isMatchOk) {
                        Text(
                            text = "⚠️ Konfirmasi password baru belum cocok.",
                            fontSize = 11.5.sp,
                            color = ArnetaRedDanger
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TestNotificationDialog(
    channel: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetInput by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    val channelTitle = when (channel) {
        "wa" -> "WhatsApp Gateway"
        "sms" -> "SMS Gateway"
        "email" -> "SMTP / Email"
        else -> "Bot Telegram"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Tes Kirim $channelTitle", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (channel == "tg") {
                    Text(
                        text = "Pesan uji coba 'PHPNuxBill Test Telegram' akan langsung dikirim ke Chat ID Telegram yang tersimpan di sistem.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                } else {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = {
                            Text(if (channel == "email") "Alamat Email Tujuan Tes" else "Nomor HP / WA Tujuan Tes")
                        },
                        placeholder = {
                            Text(if (channel == "email") "admin@domainanda.com" else "08123456789")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isTesting && (channel == "tg" || targetInput.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black),
                onClick = {
                    isTesting = true
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_test_notif",
                            mapOf("channel" to channel, "target" to targetInput.trim())
                        )
                        isTesting = false
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        if (res.status) onDismiss()
                    }
                }
            ) {
                Text(if (isTesting) "Mengirim..." else "Kirim Tes Sekarang", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = Color.White) }
        }
    )
}
