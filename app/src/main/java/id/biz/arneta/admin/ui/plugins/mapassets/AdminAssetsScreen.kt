package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR ASSET MANAGER (ODP, ODC, ONT/MODEM, ROUTER, KABEL)
 */
@Composable
fun AdminAssetsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assetList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var totalValue by remember { mutableDoubleStateOf(0.0) }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<JSONObject?>(null) }

    fun loadAssets() {
        scope.launch {
            val res = ApiClient.request("admin_assets")
            if (res.status && res.data != null) {
                totalValue = res.data.optJSONObject("summary")?.optDouble("total_value", 0.0) ?: 0.0
                val arr = res.data.optJSONArray("assets") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                assetList = list
            }
        }
    }

    LaunchedEffect(Unit) { loadAssets() }

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
                Column {
                    Text("Total Nilai Inventaris Aset", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    Text(formatRupiah(totalValue), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ArnetaGoldLight)
                    Text("${assetList.size} Unit Perangkat Terdaftar", fontSize = 11.sp, color = Color.White)
                }
                Button(
                    onClick = {
                        editingAsset = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArnetaGoldPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Tambah Aset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 95.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(assetList) { a ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.optString("name")} (${a.optString("category")})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("SN/Brand: ${a.optString("serial_number")} ${a.optString("brand")} • Kondisi: ${a.optString("condition_status")}", fontSize = 12.sp)
                            Text("Nilai: ${formatRupiah(a.optDouble("cost", 0.0))}", fontSize = 12.sp, color = ArnetaGoldPrimary, fontWeight = FontWeight.Bold)
                            if (a.optString("coordinates").isNotEmpty()) {
                                Text("📍 Koordinat: ${a.optString("coordinates")}", fontSize = 11.sp, color = ArnetaCyanIncome)
                            }
                        }
                        Row {
                            IconButton(onClick = {
                                editingAsset = a
                                showFormDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = ArnetaGoldPrimary)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    ApiClient.request("admin_asset_delete", mapOf("id" to a.optInt("id")))
                                    loadAssets()
                                }
                            }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        var name by remember { mutableStateOf(editingAsset?.optString("name") ?: "") }
        var category by remember { mutableStateOf(editingAsset?.optString("category") ?: "ODP/ODC") }
        var serial by remember { mutableStateOf(editingAsset?.optString("serial_number") ?: "") }
        var cost by remember {
            mutableStateOf(
                if (editingAsset != null) editingAsset!!.optDouble("cost", 0.0).toLong().toString() else ""
            )
        }
        var coords by remember { mutableStateOf(editingAsset?.optString("coordinates") ?: "") }

        AlertDialog(
            onDismissRequest = { showFormDialog = false },
            title = {
                Text(
                    text = if (editingAsset != null) "Edit Inventaris Asset" else "Tambah Inventaris Asset",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Perangkat / ODP") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (ODP/ODC, ONT, Router, Kabel)") }, singleLine = true)
                    OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number / Merk") }, singleLine = true)
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Harga Perolehan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = coords, onValueChange = { coords = it }, label = { Text("Koordinat Peta (Lat, Lng)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = ApiClient.request(
                            "admin_asset_save",
                            mapOf(
                                "id" to (editingAsset?.optInt("id") ?: 0),
                                "name" to name,
                                "category" to category,
                                "serial_number" to serial,
                                "cost" to (cost.toDoubleOrNull() ?: 0.0),
                                "coordinates" to coords
                            )
                        )
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        if (res.status) {
                            showFormDialog = false
                            loadAssets()
                        }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showFormDialog = false }) { Text("Batal") }
            }
        )
    }
}
