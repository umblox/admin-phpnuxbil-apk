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

data class ApiResult(
    val status: Boolean,
    val message: String,
    val data: JSONObject? = null,
    val dataArray: JSONArray? = null,
    val rawData: Any? = null,
    val code: Int = 200
)

object ApiClient {
    private const val PREF_NAME = "arneta_admin_prefs_v9"
    private const val DEFAULT_BASE_URL = "https://phpnuxbill.arneta.biz.id"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = (prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL).trimEnd('/')
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("admin_token", "") ?: ""
        set(value) = prefs.edit().putString("admin_token", value).apply()

    var adminName: String
        get() = prefs.getString("admin_name", "Administrator") ?: "Administrator"
        set(value) = prefs.edit().putString("admin_name", value).apply()

    var currencyCode: String
        get() = prefs.getString("currency_code", "Rp.") ?: "Rp."
        set(value) = prefs.edit().putString("currency_code", value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var customQuickShortcuts: Set<String>
        get() = prefs.getStringSet("quick_shortcuts", setOf("add_customer", "recharge_plan", "deposit_balance", "generate_voucher", "online_users", "audit_qris")) ?: emptySet()
        set(value) = prefs.edit().putStringSet("quick_shortcuts", value).apply()

    fun logout() {
        prefs.edit().remove("admin_token").apply()
    }

    suspend fun callNativeApi(route: String, formParams: Map<String, Any?> = emptyMap()): ApiResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val cleanRoute = route.trimStart('/')
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val queryBuilder = StringBuilder("$baseUrl/system/api.php?r=$cleanRoute")
            if (token.isNotEmpty()) queryBuilder.append("&token=$encodedToken")

            val postPairs = mutableListOf<String>()
            for ((k, v) in formParams) {
                if (v == null) continue
                val ek = URLEncoder.encode(k, "UTF-8")
                val ev = URLEncoder.encode(v.toString(), "UTF-8")
                postPairs.add("$ek=$ev")
                if (ev.length < 100) queryBuilder.append("&$ek=$ev")
            }

            conn = (URL(queryBuilder.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(postPairs.joinToString("&")); it.flush() }
            val rawText = BufferedReader(InputStreamReader(if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream, Charsets.UTF_8)).use { it.readText() }.trim()

            val jsonStart = rawText.indexOf('{')
            val json = JSONObject(if (jsonStart >= 0) rawText.substring(jsonStart) else rawText)
            val isSuccess = json.optBoolean("success", json.optBoolean("status", false))
            val rawResult = if (json.has("result") && !json.isNull("result")) json.opt("result") else json.opt("data")

            ApiResult(status = isSuccess, message = json.optString("message", "Selesai"), data = rawResult as? JSONObject, dataArray = rawResult as? JSONArray, rawData = rawResult, code = conn.responseCode)
        } catch (e: Exception) {
            ApiResult(status = false, message = "Koneksi gagal: ${e.localizedMessage}")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun request(action: String, params: Map<String, Any?> = emptyMap()): ApiResult = withContext(Dispatchers.IO) {
        when (action) {
            "admin_login" -> callNativeApi("apiv1/auth", params)
            "admin_dashboard" -> callNativeApi("apiv1/admin_dashboard", params)
            "admin_customers" -> callNativeApi("apiv1/admin_customers", params)
            "admin_customer_save" -> callNativeApi("apiv1/admin_customer_save", params)
            "admin_customer_delete" -> callNativeApi("apiv1/admin_customer_delete", params)
            "admin_prepaid_list" -> callNativeApi("apiv1/admin_prepaid_list", params)
            "admin_prepaid_recharge" -> callNativeApi("apiv1/admin_prepaid_recharge", params)
            "admin_deposit" -> callNativeApi("apiv1/admin_deposit", params)
            "admin_vouchers" -> callNativeApi("apiv1/admin_vouchers", params)
            "admin_voucher_generate" -> callNativeApi("apiv1/admin_voucher_generate", params)
            "admin_voucher_delete" -> callNativeApi("apiv1/admin_voucher_delete", params)
            "admin_plans" -> callNativeApi("apiv1/admin_plans", params)
            "admin_reports" -> callNativeApi("apiv1/admin_reports", params)
            "admin_send_message" -> callNativeApi("apiv1/admin_send_message", params)
            "admin_pages" -> callNativeApi("apiv1/admin_pages", params)
            "admin_page_save" -> callNativeApi("apiv1/admin_page_save", params)
            "admin_system_tools" -> callNativeApi("apiv1/admin_system_tools", params)
            "admin_map_data" -> callNativeApi("apiv1/admin_map_data", params)
            "admin_router_status" -> callNativeApi("apiv1/admin_router_status", params)
            "admin_online_users" -> callNativeApi("apiv1/admin_online_users", params)
            "admin_coupons" -> callNativeApi("apiv1/admin_coupons", params)
            "admin_coupon_save" -> callNativeApi("apiv1/admin_coupon_save", params)
            "admin_coupon_delete" -> callNativeApi("apiv1/admin_coupon_delete", params)
            "admin_assets" -> callNativeApi("apiv1/admin_assets", params)
            "admin_asset_save" -> callNativeApi("apiv1/admin_asset_save", params)
            "admin_asset_delete" -> callNativeApi("apiv1/admin_asset_delete", params)
            else -> callNativeApi("apiv1/$action", params)
        }
    }
}

fun formatRupiah(amount: Double, prefix: String = ApiClient.currencyCode): String {
    val formatted = String.format("%,.0f", amount).replace(',', '.')
    return "$prefix $formatted"
}
