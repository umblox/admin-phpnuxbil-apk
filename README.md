Arneta.ID Admin — PHPNuxBill Mobile Control Center
Aplikasi Android open-source berbasis Jetpack Compose yang dirancang untuk memudahkan pengelola jaringan RT/RW Net dan penyedia layanan internet dalam mengontrol billing PHPNuxBill secara mobile. Dilengkapi dengan manajemen pelanggan, pemantauan real-time MikroTik, audit QRIS, peta jaringan interaktif, hingga in-app webview.

📖 Panduan untuk Pengguna Lain (Cara Menggunakan Aplikasi Ini)
Jika Anda adalah sesama pemilik RT/RW Net atau pengelola billing PHPNuxBill yang ingin menggunakan aplikasi ini untuk server Anda sendiri, silakan ikuti panduan di bawah ini:
1. Melakukan Fork Repositori
 * Buka halaman utama repositori GitHub ini.
 * Klik tombol Fork yang terletak di pojok kanan atas halaman untuk menyalin repositori ini ke akun GitHub pribadi Anda.
 * Tunggu proses forking selesai hingga Anda dialihkan ke halaman repositori baru di akun Anda.
2. Mendapatkan File APK & Controller Server (apiv1.php)
Demi keamanan, file backend server apiv1.php disembunyikan menggunakan sistem Vault di dalam repositori. File ini akan otomatis digenerate khusus untuk Anda saat proses build berjalan:
 * Setelah melakukan fork, masuk ke tab Actions di repositori GitHub Anda.
 * Pilih workflow "Build Arneta.ID Admin APK" pada menu sebelah kiri.
 * Klik tombol Run workflow di sebelah kanan atas, lalu konfirmasi dengan mengklik tombol hijau Run workflow.
 * Tunggu proses kompilasi selesai (biasanya memakan waktu 2–3 menit).
 * Klik pada hasil build yang baru saja selesai, lalu gulir ke bagian bawah untuk mengunduh dua jenis file (Artifacts) yang disediakan:
   * 📦 Arneta-Admin-APK: Berisi file aplikasi Android (.apk) siap instal di HP.
   * 🐘 PHPNuxBill-Server-Controller: Berisi file apiv1.php beserta instruksi pemasangannya.
3. Pemasangan di Sisi Server PHPNuxBill
 * Ekstrak file PHPNuxBill-Server-Controller yang diunduh dari GitHub Actions.
 * Ambil file apiv1.php di dalamnya.
 * Unggah (upload) file tersebut ke server billing PHPNuxBill Anda melalui File Manager cPanel, FTP, atau SSH ke direktori berikut:
   /path-to-your-phpnuxbill/system/controllers/apiv1.php

 * Pastikan permission file tersebut sudah benar agar dapat diakses oleh sistem.
4. Pengaturan Server di Aplikasi Android
 * Instal file Arneta-Admin-APK pada perangkat Android Anda.
 * Buka aplikasi. Pada halaman Login Admin, Anda akan melihat teks informasi server di bawah kolom password (default: Server: phpnuxbill.arneta.biz.id).
 * Ketuk pada bagian teks server tersebut untuk membuka jendela pengaturan server kustom.
 * Masukkan alamat server Anda sesuai dengan konfigurasi jaringan:
   * Domain Publik: Gunakan format [https://billing.domainanda.com](https://billing.domainanda.com) (pastikan menggunakan SSL/HTTPS).
   * Server Lokal / IP LAN: Pilih opsi IP Lokal (HTTP) lalu masukkan alamat server lokal Anda, contoh: [http://192.168.1.100](http://192.168.1.100) atau [http://10.8.0.1/billing](http://10.8.0.1/billing).
 * Ketuk tombol Tes Koneksi untuk memastikan aplikasi berhasil mendeteksi file apiv1.php di server Anda.
 * Jika status menunjukkan berhasil terhubung, ketuk Simpan Server, lalu masukkan Username dan Password Admin/SuperAdmin PHPNuxBill Anda untuk masuk.
🛠️ Catatan untuk Developer (Kustomisasi Lanjutan)
Jika Anda ingin mengubah domain default secara permanen tanpa perlu mengetiknya secara manual di aplikasi setiap kali instal ulang, Anda dapat mengubah konstanta berikut pada kode sumber sebelum melakukan build:
 * ApiClient.kt (ubah variabel DEFAULT_BASE_URL).
 * AuthAndDashboardScreens.kt (ubah variabel DEFAULT_ARNETA_URL).
 * Unggah file logo kustom Anda dengan nama logo.png di direktori utama repositori untuk otomatis mengubah ikon aplikasi saat di-build.
Dibuat dengan AI untuk komunitas pengguna PHPNuxBill Indonesia.
Koreksi jika ada kesalahan terutama terkait security 
