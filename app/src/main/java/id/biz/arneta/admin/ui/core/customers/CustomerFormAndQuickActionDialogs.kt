package id.biz.arneta.admin.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.network.optSafeDouble
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private fun parseLatLngPair(coordsStr: String?): Pair<Double, Double>? {
    if (coordsStr.isNullOrBlank()) return null
    val parts = coordsStr.split(",")
    if (parts.size < 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lng = parts[1].trim().toDoubleOrNull() ?: return null
    if (lat.isNaN() || lng.isNaN()) return null
    return lat to lng
}

private fun mergeRouterJsonLists(arr1: JSONArray?, arr2: JSONArray?): List<JSONObject> {
    val result = mutableListOf<JSONObject>()
    val seenNames = HashSet<String>()
    fun appendFrom(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isNotEmpty() && seenNames.add(name.lowercase())) {
                result.add(obj)
            }
        }
    }
    appendFrom(arr2)
    appendFrom(arr1)
    return result
}

/**
 * DIALOG PEMILIH TITIK KOORDINAT PETA INTERAKTIF (LEAFLET)
 * Otomatis melakukan Zoom-In ke titik koordinat & lingkaran radius Router Server terpilih.
 */
@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
internal fun ArnetaMapCoordinatePickerDialog(
    routerName: String,
    routerObj: JSONObject?,
    initialCoordinates: String,
    onDismiss: () -> Unit,
    onConfirmCoordinates: (String) -> Unit
) {
    val context = LocalContext.current

    val routerCoordsPair = remember(routerObj) {
        val fromCoordStr = parseLatLngPair(routerObj?.optString("coordinates"))
        if (fromCoordStr != null) {
            fromCoordStr
        } else {
            val rLat = routerObj?.optDouble("lat", Double.NaN) ?: Double.NaN
            val rLng = routerObj?.optDouble("lng", Double.NaN) ?: Double.NaN
            if (!rLat.isNaN() && !rLng.isNaN()) rLat to rLng else (-6.200000 to 106.816666)
        }
    }
    val routerCoverageMeters = remember(routerObj) {
        routerObj?.optString("coverage", "350")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: routerObj?.optInt("coverage", 350)?.takeIf { it > 0 }
            ?: 350
    }

    val initialPinPair = remember(initialCoordinates, routerCoordsPair) {
        parseLatLngPair(initialCoordinates) ?: routerCoordsPair
    }

    var pickedCoords by remember {
        mutableStateOf(
            String.format(Locale.US, "%.6f, %.6f", initialPinPair.first, initialPinPair.second)
        )
    }
    var distanceInfoText by remember { mutableStateOf("Ketuk peta atau geser pin biru untuk menentukan lokasi") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun movePinToGpsLocation() {
        val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val lastGps = runCatching { locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val lastNet = runCatching { locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val best = when {
            lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            else -> lastNet
        }
        if (best != null) {
            val lat = best.latitude
            val lng = best.longitude
            pickedCoords = String.format(Locale.US, "%.6f, %.6f", lat, lng)
            webViewRef?.evaluateJavascript("if(window.setPickedLocation) window.setPickedLocation($lat, $lng, true);", null)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val lat = loc.latitude
                val lng = loc.longitude
                pickedCoords = String.format(Locale.US, "%.6f, %.6f", lat, lng)
                webViewRef?.evaluateJavascript("if(window.setPickedLocation) window.setPickedLocation($lat, $lng, true);", null)
                runCatching { locMgr.removeUpdates(this) }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        runCatching {
            if (locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            } else if (locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            }
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            movePinToGpsLocation()
        } else {
            Toast.makeText(context, "Izin lokasi diperlukan untuk mengambil titik GPS.", Toast.LENGTH_SHORT).show()
        }
    }

    val pickerHtml = remember(routerName, routerCoordsPair, routerCoverageMeters, initialPinPair) {
        val rLat = routerCoordsPair.first
        val rLng = routerCoordsPair.second
        val pLat = initialPinPair.first
        val pLng = initialPinPair.second
        val safeRName = routerName.replace("'", "\\'").replace("\"", "&quot;")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body { width: 100%; height: 100vh; overflow: hidden; background: #0B1120; font-family: sans-serif; }
                #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; width: 100%; height: 100vh; z-index: 1; }
                .pin-wrap { display: flex; flex-direction: column; align-items: center; transform: translate(-50%, -100%); }
                .pin-label {
                    background: rgba(15, 23, 42, 0.92); color: #FFFFFF; padding: 2px 7px; border-radius: 6px;
                    font-size: 11px; font-weight: bold; white-space: nowrap; box-shadow: 0 2px 5px rgba(0,0,0,0.45);
                    margin-bottom: 3px; border: 1px solid rgba(255,255,255,0.25);
                }
                .pin-label-router { background: #F59E0B; color: #0B0F19; border: 1.5px solid #FFFFFF; }
                .pin-label-pick { background: #3B82F6; color: #FFFFFF; border: 1.5px solid #FFFFFF; }
                .pin-dot {
                    width: 28px; height: 28px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg);
                    display: flex; align-items: center; justify-content: center; border: 2px solid #FFFFFF;
                    box-shadow: 0 3px 6px rgba(0,0,0,0.5);
                }
                .pin-dot span { transform: rotate(45deg); font-size: 13px; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var osmStreet = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20, attribution: '© OpenStreetMap' });
                var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20, attribution: '© Google Hybrid' });

                var routerLat = $rLat;
                var routerLng = $rLng;
                var routerCov = $routerCoverageMeters;
                var routerName = '$safeRName';

                var map = L.map('map', { center: [$pLat, $pLng], zoom: 17, layers: [osmStreet], zoomControl: true });
                L.control.layers({
                    "OpenStreetMap": osmStreet,
                    "Satelit Hybrid (Atap Rumah)": googleHybrid
                }).addTo(map);

                function makePinIcon(label, bgColor, emoji, extraClass) {
                    var html = '<div class="pin-wrap"><div class="pin-label ' + extraClass + '">' + label + '</div>' +
                               '<div class="pin-dot" style="background:' + bgColor + ';"><span>' + emoji + '</span></div></div>';
                    return L.divIcon({ className: '', html: html, iconSize: [0, 0], iconAnchor: [0, 0], popupAnchor: [0, -34] });
                }

                function fmtDist(m) {
                    var d = Math.round(m || 0);
                    return d >= 1000 ? ((d / 1000).toFixed(2) + ' km (' + d + ' m)') : (d + ' m');
                }

                L.circle([routerLat, routerLng], {
                    radius: routerCov,
                    color: '#00C0EF',
                    weight: 2,
                    fillColor: '#00C0EF',
                    fillOpacity: 0.15
                }).addTo(map);

                var rIcon = makePinIcon('📡 ' + routerName + ' (' + fmtDist(routerCov) + ')', '#F59E0B', '📡', 'pin-label-router');
                L.marker([routerLat, routerLng], { icon: rIcon, zIndexOffset: 900 }).addTo(map);

                var pIcon = makePinIcon('📍 Titik Lokasi', '#3B82F6', '📍', 'pin-label-pick');
                var pickMarker = L.marker([$pLat, $pLng], { icon: pIcon, draggable: true, zIndexOffset: 2000 }).addTo(map);

                function notifyAndroid(lat, lng) {
                    var dist = Math.round(map.distance([lat, lng], [routerLat, routerLng]));
                    var inside = dist <= routerCov;
                    var statusTxt = inside
                        ? ('✅ Dalam Radius ' + routerName + ' (' + fmtDist(dist) + ' / ' + fmtDist(routerCov) + ')')
                        : ('⚠️ Luar Radius ' + routerName + ' (' + fmtDist(dist) + ' / ' + fmtDist(routerCov) + ')');
                    if (window.AndroidCoordPicker && window.AndroidCoordPicker.onCoordinateChanged) {
                        window.AndroidCoordPicker.onCoordinateChanged(lat.toFixed(6) + ', ' + lng.toFixed(6), statusTxt);
                    }
                }

                pickMarker.on('dragend', function(e) {
                    var pos = pickMarker.getLatLng();
                    notifyAndroid(pos.lat, pos.lng);
                });

                map.on('click', function(e) {
                    pickMarker.setLatLng(e.latlng);
                    notifyAndroid(e.latlng.lat, e.latlng.lng);
                });

                window.setPickedLocation = function(lat, lng, fly) {
                    pickMarker.setLatLng([lat, lng]);
                    if (fly) map.flyTo([lat, lng], 18, { duration: 0.7 });
                    notifyAndroid(lat, lng);
                };

                window.focusServerArea = function() {
                    map.flyTo([routerLat, routerLng], 17, { duration: 0.7 });
                };

                setTimeout(function() {
                    map.invalidateSize();
                    map.setView([$pLat, $pLng], 17);
                    notifyAndroid($pLat, $pLng);
                }, 250);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    ArnetaFullScreenDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1120),
        topBar = {
            Surface(
                color = Color(0xFF141B2D),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                            }
                            Column {
                                Text(
                                    text = "Pilih Koordinat Lokasi Peta",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Server: $routerName • Radius: ${routerCoverageMeters}m",
                                    fontSize = 11.5.sp,
                                    color = ArnetaGoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = {
                                    webViewRef?.evaluateJavascript("if(window.focusServerArea) window.focusServerArea();", null)
                                },
                                label = { Text("📡 Fokus Server", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            AssistChip(
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine || hasCoarse) {
                                        movePinToGpsLocation()
                                    } else {
                                        gpsPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                label = { Text("📍 GPS Saya", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Koordinat Terpilih: $pickedCoords",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = ArnetaGoldPrimary
                            )
                            Text(
                                text = distanceInfoText,
                                fontSize = 11.sp,
                                color = ArnetaCyanIncome,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .height(46.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onConfirmCoordinates(pickedCoords) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.65f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Konfirmasi Koordinat",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onCoordinateChanged(coordStr: String, distStatus: String) {
                                post {
                                    pickedCoords = coordStr
                                    distanceInfoText = distStatus
                                }
                            }
                        },
                        "AndroidCoordPicker"
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(ApiClient.baseUrl, pickerHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { webViewRef = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

/**
 * LAYAR PENUH NATIVE: TAMBAH / EDIT PELANGGAN (DENGAN KUNCI KOORDINAT SERVER & INTERACTIVE MAP PICKER)
 */
@Composable
internal fun CustomerFormDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    var username by remember { mutableStateOf(initial?.optString("username") ?: "") }
    var fullname by remember { mutableStateOf(initial?.optString("fullname") ?: "") }
    var password by remember { mutableStateOf(if (isEdit) "" else "123456") }
    var phonenumber by remember { mutableStateOf(initial?.optString("phonenumber") ?: "") }
    var email by remember { mutableStateOf(initial?.optString("email") ?: "") }
    var city by remember { mutableStateOf(initial?.optString("city") ?: "") }
    var address by remember { mutableStateOf(initial?.optString("address") ?: "") }
    var serviceType by remember { mutableStateOf(initial?.optString("service_type")?.ifEmpty { null } ?: "Hotspot") }
    var status by remember { mutableStateOf(initial?.optString("status")?.ifEmpty { null } ?: "Active") }
    var pppoeUser by remember { mutableStateOf(initial?.optString("pppoe_username") ?: "") }
    var pppoePass by remember { mutableStateOf(initial?.optString("pppoe_password") ?: "") }
    var pppoeIp by remember { mutableStateOf(initial?.optString("pppoe_ip") ?: "") }
    var coordinates by remember { mutableStateOf(initial?.optString("coordinates") ?: "") }

    val cachedPlans = remember { ApiClient.getCached("admin_plans")?.data }
    val cachedRouters = remember { ApiClient.getCached("admin_routers") }

    var allInternetPlans by remember {
        val arr = cachedPlans?.optJSONArray("plans") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (!p.optString("type").equals("Balance", ignoreCase = true)) list.add(p)
        }
        mutableStateOf(list.toList())
    }
    var routersCatalogList by remember {
        val rArr1 = cachedPlans?.optJSONArray("routers")
        val rArr2 = cachedRouters?.dataArray ?: cachedRouters?.data?.optJSONArray("routers")
        mutableStateOf(mergeRouterJsonLists(rArr1, rArr2))
    }

    var routerOptions by remember {
        mutableStateOf(extractRouterOptions(allInternetPlans, cachedPlans?.optJSONArray("routers")))
    }
    var selectedRouterFilter by remember {
        mutableStateOf(
            initial?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotEmpty() } ?: "All"
        )
    }
    var paymentUsings by remember { mutableStateOf(listOf("Cash", "Recharge Zero")) }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var selectedUsing by remember { mutableStateOf("") }
    var activatePackageOnEdit by remember { mutableStateOf(false) }
    var showMapCoordinatePicker by remember { mutableStateOf(false) }

    var sendWelcomeMessage by remember { mutableStateOf(!isEdit) }
    var welcomeViaWa by remember { mutableStateOf(true) }
    var welcomeViaSms by remember { mutableStateOf(false) }
    var welcomeViaMail by remember { mutableStateOf(false) }

    var customFields by remember(initial) {
        val cfArr = initial?.optJSONArray("custom_fields")
        val initList = mutableListOf<Pair<String, String>>()
        if (cfArr != null) {
            for (i in 0 until cfArr.length()) {
                val cfObj = cfArr.optJSONObject(i) ?: continue
                val k = cfObj.optString("name", cfObj.optString("field_name", "")).trim()
                val v = cfObj.optString("value", cfObj.optString("field_value", "")).trim()
                if (k.isNotEmpty()) initList.add(k to v)
            }
        }
        mutableStateOf(initList.toList())
    }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = ApiClient.request("admin_plans")
        val rArrFromPlans = res.data?.optJSONArray("routers")
        if (res.status && res.data != null) {
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val pList = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                if (!p.optString("type").equals("Balance", ignoreCase = true)) {
                    pList.add(p)
                }
            }
            allInternetPlans = pList
            routerOptions = extractRouterOptions(pList, rArrFromPlans)
            routersCatalogList = mergeRouterJsonLists(rArrFromPlans, null)

            val uArr = res.data.optJSONArray("usings")
            if (uArr != null && uArr.length() > 0) {
                val uList = mutableListOf<String>()
                for (i in 0 until uArr.length()) {
                    val item = uArr.optString(i)
                    if (!isEdit && item.equals("Balance", ignoreCase = true)) continue
                    uList.add(item)
                }
                paymentUsings = uList
            }
        }

        val rRes = ApiClient.request("admin_routers")
        if (rRes.status) {
            val rArrFull = rRes.dataArray ?: rRes.data?.optJSONArray("routers")
            routersCatalogList = mergeRouterJsonLists(rArrFromPlans, rArrFull)
        }
    }

    LaunchedEffect(routerOptions) {
        val specificRouters = routerOptions.filter { it.first != "All" && !it.first.equals("Radius", ignoreCase = true) }
        if (selectedRouterFilter == "All" && specificRouters.size == 1) {
            selectedRouterFilter = specificRouters.first().first
        }
    }

    val matchingPlans = remember(allInternetPlans, serviceType, selectedRouterFilter) {
        allInternetPlans.filter { p ->
            val matchType = when (serviceType.uppercase()) {
                "HOTSPOT" -> p.optString("type").equals("Hotspot", ignoreCase = true)
                "PPPOE" -> p.optString("type").equals("PPPoE", ignoreCase = true)
                else -> true
            }
            val matchRouter = selectedRouterFilter == "All" ||
                p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true) ||
                (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1)
            matchType && matchRouter
        }
    }

    LaunchedEffect(matchingPlans, isEdit, activatePackageOnEdit) {
        if ((!isEdit || activatePackageOnEdit) && matchingPlans.isNotEmpty()) {
            if (selectedPlan == null || matchingPlans.none { it.optInt("id") == selectedPlan?.optInt("id") }) {
                selectedPlan = matchingPlans.first()
            }
        }
    }

    val effectiveServerName = remember(selectedRouterFilter, selectedPlan, initial) {
        when {
            selectedRouterFilter.isNotBlank() && selectedRouterFilter != "All" -> selectedRouterFilter
            selectedPlan != null && selectedPlan!!.optString("routers").isNotBlank() && !selectedPlan!!.optString("routers").equals("Radius", true) -> {
                selectedPlan!!.optString("routers")
            }
            else -> initial?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotBlank() && !it.equals("Radius", true) } ?: ""
        }
    }
    val isServerChosenForMap = effectiveServerName.isNotBlank() && !effectiveServerName.equals("All", ignoreCase = true)
    val selectedRouterDetailObj = remember(effectiveServerName, routersCatalogList) {
        routersCatalogList.firstOrNull { it.optString("name").equals(effectiveServerName, ignoreCase = true) }
    }

    val existingBalance = initial.optSafeDouble("balance", 0.0)
    val selectedPlanPrice = selectedPlan.optSafeDouble("price", 0.0)
    val isBalanceInsufficient = !isEdit || (selectedPlanPrice > 0.0 && existingBalance < selectedPlanPrice)

    LaunchedEffect(isBalanceInsufficient, selectedUsing) {
        if (isBalanceInsufficient && selectedUsing.equals("Balance", ignoreCase = true)) {
            selectedUsing = ""
        }
    }

    val needPackageAndMethod = !isEdit || activatePackageOnEdit
    val canSubmit = !isSaving && username.isNotBlank() && fullname.isNotBlank() &&
        (!needPackageAndMethod || (selectedPlan != null && selectedUsing.isNotBlank()))

    fun tryOpenMapPicker() {
        if (!isServerChosenForMap) {
            Toast.makeText(
                context,
                "Pilih Lokasi Server / Router terlebih dahulu untuk membuka kunci koordinat peta!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            showMapCoordinatePicker = true
        }
    }

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
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isEdit) "Edit Data Pelanggan" else "Tambah Pelanggan Baru",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isEdit) "@${initial?.optString("username")} • Saldo: ${formatRupiah(existingBalance)}"
                                else "Profil, Kredensial & Aktivasi Langganan",
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
                            text = serviceType.uppercase(),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (needPackageAndMethod && selectedPlan != null) {
                        val displayPrice = if (selectedUsing.equals("Recharge Zero", ignoreCase = true)) {
                            0.0
                        } else {
                            selectedPlanPrice
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Paket Dipilih: ${selectedPlan?.optString("name_plan")}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Router: ${selectedPlan?.optString("routers", "-")} • Metode: ${selectedUsing.ifEmpty { "Belum dipilih" }}",
                                    fontSize = 10.5.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                            Text(
                                text = formatRupiah(displayPrice),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                color = ArnetaGoldPrimary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.38f)
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
                                    val cfNames = customFields.map { it.first.trim() }.filter { it.isNotEmpty() }
                                    val cfValues = customFields.map { it.second.trim() }.filter { it.isNotEmpty() }

                                    val params = mutableMapOf<String, Any?>(
                                        "id" to (initial?.optInt("id") ?: 0),
                                        "username" to username.trim(),
                                        "fullname" to fullname.trim(),
                                        "password" to password.trim(),
                                        "phonenumber" to phonenumber.trim(),
                                        "email" to email.trim(),
                                        "city" to city.trim(),
                                        "address" to address.trim(),
                                        "service_type" to serviceType,
                                        "status" to status,
                                        "pppoe_username" to pppoeUser.trim(),
                                        "pppoe_password" to pppoePass.trim(),
                                        "pppoe_ip" to pppoeIp.trim(),
                                        "coordinates" to coordinates.trim(),
                                        "using" to selectedUsing,
                                        "send_welcome_message" to sendWelcomeMessage,
                                        "wa" to welcomeViaWa,
                                        "sms" to welcomeViaSms,
                                        "mail" to welcomeViaMail
                                    )

                                    if (needPackageAndMethod) {
                                        params["ppln"] = selectedPlan?.optInt("id") ?: 0
                                    }
                                    if (cfNames.isNotEmpty() && cfNames.size == cfValues.size) {
                                        params["custom_field_name"] = cfNames
                                        params["custom_field_value"] = cfValues
                                    }

                                    val res = ApiClient.request("admin_customer_save", params)
                                    isSaving = false
                                    if (res.status) {
                                        if (needPackageAndMethod && selectedPlan != null) {
                                            ArnetaTransactionToastManager.showFromApiResponse(
                                                resData = res.data,
                                                fallbackTitle = if (isEdit) "Data & Paket Diperbarui!" else "Pelanggan & Paket Aktif!",
                                                fallbackCustomer = res.data,
                                                fallbackPlanName = selectedPlan?.optString("name_plan") ?: "-",
                                                fallbackPrice = selectedPlan.optSafeDouble("price", 0.0),
                                                fallbackMethod = selectedUsing,
                                                fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                                            )
                                        } else {
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                        onSaved()
                                    } else {
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(0.62f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = if (isEdit) Icons.Filled.CheckCircle else Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaving) "Menyimpan..." else if (isEdit) "Simpan Perubahan" else "Buat & Aktifkan",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
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
            // SECTION 1: KATEGORI LAYANAN & STATUS AKUN
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
                        text = "1. Jenis Layanan & Status Akun",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaGoldPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Hotspot", "PPPoE", "Others").forEach { t ->
                            FilterChip(
                                selected = serviceType.equals(t, ignoreCase = true),
                                onClick = { serviceType = t },
                                label = { Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = if (serviceType.equals(t, ignoreCase = true)) {
                                    { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                        }
                    }

                    Text(
                        text = "Status Akun Pelanggan:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "Active" to "Active (Aktif)",
                            "Inactive" to "Inactive (Nonaktif)",
                            "Suspended" to "Suspended (Isolir)",
                            "Banned" to "Banned (Blokir)"
                        ).forEach { (stKey, stLabel) ->
                            FilterChip(
                                selected = status.equals(stKey, ignoreCase = true),
                                onClick = { status = stKey },
                                label = { Text(stLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // SECTION 2: IDENTITAS, SERVER ROUTER & KOORDINAT PETA
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
                        text = "2. Identitas, Kontak & Titik Lokasi Pelanggan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ArnetaCyanIncome
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username Pelanggan *") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = fullname,
                        onValueChange = { fullname = it },
                        label = { Text("Nama Lengkap *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isEdit) "Password Baru (Kosongkan jika tidak diubah)" else "Password Login") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = ArnetaGoldPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phonenumber,
                        onValueChange = { phonenumber = it },
                        label = { Text("Nomor WhatsApp") },
                        placeholder = { Text("0812xxxx / 62812xxxx") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("Kota / Area") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Alamat Pemasangan Lengkap") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Pemilih Server Router sebelum mengisi koordinat peta
                    CompactDropdownSelector(
                        label = "Lokasi Server / Router (Wajib sebelum mengisi koordinat)",
                        selectedValue = if (selectedRouterFilter == "All") "" else selectedRouterFilter,
                        placeholder = "-- Pilih Router Server Untuk Buka Kunci Peta --",
                        options = routerOptions.filter { it.first != "All" },
                        icon = Icons.Filled.Router,
                        onValueSelected = { selectedRouterFilter = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Kolom Koordinat Peta: Terkunci sampai Router Server dipilih, Ikon Lokasi Clickable
                    OutlinedTextField(
                        value = coordinates,
                        onValueChange = { if (isServerChosenForMap) coordinates = it },
                        enabled = isServerChosenForMap,
                        label = {
                            Text(
                                text = if (isServerChosenForMap) "Koordinat Peta (Server: $effectiveServerName)"
                                else "🔒 Koordinat Peta (Pilih Router Server dulu)"
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (isServerChosenForMap) "Ketuk ikon 📍 untuk pilih di peta"
                                else "Pilih Router Server di atas terlebih dahulu"
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = { tryOpenMapPicker() }) {
                                Icon(
                                    imageVector = if (isServerChosenForMap) Icons.Filled.LocationOn else Icons.Filled.Lock,
                                    contentDescription = "Buka Peta",
                                    tint = if (isServerChosenForMap) ArnetaCyanIncome else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { tryOpenMapPicker() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = if (isServerChosenForMap) ArnetaGoldPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Pilih Peta",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isServerChosenForMap) ArnetaGoldPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isServerChosenForMap) { tryOpenMapPicker() }
                    )
                }
            }

            // SECTION 3: KHUSUS KREDENSIAL PPPOE
            if (serviceType.equals("PPPoE", ignoreCase = true)) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaIndigoAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
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
                                text = "3. Kredensial Khusus PPPoE (Router / ONT)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ArnetaGoldLight
                            )
                            if (username.isNotBlank() && pppoeUser.isBlank()) {
                                TextButton(
                                    onClick = {
                                        pppoeUser = username.trim()
                                        if (pppoePass.isBlank() && password.isNotBlank()) {
                                            pppoePass = password.trim()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Samakan dgn Akun", fontSize = 11.sp, color = ArnetaCyanIncome, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = pppoeUser,
                            onValueChange = { pppoeUser = it },
                            label = { Text("PPPoE Username (Opsional jika sama dgn Username)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pppoePass,
                                onValueChange = { pppoePass = it },
                                label = { Text("PPPoE Password") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pppoeIp,
                                onValueChange = { pppoeIp = it },
                                label = { Text("Remote IP Static") },
                                placeholder = { Text("10.10.10.2") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SECTION 4: AKTIVASI PAKET LANGGANAN
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    if (isEdit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sekaligus Aktifkan / Perpanjang Paket?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ArnetaGoldPrimary
                                )
                                Text(
                                    text = "Aktifkan sakelar ini jika ingin langsung mengisi ulang paket pelanggan",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = activatePackageOnEdit,
                                onCheckedChange = { activatePackageOnEdit = it }
                            )
                        }
                    } else {
                        Text(
                            text = "Pilih Paket Awal & Metode Pembayaran",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArnetaGoldPrimary
                        )
                    }

                    if (!isEdit || activatePackageOnEdit) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CompactDropdownSelector(
                                label = "Lokasi Router",
                                selectedValue = selectedRouterFilter,
                                options = routerOptions,
                                icon = Icons.Filled.Router,
                                onValueSelected = { selectedRouterFilter = it },
                                modifier = Modifier.weight(1f)
                            )
                            CompactDropdownSelector(
                                label = "Metode Pembayaran *",
                                selectedValue = selectedUsing,
                                placeholder = "Pilih Metode...",
                                disabledOptions = if (isBalanceInsufficient) setOf("Balance") else emptySet(),
                                options = paymentUsings.map { u ->
                                    u to when {
                                        u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (${ApiClient.currencyCode} 0)"
                                        u.equals("Balance", ignoreCase = true) -> {
                                            if (isBalanceInsufficient) "💰 Potong Saldo (Saldo tidak cukup)"
                                            else "💰 Potong Saldo (${formatRupiah(existingBalance)})"
                                        }
                                        else -> u
                                    }
                                },
                                icon = Icons.Filled.Payments,
                                onValueSelected = { selectedUsing = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Daftar Paket $serviceType (${matchingPlans.size} tersedia):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArnetaCyanIncome
                        )

                        if (matchingPlans.isEmpty()) {
                            Text(
                                text = "Tidak ada paket $serviceType yang tersedia untuk filter router ini.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                matchingPlans.forEach { p ->
                                    SelectablePlanCard(
                                        plan = p,
                                        isSelected = selectedPlan?.optInt("id") == p.optInt("id"),
                                        onClick = {
                                            selectedPlan = p
                                            val pRouter = p.optString("routers", "").trim()
                                            if (pRouter.isNotEmpty() && !pRouter.equals("Radius", true) && selectedRouterFilter == "All") {
                                                selectedRouterFilter = pRouter
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: ATRIBUT TAMBAHAN (CUSTOM FIELDS) & PESAN SAMBUTAN
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Atribut Tambahan (Custom Fields)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        FilledTonalButton(
                            onClick = { customFields = customFields + ("" to "") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah Atribut", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    customFields.forEachIndexed { idx, pair ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = pair.first,
                                onValueChange = { newKey ->
                                    val mutable = customFields.toMutableList()
                                    mutable[idx] = newKey to pair.second
                                    customFields = mutable
                                },
                                label = { Text("Nama Atribut", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pair.second,
                                onValueChange = { newVal ->
                                    val mutable = customFields.toMutableList()
                                    mutable[idx] = pair.first to newVal
                                    customFields = mutable
                                },
                                label = { Text("Nilai", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val mutable = customFields.toMutableList()
                                    mutable.removeAt(idx)
                                    customFields = mutable
                                }
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = ArnetaRedDanger)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kirim Pesan Sambutan Akun", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(
                                "Kirim detail username, password & link login ke pelanggan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        Switch(
                            checked = sendWelcomeMessage,
                            onCheckedChange = { sendWelcomeMessage = it }
                        )
                    }

                    AnimatedVisibility(visible = sendWelcomeMessage) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = welcomeViaWa,
                                onClick = { welcomeViaWa = !welcomeViaWa },
                                label = { Text("WhatsApp", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = welcomeViaSms,
                                onClick = { welcomeViaSms = !welcomeViaSms },
                                label = { Text("SMS", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = welcomeViaMail,
                                onClick = { welcomeViaMail = !welcomeViaMail },
                                label = { Text("Email", fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showMapCoordinatePicker && isServerChosenForMap) {
        ArnetaMapCoordinatePickerDialog(
            routerName = effectiveServerName,
            routerObj = selectedRouterDetailObj,
            initialCoordinates = coordinates,
            onDismiss = { showMapCoordinatePicker = false },
            onConfirmCoordinates = { chosenCoords ->
                coordinates = chosenCoords
                showMapCoordinatePicker = false
            }
        )
    }
}

/**
 * LAYAR PENUH NATIVE: ISI ULANG PAKET & SALDO CEPAT DARI DAFTAR PELANGGAN
 */
@Composable
fun QuickRechargeOrDepositDialog(
    initialCustomer: JSONObject?,
    mode: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSuperAdmin = remember { ApiClient.adminRole.equals("SuperAdmin", ignoreCase = true) }
    val isDeposit = mode == "deposit"

    val cachedPlans = remember { ApiClient.getCached("admin_plans")?.data }

    var selectedCustomer by remember { mutableStateOf(initialCustomer) }
    var plans by remember {
        val arr = cachedPlans?.optJSONArray("plans") ?: JSONArray()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val pType = p.optString("type").uppercase()
            if (isDeposit && pType == "BALANCE") list.add(p)
            else if (!isDeposit && pType != "BALANCE") list.add(p)
        }
        mutableStateOf(list.toList())
    }
    var routerOptions by remember {
        mutableStateOf(extractRouterOptions(plans, cachedPlans?.optJSONArray("routers")))
    }
    var selectedRouterFilter by remember {
        mutableStateOf(
            initialCustomer?.optJSONObject("active_package")?.optString("routers", "")?.takeIf { it.isNotEmpty() } ?: "All"
        )
    }
    var paymentUsings by remember {
        val uArr = cachedPlans?.optJSONArray("usings")
        val initUsings = if (uArr != null && uArr.length() > 0) {
            List(uArr.length()) { i -> uArr.optString(i) }
        } else {
            listOf("Cash", "Balance", "Recharge Zero")
        }
        mutableStateOf(initUsings)
    }
    var selectedUsing by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var depositNote by remember { mutableStateOf("") }
    var filterPlanType by remember {
        mutableStateOf(
            if (isSuperAdmin && initialCustomer != null) resolveCustomerPlanCategory(initialCustomer) else "All"
        )
    }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        val res = ApiClient.request("admin_plans")
        if (res.status && res.data != null) {
            val arr = res.data.optJSONArray("plans") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val pType = p.optString("type").uppercase()
                if (isDeposit && pType == "BALANCE") {
                    list.add(p)
                } else if (!isDeposit && pType != "BALANCE") {
                    list.add(p)
                }
            }
            plans = list
            routerOptions = extractRouterOptions(list, res.data.optJSONArray("routers"))

            val uArr = res.data.optJSONArray("usings")
            if (uArr != null && uArr.length() > 0) {
                val uList = mutableListOf<String>()
                for (i in 0 until uArr.length()) uList.add(uArr.optString(i))
                paymentUsings = uList
            }
        }
    }

    val displayedPlans = remember(plans, filterPlanType, selectedRouterFilter, mode) {
        plans.filter { p ->
            if (isDeposit) {
                true
            } else {
                val matchType = filterPlanType == "All" || p.optString("type").equals(filterPlanType, ignoreCase = true)
                val matchRouter = selectedRouterFilter == "All" ||
                    p.optString("routers", "").equals(selectedRouterFilter, ignoreCase = true) ||
                    (selectedRouterFilter.equals("Radius", ignoreCase = true) && p.optInt("is_radius", 0) == 1)
                matchType && matchRouter
            }
        }
    }

    LaunchedEffect(displayedPlans) {
        if (displayedPlans.isNotEmpty() && (selectedPlan == null || displayedPlans.none { it.optInt("id") == selectedPlan?.optInt("id") })) {
            selectedPlan = displayedPlans.first()
        }
    }

    val customerBalance = selectedCustomer.optSafeDouble("balance", 0.0)
    val currentPlanPrice = selectedPlan.optSafeDouble("price", 0.0)
    val isBalanceInsufficient = selectedCustomer == null || (currentPlanPrice > 0.0 && customerBalance < currentPlanPrice)

    LaunchedEffect(isBalanceInsufficient, selectedUsing) {
        if (isBalanceInsufficient && selectedUsing.equals("Balance", ignoreCase = true)) {
            selectedUsing = ""
        }
    }

    val isReadyToSubmit = if (isDeposit) {
        !isProcessing && selectedCustomer != null && (customAmount.isNotBlank() || selectedPlan != null)
    } else {
        !isProcessing && selectedCustomer != null && selectedPlan != null && selectedUsing.isNotBlank()
    }

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
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isDeposit) "Isi Ulang Saldo Pelanggan" else "Isi Ulang Paket Internet",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (selectedCustomer != null) {
                                    "${selectedCustomer?.optString("fullname")} (@${selectedCustomer?.optString("username")}) • Saldo: ${formatRupiah(customerBalance)}"
                                } else {
                                    "Pilih pelanggan dan paket layanan di bawah ini"
                                },
                                fontSize = 11.5.sp,
                                color = ArnetaGoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val summaryTotal = if (isDeposit) {
                        if (customAmount.isNotBlank()) customAmount.toDoubleOrNull() ?: 0.0 else currentPlanPrice
                    } else {
                        if (selectedUsing.equals("Recharge Zero", ignoreCase = true)) 0.0 else currentPlanPrice
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDeposit && customAmount.isNotBlank()) "Top-Up Nominal Manual"
                                else (selectedPlan?.optString("name_plan") ?: "Belum memilih paket"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isDeposit) {
                                Text(
                                    text = "Router: ${selectedPlan?.optString("routers", "-")} • Metode: ${selectedUsing.ifEmpty { "Wajib dipilih" }}",
                                    fontSize = 11.sp,
                                    color = ArnetaCyanIncome
                                )
                            }
                        }
                        Text(
                            text = formatRupiah(summaryTotal),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = ArnetaGoldPrimary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .height(46.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = isReadyToSubmit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArnetaGoldPrimary,
                                contentColor = Color(0xFF0B0F19)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val cust = selectedCustomer ?: return@Button
                                isProcessing = true
                                scope.launch {
                                    val endpoint = if (isDeposit) "admin_deposit" else "admin_prepaid_recharge"
                                    val params = mutableMapOf<String, Any?>(
                                        "customer_id" to cust.optInt("id"),
                                        "using" to selectedUsing,
                                        "note" to depositNote.trim()
                                    )
                                    if (isDeposit && customAmount.isNotBlank()) {
                                        params["amount"] = customAmount.toDoubleOrNull() ?: 0.0
                                    } else if (selectedPlan != null) {
                                        params["plan_id"] = selectedPlan!!.optInt("id")
                                        params["server"] = selectedPlan!!.optString("routers")
                                        params["routers"] = selectedPlan!!.optString("routers")
                                    }

                                    val res = ApiClient.request(endpoint, params)
                                    isProcessing = false
                                    if (res.status) {
                                        ArnetaTransactionToastManager.showFromApiResponse(
                                            resData = res.data,
                                            fallbackTitle = if (isDeposit) "Isi Ulang Saldo Berhasil!" else "Paket Berhasil Diaktifkan!",
                                            fallbackCustomer = cust,
                                            fallbackPlanName = if (isDeposit && customAmount.isNotBlank()) "Top-Up Saldo Manual" else (selectedPlan?.optString("name_plan") ?: "-"),
                                            fallbackPrice = if (isDeposit && customAmount.isNotBlank()) (customAmount.toDoubleOrNull() ?: 0.0) else selectedPlan.optSafeDouble("price", 0.0),
                                            fallbackMethod = if (isDeposit) "Deposit" else selectedUsing,
                                            fallbackRouter = selectedPlan?.optString("routers", "-") ?: "-"
                                        )
                                        onSuccess()
                                    } else {
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(0.65f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeposit) Icons.Filled.AccountBalanceWallet else Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isProcessing) "Memproses..." else if (isDeposit) "Tambah Saldo Sekarang" else "Aktifkan Paket Sekarang",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            CustomerLiveSearchPicker(
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { cust ->
                    selectedCustomer = cust
                    if (cust != null && !isDeposit) {
                        if (isSuperAdmin) {
                            filterPlanType = resolveCustomerPlanCategory(cust)
                        }
                        val custRouter = cust.optJSONObject("active_package")?.optString("routers", "") ?: ""
                        if (custRouter.isNotEmpty() && routerOptions.any { it.first.equals(custRouter, ignoreCase = true) }) {
                            selectedRouterFilter = custRouter
                        }
                    }
                }
            )

            if (isDeposit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it },
                        label = { Text("Nominal Manual (${ApiClient.currencyCode})", fontSize = 11.5.sp) },
                        placeholder = { Text("Contoh: 50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = depositNote,
                        onValueChange = { depositNote = it },
                        label = { Text("Catatan Deposit", fontSize = 11.5.sp) },
                        placeholder = { Text("Setoran tunai") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactDropdownSelector(
                        label = "Server / Router",
                        selectedValue = selectedRouterFilter,
                        options = routerOptions,
                        icon = Icons.Filled.Router,
                        onValueSelected = { selectedRouterFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    CompactDropdownSelector(
                        label = "Jenis Paket",
                        selectedValue = filterPlanType,
                        options = listOf(
                            "All" to "Semua Paket",
                            "Hotspot" to "Hotspot",
                            "PPPoE" to "PPPoE"
                        ),
                        icon = Icons.Filled.Wifi,
                        onValueSelected = { filterPlanType = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                CompactDropdownSelector(
                    label = "Metode Pembayaran *",
                    selectedValue = selectedUsing,
                    placeholder = "-- Pilih Metode Pembayaran --",
                    disabledOptions = if (isBalanceInsufficient) setOf("Balance") else emptySet(),
                    options = paymentUsings.map { u ->
                        u to when {
                            u.equals("Recharge Zero", ignoreCase = true) -> "⚡ Recharge Zero (${ApiClient.currencyCode} 0)"
                            u.equals("Balance", ignoreCase = true) -> {
                                if (isBalanceInsufficient) "💰 Potong Saldo (Saldo tidak mencukupi)"
                                else "💰 Potong Saldo Pelanggan (${formatRupiah(customerBalance)})"
                            }
                            else -> u
                        }
                    },
                    icon = Icons.Filled.Payments,
                    onValueSelected = { selectedUsing = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = if (isDeposit) "Pilih Paket Saldo (${displayedPlans.size} tersedia):"
                else "Pilih Paket Internet (${displayedPlans.size} tersedia):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ArnetaGoldPrimary
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(displayedPlans) { p ->
                    val pid = p.optInt("id")
                    val isSel = selectedPlan?.optInt("id") == pid && customAmount.isBlank()
                    SelectablePlanCard(
                        plan = p,
                        isSelected = isSel,
                        onClick = {
                            selectedPlan = p
                            customAmount = ""
                        }
                    )
                }
            }
        }
    }
}
