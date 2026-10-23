# PHPNuxBill APK by AI

Aplikasi ini adalah versi APK (Android) dari **PHPNuxBill**, yang di-generate dan dibuat sepenuhnya oleh AI. Repository ini memuat aplikasi klien, sedangkan untuk menghubungkan aplikasi ini dengan server PHPNuxBill Anda, Anda perlu menyiapkan API di sisi server.

## Cara Pemasangan API di Server (Server-Side)

Agar aplikasi APK ini dapat berkomunikasi dengan database dan sistem PHPNuxBill Anda, Anda harus menambahkan endpoint API kustom.

1. Buka file manager server Anda (melalui cPanel, aaPanel, atau SSH).
2. Masuk ke dalam direktori instalasi PHPNuxBill Anda.
3. Navigasikan ke path berikut:
   **`/system/controllers/`**
4. Buat file baru dengan nama **`apiv1.php`**.
5. Copy dan Paste seluruh kode di bawah ini ke dalam file `apiv1.php` tersebut.

### Kode `apiv1.php`

```php
<?php
/**
 * PHPNuxBill Complete API & Companion Controller (v10 Final - Arneta.ID)
 * - Fix Deposit Transaction Recording, Income Calculation & WA/Telegram Notifications
 * - Role-Based Plan Visibility (SuperAdmin sees All Prepaid/Postpaid/Hidden plans)
 * - Native Integration with mikrotik_monitor.php (/interface/monitor-traffic once, <pppoe-user>, health, logs, wlan)
 * Path: system/controllers/apiv1.php
 */

ob_start();
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

$phpnux_root = dirname(__DIR__, 2);
if (is_dir($phpnux_root . '/system') && getcwd() !== $phpnux_root) {
    @chdir($phpnux_root);
}

if (empty($api_secret)) {
    $api_secret = !empty($config['tawkto']) ? $config['tawkto'] : 'Arneta_WiFi_Secret_Key_2026_#99';
}

$raw_input = json_decode(file_get_contents('php://input'), true);
if (is_array($raw_input)) {
    $_POST = array_merge($_POST, $raw_input);
    $_REQUEST = array_merge($_REQUEST, $raw_input);
}

$action = isset($routes[1]) && !empty($routes[1]) ? $routes[1] : (_req('action') ?: 'index');

function api_out($success, $message, $result = null, $code = 200) {
    if (ob_get_length()) {
        ob_clean();
    }
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'success' => (bool)$success,
        'status'  => (bool)$success,
        'message' => (string)$message,
        'result'  => $result,
        'data'    => $result
    ], JSON_UNESCAPED_SLASHES);
    exit();
}

function create_native_token($prefix, $uid, $secret) {
    $time = time();
    return $prefix . '.' . $uid . '.' . $time . '.' . sha1($uid . '.' . $time . '.' . $secret);
}

function generate_customer_token($user, $secret) {
    $payload = $user['id'] . '|' . $user['username'];
    $sig = hash_hmac('sha256', $payload . '|' . md5($user['password']), $secret);
    return base64_encode($payload . '|' . $sig);
}

function get_req_token() {
    if (!empty($_REQUEST['token'])) {
        return trim($_REQUEST['token']);
    }
    $headers = function_exists('apache_request_headers') ? apache_request_headers() : [];
    $auth = $headers['Authorization'] ?? ($headers['authorization'] ?? ($_SERVER['HTTP_AUTHORIZATION'] ?? ''));
    if (preg_match('/Bearer\s(\S+)/', $auth, $m)) {
        return trim($m[1]);
    }
    return '';
}

function require_admin_session($secret, $allowed_roles = []) {
    global $config;

    if (!empty($_SESSION['aid'])) {
        $adm = ORM::for_table('tbl_users')->find_one($_SESSION['aid']);
        if ($adm) {
            if (!empty($allowed_roles) && !in_array($adm['user_type'], $allowed_roles)) {
                api_out(false, 'Akses ditolak untuk level ' . $adm['user_type'], null, 403);
            }
            return $adm;
        }
    }

    $token = get_req_token();
    if (empty($token)) {
        api_out(false, 'Token otorisasi Admin tidak ditemukan.', null, 401);
    }

    if (!empty($config['api_key']) && $token === $config['api_key']) {
        $adm = ORM::for_table('tbl_users')->where_in('user_type', ['SuperAdmin', 'Admin'])->find_one();
        if ($adm) {
            $_SESSION['aid'] = $adm['id'];
            return $adm;
        }
    }

    $dot_parts = explode('.', $token);
    if (count($dot_parts) === 4 && $dot_parts[0] === 'a') {
        list($tipe, $uid, $time, $hash) = $dot_parts;
        if (trim($hash) === sha1($uid . '.' . $time . '.' . $secret)) {
            $adm = ORM::for_table('tbl_users')->find_one($uid);
            if ($adm) {
                if (!empty($allowed_roles) && !in_array($adm['user_type'], $allowed_roles)) {
                    api_out(false, 'Akses ditolak untuk level ' . $adm['user_type'], null, 403);
                }
                $_SESSION['aid'] = $adm['id'];
                return $adm;
            }
        }
    }

    $decoded = base64_decode($token);
    $pipe_parts = explode('|', $decoded);
    if (count($pipe_parts) === 4 && $pipe_parts[0] === 'ADMIN') {
        list($prefix, $id, $username, $sig) = $pipe_parts;
        $adm = ORM::for_table('tbl_users')->find_one($id);
        if ($adm && $adm['username'] === $username) {
            $expected = hash_hmac('sha256', 'ADMIN|' . $id . '|' . $username . '|' . md5($adm['password']), $secret);
            if (hash_equals($expected, $sig)) {
                $_SESSION['aid'] = $adm['id'];
                return $adm;
            }
        }
    }

    api_out(false, 'Sesi Admin tidak valid, silakan login ulang.', null, 401);
}

function require_customer_session($secret) {
    if (!empty($_SESSION['uid'])) {
        $cust = ORM::for_table('tbl_customers')->find_one($_SESSION['uid']);
        if ($cust) return $cust;
    }

    $token = get_req_token();
    if (empty($token)) {
        api_out(false, 'Token otorisasi Pelanggan tidak ditemukan.', null, 401);
    }

    $dot_parts = explode('.', $token);
    if (count($dot_parts) === 4 && $dot_parts[0] === 'c') {
        list($tipe, $uid, $time, $hash) = $dot_parts;
        if (trim($hash) === sha1($uid . '.' . $time . '.' . $secret)) {
            $cust = ORM::for_table('tbl_customers')->find_one($uid);
            if ($cust) {
                $_SESSION['uid'] = $cust['id'];
                return $cust;
            }
        }
    }

    $decoded = base64_decode($token);
    $pipe_parts = explode('|', $decoded);
    if (count($pipe_parts) === 3) {
        list($id, $username, $sig) = $pipe_parts;
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if ($cust && $cust['username'] === $username) {
            $expected = hash_hmac('sha256', $id . '|' . $username . '|' . md5($cust['password']), $secret);
            if (hash_equals($expected, $sig)) {
                $_SESSION['uid'] = $cust['id'];
                return $cust;
            }
        }
    }

    api_out(false, 'Sesi Pelanggan berakhir, silakan login ulang.', null, 401);
}

function check_user_password($plain, $hashed) {
    if (class_exists('Password') && method_exists('Password', '_verify')) {
        if (Password::_verify($plain, $hashed)) return true;
    }
    if (password_verify($plain, $hashed)) return true;
    return ($plain === $hashed || sha1($plain) === $hashed || md5($plain) === $hashed);
}

function hash_user_password($plain) {
    if (class_exists('Password') && method_exists('Password', '_crypt')) {
        return Password::_crypt($plain);
    }
    return password_hash($plain, PASSWORD_DEFAULT);
}

function write_admin_log($admin, $desc) {
    try {
        if (function_exists('_log')) {
            _log($desc, $admin['user_type'] ?? 'Admin', $admin['id']);
        } else {
            $log = ORM::for_table('tbl_logs')->create();
            $log->date = date('Y-m-d H:i:s');
            $log->type = $admin['user_type'] ?? 'Admin';
            $log->description = $desc;
            $log->userid = $admin['id'];
            $log->ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
            $log->save();
        }
    } catch (Exception $e) {}
}

function notify_telegram_admin($text) {
    try {
        if (class_exists('Message') && method_exists('Message', 'sendTelegram')) {
            @Message::sendTelegram($text);
        } elseif (function_exists('sendTelegram')) {
            @sendTelegram($text);
        }
    } catch (Exception $e) {}
}

function notify_whatsapp_customer($phone, $text) {
    if (empty($phone)) return;
    try {
        if (class_exists('Message') && method_exists('Message', 'sendWhatsapp')) {
            @Message::sendWhatsapp($phone, $text);
        }
    } catch (Exception $e) {}
}

function ensure_custom_tables() {
    try {
        $db = ORM::get_db();
        $db->exec("CREATE TABLE IF NOT EXISTS `tbl_coupons` (
            `id` int(11) NOT NULL AUTO_INCREMENT,
            `code` varchar(50) NOT NULL,
            `type` enum('percent','fixed') NOT NULL DEFAULT 'fixed',
            `value` decimal(12,2) NOT NULL DEFAULT 0.00,
            `min_order` decimal(12,2) NOT NULL DEFAULT 0.00,
            `max_discount` decimal(12,2) NOT NULL DEFAULT 0.00,
            `max_usage` int(11) NOT NULL DEFAULT 100,
            `used_count` int(11) NOT NULL DEFAULT 0,
            `status` enum('active','inactive') NOT NULL DEFAULT 'active',
            `expired_date` date DEFAULT NULL,
            `description` varchar(255) DEFAULT '',
            `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (`id`),
            UNIQUE KEY `code` (`code`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

        $db->exec("CREATE TABLE IF NOT EXISTS `tbl_assets` (
            `id` int(11) NOT NULL AUTO_INCREMENT,
            `name` varchar(120) NOT NULL,
            `category` varchar(60) NOT NULL DEFAULT 'ODP/ODC',
            `serial_number` varchar(100) DEFAULT '',
            `brand` varchar(80) DEFAULT '',
            `customer_id` int(11) DEFAULT 0,
            `router_name` varchar(80) DEFAULT '',
            `coordinates` varchar(100) DEFAULT '',
            `cost` decimal(12,2) NOT NULL DEFAULT 0.00,
            `condition_status` enum('Good','Maintenance','Damaged','Lost') NOT NULL DEFAULT 'Good',
            `purchase_date` date DEFAULT NULL,
            `notes` text DEFAULT NULL,
            `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
    } catch (Exception $e) {}
}

function fmt_bytes($bytes, $precision = 2) {
    $b = max((float)$bytes, 0);
    if ($b <= 0) return '0 B';
    $u = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
    $pow = floor(($b ? log($b) : 0) / log(1024));
    $pow = min($pow, count($u) - 1);
    $b /= pow(1024, $pow);
    return round($b, $precision) . ' ' . $u[$pow];
}

function fmt_bps($bps) {
    $b = max((float)$bps, 0);
    if ($b <= 0) return '0 bps';
    if ($b >= 1000000) {
        return round($b / 1000000, 2) . ' Mbps';
    }
    if ($b >= 1000) {
        return round($b / 1000, 1) . ' Kbps';
    }
    return round($b, 0) . ' bps';
}

function connect_ros($ip_port, $user, $pass) {
    if (class_exists('Mikrotik') && method_exists('Mikrotik', 'getClient')) {
        try {
            $c = @Mikrotik::getClient($ip_port, $user, $pass);
            if ($c) return $c;
        } catch (Exception $e) {}
    }
    if (class_exists('PEAR2\Net\RouterOS\Client')) {
        try {
            $parts = explode(':', $ip_port);
            return new PEAR2\Net\RouterOS\Client($parts[0], $user, $pass, isset($parts[1]) ? (int)$parts[1] : 8728, false, 4);
        } catch (Exception $e) {
            return null;
        }
    }
    return null;
}

// ============================================================================
// HELPER QRIS DINAMIS & ANTI-DOUBLE RECHARGE
// ============================================================================
function get_active_gateways($config) {
    $raw = !empty($config['payment_gateway']) ? $config['payment_gateway'] : 'viaqris';
    $parts = array_filter(array_map('trim', explode(',', $raw))) ?: ['viaqris'];
    $labels = [
        'viaqris'  => ['name' => 'QRIS Otomatis (ViaQRIS)', 'desc' => 'Scan / Upload QRIS ShopeePay, DANA, GoPay & M-Banking'],
        'duitku'   => ['name' => 'Duitku Payment', 'desc' => 'Virtual Account, QRIS & E-Wallet'],
        'midtrans' => ['name' => 'Midtrans Payment', 'desc' => 'QRIS, GoPay, ShopeePay & Virtual Account'],
        'tripay'   => ['name' => 'TriPay Payment', 'desc' => 'Virtual Account, QRIS & Minimarket'],
        'xendit'   => ['name' => 'Xendit Payment', 'desc' => 'Virtual Account, E-Wallet & QRIS'],
        'ipaymu'   => ['name' => 'iPaymu Payment', 'desc' => 'QRIS & Virtual Account'],
    ];
    $result = [];
    foreach ($parts as $gw) {
        $key = strtolower($gw);
        $result[] = [
            'id'          => $gw,
            'name'        => $labels[$key]['name'] ?? strtoupper($gw),
            'description' => $labels[$key]['desc'] ?? ('Pembayaran Otomatis via ' . ucfirst($gw)),
        ];
    }
    return $result;
}

function qris_crc16($str) {
    $crc = 0xFFFF;
    for ($c = 0, $len = strlen($str); $c < $len; $c++) {
        $crc ^= ord($str[$c]) << 8;
        for ($i = 0; $i < 8; $i++) {
            $crc = ($crc & 0x8000) ? ((($crc << 1) ^ 0x1021) & 0xFFFF) : (($crc << 1) & 0xFFFF);
        }
    }
    return strtoupper(str_pad(dechex($crc), 4, '0', STR_PAD_LEFT));
}

function to_dynamic_qris($qris_raw, $amount) {
    $qris = trim($qris_raw);
    if (strpos($qris, '000201') !== 0 || strpos($qris, '5802ID') === false) return $qris;
    $amt_str = (string)((int)$amount);
    if ($amt_str <= 0) return $qris;
    $crc_pos = strrpos($qris, '6304');
    if ($crc_pos === false || $crc_pos < strlen($qris) - 8) return $qris;
    $body = preg_replace('/^000201010211/', '000201010212', substr($qris, 0, $crc_pos), 1);
    $parts = explode('5802ID', $body, 2);
    if (count($parts) === 2) {
        $tag54 = '54' . str_pad(strlen($amt_str), 2, '0', STR_PAD_LEFT) . $amt_str;
        if (strpos($parts[0], $tag54) === false) $body = $parts[0] . $tag54 . '5802ID' . $parts[1];
    }
    $body .= '6304';
    return $body . qris_crc16($body);
}

function parse_qris_trx($unpaid, $config) {
    if (!$unpaid) return null;
    $total_amount = (float)$unpaid['price'];
    $qris_string  = '';
    $qris_image   = '';

    foreach (['pg_request', 'pg_paid_response'] as $col) {
        if (empty($unpaid[$col])) continue;
        $raw = trim($unpaid[$col]);
        if (preg_match('/(000201[0-9A-Za-z\.\-\s\/\+]{40,600})/', $raw, $m)) $qris_string = trim($m[1]);
        if (preg_match('/(data:image\/[a-zA-Z0-9\+\/\=;:,]+)/', $raw, $m)) $qris_image = trim($m[1]);

        $json = json_decode($raw, true);
        if (is_array($json)) {
            array_walk_recursive($json, function($val, $key) use (&$total_amount, &$qris_string, &$qris_image) {
                $k = strtolower((string)$key);
                $v = trim((string)$val);
                if (in_array($k, ['amount', 'total', 'nominal', 'total_amount', 'amount_unique', 'bayar']) && is_numeric($v) && (float)$v > 0) {
                    $total_amount = (float)$v;
                }
                if (strpos($v, '000201') === 0 && strlen($v) > 40) $qris_string = $v;
                elseif (strpos($v, 'data:image/') === 0) $qris_image = $v;
                elseif ((strpos($k, 'qr') !== false || strpos($k, 'img') !== false || strpos($k, 'image') !== false) && strpos($v, 'http') === 0) $qris_image = $v;
            });
        }
    }

    foreach ($config as $k => $v) {
        if (!is_string($v)) continue;
        $vt = trim($v);
        if (empty($qris_string) && strpos($vt, '000201') === 0) $qris_string = to_dynamic_qris($vt, $total_amount);
        if (empty($qris_image) && stripos($k, 'qris') !== false && (stripos($vt, 'http') === 0 || preg_match('/\.(png|jpg|jpeg|webp)$/i', $vt))) {
            $qris_image = (stripos($vt, 'http') === 0) ? $vt : rtrim(APP_URL, '/') . '/' . ltrim($vt, '/');
        }
    }

    if (!empty($qris_string)) {
        $qris_string = to_dynamic_qris($qris_string, $total_amount);
        if (empty($qris_image)) {
            $qris_image = 'https://api.qrserver.com/v1/create-qr-code/?size=600x600&margin=20&data=' . urlencode($qris_string);
        }
    }

    $st = (int)$unpaid['status'];
    $st_label = ($st === 2) ? 'LUNAS' : (($st === 3) ? 'EXPIRED' : (($st === 4) ? 'DIBATALKAN' : 'MENUNGGU PEMBAYARAN'));

    return [
        'id'              => (int)$unpaid['id'],
        'username'        => $unpaid['username'],
        'plan_id'         => (int)$unpaid['plan_id'],
        'plan_name'       => $unpaid['plan_name'],
        'price'           => $total_amount,
        'base_price'      => (float)$unpaid['price'],
        'gateway'         => $unpaid['gateway'],
        'payment_method'  => $unpaid['payment_method'] ?: 'QRIS',
        'payment_channel' => $unpaid['payment_channel'] ?: strtoupper($unpaid['gateway']),
        'pg_url_payment'  => $unpaid['pg_url_payment'],
        'qris_string'     => $qris_string,
        'qris_image'      => $qris_image,
        'status'          => $st,
        'status_label'    => $st_label,
        'expired_date'    => $unpaid['expired_date'] ?: '-',
        'created_date'    => $unpaid['created_date'] ?: '-',
        'paid_date'       => $unpaid['paid_date'] ?: '-',
        'trx_invoice'     => $unpaid['trx_invoice'] ?: ('INV-' . $unpaid['id'])
    ];
}

function verify_already_recharged(&$trx) {
    if (!$trx) return false;
    if ((int)$trx['status'] === 2) return true;
    if (!empty($trx['created_date'])) {
        $created_ts = strtotime($trx['created_date']);
        $recent = ORM::for_table('tbl_transactions')
            ->where('username', $trx['username'])
            ->where('plan_name', $trx['plan_name'])
            ->order_by_desc('id')
            ->find_one();
        if ($recent && !empty($recent['recharged_on'])) {
            $rech_ts = strtotime($recent['recharged_on'] . ' ' . ($recent['recharged_time'] ?: '00:00:00'));
            if ($rech_ts >= ($created_ts - 5)) {
                $trx->status = 2;
                if (empty($trx->paid_date)) $trx->paid_date = date('Y-m-d H:i:s');
                if (empty($trx->trx_invoice) && !empty($recent['invoice'])) $trx->trx_invoice = $recent['invoice'];
                $trx->save();
                return true;
            }
        }
    }
    return false;
}

register_shutdown_function(function() {
    if (!empty($GLOBALS['API_LOCK_HANDLE']) && is_resource($GLOBALS['API_LOCK_HANDLE'])) {
        @flock($GLOBALS['API_LOCK_HANDLE'], LOCK_UN);
        @fclose($GLOBALS['API_LOCK_HANDLE']);
        $GLOBALS['API_LOCK_HANDLE'] = null;
    }
    if (!empty($GLOBALS['API_INTERCEPT_TRX_ID'])) {
        $trx_id = $GLOBALS['API_INTERCEPT_TRX_ID'];
        $mode   = $GLOBALS['API_INTERCEPT_MODE'] ?? 'buy';
        $cfg    = $GLOBALS['config'] ?? [];
        $GLOBALS['API_INTERCEPT_TRX_ID'] = null;

        if (ob_get_length()) ob_clean();
        if (!headers_sent()) {
            header_remove('Location');
            header_remove('location');
            header('Content-Type: application/json; charset=utf-8');
            http_response_code(200);
        }

        $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
        $ntype = $_SESSION['ntype'] ?? 's';
        $notify = isset($_SESSION['notify']) ? trim(strip_tags($_SESSION['notify'])) : '';
        unset($_SESSION['notify'], $_SESSION['ntype']);

        if ($mode === 'check_payment') {
            $is_paid = verify_already_recharged($trx);
            $payload = ['paid' => $is_paid, 'bill' => parse_qris_trx($trx, $cfg)];
            echo json_encode([
                'success' => true, 'status' => true,
                'message' => $is_paid ? 'Pembayaran diterima! Paket sudah aktif.' : ($notify ?: 'Pembayaran belum masuk.'),
                'result'  => $payload, 'data' => $payload
            ], JSON_UNESCAPED_SLASHES);
            return;
        }

        if ($trx && (!empty($trx['pg_url_payment']) || $ntype !== 'e')) {
            $payload = parse_qris_trx($trx, $cfg);
            echo json_encode([
                'success' => true, 'status' => true,
                'message' => $notify ?: 'Tagihan pembayaran berhasil dibuat.',
                'result'  => $payload, 'data' => $payload
            ], JSON_UNESCAPED_SLASHES);
        } else {
            if ($trx && empty($trx['pg_url_payment'])) $trx->delete();
            echo json_encode(['success' => false, 'status' => false, 'message' => $notify ?: 'Gagal menghubungi Payment Gateway.', 'result' => null], JSON_UNESCAPED_SLASHES);
        }
    }
});

switch ($action) {
    // =========================================================================
    // 1. AUTH ADMIN & CUSTOMER
    // =========================================================================
    case 'auth':
    case 'admin_login':
    case 'customer_login':
        $role = strtolower(trim(_post('role') ?: ($action === 'customer_login' ? 'customer' : 'admin')));
        $username = trim(_post('username'));
        $password = _post('password');

        if (empty($username) || empty($password)) {
            api_out(false, 'Username dan Password wajib diisi.', null, 400);
        }

        if ($role === 'customer') {
            $cust = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
            if (!$cust || !check_user_password($password, $cust['password'])) {
                api_out(false, 'Username atau Password Pelanggan salah.', null, 401);
            }
            if (!empty($cust['status']) && $cust['status'] !== 'Active') {
                api_out(false, 'Status akun Anda: ' . $cust['status'], null, 403);
            }
            $cust->last_login = date('Y-m-d H:i:s');
            $cust->save();

            $token = create_native_token('c', $cust['id'], $api_secret);
            $cust_arr = $cust->as_array();
            unset($cust_arr['password']);
            api_out(true, 'Login Pelanggan berhasil', [
                'token'    => $token,
                'customer' => $cust_arr,
                'company'  => [
                    'name'            => $config['CompanyName'] ?? 'Arneta.ID',
                    'currency'        => $config['currency_code'] ?? 'Rp.',
                    'phone'           => $config['phone'] ?? '',
                    'address'         => $config['address'] ?? '',
                    'payment_gateway' => $config['payment_gateway'] ?? 'viaqris'
                ]
            ]);
        }

        $admin = ORM::for_table('tbl_users')->where('username', $username)->find_one();
        if (!$admin || !check_user_password($password, $admin['password'])) {
            api_out(false, 'Username atau Password Admin salah.', null, 401);
        }
        if (!empty($admin['status']) && $admin['status'] !== 'Active') {
            api_out(false, 'Akun Admin sedang dinonaktifkan (' . $admin['status'] . ').', null, 403);
        }

        $admin->last_login = date('Y-m-d H:i:s');
        $admin->save();

        $token = create_native_token('a', $admin['id'], $api_secret);
        $adm_arr = $admin->as_array();
        unset($adm_arr['password']);
        api_out(true, 'Selamat datang, ' . ($admin['fullname'] ?: $admin['username']), [
            'token'   => $token,
            'admin'   => $adm_arr,
            'company' => [
                'name'     => $config['CompanyName'] ?? 'Arneta.ID',
                'currency' => $config['currency_code'] ?? 'Rp.',
                'phone'    => $config['phone'] ?? '',
                'address'  => $config['address'] ?? ''
            ]
        ]);
        break;

    // =========================================================================
    // 2. DASBOR ADMIN (TERMASUK PENDAPATAN DARI DEPOSIT SALDO)
    // =========================================================================
    case 'admin_dashboard':
    case 'dashboard_extras':
        $admin = require_admin_session($api_secret);
        $today = date('Y-m-d');
        $start_month = date('Y-m-01');
        $yr = date('Y');
        $months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        // Menghitung seluruh uang masuk (Termasuk Deposit Saldo, hanya mengecualikan pembayaran yang memotong saldo pelanggan)
        $income_today = ORM::for_table('tbl_transactions')
            ->where('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->sum('price') ?: 0;

        $income_month = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_month)
            ->where_lte('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->sum('price') ?: 0;

        $u_act = ORM::for_table('tbl_user_recharges')->where('status', 'on')->count();
        $u_exp = ORM::for_table('tbl_user_recharges')->where('status', 'off')->count();
        $c_all = ORM::for_table('tbl_customers')->count();
        $cb    = ORM::for_table('tbl_customers')->sum('balance') ?: 0;

        $reg = [];
        $sales = [];
        for ($m = 1; $m <= 12; $m++) {
            $ym = $yr . '-' . str_pad($m, 2, '0', STR_PAD_LEFT);
            $cnt = ORM::for_table('tbl_customers')->where_like('created_at', $ym . '%')->count();
            $sum = ORM::for_table('tbl_transactions')
                ->where_like('recharged_on', $ym . '%')
                ->where_not_equal('method', 'Customer - Balance')
                ->sum('price') ?: 0;
            $reg[]   = ['month' => $months[$m - 1], 'count' => (int)$cnt];
            $sales[] = ['month' => $months[$m - 1], 'total' => (float)$sum];
        }

        $stocks = [];
        foreach (ORM::for_table('tbl_plans')->where('enabled', 1)->find_array() as $p) {
            if (strtoupper(trim($p['type'])) === 'BALANCE') continue;
            $stocks[] = [
                'plan_id'      => (int)$p['id'],
                'name_plan'    => $p['name_plan'],
                'type'         => $p['type'],
                'unused'       => (int)ORM::for_table('tbl_voucher')->where('id_plan', $p['id'])->where('status', 0)->count(),
                'used'         => (int)ORM::for_table('tbl_voucher')->where('id_plan', $p['id'])->where('status', 1)->count(),
                'active_users' => (int)ORM::for_table('tbl_user_recharges')->where('plan_id', $p['id'])->where('status', 'on')->count()
            ];
        }

        $unpaid_cnt = (int)ORM::for_table('tbl_payment_gateway')->where('status', 1)->count();

        api_out(true, 'Dashboard berhasil dimuat', [
            'company' => [
                'name'        => $config['CompanyName'] ?? 'Arneta.ID',
                'currency'    => $config['currency_code'] ?? 'Rp.',
                'date_Period' => date('01 M Y') . ' / ' . date('d M Y')
            ],
            'cards' => [
                'income_today'           => (float)$income_today,
                'income_month'           => (float)$income_month,
                'active_users'           => (int)$u_act,
                'expired_users'          => (int)$u_exp,
                'active_expired_label'   => $u_act . '/' . $u_exp,
                'total_customers'        => (int)$c_all,
                'total_customer_balance' => (float)$cb,
                'unpaid_qris_count'      => $unpaid_cnt
            ],
            'charts' => [
                'monthly_registered' => $reg,
                'monthly_sales'      => $sales
            ],
            'unpaid_qris_count'  => $unpaid_cnt,
            'monthly_registered' => $reg,
            'monthly_sales'      => $sales,
            'stocks'             => $stocks,
            'expiring_users'     => ORM::for_table('tbl_user_recharges')->where_lte('expiration', date('Y-m-d', strtotime('+2 days')))->order_by_desc('expiration')->limit(15)->find_array()
        ]);
        break;

    // =========================================================================
    // 3. PELANGGAN & LIVE SEARCH
    // =========================================================================
    case 'admin_customers':
        $admin = require_admin_session($api_secret);
        $search = trim(_req('search'));
        $service_type = trim(_req('service_type'));
        $limit = max(10, min(300, (int)(_req('limit') ?: 150)));

        $q = ORM::for_table('tbl_customers');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR fullname LIKE ? OR phonenumber LIKE ? OR email LIKE ? OR pppoe_username LIKE ? OR address LIKE ?)', [$like, $like, $like, $like, $like, $like]);
        }
        if ($service_type !== '' && strtolower($service_type) !== 'all') {
            $q->where('service_type', $service_type);
        }

        $total_q = clone $q;
        $total = $total_q->count();
        $rows = $q->order_by_desc('id')->limit($limit)->find_array();

        $cids = array_column($rows, 'id');
        $rech_map = [];
        if (!empty($cids)) {
            foreach (ORM::for_table('tbl_user_recharges')->where_in('customer_id', $cids)->order_by_desc('id')->find_array() as $r) {
                $cid = (int)$r['customer_id'];
                if (!isset($rech_map[$cid]) || $r['status'] === 'on') {
                    $rech_map[$cid] = $r;
                }
            }
        }

        $customers = [];
        foreach ($rows as $row) {
            $cid = (int)$row['id'];
            $rech = $rech_map[$cid] ?? null;
            $customers[] = [
                'id'             => $cid,
                'username'       => $row['username'] ?: '',
                'fullname'       => $row['fullname'] ?: $row['username'],
                'phonenumber'    => $row['phonenumber'] ?: '',
                'email'          => $row['email'] ?: '',
                'address'        => $row['address'] ?: '',
                'balance'        => (float)$row['balance'],
                'service_type'   => $row['service_type'] ?: 'Hotspot',
                'account_type'   => $row['account_type'] ?? 'Personal',
                'status'         => $row['status'] ?: 'Active',
                'auto_renewal'   => (int)$row['auto_renewal'],
                'pppoe_username' => $row['pppoe_username'] ?? '',
                'pppoe_password' => $row['pppoe_password'] ?? '',
                'pppoe_ip'       => $row['pppoe_ip'] ?? '',
                'coordinates'    => $row['coordinates'] ?? '',
                'active_package' => $rech ? [
                    'id'         => (int)$rech['id'],
                    'namebp'     => $rech['namebp'],
                    'status'     => $rech['status'],
                    'expiration' => $rech['expiration'],
                    'time'       => $rech['time'],
                    'routers'    => $rech['routers'],
                    'type'       => $rech['type']
                ] : null
            ];
        }

        api_out(true, 'Daftar pelanggan berhasil dimuat', [
            'total'     => (int)$total,
            'customers' => $customers
        ]);
        break;

    case 'admin_customer_save':
        $admin = require_admin_session($api_secret);
        $id = (int)_post('id');
        $username = trim(_post('username'));
        $fullname = trim(_post('fullname'));
        $password = _post('password');

        if (empty($username) || empty($fullname)) {
            api_out(false, 'Username dan Nama Lengkap wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $cust = ORM::for_table('tbl_customers')->find_one($id);
            if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);
            $dup = ORM::for_table('tbl_customers')->where('username', $username)->where_not_equal('id', $id)->find_one();
            if ($dup) api_out(false, 'Username sudah digunakan oleh pelanggan lain.', null, 400);
        } else {
            $dup = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
            if ($dup) api_out(false, 'Username sudah terdaftar.', null, 400);
            $cust = ORM::for_table('tbl_customers')->create();
            $cust->created_by = $admin['id'];
            $cust->created_at = date('Y-m-d H:i:s');
            $cust->balance    = (float)(_post('balance') ?: 0);
        }

        $cust->username       = $username;
        $cust->fullname       = $fullname;
        if (!empty($password)) $cust->password = $password;
        elseif ($id === 0) $cust->password = '123456';
        $cust->phonenumber    = trim(_post('phonenumber'));
        $cust->email          = trim(_post('email'));
        $cust->address        = trim(_post('address'));
        $cust->service_type   = _post('service_type') ?: 'Hotspot';
        $cust->account_type   = _post('account_type') ?: 'Personal';
        $cust->status         = _post('status') ?: 'Active';
        $cust->auto_renewal   = (int)(_post('auto_renewal') ?? 1);
        $cust->pppoe_username = trim(_post('pppoe_username'));
        $cust->pppoe_password = _post('pppoe_password');
        $cust->pppoe_ip       = trim(_post('pppoe_ip'));
        $cust->coordinates    = trim(_post('coordinates'));
        $cust->save();

        write_admin_log($admin, ($id > 0 ? 'Mengubah' : 'Menambah') . ' pelanggan: ' . $username);
        api_out(true, 'Data pelanggan ' . $fullname . ' berhasil disimpan.', $cust->as_array());
        break;

    case 'admin_customer_delete':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_req('id');
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);
        $uname = $cust['username'];
        ORM::for_table('tbl_user_recharges')->where('customer_id', $id)->delete_many();
        $cust->delete();
        write_admin_log($admin, 'Menghapus pelanggan: ' . $uname);
        api_out(true, 'Pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_customer_sync':
        $admin = require_admin_session($api_secret);
        $id = (int)_req('id');
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);

        $recharges = ORM::for_table('tbl_user_recharges')->where('customer_id', $id)->where('status', 'on')->find_many();
        $synced = 0;
        foreach ($recharges as $r) {
            $plan = ORM::for_table('tbl_plans')->find_one($r['plan_id']);
            if ($plan && class_exists('Package') && method_exists('Package', 'getDevice')) {
                $dev_file = Package::getDevice($plan);
                if (file_exists($dev_file)) {
                    require_once $dev_file;
                    $d_cls = $plan['device'];
                    if (class_exists($d_cls)) {
                        $dev = new $d_cls();
                        if (method_exists($dev, 'add_customer')) {
                            @$dev->add_customer($cust, $plan);
                            $synced++;
                        }
                    }
                }
            }
        }
        api_out(true, $synced > 0 ? 'Berhasil sinkronisasi ' . $synced . ' paket ke Router.' : 'Pelanggan belum memiliki paket berstatus ON.');
        break;

    // =========================================================================
    // 4. LAYANAN: PELANGGAN AKTIF, ISI ULANG PAKET, ISI ULANG SALDO, VOUCHER
    // =========================================================================
    case 'admin_prepaid_list':
        $admin = require_admin_session($api_secret);
        $search = trim(_req('search'));
        $status = trim(_req('status'));
        $limit  = max(10, min(300, (int)(_req('limit') ?: 120)));

        $cust_map = [];
        foreach (ORM::for_table('tbl_customers')->find_array() as $c) {
            $cust_map[(int)$c['id']] = $c;
        }

        $q = ORM::for_table('tbl_user_recharges');
        if ($status !== '' && strtolower($status) !== 'all') {
            $q->where('status', $status);
        }
        $rows = $q->order_by_desc('id')->limit($limit)->find_array();

        $items = [];
        $s_lower = strtolower($search);
        foreach ($rows as $r) {
            $c = $cust_map[(int)$r['customer_id']] ?? null;
            $r['fullname']    = $c['fullname'] ?? $r['username'];
            $r['phonenumber'] = $c['phonenumber'] ?? '';

            if ($s_lower !== '') {
                $haystack = strtolower($r['username'] . ' ' . $r['fullname'] . ' ' . $r['namebp'] . ' ' . $r['routers'] . ' ' . $r['phonenumber']);
                if (strpos($haystack, $s_lower) === false) continue;
            }
            $items[] = $r;
        }

        api_out(true, 'Daftar pelanggan aktif dimuat', [
            'total' => count($items),
            'items' => $items
        ]);
        break;

    case 'admin_prepaid_recharge':
        $admin = require_admin_session($api_secret);
        $customer_id = (int)(_post('customer_id') ?: _post('id_customer'));
        $plan_id     = (int)(_post('plan_id') ?: _post('plan'));
        $using       = trim(_post('using') ?: 'Cash');

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) api_out(false, 'Paket internet tidak ditemukan.', null, 404);

        // Jika bukan SuperAdmin, cegah pengaktifan paket Hidden
        if ($admin['user_type'] !== 'SuperAdmin' && (int)$plan['enabled'] !== 1) {
            api_out(false, 'Paket ini berstatus Hidden dan hanya dapat diakses oleh SuperAdmin.', null, 403);
        }

        $router_name = $plan['routers'] ?: (_post('routers') ?: 'radius');

        $ok = false;
        if (class_exists('Package') && method_exists('Package', 'rechargeUser')) {
            $ok = @Package::rechargeUser($cust['id'], $router_name, $plan['id'], $admin['fullname'] ?: 'Admin', $using);
        }

        if ($ok) {
            $last_trx = ORM::for_table('tbl_transactions')->where('username', $cust['username'])->order_by_desc('id')->find_one();
            $log_msg = '[' . ($admin['fullname'] ?: $admin['username']) . '] Isi Ulang Paket ' . $plan['name_plan'] . ' (' . ($config['currency_code'] ?? 'Rp.') . ' ' . number_format((float)$plan['price'], 0, ',', '.') . ') untuk ' . $cust['fullname'] . ' (@' . $cust['username'] . ')';
            write_admin_log($admin, $log_msg);
            notify_telegram_admin("⚡ *ISI ULANG PAKET BERHASIL*\nPelanggan: *" . $cust['fullname'] . "* (@" . $cust['username'] . ")\nPaket: *" . $plan['name_plan'] . "*\nHarga: *" . ($config['currency_code'] ?? 'Rp.') . " " . number_format((float)$plan['price'], 0, ',', '.') . "*\nOleh: " . ($admin['fullname'] ?: $admin['username']));

            api_out(true, 'Paket ' . $plan['name_plan'] . ' berhasil diaktifkan untuk ' . $cust['fullname'] . '!', [
                'invoice' => $last_trx ? $last_trx->as_array() : null
            ]);
        } else {
            $err = isset($_SESSION['notify']) ? trim(strip_tags($_SESSION['notify'])) : 'Gagal mengaktifkan paket ke Router ' . $router_name . '. Pastikan Router MikroTik sedang Online.';
            unset($_SESSION['notify'], $_SESSION['ntype']);
            api_out(false, $err, null, 500);
        }
        break;

    case 'admin_prepaid_delete':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_req('id');
        $ur = ORM::for_table('tbl_user_recharges')->find_one($id);
        if (!$ur) api_out(false, 'Data langganan tidak ditemukan.', null, 404);

        $plan = ORM::for_table('tbl_plans')->find_one($ur['plan_id']);
        $cust = ORM::for_table('tbl_customers')->find_one($ur['customer_id']);
        if ($plan && $cust && class_exists('Package') && method_exists('Package', 'getDevice')) {
            $dev_file = Package::getDevice($plan);
            if (file_exists($dev_file)) {
                require_once $dev_file;
                $d_cls = $plan['device'];
                if (class_exists($d_cls)) {
                    $dev = new $d_cls();
                    if (method_exists($dev, 'remove_customer')) @$dev->remove_customer($cust, $plan);
                }
            }
        }
        $uname = $ur['username'];
        $ur->delete();
        write_admin_log($admin, 'Menghapus paket aktif milik ' . $uname);
        api_out(true, 'Paket pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_deposit':
        // PERBAIKAN PENUH: Mencatat ke tbl_transactions, Menambah Pendapatan, & Memicu Notifikasi WA Pelanggan + Telegram Admin
        $admin = require_admin_session($api_secret);
        $customer_id = (int)(_post('customer_id') ?: _post('id_customer'));
        $plan_id     = (int)(_post('plan_id') ?: _post('id_plan'));
        $custom_amt  = (float)_post('amount');
        $note        = trim(_post('note') ?: 'Deposit Saldo');

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);

        $old_balance = (float)$cust['balance'];
        $admin_name  = $admin['fullname'] ?: $admin['username'];
        $currency    = $config['currency_code'] ?? 'Rp.';
        $comp_name   = $config['CompanyName'] ?? 'Arneta.ID';

        // KASUS 1: Menggunakan Paket Saldo (plan_id > 0 dan tidak mengisi nominal manual)
        if ($custom_amt <= 0 && $plan_id > 0) {
            $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
            if (!$plan) api_out(false, 'Paket Saldo tidak ditemukan.', null, 404);

            $before_trx = ORM::for_table('tbl_transactions')->where('username', $cust['username'])->order_by_desc('id')->find_one();
            $before_trx_id = $before_trx ? (int)$before_trx['id'] : 0;

            $recharged_ok = false;
            if (class_exists('Package') && method_exists('Package', 'rechargeUser')) {
                // Memanggil fungsi asli PHPNuxBill (otomatis tambah saldo, insert tbl_transactions, & kirim WA/Telegram jika diatur di sistem)
                $recharged_ok = @Package::rechargeUser($cust['id'], 'balance', $plan['id'], 'Deposit', $admin_name);
            }

            $cust_fresh = ORM::for_table('tbl_customers')->find_one($customer_id);
            $new_balance = (float)$cust_fresh['balance'];
            $price_amt = (float)$plan['price'];

            // Jika karena konfigurasi tertentu Package::rechargeUser belum menambah saldo, tambahkan secara eksplisit
            if (!$recharged_ok && abs($new_balance - $old_balance) < 0.01) {
                if (class_exists('Balance') && method_exists('Balance', 'plus')) {
                    Balance::plus($cust['id'], $price_amt);
                } else {
                    $cust_fresh->balance = $old_balance + $price_amt;
                    $cust_fresh->save();
                }
                $cust_fresh = ORM::for_table('tbl_customers')->find_one($customer_id);
                $new_balance = (float)$cust_fresh['balance'];
            }

            // Pastikan tercatat di tbl_transactions agar selalu masuk ke Pendapatan & Laporan
            $after_trx = ORM::for_table('tbl_transactions')->where('username', $cust['username'])->order_by_desc('id')->find_one();
            $after_trx_id = $after_trx ? (int)$after_trx['id'] : 0;

            if ($after_trx_id <= $before_trx_id) {
                $inv = 'INV-' . date('ymdHis') . rand(10, 99);
                $t = ORM::for_table('tbl_transactions')->create();
                $t->invoice        = $inv;
                $t->username       = $cust['username'];
                $t->user_id        = $cust['id'];
                $t->plan_name      = $plan['name_plan'];
                $t->price          = $price_amt;
                $t->recharged_on   = date('Y-m-d');
                $t->recharged_time = date('H:i:s');
                $t->expiration     = date('Y-m-d');
                $t->time           = date('H:i:s');
                $t->method         = 'Deposit - ' . $admin_name;
                $t->routers        = 'balance';
                $t->type           = 'Balance';
                $t->admin_id       = $admin['id'];
                try {
                    $t->save();
                } catch (Exception $e) {
                    // Fallback jika tabel tbl_transactions di server menggunakan skema kolom standar tanpa user_id/admin_id
                    $t2 = ORM::for_table('tbl_transactions')->create();
                    $t2->invoice        = $inv;
                    $t2->username       = $cust['username'];
                    $t2->plan_name      = $plan['name_plan'];
                    $t2->price          = $price_amt;
                    $t2->recharged_on   = date('Y-m-d');
                    $t2->recharged_time = date('H:i:s');
                    $t2->expiration     = date('Y-m-d');
                    $t2->time           = date('H:i:s');
                    $t2->method         = 'Deposit - ' . $admin_name;
                    $t2->routers        = 'balance';
                    $t2->type           = 'Balance';
                    $t2->save();
                }

                // Kirim WA & Telegram jika tadi belum terkirim oleh Package::rechargeUser
                $wa_msg = "Halo *" . $cust_fresh['fullname'] . "* (@" . $cust_fresh['username'] . "),\n\n"
                    . "Isi ulang saldo Anda sebesar *" . $currency . " " . number_format($price_amt, 0, ',', '.') . "* (" . $plan['name_plan'] . ") telah berhasil diproses.\n"
                    . "Saldo Sebelumnya: " . $currency . " " . number_format($old_balance, 0, ',', '.') . "\n"
                    . "Saldo Saat Ini: *" . $currency . " " . number_format($new_balance, 0, ',', '.') . "*\n\n"
                    . "Terima kasih,\n*" . $comp_name . "*";
                notify_whatsapp_customer($cust_fresh['phonenumber'], $wa_msg);

                if (class_exists('Message') && method_exists('Message', 'addToInbox')) {
                    @Message::addToInbox($cust_fresh['id'], 'Top-Up Saldo Berhasil (' . $plan['name_plan'] . ')', $wa_msg, $admin_name);
                }
            }

            $tg_msg = "💰 *TOP-UP SALDO PELANGGAN*\n"
                . "Pelanggan: *" . $cust_fresh['fullname'] . "* (@" . $cust_fresh['username'] . ")\n"
                . "Paket: *" . $plan['name_plan'] . "*\n"
                . "Nominal: *" . $currency . " " . number_format($price_amt, 0, ',', '.') . "*\n"
                . "Total Saldo: *" . $currency . " " . number_format($new_balance, 0, ',', '.') . "*\n"
                . "Admin: " . $admin_name;
            notify_telegram_admin($tg_msg);
            write_admin_log($admin, '[' . $admin_name . '] Isi ulang saldo ' . $plan['name_plan'] . ' (' . $currency . ' ' . number_format($price_amt, 0, ',', '.') . ') ke ' . $cust['username']);

            unset($_SESSION['notify'], $_SESSION['ntype']);
            api_out(true, 'Saldo ' . $cust_fresh['fullname'] . ' berhasil ditambah menjadi ' . $currency . ' ' . number_format($new_balance, 0, ',', '.'), [
                'balance' => $new_balance
            ]);
        }

        // KASUS 2: Menggunakan Nominal Manual (custom_amt != 0)
        if ($custom_amt == 0) {
            api_out(false, 'Pilih paket saldo atau masukkan nominal saldo terlebih dahulu.', null, 400);
        }

        if (class_exists('Balance') && method_exists('Balance', 'plus') && $custom_amt > 0) {
            Balance::plus($cust['id'], $custom_amt);
        } else {
            $cust->balance = $old_balance + $custom_amt;
            $cust->save();
        }

        $cust_fresh = ORM::for_table('tbl_customers')->find_one($customer_id);
        $new_balance = (float)$cust_fresh['balance'];
        $inv = 'DEP-' . date('ymdHis') . rand(10, 99);

        try {
            $t = ORM::for_table('tbl_transactions')->create();
            $t->invoice        = $inv;
            $t->username       = $cust_fresh['username'];
            $t->plan_name      = $note . ' (' . $currency . ' ' . number_format($custom_amt, 0, ',', '.') . ')';
            $t->price          = $custom_amt;
            $t->recharged_on   = date('Y-m-d');
            $t->recharged_time = date('H:i:s');
            $t->expiration     = date('Y-m-d');
            $t->time           = date('H:i:s');
            $t->method         = 'Deposit - ' . $admin_name;
            $t->routers        = 'balance';
            $t->type           = 'Balance';
            $t->save();
        } catch (Exception $e) {}

        // Kirim WhatsApp & Inbox ke Pelanggan
        $wa_msg = "Halo *" . $cust_fresh['fullname'] . "* (@" . $cust_fresh['username'] . "),\n\n"
            . "Top-up saldo Anda sebesar *" . $currency . " " . number_format($custom_amt, 0, ',', '.') . "* telah berhasil ditambahkan.\n"
            . "No. Invoice: " . $inv . "\n"
            . "Saldo Sebelumnya: " . $currency . " " . number_format($old_balance, 0, ',', '.') . "\n"
            . "Saldo Saat Ini: *" . $currency . " " . number_format($new_balance, 0, ',', '.') . "*\n\n"
            . "Terima kasih,\n*" . $comp_name . "*";
        notify_whatsapp_customer($cust_fresh['phonenumber'], $wa_msg);

        if (class_exists('Message') && method_exists('Message', 'addToInbox')) {
            @Message::addToInbox($cust_fresh['id'], 'Top-Up Saldo ' . $currency . ' ' . number_format($custom_amt, 0, ',', '.'), $wa_msg, $admin_name);
        }

        // Kirim Notifikasi Telegram ke Admin
        $tg_msg = "💰 *TOP-UP SALDO MANUAL*\n"
            . "Invoice: `" . $inv . "`\n"
            . "Pelanggan: *" . $cust_fresh['fullname'] . "* (@" . $cust_fresh['username'] . ")\n"
            . "Nominal: *" . $currency . " " . number_format($custom_amt, 0, ',', '.') . "*\n"
            . "Total Saldo: *" . $currency . " " . number_format($new_balance, 0, ',', '.') . "*\n"
            . "Oleh: " . $admin_name;
        notify_telegram_admin($tg_msg);
        write_admin_log($admin, '[' . $admin_name . '] Top-up saldo manual ' . $currency . ' ' . number_format($custom_amt, 0, ',', '.') . ' ke ' . $cust_fresh['username']);

        api_out(true, 'Saldo ' . $cust_fresh['fullname'] . ' berhasil ditambah menjadi ' . $currency . ' ' . number_format($new_balance, 0, ',', '.'), [
            'balance' => $new_balance,
            'invoice' => $inv
        ]);
        break;

    case 'admin_vouchers':
        $admin = require_admin_session($api_secret);
        $search = trim(_req('search'));
        $status = _req('status');
        $limit  = max(10, min(200, (int)(_req('limit') ?: 100)));

        $plans_map = [];
        foreach (ORM::for_table('tbl_plans')->find_array() as $p) {
            $plans_map[(int)$p['id']] = $p;
        }

        $q = ORM::for_table('tbl_voucher');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(code LIKE ? OR user LIKE ? OR routers LIKE ?)', [$like, $like, $like]);
        }
        if ($status !== '' && $status !== null && strtolower((string)$status) !== 'all') {
            $q->where('status', (string)$status);
        }

        $rows = $q->order_by_desc('id')->limit($limit)->find_array();
        $vouchers = [];
        foreach ($rows as $r) {
            $p = $plans_map[(int)$r['id_plan']] ?? null;
            $r['name_plan'] = $p ? $p['name_plan'] : ('Plan #' . $r['id_plan']);
            $r['price']     = $p ? (float)$p['price'] : 0.0;
            $r['validity']  = $p ? ($p['validity'] . ' ' . $p['validity_unit']) : '-';
            $vouchers[] = $r;
        }

        api_out(true, 'Daftar voucher berhasil dimuat', [
            'total'    => count($vouchers),
            'vouchers' => $vouchers
        ]);
        break;

    case 'admin_voucher_generate':
        $admin = require_admin_session($api_secret);
        $plan_id     = (int)(_post('plan_id') ?: _post('plan'));
        $count       = max(1, min(500, (int)(_post('count') ?: _post('numbervoucher') ?: 10)));
        $length_code = max(4, min(16, (int)(_post('length_code') ?: _post('lengthcode') ?: 6)));
        $prefix      = trim(_post('prefix'));
        $format      = _post('voucher_format') ?: 'up';

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) api_out(false, 'Pilih paket voucher terlebih dahulu.', null, 404);

        $routers = $plan['routers'] ?: (_post('routers') ?: 'radius');
        $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        if ($format === 'low') $chars = 'abcdefghjkmnpqrstuvwxyz23456789';
        elseif ($format === 'rand') $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
        elseif ($format === 'numbers') $chars = '0123456789';

        $generated = [];
        $max_idx = strlen($chars) - 1;
        for ($i = 0; $i < $count; $i++) {
            $rand_str = '';
            for ($j = 0; $j < $length_code; $j++) {
                $rand_str .= $chars[random_int(0, $max_idx)];
            }
            $code = $prefix . $rand_str;
            if (ORM::for_table('tbl_voucher')->where('code', $code)->find_one()) continue;

            $v = ORM::for_table('tbl_voucher')->create();
            $v->type         = $plan['type'] ?: 'Hotspot';
            $v->routers      = $routers;
            $v->id_plan      = $plan['id'];
            $v->code         = $code;
            $v->user         = '0';
            $v->status       = '0';
            $v->generated_by = $admin['id'];
            $v->save();

            $generated[] = [
                'id'        => (int)$v->id(),
                'code'      => $code,
                'name_plan' => $plan['name_plan'],
                'price'     => (float)$plan['price'],
                'validity'  => $plan['validity'] . ' ' . $plan['validity_unit'],
                'routers'   => $routers
            ];
        }

        write_admin_log($admin, 'Generate ' . count($generated) . ' voucher paket ' . $plan['name_plan']);
        api_out(true, count($generated) . ' Voucher berhasil dibuat!', ['generated' => $generated]);
        break;

    case 'admin_voucher_refill':
        $admin = require_admin_session($api_secret);
        $customer_id = (int)(_post('customer_id') ?: _post('id_customer'));
        $code        = trim(_post('code'));

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) api_out(false, 'Pilih pelanggan terlebih dahulu.', null, 404);

        $v = ORM::for_table('tbl_voucher')->where('code', $code)->where('status', 0)->find_one();
        if (!$v) api_out(false, 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);

        if (class_exists('Package') && Package::rechargeUser($cust['id'], $v['routers'], $v['id_plan'], 'Voucher', $code)) {
            $v->status = '1';
            $v->user   = $cust['username'];
            $v->save();
            write_admin_log($admin, 'Refill voucher ' . $code . ' ke ' . $cust['username']);
            api_out(true, 'Voucher ' . $code . ' berhasil diaktifkan ke ' . $cust['fullname'] . '!');
        }
        api_out(false, 'Gagal mengaktifkan voucher ke Router.', null, 500);
        break;

    case 'admin_voucher_delete':
        $admin = require_admin_session($api_secret);
        if ((int)_req('delete_used') === 1) {
            $cnt = ORM::for_table('tbl_voucher')->where('status', '1')->count();
            ORM::for_table('tbl_voucher')->where('status', '1')->delete_many();
            api_out(true, $cnt . ' voucher terpakai berhasil dihapus.');
        }
        $v = ORM::for_table('tbl_voucher')->find_one((int)_req('id'));
        if ($v) $v->delete();
        api_out(true, 'Voucher berhasil dihapus.');
        break;

    // =========================================================================
    // 5. PAKET INTERNET (SUPERADMIN: TAMPIL SEMUA PREPAID/POSTPAID/HIDDEN;
    //    STAF SELAIN SUPERADMIN: TETAP FILTER NON-HIDDEN & HOTSPOT PREPAID)
    // =========================================================================
    case 'admin_plans':
        $admin = require_admin_session($api_secret);
        $is_superadmin = ($admin['user_type'] === 'SuperAdmin');
        $type_filter = trim(_req('type'));

        $bw_rows = ORM::for_table('tbl_bandwidth')->order_by_desc('id')->find_array();
        $bw_map = [];
        foreach ($bw_rows as $b) {
            $bw_map[(int)$b['id']] = $b;
        }

        $q = ORM::for_table('tbl_plans');
        // Jika BUKAN SuperAdmin, hanya tampilkan yang enabled = 1 (Non-Hidden)
        if (!$is_superadmin) {
            $q->where('enabled', 1);
        }
        if ($type_filter !== '' && strtolower($type_filter) !== 'all') {
            $q->where('type', $type_filter);
        }

        $rows = $q->order_by_asc('price')->find_array();
        $plans = [];
        foreach ($rows as $r) {
            $p_type    = strtoupper(trim($r['type']));
            $prepaid   = strtolower(trim($r['prepaid'] ?? 'yes'));
            $is_hidden = ((int)($r['enabled'] ?? 1) === 0);

            // Jika staf selain SuperAdmin: sembunyikan Hotspot Postpaid
            if (!$is_superadmin && $p_type === 'HOTSPOT' && $prepaid === 'no') {
                continue;
            }

            $bw = $bw_map[(int)$r['id_bw']] ?? null;
            $r['name_bw'] = $bw ? $bw['name_bw'] : '-';
            $r['rate_label'] = $bw
                ? ($bw['rate_down'] . ' ' . $bw['rate_down_unit'] . ' / ' . $bw['rate_up'] . ' ' . $bw['rate_up_unit'])
                : ($p_type === 'BALANCE' ? 'Paket Deposit Saldo' : '-');
            $r['is_hidden'] = $is_hidden;
            $r['billing_label'] = (($prepaid === 'no') ? 'Postpaid (Pascabayar)' : 'Prepaid (Prabayar)')
                . ($is_hidden ? ' • [HIDDEN]' : '');
            $r['active_users'] = (int)ORM::for_table('tbl_user_recharges')
                ->where('plan_id', $r['id'])
                ->where('status', 'on')
                ->count();
            $plans[] = $r;
        }

        api_out(true, 'Daftar paket internet & bandwidth dimuat', [
            'is_superadmin' => $is_superadmin,
            'plans'         => $plans,
            'bandwidths'    => $bw_rows,
            'routers'       => ORM::for_table('tbl_routers')->find_array(),
            'pools'         => ORM::for_table('tbl_pool')->find_array()
        ]);
        break;

    case 'admin_plan_save':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id        = (int)_post('id');
        $name_plan = trim(_post('name_plan'));
        $type      = _post('type') ?: 'Hotspot';
        $price     = (float)_post('price');

        if (empty($name_plan)) api_out(false, 'Nama paket wajib diisi.', null, 400);

        $plan = ($id > 0) ? ORM::for_table('tbl_plans')->find_one($id) : ORM::for_table('tbl_plans')->create();
        if (!$plan) api_out(false, 'Paket tidak ditemukan.', null, 404);

        $plan->name_plan     = $name_plan;
        $plan->id_bw         = (int)(_post('id_bw') ?: 1);
        $plan->price         = $price;
        $plan->type          = $type;
        $plan->typebp        = _post('typebp') ?: 'Unlimited';
        $plan->limit_type    = _post('limit_type') ?: 'Time_Limit';
        $plan->time_limit    = (int)(_post('time_limit') ?: 0);
        $plan->time_unit     = _post('time_unit') ?: 'Hrs';
        $plan->data_limit    = (int)(_post('data_limit') ?: 0);
        $plan->data_unit     = _post('data_unit') ?: 'MB';
        $plan->validity      = max(1, (int)(_post('validity') ?: 30));
        $plan->validity_unit = _post('validity_unit') ?: 'Days';
        $plan->shared_users  = max(1, (int)(_post('shared_users') ?: 1));
        $plan->routers       = _post('routers') ?: 'radius';
        $plan->is_radius     = ($plan->routers === 'radius') ? 1 : 0;
        $plan->pool          = trim(_post('pool') ?: '');
        $plan->enabled       = (int)(_post('enabled') ?? 1);
        $default_prepaid     = (strtoupper($type) === 'PPPOE') ? 'no' : 'yes';
        $plan->prepaid       = _post('prepaid') ?: $default_prepaid;
        $plan->plan_type     = _post('plan_type') ?: 'Personal';
        if (empty($plan->device)) {
            $plan->device = ($type === 'PPPoE') ? 'MikrotikPppoe' : (($type === 'Hotspot') ? 'MikrotikHotspot' : 'Radius');
        }
        $plan->save();

        write_admin_log($admin, 'Menyimpan paket ' . $type . ': ' . $name_plan);
        api_out(true, 'Paket ' . $name_plan . ' berhasil disimpan.', $plan->as_array());
        break;

    case 'admin_plan_delete':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $plan = ORM::for_table('tbl_plans')->find_one((int)_req('id'));
        if (!$plan) api_out(false, 'Paket tidak ditemukan.', null, 404);
        $plan->delete();
        api_out(true, 'Paket berhasil dihapus.');
        break;

    case 'admin_bandwidths':
        require_admin_session($api_secret);
        api_out(true, 'Daftar bandwidth dimuat', ORM::for_table('tbl_bandwidth')->order_by_desc('id')->find_array());
        break;

    case 'admin_bandwidth_save':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $name_bw = trim(_post('name_bw'));
        if (empty($name_bw)) api_out(false, 'Nama bandwidth wajib diisi.', null, 400);
        $bw = ORM::for_table('tbl_bandwidth')->create();
        $bw->name_bw        = $name_bw;
        $bw->rate_down      = max(1, (int)_post('rate_down'));
        $bw->rate_down_unit = _post('rate_down_unit') === 'Kbps' ? 'Kbps' : 'Mbps';
        $bw->rate_up        = max(1, (int)_post('rate_up'));
        $bw->rate_up_unit   = _post('rate_up_unit') === 'Kbps' ? 'Kbps' : 'Mbps';
        $bw->burst          = trim(_post('burst') ?: '');
        $bw->save();
        api_out(true, 'Bandwidth ' . $name_bw . ' berhasil disimpan.', $bw->as_array());
        break;

    case 'admin_bandwidth_delete':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $bw = ORM::for_table('tbl_bandwidth')->find_one((int)_req('id'));
        if ($bw) $bw->delete();
        api_out(true, 'Bandwidth berhasil dihapus.');
        break;

    // =========================================================================
    // 6. JARINGAN & MIKROTIK MONITOR (100% MENGADOPSI PLUGIN mikrotik_monitor.php)
    // =========================================================================
    case 'admin_routers':
        require_admin_session($api_secret);
        api_out(true, 'Daftar router dimuat', ORM::for_table('tbl_routers')->order_by_asc('id')->find_array());
        break;

    case 'admin_router_save':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id   = (int)_post('id');
        $name = trim(_post('name'));
        $ip   = trim(_post('ip_address'));
        $user = trim(_post('username'));
        $pass = _post('password');
        if (empty($name) || empty($ip) || empty($user)) api_out(false, 'Nama, IP, dan Username Router wajib diisi.', null, 400);

        $r = ($id > 0) ? ORM::for_table('tbl_routers')->find_one($id) : ORM::for_table('tbl_routers')->create();
        if (!$r) api_out(false, 'Router tidak ditemukan.', null, 404);
        $r->name        = $name;
        $r->ip_address  = $ip;
        $r->username    = $user;
        if ($pass !== '' || $id === 0) $r->password = $pass;
        $r->coordinates = trim(_post('coordinates'));
        $r->coverage    = trim(_post('coverage') ?: '350');
        $r->enabled     = 1;
        $r->save();
        api_out(true, 'Router ' . $name . ' berhasil disimpan.', $r->as_array());
        break;

    case 'admin_router_delete':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $r = ORM::for_table('tbl_routers')->find_one((int)_req('id'));
        if ($r) $r->delete();
        api_out(true, 'Router berhasil dihapus.');
        break;

    case 'admin_pools':
        require_admin_session($api_secret);
        api_out(true, 'Daftar IP Pool dimuat', ORM::for_table('tbl_pool')->order_by_desc('id')->find_array());
        break;

    case 'admin_pool_save':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id        = (int)_post('id');
        $pool_name = trim(_post('pool_name'));
        $range_ip  = trim(_post('range_ip'));
        if (empty($pool_name) || empty($range_ip)) api_out(false, 'Nama Pool dan Rentang IP wajib diisi.', null, 400);
        $p = ($id > 0) ? ORM::for_table('tbl_pool')->find_one($id) : ORM::for_table('tbl_pool')->create();
        $p->pool_name = $pool_name;
        $p->range_ip  = $range_ip;
        $p->routers   = trim(_post('routers'));
        $p->save();
        api_out(true, 'IP Pool berhasil disimpan.', $p->as_array());
        break;

    case 'admin_pool_delete':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $p = ORM::for_table('tbl_pool')->find_one((int)_req('id'));
        if ($p) $p->delete();
        api_out(true, 'IP Pool berhasil dihapus.');
        break;

    case 'mikrotik_live':
    case 'admin_router_status':
        // Implementasi persis mikrotik_monitor.php:
        // Menggunakan /interface/monitor-traffic (interface + once) untuk mendapatkan tx-bits-per-second & rx-bits-per-second murni!
        require_admin_session($api_secret);
        $req_router_id = (int)_req('router_id');
        $req_iface     = trim(_req('interface'));

        $q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($req_router_id > 0) {
            $q->where('id', $req_router_id);
        }
        $routers = $q->find_array();
        $results = [];

        foreach ($routers as $r) {
            $t0 = microtime(true);
            $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
            $ping = round((microtime(true) - $t0) * 1000);

            if (!$client) {
                $results[] = [
                    'id' => (int)$r['id'], 'name' => $r['name'], 'ip_address' => $r['ip_address'],
                    'online' => false, 'ping_ms' => 0, 'cpu_load' => 0, 'ram_percent' => 0,
                    'free_memory' => '0 B', 'total_memory' => '0 B', 'uptime' => 'Offline',
                    'board_name' => '-', 'version' => '-', 'hotspot_online' => 0, 'pppoe_online' => 0,
                    'monitored_interface' => '-', 'interfaces_list' => [], 'interfaces_traffic' => [],
                    'rx_bps' => 0, 'tx_bps' => 0, 'download_mbps' => 0.0, 'upload_mbps' => 0.0,
                    'download_speed_label' => '0 bps', 'upload_speed_label' => '0 bps',
                    'rx_speed_label' => '0 bps', 'tx_speed_label' => '0 bps'
                ];
                continue;
            }

            try {
                // 1. Resource & Health (/system/resource/print & /system/health/print)
                $res_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/system/resource/print'));
                $cpu = 0; $free_mem = 0; $total_mem = 0; $up = '-'; $board = '-'; $ver = '-';
                $platform = '-'; $arch = '-'; $cpu_model = '-'; $cpu_count = '1'; $cpu_freq = '-';
                $free_hdd = 0; $total_hdd = 0;

                foreach ($res_resp as $item) {
                    if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $cpu       = (int)($item->getProperty('cpu-load') ?: 0);
                        $free_mem  = (float)($item->getProperty('free-memory') ?: 0);
                        $total_mem = (float)($item->getProperty('total-memory') ?: 0);
                        $up        = $item->getProperty('uptime') ?: '-';
                        $board     = $item->getProperty('board-name') ?: '-';
                        $ver       = $item->getProperty('version') ?: '-';
                        $platform  = $item->getProperty('platform') ?: 'MikroTik';
                        $arch      = $item->getProperty('architecture-name') ?: '-';
                        $cpu_model = $item->getProperty('cpu') ?: '-';
                        $cpu_count = $item->getProperty('cpu-count') ?: '1';
                        $cpu_freq  = $item->getProperty('cpu-frequency') ?: '-';
                        $free_hdd  = (float)($item->getProperty('free-hdd-space') ?: 0);
                        $total_hdd = (float)($item->getProperty('total-hdd-space') ?: 0);
                        break;
                    }
                }

                $temperature = 'N/A';
                $voltage     = 'N/A';
                try {
                    $health_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/system/health/print'));
                    foreach ($health_resp as $h_item) {
                        if ($h_item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                            if ($h_item->getProperty('temperature') !== null) {
                                $temperature = $h_item->getProperty('temperature') . ' °C';
                            } elseif ($h_item->getProperty('name') === 'temperature') {
                                $temperature = $h_item->getProperty('value') . ' °C';
                            }
                            if ($h_item->getProperty('voltage') !== null) {
                                $voltage = $h_item->getProperty('voltage') . ' V';
                            } elseif ($h_item->getProperty('name') === 'voltage') {
                                $voltage = $h_item->getProperty('value') . ' V';
                            }
                        }
                    }
                } catch (Exception $e) {}

                // 2. Daftar Interface & Tabel Trafik Interface (mikrotik_monitor_get_interfaces_list & mikrotik_monitor_get_traffic)
                $if_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/interface/print'));
                $interfaces_list    = [];
                $interfaces_traffic = [];
                $default_iface      = '';

                foreach ($if_resp as $iface) {
                    if ($iface->getType() !== PEAR2\Net\RouterOS\Response::TYPE_DATA) continue;
                    $if_name = $iface->getProperty('name');
                    if (empty($if_name)) continue;

                    // Jangan masukkan dynamic queue/pppoe ratusan baris ke dropdown utama kecuali dipilih, prioritaskan interface fisik/bridge/vlan
                    $is_dyn_pppoe = (strpos($if_name, '<pppoe-') === 0);
                    if (!$is_dyn_pppoe) {
                        $interfaces_list[] = $if_name;
                    }

                    $is_running = ($iface->getProperty('running') === 'true');
                    if ($default_iface === '' && $is_running && !$is_dyn_pppoe) {
                        $default_iface = $if_name;
                    }

                    $tx_b = (float)($iface->getProperty('tx-byte') ?: 0);
                    $rx_b = (float)($iface->getProperty('rx-byte') ?: 0);

                    if (!$is_dyn_pppoe) {
                        $interfaces_traffic[] = [
                            'name'    => $if_name,
                            'type'    => $iface->getProperty('type') ?: '-',
                            'running' => $is_running,
                            'status'  => $is_running ? 'UP' : 'DOWN',
                            'tx'      => fmt_bytes($tx_b),
                            'rx'      => fmt_bytes($rx_b),
                            'total'   => fmt_bytes($tx_b + $rx_b)
                        ];
                    }
                }

                if ($default_iface === '' && !empty($interfaces_list)) {
                    $default_iface = $interfaces_list[0];
                }

                $target_iface = (!empty($req_iface) && in_array($req_iface, $interfaces_list))
                    ? $req_iface
                    : $default_iface;

                // 3. Monitor Traffic Murni (/interface/monitor-traffic interface=$target_iface once)
                // Persis seperti fungsi mikrotik_monitor_traffic_update() pada plugin asli!
                $tx_bps = 0.0;
                $rx_bps = 0.0;
                if (!empty($target_iface)) {
                    $mon_req = new PEAR2\Net\RouterOS\Request('/interface/monitor-traffic');
                    $mon_req->setArgument('interface', $target_iface);
                    $mon_req->setArgument('once', '');
                    $mon_res = $client->sendSync($mon_req);
                    foreach ($mon_res as $m_item) {
                        if ($m_item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                            $tx_bps = (float)($m_item->getProperty('tx-bits-per-second') ?: 0);
                            $rx_bps = (float)($m_item->getProperty('rx-bits-per-second') ?: 0);
                            break;
                        }
                    }
                }

                // 4. Hitung jumlah Hotspot & PPPoE Online
                $hs = 0;
                foreach ($client->sendSync(new PEAR2\Net\RouterOS\Request('/ip/hotspot/active/print')) as $h) {
                    if ($h->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) $hs++;
                }
                $ppp = 0;
                foreach ($client->sendSync(new PEAR2\Net\RouterOS\Request('/ppp/active/print')) as $p) {
                    if ($p->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) $ppp++;
                }

                $used_mem = max(0, $total_mem - $free_mem);
                $ram_pct  = $total_mem > 0 ? round(($used_mem / $total_mem) * 100, 1) : 0;

                $rx_mbps = round($rx_bps / 1000000.0, 2);
                $tx_mbps = round($tx_bps / 1000000.0, 2);

                $results[] = [
                    'id'                   => (int)$r['id'],
                    'name'                 => $r['name'],
                    'ip_address'           => $r['ip_address'],
                    'online'               => true,
                    'ping_ms'              => $ping,
                    'platform'             => $platform,
                    'board_name'           => $board,
                    'arch'                 => $arch,
                    'version'              => $ver,
                    'cpu'                  => $cpu_model,
                    'cpu_info'             => $cpu_count . ' Core / ' . $cpu_freq . ' MHz / ' . $cpu . '%',
                    'cpu_load'             => $cpu,
                    'temperature'          => $temperature,
                    'voltage'              => $voltage,
                    'used_memory'          => fmt_bytes($used_mem),
                    'free_memory'          => fmt_bytes($free_mem),
                    'total_memory'         => fmt_bytes($total_mem),
                    'ram_percent'          => $ram_pct,
                    'free_hdd'             => fmt_bytes($free_hdd),
                    'total_hdd'            => fmt_bytes($total_hdd),
                    'uptime'               => $up,
                    'hotspot_online'       => $hs,
                    'pppoe_online'         => $ppp,
                    'monitored_interface'  => $target_iface,
                    'interfaces_list'      => $interfaces_list,
                    'interfaces_traffic'   => $interfaces_traffic,
                    'rx_bps'               => round($rx_bps),
                    'tx_bps'               => round($tx_bps),
                    'rx_mbps'              => $rx_mbps,
                    'tx_mbps'              => $tx_mbps,
                    'rx_speed_label'       => fmt_bps($rx_bps),
                    'tx_speed_label'       => fmt_bps($tx_bps),
                    'download_mbps'        => $rx_mbps,
                    'upload_mbps'          => $tx_mbps,
                    'download_speed_label' => fmt_bps($rx_bps),
                    'upload_speed_label'   => fmt_bps($tx_bps)
                ];
            } catch (Exception $e) {
                $results[] = [
                    'id' => (int)$r['id'], 'name' => $r['name'], 'ip_address' => $r['ip_address'],
                    'online' => false, 'ping_ms' => 0, 'uptime' => 'Error: ' . $e->getMessage()
                ];
            }
        }
        api_out(true, 'Status live router berhasil dimuat', $results);
        break;

    case 'online_users':
    case 'admin_online_users':
    case 'data_usage':
    case 'admin_data_usage':
        // Implementasi persis mikrotik_monitor_get_ppp_online_users() & mikrotik_monitor_get_hotspot_online_users()
        // Mengambil trafik PPPoE dari interface <pppoe-$username> pada /interface/print!
        require_admin_session($api_secret);
        $req_router_id = (int)_req('router_id');
        $q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($req_router_id > 0) {
            $q->where('id', $req_router_id);
        }
        $routers = $q->find_array();

        $users  = [];
        $rx_sum = 0.0;
        $tx_sum = 0.0;

        foreach ($routers as $r) {
            $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
            if (!$client) continue;

            try {
                // Ambil seluruh /interface/print untuk memetakan <pppoe-$username> (persis plugin mikrotik_monitor.php)
                $interface_data = [];
                foreach ($client->sendSync(new PEAR2\Net\RouterOS\Request('/interface/print')) as $if_item) {
                    if ($if_item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $if_name = $if_item->getProperty('name');
                        if (!empty($if_name)) {
                            $interface_data[$if_name] = [
                                'txBytes' => (float)($if_item->getProperty('tx-byte') ?: 0),
                                'rxBytes' => (float)($if_item->getProperty('rx-byte') ?: 0)
                            ];
                        }
                    }
                }

                // 1. Hotspot Active Users
                foreach ($client->sendSync(new PEAR2\Net\RouterOS\Request('/ip/hotspot/active/print')) as $hs) {
                    if ($hs->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $uname = $hs->getProperty('user') ?: '-';
                        $rx    = (float)($hs->getProperty('bytes-in') ?: 0);
                        $tx    = (float)($hs->getProperty('bytes-out') ?: 0);
                        $rx_sum += $rx;
                        $tx_sum += $tx;
                        $users[] = [
                            'ros_id'       => $hs->getProperty('.id'),
                            'username'     => $uname,
                            'service'      => 'Hotspot',
                            'server'       => $hs->getProperty('server') ?: 'all',
                            'router'       => $r['name'],
                            'router_id'    => (int)$r['id'],
                            'ip_address'   => $hs->getProperty('address') ?: '-',
                            'mac_address'  => $hs->getProperty('mac-address') ?: '-',
                            'uptime'       => $hs->getProperty('uptime') ?: '0s',
                            'session_time' => $hs->getProperty('session-time-left') ?: '-',
                            'upload'       => fmt_bytes($rx),
                            'download'     => fmt_bytes($tx),
                            'rx_bytes'     => fmt_bytes($rx),
                            'tx_bytes'     => fmt_bytes($tx),
                            'total'        => fmt_bytes($rx + $tx),
                            'total_bytes'  => 'TX: ' . fmt_bytes($tx) . ' • RX: ' . fmt_bytes($rx) . ' (Total: ' . fmt_bytes($rx + $tx) . ')',
                            'total_raw'    => $rx + $tx
                        ];
                    }
                }

                // 2. PPPoE Online Users (Mengambil trafik dari <pppoe-$username>)
                foreach ($client->sendSync(new PEAR2\Net\RouterOS\Request('/ppp/active/print')) as $ppp) {
                    if ($ppp->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $uname   = $ppp->getProperty('name') ?: '-';
                        $if_key  = "<pppoe-$uname>";
                        $tx      = isset($interface_data[$if_key]) ? $interface_data[$if_key]['txBytes'] : 0.0;
                        $rx      = isset($interface_data[$if_key]) ? $interface_data[$if_key]['rxBytes'] : 0.0;
                        $rx_sum += $rx;
                        $tx_sum += $tx;

                        $users[] = [
                            'ros_id'       => $ppp->getProperty('.id'),
                            'username'     => $uname,
                            'service'      => 'PPPoE',
                            'server'       => $ppp->getProperty('service') ?: 'pppoe',
                            'router'       => $r['name'],
                            'router_id'    => (int)$r['id'],
                            'ip_address'   => $ppp->getProperty('address') ?: '-',
                            'mac_address'  => $ppp->getProperty('caller-id') ?: '-',
                            'uptime'       => $ppp->getProperty('uptime') ?: '0s',
                            'session_time' => '-',
                            'upload'       => fmt_bytes($rx),
                            'download'     => fmt_bytes($tx),
                            'rx_bytes'     => fmt_bytes($rx),
                            'tx_bytes'     => fmt_bytes($tx),
                            'total'        => fmt_bytes($rx + $tx),
                            'total_bytes'  => 'TX: ' . fmt_bytes($tx) . ' • RX: ' . fmt_bytes($rx) . ' (Total: ' . fmt_bytes($rx + $tx) . ')',
                            'total_raw'    => $rx + $tx
                        ];
                    }
                }
            } catch (Exception $e) {}
        }

        api_out(true, 'Daftar user online berhasil dimuat', [
            'total'   => count($users),
            'users'   => $users,
            'items'   => $users,
            'summary' => [
                'total_users'    => count($users),
                'total_upload'   => fmt_bytes($rx_sum),
                'total_download' => fmt_bytes($tx_sum),
                'total_traffic'  => fmt_bytes($rx_sum + $tx_sum)
            ]
        ]);
        break;

    case 'mikrotik_extra':
        // Mengambil Log MikroTik (/log/print) & Wireless Signal (/interface/wireless/registration-table/print)
        require_admin_session($api_secret);
        $router_id = (int)_req('router_id');
        $sub_type  = _req('sub_type') ?: 'logs'; // logs | wlan

        $q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($router_id > 0) $q->where('id', $router_id);
        $r = $q->find_one();
        if (!$r) api_out(false, 'Router tidak ditemukan.', null, 404);

        $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
        if (!$client) api_out(false, 'Gagal terhubung ke Router ' . $r['name'], null, 500);

        if ($sub_type === 'wlan') {
            $signals = [];
            try {
                $wlan_res = $client->sendSync(new PEAR2\Net\RouterOS\Request('/interface/wireless/registration-table/print'));
                foreach ($wlan_res as $sig) {
                    if ($sig->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $signals[] = [
                            'interface'       => $sig->getProperty('interface') ?: '-',
                            'mac_address'     => $sig->getProperty('mac-address') ?: '-',
                            'uptime'          => $sig->getProperty('uptime') ?: '-',
                            'last_ip'         => $sig->getProperty('last-ip') ?: '-',
                            'signal_strength' => $sig->getProperty('signal-strength') ?: '-',
                            'tx_ccq'          => $sig->getProperty('tx-ccq') ?: '-',
                            'rx_rate'         => $sig->getProperty('rx-rate') ?: '-',
                            'tx_rate'         => $sig->getProperty('tx-rate') ?: '-'
                        ];
                    }
                }
            } catch (Exception $e) {}
            api_out(true, 'Data sinyal wireless dimuat', $signals);
        }

        // Default: Ambil 100 baris terbaru /log/print MikroTik
        $ros_logs = [];
        try {
            $log_res = $client->sendSync(new PEAR2\Net\RouterOS\Request('/log/print'));
            foreach ($log_res as $entry) {
                if ($entry->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                    $ros_logs[] = [
                        'id'      => $entry->getProperty('.id') ?: '',
                        'time'    => $entry->getProperty('time') ?: '-',
                        'topics'  => $entry->getProperty('topics') ?: 'info',
                        'message' => $entry->getProperty('message') ?: ''
                    ];
                }
            }
            $ros_logs = array_slice(array_reverse($ros_logs), 0, 100);
        } catch (Exception $e) {}
        api_out(true, 'Log MikroTik berhasil dimuat', $ros_logs);
        break;

    case 'kick_user':
    case 'admin_kick_user':
        // Menggunakan fungsi resmi Mikrotik::removeHotspotActiveUser & Mikrotik::removePpoeActive seperti di mikrotik_monitor.php
        require_admin_session($api_secret);
        $router_param = trim(_post('router') ?: _post('router_id'));
        $r = is_numeric($router_param)
            ? ORM::for_table('tbl_routers')->find_one((int)$router_param)
            : ORM::for_table('tbl_routers')->where('name', $router_param)->find_one();

        if (!$r) api_out(false, 'Router tidak ditemukan.', null, 404);
        $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
        if (!$client) api_out(false, 'Gagal terhubung ke Router.', null, 500);

        $service = strtolower(trim(_post('service') ?: _post('userType') ?: 'hotspot'));
        $ros_id  = trim(_post('ros_id'));
        $uname   = trim(_post('username'));

        try {
            if ($service === 'hotspot' && class_exists('Mikrotik') && method_exists('Mikrotik', 'removeHotspotActiveUser') && !empty($uname)) {
                @Mikrotik::removeHotspotActiveUser($client, $uname);
            } elseif ($service === 'pppoe' && class_exists('Mikrotik') && method_exists('Mikrotik', 'removePpoeActive') && !empty($uname)) {
                @Mikrotik::removePpoeActive($client, $uname);
            } else {
                $path = ($service === 'pppoe') ? '/ppp/active' : '/ip/hotspot/active';
                $prop = ($service === 'pppoe') ? 'name' : 'user';
                if (!empty($ros_id)) {
                    $rem = new PEAR2\Net\RouterOS\Request($path . '/remove');
                    $rem->setArgument('numbers', $ros_id);
                    $client->sendSync($rem);
                } else {
                    $pr = new PEAR2\Net\RouterOS\Request($path . '/print');
                    $pr->setQuery(PEAR2\Net\RouterOS\Query::where($prop, $uname));
                    foreach ($client->sendSync($pr) as $item) {
                        if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                            $rem = new PEAR2\Net\RouterOS\Request($path . '/remove');
                            $rem->setArgument('numbers', $item->getProperty('.id'));
                            $client->sendSync($rem);
                        }
                    }
                }
            }
            api_out(true, 'Sesi ' . $uname . ' berhasil diputus dari ' . $r['name'] . '.');
        } catch (Exception $e) {
            api_out(false, 'Gagal memutus sesi: ' . $e->getMessage(), null, 500);
        }
        break;

    case 'router_reboot':
    case 'admin_router_reboot':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $r = ORM::for_table('tbl_routers')->find_one((int)_req('id'));
        if (!$r) api_out(false, 'Router tidak ditemukan.', null, 404);
        $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
        if (!$client) api_out(false, 'Gagal terhubung ke Router.', null, 500);
        try { @$client->sendSync(new PEAR2\Net\RouterOS\Request('/system/reboot')); } catch (Exception $e) {}
        api_out(true, 'Perintah Reboot dikirim ke Router ' . $r['name'] . '.');
        break;

    // =========================================================================
    // 7. LAPORAN TRANSAKSI (MENCAKUP DEPOSIT SALDO), PESAN, HALAMAN STATIS & LOGS
    // =========================================================================
    case 'admin_reports':
        require_admin_session($api_secret);
        $start_date = trim(_req('start_date') ?: date('Y-m-01'));
        $end_date   = trim(_req('end_date') ?: date('Y-m-d'));
        $search     = trim(_req('search'));
        $limit      = max(20, min(300, (int)(_req('limit') ?: 150)));

        // Menampilkan seluruh transaksi uang masuk (Termasuk Deposit Saldo, hanya mengecualikan pemotongan saldo 'Customer - Balance')
        $q = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_date)
            ->where_lte('recharged_on', $end_date)
            ->where_not_equal('method', 'Customer - Balance');

        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR invoice LIKE ? OR plan_name LIKE ? OR method LIKE ?)', [$like, $like, $like, $like]);
        }

        $sum_q = clone $q;
        $total_income = $sum_q->sum('price') ?: 0;

        $cnt_q = clone $q;
        $total_count = $cnt_q->count();

        $rows = $q->order_by_desc('id')->limit($limit)->find_array();

        api_out(true, 'Laporan keuangan berhasil dimuat', [
            'summary' => [
                'total_transactions' => (int)$total_count,
                'total_income'       => (float)$total_income
            ],
            'transactions' => $rows
        ]);
        break;

    case 'admin_send_message':
        $admin = require_admin_session($api_secret);
        $mode        = _post('mode') ?: 'single';
        $customer_id = (int)_post('customer_id');
        $group       = _post('group') ?: 'all';
        $via         = _post('via') ?: 'wa';
        $message_raw = trim(_post('message'));
        $subject     = trim(_post('subject') ?: ('Informasi ' . ($config['CompanyName'] ?? 'Arneta.ID')));

        if (empty($message_raw)) {
            api_out(false, 'Isi pesan tidak boleh kosong.', null, 400);
        }

        $targets = [];
        if ($mode === 'single') {
            $c = ORM::for_table('tbl_customers')->find_one($customer_id);
            if (!$c && !empty(_post('username'))) {
                $c = ORM::for_table('tbl_customers')->where('username', trim(_post('username')))->find_one();
            }
            if (!$c) api_out(false, 'Pilih pelanggan tujuan terlebih dahulu.', null, 404);
            $targets[] = $c->as_array();
        } else {
            $q = ORM::for_table('tbl_customers');
            if ($group === 'hotspot') {
                $q->where('service_type', 'Hotspot');
            } elseif ($group === 'pppoe') {
                $q->where('service_type', 'PPPoE');
            } elseif ($group === 'active' || $group === 'expired') {
                $st = ($group === 'active') ? 'on' : 'off';
                $urs = ORM::for_table('tbl_user_recharges')->where('status', $st)->find_array();
                $cids = array_unique(array_column($urs, 'customer_id'));
                if (!empty($cids)) {
                    $q->where_in('id', $cids);
                } else {
                    api_out(false, 'Tidak ada pelanggan dalam kategori ' . $group, null, 404);
                }
            }
            $targets = $q->limit(300)->find_array();
        }

        $sent_count = 0;
        foreach ($targets as $cust) {
            $msg = str_replace(
                ['[[name]]', '[[user_name]]', '[[phone]]', '[[company_name]]', '[[balance]]'],
                [$cust['fullname'], $cust['username'], $cust['phonenumber'], $config['CompanyName'] ?? 'Arneta.ID', number_format((float)$cust['balance'], 0, ',', '.')],
                $message_raw
            );

            $ok = false;
            if (($via === 'wa' || $via === 'both') && !empty($cust['phonenumber'])) {
                if (class_exists('Message') && method_exists('Message', 'sendWhatsapp')) {
                    @Message::sendWhatsapp($cust['phonenumber'], $msg);
                    $ok = true;
                }
            }
            if (($via === 'sms' || $via === 'both') && !empty($cust['phonenumber'])) {
                if (class_exists('Message') && method_exists('Message', 'sendSMS')) {
                    @Message::sendSMS($cust['phonenumber'], $msg);
                    $ok = true;
                }
            }
            if ($via === 'inbox' || $via === 'both') {
                if (class_exists('Message') && method_exists('Message', 'addToInbox')) {
                    @Message::addToInbox($cust['id'], $subject, $msg, 'Admin');
                    $ok = true;
                } else {
                    try {
                        $inb = ORM::for_table('tbl_customers_inbox')->create();
                        $inb->customer_id  = $cust['id'];
                        $inb->date_created = date('Y-m-d H:i:s');
                        $inb->subject      = $subject;
                        $inb->body         = nl2br($msg);
                        $inb->from         = 'Admin';
                        $inb->save();
                        $ok = true;
                    } catch (Exception $e) {}
                }
            }
            if ($ok) $sent_count++;
        }

        write_admin_log($admin, 'Mengirim pesan (' . strtoupper($via) . ') ke ' . $sent_count . ' pelanggan');
        api_out(true, 'Pesan berhasil dikirim ke ' . $sent_count . ' pelanggan.');
        break;

    case 'pages':
    case 'admin_pages':
        require_admin_session($api_secret);
        $pages_dir = is_dir($phpnux_root . '/pages') ? ($phpnux_root . '/pages') : 'pages';
        $files = glob($pages_dir . '/*.html') ?: [];
        $list = [];
        foreach ($files as $f) {
            $name = basename($f, '.html');
            $list[] = [
                'name'    => $name,
                'title'   => str_replace('_', ' ', $name),
                'content' => @file_get_contents($f) ?: ''
            ];
        }
        api_out(true, 'Halaman statis dimuat', $list);
        break;

    case 'page_save':
    case 'admin_page_save':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $name = preg_replace('/[^a-zA-Z0-9_\-]/', '', _post('name'));
        if (empty($name)) api_out(false, 'Nama halaman tidak valid.', null, 400);
        $pages_dir = is_dir($phpnux_root . '/pages') ? ($phpnux_root . '/pages') : 'pages';
        if (@file_put_contents($pages_dir . '/' . $name . '.html', _post('content')) !== false) {
            api_out(true, 'Halaman ' . str_replace('_', ' ', $name) . ' berhasil disimpan.');
        }
        api_out(false, 'Gagal menulis ke folder pages/. Pastikan permission writable.', null, 500);
        break;

    case 'admin_settings':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $rows = ORM::for_table('tbl_appconfig')->find_array();
        $cfg = [];
        foreach ($rows as $r) {
            $cfg[$r['setting']] = $r['value'];
        }
        api_out(true, 'Pengaturan sistem dimuat', $cfg);
        break;

    case 'admin_settings_save':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $updates = _post('settings');
        if (!is_array($updates)) $updates = $_POST;
        foreach ($updates as $k => $v) {
            if (in_array($k, ['r', 'action', 'token', 'settings'])) continue;
            $row = ORM::for_table('tbl_appconfig')->where('setting', $k)->find_one();
            if (!$row) {
                $row = ORM::for_table('tbl_appconfig')->create();
                $row->setting = $k;
            }
            $row->value = is_array($v) ? implode(',', $v) : trim((string)$v);
            $row->save();
        }
        write_admin_log($admin, 'Memperbarui pengaturan sistem');
        api_out(true, 'Pengaturan sistem berhasil disimpan.');
        break;

    case 'admin_staff_list':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $rows = ORM::for_table('tbl_users')->order_by_asc('id')->find_array();
        foreach ($rows as &$r) unset($r['password']);
        api_out(true, 'Daftar staf dimuat', $rows);
        break;

    case 'admin_staff_save':
        require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $username  = trim(_post('username'));
        $fullname  = trim(_post('fullname'));
        $password  = _post('password');
        $user_type = _post('user_type') ?: 'Agent';
        if (empty($username) || empty($fullname)) api_out(false, 'Username & Nama wajib diisi.', null, 400);
        $u = ORM::for_table('tbl_users')->create();
        $u->username     = $username;
        $u->fullname     = $fullname;
        $u->user_type    = $user_type;
        $u->status       = 'Active';
        $u->creationdate = date('Y-m-d H:i:s');
        $u->password     = hash_user_password($password ?: '123456');
        $u->save();
        api_out(true, 'Akun staf ' . $fullname . ' berhasil ditambahkan.');
        break;

    case 'admin_staff_delete':
        $admin = require_admin_session($api_secret, ['SuperAdmin']);
        $id = (int)_req('id');
        if ($id === (int)$admin['id']) api_out(false, 'Tidak dapat menghapus akun sendiri.', null, 400);
        $u = ORM::for_table('tbl_users')->find_one($id);
        if ($u) $u->delete();
        api_out(true, 'Akun staf berhasil dihapus.');
        break;

    case 'admin_logs':
        require_admin_session($api_secret);
        $logs = ORM::for_table('tbl_logs')->order_by_desc('id')->limit(100)->find_array();
        api_out(true, 'System logs berhasil dimuat', $logs);
        break;

    case 'system_tools':
    case 'admin_system_tools':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $tool = _req('tool') ?: 'clear_cache';
        if ($tool === 'clear_cache') {
            $del = 0;
            foreach ([$phpnux_root . '/ui/compiled/*', $phpnux_root . '/system/cache/*'] as $pat) {
                foreach (glob($pat) ?: [] as $f) {
                    if (is_file($f) && !in_array(basename($f), ['index.html', '.htaccess'])) {
                        @unlink($f);
                        $del++;
                    }
                }
            }
            api_out(true, $del . ' file cache sistem berhasil dibersihkan.');
        }
        if ($tool === 'run_cron') {
            $cnt = 0;
            $active = ORM::for_table('tbl_user_recharges')->where('status', 'on')->where_lte('expiration', date('Y-m-d'))->find_many();
            foreach ($active as $ur) {
                if (time() >= strtotime($ur['expiration'] . ' ' . ($ur['time'] ?: '23:59:59'))) {
                    $ur->status = 'off';
                    $ur->save();
                    $cnt++;
                }
            }
            api_out(true, 'Cronjob selesai. ' . $cnt . ' paket kedaluwarsa telah dinonaktifkan.');
        }
        api_out(false, 'Perintah tidak dikenali.', null, 400);
        break;

    // =========================================================================
    // 8. PETA, KUPON, ASSET MANAGER, AUDIT QRIS & WEBVIEW SSO
    // =========================================================================
    case 'map_all':
    case 'admin_map_data':
        require_admin_session($api_secret);
        ensure_custom_tables();

        $active_map = [];
        foreach (ORM::for_table('tbl_user_recharges')->find_array() as $r) {
            $cid = (int)$r['customer_id'];
            if (!isset($active_map[$cid]) || $r['status'] === 'on') $active_map[$cid] = $r;
        }

        $customers = [];
        foreach (ORM::for_table('tbl_customers')->where_not_equal('coordinates', '')->where_not_null('coordinates')->find_array() as $c) {
            $p = array_map('trim', explode(',', $c['coordinates']));
            if (count($p) < 2 || !is_numeric($p[0]) || !is_numeric($p[1])) continue;
            $rech = $active_map[(int)$c['id']] ?? null;
            $customers[] = [
                'id'           => (int)$c['id'],
                'username'     => $c['username'] ?: '-',
                'fullname'     => $c['fullname'] ?: $c['username'],
                'phonenumber'  => $c['phonenumber'] ?: '-',
                'address'      => str_replace(["\r", "\n"], ' ', $c['address'] ?: '-'),
                'service_type' => $c['service_type'] ?: 'Hotspot',
                'lat'          => (float)$p[0],
                'lng'          => (float)$p[1],
                'plan_status'  => $rech ? $rech['status'] : 'off',
                'plan_name'    => $rech ? $rech['namebp'] : 'Non-Paket',
                'expiration'   => $rech ? $rech['expiration'] : '-'
            ];
        }

        $routers = [];
        foreach (ORM::for_table('tbl_routers')->find_array() as $r) {
            if (empty($r['coordinates'])) continue;
            $p = array_map('trim', explode(',', $r['coordinates']));
            if (count($p) < 2 || !is_numeric($p[0]) || !is_numeric($p[1])) continue;
            $routers[] = [
                'id'          => (int)$r['id'],
                'name'        => $r['name'],
                'ip_address'  => $r['ip_address'],
                'description' => str_replace(["\r", "\n"], ' ', $r['description'] ?: ''),
                'coverage'    => (int)($r['coverage'] ?: 350),
                'lat'         => (float)$p[0],
                'lng'         => (float)$p[1]
            ];
        }

        $assets = [];
        try {
            foreach (ORM::for_table('tbl_assets')->find_array() as $a) {
                if (empty($a['coordinates'])) continue;
                $p = array_map('trim', explode(',', $a['coordinates']));
                if (count($p) < 2 || !is_numeric($p[0]) || !is_numeric($p[1])) continue;
                $assets[] = [
                    'id'        => (int)$a['id'],
                    'name'      => $a['name'],
                    'category'  => $a['category'],
                    'condition' => $a['condition_status'],
                    'lat'       => (float)$p[0],
                    'lng'       => (float)$p[1]
                ];
            }
        } catch (Exception $e) {}

        api_out(true, 'Data peta berhasil dimuat', [
            'customers' => $customers,
            'routers'   => $routers,
            'assets'    => $assets
        ]);
        break;

    case 'coupons':
    case 'admin_coupons':
        require_admin_session($api_secret);
        ensure_custom_tables();
        api_out(true, 'Daftar kupon dimuat', ORM::for_table('tbl_coupons')->order_by_desc('id')->find_array());
        break;

    case 'coupon_save':
    case 'admin_coupon_save':
        require_admin_session($api_secret);
        ensure_custom_tables();
        $code = strtoupper(trim(_post('code')));
        if (empty($code)) api_out(false, 'Kode kupon wajib diisi.', null, 400);
        $c = ORM::for_table('tbl_coupons')->create();
        $c->code = $code;
        $c->type = _post('type') === 'percent' ? 'percent' : 'fixed';
        $c->value = (float)_post('value');
        $c->expired_date = _post('expired_date') ?: date('Y-m-d', strtotime('+30 days'));
        $c->save();
        api_out(true, 'Kupon ' . $code . ' berhasil disimpan.', $c->as_array());
        break;

    case 'coupon_delete':
    case 'admin_coupon_delete':
        require_admin_session($api_secret);
        ensure_custom_tables();
        $c = ORM::for_table('tbl_coupons')->find_one((int)_req('id'));
        if ($c) $c->delete();
        api_out(true, 'Kupon berhasil dihapus.');
        break;

    case 'assets':
    case 'admin_assets':
        require_admin_session($api_secret);
        ensure_custom_tables();
        $assets = ORM::for_table('tbl_assets')->order_by_desc('id')->find_array();
        $val = ORM::for_table('tbl_assets')->sum('cost') ?: 0;
        api_out(true, 'Daftar aset dimuat', [
            'summary' => ['total_items' => count($assets), 'total_value' => (float)$val],
            'assets'  => $assets
        ]);
        break;

    case 'asset_save':
    case 'admin_asset_save':
        require_admin_session($api_secret);
        ensure_custom_tables();
        $name = trim(_post('name'));
        if (empty($name)) api_out(false, 'Nama aset wajib diisi.', null, 400);
        $a = ORM::for_table('tbl_assets')->create();
        $a->name             = $name;
        $a->category         = trim(_post('category') ?: 'ODP/ODC');
        $a->serial_number    = trim(_post('serial_number'));
        $a->cost             = (float)(_post('cost') ?: 0);
        $a->coordinates      = trim(_post('coordinates'));
        $a->condition_status = _post('condition_status') ?: 'Good';
        $a->purchase_date    = date('Y-m-d');
        $a->save();
        api_out(true, 'Aset ' . $name . ' berhasil disimpan.', $a->as_array());
        break;

    case 'asset_delete':
    case 'admin_asset_delete':
        require_admin_session($api_secret);
        ensure_custom_tables();
        $a = ORM::for_table('tbl_assets')->find_one((int)_req('id'));
        if ($a) $a->delete();
        api_out(true, 'Aset berhasil dihapus.');
        break;

    case 'pg_action':
    case 'admin_payment_gateways':
    case 'admin_pg_action':
        $admin = require_admin_session($api_secret);
        $sub = trim(_req('sub_action') ?: ($action === 'admin_payment_gateways' ? 'list' : 'check'));

        if ($sub === 'list') {
            $status = _req('status');
            $q = ORM::for_table('tbl_payment_gateway');
            if ($status !== '' && $status !== null && strtolower((string)$status) !== 'all') {
                $q->where('status', (int)$status);
            }
            $rows = $q->order_by_desc('id')->limit(60)->find_many();
            $items = [];
            foreach ($rows as $r) {
                if ((int)$r['status'] === 1) verify_already_recharged($r);
                $items[] = parse_qris_trx($r, $config);
            }
            api_out(true, 'Daftar transaksi Payment Gateway', ['items' => $items]);
        }

        $trx_id = (int)_req('id');
        $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
        if (!$trx) api_out(false, 'Data tagihan tidak ditemukan.', null, 404);
        $cust = ORM::for_table('tbl_customers')->where('username', $trx['username'])->find_one();

        if ($sub === 'cancel') {
            if ((int)$trx['status'] === 2) api_out(false, 'Tagihan sudah LUNAS.', null, 400);
            $trx->status = 4;
            $trx->save();
            api_out(true, 'Tagihan berhasil dibatalkan.', parse_qris_trx($trx, $config));
        }

        if ($sub === 'approve_manual') {
            if (verify_already_recharged($trx)) {
                api_out(true, 'Tagihan sudah LUNAS sebelumnya.', parse_qris_trx($trx, $config));
            }
            if ($cust && class_exists('Package') && Package::rechargeUser($cust['id'], $trx['routers'], $trx['plan_id'], $trx['gateway'], 'Manual Approve (' . $admin['username'] . ')')) {
                $trx->status = 2;
                $trx->paid_date = date('Y-m-d H:i:s');
                $trx->save();
                api_out(true, 'Tagihan berhasil dilunasi & paket aktif!', parse_qris_trx($trx, $config));
            }
            api_out(false, 'Gagal mengaktifkan paket ke Router.', null, 500);
        }

        if (!verify_already_recharged($trx) && (int)$trx['status'] === 1 && !empty($trx['gateway']) && $cust) {
            $gw_file = 'system/paymentgateway/' . $trx['gateway'] . '.php';
            if (file_exists($gw_file)) {
                include_once $gw_file;
                $status_fn = $trx['gateway'] . '_get_status';
                if (function_exists($status_fn)) {
                    $GLOBALS['API_INTERCEPT_MODE']   = 'check_payment';
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = $trx['id'];
                    @$status_fn($trx, $cust);
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                    $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
                }
            }
        }
        $is_paid = verify_already_recharged($trx);
        api_out(true, $is_paid ? 'Pembayaran LUNAS!' : 'Pembayaran belum diterima.', [
            'paid' => $is_paid,
            'bill' => parse_qris_trx($trx, $config)
        ]);
        break;

    case 'webview_sso':
    case 'admin_webview_sso':
        $token = get_req_token();
        $target = trim(_get('redirect') ?: _post('redirect') ?: 'dashboard');

        // Pastikan cookie sesi PHP berlaku di root domain '/' (bukan terkunci di subfolder '/system')
        if (session_status() === PHP_SESSION_ACTIVE) {
            setcookie(session_name(), session_id(), 0, '/');
        }

        if (strpos($token, 'c.') === 0) {
            $cust = require_customer_session($api_secret);
            $_SESSION['uid'] = $cust['id'];
            if (class_exists('User') && method_exists('User', 'setCookie')) {
                @User::setCookie($cust['id']);
            }
            if ($target === 'dashboard') $target = 'home';
        } else {
            $adm = require_admin_session($api_secret);
            $_SESSION['aid'] = $adm['id'];
            if (class_exists('Admin') && method_exists('Admin', 'setCookie')) {
                @Admin::setCookie($adm['id']);
            }
        }

        // Hapus imbuhan '/system' dari APP_URL jika dipanggil lewat /system/api.php
        $clean_root_url = preg_replace('#/system/?$#i', '', rtrim(APP_URL, '/'));

        if (ob_get_length()) ob_clean();
        header('Location: ' . $clean_root_url . '/?_route=' . ltrim($target, '/'));
        exit();

    // =========================================================================
    // 9. ENDPOINT APK PELANGGAN (KOMPATIBILITAS PENUH)
    // =========================================================================
    case 'login':
        $username = _post('username');
        $password = _post('password');
        $user = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
        if (!$user || !check_user_password($password, $user['password'])) {
            api_out(false, 'Username atau Password salah.', null, 401);
        }
        api_out(true, 'Login berhasil', [
            'token'        => generate_customer_token($user, $api_secret),
            'native_token' => create_native_token('c', $user['id'], $api_secret)
        ]);
        break;

    case 'dashboard':
        $user = require_customer_session($api_secret);
        $recharges = ORM::for_table('tbl_user_recharges')->where('customer_id', $user['id'])->order_by_desc('id')->find_array();
        $unpaid_rows = ORM::for_table('tbl_payment_gateway')->where('username', $user['username'])->where('status', 1)->order_by_desc('id')->find_many();
        $unpaid_bills = [];
        foreach ($unpaid_rows as $ur) {
            if (!verify_already_recharged($ur)) $unpaid_bills[] = parse_qris_trx($ur, $config);
        }
        api_out(true, 'Data beranda dimuat', [
            'company' => [
                'name' => $config['CompanyName'] ?? 'Arneta.ID', 'phone' => $config['phone'] ?? '',
                'address' => $config['address'] ?? '', 'currency' => $config['currency_code'] ?? 'Rp',
                'enable_balance' => ($config['enable_balance'] ?? 'yes') === 'yes'
            ],
            'customer' => [
                'id' => (int)$user['id'], 'username' => $user['username'], 'fullname' => $user['fullname'],
                'phonenumber' => $user['phonenumber'], 'email' => $user['email'], 'address' => $user['address'],
                'balance' => (float)$user['balance'], 'service_type' => $user['service_type'] ?: 'Others',
                'auto_renewal' => (int)$user['auto_renewal']
            ],
            'payment_gateways' => get_active_gateways($config),
            'active_packages'  => $recharges,
            'unpaid_bills'     => $unpaid_bills,
            'unpaid_bill'      => $unpaid_bills[0] ?? null
        ]);
        break;

    case 'plans':
        $user = require_customer_session($api_secret);
        $cust_type = strtoupper(trim($user['service_type'] ?: 'OTHERS'));
        $plans = ORM::for_table('tbl_plans')->where('enabled', 1)->order_by_asc('price')->find_array();
        $internet_plans = []; $balance_plans = [];
        foreach ($plans as $p) {
            $p_type = strtoupper(trim($p['type']));
            $item = [
                'id' => (int)$p['id'], 'name_plan' => $p['name_plan'], 'price' => (float)$p['price'],
                'type' => ($p_type === 'PPPOE') ? 'PPPoE' : $p['type'], 'prepaid' => $p['prepaid'],
                'validity' => $p['validity'] . ' ' . $p['validity_unit'], 'routers' => $p['routers']
            ];
            if ($p_type === 'BALANCE') { $balance_plans[] = $item; continue; }
            if ($cust_type === $p_type || ($cust_type !== 'HOTSPOT' && $cust_type !== 'PPPOE')) {
                $internet_plans[] = $item;
            }
        }
        api_out(true, 'Daftar paket dimuat', [
            'internet_plans' => $internet_plans, 'balance_plans' => $balance_plans,
            'payment_gateways' => get_active_gateways($config)
        ]);
        break;

    case 'transactions':
        $user = require_customer_session($api_secret);
        api_out(true, 'Riwayat transaksi', ORM::for_table('tbl_transactions')->where('username', $user['username'])->order_by_desc('id')->limit(30)->find_array());
        break;

    case 'toggle_renewal':
        $user = require_customer_session($api_secret);
        $user->auto_renewal = ($user->auto_renewal == 1) ? 0 : 1;
        $user->save();
        api_out(true, 'Status perpanjangan otomatis diubah.', ['auto_renewal' => (int)$user->auto_renewal]);
        break;

    case 'redeem_voucher':
        $user = require_customer_session($api_secret);
        $code = trim(_post('code'));
        $v = ORM::for_table('tbl_voucher')->where('code', $code)->where('status', 0)->find_one();
        if (!$v) api_out(false, 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);
        if (class_exists('Package') && Package::rechargeUser($user['id'], $v['routers'], $v['id_plan'], 'Voucher', $code)) {
            $v->status = '1'; $v->user = $user['username']; $v->save();
            api_out(true, 'Voucher berhasil diaktifkan!');
        }
        api_out(false, 'Gagal mengaktifkan voucher.', null, 500);
        break;

    case 'buy':
    case 'qris_buy':
        $user = require_customer_session($api_secret);
        $plan = ORM::for_table('tbl_plans')->find_one((int)_post('plan_id'));
        $gateway_choice = trim(_post('gateway') ?: _post('method'));
        if (!$plan) api_out(false, 'Paket tidak ditemukan.', null, 404);

        if ($gateway_choice === 'balance') {
            if ((float)$user['balance'] < (float)$plan['price']) api_out(false, 'Saldo tidak mencukupi.', null, 400);
            if (class_exists('Package') && Package::rechargeUser($user['id'], $plan['routers'], $plan['id'], 'Customer', 'Balance')) {
                if (class_exists('Balance')) Balance::min($user['id'], $plan['price']);
                else { $user->balance = (float)$user->balance - (float)$plan['price']; $user->save(); }
                api_out(true, 'Paket berhasil diaktifkan menggunakan Saldo!');
            }
            api_out(false, 'Gagal mengaktifkan paket.', null, 500);
        }

        $existing = ORM::for_table('tbl_payment_gateway')->where('username', $user['username'])->where('status', 1)->find_one();
        if ($existing && !verify_already_recharged($existing)) {
            api_out(false, 'Masih ada tagihan yang belum dibayar.', parse_qris_trx($existing, $config), 400);
        }

        $active_gws = array_filter(array_map('trim', explode(',', $config['payment_gateway'] ?? 'viaqris')));
        $gateway = (!empty($gateway_choice) && in_array($gateway_choice, $active_gws)) ? $gateway_choice : ($active_gws[0] ?? 'viaqris');

        $d = ORM::for_table('tbl_payment_gateway')->create();
        $d->username = $user['username']; $d->gateway = $gateway; $d->plan_id = $plan['id'];
        $d->plan_name = $plan['name_plan']; $d->routers_id = 0; $d->routers = $plan['routers'];
        $d->price = $plan['price']; $d->created_date = date('Y-m-d H:i:s'); $d->status = 1;
        $d->save();

        $gw_file = 'system/paymentgateway/' . $gateway . '.php';
        if (file_exists($gw_file)) {
            include_once $gw_file;
            $fn = $gateway . '_create_transaction';
            if (function_exists($fn)) {
                $GLOBALS['API_INTERCEPT_MODE'] = 'buy'; $GLOBALS['API_INTERCEPT_TRX_ID'] = $d->id();
                $fn($d, $user);
                $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                $updated = ORM::for_table('tbl_payment_gateway')->find_one($d->id());
                api_out(true, 'Tagihan berhasil dibuat.', parse_qris_trx($updated, $config));
            }
        }
        api_out(true, 'Tagihan berhasil dibuat.', parse_qris_trx($d, $config));
        break;

    case 'payment_detail':
    case 'check_payment':
    case 'qris_status':
        $user = require_customer_session($api_secret);
        $trx_id = (int)_req('id');
        $trx = ORM::for_table('tbl_payment_gateway')->where('username', $user['username'])->find_one($trx_id);
        if (!$trx) api_out(false, 'Tagihan tidak ditemukan.', null, 404);
        if ($action === 'check_payment' && !verify_already_recharged($trx) && (int)$trx['status'] === 1 && !empty($trx['gateway'])) {
            $gw_file = 'system/paymentgateway/' . $trx['gateway'] . '.php';
            if (file_exists($gw_file)) {
                include_once $gw_file;
                $status_fn = $trx['gateway'] . '_get_status';
                if (function_exists($status_fn)) {
                    $GLOBALS['API_INTERCEPT_MODE'] = 'check_payment'; $GLOBALS['API_INTERCEPT_TRX_ID'] = $trx['id'];
                    @$status_fn($trx, $user);
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                    $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
                }
            }
        }
        $is_paid = verify_already_recharged($trx);
        api_out(true, $is_paid ? 'Pembayaran diterima!' : 'Menunggu pembayaran.', [
            'paid' => $is_paid, 'bill' => parse_qris_trx($trx, $config)
        ]);
        break;

    case 'cancel_payment':
    case 'qris_cancel':
        $user = require_customer_session($api_secret);
        $trx = ORM::for_table('tbl_payment_gateway')->where('username', $user['username'])->where('status', 1)->find_one((int)_req('id'));
        if ($trx && !verify_already_recharged($trx)) { $trx->status = 4; $trx->save(); }
        api_out(true, 'Tagihan berhasil dibatalkan.');
        break;

    default:
        api_out(true, 'PHPNuxBill Complete API v10 Ready (MikroTik Monitor & SuperAdmin Integrated)');
        break;
}
```

### Bagian yang Dapat Diedit (Opsional)
Jika Anda perlu mengkustomisasi pengaturan API (misalnya mengubah fallback "Secret Key"), Anda dipersilakan mengedit bagian ini pada kode yang sudah disalin di atas:

```php
// Baris ~19
$api_secret = !empty($config['tawkto']) ? $config['tawkto'] : 'Arneta_WiFi_Secret_Key_2026_#99';
```
Anda dapat mengubah `'Arneta_WiFi_Secret_Key_2026_#99'` ke kunci kustom Anda sendiri (disarankan demi keamanan jika integrasi token Anda membutuhkannya).

---
*Generated by AI*
