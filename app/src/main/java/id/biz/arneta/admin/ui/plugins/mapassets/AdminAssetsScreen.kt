package id.biz.arneta.admin.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * LAYAR ASSET MANAGER (ODP, ODC, ONT/MODEM, ROUTER, KABEL)
 */
@Composable
fun AdminAssetsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assetList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var totalValue by remember { mutableDoubleStateOf(0.0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(false) }

    var showFormDialog by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<JSONObject?>(null) }
    var deletingAssetTarget by remember { mutableStateOf<JSONObject?>(null) }

    fun loadAssets() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_assets")
            if (res.status && res.data != null) {
                totalValue = res.data.optJSONObject("summary")?.optDouble("total_value", 0.0) ?: 0.0
                val arr = res.data.optJSONArray("assets") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                assetList = list
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAssets() }

    val filteredAssets = remember(assetList, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        assetList.filter { a ->
            val matchCat = selectedCategory == "All" || a.optString("category", "").equals(selectedCategory, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                a.optString("name", "").lowercase().contains(q) ||
                a.optString("category", "").lowercase().contains(q) ||
                a.optString("brand", "").lowercase().contains(q) ||
                a.optString("serial_number", "").lowercase().contains(q) ||
                a.optString("location", "").lowercase().contains(q)
            matchCat && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ArnetaIndigoAccent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Nilai Inventaris Aset", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    Text(formatRupiah(totalValue), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ArnetaGoldLight)
                    Text("${assetList.size} Unit Perangkat Terdaftar", fontSize = 11.sp, color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { loadAssets() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = Color.White)
                    }
                    Button(
                        onClick = {
                            editingAsset = null
                            showFormDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Aset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama ODP, SN, merk, atau lokasi...", fontSize = 12.5.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArnetaGoldPrimary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "ODP/ODC", "ONT/Modem", "Router", "Kabel/Optik", "Lainnya").forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(if (cat == "All") "Semua (${assetList.size})" else cat, fontSize = 11.5.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && assetList.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else if (filteredAssets.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Belum ada data inventaris aset yang cocok.", fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 95.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAssets) { a ->
                    val cond = a.optString("condition_status", "Good")
                    val condColor = when (cond.lowercase()) {
                        "good", "baik" -> ArnetaGreenSuccess
                        "repair", "perbaikan" -> ArnetaOrangeActive
                        else -> ArnetaRedDanger
                    }
                    val locStr = a.optString("location", "").trim()
                    val coordStr = a.optString("coordinates", "").trim()

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                editingAsset = a
                                showFormDialog = true
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
                                    Text(a.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Surface(
                                        color = condColor.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = cond.uppercase(),
                                            color = condColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${a.optString("category")} • Merk/SN: ${a.optString("brand").ifEmpty { "-" }} / ${a.optString("serial_number").ifEmpty { "-" }}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "Nilai: ${formatRupiah(a.optDouble("cost", 0.0))}" +
                                        if (locStr.isNotEmpty()) " • Lokasi: $locStr" else "",
                                    fontSize = 12.sp,
                                    color = ArnetaGoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (coordStr.isNotEmpty()) {
                                    Text("📍 Koordinat: $coordStr", fontSize = 11.sp, color = ArnetaCyanIncome)
                                }
                            }
                            Row {
                                IconButton(onClick = {
                                    editingAsset = a
                                    showFormDialog = true
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                                }
                                IconButton(onClick = { deletingAssetTarget = a }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        AssetFormFullDialog(
            initial = editingAsset,
            onDismiss = {
                showFormDialog = false
                editingAsset = null
            },
            onSaved = {
                showFormDialog = false
                editingAsset = null
                loadAssets()
            }
        )
    }

    if (deletingAssetTarget != null) {
        val target = deletingAssetTarget!!
        ConfirmDeleteArnetaDialog(
            title = "Hapus Inventaris Aset?",
            subtitle = "${target.optString("name")} (${target.optString("category")})",
            description = "Data aset ini akan dihapus secara permanen dari daftar inventaris dan peta jaringan.",
            onDismiss = { deletingAssetTarget = null },
            onConfirm = {
                deletingAssetTarget = null
                scope.launch {
                    val dRes = ApiClient.request("admin_asset_delete", mapOf("id" to target.optInt("id")))
                    Toast.makeText(context, dRes.message, Toast.LENGTH_SHORT).show()
                    if (dRes.status) loadAssets()
                }
            }
        )
    }
}

/**
 * LAYAR PENUH NATIVE: FORM TAMBAH & EDIT INVENTARIS ASET + AMBIL TITIK GPS OTOMATIS
 */
@SuppressLint("MissingPermission")
@Composable
private fun AssetFormFullDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var name by remember { mutableStateOf(initial?.optString("name") ?: "") }
    var category by remember { mutableStateOf(initial?.optString("category")?.ifEmpty { null } ?: "ODP/ODC") }
    var brand by remember { mutableStateOf(initial?.optString("brand") ?: "") }
    var serial by remember { mutableStateOf(initial?.optString("serial_number") ?: "") }
    var cost by remember {
        mutableStateOf(
            if (initial != null) initial.optDouble("cost", 0.0).toLong().toString() else ""
        )
    }
    var location by remember { mutableStateOf(initial?.optString("location") ?: "") }
    var coords by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }
    var conditionStatus by remember { mutableStateOf(initial?.optString("condition_status")?.ifEmpty { null } ?: "Good") }
    var notes by remember { mutableStateOf(initial?.optString("notes") ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    fun fillWithCurrentGps() {
        val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val lastGps = runCatching { locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val lastNet = runCatching { locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val best = lastGps ?: lastNet
        if (best != null) {
            coords = String.format(Locale.US, "%.6f, %.6f", best.latitude, best.longitude)
            Toast.makeText(context, "Koordinat GPS berhasil diambil!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Pastikan GPS aktif dan coba lagi.", Toast.LENGTH_SHORT).show()
        }
    }

    val gpsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fillWithCurrentGps()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
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
                                text = if (isEdit) "Edit Inventaris Aset" else "Tambah Inventaris Aset",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Data ODP, ODC, ONT, Router & Titik Peta",
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
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.36f)
                                .height(48.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = !isSaving && name.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val res = ApiClient.request(
                                        "admin_asset_save",
                                        mapOf(
                                            "id" to (initial?.optInt("id") ?: 0),
                                            "name" to name.trim(),
                                            "category" to category.trim(),
                                            "brand" to brand.trim(),
                                            "serial_number" to serial.trim(),
                                            "cost" to (cost.toDoubleOrNull() ?: 0.0),
                                            "location" to location.trim(),
                                            "coordinates" to coords.trim(),
                                            "condition_status" to conditionStatus,
                                            "notes" to notes.trim()
                                        )
                                    )
                                    isSaving = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    if (res.status) onSaved()
                                }
                            },
                            modifier = Modifier
                                .weight(0.64f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else "Simpan Aset",
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
                            text = "1. Kategori, Kondisi & Identitas Aset",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("ODP/ODC", "ONT/Modem", "Router", "Kabel/Optik", "Lainnya").forEach { cat ->
                                FilterChip(
                                    selected = category.equals(cat, ignoreCase = true),
                                    onClick = { category = cat },
                                    label = { Text(cat, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Good" to "Good (Baik)",
                                "Repair" to "Repair (Perbaikan)",
                                "Damaged" to "Damaged (Rusak)"
                            ).forEach { (cKey, cLbl) ->
                                FilterChip(
                                    selected = conditionStatus.equals(cKey, ignoreCase = true),
                                    onClick = { conditionStatus = cKey },
                                    label = { Text(cLbl, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Perangkat / ODP *") },
                            placeholder = { Text("Contoh: ODP-01 Jl. Merdeka") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Merk / Brand") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = serial,
                                onValueChange = { serial = it },
                                label = { Text("Serial Number") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = cost,
                            onValueChange = { cost = it },
                            label = { Text("Harga Perolehan (${ApiClient.currencyCode})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = "2. Lokasi & Titik Koordinat Peta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaCyanIncome
                            )
                            TextButton(
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine || hasCoarse) {
                                        fillWithCurrentGps()
                                    } else {
                                        gpsPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = ArnetaGoldPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ambil Lokasi GPS Saya", fontSize = 11.5.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = coords,
                            onValueChange = { coords = it },
                            label = { Text("Koordinat Peta (Lat, Lng)") },
                            placeholder = { Text("-6.200000, 106.816666") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ArnetaCyanIncome) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Alamat / Patokan Lokasi") },
                            placeholder = { Text("Contoh: Tiang depan gang RT 03") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Catatan Tambahan (Splitter / Port Terpakai)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
