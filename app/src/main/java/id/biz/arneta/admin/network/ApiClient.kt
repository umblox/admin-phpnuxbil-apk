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
    private const val PREF_NAME = "arneta_admin_prefs_v8"
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

    var adminUsername: String
        get() = prefs.getString("admin_username", "admin") ?: "admin"
        set(value) = prefs.edit().putString("admin_username", value).apply()

    var adminRole: String
        get() = prefs.getString("admin_role", "SuperAdmin") ?: "SuperAdmin"
        set(value) = prefs.edit().putString("admin_role", value).apply()

    var companyName: String
        get() = prefs.getString("company_name", "Arneta.ID") ?: "Arneta.ID"
        set(value) = prefs.edit().putString("company_name", value).apply()

    var currencyCode: String
        get() = prefs.getString("currency_code", "Rp.") ?: "Rp."
        set(value) = prefs.edit().putString("currency_code", value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var customQuickShortcuts: Set<String>
        get() = prefs.getStringSet(
            "quick_shortcuts",
            setOf("add_customer", "recharge_plan", "deposit_balance", "generate_voucher", "online_users", "audit_qris")
        ) ?: setOf("add_customer", "recharge_plan", "deposit_balance", "generate_voucher", "online_users", "audit_qris")
        set(value) = prefs.edit().putStringSet("quick_shortcuts", value).apply()

    fun logout() {
        prefs.edit().remove("admin_token").apply()
    }

    /**
     * Memanggil /system/api.php?r=<route>&token=<token> dengan POST Form-UrlEncoded
     * agar kompatibel 100% dengan _post() / _get() / _req() pada controller asli PHPNuxBill (.tpl)
     */
    suspend fun callNativeApi(
        route: String,
        formParams: Map<String, Any?> = emptyMap()
    ): ApiResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val cleanRoute = route.trimStart('/')
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val queryBuilder = StringBuilder("$baseUrl/system/api.php?r=$cleanRoute")
            if (token.isNotEmpty()) {
                queryBuilder.append("&token=$encodedToken")
            }

            // Sertakan parameter sederhana di query string sekaligus di POST body agar _get() & _post() sama-sama terbaca
            val postPairs = mutableListOf<String>()
            for ((k, v) in formParams) {
                if (v == null) continue
                when (v) {
                    is Map<*, *> -> {
                        for ((subK, subV) in v) {
                            if (subK != null && subV != null) {
                                val ek = URLEncoder.encode(subK.toString(), "UTF-8")
                                val ev = URLEncoder.encode(subV.toString(), "UTF-8")
                                postPairs.add("$ek=$ev")
                            }
                        }
                    }
                    else -> {
                        val ek = URLEncoder.encode(k, "UTF-8")
                        val ev = URLEncoder.encode(v.toString(), "UTF-8")
                        postPairs.add("$ek=$ev")
                        if (ev.length < 120 && !k.contains("password", ignoreCase = true) && !k.contains("content", ignoreCase = true)) {
                            queryBuilder.append("&$ek=$ev")
                        }
                    }
                }
            }

            val url = URL(queryBuilder.toString())
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (token.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val formBody = postPairs.joinToString("&")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(formBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val rawText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }.trim()

            // Tangani jika output mengandung notice/spasi sebelum kurung kurawal JSON
            val jsonStart = rawText.indexOf('{')
            val cleanJson = if (jsonStart >= 0) rawText.substring(jsonStart) else rawText

            val json = JSONObject(cleanJson)
            val isSuccess = if (json.has("success")) json.optBoolean("success", false) else json.optBoolean("status", false)
            val msg = json.optString("message", if (isSuccess) "Berhasil" else "Gagal memproses permintaan")
            val rawResult = if (json.has("result") && !json.isNull("result")) json.opt("result") else json.opt("data")

            ApiResult(
                status = isSuccess,
                message = msg,
                data = rawResult as? JSONObject,
                dataArray = rawResult as? JSONArray,
                rawData = rawResult,
                code = responseCode
            )
        } catch (e: Exception) {
            ApiResult(
                status = false,
                message = "Koneksi bermasalah: ${e.localizedMessage ?: "Timeout"}",
                code = 500
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Smart Router & Adapter:
     * Menghubungkan seluruh request aplikasi ke Controller Asli (/system/api.php?r=...)
     * dan menerjemahkan variabel Smarty (.tpl) ke format yang siap dirender UI Android
     */
    suspend fun request(
        action: String,
        params: Map<String, Any?> = emptyMap()
    ): ApiResult = withContext(Dispatchers.IO) {
        when (action) {
            // 1. LOGIN ADMIN -> /system/api.php?r=apiv1/auth
            "admin_login" -> callNativeApi("apiv1/auth", params + mapOf("role" to "admin"))

            // 2. DASBOR ADMIN -> Menggabungkan /system/api.php?r=dashboard (.tpl asli) + ?r=apiv1/dashboard_extras
            "admin_dashboard" -> {
                val dashRes = callNativeApi("dashboard")
                val extRes = callNativeApi("apiv1/dashboard_extras")

                val dObj = dashRes.data ?: JSONObject()
                val eObj = extRes.data ?: JSONObject()
                val cfgObj = dObj.optJSONObject("_c") ?: JSONObject()

                val iday = dObj.optString("iday", "0").toDoubleOrNull() ?: 0.0
                val imonth = dObj.optString("imonth", "0").toDoubleOrNull() ?: 0.0
                val uAct = dObj.optString("u_act", "0").toIntOrNull() ?: 0
                val uAll = dObj.optString("u_all", "0").toIntOrNull() ?: 0
                val uExp = (uAll - uAct).coerceAtLeast(0)
                val cAll = dObj.optString("c_all", "0").toIntOrNull() ?: 0
                val cb = dObj.optString("cb", "0").toDoubleOrNull() ?: 0.0

                // Konversi variabel stocks & plans dari dashboard.php asli
                val stocksOut = JSONArray()
                val rawPlans = dObj.optJSONArray("plans")
                val rawStocks = dObj.optJSONObject("stocks")
                if (rawPlans != null) {
                    for (i in 0 until rawPlans.length()) {
                        val p = rawPlans.getJSONObject(i)
                        stocksOut.put(
                            JSONObject().apply {
                                put("name_plan", p.optString("name_plan"))
                                put("active_users", p.optInt("active_users", 0))
                                put("unused", p.optInt("unused", 0))
                                put("used", p.optInt("used", 0))
                            }
                        )
                    }
                } else if (rawStocks != null) {
                    stocksOut.put(
                        JSONObject().apply {
                            put("name_plan", "Total Semua Voucher")
                            put("active_users", uAct)
                            put("unused", rawStocks.optInt("unused", 0))
                            put("used", rawStocks.optInt("used", 0))
                        }
                    )
                }

                val combined = JSONObject().apply {
                    put("company", JSONObject().apply {
                        put("name", cfgObj.optString("CompanyName", companyName))
                        put("currency", cfgObj.optString("currency_code", currencyCode))
                        put("date_Period", "${dObj.optString("start_date", "01")} / ${dObj.optString("current_date", "Hari Ini")}")
                    })
                    put("cards", JSONObject().apply {
                        put("income_today", iday)
                        put("income_month", imonth)
                        put("active_users", uAct)
                        put("expired_users", uExp)
                        put("active_expired_label", "$uAct/$uExp")
                        put("total_customers", cAll)
                        put("total_customer_balance", cb)
                        put("unpaid_qris_count", eObj.optInt("unpaid_qris_count", 0))
                    })
                    put("charts", JSONObject().apply {
                        put("monthly_registered", eObj.optJSONArray("monthly_registered") ?: JSONArray())
                        put("monthly_sales", eObj.optJSONArray("monthly_sales") ?: JSONArray())
                    })
                    put("stocks", stocksOut)
                    put("expiring_users", dObj.optJSONArray("exp") ?: JSONArray())
                }
                ApiResult(status = true, message = "Dashboard dimuat", data = combined)
            }

            // 3. MANAJEMEN PELANGGAN -> /system/api.php?r=customers/...
            "admin_customers" -> {
                val res = callNativeApi("customers/list", params)
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                val out = JSONObject().apply {
                    put("total", dArr.length())
                    put("customers", dArr)
                }
                ApiResult(status = res.status, message = res.message, data = out)
            }
            "admin_customer_save" -> {
                val id = (params["id"]?.toString()?.toIntOrNull() ?: 0)
                val route = if (id > 0) "customers/edit-post" else "customers/add-post"
                callNativeApi(route, params)
            }
            "admin_customer_delete" -> callNativeApi("customers/delete/${params["id"]}")
            "admin_customer_sync" -> callNativeApi("customers/sync/${params["id"]}")

            // 4. LAYANAN / PREPAID & VOUCHER -> /system/api.php?r=prepaid/...
            "admin_prepaid_list" -> {
                val res = callNativeApi("prepaid/list", params)
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                val out = JSONObject().apply {
                    put("total", dArr.length())
                    put("items", dArr)
                }
                ApiResult(status = res.status, message = res.message, data = out)
            }
            "admin_prepaid_recharge" -> {
                callNativeApi(
                    "prepaid/recharge-post",
                    mapOf(
                        "id_customer" to params["customer_id"],
                        "plan" to params["plan_id"],
                        "server" to (params["routers"] ?: "radius"),
                        "using" to (params["using"] ?: "Cash")
                    )
                )
            }
            "admin_prepaid_delete" -> callNativeApi("prepaid/delete/${params["id"]}")
            "admin_deposit" -> {
                callNativeApi(
                    "prepaid/deposit-post",
                    mapOf(
                        "id_customer" to params["customer_id"],
                        "id_plan" to params["plan_id"],
                        "amount" to params["amount"],
                        "note" to params["note"]
                    )
                )
            }
            "admin_vouchers" -> {
                val res = callNativeApi("prepaid/voucher", params)
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                val out = JSONObject().apply {
                    put("total", dArr.length())
                    put("vouchers", dArr)
                }
                ApiResult(status = res.status, message = res.message, data = out)
            }
            "admin_voucher_generate" -> {
                callNativeApi(
                    "prepaid/voucher-post",
                    mapOf(
                        "type" to (params["type"] ?: "Hotspot"),
                        "plan" to params["plan_id"],
                        "server" to (params["routers"] ?: "radius"),
                        "numbervoucher" to (params["count"] ?: 10),
                        "lengthcode" to (params["length_code"] ?: 6),
                        "prefix" to (params["prefix"] ?: ""),
                        "voucher_format" to (params["voucher_format"] ?: "up")
                    )
                )
            }
            "admin_voucher_refill" -> {
                callNativeApi(
                    "prepaid/refill-post",
                    mapOf("id_customer" to params["customer_id"], "code" to params["code"])
                )
            }
            "admin_voucher_delete" -> {
                if (params["delete_used"] == 1) {
                    callNativeApi("prepaid/remove-voucher")
                } else {
                    callNativeApi("prepaid/voucher-delete/${params["id"]}")
                }
            }

            // 5. PAKET INTERNET & BANDWIDTH -> /system/api.php?r=plans/... & ?r=bandwidth/...
            "admin_plans" -> {
                val hsRes = callNativeApi("plans/hotspot")
                val pppRes = callNativeApi("plans/pppoe")
                val balRes = callNativeApi("plans/balance")
                val bwRes = callNativeApi("bandwidth/list")
                val rRes = callNativeApi("routers/list")
                val poolRes = callNativeApi("pool/list")

                val allPlans = JSONArray()
                fun appendPlans(arr: JSONArray?) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) allPlans.put(arr.getJSONObject(i))
                }
                appendPlans(hsRes.data?.optJSONArray("d"))
                appendPlans(pppRes.data?.optJSONArray("d"))
                appendPlans(balRes.data?.optJSONArray("d"))

                val out = JSONObject().apply {
                    put("plans", allPlans)
                    put("bandwidths", bwRes.data?.optJSONArray("d") ?: JSONArray())
                    put("routers", rRes.data?.optJSONArray("d") ?: JSONArray())
                    put("pools", poolRes.data?.optJSONArray("d") ?: JSONArray())
                }
                ApiResult(status = true, message = "Daftar paket dimuat", data = out)
            }
            "admin_plan_save" -> {
                val id = (params["id"]?.toString()?.toIntOrNull() ?: 0)
                val type = (params["type"]?.toString() ?: "Hotspot").lowercase()
                val route = when {
                    type == "pppoe" && id > 0 -> "plans/edit-pppoe-post"
                    type == "pppoe" -> "plans/add-pppoe-post"
                    type == "balance" && id > 0 -> "plans/edit-balance-post"
                    type == "balance" -> "plans/add-balance-post"
                    id > 0 -> "plans/edit-hotspot-post"
                    else -> "plans/add-hotspot-post"
                }
                callNativeApi(
                    route,
                    params + mapOf(
                        "name_plan" to params["name_plan"],
                        "id_bw" to params["id_bw"],
                        "price" to params["price"],
                        "validity" to params["validity"],
                        "validity_unit" to params["validity_unit"],
                        "routers" to params["routers"],
                        "pool_name" to params["pool"]
                    )
                )
            }
            "admin_plan_delete" -> callNativeApi("plans/delete/${params["id"]}")
            "admin_bandwidth_save" -> callNativeApi("bandwidth/add-post", params)
            "admin_bandwidth_delete" -> callNativeApi("bandwidth/delete/${params["id"]}")

            // 6. ROUTER & IP POOL -> /system/api.php?r=routers/... & ?r=pool/...
            "admin_routers" -> {
                val res = callNativeApi("routers/list")
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                ApiResult(status = res.status, message = res.message, dataArray = dArr)
            }
            "admin_router_save" -> {
                val id = (params["id"]?.toString()?.toIntOrNull() ?: 0)
                callNativeApi(if (id > 0) "routers/edit-post" else "routers/add-post", params)
            }
            "admin_router_delete" -> callNativeApi("routers/delete/${params["id"]}")
            "admin_pools" -> {
                val res = callNativeApi("pool/list")
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                ApiResult(status = res.status, message = res.message, dataArray = dArr)
            }
            "admin_pool_save" -> {
                val id = (params["id"]?.toString()?.toIntOrNull() ?: 0)
                callNativeApi(if (id > 0) "pool/edit-post" else "pool/add-post", params + mapOf("name" to params["pool_name"]))
            }
            "admin_pool_delete" -> callNativeApi("pool/delete/${params["id"]}")

            // 7. LAPORAN, BROADCAST PESAN, PENGATURAN & LOGS -> Controller Asli system/api.php
            "admin_reports" -> {
                val res = callNativeApi(
                    "reports/period-view",
                    mapOf(
                        "fdate" to params["start_date"],
                        "tdate" to params["end_date"],
                        "stype" to (params["type"] ?: "")
                    )
                )
                val dArr = res.data?.optJSONArray("d") ?: JSONArray()
                val dr = res.data?.optString("dr", "0")?.toDoubleOrNull() ?: 0.0
                val out = JSONObject().apply {
                    put("summary", JSONObject().apply {
                        put("total_transactions", dArr.length())
                        put("total_income", dr)
                    })
                    put("transactions", dArr)
                }
                ApiResult(status = res.status, message = res.message, data = out)
            }
            "admin_payment_gateways" -> callNativeApi("apiv1/pg_action", params + mapOf("sub_action" to "list"))
            "admin_pg_action" -> callNativeApi("apiv1/pg_action", params)
            "admin_send_message" -> {
                val mode = params["mode"]?.toString() ?: "single"
                if (mode == "single") {
                    callNativeApi("message/send-post", mapOf("id_customer" to params["customer_id"], "message" to params["message"], "via" to params["via"]))
                } else {
                    callNativeApi("message/send_bulk-post", mapOf("group" to params["group"], "message" to params["message"], "via" to params["via"]))
                }
            }
            "admin_settings" -> {
                val res = callNativeApi("settings/app")
                val cfg = res.data?.optJSONObject("_c") ?: res.data ?: JSONObject()
                ApiResult(status = res.status, message = res.message, data = cfg)
            }
            "admin_settings_save" -> {
                val settingsMap = (params["settings"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: params
                callNativeApi("settings/app-post", settingsMap + mapOf("general" to "1"))
            }
            "admin_staff_list" -> {
                val res = callNativeApi("settings/users")
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                ApiResult(status = res.status, message = res.message, dataArray = dArr)
            }
            "admin_staff_save" -> callNativeApi("settings/users-post", params)
            "admin_staff_delete" -> callNativeApi("settings/users-delete/${params["id"]}")
            "admin_logs" -> {
                val res = callNativeApi("logs/phpnuxbill")
                val dArr = res.data?.optJSONArray("d") ?: res.dataArray ?: JSONArray()
                ApiResult(status = res.status, message = res.message, dataArray = dArr)
            }

            // 8. MODUL EKSTRA PENDAMPING -> /system/api.php?r=apiv1/...
            "admin_map_data" -> callNativeApi("apiv1/map_all", params)
            "admin_router_status" -> callNativeApi("apiv1/mikrotik_live", params)
            "admin_online_users" -> callNativeApi("apiv1/online_users", params)
            "admin_data_usage" -> callNativeApi("apiv1/data_usage", params)
            "admin_kick_user" -> callNativeApi("apiv1/kick_user", params)
            "admin_router_reboot" -> callNativeApi("apiv1/router_reboot", params)
            "admin_coupons" -> callNativeApi("apiv1/coupons", params)
            "admin_coupon_save" -> callNativeApi("apiv1/coupon_save", params)
            "admin_coupon_delete" -> callNativeApi("apiv1/coupon_delete", params)
            "admin_assets" -> callNativeApi("apiv1/assets", params)
            "admin_asset_save" -> callNativeApi("apiv1/asset_save", params)
            "admin_asset_delete" -> callNativeApi("apiv1/asset_delete", params)
            else -> callNativeApi("apiv1/$action", params)
        }
    }
}

fun formatRupiah(amount: Double, prefix: String = ApiClient.currencyCode): String {
    val formatted = String.format("%,.0f", amount).replace(',', '.')
    return "$prefix $formatted"
}
