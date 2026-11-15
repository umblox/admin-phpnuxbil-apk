package id.biz.arneta.admin.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Kalkulasi jarak (meter) dan evaluasi apakah suatu koordinat berada di dalam radius jangkauan Server/Router terdekat
 */
private data class NearestServerEval(
    val routerName: String,
    val routerLat: Double,
    val routerLng: Double,
    val distanceMeters: Int,
    val coverageMeters: Int,
    val isInsideCoverage: Boolean
)

private fun evaluateNearestServer(
    lat: Double,
    lng: Double,
    routers: List<JSONObject>
): NearestServerEval? {
    if (routers.isEmpty()) return null
    var bestRouter: JSONObject? = null
    var minDistance = Float.MAX_VALUE

    for (r in routers) {
        val rLat = r.optDouble("lat", Double.NaN)
        val rLng = r.optDouble("lng", Double.NaN)
        if (rLat.isNaN() || rLng.isNaN()) continue
        val res = FloatArray(1)
        Location.distanceBetween(lat, lng, rLat, rLng, res)
        if (res[0] < minDistance) {
            minDistance = res[0]
            bestRouter = r
        }
    }

    val chosen = bestRouter ?: return null
    val cov = chosen.optInt("coverage", 350).takeIf { it > 0 } ?: 350
    val distInt = minDistance.roundToInt()
    return NearestServerEval(
        routerName = chosen.optString("name", "Server"),
        routerLat = chosen.optDouble("lat"),
        routerLng = chosen.optDouble("lng"),
        distanceMeters = distInt,
        coverageMeters = cov,
        isInsideCoverage = distInt <= cov
    )
}

/**
 * LAYAR PETA TERPADU: SERVER COVERAGE RADIUS, PELANGGAN, ODP & DETEKSI GPS LOKASI SAYA
 */
@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
fun AdminMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var customersGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var routersGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var assetsGeo by remember { mutableStateOf(emptyList<JSONObject>()) }
    var mapHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var mapWebViewRef by remember { mutableStateOf<WebView?>(null) }

    // State Statistik Radius Pelanggan
    var inCoverageCount by remember { mutableIntStateOf(0) }
    var outCoverageCount by remember { mutableIntStateOf(0) }
    var showCoverageLines by remember { mutableStateOf(true) }
    var activeFilterMode by remember { mutableStateOf("all") } // all | in_radius | out_radius | assets

    // State GPS Lokasi Saya
    var isLocatingGps by remember { mutableStateOf(false) }
    var myGpsLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var myGpsAccuracy by remember { mutableFloatStateOf(0f) }
    var myCoverageEval by remember { mutableStateOf<NearestServerEval?>(null) }

    fun loadMapData() {
        isLoading = true
        scope.launch {
            val res = ApiClient.request("admin_map_data")
            if (res.status && res.data != null) {
                val rArr = res.data.optJSONArray("routers") ?: JSONArray()
                val rList = mutableListOf<JSONObject>()
                for (i in 0 until rArr.length()) rList.add(rArr.getJSONObject(i))
                routersGeo = rList

                val cArr = res.data.optJSONArray("customers") ?: JSONArray()
                val enrichedCustomersArr = JSONArray()
                val cList = mutableListOf<JSONObject>()
                var inCnt = 0
                var outCnt = 0

                for (i in 0 until cArr.length()) {
                    val cObj = JSONObject(cArr.getJSONObject(i).toString())
                    val cLat = cObj.optDouble("lat", Double.NaN)
                    val cLng = cObj.optDouble("lng", Double.NaN)
                    if (!cLat.isNaN() && !cLng.isNaN()) {
                        val eval = evaluateNearestServer(cLat, cLng, rList)
                        if (eval != null) {
                            cObj.put("nearest_router", eval.routerName)
                            cObj.put("nearest_router_lat", eval.routerLat)
                            cObj.put("nearest_router_lng", eval.routerLng)
                            cObj.put("distance_m", eval.distanceMeters)
                            cObj.put("router_coverage_m", eval.coverageMeters)
                            cObj.put("in_coverage", eval.isInsideCoverage)
                            if (eval.isInsideCoverage) inCnt++ else outCnt++
                        }
                    }
                    cList.add(cObj)
                    enrichedCustomersArr.put(cObj)
                }
                customersGeo = cList
                inCoverageCount = inCnt
                outCoverageCount = outCnt

                val aArr = res.data.optJSONArray("assets") ?: JSONArray()
                val aList = mutableListOf<JSONObject>()
                for (i in 0 until aArr.length()) aList.add(aArr.getJSONObject(i))
                assetsGeo = aList

                // Re-evaluasi Lokasi Saya jika sudah ada titik GPS sebelumnya
                myGpsLocation?.let { (lat, lng) ->
                    myCoverageEval = evaluateNearestServer(lat, lng, rList)
                }

                val cBase64 = android.util.Base64.encodeToString(
                    enrichedCustomersArr.toString().toByteArray(Charsets.UTF_8),
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
                                font-size: 10.5px; font-weight: bold; white-space: nowrap; box-shadow: 0 2px 5px rgba(0,0,0,0.45);
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
                            .leaflet-popup-content-wrapper { border-radius: 12px; font-size: 12px; line-height: 1.45; }
                            .btn-dir {
                                display: inline-block; margin-top: 6px; padding: 5px 10px; background: #00C0EF;
                                color: #FFFFFF !important; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 11px;
                            }
                            .badge-in { color: #10B981; font-weight: bold; }
                            .badge-out { color: #F59E0B; font-weight: bold; }
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

                            var osmStreet = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20, attribution: '© OpenStreetMap' });
                            var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 20, attribution: '© Carto' });
                            var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20, attribution: '© Google Hybrid' });

                            var map = L.map('map', { center: [$initLat, $initLng], zoom: 15, layers: [osmStreet], zoomControl: true });
                            L.control.layers({
                                "OpenStreetMap (Default Web)": osmStreet,
                                "Peta Jalan Terang": cartoVoyager,
                                "Satelit Hybrid (Atap Rumah)": googleHybrid
                            }).addTo(map);

                            var customers = decodeB64("$cBase64");
                            var routers   = decodeB64("$rBase64");
                            var assets    = decodeB64("$aBase64");

                            var routerLayerGroup   = L.layerGroup().addTo(map);
                            var customerInGroup    = L.layerGroup().addTo(map);
                            var customerOutGroup   = L.layerGroup().addTo(map);
                            var linesLayerGroup    = L.layerGroup().addTo(map);
                            var assetLayerGroup    = L.layerGroup().addTo(map);
                            var myGpsLayerGroup    = L.layerGroup().addTo(map);

                            var allBounds = [];
                            var inRadiusBounds = [];
                            var outRadiusBounds = [];
                            var assetBounds = [];

                            function makePinIcon(label, bgColor, emoji, specialClass) {
                                var lblClass = specialClass ? ("pin-label " + specialClass) : "pin-label";
                                var html = '<div class="pin-wrap"><div class="' + lblClass + '">' + label + '</div>' +
                                           '<div class="pin-dot" style="background:' + bgColor + ';"><span>' + emoji + '</span></div></div>';
                                return L.divIcon({ className: '', html: html, iconSize: [0, 0], iconAnchor: [0, 0], popupAnchor: [0, -34] });
                            }

                            routers.forEach(function(r) {
                                var lat = parseFloat(r.lat); var lng = parseFloat(r.lng);
                                if (isNaN(lat) || isNaN(lng)) return;
                                var cov = parseInt(r.coverage) || 350;
                                allBounds.push([lat, lng]);
                                inRadiusBounds.push([lat, lng]);
                                outRadiusBounds.push([lat, lng]);

                                var circle = L.circle([lat, lng], {
                                    radius: cov,
                                    color: '#00C0EF',
                                    weight: 2,
                                    dashArray: '6, 4',
                                    fillColor: '#00C0EF',
                                    fillOpacity: 0.13
                                }).addTo(routerLayerGroup);

                                var icon = makePinIcon('📡 ' + (r.name || 'Server') + ' (' + cov + 'm)', '#F59E0B', '📡', 'pin-label-router');
                                L.marker([lat, lng], { icon: icon, zIndexOffset: 1000 }).addTo(routerLayerGroup).bindPopup(
                                    '<b>📡 Server / Router: ' + (r.name || '-') + '</b><br>' +
                                    'IP/API: ' + (r.ip_address || '-') + '<br>' +
                                    'Radius Jangkauan: <b>' + cov + ' meter</b><br>' +
                                    (r.description ? ('Lokasi: ' + r.description + '<br>') : '') +
                                    '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                                );
                            });

                            customers.forEach(function(c) {
                                var lat = parseFloat(c.lat); var lng = parseFloat(c.lng);
                                if (isNaN(lat) || isNaN(lng)) return;
                                allBounds.push([lat, lng]);

                                var isOn = (c.plan_status === 'on');
                                var inCov = (c.in_coverage !== false);
                                var distM = c.distance_m || 0;
                                var covM  = c.router_coverage_m || 350;
                                var nearName = c.nearest_router || '-';

                                if (inCov) inRadiusBounds.push([lat, lng]);
                                else outRadiusBounds.push([lat, lng]);

                                var pinColor = isOn ? (inCov ? '#10B981' : '#F59E0B') : '#EF4444';
                                var statusBadge = isOn
                                    ? '<span style="color:#10B981;font-weight:bold;">AKTIF (ON)</span>'
                                    : '<span style="color:#EF4444;font-weight:bold;">EXPIRED (OFF)</span>';

                                var covBadge = '';
                                if (c.nearest_router) {
                                    covBadge = inCov
                                        ? '<br>Radius: <span class="badge-in">✅ Dalam Jangkauan ' + nearName + ' (' + distM + 'm / ' + covM + 'm)</span>'
                                        : '<br>Radius: <span class="badge-out">⚠️ Di Luar Jangkauan ' + nearName + ' (' + distM + 'm / ' + covM + 'm)</span>';

                                    var rLat = parseFloat(c.nearest_router_lat);
                                    var rLng = parseFloat(c.nearest_router_lng);
                                    if (!isNaN(rLat) && !isNaN(rLng)) {
                                        L.polyline([[rLat, rLng], [lat, lng]], {
                                            color: inCov ? '#10B981' : '#F59E0B',
                                            weight: 1.6,
                                            dashArray: '4, 6',
                                            opacity: 0.72
                                        }).addTo(linesLayerGroup);
                                    }
                                }

                                var displayName = c.fullname || c.username || 'User';
                                var distTag = c.nearest_router ? (' • ' + distM + 'm') : '';
                                var icon = makePinIcon(displayName + distTag, pinColor, isOn ? '🟢' : '🔴', '');
                                var targetGroup = inCov ? customerInGroup : customerOutGroup;

                                L.marker([lat, lng], { icon: icon }).addTo(targetGroup).bindPopup(
                                    '<b>👤 ' + displayName + '</b> (@' + (c.username || '-') + ')<br>' +
                                    'Status Paket: ' + statusBadge + '<br>' +
                                    'Paket: <b>' + (c.plan_name || '-') + '</b> (' + (c.service_type || '-') + ')' +
                                    covBadge + '<br>' +
                                    'Exp: ' + (c.expiration || '-') + '<br>' +
                                    'Alamat: ' + (c.address || '-') + '<br>' +
                                    'WA: ' + (c.phonenumber || '-') + '<br>' +
                                    '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Rute ke Pelanggan</a>'
                                );
                            });

                            assets.forEach(function(a) {
                                var lat = parseFloat(a.lat); var lng = parseFloat(a.lng);
                                if (isNaN(lat) || isNaN(lng)) return;
                                assetBounds.push([lat, lng]);
                                allBounds.push([lat, lng]);
                                var icon = makePinIcon('📦 ' + (a.name || 'ODP'), '#8B5CF6', '📦', '');
                                L.marker([lat, lng], { icon: icon }).addTo(assetLayerGroup).bindPopup(
                                    '<b>📦 ' + (a.name || '-') + '</b><br>' +
                                    'Kategori: ' + (a.category || '-') + '<br>' +
                                    'Kondisi: ' + (a.condition || 'Good') + '<br>' +
                                    '<a class="btn-dir" href="https://www.google.com/maps/dir/?api=1&destination=' + lat + ',' + lng + '">🗺️ Petunjuk Arah</a>'
                                );
                            });

                            window.setCoverageLinesVisible = function(visible) {
                                if (visible) {
                                    if (!map.hasLayer(linesLayerGroup)) map.addLayer(linesLayerGroup);
                                } else {
                                    if (map.hasLayer(linesLayerGroup)) map.removeLayer(linesLayerGroup);
                                }
                            };

                            window.applyUnifiedFilter = function(mode) {
                                map.invalidateSize();
                                if (!map.hasLayer(routerLayerGroup)) map.addLayer(routerLayerGroup);

                                if (mode === 'all') {
                                    if (!map.hasLayer(customerInGroup)) map.addLayer(customerInGroup);
                                    if (!map.hasLayer(customerOutGroup)) map.addLayer(customerOutGroup);
                                    if (!map.hasLayer(assetLayerGroup)) map.addLayer(assetLayerGroup);
                                    if (allBounds.length > 0) {
                                        map.flyToBounds(allBounds, { padding: [45, 45], maxZoom: 16, duration: 0.8 });
                                    }
                                } else if (mode === 'in_radius') {
                                    if (!map.hasLayer(customerInGroup)) map.addLayer(customerInGroup);
                                    if (map.hasLayer(customerOutGroup)) map.removeLayer(customerOutGroup);
                                    if (inRadiusBounds.length > 0) {
                                        map.flyToBounds(inRadiusBounds, { padding: [45, 45], maxZoom: 17, duration: 0.8 });
                                    }
                                } else if (mode === 'out_radius') {
                                    if (map.hasLayer(customerInGroup)) map.removeLayer(customerInGroup);
                                    if (!map.hasLayer(customerOutGroup)) map.addLayer(customerOutGroup);
                                    if (outRadiusBounds.length > 0) {
                                        map.flyToBounds(outRadiusBounds, { padding: [45, 45], maxZoom: 16, duration: 0.8 });
                                    }
                                } else if (mode === 'assets') {
                                    if (!map.hasLayer(assetLayerGroup)) map.addLayer(assetLayerGroup);
                                    if (assetBounds.length > 0) {
                                        map.flyToBounds(assetBounds, { padding: [45, 45], maxZoom: 17, duration: 0.8 });
                                    }
                                }
                            };

                            window.setMyGpsLocation = function(lat, lng, accMeters, serverName, distM, covM, isInside, sLat, sLng) {
                                map.invalidateSize();
                                myGpsLayerGroup.clearLayers();

                                if (accMeters && accMeters > 0) {
                                    L.circle([lat, lng], {
                                        radius: accMeters,
                                        color: '#3B82F6',
                                        weight: 1,
                                        fillColor: '#3B82F6',
                                        fillOpacity: 0.15
                                    }).addTo(myGpsLayerGroup);
                                }

                                var statusHtml = '';
                                var targetBounds = [[lat, lng]];

                                if (serverName && serverName !== '') {
                                    statusHtml = isInside
                                        ? '<br><span class="badge-in">✅ DALAM JANGKAUAN ' + serverName + ' (' + distM + 'm / ' + covM + 'm)</span>'
                                        : '<br><span class="badge-out">⚠️ DI LUAR JANGKAUAN ' + serverName + ' (' + distM + 'm / ' + covM + 'm)</span>';

                                    if (!isNaN(sLat) && !isNaN(sLng)) {
                                        targetBounds.push([sLat, sLng]);
                                        L.polyline([[lat, lng], [sLat, sLng]], {
                                            color: isInside ? '#10B981' : '#EF4444',
                                            weight: 2.8,
                                            dashArray: '6, 6'
                                        }).addTo(myGpsLayerGroup);
                                    }
                                }

                                var myIcon = makePinIcon('📍 Lokasi Saya' + (serverName ? (' • ' + distM + 'm') : ''), '#3B82F6', '📍', 'pin-label-me');
                                var marker = L.marker([lat, lng], { icon: myIcon, zIndexOffset: 2000 }).addTo(myGpsLayerGroup);
                                marker.bindPopup(
                                    '<b>📍 Posisi GPS Saya Saat Ini</b><br>' +
                                    'Koordinat: <code>' + lat.toFixed(6) + ', ' + lng.toFixed(6) + '</code>' +
                                    statusHtml
                                ).openPopup();

                                if (targetBounds.length > 1) {
                                    map.flyToBounds(targetBounds, { padding: [65, 65], maxZoom: 17, duration: 0.9 });
                                } else {
                                    map.flyTo([lat, lng], 17, { duration: 0.9 });
                                }
                            };

                            setTimeout(function() {
                                map.invalidateSize();
                                if (allBounds.length > 0) {
                                    map.fitBounds(allBounds, { padding: [45, 45], maxZoom: 16 });
                                }
                            }, 320);
                        </script>
                    </body>
                    </html>
                """.trimIndent()
            }
            isLoading = false
        }
    }

    fun pushLocationToMap(loc: Location) {
        isLocatingGps = false
        val lat = loc.latitude
        val lng = loc.longitude
        myGpsLocation = lat to lng
        myGpsAccuracy = loc.accuracy

        val eval = evaluateNearestServer(lat, lng, routersGeo)
        myCoverageEval = eval

        val sName = eval?.routerName?.replace("'", "\\'") ?: ""
        val distM = eval?.distanceMeters ?: 0
        val covM = eval?.coverageMeters ?: 0
        val inside = eval?.isInsideCoverage ?: false
        val sLat = eval?.routerLat ?: Double.NaN
        val sLng = eval?.routerLng ?: Double.NaN

        mapWebViewRef?.evaluateJavascript(
            "if(window.setMyGpsLocation) window.setMyGpsLocation($lat, $lng, ${loc.accuracy}, '$sName', $distM, $covM, $inside, $sLat, $sLng);",
            null
        )
    }

    fun startNativeGpsDetection() {
        val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locMgr == null) {
            Toast.makeText(context, "Layanan lokasi tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show()
            return
        }

        val gpsEnabled = runCatching { locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val netEnabled = runCatching { locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

        if (!gpsEnabled && !netEnabled) {
            Toast.makeText(context, "Aktifkan GPS / Lokasi HP Anda terlebih dahulu.", Toast.LENGTH_LONG).show()
            runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }

        isLocatingGps = true

        // 1. Tampilkan lokasi terakhir yang tersimpan sambil menunggu titik GPS satelit terbaru
        val lastGps = runCatching { locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val lastNet = runCatching { locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val bestLast = when {
            lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            else -> lastNet
        }
        if (bestLast != null) {
            pushLocationToMap(bestLast)
        }

        // 2. Minta pembaruan koordinat GPS presisi tinggi secara real-time
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                pushLocationToMap(location)
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
            startNativeGpsDetection()
        } else {
            Toast.makeText(context, "Izin lokasi diperlukan untuk mendeteksi posisi Anda di peta.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestMyLocationOnMap() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            startNativeGpsDetection()
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
        loadMapData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp)
    ) {
        // BARIS KONTROL TERPADU (SERVER + PELANGGAN + RADIUS + LOKASI SAYA)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            FilterChip(
                selected = isLocatingGps || myGpsLocation != null,
                onClick = { requestMyLocationOnMap() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = if (myGpsLocation != null) ArnetaCyanIncome else ArnetaGoldPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                },
                label = {
                    Text(
                        text = if (isLocatingGps) "Mencari GPS..." else "Lokasi Saya & Cek Radius",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            )

            FilterChip(
                selected = activeFilterMode == "all",
                onClick = {
                    activeFilterMode = "all"
                    mapWebViewRef?.evaluateJavascript("window.applyUnifiedFilter('all');", null)
                },
                label = {
                    Text(
                        text = "🗺️ Semua (${routersGeo.size} Server • ${customersGeo.size} User)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            FilterChip(
                selected = activeFilterMode == "in_radius",
                onClick = {
                    activeFilterMode = "in_radius"
                    mapWebViewRef?.evaluateJavascript("window.applyUnifiedFilter('in_radius');", null)
                },
                label = {
                    Text(
                        text = "✅ Dalam Radius ($inCoverageCount)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            if (outCoverageCount > 0) {
                FilterChip(
                    selected = activeFilterMode == "out_radius",
                    onClick = {
                        activeFilterMode = "out_radius"
                        mapWebViewRef?.evaluateJavascript("window.applyUnifiedFilter('out_radius');", null)
                    },
                    label = {
                        Text(
                            text = "⚠️ Luar Radius ($outCoverageCount)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            if (assetsGeo.isNotEmpty()) {
                FilterChip(
                    selected = activeFilterMode == "assets",
                    onClick = {
                        activeFilterMode = "assets"
                        mapWebViewRef?.evaluateJavascript("window.applyUnifiedFilter('assets');", null)
                    },
                    label = {
                        Text(
                            text = "📦 ODP/Aset (${assetsGeo.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            FilterChip(
                selected = showCoverageLines,
                onClick = {
                    showCoverageLines = !showCoverageLines
                    mapWebViewRef?.evaluateJavascript("window.setCoverageLinesVisible($showCoverageLines);", null)
                },
                label = { Text("Garis Radius", fontSize = 11.sp) }
            )

            IconButton(
                onClick = { loadMapData() },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang Peta", tint = ArnetaGoldPrimary, modifier = Modifier.size(18.dp))
            }
        }

        // PANEL HASIL DETEKSI LOKASI SAYA & EVALUASI RADIUS JANGKAUAN SERVER
        AnimatedVisibility(visible = myGpsLocation != null) {
            val (lat, lng) = myGpsLocation ?: (0.0 to 0.0)
            val eval = myCoverageEval
            val isInside = eval?.isInsideCoverage == true
            val coordString = String.format(Locale.US, "%.6f, %.6f", lat, lng)

            Surface(
                color = Color(0xFF141B2D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = if (eval == null) ArnetaCyanIncome else if (isInside) ArnetaGreenSuccess else ArnetaOrangeActive,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isInside) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (eval == null) ArnetaCyanIncome else if (isInside) ArnetaGreenSuccess else ArnetaOrangeActive,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = when {
                                    eval == null -> "Lokasi Saya Terdeteksi (Belum ada koordinat Server)"
                                    isInside -> "DALAM RADIUS JANGKAUAN SERVER (${eval.routerName})"
                                    else -> "DI LUAR RADIUS JANGKAUAN SERVER (${eval.routerName})"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (eval == null) ArnetaCyanIncome else if (isInside) ArnetaGreenSuccess else ArnetaOrangeActive
                            )
                        }
                        if (eval != null) {
                            val diffMeters = eval.distanceMeters - eval.coverageMeters
                            Text(
                                text = "Jarak ke ${eval.routerName}: ${eval.distanceMeters}m • Radius Server: ${eval.coverageMeters}m" +
                                    if (!isInside) " (+${diffMeters}m di luar batas)" else " (Tercover)",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Text(
                            text = "GPS: $coordString" + if (myGpsAccuracy > 0f) " (±${myGpsAccuracy.roundToInt()}m)" else "",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ArnetaCyanIncome
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("Koordinat GPS", coordString))
                                Toast.makeText(context, "Koordinat $coordString disalin", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Koordinat", tint = ArnetaGoldPrimary, modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = { myGpsLocation = null }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
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
                            userAgentString = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
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
