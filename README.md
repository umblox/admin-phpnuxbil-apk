# PHPNuxBill Admin (Android)

Aplikasi Android native berbasis Kotlin dan Jetpack Compose untuk mengelola server billing **PHPNuxBill** langsung dari HP. Dibuat untuk memudahkan operasional harian RT/RW Net dan ISP lokal tanpa harus selalu membuka dashboard web.

## Fitur Utama

- **Manajemen Pelanggan & Layanan:** Tambah/edit pelanggan (Hotspot & PPPoE), isi ulang paket, top-up saldo deposit, perpanjang masa aktif (extend), dan sinkronisasi ulang akun ke router.
- **Voucher & Kupon Promo:** Generate voucher massal sesuai format web, cetak voucher via printer thermal Bluetooth, bagikan kode ke WhatsApp, dan kelola kupon diskon.
- **Monitoring MikroTik Real-Time:** Pantau grafik trafik interface (`rx/tx`) secara langsung, cek beban CPU/RAM router, lihat daftar user yang sedang online, pantau live speed per pelanggan, hingga putus sesi (*kick* user) dan *reboot* router.
- **Manajemen GenieACS (TR-069):** Mendukung koneksi multi-host (HTTP lokal maupun HTTPS domain/tunnel). Pantau redaman optik (RX Power), suhu ONT, daftar perangkat/HP yang terhubung ke WiFi pelanggan, ubah SSID/Password WiFi, kelola koneksi WAN, serta *summon* dan *reboot* modem dari jarak jauh.
- **Laporan Keuangan & Audit Payment Gateway:** Rekap pemasukan berdasarkan rentang tanggal dan metode pembayaran, serta pantau status tagihan Payment Gateway (ViaQRIS, Tripay, Duitku, Xendit, PayPal) lengkap dengan fitur cek status dan *approve* manual.
- **Peta Jaringan & Inventaris Aset:** Visualisasi titik lokasi pelanggan, cakupan radius server/router, titik ODP/aset di peta Leaflet, serta deteksi posisi GPS teknisi di lapangan.
- **Struk & Notifikasi:** Cetak struk transaksi langsung ke printer Bluetooth (ESC/POS) dan kirim ulang struk WhatsApp mengikuti format template web (`notifications.json`).

---

## Persyaratan Server (Wajib)

Aplikasi ini tidak memodifikasi file inti (*core*) PHPNuxBill. Agar aplikasi dapat terhubung ke server, Anda wajib memasang plugin **PHPNuxBill Mobile REST API Bridge** di sisi server.

Repositori plugin:
👉 **[https://github.com/umblox/phpnuxbill-mobile-api-plugin](https://github.com/umblox/phpnuxbill-mobile-api-plugin)**

### Pasang Plugin di Server PHPNuxBill Melalui Plugin Manager Atau Manual Dengan Cara Berikut :

1. Unduh atau *clone* repositori [phpnuxbill-mobile-api-plugin](https://github.com/umblox/phpnuxbill-mobile-api-plugin).
2. Salin file `mobile_api.php` dan seluruh folder `mobile_api_modules/` ke dalam direktori `system/plugin/` pada instalasi PHPNuxBill Anda:
   ```text
   system/
   └── plugin/
       ├── mobile_api.php
       └── mobile_api_modules/
           ├── helpers_common.php
           ├── core_auth_dashboard.php
           ├── core_customers.php
           ├── core_services_vouchers.php
           ├── core_plans_bandwidth.php
           ├── core_network_mikrotik.php
           ├── core_settings_staff.php
           ├── plugin_genieacs.php
           ├── plugin_coupons_assets_pg.php
           └── customer_portal_api.php
