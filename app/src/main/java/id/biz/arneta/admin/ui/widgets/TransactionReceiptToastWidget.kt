package id.biz.arneta.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import id.biz.arneta.admin.ui.theme.*
import id.biz.arneta.admin.util.BluetoothPrinterHelper
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * MANAJER & WIDGET TOAST STRUK TRANSAKSI INTERAKTIF (PRINT BLUETOOTH + RESEND WA WEB)
 */
object ArnetaTransactionToastManager {
    var currentReceipt by mutableStateOf<JSONObject?>(null)

    fun showFromApiResponse(
        resData: JSONObject?,
        fallbackTitle: String,
        fallbackCustomer: JSONObject? = null,
        fallbackPlanName: String = "-",
        fallbackPrice: Double = 0.0,
        fallbackMethod: String = "-",
        fallbackRouter: String = "-"
    ) {
        val receiptObj = resData?.optJSONObject("receipt")
        if (receiptObj != null) {
            receiptObj.put("toast_title", fallbackTitle)
            currentReceipt = receiptObj
            return
        }
        val invObj = resData?.optJSONObject("invoice")
        val constructed = JSONObject().apply {
            put("toast_title", fallbackTitle)
            put("trx_id", invObj?.optInt("id") ?: 0)
            put("invoice", invObj?.optString("invoice")?.ifEmpty { null } ?: "INV-${System.currentTimeMillis() % 10000}")
            put("username", invObj?.optString("username")?.ifEmpty { null } ?: fallbackCustomer?.optString("username", "-"))
            put("fullname", fallbackCustomer?.optString("fullname")?.ifEmpty { null } ?: invObj?.optString("username", "-"))
            put("phonenumber", fallbackCustomer?.optString("phonenumber", "") ?: "")
            put("plan_name", invObj?.optString("plan_name")?.ifEmpty { null } ?: fallbackPlanName)
            put("price", invObj?.optDouble("price") ?: fallbackPrice)
            put("method", invObj?.optString("method")?.ifEmpty { null } ?: fallbackMethod)
            put("routers", invObj?.optString("routers")?.ifEmpty { null } ?: fallbackRouter)
            put(
                "recharged_at",
                if (invObj != null) "${invObj.optString("recharged_on")} ${invObj.optString("recharged_time")}".trim()
                else "Baru saja"
            )
            put(
                "expiration",
                if (invObj != null) "${invObj.optString("expiration")} ${invObj.optString("time")}".trim()
                else "-"
            )
            put("balance_after", resData?.optDouble("balance") ?: (fallbackCustomer?.optDouble("balance", 0.0) ?: 0.0))
        }
        currentReceipt = constructed
    }

    fun dismiss() {
        currentReceipt = null
    }
}

@Composable
fun ArnetaInteractiveTransactionToast() {
    val receipt = ArnetaTransactionToastManager.currentReceipt ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResendingWa by remember { mutableStateOf(false) }

    val title = receipt.optString("toast_title", "Transaksi Berhasil!")
    val trxId = receipt.optInt("trx_id", 0)
    val invoiceNo = receipt.optString("invoice", "-")
    val fullname = receipt.optString("fullname", "-")
    val username = receipt.optString("username", "-")
    val phone = receipt.optString("phonenumber", "")
    val planName = receipt.optString("plan_name", "-")
    val typeStr = receipt.optString("type", "Layanan")
    val priceVal = receipt.optDouble("price", 0.0)
    val methodStr = receipt.optString("method", "-")
    val routerStr = receipt.optString("routers", "-")
    val rechargedAt = receipt.optString("recharged_at", "-")
    val expiration = receipt.optString("expiration", "-")
    val isBalanceTrx = typeStr.equals("Balance", ignoreCase = true) || routerStr.equals("balance", ignoreCase = true)
    val balanceAfter = receipt.optDouble("balance_after", 0.0)

    AlertDialog(
        onDismissRequest = { ArnetaTransactionToastManager.dismiss() },
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ArnetaGreenSuccess.copy(alpha = 0.2f))
                            .border(1.5.dp, ArnetaGreenSuccess, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ArnetaGreenSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = ArnetaGreenSuccess
                        )
                        Text(
                            text = "No. Invoice: $invoiceNo",
                            fontSize = 11.5.sp,
                            color = ArnetaGoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = { ArnetaTransactionToastManager.dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArnetaGoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        ToastDetailRow("Pelanggan", "$fullname (@$username)")
                        ToastDetailRow("Paket / Layanan", planName)
                        ToastDetailRow("Nominal Harga", formatRupiah(priceVal), valueColor = ArnetaGoldPrimary)
                        ToastDetailRow("Metode Bayar", methodStr)
                        if (rechargedAt.isNotEmpty() && rechargedAt != "-") {
                            ToastDetailRow("Waktu Trx", rechargedAt)
                        }
                        if (isBalanceTrx) {
                            ToastDetailRow("Saldo Saat Ini", formatRupiah(balanceAfter), valueColor = ArnetaCyanIncome)
                        } else {
                            if (expiration.isNotEmpty() && expiration != "-") {
                                ToastDetailRow("Aktif s/d", expiration, valueColor = ArnetaGreenSuccess)
                            }
                            if (routerStr.isNotEmpty() && routerStr != "-") {
                                ToastDetailRow("Router / Server", routerStr)
                            }
                        }
                    }
                }
                Text(
                    text = "Pilih aksi cetak struk, kirim ulang WA (Template Web), atau tekan Selesai.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val lines = mutableListOf(
                                    "No. Invoice" to invoiceNo,
                                    "Pelanggan" to "$fullname ($username)",
                                    "Paket" to planName,
                                    "Harga" to formatRupiah(priceVal),
                                    "Metode" to methodStr,
                                    "Tgl Trx" to rechargedAt
                                )
                                if (isBalanceTrx) {
                                    lines.add("Saldo Baru" to formatRupiah(balanceAfter))
                                } else {
                                    lines.add("Aktif s/d" to expiration)
                                    lines.add("Router" to routerStr)
                                }
                                BluetoothPrinterHelper.printReceiptText(
                                    context = context,
                                    title = "STRUK LAYANAN ${ApiClient.companyName.uppercase()}",
                                    lines = lines
                                )
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ArnetaCyanIncome.copy(alpha = 0.18f),
                            contentColor = ArnetaCyanIncome
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Struk", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }

                    FilledTonalButton(
                        enabled = !isResendingWa,
                        onClick = {
                            isResendingWa = true
                            scope.launch {
                                val wRes = ApiClient.request(
                                    "admin_resend_invoice_wa",
                                    mapOf(
                                        "trx_id" to trxId,
                                        "invoice" to invoiceNo,
                                        "username" to username,
                                        "phonenumber" to phone
                                    )
                                )
                                isResendingWa = false
                                val webWaText = wRes.data?.optString("wa_web_text")?.ifEmpty { null }
                                    ?: receipt.optString("wa_web_text", "")
                                val targetPhone = wRes.data?.optString("phonenumber")?.ifEmpty { null } ?: phone
                                val sentByServer = wRes.data?.optBoolean("sent_via_gateway", false) ?: false

                                if (sentByServer) {
                                    Toast.makeText(context, wRes.message, Toast.LENGTH_SHORT).show()
                                } else if (webWaText.isNotEmpty()) {
                                    BluetoothPrinterHelper.shareToWhatsApp(context, webWaText, targetPhone)
                                } else {
                                    val fallbackWa = BluetoothPrinterHelper.buildInvoiceText(
                                        invoice = invoiceNo,
                                        username = "$fullname ($username)",
                                        planName = planName,
                                        price = priceVal,
                                        expiration = expiration,
                                        method = methodStr
                                    )
                                    BluetoothPrinterHelper.shareToWhatsApp(context, fallbackWa, targetPhone)
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ArnetaGreenSuccess.copy(alpha = 0.2f),
                            contentColor = ArnetaGreenSuccess
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isResendingWa) "Mengirim..." else "Kirim Ulang WA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = { ArnetaTransactionToastManager.dismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArnetaGoldPrimary,
                        contentColor = Color(0xFF0B0F19)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesai", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
private fun ToastDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}
