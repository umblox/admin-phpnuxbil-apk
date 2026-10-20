# PHPNuxBill APK by AI

Aplikasi ini adalah versi APK (Android) dari **PHPNuxBill**, yang di-generate dan dibuat sepenuhnya oleh AI. Repository ini memuat aplikasi klien, sedangkan untuk menghubungkan aplikasi ini dengan server PHPNuxBill Anda, Anda perlu menyiapkan API di sisi server.

## ðŸš€ Cara Pemasangan API di Server (Server-Side)

Agar aplikasi APK ini dapat berkomunikasi dengan database dan sistem PHPNuxBill Anda, Anda harus menambahkan endpoint API kustom.

1. Buka file manager server Anda (melalui cPanel, aaPanel, atau SSH).
2. Masuk ke dalam direktori instalasi PHPNuxBill Anda.
3. Navigasikan ke path berikut:
   **`/system/controllers/`**
4. Buat file baru dengan nama **`apiv1.php`**.
5. Copy dan Paste seluruh kode di bawah ini ke dalam file `apiv1.php` tersebut.

### ðŸ“ Kode `apiv1.php`

```php
<?php
/**
 * Custom Customer & Admin REST API v6 for PHPNuxBill (Arneta.ID Edition)
 * - 100% Backward Compatible dengan Customer API v5 (Anti-Double Recharge & Native QRIS)
 * - Full Admin Dashboard API (Tahap 1: Auth, Dashboard, Pelanggan, Layanan/Prepaid, Voucher, Kupon, Data Usage)
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

$api_secret = !empty($config['tawkto']) ? $config['tawkto'] : 'Arneta_WiFi_Secret_Key_2026_#99';
$action = isset($routes[1]) ? $routes[1] : 'index';

function api_response($status, $message, $data = null, $code = 200) {
    if (ob_get_length()) {
        ob_clean();
    }
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'status'  => $status,
        'message' => $message,
        'data'    => $data
    ], JSON_UNESCAPED_SLASHES);
    exit();
}

function generate_customer_token($user, $secret) {
    $payload = $user['id'] . '|' . $user['username'];
    $sig = hash_hmac('sha256', $payload . '|' . md5($user['password']), $secret);
    return base64_encode($payload . '|' . $sig);
}

function generate_admin_token($admin, $secret) {
    $payload = 'ADMIN|' . $admin['id'] . '|' . $admin['username'];
    $sig = hash_hmac('sha256', $payload . '|' . md5($admin['password']), $secret);
    return base64_encode($payload . '|' . $sig);
}

function get_bearer_token() {
    $headers = function_exists('apache_request_headers') ? apache_request_headers() : [];
    $auth = '';
    if (isset($headers['Authorization'])) {
        $auth = $headers['Authorization'];
    } elseif (isset($headers['authorization'])) {
        $auth = $headers['authorization'];
    } elseif (isset($_SERVER['HTTP_AUTHORIZATION'])) {
        $auth = $_SERVER['HTTP_AUTHORIZATION'];
    } elseif (isset($_GET['token'])) {
        return $_GET['token'];
    }
    if (preg_match('/Bearer\s(\S+)/', $auth, $matches)) {
        return $matches[1];
    }
    return '';
}

function authenticate_customer($secret) {
    $token = get_bearer_token();
    if (empty($token)) {
        api_response(false, 'Token otorisasi tidak ditemukan.', null, 401);
    }

    $decoded = base64_decode($token);
    $parts = explode('|', $decoded);
    if (count($parts) !== 3) {
        api_response(false, 'Format token pelanggan tidak valid.', null, 401);
    }

    list($id, $username, $sig) = $parts;
    $user = ORM::for_table('tbl_customers')->find_one($id);
    if (!$user || $user['username'] !== $username) {
        api_response(false, 'Akun tidak ditemukan.', null, 401);
    }

    $expected_sig = hash_hmac('sha256', $id . '|' . $username . '|' . md5($user['password']), $secret);
    if (!hash_equals($expected_sig, $sig)) {
        api_response(false, 'Sesi berakhir, silakan login ulang.', null, 401);
    }

    return $user;
}

function authenticate_admin($secret, $allowed_roles = []) {
    $token = get_bearer_token();
    if (empty($token)) {
        api_response(false, 'Token otorisasi Admin tidak ditemukan.', null, 401);
    }

    $decoded = base64_decode($token);
    $parts = explode('|', $decoded);
    if (count($parts) !== 4 || $parts[0] !== 'ADMIN') {
        api_response(false, 'Format token Admin tidak valid.', null, 401);
    }

    list($prefix, $id, $username, $sig) = $parts;
    $admin = ORM::for_table('tbl_users')->find_one($id);
    if (!$admin || $admin['username'] !== $username) {
        api_response(false, 'Akun Admin tidak ditemukan.', null, 401);
    }

    if (!empty($admin['status']) && $admin['status'] !== 'Active') {
        api_response(false, 'Akun Admin sedang dinonaktifkan (' . $admin['status'] . ').', null, 403);
    }

    $expected_sig = hash_hmac('sha256', 'ADMIN|' . $id . '|' . $username . '|' . md5($admin['password']), $secret);
    if (!hash_equals($expected_sig, $sig)) {
        api_response(false, 'Sesi Admin telah berakhir, silakan login ulang.', null, 401);
    }

    if (!empty($allowed_roles) && !in_array($admin['user_type'], $allowed_roles)) {
        api_response(false, 'Akses ditolak untuk level ' . $admin['user_type'], null, 403);
    }

    // Set session aid agar fungsi internal PHPNuxBill berjalan mulus
    $_SESSION['aid'] = $admin['id'];
    return $admin;
}

function verify_admin_password($input_password, $hashed_password) {
    if (class_exists('Password') && method_exists('Password', '_verify')) {
        if (Password::_verify($input_password, $hashed_password)) {
            return true;
        }
    }
    if (password_verify($input_password, $hashed_password)) {
        return true;
    }
    if (sha1($input_password) === $hashed_password || md5($input_password) === $hashed_password || $input_password === $hashed_password) {
        return true;
    }
    return false;
}

function hash_admin_password($plain_password) {
    if (class_exists('Password') && method_exists('Password', '_crypt')) {
        return Password::_crypt($plain_password);
    }
    return password_hash($plain_password, PASSWORD_DEFAULT);
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
    } catch (Exception $e) {
        // Abaikan jika gagal tulis log
    }
}

function ensure_extra_admin_tables() {
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
            `category` varchar(60) NOT NULL DEFAULT 'Router/ODP',
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
    } catch (Exception $e) {
        // Abaikan jika user DB tidak punya akses CREATE
    }
}

function format_bytes_readable($bytes) {
    $b = (float)$bytes;
    if ($b <= 0) return '0 B';
    $units = ['B', 'KB', 'MB', 'GB', 'TB'];
    $i = (int)floor(log($b, 1024));
    $i = max(0, min($i, count($units) - 1));
    return round($b / pow(1024, $i), 2) . ' ' . $units[$i];
}

function get_mikrotik_ros_client($ip_port, $user, $pass) {
    if (!class_exists('PEAR2\Net\RouterOS\Client')) {
        if (class_exists('Mikrotik') && method_exists('Mikrotik', 'getClient')) {
            return Mikrotik::getClient($ip_port, $user, $pass);
        }
        return null;
    }
    try {
        $parts = explode(':', $ip_port);
        $ip = $parts[0];
        $port = isset($parts[1]) ? (int)$parts[1] : 8728;
        return new PEAR2\Net\RouterOS\Client($ip, $user, $pass, $port, false, 4);
    } catch (Exception $e) {
        return null;
    }
}

function get_active_gateways($config) {
    $raw = !empty($config['payment_gateway']) ? $config['payment_gateway'] : 'viaqris';
    $parts = array_filter(array_map('trim', explode(',', $raw)));
    if (empty($parts)) {
        $parts = ['viaqris'];
    }

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
            'name'        => isset($labels[$key]) ? $labels[$key]['name'] : strtoupper($gw),
            'description' => isset($labels[$key]) ? $labels[$key]['desc'] : 'Pembayaran Otomatis via ' . ucfirst($gw),
        ];
    }
    return $result;
}

function qris_crc16($str) {
    $crc = 0xFFFF;
    $strlen = strlen($str);
    for ($c = 0; $c < $strlen; $c++) {
        $crc ^= ord($str[$c]) << 8;
        for ($i = 0; $i < 8; $i++) {
            if ($crc & 0x8000) {
                $crc = (($crc << 1) ^ 0x1021) & 0xFFFF;
            } else {
                $crc = ($crc << 1) & 0xFFFF;
            }
        }
    }
    return strtoupper(str_pad(dechex($crc), 4, '0', STR_PAD_LEFT));
}

function convert_static_to_dynamic_qris($qris_raw, $amount) {
    $qris = trim($qris_raw);
    if (strpos($qris, '000201') !== 0 || strpos($qris, '5802ID') === false) {
        return $qris;
    }
    $amt_str = (string)((int)$amount);
    if ($amt_str <= 0) return $qris;

    $crc_pos = strrpos($qris, '6304');
    if ($crc_pos !== false && $crc_pos >= strlen($qris) - 8) {
        $qris_body = substr($qris, 0, $crc_pos);
    } else {
        return $qris;
    }

    $qris_body = preg_replace('/^000201010211/', '000201010212', $qris_body, 1);

    $parts = explode('5802ID', $qris_body, 2);
    if (count($parts) === 2) {
        $tag54 = '54' . str_pad(strlen($amt_str), 2, '0', STR_PAD_LEFT) . $amt_str;
        if (strpos($parts[0], $tag54) === false) {
            $qris_body = $parts[0] . $tag54 . '5802ID' . $parts[1];
        }
    }

    $qris_body .= '6304';
    return $qris_body . qris_crc16($qris_body);
}

function extract_qris_details($unpaid, $config, $allow_loopback = true) {
    $total_amount = (float)$unpaid['price'];
    $qris_string  = '';
    $qris_image   = '';

    foreach (['pg_request', 'pg_paid_response'] as $col) {
        if (empty($unpaid[$col])) continue;
        $raw = trim($unpaid[$col]);

        if (preg_match('/(000201[0-9A-Za-z\.\-\s\/\+]{40,600})/', $raw, $m)) {
            $qris_string = trim($m[1]);
        }
        if (preg_match('/(data:image\/[a-zA-Z0-9\+\/\=;:,]+)/', $raw, $m)) {
            $qris_image = trim($m[1]);
        }

        $json = json_decode($raw, true);
        if (is_array($json)) {
            array_walk_recursive($json, function($val, $key) use (&$total_amount, &$qris_string, &$qris_image) {
                $k = strtolower((string)$key);
                $v = trim((string)$val);
                if (in_array($k, ['amount', 'total', 'nominal', 'total_amount', 'amount_unique', 'bayar']) && is_numeric($v) && (float)$v > 0) {
                    $total_amount = (float)$v;
                }
                if (strpos($v, '000201') === 0 && strlen($v) > 40) {
                    $qris_string = $v;
                } elseif (strpos($v, 'data:image/') === 0) {
                    $qris_image = $v;
                } elseif ((strpos($k, 'qr') !== false || strpos($k, 'img') !== false || strpos($k, 'image') !== false) && strpos($v, 'http') === 0) {
                    $qris_image = $v;
                }
            });
        }
    }

    if (!empty($unpaid['pg_url_payment'])) {
        $parsed_url = parse_url($unpaid['pg_url_payment']);
        if (!empty($parsed_url['query'])) {
            parse_str($parsed_url['query'], $qparams);
            foreach ($qparams as $qk => $qv) {
                if (is_string($qv)) {
                    $decoded_qv = urldecode($qv);
                    if (strpos($decoded_qv, '000201') === 0) {
                        $qris_string = $decoded_qv;
                    } elseif (strpos($decoded_qv, 'data:image/') === 0) {
                        $qris_image = $decoded_qv;
                    }
                }
            }
        }
    }

    foreach ($config as $k => $v) {
        if (!is_string($v)) continue;
        $v_trim = trim($v);
        if (empty($qris_string) && strpos($v_trim, '000201') === 0) {
            $qris_string = convert_static_to_dynamic_qris($v_trim, $total_amount);
        }
        if (empty($qris_image) && stripos($k, 'qris') !== false && (stripos($v_trim, 'http') === 0 || preg_match('/\.(png|jpg|jpeg|webp)$/i', $v_trim))) {
            $qris_image = (stripos($v_trim, 'http') === 0) ? $v_trim : rtrim(APP_URL, '/') . '/' . ltrim($v_trim, '/');
        }
    }

    if (empty($qris_string) && empty($qris_image)) {
        $scan_files = array_merge(
            glob('system/paymentgateway/*qris*.php') ?: [],
            glob('system/plugin/*.php') ?: [],
            glob('ui/theme/*/*payment*.tpl') ?: [],
            glob('system/plugin/ui/*.tpl') ?: []
        );
        foreach ($scan_files as $sf) {
            $content = @file_get_contents($sf);
            if (!$content) continue;
            if (empty($qris_string) && preg_match('/["\'](000201[0-9A-Za-z\.\-\s\/\+]{40,500})["\']/', $content, $m)) {
                $qris_string = convert_static_to_dynamic_qris($m[1], $total_amount);
            }
            if (empty($qris_image) && preg_match('/(data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9\+\/\=]+)/', $content, $m)) {
                $qris_image = $m[1];
            }
        }
    }

    if ($allow_loopback && (int)$unpaid['status'] === 1 && empty($qris_string) && empty($qris_image) && !empty($unpaid['pg_url_payment']) && function_exists('curl_init')) {
        $cust = ORM::for_table('tbl_customers')->where('username', $unpaid['username'])->find_one();
        if ($cust) {
            $_SESSION['uid'] = $cust['id'];
        }
        $sess_name = session_name();
        $sess_id   = session_id();
        session_write_close();

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $unpaid['pg_url_payment']);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 4);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        if ($sess_id) {
            curl_setopt($ch, CURLOPT_COOKIE, $sess_name . '=' . $sess_id);
        }
        $html = @curl_exec($ch);
        curl_close($ch);
        @session_start();

        if (!empty($html)) {
            if (preg_match('/(000201[0-9A-Za-z\.\-\s\/\+]{40,600})/', $html, $m)) {
                $qris_string = trim($m[1]);
            }
            if (preg_match('/(data:image\/[a-zA-Z0-9\+\/\=;:,]+)/', $html, $m)) {
                $qris_image = trim($m[1]);
            } elseif (preg_match_all('/<img[^>]+src=["\']([^"\']+)["\']/i', $html, $matches)) {
                foreach ($matches[1] as $img_src) {
                    if (stripos($img_src, 'logo') === false && stripos($img_src, 'avatar') === false && stripos($img_src, 'user') === false) {
                        $qris_image = (stripos($img_src, 'http') === 0 || stripos($img_src, 'data:') === 0)
                            ? $img_src
                            : rtrim(APP_URL, '/') . '/' . ltrim($img_src, '/');
                        break;
                    }
                }
            }
        }
    }

    if (!empty($qris_string)) {
        $qris_string = convert_static_to_dynamic_qris($qris_string, $total_amount);
        if (empty($qris_image)) {
            $qris_image = 'https://api.qrserver.com/v1/create-qr-code/?size=600x600&margin=20&data=' . urlencode($qris_string);
        }
    }

    return [
        'amount'      => $total_amount,
        'qris_string' => $qris_string,
        'qris_image'  => $qris_image
    ];
}

function format_unpaid_data($unpaid, $token, $config, $allow_loopback = true) {
    if (!$unpaid) return null;

    $extracted = extract_qris_details($unpaid, $config, $allow_loopback);

    $status_int = (int)$unpaid['status'];
    $status_label = 'MENUNGGU PEMBAYARAN';
    if ($status_int === 2) $status_label = 'LUNAS';
    elseif ($status_int === 3) $status_label = 'GAGAL / EXPIRED';
    elseif ($status_int === 4) $status_label = 'DIBATALKAN';

    return [
        'id'              => (int)$unpaid['id'],
        'username'        => $unpaid['username'],
        'plan_id'         => (int)$unpaid['plan_id'],
        'plan_name'       => $unpaid['plan_name'],
        'price'           => $extracted['amount'],
        'base_price'      => (float)$unpaid['price'],
        'gateway'         => $unpaid['gateway'],
        'payment_method'  => $unpaid['payment_method'] ?: 'QRIS',
        'payment_channel' => $unpaid['payment_channel'] ?: strtoupper($unpaid['gateway']),
        'pg_url_payment'  => $unpaid['pg_url_payment'],
        'qris_string'     => $extracted['qris_string'],
        'qris_image'      => $extracted['qris_image'],
        'status'          => $status_int,
        'status_label'    => $status_label,
        'expired_date'    => $unpaid['expired_date'] ?: '-',
        'created_date'    => $unpaid['created_date'] ?: '-',
        'paid_date'       => $unpaid['paid_date'] ?: '-',
        'trx_invoice'     => $unpaid['trx_invoice'] ?: ('INV-' . $unpaid['id'])
    ];
}

function verify_if_already_recharged(&$trx) {
    if (!$trx) return false;
    if ((int)$trx['status'] === 2) return true;

    if (!empty($trx['created_date'])) {
        $created_ts = strtotime($trx['created_date']);
        $recent_trx = ORM::for_table('tbl_transactions')
            ->where('username', $trx['username'])
            ->where('plan_name', $trx['plan_name'])
            ->order_by_desc('id')
            ->find_one();

        if ($recent_trx && !empty($recent_trx['recharged_on'])) {
            $rech_ts = strtotime($recent_trx['recharged_on'] . ' ' . ($recent_trx['recharged_time'] ?: '00:00:00'));
            if ($rech_ts >= ($created_ts - 5)) {
                $trx->status = 2;
                if (empty($trx->paid_date)) {
                    $trx->paid_date = date('Y-m-d H:i:s');
                }
                if (empty($trx->trx_invoice) && !empty($recent_trx['invoice'])) {
                    $trx->trx_invoice = $recent_trx['invoice'];
                }
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
        $token  = $GLOBALS['API_INTERCEPT_TOKEN'] ?? '';
        $cfg    = $GLOBALS['config'] ?? [];
        $GLOBALS['API_INTERCEPT_TRX_ID'] = null;

        if (ob_get_length()) {
            ob_clean();
        }
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
            $is_paid = verify_if_already_recharged($trx);
            $msg = $is_paid
                ? 'Pembayaran telah diterima! Paket/Saldo Anda sudah aktif.'
                : ($notify ?: 'Pembayaran belum masuk. Pastikan nominal transfer sesuai hingga 3 digit terakhir.');
            echo json_encode([
                'status'  => true,
                'message' => $msg,
                'data'    => [
                    'paid' => $is_paid,
                    'bill' => format_unpaid_data($trx, $token, $cfg, false)
                ]
            ], JSON_UNESCAPED_SLASHES);
            return;
        }

        if ($trx && (!empty($trx['pg_url_payment']) || $ntype !== 'e')) {
            echo json_encode([
                'status'  => true,
                'message' => $notify ?: 'Tagihan pembayaran berhasil dibuat.',
                'data'    => format_unpaid_data($trx, $token, $cfg, true)
            ], JSON_UNESCAPED_SLASHES);
        } else {
            if ($trx && empty($trx['pg_url_payment'])) {
                $trx->delete();
            }
            echo json_encode([
                'status'  => false,
                'message' => $notify ?: 'Gagal menghubungi Payment Gateway.',
                'data'    => null
            ], JSON_UNESCAPED_SLASHES);
        }
    }
});

$raw_input = json_decode(file_get_contents('php://input'), true);
if (is_array($raw_input)) {
    $_POST = array_merge($_POST, $raw_input);
}

switch ($action) {
    // =========================================================================
    // BAGIAN A: ENDPOINT DASHBOARD PELANGGAN (UTUH 100% DARI VERSI SEBELUMNYA)
    // =========================================================================
    case 'login':
        $username = _post('username');
        $password = _post('password');

        if (empty($username) || empty($password)) {
            api_response(false, 'Username dan Password wajib diisi.', null, 400);
        }

        $user = ORM::for_table('tbl_customers')
            ->where('username', $username)
            ->find_one();

        if (!$user || $user['password'] !== $password) {
            api_response(false, 'Username atau Password salah.', null, 401);
        }

        if ($user['status'] !== 'Active' && !empty($user['status'])) {
            api_response(false, 'Status akun Anda: ' . $user['status'], null, 403);
        }

        $token = generate_customer_token($user, $api_secret);
        api_response(true, 'Login berhasil', [
            'token' => $token
        ]);
        break;

    case 'dashboard':
        $user = authenticate_customer($api_secret);
        $token = get_bearer_token();

        $recharges = ORM::for_table('tbl_user_recharges')
            ->where('customer_id', $user['id'])
            ->order_by_desc('id')
            ->find_array();

        $unpaid_rows = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->order_by_desc('id')
            ->find_many();

        $unpaid_bills = [];
        foreach ($unpaid_rows as $ur) {
            if (verify_if_already_recharged($ur)) {
                continue;
            }
            $unpaid_bills[] = format_unpaid_data($ur, $token, $config, false);
        }

        api_response(true, 'Data beranda dimuat', [
            'company' => [
                'name'           => $config['CompanyName'] ?? 'Arneta.ID',
                'phone'          => $config['phone'] ?? '',
                'address'        => $config['address'] ?? '',
                'currency'       => $config['currency_code'] ?? 'Rp',
                'enable_balance' => ($config['enable_balance'] ?? 'yes') === 'yes'
            ],
            'customer' => [
                'id'           => (int)$user['id'],
                'username'     => $user['username'],
                'fullname'     => $user['fullname'],
                'phonenumber'  => $user['phonenumber'],
                'email'        => $user['email'],
                'address'      => $user['address'],
                'balance'      => (float)$user['balance'],
                'service_type' => $user['service_type'] ?: 'Others',
                'auto_renewal' => (int)$user['auto_renewal']
            ],
            'payment_gateways' => get_active_gateways($config),
            'active_packages'  => $recharges,
            'unpaid_bills'     => $unpaid_bills,
            'unpaid_bill'      => !empty($unpaid_bills) ? $unpaid_bills[0] : null
        ]);
        break;

    case 'plans':
        $user = authenticate_customer($api_secret);
        $cust_type = strtoupper(trim($user['service_type'] ?: 'OTHERS'));

        $plans = ORM::for_table('tbl_plans')
            ->where('enabled', 1)
            ->order_by_asc('price')
            ->find_array();

        $internet_plans = [];
        $balance_plans  = [];
        $included_ids   = [];

        foreach ($plans as $p) {
            $p_type  = strtoupper(trim($p['type']));
            $prepaid = strtolower(trim($p['prepaid'] ?? 'yes'));

            $display_type = ($p_type === 'PPPOE') ? 'PPPoE' : (($p_type === 'HOTSPOT') ? 'Hotspot' : $p['type']);
            if ($prepaid !== 'yes' && $p_type === 'PPPOE') {
                $display_type = 'PPPoE • Pascabayar';
            }

            $item = [
                'id'        => (int)$p['id'],
                'name_plan' => $p['name_plan'],
                'price'     => (float)$p['price'],
                'type'      => $display_type,
                'prepaid'   => $p['prepaid'],
                'validity'  => $p['validity'] . ' ' . $p['validity_unit'],
                'routers'   => $p['routers']
            ];

            if ($p_type === 'BALANCE') {
                if ($prepaid === 'yes') {
                    $balance_plans[] = $item;
                }
                continue;
            }

            if ($p_type === 'HOTSPOT' && $prepaid !== 'yes') {
                continue;
            }

            if ($cust_type === 'HOTSPOT' && $p_type === 'HOTSPOT') {
                $internet_plans[] = $item;
                $included_ids[] = (int)$p['id'];
            } elseif ($cust_type === 'PPPOE' && $p_type === 'PPPOE') {
                $internet_plans[] = $item;
                $included_ids[] = (int)$p['id'];
            } elseif ($cust_type !== 'HOTSPOT' && $cust_type !== 'PPPOE') {
                if ($p_type === 'HOTSPOT' || $p_type === 'PPPOE') {
                    $internet_plans[] = $item;
                    $included_ids[] = (int)$p['id'];
                }
            }
        }

        $user_recharges = ORM::for_table('tbl_user_recharges')
            ->where('customer_id', $user['id'])
            ->find_many();

        foreach ($user_recharges as $ur) {
            $pid = (int)$ur['plan_id'];
            if ($pid > 0 && !in_array($pid, $included_ids)) {
                $assigned_plan = ORM::for_table('tbl_plans')->find_one($pid);
                if ($assigned_plan && strtoupper(trim($assigned_plan['type'])) !== 'BALANCE') {
                    array_unshift($internet_plans, [
                        'id'        => (int)$assigned_plan['id'],
                        'name_plan' => $assigned_plan['name_plan'],
                        'price'     => (float)$assigned_plan['price'],
                        'type'      => (strtoupper(trim($assigned_plan['type'])) === 'PPPOE') ? 'PPPoE' : $assigned_plan['type'],
                        'prepaid'   => $assigned_plan['prepaid'],
                        'validity'  => $assigned_plan['validity'] . ' ' . $assigned_plan['validity_unit'],
                        'routers'   => $assigned_plan['routers']
                    ]);
                    $included_ids[] = $pid;
                }
            }
        }

        api_response(true, 'Daftar paket berhasil dimuat', [
            'internet_plans'   => $internet_plans,
            'balance_plans'    => $balance_plans,
            'payment_gateways' => get_active_gateways($config)
        ]);
        break;

    case 'transactions':
        $user = authenticate_customer($api_secret);
        $trx = ORM::for_table('tbl_transactions')
            ->where('username', $user['username'])
            ->order_by_desc('id')
            ->limit(30)
            ->find_array();

        api_response(true, 'Riwayat transaksi', $trx);
        break;

    case 'toggle_renewal':
        $user = authenticate_customer($api_secret);
        $user->auto_renewal = ($user->auto_renewal == 1) ? 0 : 1;
        $user->save();
        api_response(true, 'Status perpanjangan otomatis diubah.', [
            'auto_renewal' => (int)$user->auto_renewal
        ]);
        break;

    case 'redeem_voucher':
        $user = authenticate_customer($api_secret);
        $code = trim(_post('code'));
        if (empty($code)) {
            api_response(false, 'Kode voucher tidak boleh kosong.', null, 400);
        }

        $v = ORM::for_table('tbl_voucher')
            ->where('code', $code)
            ->where('status', 0)
            ->find_one();

        if (!$v) {
            api_response(false, 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);
        }

        if (class_exists('Package') && Package::rechargeUser($user['id'], $v['routers'], $v['id_plan'], 'Voucher', $code)) {
            $v->status = "1";
            $v->user = $user['username'];
            $v->save();
            api_response(true, 'Voucher berhasil diaktifkan!');
        } else {
            api_response(false, 'Gagal mengaktifkan voucher.', null, 500);
        }
        break;

    case 'buy':
        $user = authenticate_customer($api_secret);
        $token = get_bearer_token();
        $plan_id = (int)_post('plan_id');
        $gateway_choice = trim(_post('gateway') ?: _post('method'));

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);

        if (!$plan) {
            api_response(false, 'Paket tidak ditemukan atau tidak tersedia.', null, 404);
        }

        if ($gateway_choice === 'balance') {
            if (strtoupper(trim($plan['type'])) === 'BALANCE') {
                api_response(false, 'Topup saldo tidak dapat dibayar menggunakan saldo.', null, 400);
            }
            if ((float)$user['balance'] < (float)$plan['price']) {
                api_response(false, 'Saldo Anda tidak mencukupi untuk membeli paket ini.', null, 400);
            }

            if (class_exists('Package') && Package::rechargeUser($user['id'], $plan['routers'], $plan['id'], 'Customer', 'Balance')) {
                if (class_exists('Balance')) {
                    Balance::min($user['id'], $plan['price']);
                } else {
                    $user->balance = (float)$user->balance - (float)$plan['price'];
                    $user->save();
                }
                api_response(true, 'Paket ' . $plan['name_plan'] . ' berhasil diaktifkan menggunakan Saldo!');
            } else {
                api_response(false, 'Gagal mengaktifkan paket ke router.', null, 500);
            }
        }

        $existing = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->find_one();

        if ($existing) {
            if (!verify_if_already_recharged($existing)) {
                api_response(
                    false,
                    'Anda masih memiliki tagihan yang belum dibayar. Silakan selesaikan atau batalkan terlebih dahulu.',
                    format_unpaid_data($existing, $token, $config, true),
                    400
                );
            }
        }

        $active_gws = array_filter(array_map('trim', explode(',', $config['payment_gateway'] ?? 'viaqris')));
        $gateway = $gateway_choice;
        if (empty($gateway) || !in_array($gateway, $active_gws)) {
            $gateway = !empty($active_gws) ? $active_gws[0] : 'viaqris';
        }

        $router_id = 0;
        $router_name = $plan['routers'];
        if (strtoupper(trim($plan['type'])) === 'BALANCE') {
            $router_name = 'balance';
        } elseif ($plan['routers'] !== 'radius') {
            $r = ORM::for_table('tbl_routers')->where('name', $plan['routers'])->find_one();
            if ($r) $router_id = (int)$r['id'];
        }

        $d = ORM::for_table('tbl_payment_gateway')->create();
        $d->username = $user['username'];
        $d->gateway = $gateway;
        $d->plan_id = $plan['id'];
        $d->plan_name = $plan['name_plan'];
        $d->routers_id = $router_id;
        $d->routers = $router_name;
        $d->price = $plan['price'];
        $d->created_date = date('Y-m-d H:i:s');
        $d->status = 1;
        $d->save();

        $gw_file = 'system/paymentgateway/' . $gateway . '.php';
        if (file_exists($gw_file)) {
            include_once $gw_file;
            $fn = $gateway . '_create_transaction';
            if (function_exists($fn)) {
                $GLOBALS['API_INTERCEPT_MODE']   = 'buy';
                $GLOBALS['API_INTERCEPT_TRX_ID'] = $d->id();
                $GLOBALS['API_INTERCEPT_TOKEN']  = $token;
                $fn($d, $user);
                $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                $updated = ORM::for_table('tbl_payment_gateway')->find_one($d->id());
                api_response(true, 'Tagihan berhasil dibuat.', format_unpaid_data($updated, $token, $config, true));
            }
        }

        api_response(true, 'Tagihan berhasil dibuat.', format_unpaid_data($d, $token, $config, true));
        break;

    case 'payment_detail':
        $user = authenticate_customer($api_secret);
        $token = get_bearer_token();
        $trx_id = (int)(_post('id') ?: _get('id'));
        $need_qr = (bool)(_post('need_qr') ?: _get('need_qr'));

        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->find_one($trx_id);

        if (!$trx) {
            api_response(false, 'Data tagihan tidak ditemukan.', null, 404);
        }

        $is_paid = verify_if_already_recharged($trx);
        $msg = $is_paid
            ? 'Pembayaran telah diterima! Paket/Saldo Anda sudah aktif.'
            : 'Menunggu pembayaran QRIS.';

        api_response(true, $msg, [
            'paid' => $is_paid,
            'bill' => format_unpaid_data($trx, $token, $config, $need_qr)
        ]);
        break;

    case 'check_payment':
        $user = authenticate_customer($api_secret);
        $token = get_bearer_token();
        $trx_id = (int)(_post('id') ?: _get('id'));

        $lock_file = sys_get_temp_dir() . '/arneta_paylock_' . $trx_id . '.lock';
        $lock_fp = @fopen($lock_file, 'c+');
        if ($lock_fp) {
            if (!@flock($lock_fp, LOCK_EX | LOCK_NB)) {
                @flock($lock_fp, LOCK_EX);
                $trx_after = ORM::for_table('tbl_payment_gateway')
                    ->where('username', $user['username'])
                    ->find_one($trx_id);
                @flock($lock_fp, LOCK_UN);
                @fclose($lock_fp);

                $is_paid_after = verify_if_already_recharged($trx_after);
                api_response(true, $is_paid_after ? 'Pembayaran berhasil diverifikasi!' : 'Sedang memeriksa status pembayaran...', [
                    'paid' => $is_paid_after,
                    'bill' => format_unpaid_data($trx_after, $token, $config, false)
                ]);
            }
            $GLOBALS['API_LOCK_HANDLE'] = $lock_fp;
        }

        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->find_one($trx_id);

        if (!$trx) {
            api_response(false, 'Data tagihan tidak ditemukan.', null, 404);
        }

        if (!verify_if_already_recharged($trx) && (int)$trx['status'] === 1 && !empty($trx['gateway'])) {
            $gw_file = 'system/paymentgateway/' . $trx['gateway'] . '.php';
            if (file_exists($gw_file)) {
                include_once $gw_file;
                $status_fn = $trx['gateway'] . '_get_status';
                if (function_exists($status_fn)) {
                    $GLOBALS['API_INTERCEPT_MODE']   = 'check_payment';
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = $trx['id'];
                    $GLOBALS['API_INTERCEPT_TOKEN']  = $token;
                    @$status_fn($trx, $user);
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                    $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
                }
            }
        }

        $is_paid = verify_if_already_recharged($trx);
        $msg = $is_paid
            ? 'Pembayaran telah diterima! Paket/Saldo Anda sudah aktif.'
            : 'Pembayaran belum masuk. Pastikan Anda mentransfer tepat sesuai nominal.';

        api_response(true, $msg, [
            'paid' => $is_paid,
            'bill' => format_unpaid_data($trx, $token, $config, false)
        ]);
        break;

    case 'cancel_payment':
        $user = authenticate_customer($api_secret);
        $trx_id = (int)_post('id');

        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->find_one($trx_id);

        if ($trx && !verify_if_already_recharged($trx)) {
            $trx->status = 4;
            $trx->save();
            api_response(true, 'Tagihan berhasil dibatalkan.');
        }
        api_response(true, 'Tagihan sudah diselesaikan atau dibatalkan.');
        break;


    // =========================================================================
    // BAGIAN B: ENDPOINT DASHBOARD ADMIN - TAHAP 1
    // (Auth, Dashboard, Pelanggan, Layanan/Prepaid, Voucher, Kupon, Data Usage)
    // =========================================================================

    case 'admin_login':
        $username = trim(_post('username'));
        $password = _post('password');

        if (empty($username) || empty($password)) {
            api_response(false, 'Username dan Password Admin wajib diisi.', null, 400);
        }

        $admin = ORM::for_table('tbl_users')
            ->where('username', $username)
            ->find_one();

        if (!$admin || !verify_admin_password($password, $admin['password'])) {
            api_response(false, 'Username atau Password Admin salah.', null, 401);
        }

        if (!empty($admin['status']) && $admin['status'] !== 'Active') {
            api_response(false, 'Akun Admin sedang dinonaktifkan (' . $admin['status'] . ').', null, 403);
        }

        $admin->last_login = date('Y-m-d H:i:s');
        $admin->save();

        write_admin_log($admin, $admin['username'] . ' Login via Aplikasi Android Admin');

        $token = generate_admin_token($admin, $api_secret);
        api_response(true, 'Selamat datang, ' . ($admin['fullname'] ?: $admin['username']), [
            'token' => $token,
            'admin' => [
                'id'        => (int)$admin['id'],
                'username'  => $admin['username'],
                'fullname'  => $admin['fullname'],
                'user_type' => $admin['user_type'],
                'phone'     => $admin['phone'] ?? '',
                'email'     => $admin['email'] ?? '',
                'city'      => $admin['city'] ?? '',
                'root'      => (int)($admin['root'] ?? 0)
            ],
            'company' => [
                'name'     => $config['CompanyName'] ?? 'Arneta.ID',
                'currency' => $config['currency_code'] ?? 'Rp.',
                'phone'    => $config['phone'] ?? '',
                'address'  => $config['address'] ?? ''
            ]
        ]);
        break;

    case 'admin_profile':
        $admin = authenticate_admin($api_secret);
        if ($_SERVER['REQUEST_METHOD'] === 'POST' && !empty(_post('fullname'))) {
            $admin->fullname = trim(_post('fullname'));
            if (_post('username') && _post('username') !== $admin->username) {
                $exists = ORM::for_table('tbl_users')->where('username', trim(_post('username')))->find_one();
                if ($exists) {
                    api_response(false, 'Username sudah digunakan oleh pengguna lain.', null, 400);
                }
                $admin->username = trim(_post('username'));
            }
            if (_post('phone') !== '') $admin->phone = trim(_post('phone'));
            if (_post('email') !== '') $admin->email = trim(_post('email'));
            if (!empty(_post('new_password'))) {
                $admin->password = hash_admin_password(_post('new_password'));
            }
            $admin->save();
            write_admin_log($admin, 'Memperbarui profil Admin via Aplikasi');
            api_response(true, 'Profil berhasil diperbarui.', [
                'token' => generate_admin_token($admin, $api_secret),
                'admin' => [
                    'id'        => (int)$admin['id'],
                    'username'  => $admin['username'],
                    'fullname'  => $admin['fullname'],
                    'user_type' => $admin['user_type'],
                    'phone'     => $admin['phone'] ?? '',
                    'email'     => $admin['email'] ?? ''
                ]
            ]);
        }

        api_response(true, 'Profil Admin', [
            'id'        => (int)$admin['id'],
            'username'  => $admin['username'],
            'fullname'  => $admin['fullname'],
            'user_type' => $admin['user_type'],
            'phone'     => $admin['phone'] ?? '',
            'email'     => $admin['email'] ?? '',
            'last_login'=> $admin['last_login'] ?? '-'
        ]);
        break;

    case 'admin_dashboard':
        $admin = authenticate_admin($api_secret);
        $today = date('Y-m-d');
        $month_prefix = date('Y-m-');
        $current_year = date('Y');
        $start_month_date = date('Y-m-01');

        // 1. Pendapatan Hari Ini (sesuai logika asli PHPNuxBill dashboard.php)
        $income_today = ORM::for_table('tbl_transactions')
            ->where('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_equal('type', 'Balance')
            ->sum('price') ?: 0;

        // 2. Pendapatan Bulan Ini
        $income_month = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_month_date)
            ->where_lte('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_equal('type', 'Balance')
            ->sum('price') ?: 0;

        // 3. Aktif / Kedaluwarsa (seperti kartu oranye 19/7 di screenshot)
        $active_recharges = ORM::for_table('tbl_user_recharges')->where('status', 'on')->count();
        $expired_recharges = ORM::for_table('tbl_user_recharges')->where('status', 'off')->count();

        // 4. Total Pelanggan & Total Saldo Pelanggan
        $total_customers = ORM::for_table('tbl_customers')->count();
        $total_customer_balance = ORM::for_table('tbl_customers')->sum('balance') ?: 0;

        // 5. Grafik Pendaftaran Pelanggan Perbulan (Jan - Des tahun berjalan)
        $monthly_registered = [];
        $month_labels = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        for ($m = 1; $m <= 12; $m++) {
            $ym = $current_year . '-' . str_pad($m, 2, '0', STR_PAD_LEFT);
            $cnt = ORM::for_table('tbl_customers')
                ->where_like('created_at', $ym . '%')
                ->count();
            $monthly_registered[] = [
                'month' => $month_labels[$m - 1],
                'count' => (int)$cnt
            ];
        }

        // 6. Grafik Total Penjualan Perbulan (Jan - Des tahun berjalan)
        $monthly_sales = [];
        for ($m = 1; $m <= 12; $m++) {
            $ym = $current_year . '-' . str_pad($m, 2, '0', STR_PAD_LEFT);
            $sum = ORM::for_table('tbl_transactions')
                ->where_like('recharged_on', $ym . '%')
                ->where_not_equal('method', 'Customer - Balance')
                ->where_not_equal('type', 'Balance')
                ->sum('price') ?: 0;
            $monthly_sales[] = [
                'month' => $month_labels[$m - 1],
                'total' => (float)$sum
            ];
        }

        // 7. Stok Voucher & Penggunaan per Paket
        $plans = ORM::for_table('tbl_plans')->where('enabled', 1)->find_array();
        $stocks = [];
        $total_unused_vouchers = 0;
        $total_used_vouchers = 0;
        foreach ($plans as $p) {
            if (strtoupper($p['type']) === 'BALANCE') continue;
            $unused = ORM::for_table('tbl_voucher')->where('id_plan', $p['id'])->where('status', 0)->count();
            $used   = ORM::for_table('tbl_voucher')->where('id_plan', $p['id'])->where('status', 1)->count();
            $active_plan_users = ORM::for_table('tbl_user_recharges')->where('plan_id', $p['id'])->where('status', 'on')->count();
            $total_unused_vouchers += $unused;
            $total_used_vouchers += $used;
            $stocks[] = [
                'plan_id'      => (int)$p['id'],
                'name_plan'    => $p['name_plan'],
                'type'         => $p['type'],
                'unused'       => (int)$unused,
                'used'         => (int)$used,
                'active_users' => (int)$active_plan_users
            ];
        }

        // 8. Transaksi Terakhir & Antrean QRIS Unpaid
        $recent_trx = ORM::for_table('tbl_transactions')
            ->order_by_desc('id')
            ->limit(8)
            ->find_array();

        $unpaid_qris_count = ORM::for_table('tbl_payment_gateway')
            ->where('status', 1)
            ->count();

        // 9. Pelanggan yang akan segera expired / baru expired
        $expiring_list = ORM::for_table('tbl_user_recharges')
            ->where_lte('expiration', date('Y-m-d', strtotime('+2 days')))
            ->order_by_desc('expiration')
            ->limit(10)
            ->find_array();

        api_response(true, 'Dashboard Admin berhasil dimuat', [
            'company' => [
                'name'        => $config['CompanyName'] ?? 'Arneta.ID',
                'currency'    => $config['currency_code'] ?? 'Rp.',
                'date_Period' => date('01 M Y') . ' / ' . date('d M Y')
            ],
            'cards' => [
                'income_today'           => (float)$income_today,
                'income_month'           => (float)$income_month,
                'active_users'           => (int)$active_recharges,
                'expired_users'          => (int)$expired_recharges,
                'active_expired_label'   => $active_recharges . '/' . $expired_recharges,
                'total_customers'        => (int)$total_customers,
                'total_customer_balance' => (float)$total_customer_balance,
                'unused_vouchers'        => (int)$total_unused_vouchers,
                'used_vouchers'          => (int)$total_used_vouchers,
                'unpaid_qris_count'      => (int)$unpaid_qris_count
            ],
            'charts' => [
                'monthly_registered' => $monthly_registered,
                'monthly_sales'      => $monthly_sales
            ],
            'stocks'              => $stocks,
            'recent_transactions' => $recent_trx,
            'expiring_users'      => $expiring_list
        ]);
        break;

    case 'admin_customers':
        $admin = authenticate_admin($api_secret);
        $search = trim(_get('search') ?: _post('search'));
        $service_type = trim(_get('service_type') ?: _post('service_type'));
        $status = trim(_get('status') ?: _post('status'));
        $page = max(1, (int)(_get('page') ?: _post('page') ?: 1));
        $limit = max(10, min(100, (int)(_get('limit') ?: _post('limit') ?: 25)));
        $offset = ($page - 1) * $limit;

        $q = ORM::for_table('tbl_customers');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR fullname LIKE ? OR phonenumber LIKE ? OR email LIKE ? OR pppoe_username LIKE ? OR address LIKE ?)', [$like, $like, $like, $like, $like, $like]);
        }
        if ($service_type !== '' && strtolower($service_type) !== 'all') {
            $q->where('service_type', $service_type);
        }
        if ($status !== '' && strtolower($status) !== 'all') {
            $q->where('status', $status);
        }

        $total_q = clone $q;
        $total = $total_q->count();

        $rows = $q->order_by_desc('id')
            ->limit($limit)
            ->offset($offset)
            ->find_array();

        // Perkaya dengan status paket aktif masing-masing pelanggan
        $customer_ids = array_column($rows, 'id');
        $recharges_map = [];
        if (!empty($customer_ids)) {
            $rech_rows = ORM::for_table('tbl_user_recharges')
                ->where_in('customer_id', $customer_ids)
                ->order_by_desc('id')
                ->find_array();
            foreach ($rech_rows as $r) {
                $cid = (int)$r['customer_id'];
                if (!isset($recharges_map[$cid])) {
                    $recharges_map[$cid] = $r;
                }
            }
        }

        $customers = [];
        foreach ($rows as $row) {
            $cid = (int)$row['id'];
            $rech = $recharges_map[$cid] ?? null;
            $customers[] = [
                'id'             => $cid,
                'username'       => $row['username'],
                'fullname'       => $row['fullname'],
                'phonenumber'    => $row['phonenumber'],
                'email'          => $row['email'],
                'address'        => $row['address'],
                'balance'        => (float)$row['balance'],
                'service_type'   => $row['service_type'] ?: 'Hotspot',
                'account_type'   => $row['account_type'] ?? 'Personal',
                'status'         => $row['status'] ?: 'Active',
                'auto_renewal'   => (int)$row['auto_renewal'],
                'pppoe_username' => $row['pppoe_username'] ?? '',
                'pppoe_password' => $row['pppoe_password'] ?? '',
                'pppoe_ip'       => $row['pppoe_ip'] ?? '',
                'coordinates'    => $row['coordinates'] ?? '',
                'created_at'     => $row['created_at'] ?? '',
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

        api_response(true, 'Daftar pelanggan berhasil dimuat', [
            'total'     => (int)$total,
            'page'      => $page,
            'limit'     => $limit,
            'customers' => $customers
        ]);
        break;

    case 'admin_customer_detail':
        $admin = authenticate_admin($api_secret);
        $id = (int)(_get('id') ?: _post('id'));
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        $recharges = ORM::for_table('tbl_user_recharges')
            ->where('customer_id', $cust['id'])
            ->order_by_desc('id')
            ->find_array();

        $transactions = ORM::for_table('tbl_transactions')
            ->where('username', $cust['username'])
            ->order_by_desc('id')
            ->limit(30)
            ->find_array();

        $custom_fields = [];
        try {
            $custom_fields = ORM::for_table('tbl_customers_fields')
                ->where('customer_id', $cust['id'])
                ->find_array();
        } catch (Exception $e) {}

        api_response(true, 'Detail pelanggan dimuat', [
            'customer'      => $cust->as_array(),
            'recharges'     => $recharges,
            'transactions'  => $transactions,
            'custom_fields' => $custom_fields
        ]);
        break;

    case 'admin_customer_save':
        // Mendukung Add Customer & Edit Customer
        $admin = authenticate_admin($api_secret);
        $id = (int)_post('id');
        $username = trim(_post('username'));
        $fullname = trim(_post('fullname'));
        $password = _post('password');

        if (empty($username) || empty($fullname)) {
            api_response(false, 'Username dan Nama Lengkap wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $cust = ORM::for_table('tbl_customers')->find_one($id);
            if (!$cust) {
                api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
            }
            $dup = ORM::for_table('tbl_customers')
                ->where('username', $username)
                ->where_not_equal('id', $id)
                ->find_one();
            if ($dup) {
                api_response(false, 'Username sudah digunakan oleh pelanggan lain.', null, 400);
            }
        } else {
            $dup = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
            if ($dup) {
                api_response(false, 'Username sudah terdaftar.', null, 400);
            }
            $cust = ORM::for_table('tbl_customers')->create();
            $cust->created_by = $admin['id'];
            $cust->created_at = date('Y-m-d H:i:s');
            $cust->balance = (float)(_post('balance') ?: 0);
        }

        $cust->username       = $username;
        $cust->fullname       = $fullname;
        if (!empty($password)) {
            $cust->password   = $password;
        } elseif ($id === 0) {
            $cust->password   = '123456';
        }
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
        $cust->city           = trim(_post('city'));
        $cust->subdistrict    = trim(_post('subdistrict'));
        $cust->ward           = trim(_post('ward'));
        $cust->save();

        write_admin_log($admin, ($id > 0 ? 'Mengubah' : 'Menambah') . ' pelanggan: ' . $username);

        api_response(true, ($id > 0 ? 'Data pelanggan berhasil diperbarui.' : 'Pelanggan baru berhasil ditambahkan.'), [
            'customer' => $cust->as_array()
        ]);
        break;

    case 'admin_customer_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        $uname = $cust['username'];
        ORM::for_table('tbl_user_recharges')->where('customer_id', $id)->delete_many();
        try {
            ORM::for_table('tbl_customers_fields')->where('customer_id', $id)->delete_many();
        } catch (Exception $e) {}
        $cust->delete();

        write_admin_log($admin, 'Menghapus pelanggan: ' . $uname);
        api_response(true, 'Pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_customer_sync':
        $admin = authenticate_admin($api_secret);
        $id = (int)_post('id');
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        $recharges = ORM::for_table('tbl_user_recharges')
            ->where('customer_id', $id)
            ->where('status', 'on')
            ->find_many();

        $synced = 0;
        foreach ($recharges as $r) {
            $plan = ORM::for_table('tbl_plans')->find_one($r['plan_id']);
            if ($plan && class_exists('Package')) {
                $device_file = class_exists('Package') && method_exists('Package', 'getDevice')
                    ? Package::getDevice($plan)
                    : ('system/devices/' . $plan['device'] . '.php');
                if (file_exists($device_file)) {
                    require_once $device_file;
                    $d_class = $plan['device'];
                    if (class_exists($d_class)) {
                        $dev = new $d_class();
                        if (method_exists($dev, 'add_customer')) {
                            $dev->add_customer($cust, $plan);
                            $synced++;
                        }
                    }
                }
            }
        }

        api_response(true, $synced > 0
            ? 'Berhasil menyinkronkan ' . $synced . ' layanan aktif milik ' . $cust['username'] . ' ke Router.'
            : 'Tidak ada paket berstatus ON untuk disinkronkan pada pelanggan ini.');
        break;

    case 'admin_prepaid_list':
        // Menu: Layanan -> Pelanggan Aktif
        $admin = authenticate_admin($api_secret);
        $search = trim(_get('search') ?: _post('search'));
        $status = trim(_get('status') ?: _post('status'));
        $type   = trim(_get('type') ?: _post('type'));
        $router = trim(_get('router') ?: _post('router'));
        $page   = max(1, (int)(_get('page') ?: _post('page') ?: 1));
        $limit  = max(10, min(100, (int)(_get('limit') ?: _post('limit') ?: 25)));
        $offset = ($page - 1) * $limit;

        $q = ORM::for_table('tbl_user_recharges');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR namebp LIKE ? OR routers LIKE ?)', [$like, $like, $like]);
        }
        if ($status !== '' && strtolower($status) !== 'all') {
            $q->where('status', $status);
        }
        if ($type !== '' && strtolower($type) !== 'all') {
            $q->where('type', $type);
        }
        if ($router !== '' && strtolower($router) !== 'all') {
            $q->where('routers', $router);
        }

        $total_q = clone $q;
        $total = $total_q->count();

        $rows = $q->order_by_desc('id')
            ->limit($limit)
            ->offset($offset)
            ->find_array();

        $cust_ids = array_column($rows, 'customer_id');
        $cust_map = [];
        if (!empty($cust_ids)) {
            $c_rows = ORM::for_table('tbl_customers')->where_in('id', $cust_ids)->find_array();
            foreach ($c_rows as $c) {
                $cust_map[(int)$c['id']] = $c;
            }
        }

        $items = [];
        foreach ($rows as $r) {
            $c = $cust_map[(int)$r['customer_id']] ?? null;
            $r['fullname']    = $c['fullname'] ?? $r['username'];
            $r['phonenumber'] = $c['phonenumber'] ?? '';
            $items[] = $r;
        }

        api_response(true, 'Daftar pelanggan aktif berhasil dimuat', [
            'total' => (int)$total,
            'page'  => $page,
            'limit' => $limit,
            'items' => $items
        ]);
        break;

    case 'admin_prepaid_recharge':
        // Menu: Layanan -> Isi Ulang Paket
        $admin = authenticate_admin($api_secret);
        $customer_id = (int)_post('customer_id');
        $plan_id     = (int)_post('plan_id');
        $using       = trim(_post('using') ?: 'Cash');

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) {
            api_response(false, 'Paket tidak ditemukan.', null, 404);
        }

        $router_name = _post('routers') ?: $plan['routers'];

        if (class_exists('Package') && Package::rechargeUser($cust['id'], $router_name, $plan['id'], $admin['fullname'] ?: 'Admin', $using)) {
            $last_trx = ORM::for_table('tbl_transactions')
                ->where('username', $cust['username'])
                ->order_by_desc('id')
                ->find_one();

            write_admin_log($admin, 'Isi ulang paket ' . $plan['name_plan'] . ' untuk ' . $cust['username']);
            api_response(true, 'Paket ' . $plan['name_plan'] . ' berhasil diaktifkan untuk ' . $cust['fullname'] . '!', [
                'invoice' => $last_trx ? $last_trx->as_array() : null
            ]);
        } else {
            $err = isset($_SESSION['notify']) ? strip_tags($_SESSION['notify']) : 'Gagal menghubungi Router MikroTik.';
            unset($_SESSION['notify'], $_SESSION['ntype']);
            api_response(false, $err, null, 500);
        }
        break;

    case 'admin_prepaid_edit':
        // Edit Masa Aktif / Paket pada Pelanggan Aktif
        $admin = authenticate_admin($api_secret);
        $id = (int)_post('id');
        $ur = ORM::for_table('tbl_user_recharges')->find_one($id);
        if (!$ur) {
            api_response(false, 'Data langganan tidak ditemukan.', null, 404);
        }

        if (!empty(_post('expiration'))) {
            $ur->expiration = trim(_post('expiration'));
        }
        if (!empty(_post('time'))) {
            $ur->time = trim(_post('time'));
        }
        if (!empty(_post('status'))) {
            $ur->status = trim(_post('status'));
        }
        if (!empty(_post('plan_id')) && (int)_post('plan_id') !== (int)$ur->plan_id) {
            $new_plan = ORM::for_table('tbl_plans')->find_one((int)_post('plan_id'));
            if ($new_plan) {
                $ur->plan_id = $new_plan['id'];
                $ur->namebp  = $new_plan['name_plan'];
            }
        }
        $ur->save();

        write_admin_log($admin, 'Mengedit masa aktif paket pelanggan ' . $ur['username'] . ' ke ' . $ur['expiration']);
        api_response(true, 'Masa aktif pelanggan berhasil diperbarui.', $ur->as_array());
        break;

    case 'admin_prepaid_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $ur = ORM::for_table('tbl_user_recharges')->find_one($id);
        if (!$ur) {
            api_response(false, 'Data paket pelanggan tidak ditemukan.', null, 404);
        }

        $plan = ORM::for_table('tbl_plans')->find_one($ur['plan_id']);
        $cust = ORM::for_table('tbl_customers')->find_one($ur['customer_id']);
        if ($plan && $cust && class_exists('Package') && method_exists('Package', 'getDevice')) {
            $device_file = Package::getDevice($plan);
            if (file_exists($device_file)) {
                require_once $device_file;
                $d_class = $plan['device'];
                if (class_exists($d_class)) {
                    $dev = new $d_class();
                    if (method_exists($dev, 'remove_customer')) {
                        @$dev->remove_customer($cust, $plan);
                    }
                }
            }
        }

        $uname = $ur['username'];
        $ur->delete();
        write_admin_log($admin, 'Menghapus paket aktif milik ' . $uname);
        api_response(true, 'Paket pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_deposit':
        // Menu: Layanan -> Isi Ulang Saldo
        $admin = authenticate_admin($api_secret);
        $customer_id = (int)_post('customer_id');
        $plan_id     = (int)_post('plan_id');
        $custom_amt  = (float)_post('amount');
        $note        = trim(_post('note') ?: 'Deposit via Admin');

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        if ($plan_id > 0) {
            $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
            if (!$plan) {
                api_response(false, 'Paket Saldo tidak ditemukan.', null, 404);
            }
            if (class_exists('Package') && Package::rechargeUser($cust['id'], 'balance', $plan['id'], $admin['fullname'] ?: 'Admin', 'Deposit')) {
                $cust_updated = ORM::for_table('tbl_customers')->find_one($customer_id);
                write_admin_log($admin, 'Isi ulang saldo ' . $plan['name_plan'] . ' untuk ' . $cust['username']);
                api_response(true, 'Saldo ' . $cust['fullname'] . ' berhasil ditambah!', [
                    'balance' => (float)$cust_updated['balance']
                ]);
            }
        }

        if ($custom_amt != 0) {
            $old_bal = (float)$cust['balance'];
            $new_bal = $old_bal + $custom_amt;
            $cust->balance = $new_bal;
            $cust->save();

            // Catat di tbl_transactions agar masuk laporan
            $t = ORM::for_table('tbl_transactions')->create();
            $t->invoice        = 'DEP-' . date('ymdHis');
            $t->username       = $cust['username'];
            $t->plan_name      = $note;
            $t->price          = $custom_amt;
            $t->recharged_on   = date('Y-m-d');
            $t->recharged_time = date('H:i:s');
            $t->expiration     = date('Y-m-d');
            $t->time           = date('H:i:s');
            $t->method         = ($admin['fullname'] ?: 'Admin') . ' - Deposit';
            $t->routers        = 'balance';
            $t->type           = 'Balance';
            $t->save();

            write_admin_log($admin, 'Topup saldo manual Rp ' . number_format($custom_amt, 0, ',', '.') . ' ke ' . $cust['username']);
            api_response(true, 'Saldo berhasil diperbarui menjadi Rp ' . number_format($new_bal, 0, ',', '.'), [
                'balance' => $new_bal
            ]);
        }

        api_response(false, 'Pilih paket saldo atau masukkan nominal saldo.', null, 400);
        break;

    case 'admin_vouchers':
        // Menu: Layanan -> Voucher & Recharge Cards
        $admin = authenticate_admin($api_secret);
        $search  = trim(_get('search') ?: _post('search'));
        $router  = trim(_get('router') ?: _post('router'));
        $plan_id = (int)(_get('plan_id') ?: _post('plan_id'));
        $status  = _get('status') !== '' ? _get('status') : _post('status');
        $page    = max(1, (int)(_get('page') ?: _post('page') ?: 1));
        $limit   = max(10, min(200, (int)(_get('limit') ?: _post('limit') ?: 30)));
        $offset  = ($page - 1) * $limit;

        $q = ORM::for_table('tbl_voucher');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(code LIKE ? OR user LIKE ? OR routers LIKE ?)', [$like, $like, $like]);
        }
        if ($router !== '' && strtolower($router) !== 'all') {
            $q->where('routers', $router);
        }
        if ($plan_id > 0) {
            $q->where('id_plan', $plan_id);
        }
        if ($status !== '' && $status !== null && strtolower((string)$status) !== 'all') {
            $q->where('status', (string)$status);
        }

        $total_q = clone $q;
        $total = $total_q->count();

        $rows = $q->order_by_desc('id')
            ->limit($limit)
            ->offset($offset)
            ->find_array();

        $plans_map = [];
        $all_plans = ORM::for_table('tbl_plans')->find_array();
        foreach ($all_plans as $p) {
            $plans_map[(int)$p['id']] = $p;
        }

        $vouchers = [];
        foreach ($rows as $r) {
            $p = $plans_map[(int)$r['id_plan']] ?? null;
            $r['name_plan'] = $p['name_plan'] ?? ('Plan #' . $r['id_plan']);
            $r['price']     = (float)($p['price'] ?? 0);
            $r['validity']  = $p ? ($p['validity'] . ' ' . $p['validity_unit']) : '-';
            $vouchers[] = $r;
        }

        api_response(true, 'Daftar voucher berhasil dimuat', [
            'total'    => (int)$total,
            'page'     => $page,
            'limit'    => $limit,
            'vouchers' => $vouchers
        ]);
        break;

    case 'admin_voucher_generate':
        $admin = authenticate_admin($api_secret);
        $type        = _post('type') ?: 'Hotspot';
        $routers     = _post('routers');
        $plan_id     = (int)_post('plan_id');
        $count       = max(1, min(500, (int)(_post('count') ?: 10)));
        $length_code = max(4, min(16, (int)(_post('length_code') ?: 8)));
        $prefix      = trim(_post('prefix'));
        $format      = _post('voucher_format') ?: 'up'; // up, low, rand, numbers

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) {
            api_response(false, 'Paket voucher tidak ditemukan.', null, 404);
        }
        if (empty($routers)) {
            $routers = $plan['routers'];
        }

        $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        if ($format === 'low') {
            $chars = 'abcdefghjkmnpqrstuvwxyz23456789';
        } elseif ($format === 'rand') {
            $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
        } elseif ($format === 'numbers') {
            $chars = '0123456789';
        }

        $generated = [];
        for ($i = 0; $i < $count; $i++) {
            $rand_str = '';
            $max_idx = strlen($chars) - 1;
            for ($j = 0; $j < $length_code; $j++) {
                $rand_str .= $chars[random_int(0, $max_idx)];
            }
            $code = $prefix . $rand_str;

            if (ORM::for_table('tbl_voucher')->where('code', $code)->find_one()) {
                continue;
            }

            $v = ORM::for_table('tbl_voucher')->create();
            $v->type         = $type;
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

        write_admin_log($admin, 'Membuat ' . count($generated) . ' voucher untuk paket ' . $plan['name_plan']);
        api_response(true, count($generated) . ' Voucher berhasil dibuat!', [
            'generated' => $generated
        ]);
        break;

    case 'admin_voucher_refill':
        // Menu: Layanan -> Isi Ulang Voucher
        $admin = authenticate_admin($api_secret);
        $customer_id = (int)_post('customer_id');
        $code        = trim(_post('code'));

        $cust = ORM::for_table('tbl_customers')->find_one($customer_id);
        if (!$cust) {
            api_response(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        $v = ORM::for_table('tbl_voucher')
            ->where('code', $code)
            ->where('status', 0)
            ->find_one();

        if (!$v) {
            api_response(false, 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);
        }

        if (class_exists('Package') && Package::rechargeUser($cust['id'], $v['routers'], $v['id_plan'], 'Voucher', $code)) {
            $v->status = '1';
            $v->user   = $cust['username'];
            $v->save();
            write_admin_log($admin, 'Mengaktifkan voucher ' . $code . ' untuk pelanggan ' . $cust['username']);
            api_response(true, 'Voucher ' . $code . ' berhasil diaktifkan ke akun ' . $cust['fullname'] . '!');
        } else {
            api_response(false, 'Gagal mengaktifkan voucher ke Router.', null, 500);
        }
        break;

    case 'admin_voucher_delete':
        $admin = authenticate_admin($api_secret);
        $id = (int)_post('id');
        $delete_used = (int)_post('delete_used');

        if ($delete_used === 1) {
            $cnt = ORM::for_table('tbl_voucher')->where('status', '1')->count();
            ORM::for_table('tbl_voucher')->where('status', '1')->delete_many();
            write_admin_log($admin, 'Menghapus massal ' . $cnt . ' voucher yang sudah terpakai');
            api_response(true, $cnt . ' voucher terpakai berhasil dibersihkan.');
        }

        $v = ORM::for_table('tbl_voucher')->find_one($id);
        if (!$v) {
            api_response(false, 'Voucher tidak ditemukan.', null, 404);
        }
        $v->delete();
        api_response(true, 'Voucher berhasil dihapus.');
        break;

    case 'admin_coupons':
        // Menu: Layanan -> Kupon
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();
        $coupons = ORM::for_table('tbl_coupons')->order_by_desc('id')->find_array();
        api_response(true, 'Daftar kupon dimuat', $coupons);
        break;

    case 'admin_coupon_save':
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();
        $id   = (int)_post('id');
        $code = strtoupper(trim(_post('code')));

        if (empty($code)) {
            api_response(false, 'Kode kupon wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $c = ORM::for_table('tbl_coupons')->find_one($id);
            if (!$c) api_response(false, 'Kupon tidak ditemukan.', null, 404);
        } else {
            $dup = ORM::for_table('tbl_coupons')->where('code', $code)->find_one();
            if ($dup) api_response(false, 'Kode kupon sudah ada.', null, 400);
            $c = ORM::for_table('tbl_coupons')->create();
        }

        $c->code         = $code;
        $c->type         = _post('type') === 'percent' ? 'percent' : 'fixed';
        $c->value        = (float)_post('value');
        $c->min_order    = (float)(_post('min_order') ?: 0);
        $c->max_discount = (float)(_post('max_discount') ?: 0);
        $c->max_usage    = (int)(_post('max_usage') ?: 100);
        $c->status       = _post('status') === 'inactive' ? 'inactive' : 'active';
        $c->expired_date = _post('expired_date') ?: date('Y-m-d', strtotime('+30 days'));
        $c->description  = trim(_post('description'));
        $c->save();

        write_admin_log($admin, 'Menyimpan kupon promo: ' . $code);
        api_response(true, 'Kupon ' . $code . ' berhasil disimpan.', $c->as_array());
        break;

    case 'admin_coupon_delete':
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();
        $id = (int)_post('id');
        $c = ORM::for_table('tbl_coupons')->find_one($id);
        if ($c) {
            $c->delete();
        }
        api_response(true, 'Kupon berhasil dihapus.');
        break;

    case 'admin_data_usage':
        // Menu: Layanan -> User Data Usage (Tarik Live dari MikroTik + Tabel Lokal)
        $admin = authenticate_admin($api_secret);
        $router_filter = trim(_get('router') ?: _post('router'));

        $routers_q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($router_filter !== '' && strtolower($router_filter) !== 'all') {
            $routers_q->where('name', $router_filter);
        }
        $routers = $routers_q->find_array();

        $usage_list = [];
        $total_rx = 0;
        $total_tx = 0;

        foreach ($routers as $r) {
            $client = get_mikrotik_ros_client($r['ip_address'], $r['username'], $r['password']);
            if (!$client) continue;

            try {
                // 1. Hotspot Active Users
                $hs_req = new PEAR2\Net\RouterOS\Request('/ip/hotspot/active/print');
                $hs_res = $client->sendSync($hs_req);
                foreach ($hs_res as $item) {
                    if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $user = $item->getProperty('user');
                        $rx   = (float)($item->getProperty('bytes-in') ?: 0);
                        $tx   = (float)($item->getProperty('bytes-out') ?: 0);
                        $total_rx += $rx;
                        $total_tx += $tx;
                        $usage_list[] = [
                            'username'    => $user,
                            'service'     => 'Hotspot',
                            'router'      => $r['name'],
                            'ip_address'  => $item->getProperty('address') ?: '-',
                            'mac_address' => $item->getProperty('mac-address') ?: '-',
                            'uptime'      => $item->getProperty('uptime') ?: '0s',
                            'upload_raw'  => $rx,
                            'download_raw'=> $tx,
                            'total_raw'   => $rx + $tx,
                            'upload'      => format_bytes_readable($rx),
                            'download'    => format_bytes_readable($tx),
                            'total'       => format_bytes_readable($rx + $tx),
                            'is_online'   => true
                        ];
                    }
                }

                // 2. Simple Queues (Untuk melacak pemakaian kuota PPPoE & Static)
                $q_req = new PEAR2\Net\RouterOS\Request('/queue/simple/print');
                $q_res = $client->sendSync($q_req);
                $ppp_active_map = [];

                $ppp_req = new PEAR2\Net\RouterOS\Request('/ppp/active/print');
                $ppp_res = $client->sendSync($ppp_req);
                foreach ($ppp_res as $p_item) {
                    if ($p_item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $pname = $p_item->getProperty('name');
                        $ppp_active_map[$pname] = [
                            'ip'     => $p_item->getProperty('address') ?: '-',
                            'mac'    => $p_item->getProperty('caller-id') ?: '-',
                            'uptime' => $p_item->getProperty('uptime') ?: '-'
                        ];
                    }
                }

                foreach ($q_res as $q_item) {
                    if ($q_item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $qname = $q_item->getProperty('name');
                        $clean_name = trim(str_replace(['<pppoe-', '<hotspot-', '>'], '', $qname));
                        $bytes_str = $q_item->getProperty('bytes') ?: '0/0';
                        $b_parts = explode('/', $bytes_str);
                        $rx = (float)($b_parts[0] ?? 0);
                        $tx = (float)($b_parts[1] ?? 0);
                        if ($rx == 0 && $tx == 0 && !isset($ppp_active_map[$clean_name])) continue;

                        $p_info = $ppp_active_map[$clean_name] ?? null;
                        $total_rx += $rx;
                        $total_tx += $tx;
                        $usage_list[] = [
                            'username'    => $clean_name,
                            'service'     => isset($ppp_active_map[$clean_name]) ? 'PPPoE' : 'Queue',
                            'router'      => $r['name'],
                            'ip_address'  => $p_info['ip'] ?? ($q_item->getProperty('target') ?: '-'),
                            'mac_address' => $p_info['mac'] ?? '-',
                            'uptime'      => $p_info['uptime'] ?? '-',
                            'upload_raw'  => $rx,
                            'download_raw'=> $tx,
                            'total_raw'   => $rx + $tx,
                            'upload'      => format_bytes_readable($rx),
                            'download'    => format_bytes_readable($tx),
                            'total'       => format_bytes_readable($rx + $tx),
                            'is_online'   => isset($ppp_active_map[$clean_name])
                        ];
                    }
                }
            } catch (Exception $e) {
                // Lanjut ke router berikutnya jika timeout
            }
        }

        // Urutkan dari pemakaian kuota terbesar
        usort($usage_list, function($a, $b) {
            return $b['total_raw'] <=> $a['total_raw'];
        });

        api_response(true, 'Data pemakaian kuota berhasil dimuat', [
            'summary' => [
                'total_users'    => count($usage_list),
                'total_upload'   => format_bytes_readable($total_rx),
                'total_download' => format_bytes_readable($total_tx),
                'total_traffic'  => format_bytes_readable($total_rx + $total_tx)
            ],
            'items' => $usage_list
        ]);
        break;

        // =========================================================================
    // BAGIAN C: ENDPOINT DASHBOARD ADMIN - TAHAP 2
    // (Paket, Bandwidth, Jaringan/MikroTik, Peta, Asset Manager, Laporan,
    //  Audit QRIS, Broadcast Pesan, Halaman Statis, Pengaturan & System Tools)
    // =========================================================================

    case 'admin_plans':
        // Menu: Paket Internet -> Hotspot / PPPoE / Balance
        $admin = authenticate_admin($api_secret);
        $type   = trim(_get('type') ?: _post('type'));
        $search = trim(_get('search') ?: _post('search'));
        $router = trim(_get('router') ?: _post('router'));

        $q = ORM::for_table('tbl_plans');
        if ($type !== '' && strtolower($type) !== 'all') {
            $q->where('type', $type);
        }
        if ($router !== '' && strtolower($router) !== 'all') {
            $q->where('routers', $router);
        }
        if ($search !== '') {
            $q->where_like('name_plan', '%' . $search . '%');
        }

        $rows = $q->order_by_desc('id')->find_array();

        $bw_map = [];
        $bws = ORM::for_table('tbl_bandwidth')->find_array();
        foreach ($bws as $b) {
            $bw_map[(int)$b['id']] = $b;
        }

        $plans = [];
        foreach ($rows as $r) {
            $bw = $bw_map[(int)$r['id_bw']] ?? null;
            $r['name_bw'] = $bw ? $bw['name_bw'] : '-';
            $r['rate_label'] = $bw
                ? ($bw['rate_down'] . $bw['rate_down_unit'] . ' / ' . $bw['rate_up'] . $bw['rate_up_unit'])
                : '-';
            $r['active_users'] = (int)ORM::for_table('tbl_user_recharges')
                ->where('plan_id', $r['id'])
                ->where('status', 'on')
                ->count();
            $plans[] = $r;
        }

        api_response(true, 'Daftar paket internet dimuat', [
            'plans'      => $plans,
            'bandwidths' => $bws,
            'routers'    => ORM::for_table('tbl_routers')->find_array(),
            'pools'      => ORM::for_table('tbl_pool')->find_array()
        ]);
        break;

    case 'admin_plan_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id        = (int)_post('id');
        $name_plan = trim(_post('name_plan'));
        $type      = _post('type') ?: 'Hotspot'; // Hotspot, PPPoE, Balance
        $price     = (float)_post('price');

        if (empty($name_plan)) {
            api_response(false, 'Nama paket wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $plan = ORM::for_table('tbl_plans')->find_one($id);
            if (!$plan) {
                api_response(false, 'Paket tidak ditemukan.', null, 404);
            }
        } else {
            $plan = ORM::for_table('tbl_plans')->create();
        }

        $plan->name_plan     = $name_plan;
        $plan->id_bw         = (int)(_post('id_bw') ?: 1);
        $plan->price         = $price;
        $plan->price_old     = (float)(_post('price_old') ?: 0);
        $plan->type          = $type;
        $plan->typebp        = _post('typebp') ?: 'Unlimited'; // Unlimited / Limited
        $plan->limit_type    = _post('limit_type') ?: 'Time_Limit'; // Time_Limit, Data_Limit, Both_Limit
        $plan->time_limit    = (int)(_post('time_limit') ?: 0);
        $plan->time_unit     = _post('time_unit') ?: 'Hrs';
        $plan->data_limit    = (int)(_post('data_limit') ?: 0);
        $plan->data_unit     = _post('data_unit') ?: 'MB';
        $plan->validity      = max(1, (int)(_post('validity') ?: 30));
        $plan->validity_unit = _post('validity_unit') ?: 'Days'; // Mins, Hrs, Days, Months, Period
        $plan->shared_users  = max(1, (int)(_post('shared_users') ?: 1));
        $plan->routers       = _post('routers') ?: 'radius';
        $plan->is_radius     = ($plan->routers === 'radius') ? 1 : (int)(_post('is_radius') ?: 0);
        $plan->pool          = trim(_post('pool') ?: '');
        $plan->pool_expired  = trim(_post('pool_expired') ?: '');
        $plan->plan_expired  = (int)(_post('plan_expired') ?: 0);
        $plan->enabled       = (int)(_post('enabled') ?? 1);
        $plan->prepaid       = _post('prepaid') === 'no' ? 'no' : 'yes';
        $plan->plan_type     = _post('plan_type') ?: 'Personal';
        if (empty($plan->device)) {
            $plan->device = ($type === 'PPPoE') ? 'MikrotikPppoe' : (($type === 'Hotspot') ? 'MikrotikHotspot' : 'Radius');
        }
        if (_post('device')) {
            $plan->device = _post('device');
        }
        $plan->save();

        // Sinkronkan profil paket ke MikroTik jika bukan paket Balance
        if (strtoupper($type) !== 'BALANCE' && class_exists('Package') && method_exists('Package', 'getDevice')) {
            try {
                $dev_file = Package::getDevice($plan);
                if (file_exists($dev_file)) {
                    require_once $dev_file;
                    $d_cls = $plan->device;
                    if (class_exists($d_cls)) {
                        $dev = new $d_cls();
                        if ($id > 0 && method_exists($dev, 'update_plan')) {
                            @$dev->update_plan($plan, $plan);
                        } elseif (method_exists($dev, 'add_plan')) {
                            @$dev->add_plan($plan);
                        }
                    }
                }
            } catch (Exception $e) {}
        }

        write_admin_log($admin, ($id > 0 ? 'Mengubah' : 'Menambah') . ' paket ' . $type . ': ' . $name_plan);
        api_response(true, 'Paket ' . $name_plan . ' berhasil disimpan.', $plan->as_array());
        break;

    case 'admin_plan_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $plan = ORM::for_table('tbl_plans')->find_one($id);
        if (!$plan) {
            api_response(false, 'Paket tidak ditemukan.', null, 404);
        }
        $pname = $plan['name_plan'];
        $plan->delete();
        write_admin_log($admin, 'Menghapus paket: ' . $pname);
        api_response(true, 'Paket ' . $pname . ' berhasil dihapus.');
        break;

    case 'admin_bandwidths':
        $admin = authenticate_admin($api_secret);
        $bws = ORM::for_table('tbl_bandwidth')->order_by_desc('id')->find_array();
        api_response(true, 'Daftar profil bandwidth dimuat', $bws);
        break;

    case 'admin_bandwidth_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id      = (int)_post('id');
        $name_bw = trim(_post('name_bw'));
        if (empty($name_bw)) {
            api_response(false, 'Nama bandwidth wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $bw = ORM::for_table('tbl_bandwidth')->find_one($id);
            if (!$bw) api_response(false, 'Data bandwidth tidak ditemukan.', null, 404);
        } else {
            $bw = ORM::for_table('tbl_bandwidth')->create();
        }

        $bw->name_bw        = $name_bw;
        $bw->rate_down      = max(1, (int)_post('rate_down'));
        $bw->rate_down_unit = _post('rate_down_unit') === 'Kbps' ? 'Kbps' : 'Mbps';
        $bw->rate_up        = max(1, (int)_post('rate_up'));
        $bw->rate_up_unit   = _post('rate_up_unit') === 'Kbps' ? 'Kbps' : 'Mbps';
        $bw->burst          = trim(_post('burst') ?: '');
        $bw->save();

        write_admin_log($admin, 'Menyimpan profil bandwidth: ' . $name_bw);
        api_response(true, 'Profil bandwidth ' . $name_bw . ' berhasil disimpan.', $bw->as_array());
        break;

    case 'admin_bandwidth_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $bw = ORM::for_table('tbl_bandwidth')->find_one($id);
        if (!$bw) {
            api_response(false, 'Profil bandwidth tidak ditemukan.', null, 404);
        }
        $bw->delete();
        api_response(true, 'Profil bandwidth berhasil dihapus.');
        break;

    case 'admin_routers':
        // Menu: Jaringan -> Routers
        $admin = authenticate_admin($api_secret);
        $routers = ORM::for_table('tbl_routers')->order_by_asc('id')->find_array();
        api_response(true, 'Daftar router dimuat', $routers);
        break;

    case 'admin_router_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id   = (int)_post('id');
        $name = trim(_post('name'));
        $ip   = trim(_post('ip_address'));
        $user = trim(_post('username'));
        $pass = _post('password');

        if (empty($name) || empty($ip) || empty($user)) {
            api_response(false, 'Nama Router, IP Address, dan Username MikroTik wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $r = ORM::for_table('tbl_routers')->find_one($id);
            if (!$r) api_response(false, 'Router tidak ditemukan.', null, 404);
        } else {
            $r = ORM::for_table('tbl_routers')->create();
        }

        $r->name        = $name;
        $r->ip_address  = $ip;
        $r->username    = $user;
        if ($pass !== '' || $id === 0) {
            $r->password = $pass;
        }
        $r->description = trim(_post('description'));
        $r->coordinates = trim(_post('coordinates'));
        $r->coverage    = trim(_post('coverage') ?: '500');
        $r->enabled     = (int)(_post('enabled') ?? 1);
        $r->save();

        write_admin_log($admin, 'Menyimpan konfigurasi router: ' . $name);
        api_response(true, 'Router ' . $name . ' berhasil disimpan.', $r->as_array());
        break;

    case 'admin_router_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $r = ORM::for_table('tbl_routers')->find_one($id);
        if (!$r) {
            api_response(false, 'Router tidak ditemukan.', null, 404);
        }
        $rname = $r['name'];
        $r->delete();
        write_admin_log($admin, 'Menghapus router: ' . $rname);
        api_response(true, 'Router ' . $rname . ' berhasil dihapus.');
        break;

    case 'admin_router_status':
        // Live MikroTik Resource Monitor (CPU, RAM, Uptime, Interface Traffic, Active Sessions)
        $admin = authenticate_admin($api_secret);
        $router_id = (int)(_get('id') ?: _post('id'));

        $q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($router_id > 0) {
            $q->where('id', $router_id);
        }
        $routers = $q->find_array();

        $results = [];
        foreach ($routers as $r) {
            $start_ms = microtime(true);
            $client = get_mikrotik_ros_client($r['ip_address'], $r['username'], $r['password']);
            $ping_ms = round((microtime(true) - $start_ms) * 1000);

            if (!$client) {
                $results[] = [
                    'id'          => (int)$r['id'],
                    'name'        => $r['name'],
                    'ip_address'  => $r['ip_address'],
                    'online'      => false,
                    'ping_ms'     => 0,
                    'cpu_load'    => 0,
                    'free_memory' => '0 B',
                    'total_memory'=> '0 B',
                    'ram_percent' => 0,
                    'uptime'      => 'Offline',
                    'board_name'  => '-',
                    'version'     => '-',
                    'hotspot_online' => 0,
                    'pppoe_online'   => 0
                ];
                continue;
            }

            try {
                $res_req = new PEAR2\Net\RouterOS\Request('/system/resource/print');
                $res_resp = $client->sendSync($res_req);
                $cpu = 0;
                $free_mem = 0;
                $total_mem = 0;
                $uptime = '-';
                $board = '-';
                $ver = '-';

                foreach ($res_resp as $item) {
                    if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $cpu       = (int)($item->getProperty('cpu-load') ?: 0);
                        $free_mem  = (float)($item->getProperty('free-memory') ?: 0);
                        $total_mem = (float)($item->getProperty('total-memory') ?: 0);
                        $uptime    = $item->getProperty('uptime') ?: '-';
                        $board     = $item->getProperty('board-name') ?: '-';
                        $ver       = $item->getProperty('version') ?: '-';
                        break;
                    }
                }

                $hs_cnt = 0;
                $hs_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/ip/hotspot/active/print'));
                foreach ($hs_resp as $h) {
                    if ($h->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) $hs_cnt++;
                }

                $ppp_cnt = 0;
                $ppp_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/ppp/active/print'));
                foreach ($ppp_resp as $p) {
                    if ($p->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) $ppp_cnt++;
                }

                $used_mem = max(0, $total_mem - $free_mem);
                $ram_pct  = $total_mem > 0 ? round(($used_mem / $total_mem) * 100, 1) : 0;

                $results[] = [
                    'id'             => (int)$r['id'],
                    'name'           => $r['name'],
                    'ip_address'     => $r['ip_address'],
                    'online'         => true,
                    'ping_ms'        => $ping_ms,
                    'cpu_load'       => $cpu,
                    'free_memory'    => format_bytes_readable($free_mem),
                    'total_memory'   => format_bytes_readable($total_mem),
                    'ram_percent'    => $ram_pct,
                    'uptime'         => $uptime,
                    'board_name'     => $board,
                    'version'        => $ver,
                    'hotspot_online' => $hs_cnt,
                    'pppoe_online'   => $ppp_cnt
                ];
            } catch (Exception $e) {
                $results[] = [
                    'id'          => (int)$r['id'],
                    'name'        => $r['name'],
                    'ip_address'  => $r['ip_address'],
                    'online'      => false,
                    'ping_ms'     => 0,
                    'uptime'      => 'Error API'
                ];
            }
        }

        api_response(true, 'Status router berhasil diperiksa', $results);
        break;

    case 'admin_router_reboot':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $r = ORM::for_table('tbl_routers')->find_one($id);
        if (!$r) {
            api_response(false, 'Router tidak ditemukan.', null, 404);
        }

        $client = get_mikrotik_ros_client($r['ip_address'], $r['username'], $r['password']);
        if (!$client) {
            api_response(false, 'Gagal terhubung ke Router ' . $r['name'], null, 500);
        }

        try {
            $req = new PEAR2\Net\RouterOS\Request('/system/reboot');
            @$client->sendSync($req);
        } catch (Exception $e) {}

        write_admin_log($admin, 'Melakukan Reboot Router: ' . $r['name']);
        api_response(true, 'Perintah Reboot telah dikirim ke Router ' . $r['name'] . '.');
        break;

    case 'admin_online_users':
        // Live Hotspot & PPPoE Online Users di MikroTik
        $admin = authenticate_admin($api_secret);
        $router_filter  = trim(_get('router') ?: _post('router'));
        $service_filter = strtolower(trim(_get('service') ?: _post('service') ?: 'all'));

        $q = ORM::for_table('tbl_routers')->where('enabled', 1);
        if ($router_filter !== '' && strtolower($router_filter) !== 'all') {
            $q->where('name', $router_filter);
        }
        $routers = $q->find_array();

        $online_users = [];
        foreach ($routers as $r) {
            $client = get_mikrotik_ros_client($r['ip_address'], $r['username'], $r['password']);
            if (!$client) continue;

            try {
                if ($service_filter === 'all' || $service_filter === 'hotspot') {
                    $hs_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/ip/hotspot/active/print'));
                    foreach ($hs_resp as $item) {
                        if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                            $rx = (float)($item->getProperty('bytes-in') ?: 0);
                            $tx = (float)($item->getProperty('bytes-out') ?: 0);
                            $online_users[] = [
                                'ros_id'      => $item->getProperty('.id'),
                                'username'    => $item->getProperty('user') ?: '-',
                                'service'     => 'Hotspot',
                                'router'      => $r['name'],
                                'router_id'   => (int)$r['id'],
                                'ip_address'  => $item->getProperty('address') ?: '-',
                                'mac_address' => $item->getProperty('mac-address') ?: '-',
                                'uptime'      => $item->getProperty('uptime') ?: '0s',
                                'upload'      => format_bytes_readable($rx),
                                'download'    => format_bytes_readable($tx),
                                'total_bytes' => format_bytes_readable($rx + $tx)
                            ];
                        }
                    }
                }

                if ($service_filter === 'all' || $service_filter === 'pppoe') {
                    $ppp_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/ppp/active/print'));
                    foreach ($ppp_resp as $item) {
                        if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                            $online_users[] = [
                                'ros_id'      => $item->getProperty('.id'),
                                'username'    => $item->getProperty('name') ?: '-',
                                'service'     => 'PPPoE',
                                'router'      => $r['name'],
                                'router_id'   => (int)$r['id'],
                                'ip_address'  => $item->getProperty('address') ?: '-',
                                'mac_address' => $item->getProperty('caller-id') ?: '-',
                                'uptime'      => $item->getProperty('uptime') ?: '0s',
                                'upload'      => '-',
                                'download'    => '-',
                                'total_bytes' => 'Active Tunnel'
                            ];
                        }
                    }
                }
            } catch (Exception $e) {}
        }

        api_response(true, 'Daftar user online berhasil dimuat', [
            'total' => count($online_users),
            'users' => $online_users
        ]);
        break;

    case 'admin_kick_user':
        // Putuskan sesi Hotspot / PPPoE langsung dari MikroTik
        $admin = authenticate_admin($api_secret);
        $router_name = trim(_post('router'));
        $username    = trim(_post('username'));
        $service     = strtolower(trim(_post('service') ?: 'hotspot'));
        $ros_id      = trim(_post('ros_id'));

        $r = ORM::for_table('tbl_routers')->where('name', $router_name)->find_one();
        if (!$r) {
            api_response(false, 'Router ' . $router_name . ' tidak ditemukan.', null, 404);
        }

        $client = get_mikrotik_ros_client($r['ip_address'], $r['username'], $r['password']);
        if (!$client) {
            api_response(false, 'Gagal terhubung ke Router ' . $r['name'], null, 500);
        }

        try {
            $path = ($service === 'pppoe') ? '/ppp/active' : '/ip/hotspot/active';
            $prop = ($service === 'pppoe') ? 'name' : 'user';

            if (!empty($ros_id)) {
                $rem = new PEAR2\Net\RouterOS\Request($path . '/remove');
                $rem->setArgument('numbers', $ros_id);
                $client->sendSync($rem);
            } else {
                $print = new PEAR2\Net\RouterOS\Request($path . '/print');
                $print->setQuery(PEAR2\Net\RouterOS\Query::where($prop, $username));
                $resp = $client->sendSync($print);
                foreach ($resp as $item) {
                    if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                        $rem = new PEAR2\Net\RouterOS\Request($path . '/remove');
                        $rem->setArgument('numbers', $item->getProperty('.id'));
                        $client->sendSync($rem);
                    }
                }
            }

            write_admin_log($admin, 'Memutus sesi online (' . strtoupper($service) . ') user: ' . $username . ' di ' . $router_name);
            api_response(true, 'Sesi ' . $username . ' berhasil diputus dari ' . $router_name . '.');
        } catch (Exception $e) {
            api_response(false, 'Gagal memutus sesi: ' . $e->getMessage(), null, 500);
        }
        break;

    case 'admin_pools':
        // Menu: Jaringan -> IP Pool
        $admin = authenticate_admin($api_secret);
        $pools = ORM::for_table('tbl_pool')->order_by_desc('id')->find_array();
        api_response(true, 'Daftar IP Pool dimuat', $pools);
        break;

    case 'admin_pool_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id        = (int)_post('id');
        $pool_name = trim(_post('pool_name'));
        $range_ip  = trim(_post('range_ip'));
        $routers   = trim(_post('routers'));

        if (empty($pool_name) || empty($range_ip)) {
            api_response(false, 'Nama Pool dan Rentang IP wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $pool = ORM::for_table('tbl_pool')->find_one($id);
            if (!$pool) api_response(false, 'IP Pool tidak ditemukan.', null, 404);
        } else {
            $pool = ORM::for_table('tbl_pool')->create();
        }

        $pool->pool_name = $pool_name;
        $pool->local_ip  = trim(_post('local_ip') ?: '');
        $pool->range_ip  = $range_ip;
        $pool->routers   = $routers;
        $pool->save();

        write_admin_log($admin, 'Menyimpan IP Pool: ' . $pool_name);
        api_response(true, 'IP Pool ' . $pool_name . ' berhasil disimpan.', $pool->as_array());
        break;

    case 'admin_pool_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_post('id');
        $pool = ORM::for_table('tbl_pool')->find_one($id);
        if ($pool) {
            $pool->delete();
        }
        api_response(true, 'IP Pool berhasil dihapus.');
        break;

    case 'admin_map_data':
        // Menu: Peta (Lokasi Pelanggan, Router, dan Asset/ODP)
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();

        $cust_rows = ORM::for_table('tbl_customers')
            ->where_not_equal('coordinates', '')
            ->where_not_null('coordinates')
            ->find_array();

        $active_map = [];
        $recharges = ORM::for_table('tbl_user_recharges')->find_array();
        foreach ($recharges as $r) {
            $cid = (int)$r['customer_id'];
            if (!isset($active_map[$cid]) || $r['status'] === 'on') {
                $active_map[$cid] = $r;
            }
        }

        $customer_markers = [];
        foreach ($cust_rows as $c) {
            $parts = array_map('trim', explode(',', $c['coordinates']));
            if (count($parts) !== 2 || !is_numeric($parts[0]) || !is_numeric($parts[1])) continue;
            $rech = $active_map[(int)$c['id']] ?? null;
            $customer_markers[] = [
                'id'           => (int)$c['id'],
                'username'     => $c['username'],
                'fullname'     => $c['fullname'],
                'phonenumber'  => $c['phonenumber'],
                'address'      => $c['address'],
                'service_type' => $c['service_type'],
                'lat'          => (float)$parts[0],
                'lng'          => (float)$parts[1],
                'plan_status'  => $rech ? $rech['status'] : 'off',
                'plan_name'    => $rech ? $rech['namebp'] : 'Belum Ada Paket',
                'expiration'   => $rech ? $rech['expiration'] : '-'
            ];
        }

        $router_rows = ORM::for_table('tbl_routers')
            ->where_not_equal('coordinates', '')
            ->where_not_null('coordinates')
            ->find_array();

        $router_markers = [];
        foreach ($router_rows as $r) {
            $parts = array_map('trim', explode(',', $r['coordinates']));
            if (count($parts) !== 2 || !is_numeric($parts[0]) || !is_numeric($parts[1])) continue;
            $router_markers[] = [
                'id'          => (int)$r['id'],
                'name'        => $r['name'],
                'ip_address'  => $r['ip_address'],
                'description' => $r['description'],
                'coverage'    => (int)($r['coverage'] ?: 500),
                'lat'         => (float)$parts[0],
                'lng'         => (float)$parts[1]
            ];
        }

        $asset_markers = [];
        try {
            $asset_rows = ORM::for_table('tbl_assets')
                ->where_not_equal('coordinates', '')
                ->where_not_null('coordinates')
                ->find_array();
            foreach ($asset_rows as $a) {
                $parts = array_map('trim', explode(',', $a['coordinates']));
                if (count($parts) !== 2 || !is_numeric($parts[0]) || !is_numeric($parts[1])) continue;
                $asset_markers[] = [
                    'id'        => (int)$a['id'],
                    'name'      => $a['name'],
                    'category'  => $a['category'],
                    'condition' => $a['condition_status'],
                    'lat'       => (float)$parts[0],
                    'lng'       => (float)$parts[1]
                ];
            }
        } catch (Exception $e) {}

        api_response(true, 'Data peta berhasil dimuat', [
            'customers' => $customer_markers,
            'routers'   => $router_markers,
            'assets'    => $asset_markers
        ]);
        break;

    case 'admin_assets':
        // Menu: Asset Manager
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();
        $category = trim(_get('category') ?: _post('category'));
        $search   = trim(_get('search') ?: _post('search'));

        $q = ORM::for_table('tbl_assets');
        if ($category !== '' && strtolower($category) !== 'all') {
            $q->where('category', $category);
        }
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(name LIKE ? OR serial_number LIKE ? OR brand LIKE ? OR notes LIKE ?)', [$like, $like, $like, $like]);
        }

        $assets = $q->order_by_desc('id')->find_array();
        $total_value = ORM::for_table('tbl_assets')->sum('cost') ?: 0;

        api_response(true, 'Daftar aset jaringan dimuat', [
            'summary' => [
                'total_items' => count($assets),
                'total_value' => (float)$total_value
            ],
            'assets' => $assets
        ]);
        break;

    case 'admin_asset_save':
        $admin = authenticate_admin($api_secret);
        ensure_extra_admin_tables();
        $id   = (int)_post('id');
        $name = trim(_post('name'));

        if (empty($name)) {
            api_response(false, 'Nama aset / perangkat wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $a = ORM::for_table('tbl_assets')->find_one($id);
            if (!$a) api_response(false, 'Aset tidak ditemukan.', null, 404);
        } else {
            $a = ORM::for_table('tbl_assets')->create();
        }

        $a->name             = $name;
        $a->category         = trim(_post('category') ?: 'ODP/ODC');
        $a->serial_number    = trim(_post('serial_number'));
        $a->brand            = trim(_post('brand'));
        $a->customer_id      = (int)(_post('customer_id') ?: 0);
        $a->router_name      = trim(_post('router_name'));
        $a->coordinates      = trim(_post('coordinates'));
        $a->cost             = (float)(_post('cost') ?: 0);
        $a->condition_status = _post('condition_status') ?: 'Good';
        $a->purchase_date    = _post('purchase_date') ?: date('Y-m-d');
        $a->notes            = trim(_post('notes'));
        $a->save();

        write_admin_log($admin, 'Menyimpan data aset: ' . $name);
        api_response(true, 'Data aset ' . $name . ' berhasil disimpan.', $a->as_array());
        break;

    case 'admin_asset_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        ensure_extra_admin_tables();
        $id = (int)_post('id');
        $a = ORM::for_table('tbl_assets')->find_one($id);
        if ($a) {
            $a->delete();
        }
        api_response(true, 'Data aset berhasil dihapus.');
        break;

    case 'admin_reports':
        // Menu: Laporan (Harian & Periode)
        $admin = authenticate_admin($api_secret);
        $start_date = trim(_get('start_date') ?: _post('start_date') ?: date('Y-m-01'));
        $end_date   = trim(_get('end_date') ?: _post('end_date') ?: date('Y-m-d'));
        $type       = trim(_get('type') ?: _post('type'));
        $router     = trim(_get('router') ?: _post('router'));
        $method     = trim(_get('method') ?: _post('method'));
        $search     = trim(_get('search') ?: _post('search'));
        $page       = max(1, (int)(_get('page') ?: _post('page') ?: 1));
        $limit      = max(15, min(200, (int)(_get('limit') ?: _post('limit') ?: 50)));
        $offset     = ($page - 1) * $limit;

        $q = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_date)
            ->where_lte('recharged_on', $end_date)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_equal('type', 'Balance');

        if ($type !== '' && strtolower($type) !== 'all') {
            $q->where('type', $type);
        }
        if ($router !== '' && strtolower($router) !== 'all') {
            $q->where('routers', $router);
        }
        if ($method !== '' && strtolower($method) !== 'all') {
            $q->where_like('method', '%' . $method . '%');
        }
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR invoice LIKE ? OR plan_name LIKE ?)', [$like, $like, $like]);
        }

        $sum_q = clone $q;
        $total_income = $sum_q->sum('price') ?: 0;

        $cnt_q = clone $q;
        $total_count = $cnt_q->count();

        $rows = $q->order_by_desc('id')
            ->limit($limit)
            ->offset($offset)
            ->find_array();

        api_response(true, 'Laporan keuangan berhasil dimuat', [
            'filter' => [
                'start_date' => $start_date,
                'end_date'   => $end_date
            ],
            'summary' => [
                'total_transactions' => (int)$total_count,
                'total_income'       => (float)$total_income
            ],
            'page'         => $page,
            'limit'        => $limit,
            'transactions' => $rows
        ]);
        break;

    case 'admin_payment_gateways':
        // Audit Transaksi Payment Gateway (ViaQRIS, Duitku, Midtrans, Xendit, dll)
        $admin  = authenticate_admin($api_secret);
        $token  = get_bearer_token();
        $status = _get('status') !== '' ? _get('status') : _post('status');
        $search = trim(_get('search') ?: _post('search'));
        $page   = max(1, (int)(_get('page') ?: _post('page') ?: 1));
        $limit  = max(10, min(100, (int)(_get('limit') ?: _post('limit') ?: 30)));
        $offset = ($page - 1) * $limit;

        $q = ORM::for_table('tbl_payment_gateway');
        if ($status !== '' && $status !== null && strtolower((string)$status) !== 'all') {
            $q->where('status', (int)$status);
        }
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR plan_name LIKE ? OR trx_invoice LIKE ? OR gateway LIKE ?)', [$like, $like, $like, $like]);
        }

        $total_q = clone $q;
        $total = $total_q->count();

        $rows = $q->order_by_desc('id')
            ->limit($limit)
            ->offset($offset)
            ->find_many();

        $items = [];
        foreach ($rows as $row) {
            if ((int)$row['status'] === 1) {
                verify_if_already_recharged($row);
            }
            $items[] = format_unpaid_data($row, $token, $config, false);
        }

        api_response(true, 'Data transaksi Payment Gateway dimuat', [
            'total' => (int)$total,
            'page'  => $page,
            'limit' => $limit,
            'items' => $items
        ]);
        break;

    case 'admin_pg_action':
        // Tindakan Admin pada Tagihan Payment Gateway: 'check', 'approve_manual', 'cancel'
        $admin      = authenticate_admin($api_secret);
        $token      = get_bearer_token();
        $trx_id     = (int)_post('id');
        $sub_action = trim(_post('sub_action') ?: 'check');

        $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
        if (!$trx) {
            api_response(false, 'Data tagihan tidak ditemukan.', null, 404);
        }

        $cust = ORM::for_table('tbl_customers')->where('username', $trx['username'])->find_one();

        if ($sub_action === 'cancel') {
            if ((int)$trx['status'] === 2) {
                api_response(false, 'Tagihan yang sudah LUNAS tidak dapat dibatalkan.', null, 400);
            }
            $trx->status = 4;
            $trx->save();
            write_admin_log($admin, 'Membatalkan tagihan QRIS #' . $trx_id . ' milik ' . $trx['username']);
            api_response(true, 'Tagihan berhasil dibatalkan.', format_unpaid_data($trx, $token, $config, false));
        }

        if ($sub_action === 'approve_manual') {
            if (!in_array($admin['user_type'], ['SuperAdmin', 'Admin'])) {
                api_response(false, 'Hanya SuperAdmin/Admin yang dapat menyetujui pembayaran manual.', null, 403);
            }
            if (verify_if_already_recharged($trx)) {
                api_response(true, 'Tagihan ini sudah berstatus LUNAS sebelumnya.', format_unpaid_data($trx, $token, $config, false));
            }
            if (!$cust) {
                api_response(false, 'Data pelanggan pemilik tagihan tidak ditemukan.', null, 404);
            }

            if (class_exists('Package') && Package::rechargeUser($cust['id'], $trx['routers'], $trx['plan_id'], $trx['gateway'], 'Manual Approve (' . $admin['username'] . ')')) {
                $trx->status = 2;
                $trx->paid_date = date('Y-m-d H:i:s');
                $trx->save();
                write_admin_log($admin, 'Approve manual tagihan Payment Gateway #' . $trx_id . ' untuk ' . $trx['username']);
                api_response(true, 'Tagihan berhasil dilunasi & paket pelanggan telah aktif!', format_unpaid_data($trx, $token, $config, false));
            } else {
                api_response(false, 'Gagal mengaktifkan paket ke Router MikroTik.', null, 500);
            }
        }

        // Default sub_action === 'check'
        if (!verify_if_already_recharged($trx) && (int)$trx['status'] === 1 && !empty($trx['gateway']) && $cust) {
            $gw_file = 'system/paymentgateway/' . $trx['gateway'] . '.php';
            if (file_exists($gw_file)) {
                include_once $gw_file;
                $status_fn = $trx['gateway'] . '_get_status';
                if (function_exists($status_fn)) {
                    $GLOBALS['API_INTERCEPT_MODE']   = 'check_payment';
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = $trx['id'];
                    $GLOBALS['API_INTERCEPT_TOKEN']  = $token;
                    @$status_fn($trx, $cust);
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                    $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
                }
            }
        }

        $is_paid = verify_if_already_recharged($trx);
        api_response(true, $is_paid ? 'Pembayaran LUNAS terverifikasi!' : 'Pembayaran belum diterima oleh Gateway.', [
            'paid' => $is_paid,
            'bill' => format_unpaid_data($trx, $token, $config, false)
        ]);
        break;

    case 'admin_send_message':
        // Menu: Mengirim Pesan (Single & Bulk Broadcast WA / SMS / Inbox / Telegram)
        $admin = authenticate_admin($api_secret);
        $mode        = _post('mode') ?: 'single'; // single / bulk
        $customer_id = (int)_post('customer_id');
        $group       = _post('group') ?: 'all'; // all, active, expired, hotspot, pppoe
        $via         = _post('via') ?: 'wa'; // wa, sms, inbox, both
        $message_raw = trim(_post('message'));
        $subject     = trim(_post('subject') ?: ('Informasi ' . ($config['CompanyName'] ?? 'Arneta.ID')));

        if (empty($message_raw)) {
            api_response(false, 'Isi pesan tidak boleh kosong.', null, 400);
        }

        $targets = [];
        if ($mode === 'single') {
            $c = ORM::for_table('tbl_customers')->find_one($customer_id);
            if (!$c) {
                api_response(false, 'Pelanggan tujuan tidak ditemukan.', null, 404);
            }
            $targets[] = $c;
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
                    api_response(false, 'Tidak ada pelanggan dalam kategori ' . $group, null, 404);
                }
            }
            $targets = $q->limit(250)->find_many();
        }

        $sent_count = 0;
        foreach ($targets as $cust) {
            $msg = str_replace(
                ['[[name]]', '[[user_name]]', '[[phone]]', '[[company_name]]', '[[balance]]'],
                [$cust['fullname'], $cust['username'], $cust['phonenumber'], $config['CompanyName'] ?? 'Arneta.ID', number_format((float)$cust['balance'], 0, ',', '.')],
                $message_raw
            );

            $sent_any = false;
            if (($via === 'wa' || $via === 'both') && !empty($cust['phonenumber']) && class_exists('Message') && method_exists('Message', 'sendWhatsapp')) {
                @Message::sendWhatsapp($cust['phonenumber'], $msg);
                $sent_any = true;
            }
            if (($via === 'sms' || $via === 'both') && !empty($cust['phonenumber']) && class_exists('Message') && method_exists('Message', 'sendSMS')) {
                @Message::sendSMS($cust['phonenumber'], $msg);
                $sent_any = true;
            }
            if ($via === 'inbox' || $via === 'both') {
                if (class_exists('Message') && method_exists('Message', 'addToInbox')) {
                    @Message::addToInbox($cust['id'], $subject, $msg, 'Admin');
                    $sent_any = true;
                } else {
                    try {
                        $inb = ORM::for_table('tbl_customers_inbox')->create();
                        $inb->customer_id = $cust['id'];
                        $inb->date_created = date('Y-m-d H:i:s');
                        $inb->subject = $subject;
                        $inb->body = nl2br($msg);
                        $inb->from = 'Admin';
                        $inb->save();
                        $sent_any = true;
                    } catch (Exception $e) {}
                }
            }
            if ($sent_any) $sent_count++;
        }

        write_admin_log($admin, 'Mengirim pesan (' . strtoupper($via) . ') ke ' . $sent_count . ' pelanggan');
        api_response(true, 'Pesan berhasil dikirim ke ' . $sent_count . ' pelanggan.');
        break;

    case 'admin_pages':
        // Menu: Halaman Statis (Membaca template di folder pages/)
        $admin = authenticate_admin($api_secret);
        $pages_dir = 'pages';
        $files = glob($pages_dir . '/*.html') ?: [];

        $page_list = [];
        foreach ($files as $f) {
            $name = basename($f, '.html');
            $page_list[] = [
                'name'    => $name,
                'title'   => str_replace('_', ' ', $name),
                'content' => @file_get_contents($f) ?: ''
            ];
        }

        api_response(true, 'Daftar halaman statis dimuat', $page_list);
        break;

    case 'admin_page_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $name    = preg_replace('/[^a-zA-Z0-9_\-]/', '', _post('name'));
        $content = _post('content');

        if (empty($name)) {
            api_response(false, 'Nama halaman tidak valid.', null, 400);
        }

        $path = 'pages/' . $name . '.html';
        if (@file_put_contents($path, $content) !== false) {
            write_admin_log($admin, 'Memperbarui halaman statis: ' . $name);
            api_response(true, 'Halaman ' . str_replace('_', ' ', $name) . ' berhasil disimpan.');
        } else {
            api_response(false, 'Gagal menulis file ke folder pages/. Pastikan izin folder writable.', null, 500);
        }
        break;

    case 'admin_settings':
        // Menu: Pengaturan Umum & Notifikasi
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $safe_keys = [
            'CompanyName', 'address', 'phone', 'currency_code', 'country_code_phone',
            'enable_balance', 'allow_balance_transfer', 'minimum_transfer',
            'enable_tax', 'tax_rate', 'payment_gateway', 'whatsapp_gateway_url',
            'wa_url', 'sms_url', 'telegram_bot', 'telegram_target_id',
            'user_notification_expired', 'user_notification_payment', 'user_notification_reminder',
            'note', 'tawkto', 'theme'
        ];

        $settings_data = [];
        foreach ($safe_keys as $k) {
            $settings_data[$k] = $config[$k] ?? '';
        }

        api_response(true, 'Pengaturan sistem dimuat', $settings_data);
        break;

    case 'admin_settings_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $updates = _post('settings');
        if (!is_array($updates)) {
            $updates = $_POST;
        }

        $saved = 0;
        foreach ($updates as $k => $v) {
            if (in_array($k, ['action', 'token', 'settings'])) continue;
            $row = ORM::for_table('tbl_appconfig')->where('setting', $k)->find_one();
            if (!$row) {
                $row = ORM::for_table('tbl_appconfig')->create();
                $row->setting = $k;
            }
            $row->value = is_array($v) ? implode(',', $v) : trim((string)$v);
            $row->save();
            $saved++;
        }

        write_admin_log($admin, 'Memperbarui ' . $saved . ' pengaturan aplikasi melalui Android Admin');
        api_response(true, 'Pengaturan sistem berhasil disimpan.');
        break;

    case 'admin_staff_list':
        // Menu: Pengaturan -> Pengaturan Pengguna (Admin / Agent / Sales)
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $rows = ORM::for_table('tbl_users')->order_by_asc('id')->find_array();
        $staff = [];
        foreach ($rows as $r) {
            unset($r['password']);
            $staff[] = $r;
        }
        api_response(true, 'Daftar staf/admin dimuat', $staff);
        break;

    case 'admin_staff_save':
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $id        = (int)_post('id');
        $username  = trim(_post('username'));
        $fullname  = trim(_post('fullname'));
        $password  = _post('password');
        $user_type = _post('user_type') ?: 'Agent';

        if (empty($username) || empty($fullname)) {
            api_response(false, 'Username dan Nama Lengkap wajib diisi.', null, 400);
        }

        if ($id > 0) {
            $u = ORM::for_table('tbl_users')->find_one($id);
            if (!$u) api_response(false, 'Staf tidak ditemukan.', null, 404);
        } else {
            if (empty($password)) {
                api_response(false, 'Password wajib diisi untuk staf baru.', null, 400);
            }
            $dup = ORM::for_table('tbl_users')->where('username', $username)->find_one();
            if ($dup) api_response(false, 'Username sudah digunakan.', null, 400);
            $u = ORM::for_table('tbl_users')->create();
            $u->creationdate = date('Y-m-d H:i:s');
        }

        $u->username  = $username;
        $u->fullname  = $fullname;
        $u->user_type = $user_type;
        $u->phone     = trim(_post('phone'));
        $u->email     = trim(_post('email'));
        $u->city      = trim(_post('city'));
        $u->status    = _post('status') ?: 'Active';
        if (!empty($password)) {
            $u->password = hash_admin_password($password);
        }
        $u->save();

        write_admin_log($admin, ($id > 0 ? 'Mengubah' : 'Menambah') . ' staf ' . $user_type . ': ' . $username);
        api_response(true, 'Data staf ' . $fullname . ' berhasil disimpan.');
        break;

    case 'admin_staff_delete':
        $admin = authenticate_admin($api_secret, ['SuperAdmin']);
        $id = (int)_post('id');
        if ($id === (int)$admin['id']) {
            api_response(false, 'Anda tidak dapat menghapus akun Anda sendiri.', null, 400);
        }
        $u = ORM::for_table('tbl_users')->find_one($id);
        if ($u) {
            $u->delete();
        }
        api_response(true, 'Akun staf berhasil dihapus.');
        break;

    case 'admin_logs':
        $admin = authenticate_admin($api_secret);
        $clear = (int)_post('clear');
        if ($clear === 1 && in_array($admin['user_type'], ['SuperAdmin', 'Admin'])) {
            ORM::for_table('tbl_logs')->delete_many();
            write_admin_log($admin, 'Membersihkan riwayat System Logs');
            api_response(true, 'System Logs berhasil dibersihkan.');
        }

        $logs = ORM::for_table('tbl_logs')
            ->order_by_desc('id')
            ->limit(100)
            ->find_array();
        api_response(true, 'System logs dimuat', $logs);
        break;

    case 'admin_system_tools':
        // Utilitas Admin: Clear Cache & Trigger Cronjob
        $admin = authenticate_admin($api_secret, ['SuperAdmin', 'Admin']);
        $tool = _post('tool') ?: 'clear_cache';

        if ($tool === 'clear_cache') {
            $deleted = 0;
            $dirs = ['ui/compiled/*', 'system/cache/*'];
            foreach ($dirs as $pattern) {
                foreach (glob($pattern) ?: [] as $f) {
                    if (is_file($f) && basename($f) !== 'index.html' && basename($f) !== '.htaccess') {
                        @unlink($f);
                        $deleted++;
                    }
                }
            }
            write_admin_log($admin, 'Membersihkan ' . $deleted . ' file cache sistem');
            api_response(true, $deleted . ' file cache berhasil dibersihkan.');
        }

        if ($tool === 'run_cron') {
            // Mengecek dan menonaktifkan paket yang sudah melewati tanggal/jam expired
            $now_date = date('Y-m-d');
            $now_time = date('H:i:s');
            $active_list = ORM::for_table('tbl_user_recharges')
                ->where('status', 'on')
                ->where_lte('expiration', $now_date)
                ->find_many();

            $expired_cnt = 0;
            foreach ($active_list as $ur) {
                $exp_ts = strtotime($ur['expiration'] . ' ' . ($ur['time'] ?: '23:59:59'));
                if (time() >= $exp_ts) {
                    $plan = ORM::for_table('tbl_plans')->find_one($ur['plan_id']);
                    $cust = ORM::for_table('tbl_customers')->find_one($ur['customer_id']);
                    if ($plan && $cust && class_exists('Package') && method_exists('Package', 'getDevice')) {
                        $dev_file = Package::getDevice($plan);
                        if (file_exists($dev_file)) {
                            require_once $dev_file;
                            $d_cls = $plan['device'];
                            if (class_exists($d_cls)) {
                                $dev = new $d_cls();
                                if (method_exists($dev, 'remove_customer')) {
                                    @$dev->remove_customer($cust, $plan);
                                }
                            }
                        }
                    }
                    $ur->status = 'off';
                    $ur->save();
                    $expired_cnt++;
                }
            }

            write_admin_log($admin, 'Menjalankan Cronjob manual (' . $expired_cnt . ' pelanggan kedaluwarsa ditertibkan)');
            api_response(true, 'Cronjob selesai dijalankan. ' . $expired_cnt . ' paket kedaluwarsa telah dinonaktifkan.');
        }

        api_response(false, 'Perintah utilitas tidak dikenali.', null, 400);
        break;

    case 'admin_webview_sso':
        // Jembatan Auto-Login dari Aplikasi Android ke WebView Internal (?_route=admin -> ?_route=dashboard)
        $admin = authenticate_admin($api_secret);
        $target_route = trim(_get('redirect') ?: _post('redirect') ?: 'dashboard');

        $_SESSION['aid'] = $admin['id'];
        if (class_exists('Admin') && method_exists('Admin', 'setCookie')) {
            @Admin::setCookie($admin['id']);
        }

        if (ob_get_length()) {
            ob_clean();
        }
        header('Location: ' . APP_URL . '/?_route=' . ltrim($target_route, '/'));
        exit();

    default:
        api_response(true, 'PHPNuxBill Customer & Admin API v6 Ready (Arneta.ID)');
        break;
}
```

### âš™ï¸ Bagian yang Dapat Diedit (Opsional)
Jika Anda perlu mengkustomisasi pengaturan API (misalnya mengubah fallback "Secret Key"), Anda dipersilakan mengedit bagian ini pada kode yang sudah disalin di atas:

```php
// Baris ~19
$api_secret = !empty($config['tawkto']) ? $config['tawkto'] : 'Arneta_WiFi_Secret_Key_2026_#99';
```
Anda dapat mengubah `'Arneta_WiFi_Secret_Key_2026_#99'` ke kunci kustom Anda sendiri (disarankan demi keamanan jika integrasi token Anda membutuhkannya).

---
*Generated by AI*
