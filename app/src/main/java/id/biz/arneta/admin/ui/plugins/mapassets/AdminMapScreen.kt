package id.biz.arneta.admin.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import org.json.JSONArray
import org.json.JSONObject

/**
 * LAYAR PETA PELANGGAN, ROUTER COVERAGE & ODP INTERAKTIF
 */
@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
fun AdminMapScreen() {
    val context = LocalContext.current

    var customersGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var routersGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var assetsGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var mapHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLocatingGps by remember { mutableStateOf(false) }
    var mapWebViewRef by remember { mutableStateOf<WebView?>(null) }

    fun sendMyLocationToWebView(loc: Location) {
        isLocatingGps = false
        val lat = loc.latitude
        val lng = loc.longitude
        val acc = loc.accuracy
        mapWebViewRef?.evaluateJavascript(
            "if(window.focusMyGpsLocation) window.focusMyGpsLocation($lat, $lng, $acc);",
            null
        )
    }

    fun startNativeGpsLocation() {
        val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locMgr == null) {
            Toast.makeText(context, "Layanan GPS tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show()
            return
        }

        val gpsEnabled = runCatching { locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val netEnabled = runCatching { locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

        if (!gpsEnabled && !netEnabled) {
            Toast.makeText(context, "Silakan aktifkan GPS / Lokasi HP Anda terlebih dahulu.", Toast.LENGTH_LONG).show()
            runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }

        isLocatingGps = true

        // Gunakan lokasi terakhir yang akurat jika tersedia agar respons instan
        val lastGps = runCatching { locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val lastNet = runCatching { locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val bestLast = when {
            lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            else -> lastNet
        }
        if (bestLast != null) {
            sendMyLocationToWebView(bestLast)
        }

        // Minta titik koordinat baru yang paling presisi dari sensor GPS
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                sendMyLocationToWebView(location)
                runCatching { locMgr.removeUpdates(this) }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        runCatching {
            if (gpsEnabled) {
                locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            }
            if (netEnabled) {
                locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            }
        }.onFailure {
            isLocatingGps = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startNativeGpsLocation()
        } else {
            Toast.makeText(context, "Izin lokasi diperlukan untuk menampilkan posisi Anda.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestMyGpsLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            startNativeGpsLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val res = ApiClient.request("admin_map_data")
        if (res.status && res.data != null) {
            val cArr = res.data.optJSONArray("customers") ?: JSONArray()
            val rArr = res.data.optJSONArray("routers") ?: JSONArray()
            val aArr = res.data.optJSONArray("assets") ?: JSONArray()

            val cList = mutableListOf<JSONObject>()
            for (i in 0 until cArr.length()) cList.add(cArr.getJSONObject(i))
            customersGeo = cList

            val rList = mutableListOf<JSONObject>()
            for (i in 0 until rArr.length()) rList.add(rArr.getJSONObject(i))
            routersGeo = rList

            val aList = mutableListOf<JSONObject>()
            for (i in 0 until aArr.length()) aList.add(aArr.getJSONObject(i))
            assetsGeo = aList

            val cBase64 = android.util.Base64.encodeToString(
                cArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val rBase64 = android.util.Base64.encodeToString(
                rArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val aBase64 = android.util.Base64.encodeToString(
                aArr.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            val initLat = rList.firstOrNull()?.optDouble("lat")
                ?: cList.firstOrNull()?.optDouble("lat")
                ?: -6.200000
            val initLng = rList.firstOrNull()?.optDouble("lng")
                ?: cList.firstOrNull()?.optDouble("lng")
                ?: 106.816666

            mapHtml = """
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
                        .pin-label-router { background: #F59E0B; color: #0B0F19; border: 1.5px solid #FFFFFF; font-size: 11.5px; }
                        .pin-label-me { background: #3B82F6; color: #FFFFFF; border: 1.5px solid #FFFFFF; font-size: 11.5px; }
                        .pin-dot {
                            width: 26px; height: 26px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg);
                            display: flex; align-items: center; justify-content: center; border: 2px solid #FFFFFF;
                            box-shadow: 0 3px 6px rgba(0,0,0,0.5);
                        }
                        .pin-dot span { transform: rotate(45deg); font-size: 12px; }
                        .leaflet-popup-content-wrapper { border-radius: 12px; font-size: 12.5px; line-height: 1.45; }
                        .btn-dir {
                            display: inline-block; margin-top: 6px; padding: 5px 10px; background: #00C0EF;
                            color: #FFFFFF !important; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 11.5px;
                        }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        function decodeB64(b64) {
                            try {
                                return JSON.parse(decodeURIComponent(Array.prototype.map.call(window.atob(b64), function(c) {
                                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                                }).join('')));
                            } catch (e) { return []; }
                        }

                        function formatDistanceLabel(meters) {
                            var m = Math.round(parseFloat(meters) || 0);
                            if (m >= 1000) {
                                return (m / 1000).toFixed(2).replace(/\.?0+$/, '') + ' km (' + m + ' m)';
                            }
                            return m + ' m';
                        }

                        var osmStreet = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20, attribution: '© OpenStreetMap' });
                        var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 20, attribution: '© Carto' });
                        var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20, attribution: '© Google Hybrid' });

                        var map = L.map('map', { center: [$initLat, $initLng], zoom: 17, layers: [osmStreet], zoomControl: true });
                        L.control.layers({
                            "OpenStreetMap (Default Web)": osmStreet,
                            "Peta Jalan Terang": cartoVoyager,
                            "Satelit Hybrid (Atap Rumah)": googleHybrid
                        }).addTo(map);

                        var customers = decodeB64("$cBase64");
                        var routers   = decodeB64("$rBase64");
                        var assets    = decodeB64("$aBase64");

                        var routerBounds = [];
                        var customerBounds = [];
                        var assetBounds = [];
                        var allBounds = [];
                        var validRouters = [];
                        var myGpsLayer = L.layerGroup().addTo(map);

                        function makePinIcon(label, bgColor, emoji, extraLabelClass) {
                            var lblClass = extraLabelClass ? ("pin-label " + extraLabelClass) : "pin-label";
                            var html = '<div class="pin-wrap"><div class="' + lblClass + '">' + label + '</div>' +
                                       '<div class="pin-dot" style="background:' + bgColor + ';"><span>' + emoji + '</span></div></div>';
                            return L.divIcon({ className: '', html: html, iconSize: [0, 0], iconAnchor: [0, 0], popupAnchor: [0, -34] });
                        }

                        // Evaluasi apakah suatu koordinat (Pelanggan / Lokasi Saya) masuk dalam radius jangkauan Server
                        function checkServerCoverage(lat, lng) {
                            if (validRouters.length === 0) return '';
                            var coveredBy = [];
                            var nearest = null;
                            var minDist = Infinity;

                            validRouters.forEach(function(r) {
                                var d = map.distance([lat, lng], [r.lat, r.lng]);
                                if (d < minDist) {
                                    minDist = d;
                                    nearest = r;
                                }
                                if (d <= r.cov) {
                                    coveredBy.push({ name: r.name, dist: Math.round(d), cov: r.cov });
                                }
                            });

                            if (coveredBy.length > 0) {
                                var info = coveredBy.map(function(item) {
                                    return '<b>' + item.name + '</b> (' + formatDistanceLabel(item.dist) + ' / Radius ' + formatDistanceLabel(item.cov) + ')';
                                }).join(', ');
                                return '<span style="color:#10B981;font-weight:bold;">✅ Dalam Radius Server:</span> ' + info;
                            } else if (nearest) {
                                return '<span style="color:#F59E0B;font-weight:bold;">⚠️ Di Luar Radius Server:</span> Terdekat <b>' +
                                       nearest.name + '</b> (' + formatDistanceLabel(minDist) + ' / Radius ' + formatDistanceLabel(nearest.cov) + ')';
                            }
                            return '';
                        }

                        routers.forEach(function(r) {
                            var lat = parseFloat(r.lat); var lng = parseFloat(r.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            var cov = parseInt(r.coverage) || 350;
                            var rName = r.name || 'Router';

                            validRouters.push({ name: rName, lat: lat, lng: lng, cov: cov });
                            routerBounds.push([lat, lng]);
                            allBounds.push([lat, lng]);

                            // Lingkaran radius samar melingkari router server sesuai data coverage web
                            L.circle([lat, lng], {
                                radius: cov,
                                color: '#00C0EF',
                                weight: 2,
                                fillColor: '#00C0EF',
                                fillOpacity: 0.14
                            }).addTo(map);

                            var icon = makePinIcon('📡 ' + rName + ' (' + formatDistanceLabel(cov) + ')', '#F59E0B', '📡', 'pin-label-router');
                            L.marker([lat, lng], { icon: icon, zIndexOffset: 1000 }).addTo(map).bindPopup(
                                '<b>📡 Server / Router: ' + rName + '</b><br>' +
                                'IP/API: ' + (r.ip_address || '-') + '<br>' +
                                'Radius Jangkauan: <b>' + formatDistanceLabel(cov) + '</b><br>' +
                                (r.description ? ('Deskripsi: ' + r.description + '<br>') : '') +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                            );
                        });

                        customers.forEach(function(c) {
                            var lat = parseFloat(c.lat); var lng = parseFloat(c.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            customerBounds.push([lat, lng]);
                            allBounds.push([lat, lng]);

                            var isOn = (c.plan_status === 'on');
                            var pinColor = isOn ? '#10B981' : '#EF4444';
                            var statusBadge = isOn
                                ? '<span style="color:#10B981;font-weight:bold;">AKTIF (ON)</span>'
                                : '<span style="color:#EF4444;font-weight:bold;">EXPIRED (OFF)</span>';
                            var displayName = c.fullname || c.username || 'User';
                            var coverageInfo = checkServerCoverage(lat, lng);

                            var icon = makePinIcon(displayName, pinColor, '👤', '');
                            L.marker([lat, lng], { icon: icon }).addTo(map).bindPopup(
                                '<b>👤 ' + displayName + '</b> (@' + (c.username || '-') + ')<br>' +
                                'Status: ' + statusBadge + '<br>' +
                                'Paket: <b>' + (c.plan_name || '-') + '</b><br>' +
                                (coverageInfo !== '' ? (coverageInfo + '<br>') : '') +
                                'Exp: ' + (c.expiration || '-') + '<br>' +
                                'Alamat: ' + (c.address || '-') + '<br>' +
                                'WA: ' + (c.phonenumber || '-') + '<br>' +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Direction / Rute</a>'
                            );
                        });

                        assets.forEach(function(a) {
                            var lat = parseFloat(a.lat); var lng = parseFloat(a.lng);
                            if (isNaN(lat) || isNaN(lng)) return;
                            assetBounds.push([lat, lng]);
                            allBounds.push([lat, lng]);
                            var coverageInfo = checkServerCoverage(lat, lng);
                            var icon = makePinIcon('📦 ' + (a.name || 'ODP'), '#8B5CF6', '📦', '');
                            L.marker([lat, lng], { icon: icon }).addTo(map).bindPopup(
                                '<b>📦 ' + (a.name || '-') + '</b><br>' +
                                'Kategori: ' + (a.category || '-') + '<br>' +
                                'Kondisi: ' + (a.condition || 'Good') + '<br>' +
                                (coverageInfo !== '' ? (coverageInfo + '<br>') : '') +
                                '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                            );
                        });

                        function zoomToPoints(boundsArray, singleZoom, maxMultiZoom) {
                            if (!boundsArray || boundsArray.length === 0) return;
                            if (boundsArray.length === 1) {
                                map.flyTo(boundsArray[0], singleZoom, { duration: 0.8 });
                            } else {
                                map.flyToBounds(boundsArray, { padding: [35, 35], maxZoom: maxMultiZoom, duration: 0.8 });
                            }
                        }

                        window.focusMapTarget = function(target) {
                            map.invalidateSize();
                            if (target === 'router') {
                                zoomToPoints(routerBounds, 17, 17);
                            } else if (target === 'customers') {
                                zoomToPoints(customerBounds, 17, 17);
                            } else if (target === 'assets') {
                                zoomToPoints(assetBounds, 17, 17);
                            } else if (target === 'all') {
                                zoomToPoints(allBounds, 17, 17);
                            }
                        };

                        window.focusMyGpsLocation = function(lat, lng, accMeters) {
                            map.invalidateSize();
                            myGpsLayer.clearLayers();

                            var radiusAcc = Math.max(8, Math.min(120, Math.round(parseFloat(accMeters) || 15)));
                            L.circle([lat, lng], {
                                radius: radiusAcc,
                                color: '#3B82F6',
                                weight: 1.5,
                                fillColor: '#3B82F6',
                                fillOpacity: 0.18
                            }).addTo(myGpsLayer);

                            var coverageInfo = checkServerCoverage(lat, lng);
                            var myIcon = makePinIcon('📍 Lokasi Saya', '#3B82F6', '📍', 'pin-label-me');
                            var myMarker = L.marker([lat, lng], { icon: myIcon, zIndexOffset: 2000 }).addTo(myGpsLayer);

                            myMarker.bindPopup(
                                '<b>📍 Lokasi Saya Saat Ini</b><br>' +
                                'Koordinat: <code>' + lat.toFixed(6) + ', ' + lng.toFixed(6) + '</code> (±' + Math.round(accMeters || 0) + ' m)<br>' +
                                (coverageInfo !== '' ? (coverageInfo + '<br>') : '') +
                                '<a class="btn-dir" href="https://www.google.com/maps/search/?api=1&query=' + lat + ',' + lng + '">🗺️ Buka di Google Maps</a>'
                            );

                            map.flyTo([lat, lng], 18, { duration: 0.85 });
                            setTimeout(function() { myMarker.openPopup(); }, 450);
                        };

                        // Saat pertama kali buka menu peta: tampilkan server router dengan zoom paling dekat
                        setTimeout(function() {
                            map.invalidateSize();
                            if (routerBounds.length === 1) {
                                map.setView(routerBounds[0], 17);
                            } else if (routerBounds.length > 1) {
                                map.fitBounds(routerBounds, { padding: [35, 35], maxZoom: 17 });
                            } else if (customerBounds.length === 1) {
                                map.setView(customerBounds[0], 17);
                            } else if (allBounds.length > 0) {
                                map.fitBounds(allBounds, { padding: [35, 35], maxZoom: 17 });
                            }
                        }, 300);
                    </script>
                </body>
                </html>
            """.trimIndent()
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('router');", null) },
                label = { Text("🟡 Fokus Router (${routersGeo.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('customers');", null) },
                label = {
                    val activeCnt = customersGeo.count { it.optString("plan_status") == "on" }
                    val expCnt = customersGeo.count { it.optString("plan_status") != "on" }
                    Text("🟢 Aktif ($activeCnt) • 🔴 Exp ($expCnt)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('assets');", null) },
                label = { Text("📦 Aset/ODP (${assetsGeo.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            )
            AssistChip(
                onClick = { mapWebViewRef?.evaluateJavascript("window.focusMapTarget('all');", null) },
                label = { Text("🗺️ Lihat Semua", fontSize = 11.5.sp) }
            )
            AssistChip(
                onClick = { requestMyGpsLocation() },
                label = {
                    Text(
                        text = if (isLocatingGps) "⏳ Mencari GPS..." else "📍 Lokasi Saya",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = ArnetaGoldPrimary)
            }
        } else {
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
                            setGeolocationEnabled(true)
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 15; ArnetaAdminApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: android.webkit.GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, true, false)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.contains("google.com/maps") || url.startsWith("geo:")) {
                                    try {
                                        ctx.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(url)
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Aplikasi Google Maps tidak ditemukan.", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        tag = ""
                        mapWebViewRef = this
                    }
                },
                update = { webView ->
                    mapWebViewRef = webView
                    if (mapHtml.isNotEmpty() && webView.tag != mapHtml) {
                        webView.tag = mapHtml
                        webView.loadDataWithBaseURL(
                            ApiClient.baseUrl,
                            mapHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
