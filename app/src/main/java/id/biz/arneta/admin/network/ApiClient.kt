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
    private const val DEFAULT_BASE_URL = "https://phpnuxbill.arneta.biz.id"
    private lateinit var prefs: SharedPreferences

    private data class CacheEntry(
        val response: ApiResponse,
        val timestamp: Long
    )

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    // Endpoint yang boleh di-cache singkat (3.5 detik) agar perpindahan tab & buka dialog instan
    private val cacheableActions = setOf(
        "admin_plans",
        "admin_customers",
        "admin_routers",
        "admin_pools",
        "admin_bandwidths"
    )

    // Endpoint mutasi yang otomatis membersihkan cache agar data selalu segar setelah transaksi
    private val mutationActions = setOf(
        "admin_customer_save",
        "admin_customer_delete",
        "admin_prepaid_recharge",
        "admin_prepaid_delete",
        "admin_deposit",
        "admin_voucher_generate",
        "admin_voucher_refill",
        "admin_voucher_delete",
        "admin_plan_save",
        "admin_plan_delete",
        "admin_bandwidth_save",
        "admin_bandwidth_delete",
        "admin_router_save",
        "admin_router_delete",
        "admin_pool_save",
        "admin_pool_delete",
        "admin_coupon_save",
        "admin_coupon_delete",
        "admin_asset_save",
        "admin_asset_delete",
        "admin_settings_save",
        "admin_staff_save",
        "admin_staff_delete",
        "admin_page_save",
        "admin_pg_action"
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = (if (::prefs.isInitialized) prefs.getString("base_url", DEFAULT_BASE_URL) else DEFAULT_BASE_URL)
            ?.trimEnd('/') ?: DEFAULT_BASE_URL
        set(value) {
            val clean = value.trim().trimEnd('/')
            if (::prefs.isInitialized) {
                prefs.edit().putString("base_url", clean.ifEmpty { DEFAULT_BASE_URL }).apply()
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
        get() = (if (::prefs.isInitialized) prefs.getString("company_name", "Arneta.ID") else "Arneta.ID") ?: "Arneta.ID"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("company_name", value).apply()
        }

    var currencyCode: String
        get() = (if (::prefs.isInitialized) prefs.getString("currency_code", "Rp.") else "Rp.") ?: "Rp."
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString("currency_code", value).apply()
        }

    fun isLoggedIn(): Boolean = token.isNotBlank()

    fun clearCache() {
        memoryCache.clear()
    }

    fun logout() {
        token = ""
        clearCache()
    }

    suspend fun request(
        action: String,
        params: Map<String, Any?> = emptyMap()
    ): ApiResponse = withContext(Dispatchers.IO) {
        if (mutationActions.contains(action)) {
            clearCache()
        }

        val cacheKey = if (cacheableActions.contains(action)) {
            "$baseUrl|$action|${params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }}"
        } else null

        if (cacheKey != null) {
            val cached = memoryCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < 3500L) {
                return@withContext cached.response
            }
        }

        val encodedAction = URLEncoder.encode(action, "UTF-8")
        val primaryEndpoint = "$baseUrl/system/api.php?r=apiv1/$encodedAction"
        val fallbackEndpoint = "$baseUrl/?_route=apiv1/$encodedAction"

        var res = executeHttpPost(primaryEndpoint, action, params)
        if (!res.status && res.message.startsWith("HTTP Error")) {
            val fallbackRes = executeHttpPost(fallbackEndpoint, action, params)
            if (fallbackRes.status || !fallbackRes.message.startsWith("HTTP Error")) {
                res = fallbackRes
            }
        }

        if (cacheKey != null && res.status) {
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
                readTimeout = 18000
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

fun formatRupiah(amount: Double, currencyPrefix: String = ApiClient.currencyCode): String {
    val cleanCur = currencyPrefix.trim()
    if (cleanCur.equals("USD", ignoreCase = true) || cleanCur == "$") {
        val usFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "USD ${usFormat.format(amount)}"
    }
    val idFormat = NumberFormat.getNumberInstance(Locale("in", "ID")).apply {
        maximumFractionDigits = 0
    }
    val prefix = if (cleanCur.isEmpty()) "Rp." else cleanCur
    return "$prefix ${idFormat.format(amount)}"
}
