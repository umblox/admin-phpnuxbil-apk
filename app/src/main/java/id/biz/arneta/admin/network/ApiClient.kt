package id.biz.arneta.admin.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

data class ApiResponse(
    val status: Boolean,
    val message: String,
    val data: JSONObject? = null,
    val dataArray: JSONArray? = null
)

object ApiClient {
    private const val PREF_NAME = "arneta_admin_prefs"
    private lateinit var prefs: SharedPreferences

    private val defaultShortcuts = setOf(
        "add_customer",
        "recharge_plan",
        "deposit_balance",
        "generate_voucher",
        "online_users",
        "audit_qris",
        "live_monitor",
        "broadcast_wa",
        "webview_admin"
    )

    private data class CacheEntry(
        val response: ApiResponse,
        val timestamp: Long
    )

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    private val nonCacheableActions = setOf(
        "admin_login",
        "admin_user_live_traffic",
        "admin_resend_invoice_wa",
        "admin_test_notif",
        "admin_system_tools",
        "admin_db_backup",
        "admin_kick_user",
        "admin_router_reboot",
        "admin_customer_sync"
    )

    private val mutationActions = setOf(
        "admin_customer_save",
        "admin_customer_delete",
        "admin_prepaid_recharge",
        "admin_prepaid_edit",
        "admin_prepaid_extend",
        "admin_prepaid_delete",
        "admin_deposit",
        "admin_voucher_generate",
        "admin_voucher_refill",
        "admin_voucher_delete",
        "admin_plan_save",
        "admin_plan_delete",
        "admin_plan_sync",
        "admin_bandwidth_save",
        "admin_bandwidth_delete",
        "admin_router_save",
        "admin_router_delete",
        "admin_pool_save",
        "admin_pool_delete",
        "admin_coupon_save",
        "admin_coupon_status",
        "admin_coupon_delete",
        "admin_asset_save",
        "admin_asset_delete",
        "admin_settings_save",
        "admin_notifications_save",
        "admin_staff_save",
        "admin_staff_delete",
        "admin_change_password",
        "admin_page_save",
        "admin_pg_action",
        "pg_action",
        "admin_genieacs_host_save",
        "admin_genieacs_host_action",
        "admin_genieacs_device_action",
        "admin_genieacs_wan_save",
        "admin_genieacs_wlan_save"
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = (if (::prefs.isInitialized) prefs.getString("base_url", "") else "")
            ?.trimEnd('/') ?: ""
        set(value) {
            val clean = value.trim().trimEnd('/').removeSuffix("/system")
            if (::prefs.isInitialized) {
                prefs.edit().putString("base_url", clean).apply()
            }
            clearCache()
        }

    var token: String
        get() = (if (::prefs.isInitialized) prefs.getString("token", "") else "") ?: ""
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("token", value).apply()
        }

    var adminName: String
        get() = (if (::prefs.isInitialized) prefs.getString("admin_name", "Administrator") else "Administrator") ?: "Administrator"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("admin_name", value).apply()
        }

    var adminUsername: String
        get() = (if (::prefs.isInitialized) prefs.getString("admin_username", "admin") else "admin") ?: "admin"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("admin_username", value).apply()
        }

    var adminRole: String
        get() = (if (::prefs.isInitialized) prefs.getString("admin_role", "SuperAdmin") else "SuperAdmin") ?: "SuperAdmin"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("admin_role", value).apply()
        }

    var companyName: String
        get() = (if (::prefs.isInitialized) prefs.getString("company_name", "PHPNuxBill") else "PHPNuxBill")
            ?.takeIf { it.isNotBlank() } ?: "PHPNuxBill"
        set(value) {
            val cleanName = value.trim().ifEmpty { "PHPNuxBill" }
            if (::prefs.isInitialized) prefs.edit().putString("company_name", cleanName).apply()
        }

    var currencyCode: String
        get() = (if (::prefs.isInitialized) prefs.getString("currency_code", "Rp.") else "Rp.")
            ?.takeIf { it.isNotBlank() } ?: "Rp."
        set(value) {
            val cleanCur = value.trim().ifEmpty { "Rp." }
            if (::prefs.isInitialized) prefs.edit().putString("currency_code", cleanCur).apply()
        }

    var isDarkMode: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean("is_dark_mode", true) else true
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putBoolean("is_dark_mode", value).apply()
        }

    var customQuickShortcuts: Set<String>
        get() = if (::prefs.isInitialized) {
            prefs.getStringSet("custom_quick_shortcuts", defaultShortcuts) ?: defaultShortcuts
        } else {
            defaultShortcuts
        }
        set(value) {
            if (::prefs.isInitialized) {
                prefs.edit().putStringSet("custom_quick_shortcuts", value).apply()
            }
        }

    fun isLoggedIn(): Boolean = token.isNotBlank() && baseUrl.isNotBlank()

    fun clearCache() {
        memoryCache.clear()
    }

    fun logout() {
        token = ""
        clearCache()
    }

    fun buildWebViewSsoUrl(redirect: String = "dashboard"): String {
        val encodedRedirect = URLEncoder.encode(redirect, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return "$baseUrl/system/api.php?r=plugin/mobile_api/webview_sso&redirect=$encodedRedirect&token=$encodedToken"
    }

    private fun buildCacheKey(action: String, params: Map<String, Any?>): String {
        val sortedParams = params.entries
            .filter { it.value != null && it.value.toString().isNotEmpty() }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl|$action|$sortedParams"
    }

    private fun isCacheable(action: String): Boolean {
        return !mutationActions.contains(action) && !nonCacheableActions.contains(action)
    }

    fun getCached(action: String, params: Map<String, Any?> = emptyMap()): ApiResponse? {
        if (!isCacheable(action)) return null
        return memoryCache[buildCacheKey(action, params)]?.response
    }

    fun warmupCacheInBackground() {
        // Dikosongkan agar tidak memicu tabrakan state sinkron saat startup
    }

    suspend fun request(
        action: String,
        params: Map<String, Any?> = emptyMap(),
        forceRefresh: Boolean = false
    ): ApiResponse = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext ApiResponse(
                status = false,
                message = "Server PHPNuxBill belum diatur. Silakan atur URL Server terlebih dahulu."
            )
        }

        val isMutation = mutationActions.contains(action)
        val cacheKey = if (isCacheable(action)) buildCacheKey(action, params) else null

        if (!forceRefresh && cacheKey != null) {
            val cached = memoryCache[cacheKey]
            // Gunakan cache RAM jika masih segar (< 4 detik) agar perpindahan sub-tab instan
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < 4000L) {
                return@withContext cached.response
            }
        }

        val encodedAction = URLEncoder.encode(action, "UTF-8")
        val endpoint = "$baseUrl/system/api.php?r=plugin/mobile_api/$encodedAction"

        val res = executeHttpPost(endpoint, action, params)

        if (isMutation && res.status) {
            memoryCache.clear()
        } else if (cacheKey != null && res.status) {
            memoryCache[cacheKey] = CacheEntry(res, System.currentTimeMillis())
        }

        res
    }

    private fun executeHttpPost(
        endpointUrl: String,
        action: String,
        params: Map<String, Any?>
    ): ApiResponse {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 20000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Connection", "keep-alive")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val jsonBody = JSONObject()
            jsonBody.put("action", action)
            if (token.isNotBlank()) {
                jsonBody.put("token", token)
            }
            for ((k, v) in params) {
                when (v) {
                    null -> jsonBody.put(k, JSONObject.NULL)
                    is Double -> jsonBody.put(k, if (v.isNaN() || v.isInfinite()) 0.0 else v)
                    is Float -> jsonBody.put(k, if (v.isNaN() || v.isInfinite()) 0f else v)
                    is Collection<*> -> jsonBody.put(k, JSONArray(v))
                    is Array<*> -> jsonBody.put(k, JSONArray(v.toList()))
                    else -> jsonBody.put(k, v)
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val rawStream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val contentEncoding = conn.contentEncoding ?: ""
            val decodedStream = if (contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }

            val responseText = BufferedReader(InputStreamReader(decodedStream, Charsets.UTF_8)).use {
                it.readText()
            }.trim()

            if (code == 401 && action != "admin_login") {
                logout()
            }

            if (responseText.isEmpty()) {
                return ApiResponse(false, "HTTP Error $code: Respon server kosong.")
            }

            val jsonStart = responseText.indexOf('{')
            val cleanJson = if (jsonStart > 0) responseText.substring(jsonStart) else responseText

            val root = JSONObject(cleanJson)
            val ok = root.optBoolean("status", root.optBoolean("success", false))
            val msg = root.optString("message", if (ok) "Berhasil" else "Permintaan gagal")

            val dataObj = root.optJSONObject("data") ?: root.optJSONObject("result")
            val dataArr = root.optJSONArray("data") ?: root.optJSONArray("result")

            if (dataObj != null) {
                val compObj = dataObj.optJSONObject("company")
                val dynCompName = compObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                    ?: dataObj.optString("company_name", "").takeIf { it.isNotBlank() }
                    ?: dataObj.optString("CompanyName", "").takeIf { it.isNotBlank() }
                if (!dynCompName.isNullOrBlank()) {
                    companyName = dynCompName
                }

                val dynCurr = compObj?.optString("currency", "")?.takeIf { it.isNotBlank() }
                    ?: dataObj.optString("currency_code", "").takeIf { it.isNotBlank() }
                if (!dynCurr.isNullOrBlank()) {
                    currencyCode = dynCurr
                }
            }

            ApiResponse(
                status = ok,
                message = msg,
                data = dataObj,
                dataArray = dataArr
            )
        } catch (e: Exception) {
            ApiResponse(false, "Gangguan jaringan: ${e.localizedMessage ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }
}

fun JSONObject?.optSafeDouble(key: String, default: Double = 0.0): Double {
    if (this == null || !this.has(key) || this.isNull(key)) return default
    val v = this.optDouble(key, default)
    return if (v.isNaN() || v.isInfinite()) default else v
}

fun formatRupiah(amount: Double, currencyPrefix: String = ApiClient.currencyCode): String {
    val safeAmount = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
    val cleanCur = currencyPrefix.trim()
    if (cleanCur.equals("USD", ignoreCase = true) || cleanCur == "$") {
        val usFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "USD ${usFormat.format(safeAmount)}"
    }
    val idFormat = NumberFormat.getNumberInstance(Locale("in", "ID")).apply {
        maximumFractionDigits = 0
    }
    val prefix = if (cleanCur.isEmpty()) "Rp." else cleanCur
    return "$prefix ${idFormat.format(safeAmount)}"
}
