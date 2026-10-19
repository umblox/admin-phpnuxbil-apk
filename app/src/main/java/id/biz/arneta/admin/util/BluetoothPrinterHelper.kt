package id.biz.arneta.admin.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import id.biz.arneta.admin.network.ApiClient
import id.biz.arneta.admin.network.formatRupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinterHelper {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    suspend fun printReceiptText(context: Context, title: String, lines: List<Pair<String, String>>, footer: String = "Terima kasih telah menggunakan layanan internet ${ApiClient.companyName}"): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter: BluetoothAdapter? = btManager?.adapter
                if (adapter == null || !adapter.isEnabled) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Aktifkan Bluetooth terlebih dahulu untuk mencetak.", Toast.LENGTH_LONG).show()
                    }
                    return@withContext false
                }

                val pairedDevices = adapter.bondedDevices
                if (pairedDevices.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Belum ada Printer Bluetooth yang dipasangkan (Paired).", Toast.LENGTH_LONG).show()
                    }
                    return@withContext false
                }

                // Pilih perangkat printer pertama yang terhubung
                val printer = pairedDevices.firstOrNull {
                    val n = it.name?.lowercase() ?: ""
                    n.contains("print") || n.contains("pos") || n.contains("rpp") || n.contains("mpt") || n.contains("bt") || n.contains("thermal")
                } ?: pairedDevices.first()

                val socket = printer.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out: OutputStream = socket.outputStream

                // ESC/POS Initialize & Center Align
                out.write(byteArrayOf(0x1B, 0x40))
                out.write(byteArrayOf(0x1B, 0x61, 0x01))
                out.write(byteArrayOf(0x1B, 0x21, 0x10)) // Bold Double Height
                out.write("${ApiClient.companyName}\n".toByteArray())

                out.write(byteArrayOf(0x1B, 0x21, 0x00)) // Normal text
                out.write("$title\n".toByteArray())
                out.write("--------------------------------\n".toByteArray())

                // Left Align
                out.write(byteArrayOf(0x1B, 0x61, 0x00))
                for ((k, v) in lines) {
                    val left = k.take(14).padEnd(14, ' ')
                    val right = v.take(16).padStart(18, ' ')
                    out.write("$left$right\n".toByteArray())
                }

                out.write("--------------------------------\n".toByteArray())
                out.write(byteArrayOf(0x1B, 0x61, 0x01))
                out.write("$footer\n\n\n".toByteArray())

                out.flush()
                out.close()
                socket.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Berhasil mencetak ke ${printer.name}", Toast.LENGTH_SHORT).show()
                }
                true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mencetak: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                false
            }
        }
    }

    fun shareToWhatsApp(context: Context, text: String, phone: String = "") {
        try {
            val cleanPhone = phone.replace(Regex("[^0-9]"), "").let {
                if (it.startsWith("0")) "62" + it.substring(1) else it
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (cleanPhone.isNotEmpty()) {
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Struk / Voucher"))
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka menu bagikan.", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildInvoiceText(
        invoice: String,
        username: String,
        planName: String,
        price: Double,
        expiration: String,
        method: String
    ): String {
        return """
            *STRUK LAYANAN ${ApiClient.companyName.uppercase()}*
            --------------------------------
            No. Invoice : $invoice
            Pelanggan   : $username
            Paket       : $planName
            Harga       : ${formatRupiah(price)}
            Metode      : $method
            Aktif s/d   : $expiration
            --------------------------------
            Terima kasih telah berlangganan!
        """.trimIndent()
    }
}
