Arneta.ID Admin - PHPNuxBill Mobile Control Center
Aplikasi Android pendamping untuk sistem billing PHPNuxBill, dirancang khusus bagi Administrator, SuperAdmin, dan Staf RT/RW Net untuk memantau trafik MikroTik, mengelola pelanggan, isi ulang paket/saldo, audit pembayaran QRIS, hingga pemetaan jaringan secara real-time.
📋 Daftar Isi
 * Cara Melakukan Fork Repo
 * Instalasi Server Backend (apiv1.php)
 * Cara Mendapatkan & Mengonfigurasi API
 * Cara Setting Server di Aplikasi Android
 * Hal Penting yang Perlu Diketahui
🔄 Cara Melakukan Fork Repo
Jika Anda adalah pemilik RT/RW Net atau penyedia layanan billing lain yang ingin menggunakan atau memodifikasi aplikasi ini dengan branding Anda sendiri:
 * Masuk ke halaman utama repositori GitHub ini.
 * Di pojok kanan atas halaman, klik tombol Fork ↗.
 * Pilih akun atau organisasi tujuan tempat Anda ingin menyalin repositori ini.
 * Setelah proses fork selesai, Anda akan memiliki salinan repositori ini di akun GitHub Anda sendiri.
 * (Opsional) Untuk mem-build APK dengan nama dan logo Anda sendiri, Anda dapat mengunggah file logo (logo.png) ke direktori utama repo, lalu edit berkas konfigurasi seperti ApiClient.kt atau strings.xml. GitHub Actions akan otomatis mem-build APK Anda melalui tab Actions.
⚙️ Instalasi Server Backend (apiv1.php)
Aplikasi seluler ini membutuhkan jembatan API agar dapat berkomunikasi dengan database PHPNuxBill Anda. File pengendali backend (apiv1.php) otomatis disertakan di dalam folder Artifacts (PHPNuxBill-Server-Controller) setiap kali proses build di fork repo Anda selesai.
 * Unduh (download) artefak PHPNuxBill-Server-Controller dari tab Actions di repo GitHub Anda.
 * Ekstrak dan ambil file apiv1.php.
 * Unggah file apiv1.php tersebut ke server PHPNuxBill Anda pada direktori berikut:
   /path/to/phpnuxbill/system/controllers/apiv1.php

 * Pastikan permission file tersebut dapat dibaca oleh web server Anda (misalnya 644).
🔑 Cara Dapat API
Aplikasi ini tidak memerlukan registrasi token manual yang rumit karena terintegrasi langsung dengan sistem autentikasi native PHPNuxBill:
 * Token Admin: Dibuat secara otomatis dan aman saat admin/SuperAdmin berhasil melakukan login pertama kali melalui aplikasi menggunakan username & password akun admin PHPNuxBill Anda.
 * Keamanan: Menggunakan format token resmi sistem (a.<id>.<time>.<sha1>) yang kompatibel dengan protokol keamanan system/api.php milik PHPNuxBill.
📱 Cara Setting Server di Aplikasi Android
Saat pertama kali menginstal dan membuka aplikasi Arneta.ID Admin:
 * Pada halaman utama Login, Anda akan melihat tombol informasi server (secara default mengarah ke server demo/utama).
 * Ketuk tombol Server: [...] untuk membuka menu pengaturan domain.
 * Pilih jenis koneksi server Anda:
   * 🌐 Domain Publik (HTTPS): Masukkan domain billing Anda (contoh: billing.wifiku.id).
   * 🏠 Server Lokal / IP LAN (HTTP): Masukkan alamat IP lokal server atau VPN MikroTik Anda (contoh: 192.168.1.100 atau 10.8.0.1).
 * Ketuk tombol Tes Koneksi untuk memastikan aplikasi berhasil mendeteksi backend apiv1.php di server Anda.
 * Jika status koneksi hijau (berhasil), ketuk Simpan Server.
 * Masukkan Username dan Password Admin/SuperAdmin PHPNuxBill Anda, lalu ketuk MASUK DASHBOARD ADMIN.
⚠️ Hal Penting yang Perlu Diketahui
 * Kompatibilitas PHPNuxBill: Aplikasi ini dioptimalkan untuk berjalan pada core PHPNuxBill modern yang mendukung modul router API (PEAR2 RouterOS).
 * Hak Akses SuperAdmin: Beberapa menu lanjutan seperti melihat paket berstatus Hidden (enabled = 0) atau pengaturan sistem tingkat lanjut memerlukan akun dengan level SuperAdmin pada database PHPNuxBill Anda.
 * Koneksi Lokal/LAN: Jika Anda menggunakan IP lokal (misalnya 192.168.x.x), pastikan perangkat Android Anda terhubung ke jaringan Wi-Fi lokal yang sama atau terhubung melalui jaringan VPN (seperti WireGuard / ZeroTier / Tailscale) ke server billing Anda.
