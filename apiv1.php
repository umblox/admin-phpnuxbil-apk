<?php
/**
 * PHPNuxBill Complete API & Companion Controller (v12 Final - Arneta.ID)
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
    global $config, $admin;

    if (!empty($_SESSION['aid'])) {
        $adm = ORM::for_table('tbl_users')->find_one($_SESSION['aid']);
        if ($adm) {
            if (!empty($allowed_roles) && !in_array($adm['user_type'], $allowed_roles)) {
                api_out(false, 'Akses ditolak untuk level ' . $adm['user_type'], null, 403);
            }
            $admin = $adm;
            $GLOBALS['admin'] = $adm;
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
            $admin = $adm;
            $GLOBALS['admin'] = $adm;
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
                $admin = $adm;
                $GLOBALS['admin'] = $adm;
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
                $admin = $adm;
                $GLOBALS['admin'] = $adm;
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
        } elseif (function_exists('sendWhatsapp')) {
            @sendWhatsapp($phone, $text);
        }
    } catch (Exception $e) {}
}

function notify_sms_customer($phone, $text) {
    if (empty($phone)) return;
    try {
        if (class_exists('Message') && method_exists('Message', 'sendSMS')) {
            @Message::sendSMS($phone, $text);
        } elseif (function_exists('sendSMS')) {
            @sendSMS($phone, $text);
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
    // 2. DASBOR ADMIN
    // =========================================================================
    case 'admin_dashboard':
    case 'dashboard_extras':
        $admin = require_admin_session($api_secret);
        $today = date('Y-m-d');
        $start_month = date('Y-m-01');
        $yr = date('Y');
        $months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        $income_today = ORM::for_table('tbl_transactions')
            ->where('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_like('method', 'Recharge Balance%')
            ->sum('price') ?: 0;

        $income_month = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_month)
            ->where_lte('recharged_on', $today)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_like('method', 'Recharge Balance%')
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
                ->where_not_like('method', 'Recharge Balance%')
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
    // 3. PELANGGAN, LIVE SEARCH & QUICKADD CUSTOMER
    // =========================================================================
    case 'admin_customers':
        $admin = require_admin_session($api_secret);
        $search = trim(_req('search'));
        $service_type = trim(_req('service_type'));
        $limit = max(10, min(300, (int)(_req('limit') ?: 150)));

        $q = ORM::for_table('tbl_customers');
        if ($search !== '') {
            $like = '%' . $search . '%';
            $q->where_raw('(username LIKE ? OR fullname LIKE ? OR phonenumber LIKE ? OR email LIKE ? OR pppoe_username LIKE ? OR address LIKE ? OR city LIKE ?)', [$like, $like, $like, $like, $like, $like, $like]);
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
                'city'           => $row['city'] ?? '',
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
                    'plan_id'    => (int)$rech['plan_id'],
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
        global $zero;
        $admin = require_admin_session($api_secret);
        $id = (int)_post('id');

        $raw_username   = trim(_post('username'));
        $username       = function_exists('alphanumeric') ? alphanumeric($raw_username, ":+_.@-") : $raw_username;
        $fullname       = trim(_post('fullname'));
        $password       = trim(_post('password'));
        $pppoe_username = trim(_post('pppoe_username'));
        $pppoe_password = trim(_post('pppoe_password'));
        $pppoe_ip       = trim(_post('pppoe_ip'));
        $email          = trim(_post('email'));
        $address        = trim(_post('address'));
        $city           = trim(_post('city'));
        $phonenumber    = trim(_post('phonenumber'));
        $service_type   = _post('service_type') ?: 'Hotspot';
        $account_type   = _post('account_type') ?: 'Personal';
        $status         = _post('status') ?: 'Active';
        $coordinates    = trim(_post('coordinates'));

        $ppln  = (int)(_post('ppln') ?: _post('plan_id'));
        $using = trim(_post('using') ?: 'Cash');
        $zero  = 0;
        $GLOBALS['zero'] = 0;

        if ($id === 0 && function_exists('run_hook')) {
            @run_hook('add_customer');
        }

        $errors = [];
        $u_len = strlen($username);
        if ($u_len < 3 || $u_len > 54) {
            $errors[] = 'Username harus terdiri dari 3 hingga 54 karakter.';
        }
        $f_len = strlen($fullname);
        if ($f_len < 2 || $f_len > 60) {
            $errors[] = 'Nama Lengkap harus terdiri dari 2 hingga 60 karakter.';
        }
        if ($id === 0) {
            if ($ppln <= 0) {
                $errors[] = 'Pilih Paket Internet terlebih dahulu!';
            }
            $p_len = strlen($password);
            if ($p_len < 3 || $p_len > 35) {
                $errors[] = 'Password harus terdiri dari 3 hingga 35 karakter.';
            }
        } elseif (!empty($password)) {
            $p_len = strlen($password);
            if ($p_len < 3 || $p_len > 35) {
                $errors[] = 'Password baru harus terdiri dari 3 hingga 35 karakter.';
            }
        }

        if ($id > 0) {
            $cust = ORM::for_table('tbl_customers')->find_one($id);
            if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);
            $dup = ORM::for_table('tbl_customers')->where('username', $username)->where_not_equal('id', $id)->find_one();
            if ($dup) $errors[] = 'Username sudah digunakan oleh pelanggan lain.';
        } else {
            $dup = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
            if ($dup) {
                $errors[] = class_exists('Lang') ? Lang::T('Account already axist') : 'Username sudah terdaftar.';
            }
        }

        if (!empty($errors)) {
            api_out(false, implode("\n", $errors), null, 400);
        }

        if ($id === 0) {
            $cust = ORM::for_table('tbl_customers')->create();
            $cust->created_by = $admin['id'];
            $cust->created_at = date('Y-m-d H:i:s');
            $cust->balance    = (float)(_post('balance') ?: 0);
        }

        if (!empty($phonenumber) && class_exists('Lang') && method_exists('Lang', 'phoneFormat')) {
            $phonenumber = Lang::phoneFormat($phonenumber);
        }

        $cust->username       = $username;
        $cust->fullname       = $fullname;
        if (!empty($password)) {
            $cust->password = $password;
        } elseif ($id === 0) {
            $cust->password = '123456';
        }
        $cust->phonenumber    = $phonenumber;
        $cust->email          = $email;
        $cust->address        = $address;
        $cust->city           = $city;
        $cust->service_type   = $service_type;
        $cust->account_type   = $account_type;
        $cust->status         = $status;
        $cust->auto_renewal   = (int)(_post('auto_renewal') ?? 1);
        $cust->pppoe_username = $pppoe_username;
        $cust->pppoe_password = $pppoe_password;
        $cust->pppoe_ip       = $pppoe_ip;
        $cust->coordinates    = $coordinates;
        $cust->save();

        $customerId = (int)$cust->id();

        $custom_field_names  = isset($_POST['custom_field_name']) ? (array)$_POST['custom_field_name'] : [];
        $custom_field_values = isset($_POST['custom_field_value']) ? (array)$_POST['custom_field_value'] : [];
        if (!empty($custom_field_values)) {
            $totalFields = min(count($custom_field_names), count($custom_field_values));
            for ($i = 0; $i < $totalFields; $i++) {
                $cf_name  = trim((string)$custom_field_names[$i]);
                $cf_value = trim((string)$custom_field_values[$i]);
                if (!empty($cf_name) && !empty($cf_value)) {
                    try {
                        $customField = ORM::for_table('tbl_customers_fields')->create();
                        $customField->customer_id = $customerId;
                        $customField->field_name  = $cf_name;
                        $customField->field_value = $cf_value;
                        $customField->save();
                    } catch (Exception $e) {}
                }
            }
        }

        $recharged_plan_name = '';
        if ($ppln > 0) {
            $plan_q = ORM::for_table('tbl_plans');
            if (!in_array($admin['user_type'], ['SuperAdmin', 'Admin'])) {
                $plan_q->where('enabled', 1);
            }
            $plan = $plan_q->find_one($ppln);

            if ($plan) {
                $gateway = $using;
                if ($using === 'Recharge Zero' || strtolower($using) === 'zero') {
                    $zero = 1;
                    $GLOBALS['zero'] = 1;
                    $gateway = 'Recharge Zero';
                } elseif (strtolower($using) === 'balance') {
                    $gateway = 'Recharge Balance';
                }

                $server = ($plan['is_radius'] == '1') ? 'Radius' : $plan['routers'];
                $admin_name = $admin['fullname'] ?: $admin['username'];

                if (class_exists('Package') && method_exists('Package', 'rechargeUser')) {
                    if (@Package::rechargeUser($customerId, $server, $plan['id'], $gateway, $admin_name)) {
                        $recharged_plan_name = $plan['name_plan'];
                        if (class_exists('User') && method_exists('User', 'getBills')) {
                            @User::getBills($customerId);
                        }
                    }
                }
            }
        }

        $send_welcome = _post('send_welcome_message');
        if ($send_welcome === true || $send_welcome === 'true' || $send_welcome === '1' || $send_welcome === 1) {
            $welcomeMessage = '';
            if (class_exists('Lang') && method_exists('Lang', 'getNotifText')) {
                $welcomeMessage = @Lang::getNotifText('welcome_message');
            }
            if (empty(trim($welcomeMessage))) {
                $welcomeMessage = "Halo *[[name]]*,\n\nSelamat datang di layanan internet *[[company]]*.\nBerikut informasi akun Anda:\nUsername: *[[username]]*\nPassword: *[[password]]*\nLogin Portal: [[url]]\n\nTerima kasih telah bergabung!";
            }

            $clean_root_url = preg_replace('#/system/?$#i', '', rtrim(APP_URL, '/'));
            $comp_title = $config['CompanyName'] ?? 'Arneta.ID';

            $welcomeMessage = str_replace('[[company]]', $comp_title, $welcomeMessage);
            $welcomeMessage = str_replace('[[name]]', $cust['fullname'], $welcomeMessage);
            $welcomeMessage = str_replace('[[username]]', $cust['username'], $welcomeMessage);
            $welcomeMessage = str_replace('[[password]]', $cust['password'], $welcomeMessage);
            $welcomeMessage = str_replace('[[url]]', $clean_root_url . '/?_route=login', $welcomeMessage);

            $emailSubject = 'Welcome to ' . $comp_title;

            $enable_wa   = (_post('wa') === true || _post('wa') === 'true' || _post('wa') === '1' || _post('wa') === 1);
            $enable_sms  = (_post('sms') === true || _post('sms') === 'true' || _post('sms') === '1' || _post('sms') === 1);
            $enable_mail = (_post('mail') === true || _post('mail') === 'true' || _post('mail') === '1' || _post('mail') === 1);

            if ($enable_wa && !empty($cust['phonenumber'])) {
                notify_whatsapp_customer($cust['phonenumber'], $welcomeMessage);
            }
            if ($enable_sms && !empty($cust['phonenumber'])) {
                notify_sms_customer($cust['phonenumber'], $welcomeMessage);
            }
            if ($enable_mail && !empty($cust['email']) && class_exists('Message') && method_exists('Message', 'sendEmail')) {
                try {
                    @Message::sendEmail($cust['email'], $emailSubject, $welcomeMessage, $cust['email']);
                } catch (Exception $e) {
                    write_admin_log($admin, 'Gagal mengirim email welcome message: ' . $e->getMessage());
                }
            }
        }

        unset($zero, $GLOBALS['zero'], $_SESSION['notify'], $_SESSION['ntype']);

        $action_label = ($id > 0 ? 'Mengubah' : 'Menambah (QuickAdd)') . ' pelanggan: ' . $username
            . (!empty($recharged_plan_name) ? ' + Paket ' . $recharged_plan_name . ' (' . $using . ')' : '');
        write_admin_log($admin, $action_label);

        $success_msg = ($id === 0)
            ? ('Akun ' . $fullname . ' berhasil dibuat' . (!empty($recharged_plan_name) ? ' & paket ' . $recharged_plan_name . ' telah aktif!' : '!'))
            : ('Data pelanggan ' . $fullname . ' berhasil diperbarui.');

        api_out(true, $success_msg, $cust->as_array());
        break;

    case 'admin_customer_delete':
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_req('id');
        $cust = ORM::for_table('tbl_customers')->find_one($id);
        if (!$cust) api_out(false, 'Pelanggan tidak ditemukan.', null, 404);
        $uname = $cust['username'];
        ORM::for_table('tbl_user_recharges')->where('customer_id', $id)->delete_many();
        try { ORM::for_table('tbl_customers_fields')->where('customer_id', $id)->delete_many(); } catch (Exception $e) {}
        $cust->delete();
        write_admin_log($admin, 'Menghapus pelanggan: ' . $uname);
        api_out(true, 'Pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_customer_sync':
        $admin = require_admin_session($api_secret);
        $id = (int)_req('id');
        if ($id === 0 && _req('sync_all') == '1') {
            if (!in_array($admin['user_type'], ['SuperAdmin', 'Admin'])) {
                api_out(false, 'Akses ditolak.', null, 403);
            }
            @set_time_limit(-1);
            $turs = ORM::for_table('tbl_user_recharges')->where('status', 'on')->find_many();
            $synced = 0;
            foreach ($turs as $tur) {
                $p = ORM::for_table('tbl_plans')->find_one($tur['plan_id']);
                $c = ORM::for_table('tbl_customers')->find_one($tur['customer_id']);
                if ($p && $c && class_exists('Package')) {
                    $dvc = Package::getDevice($p);
                    if (file_exists($dvc)) {
                        require_once $dvc;
                        $cls = $p['device'];
                        if (class_exists($cls)) {
                            $inst = new $cls();
                            if (method_exists($inst, 'sync_customer')) @$inst->sync_customer($c, $p);
                            elseif (method_exists($inst, 'add_customer')) @$inst->add_customer($c, $p);
                            $synced++;
                        }
                    }
                }
            }
            api_out(true, 'Berhasil sinkronisasi ' . $synced . ' pelanggan aktif ke Router.');
        }

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
                        if (method_exists($dev, 'sync_customer')) @$dev->sync_customer($cust, $plan);
                        elseif (method_exists($dev, 'add_customer')) @$dev->add_customer($cust, $plan);
                        $synced++;
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

        $plan_map = [];
        foreach (ORM::for_table('tbl_plans')->find_array() as $p) {
            $plan_map[(int)$p['id']] = (float)$p['price'];
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

            // Cari harga & invoice saat transaksi berlangsung di tbl_transactions
            $trx = ORM::for_table('tbl_transactions')
                ->where('username', $r['username'])
                ->where('plan_name', $r['namebp'])
                ->where('recharged_on', $r['recharged_on'])
                ->order_by_desc('id')
                ->find_one();

            if (!$trx) {
                $trx = ORM::for_table('tbl_transactions')
                    ->where('username', $r['username'])
                    ->where('plan_name', $r['namebp'])
                    ->order_by_desc('id')
                    ->find_one();
            }

            $is_zero = (stripos($r['method'] ?? '', 'Recharge Zero') !== false || stripos($r['method'] ?? '', 'zero') !== false);
            $fallback_price = $is_zero ? 0.0 : ($plan_map[(int)$r['plan_id']] ?? 0.0);

            $r['price']   = $trx ? (float)$trx['price'] : $fallback_price;
            $r['invoice'] = ($trx && !empty($trx['invoice'])) ? $trx['invoice'] : ('INV-' . $r['id']);

            $items[] = $r;
        }

        api_out(true, 'Daftar pelanggan aktif dimuat', [
            'total' => count($items),
            'items' => $items
        ]);
        break;

    case 'admin_prepaid_recharge':
        // 100% Mengikuti alur asli system/controllers/plan.php (case 'recharge-post') & Package.php
        // Tanpa double notifikasi Telegram karena Package::rechargeUser sudah mengirim Telegram & Invoice WA
        global $zero;
        $admin = require_admin_session($api_secret);
        $id_customer = (int)(_post('customer_id') ?: _post('id_customer'));
        $planId      = (int)(_post('plan_id') ?: _post('plan'));
        $using       = trim(_post('using') ?: 'Cash');
        $req_server  = trim(_post('server') ?: _post('routers'));

        if ($id_customer <= 0 || $planId <= 0) {
            api_out(false, class_exists('Lang') ? Lang::T('All field is required') : 'Pilih pelanggan dan paket terlebih dahulu.', null, 400);
        }

        $cust = (class_exists('User') && method_exists('User', '_info'))
            ? User::_info($id_customer)
            : ORM::for_table('tbl_customers')->find_one($id_customer);
        if (!$cust) {
            api_out(false, class_exists('Lang') ? Lang::T('Customer not found') : 'Pelanggan tidak ditemukan.', null, 404);
        }

        $plan = ORM::for_table('tbl_plans')->find_one($planId);
        if (!$plan) {
            api_out(false, class_exists('Lang') ? Lang::T('Plan not found') : 'Paket internet tidak ditemukan.', null, 404);
        }

        if (!in_array($admin['user_type'], ['SuperAdmin', 'Admin']) && (int)$plan['enabled'] !== 1) {
            api_out(false, 'Paket ini berstatus Hidden dan hanya dapat diakses oleh SuperAdmin/Admin.', null, 403);
        }

        $server = ($plan['is_radius'] == '1')
            ? 'Radius'
            : (!empty($plan['routers']) ? $plan['routers'] : (!empty($req_server) ? $req_server : 'radius'));

        $bills = [];
        $add_cost = 0;
        if (class_exists('User') && method_exists('User', 'getBills')) {
            list($bills, $add_cost) = User::getBills($id_customer);
        }

        $tax_enable = isset($config['enable_tax']) ? $config['enable_tax'] : 'no';
        $tax_rate_setting = isset($config['tax_rate']) ? $config['tax_rate'] : null;
        $custom_tax_rate = isset($config['custom_tax_rate']) ? (float)$config['custom_tax_rate'] : null;
        $tax_rate = ($tax_rate_setting === 'custom') ? $custom_tax_rate : $tax_rate_setting;
        $tax = ($tax_enable === 'yes' && class_exists('Package') && method_exists('Package', 'tax'))
            ? Package::tax($plan['price'], $tax_rate)
            : 0;

        $total_cost = (float)$plan['price'] + (float)$add_cost + (float)$tax;

        $gateway = ucwords($using);
        $channel = $admin['fullname'] ?: $admin['username'];
        $zero = 0;
        $GLOBALS['zero'] = 0;

        $using_lower = strtolower($using);
        if (($using_lower === 'balance' || $using_lower === 'recharge balance') && ($config['enable_balance'] ?? 'yes') === 'yes') {
            if ((float)$cust['balance'] < $total_cost) {
                api_out(false, class_exists('Lang') ? Lang::T('insufficient balance') : 'Saldo pelanggan tidak mencukupi (Butuh ' . number_format($total_cost, 0, ',', '.') . ').', null, 400);
            }
            $gateway = 'Recharge Balance';
            $using_lower = 'balance';
        } elseif ($using_lower === 'zero' || $using_lower === 'recharge zero') {
            $add_cost = 0;
            $zero = 1;
            $GLOBALS['zero'] = 1;
            $gateway = 'Recharge Zero';
        }

        $ok = false;
        if (class_exists('Package') && method_exists('Package', 'rechargeUser')) {
            $ok = @Package::rechargeUser($id_customer, $server, $planId, $gateway, $channel);
        }
        unset($zero, $GLOBALS['zero']);

        if ($ok) {
            if ($using_lower === 'balance') {
                if (class_exists('Balance') && method_exists('Balance', 'min')) {
                    Balance::min($cust['id'], $total_cost);
                } else {
                    $c_upd = ORM::for_table('tbl_customers')->find_one($cust['id']);
                    if ($c_upd) {
                        $c_upd->balance = max(0, (float)$c_upd['balance'] - $total_cost);
                        $c_upd->save();
                    }
                }
            }

            $in = ORM::for_table('tbl_transactions')->where('username', $cust['username'])->order_by_desc('id')->find_one();
            $price_fmt = (class_exists('Lang') && method_exists('Lang', 'moneyFormat'))
                ? Lang::moneyFormat($in ? $in['price'] : $plan['price'])
                : (($config['currency_code'] ?? 'Rp.') . ' ' . number_format((float)($in ? $in['price'] : $plan['price']), 0, ',', '.'));

            write_admin_log($admin, '[' . $admin['username'] . ']: Recharge ' . $cust['username'] . ' [' . ($in ? $in['plan_name'] : $plan['name_plan']) . '][' . $price_fmt . ']');
            unset($_SESSION['notify'], $_SESSION['ntype']);

            api_out(true, 'Paket ' . $plan['name_plan'] . ' berhasil diaktifkan untuk ' . $cust['fullname'] . '!', [
                'invoice' => $in ? $in->as_array() : null
            ]);
        } else {
            $err = isset($_SESSION['notify']) ? trim(strip_tags($_SESSION['notify'])) : 'Gagal mengaktifkan paket ke Router ' . $server . '.';
            unset($_SESSION['notify'], $_SESSION['ntype']);
            api_out(false, $err, null, 500);
        }
        break;

    case 'admin_prepaid_delete':
        // Sesuai case 'delete' di system/controllers/plan.php
        $admin = require_admin_session($api_secret, ['SuperAdmin', 'Admin']);
        $id = (int)_req('id');
        $d = ORM::for_table('tbl_user_recharges')->find_one($id);
        if (!$d) api_out(false, 'Data langganan tidak ditemukan.', null, 404);

        if (function_exists('run_hook')) {
            @run_hook('delete_customer_active_plan');
        }

        $p = ORM::for_table('tbl_plans')->find_one($d['plan_id']);
        $c = (class_exists('User') && method_exists('User', '_info'))
            ? User::_info($d['customer_id'])
            : ORM::for_table('tbl_customers')->find_one($d['customer_id']);

        if ($p && $c && class_exists('Package') && method_exists('Package', 'getDevice')) {
            $dvc = Package::getDevice($p);
            if (file_exists($dvc)) {
                require_once $dvc;
                $d_cls = $p['device'];
                if (class_exists($d_cls)) {
                    $dev = new $d_cls();
                    if (method_exists($dev, 'remove_customer')) @$dev->remove_customer($c, $p);
                }
            }
        }
        $uname = $d['username'];
        $pname = $d['namebp'];
        $d->delete();
        write_admin_log($admin, '[' . $admin['username'] . ']: Delete Plan for Customer ' . $uname . ' [' . $pname . ']');
        api_out(true, 'Paket pelanggan ' . $uname . ' berhasil dihapus.');
        break;

    case 'admin_deposit':
        // 100% Mengikuti alur asli system/controllers/plan.php (case 'deposit-post') & Package::rechargeBalance()
        $admin = require_admin_session($api_secret);
        $user_id    = (int)(_post('customer_id') ?: _post('id_customer'));
        $plan_id    = (int)(_post('plan_id') ?: _post('id_plan'));
        $custom_amt = (float)_post('amount');
        $note       = trim(_post('note') ?: '');

        $c = ORM::for_table('tbl_customers')->find_one($user_id);
        if (!$c) {
            api_out(false, 'Pelanggan tidak ditemukan.', null, 404);
        }

        if (function_exists('run_hook')) {
            @run_hook('deposit_customer');
        }

        $admin_name = $admin['fullname'] ?: $admin['username'];
        $currency   = $config['currency_code'] ?? 'Rp.';
        $trxId      = 0;
        $plan_label = 'Balance';
        $price_amt  = 0.0;

        if ($custom_amt != 0) {
            $plan = [
                'name_plan' => class_exists('Lang') ? Lang::T('Balance') : 'Balance',
                'price'     => $custom_amt
            ];
            $plan_label = $plan['name_plan'];
            $price_amt  = $custom_amt;

            if (class_exists('Package') && method_exists('Package', 'rechargeBalance')) {
                $trxId = (int)@Package::rechargeBalance($c, $plan, 'Deposit', $admin_name, $note);
            }
        } elseif ($plan_id > 0) {
            $p = ORM::for_table('tbl_plans')->find_one($plan_id);
            if (!$p) {
                api_out(false, 'Paket Saldo tidak ditemukan.', null, 404);
            }
            $plan_label = $p['name_plan'];
            $price_amt  = (float)$p['price'];

            if (class_exists('Package') && method_exists('Package', 'rechargeBalance')) {
                $trxId = (int)@Package::rechargeBalance($c, $p, 'Deposit', $admin_name, $note);
            }
        } else {
            api_out(false, 'Pilih paket saldo atau masukkan nominal top-up terlebih dahulu.', null, 400);
        }

        if ($trxId > 0) {
            $c_fresh = ORM::for_table('tbl_customers')->find_one($user_id);
            $new_bal = $c_fresh ? (float)$c_fresh['balance'] : ((float)$c['balance'] + $price_amt);
            $in = ORM::for_table('tbl_transactions')->find_one($trxId);

            // Package::rechargeBalance sudah otomatis mengirim WA/SMS/Email ke pelanggan sesuai $config['user_notification_payment']
            // Kita tambahkan 1 kali notifikasi Telegram ke Admin karena di Package::rechargeBalance asli belum ada sendTelegram
            $tg_msg = "#u" . $c['username'] . " " . $c['fullname'] . " #deposit #Balance \n"
                . $plan_label
                . "\nGateway: Deposit"
                . "\nChannel: " . $admin_name
                . "\nAmount: " . $currency . " " . number_format($price_amt, 0, ',', '.')
                . "\nNew Balance: " . $currency . " " . number_format($new_bal, 0, ',', '.')
                . (!empty($note) ? "\nNote:\n" . $note : "");
            notify_telegram_admin($tg_msg);

            write_admin_log($admin, '[' . $admin['username'] . ']: Deposit ' . $c['username'] . ' [' . $plan_label . '][' . $currency . ' ' . number_format($price_amt, 0, ',', '.') . ']');
            unset($_SESSION['notify'], $_SESSION['ntype']);

            api_out(true, 'Saldo ' . $c['fullname'] . ' berhasil ditambah menjadi ' . $currency . ' ' . number_format($new_bal, 0, ',', '.'), [
                'balance' => $new_bal,
                'invoice' => $in ? $in->as_array() : null
            ]);
        } else {
            api_out(false, 'Gagal memproses isi ulang saldo pelanggan.', null, 500);
        }
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
        // Sesuai case 'voucher-post' di system/controllers/plan.php
        $admin = require_admin_session($api_secret);
        $plan_id     = (int)(_post('plan_id') ?: _post('plan'));
        $count       = max(1, min(500, (int)(_post('count') ?: _post('numbervoucher') ?: 10)));
        $length_code = max(4, min(16, (int)(_post('length_code') ?: _post('lengthcode') ?: 6)));
        $prefix      = trim(_post('prefix'));
        $format      = _post('voucher_format') ?: 'up';
        $req_server  = trim(_post('server') ?: _post('routers'));

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) api_out(false, 'Pilih paket voucher terlebih dahulu.', null, 404);

        if (!empty($prefix)) {
            $cfg_p = ORM::for_table('tbl_appconfig')->where('setting', 'voucher_prefix')->find_one();
            if ($cfg_p) {
                $cfg_p->value = $prefix;
                $cfg_p->save();
            } else {
                $cfg_p = ORM::for_table('tbl_appconfig')->create();
                $cfg_p->setting = 'voucher_prefix';
                $cfg_p->value = $prefix;
                $cfg_p->save();
            }
        }

        if (function_exists('run_hook')) {
            @run_hook('create_voucher');
        }

        $routers = !empty($req_server) ? $req_server : ($plan['routers'] ?: 'radius');
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
        // Sesuai case 'refill-post' di system/controllers/plan.php
        $admin = require_admin_session($api_secret);
        $customer_id = (int)(_post('customer_id') ?: _post('id_customer'));
        $raw_code    = trim(_post('code'));
        $code        = (class_exists('Text') && method_exists('Text', 'alphanumeric'))
            ? Text::alphanumeric($raw_code, "-_.,")
            : $raw_code;

        $user = ORM::for_table('tbl_customers')->where('id', $customer_id)->find_one();
        if (!$user) api_out(false, 'Pilih pelanggan terlebih dahulu.', null, 404);

        $v1 = ORM::for_table('tbl_voucher')->where_raw("BINARY code = ?", [$code])->where('status', 0)->find_one();
        if (!$v1) {
            api_out(false, class_exists('Lang') ? Lang::T('Voucher Not Valid') : 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);
        }

        if (function_exists('run_hook')) {
            @run_hook('refill_customer');
        }

        if (class_exists('Package') && Package::rechargeUser($user['id'], $v1['routers'], $v1['id_plan'], 'Voucher', $code)) {
            $v1->status = '1';
            $v1->user   = $user['username'];
            $v1->save();
            $in = ORM::for_table('tbl_transactions')->where('username', $user['username'])->order_by_desc('id')->find_one();
            unset($_SESSION['notify'], $_SESSION['ntype']);
            api_out(true, 'Voucher ' . $code . ' berhasil diaktifkan ke ' . $user['fullname'] . '!', [
                'invoice' => $in ? $in->as_array() : null
            ]);
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
    // 5. PAKET INTERNET, ROUTERS & DAFTAR PAYMENT USINGS
    // =========================================================================
    case 'admin_plans':
        $admin = require_admin_session($api_secret);
        $is_superadmin = ($admin['user_type'] === 'SuperAdmin');
        $type_filter   = trim(_req('type'));
        $router_filter = trim(_req('routers') ?: _req('server'));

        $bw_rows = ORM::for_table('tbl_bandwidth')->order_by_desc('id')->find_array();
        $bw_map = [];
        foreach ($bw_rows as $b) {
            $bw_map[(int)$b['id']] = $b;
        }

        $q = ORM::for_table('tbl_plans');
        if (!$is_superadmin) {
            $q->where('enabled', 1);
        }
        if ($type_filter !== '' && strtolower($type_filter) !== 'all') {
            $q->where('type', $type_filter);
        }
        if ($router_filter !== '' && strtolower($router_filter) !== 'all') {
            if (strtolower($router_filter) === 'radius') {
                $q->where_raw('(is_radius = 1 OR LOWER(routers) = ?)', ['radius']);
            } else {
                $q->where('routers', $router_filter);
            }
        }

        $rows = $q->order_by_asc('price')->find_array();
        $plans = [];
        foreach ($rows as $r) {
            $p_type    = strtoupper(trim($r['type']));
            $prepaid   = strtolower(trim($r['prepaid'] ?? 'yes'));
            $is_hidden = ((int)($r['enabled'] ?? 1) === 0);

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

        // Ambil daftar metode pembayaran sesuai plan.php & quickadd
        $raw_usings = !empty($config['payment_usings']) ? explode(',', $config['payment_usings']) : [];
        $usings = array_values(array_filter(array_unique(array_map('trim', $raw_usings))));
        if (empty($usings)) {
            $usings[] = class_exists('Lang') ? Lang::T('Cash') : 'Cash';
        }
        if (($config['enable_balance'] ?? 'yes') === 'yes' && !in_array('Balance', $usings) && !in_array('balance', $usings)) {
            $usings[] = 'Balance';
        }
        if (!in_array('Recharge Zero', $usings)) {
            $usings[] = 'Recharge Zero';
        }

        api_out(true, 'Daftar paket internet & bandwidth dimuat', [
            'is_superadmin' => $is_superadmin,
            'plans'         => $plans,
            'usings'        => $usings,
            'bandwidths'    => $bw_rows,
            'routers'       => ORM::for_table('tbl_routers')->where('enabled', 1)->find_array(),
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
    // 6. JARINGAN & MIKROTIK MONITOR
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

                $if_resp = $client->sendSync(new PEAR2\Net\RouterOS\Request('/interface/print'));
                $interfaces_list    = [];
                $interfaces_traffic = [];
                $default_iface      = '';

                foreach ($if_resp as $iface) {
                    if ($iface->getType() !== PEAR2\Net\RouterOS\Response::TYPE_DATA) continue;
                    $if_name = $iface->getProperty('name');
                    if (empty($if_name)) continue;

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
        require_admin_session($api_secret);
        $router_id = (int)_req('router_id');
        $sub_type  = _req('sub_type') ?: 'logs';

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
    // 7. LAPORAN TRANSAKSI, PESAN, HALAMAN STATIS & LOGS
    // =========================================================================
    case 'admin_reports':
        require_admin_session($api_secret);
        $start_date = trim(_req('start_date') ?: date('Y-m-01'));
        $end_date   = trim(_req('end_date') ?: date('Y-m-d'));
        $search     = trim(_req('search'));
        $limit      = max(20, min(300, (int)(_req('limit') ?: 150)));

        $q = ORM::for_table('tbl_transactions')
            ->where_gte('recharged_on', $start_date)
            ->where_lte('recharged_on', $end_date)
            ->where_not_equal('method', 'Customer - Balance')
            ->where_not_like('method', 'Recharge Balance%');

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
                notify_whatsapp_customer($cust['phonenumber'], $msg);
                $ok = true;
            }
            if (($via === 'sms' || $via === 'both') && !empty($cust['phonenumber'])) {
                notify_sms_customer($cust['phonenumber'], $msg);
                $ok = true;
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

        $clean_root_url = preg_replace('#/system/?$#i', '', rtrim(APP_URL, '/'));
        if (ob_get_length()) ob_clean();
        header('Location: ' . $clean_root_url . '/?_route=' . ltrim($target, '/'));
        exit();

    // =========================================================================
    // 9. ENDPOINT APK PELANGGAN (MENGGUNAKAN FUNGSI ASLI VERSI WEB & SYSTEM/API.PHP)
    // =========================================================================
    case 'login':
        $username = trim(_post('username'));
        $password = _post('password');

        if (empty($username) || empty($password)) {
            api_out(false, class_exists('Lang') ? Lang::T('Enter username and password') : 'Username dan Password wajib diisi.', null, 400);
        }

        $user = ORM::for_table('tbl_customers')->where('username', $username)->find_one();
        if (!$user || !check_user_password($password, $user['password'])) {
            api_out(false, class_exists('Lang') ? Lang::T('Invalid Username or Password') : 'Username atau Password salah.', null, 401);
        }

        if (!empty($user['status']) && $user['status'] !== 'Active') {
            api_out(false, (class_exists('Lang') ? Lang::T('This account status') : 'Status akun Anda') . ': ' . $user['status'], null, 403);
        }

        $user->last_login = date('Y-m-d H:i:s');
        $user->save();
        $_SESSION['uid'] = $user['id'];
        if (class_exists('User') && method_exists('User', 'setCookie')) {
            @User::setCookie($user['id']);
        }
        if (function_exists('_log')) {
            @_log($username . ' ' . (class_exists('Lang') ? Lang::T('Login Successful') : 'Login Successful'), 'User', $user['id']);
        }

        $native_token = create_native_token('c', $user['id'], $api_secret);
        api_out(true, class_exists('Lang') ? Lang::T('Login Successful') : 'Login berhasil', [
            'token'        => $native_token,
            'native_token' => $native_token
        ]);
        break;

    case 'dashboard':
        $user = require_customer_session($api_secret);
        if (class_exists('User') && method_exists('User', '_info')) {
            $u_info = User::_info($user['id']);
            if ($u_info) $user = $u_info;
        }

        $recharges = [];
        if (class_exists('User') && method_exists('User', '_billing')) {
            $bills = User::_billing($user['id']);
            if ($bills) {
                foreach ($bills as $b) {
                    $recharges[] = ($b instanceof ORM) ? $b->as_array() : (array)$b;
                }
            }
        } else {
            $recharges = ORM::for_table('tbl_user_recharges')->where('customer_id', $user['id'])->order_by_desc('id')->find_array();
        }

        foreach ($recharges as &$b_item) {
            if ($b_item['status'] === 'on' && !empty($b_item['expiration']) && time() > strtotime($b_item['expiration'] . ' ' . ($b_item['time'] ?: '23:59:59'))) {
                $ur_obj = ORM::for_table('tbl_user_recharges')->find_one($b_item['id']);
                if ($ur_obj) {
                    if ($config['enable_balance'] === 'yes' && (int)$user['auto_renewal'] === 1 && class_exists('Package') && method_exists('Package', 'changeTo')) {
                        $plan_info = ORM::for_table('tbl_plans')->find_one($ur_obj['plan_id']);
                        if ($plan_info && (int)$plan_info['enabled'] === 1 && (float)$user['balance'] >= (float)$plan_info['price']) {
                            if (@Package::rechargeUser($user['id'], $plan_info['routers'], $plan_info['id'], 'Customer', 'Balance')) {
                                if (class_exists('Balance')) Balance::min($user['id'], $plan_info['price']);
                                $user = ORM::for_table('tbl_customers')->find_one($user['id']);
                                $ur_fresh = ORM::for_table('tbl_user_recharges')->find_one($b_item['id']);
                                if ($ur_fresh) $b_item = $ur_fresh->as_array();
                                continue;
                            }
                        }
                    }
                }
            }
        }
        unset($b_item);

        $unpaid_rows = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->order_by_desc('id')
            ->find_many();

        $unpaid_bills = [];
        foreach ($unpaid_rows as $ur) {
            if (!verify_already_recharged($ur)) {
                $unpaid_bills[] = parse_qris_trx($ur, $config);
            }
        }

        api_out(true, 'Data beranda dimuat', [
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
            'unpaid_bill'      => $unpaid_bills[0] ?? null
        ]);
        break;

    case 'plans':
        $user = require_customer_session($api_secret);
        $cust_type = strtoupper(trim($user['service_type'] ?: 'OTHERS'));

        $plans = ORM::for_table('tbl_plans')
            ->where('enabled', 1)
            ->order_by_asc('price')
            ->find_array();

        $internet_plans = [];
        $balance_plans  = [];

        foreach ($plans as $p) {
            $p_type  = strtoupper(trim($p['type']));
            $prepaid = strtolower(trim($p['prepaid'] ?? 'yes'));

            $item = [
                'id'        => (int)$p['id'],
                'name_plan' => $p['name_plan'],
                'price'     => (float)$p['price'],
                'type'      => ($p_type === 'PPPOE') ? 'PPPoE' : (($p_type === 'HOTSPOT') ? 'Hotspot' : $p['type']),
                'prepaid'   => $p['prepaid'],
                'enabled'   => (int)$p['enabled'],
                'validity'  => $p['validity'] . ' ' . $p['validity_unit'],
                'routers'   => $p['routers']
            ];

            if ($p_type === 'BALANCE') {
                if ($prepaid === 'yes') {
                    $balance_plans[] = $item;
                }
                continue;
            }

            if ($p_type === 'HOTSPOT' && $prepaid === 'no') {
                continue;
            }

            if ($cust_type === 'HOTSPOT' && $p_type === 'HOTSPOT') {
                $internet_plans[] = $item;
            } elseif ($cust_type === 'PPPOE' && $p_type === 'PPPOE') {
                $internet_plans[] = $item;
            } elseif ($cust_type !== 'HOTSPOT' && $cust_type !== 'PPPOE') {
                if ($p_type === 'HOTSPOT' || $p_type === 'PPPOE') {
                    $internet_plans[] = $item;
                }
            }
        }

        api_out(true, 'Daftar paket dimuat', [
            'internet_plans'   => $internet_plans,
            'balance_plans'    => $balance_plans,
            'payment_gateways' => get_active_gateways($config)
        ]);
        break;

    case 'transactions':
        $user = require_customer_session($api_secret);
        $trx = ORM::for_table('tbl_transactions')
            ->where('username', $user['username'])
            ->order_by_desc('id')
            ->limit(30)
            ->find_array();
        api_out(true, 'Riwayat transaksi', $trx);
        break;

    case 'toggle_renewal':
        $user = require_customer_session($api_secret);
        $user->auto_renewal = ($user->auto_renewal == 1) ? 0 : 1;
        $user->save();
        api_out(true, 'Status perpanjangan otomatis diubah.', ['auto_renewal' => (int)$user->auto_renewal]);
        break;

    case 'redeem_voucher':
        $user = require_customer_session($api_secret);
        $raw_code = trim(_post('code'));
        $code = function_exists('alphanumeric') ? alphanumeric($raw_code, "-_.,") : $raw_code;

        if (empty($code)) {
            api_out(false, class_exists('Lang') ? Lang::T('Voucher Not Valid') : 'Kode voucher tidak boleh kosong.', null, 400);
        }

        $v1 = ORM::for_table('tbl_voucher')
            ->where_raw("BINARY code = ?", [$code])
            ->where('status', 0)
            ->find_one();

        if (function_exists('run_hook')) {
            @run_hook('customer_activate_voucher');
        }

        if (!$v1) {
            $is_registered_cust = ORM::for_table('tbl_customers')->where('username', $code)->find_one();
            if (!$is_registered_cust) {
                $routers = ORM::for_table('tbl_routers')->where('enabled', 1)->find_many();
                foreach ($routers as $r) {
                    $client = connect_ros($r['ip_address'], $r['username'], $r['password']);
                    if (!$client) continue;
                    try {
                        $print_req = new PEAR2\Net\RouterOS\Request('/ip/hotspot/user/print');
                        $print_req->setQuery(PEAR2\Net\RouterOS\Query::where('name', $code));
                        $resp = $client->sendSync($print_req);
                        foreach ($resp as $item) {
                            if ($item->getType() === PEAR2\Net\RouterOS\Response::TYPE_DATA) {
                                $ros_id  = $item->getProperty('.id');
                                $profile = trim($item->getProperty('profile') ?: '');
                                $uptime  = $item->getProperty('uptime') ?: '0s';

                                if ($uptime !== '0s' && $uptime !== '00:00:00' && (int)$item->getProperty('bytes-in') > 0) {
                                    api_out(false, 'Voucher MikroTik tersebut sudah pernah digunakan.', null, 400);
                                }

                                $plan = ORM::for_table('tbl_plans')
                                    ->where('name_plan', $profile)
                                    ->where('routers', $r['name'])
                                    ->find_one();
                                if (!$plan) {
                                    $plan = ORM::for_table('tbl_plans')->where('name_plan', $profile)->find_one();
                                }

                                if ($plan) {
                                    if (!empty($ros_id) && $code !== $user['username']) {
                                        $rem_req = new PEAR2\Net\RouterOS\Request('/ip/hotspot/user/remove');
                                        $rem_req->setArgument('numbers', $ros_id);
                                        @$client->sendSync($rem_req);
                                    }

                                    $v1 = ORM::for_table('tbl_voucher')->create();
                                    $v1->type         = 'Hotspot';
                                    $v1->routers      = $r['name'];
                                    $v1->id_plan      = $plan['id'];
                                    $v1->code         = $code;
                                    $v1->user         = '0';
                                    $v1->status       = '0';
                                    $v1->generated_by = 0;
                                    $v1->save();
                                    break 2;
                                }
                            }
                        }
                    } catch (Exception $e) {}
                }
            }
        }

        if ($v1) {
            if (class_exists('Package') && Package::rechargeUser($user['id'], $v1['routers'], $v1['id_plan'], "Voucher", $code)) {
                $v1->status    = "1";
                $v1->used_date = date('Y-m-d H:i:s');
                $v1->user      = $user['username'];
                $v1->save();
                unset($_SESSION['notify'], $_SESSION['ntype']);
                api_out(true, class_exists('Lang') ? Lang::T('Activation Vouchers Successfully') : 'Voucher berhasil diaktifkan!');
            } else {
                api_out(false, "Failed to refill account", null, 500);
            }
        } else {
            api_out(false, class_exists('Lang') ? Lang::T('Voucher Not Valid') : 'Kode voucher tidak ditemukan atau sudah terpakai.', null, 404);
        }
        break;

    case 'buy':
    case 'qris_buy':
        $user = require_customer_session($api_secret);
        $plan_id = (int)_post('plan_id');
        $gateway_choice = trim(_post('gateway') ?: _post('method'));

        if (function_exists('run_hook')) {
            @run_hook('customer_buy_plan');
        }

        $plan = ORM::for_table('tbl_plans')->find_one($plan_id);
        if (!$plan) {
            api_out(false, class_exists('Lang') ? Lang::T('Plan Not found') : 'Paket tidak ditemukan.', null, 404);
        }

        if ($gateway_choice === 'balance') {
            if (($config['enable_balance'] ?? 'yes') !== 'yes') {
                api_out(false, 'Fitur pembayaran menggunakan Saldo sedang dinonaktifkan.', null, 400);
            }
            if (strtoupper(trim($plan['type'])) === 'BALANCE') {
                api_out(false, 'Paket Topup Saldo tidak dapat dibayar menggunakan Saldo.', null, 400);
            }

            $add_cost = 0;
            if (class_exists('User') && method_exists('User', 'getBills')) {
                list($bills, $add_cost) = User::getBills($user['id']);
            }

            $total_cost = (float)$plan['price'] + (float)$add_cost;
            if ((float)$user['balance'] < $total_cost) {
                api_out(false, class_exists('Lang') ? Lang::T('insufficient balance') : 'Saldo Anda tidak mencukupi.', null, 400);
            }

            $router_target = ($plan['is_radius'] == '1') ? 'Radius' : $plan['routers'];
            if (class_exists('Package') && Package::rechargeUser($user['id'], $router_target, $plan['id'], 'Customer', 'Balance')) {
                if (class_exists('Balance') && method_exists('Balance', 'min')) {
                    Balance::min($user['id'], $total_cost);
                } else {
                    $user->balance = (float)$user->balance - $total_cost;
                    $user->save();
                }
                if (function_exists('_log')) {
                    @_log($user['username'] . ' BUY ' . $plan['name_plan'] . ' With Balance', 'User', $user['id']);
                }
                api_out(true, 'Paket ' . $plan['name_plan'] . ' berhasil diaktifkan menggunakan Saldo!');
            }
            api_out(false, 'Gagal mengaktifkan paket ke Router.', null, 500);
        }

        $existing = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->find_one();

        if ($existing && !verify_already_recharged($existing)) {
            api_out(false, 'Anda masih memiliki transaksi pembayaran yang belum diselesaikan.', parse_qris_trx($existing, $config), 400);
        }

        $active_gws = array_filter(array_map('trim', explode(',', $config['payment_gateway'] ?? 'viaqris')));
        $gateway = (!empty($gateway_choice) && in_array($gateway_choice, $active_gws))
            ? $gateway_choice
            : ($active_gws[0] ?? 'viaqris');

        $router_id   = 0;
        $router_name = $plan['routers'];
        if (strtoupper(trim($plan['type'])) === 'BALANCE') {
            $router_id   = 0;
            $router_name = 'balance';
        } elseif ($plan['is_radius'] == '1' || $plan['routers'] === 'radius') {
            $router_id   = 0;
            $router_name = 'radius';
        } elseif (!empty($plan['routers'])) {
            $r_row = ORM::for_table('tbl_routers')->where('name', $plan['routers'])->find_one();
            if ($r_row) {
                $router_id   = (int)$r_row['id'];
                $router_name = $r_row['name'];
            }
        }

        $add_cost = 0;
        if ($router_name !== 'balance' && class_exists('User') && method_exists('User', 'getBills')) {
            list($bills, $add_cost) = User::getBills($user['id']);
        }

        $d = ORM::for_table('tbl_payment_gateway')->create();
        $d->username     = $user['username'];
        $d->gateway      = $gateway;
        $d->plan_id      = $plan['id'];
        $d->plan_name    = $plan['name_plan'];
        $d->routers_id   = $router_id;
        $d->routers      = $router_name;
        $d->price        = (float)$plan['price'] + (float)$add_cost;
        $d->created_date = date('Y-m-d H:i:s');
        $d->status       = 1;
        $d->save();

        $gw_file = 'system/paymentgateway/' . $gateway . '.php';
        if (file_exists($gw_file)) {
            include_once $gw_file;
            $fn = $gateway . '_create_transaction';
            if (function_exists($fn)) {
                $GLOBALS['API_INTERCEPT_MODE']   = 'buy';
                $GLOBALS['API_INTERCEPT_TRX_ID'] = $d->id();
                $fn($d, $user);
                $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                $updated = ORM::for_table('tbl_payment_gateway')->find_one($d->id());
                api_out(true, 'Tagihan berhasil dibuat.', parse_qris_trx($updated, $config));
            }
        }
        api_out(true, 'Tagihan berhasil dibuat.', parse_qris_trx($d, $config));
        break;

    case 'payment_detail':
        $user = require_customer_session($api_secret);
        $trx_id = (int)_req('id');
        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->find_one($trx_id);

        if (!$trx) api_out(false, 'Tagihan tidak ditemukan.', null, 404);
        $is_paid = verify_already_recharged($trx);
        api_out(true, $is_paid ? 'Pembayaran diterima!' : 'Menunggu pembayaran.', [
            'paid' => $is_paid,
            'bill' => parse_qris_trx($trx, $config)
        ]);
        break;

    case 'check_payment':
    case 'qris_status':
        $user = require_customer_session($api_secret);
        $trx_id = (int)_req('id');

        $lock_file = sys_get_temp_dir() . '/arneta_paylock_' . $trx_id . '.lock';
        $lock_fp = @fopen($lock_file, 'c+');
        if ($lock_fp) {
            if (!@flock($lock_fp, LOCK_EX | LOCK_NB)) {
                @flock($lock_fp, LOCK_EX);
                $trx_after = ORM::for_table('tbl_payment_gateway')->where('username', $user['username'])->find_one($trx_id);
                @flock($lock_fp, LOCK_UN);
                @fclose($lock_fp);
                $is_paid_after = verify_already_recharged($trx_after);
                api_out(true, $is_paid_after ? 'Pembayaran diterima!' : 'Sedang memeriksa pembayaran...', [
                    'paid' => $is_paid_after,
                    'bill' => parse_qris_trx($trx_after, $config)
                ]);
            }
            $GLOBALS['API_LOCK_HANDLE'] = $lock_fp;
        }

        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->find_one($trx_id);

        if (!$trx) api_out(false, 'Tagihan tidak ditemukan.', null, 404);

        if (!verify_already_recharged($trx) && (int)$trx['status'] === 1 && !empty($trx['gateway'])) {
            $gw_file = 'system/paymentgateway/' . $trx['gateway'] . '.php';
            if (file_exists($gw_file)) {
                include_once $gw_file;
                $status_fn = $trx['gateway'] . '_get_status';
                if (function_exists($status_fn)) {
                    $GLOBALS['API_INTERCEPT_MODE']   = 'check_payment';
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = $trx['id'];
                    @$status_fn($trx, $user);
                    $GLOBALS['API_INTERCEPT_TRX_ID'] = null;
                    $trx = ORM::for_table('tbl_payment_gateway')->find_one($trx_id);
                }
            }
        }
        $is_paid = verify_already_recharged($trx);
        api_out(true, $is_paid ? 'Pembayaran diterima! Layanan/Saldo Anda telah aktif.' : 'Pembayaran belum masuk.', [
            'paid' => $is_paid,
            'bill' => parse_qris_trx($trx, $config)
        ]);
        break;

    case 'cancel_payment':
    case 'qris_cancel':
        $user = require_customer_session($api_secret);
        $trx = ORM::for_table('tbl_payment_gateway')
            ->where('username', $user['username'])
            ->where('status', 1)
            ->find_one((int)_req('id'));

        if ($trx && !verify_already_recharged($trx)) {
            $trx->status = 4;
            $trx->save();
        }
        api_out(true, 'Tagihan berhasil dibatalkan.');
        break;

    default:
        api_out(true, 'PHPNuxBill Complete API v12 Ready (Official Package.php & plan.php Integrated)');
        break;
}
