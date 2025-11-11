-- ============================================
-- TEST DATA (REWRITTEN) - COMPLETE ORDER FLOW
-- ============================================

-- ============================================
-- OPTIONAL DEMO RESET (Use carefully)
-- ============================================
-- SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS;
-- SET FOREIGN_KEY_CHECKS = 0;
-- -- Delete in dependency-safe order
-- DELETE FROM payment_transaction WHERE id BETWEEN 1 AND 999999; -- if exists
-- DELETE FROM order_item WHERE id BETWEEN 1 AND 999999;          -- if exists
-- DELETE FROM orders WHERE id BETWEEN 1 AND 999999;               -- if exists
-- DELETE FROM cart_item WHERE id BETWEEN 1 AND 999999;           -- if exists
-- DELETE FROM cart WHERE id IN (11);
-- DELETE FROM user_address WHERE id IN (11,12);
-- DELETE FROM product WHERE id BETWEEN 1001 AND 1999;
-- DELETE FROM product_category WHERE id BETWEEN 11 AND 19;
-- DELETE FROM store_address WHERE id BETWEEN 11 AND 19;
-- DELETE FROM store WHERE id BETWEEN 11 AND 19;
-- DELETE FROM user_role WHERE user_id IN (11,12,50);
-- DELETE FROM users WHERE id IN (11,12,50);
-- SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- 0) Optional: clear old demo ids (only if you want a clean slate)
-- DELETE FROM user_role WHERE user_id IN (11,12);
-- DELETE FROM user_address WHERE user_id IN (11);
-- DELETE FROM cart WHERE id IN (11);
-- DELETE FROM product WHERE id BETWEEN 1001 AND 1999;
-- DELETE FROM product_category WHERE id BETWEEN 11 AND 19;
-- DELETE FROM store_address WHERE id BETWEEN 11 AND 19;
-- DELETE FROM store WHERE id BETWEEN 11 AND 19;
-- DELETE FROM users WHERE id IN (11,12);

-- 1. Users
INSERT IGNORE INTO users
(id, username, email, password_hash, full_name, phone, status, date_of_birth, gender, created_at, updated_at)
VALUES
(11, 'linh.ng', 'linh.nguyen@test.vn',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- password
 'Nguyễn Ngọc Linh', '0912000111', 'ACTIVE', '1996-03-22', 'FEMALE', NOW(), NOW()),
(12, 'quang.pham', 'quang.pham@test.vn',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- password
 'Phạm Minh Quang', '0988000222', 'ACTIVE', '1992-11-05', 'MALE', NOW(), NOW());

-- Admin user (login: admin / password). Change hash if you want a different password.
INSERT IGNORE INTO users
 (id, username, email, password_hash, full_name, phone, status, date_of_birth, gender, created_at, updated_at)
VALUES
 (50, 'admin', 'admin@test.vn',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- bcrypt for 'password'
  'System Administrator', '0900000000', 'ACTIVE', '1990-01-01', 'OTHER', NOW(), NOW());

-- 2. Roles
INSERT IGNORE INTO roles (id, name) VALUES
(1, 'USER'),
(2, 'STORE_OWNER'),
(3, 'ADMIN');

-- 3. User ↔ Role
INSERT IGNORE INTO user_role (user_id, role_id) VALUES
(11, 1),          -- Linh là USER
(12, 1),          -- Quang là USER
(12, 2),          -- Quang đồng thời là STORE_OWNER
(50, 3);          -- Admin có quyền ADMIN

-- 4. Product Categories (mới)
INSERT IGNORE INTO product_category (id, name, description, status, created_at, updated_at) VALUES
(11, 'Pizza',        'Các món pizza Ý',                'ACTIVE', NOW(), NOW()),
(12, 'Sushi',        'Các món sushi Nhật',             'ACTIVE', NOW(), NOW()),
(13, 'Bánh mì',      'Bánh mì Việt Nam',               'ACTIVE', NOW(), NOW()),
(14, 'Cà phê',       'Đồ uống cà phê',                 'ACTIVE', NOW(), NOW()),
(15, 'Tráng miệng',  'Món ngọt, bánh, kem',            'ACTIVE', NOW(), NOW()),
(16, 'Đồ chay',      'Món chay tốt cho sức khỏe',      'ACTIVE', NOW(), NOW()),
(17, 'Nước ép',      'Nước ép trái cây tươi',          'ACTIVE', NOW(), NOW()),
(18, 'Mì Ý',         'Pasta, spaghetti, lasagna',      'ACTIVE', NOW(), NOW()),
(19, 'Gà rán',       'Các món gà rán, gà sốt',         'ACTIVE', NOW(), NOW());

-- 5. Stores (khác hoàn toàn, đặt tại Hà Nội/Đà Nẵng)
-- Stores adapted to `Store` entity schema (no phone_number, email, logo_url, rating columns)
INSERT IGNORE INTO store
(id, owner_user_id, name, description, bank_account_name, bank_account_number, bank_name, bank_branch, payout_email, status, created_at, updated_at)
VALUES
(11, 12, 'Pizza Tháp Rùa', 'Pizza nướng lò đá phong cách Ý tại Hà Nội', 'PHAM MINH QUANG', '0241234567', 'Vietcombank', 'CN Hoàn Kiếm', 'payout@thaprua.vn', 'ACTIVE', NOW(), NOW()),
(12, 12, 'Sushi Cầu Rồng', 'Sushi & sashimi tươi mỗi ngày ở Đà Nẵng',   'PHAM MINH QUANG', '02363556677', 'Techcombank', 'CN Đà Nẵng',  'payout@sushicaurong.vn', 'ACTIVE', NOW(), NOW()),
(13, 12, 'Bánh Mì Hồ Gươm', 'Bánh mì kẹp nóng giòn, pate/bò nướng',     'PHAM MINH QUANG', '0243344556', 'MB Bank',     'CN Hoàn Kiếm',  'payout@banhmi-hoguom.vn', 'ACTIVE', NOW(), NOW());

-- 6. Store addresses (tọa độ khác hẳn TP.HCM)
-- Store addresses adapted to StoreAddress entity (address_line, ward nullable, district, city, country, latitude, longitude, optional flight_corridor_radius)
INSERT IGNORE INTO store_address
(id, store_id, address_line, ward, district, city, country, latitude, longitude, flight_corridor_radius, created_at, updated_at)
VALUES
(11, 11, '25 Hàng Trống', NULL, 'Hoàn Kiếm', 'Hà Nội', 'Việt Nam', 21.028800, 105.852000, NULL, NOW(), NOW()),
(12, 12, '99 Bạch Đằng',  NULL, 'Hải Châu',  'Đà Nẵng','Việt Nam', 16.067800, 108.222000, NULL, NOW(), NOW()),
(13, 13, '12 Lò Sũ',      NULL, 'Hoàn Kiếm', 'Hà Nội', 'Việt Nam', 21.029900, 105.853500, NULL, NOW(), NOW());

-- 7. Products for Store 11 (Pizza)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1001, 11, 11, 'PZ-MAR-001', 'Pizza Margherita 30cm', 'Cà chua San Marzano, mozzarella, basil', 99000, 'VND', 700, 40, 'ACTIVE', 'https://images.foodie.local/pizza/margherita.jpg', NOW(), NOW()),
(1002, 11, 11, 'PZ-PEP-002', 'Pizza Pepperoni 30cm',  'Pepperoni bò, phô mai béo',              119000, 'VND', 750, 35, 'ACTIVE', 'https://images.foodie.local/pizza/pepperoni.jpg', NOW(), NOW()),
(1003, 11, 15, 'DS-TIRA-01', 'Tiramisu',               'Bánh tiramisu cacao, mascarpone',        59000, 'VND', 120, 25, 'ACTIVE', 'https://images.foodie.local/dessert/tiramisu.jpg', NOW(), NOW()),
(1004, 11, 14, 'CF-ICE-01',  'Cà phê Ý đá',            'Cà phê espresso pha lạnh',               39000, 'VND', 300, 60, 'ACTIVE', 'https://images.foodie.local/drink/iced-espresso.jpg', NOW(), NOW()),
(1005, 11, 11, 'PZ-SEA-01',  'Pizza Hải Sản 30cm',     'Tôm, mực, phô mai mozzarella',           139000, 'VND', 780, 30, 'ACTIVE', 'https://images.foodie.local/pizza/seafood.jpg', NOW(), NOW()),
(1006, 11, 11, 'PZ-MIX-01',  'Pizza Thập Cẩm 30cm',    'Thịt nguội, nấm, ớt chuông',             149000, 'VND', 800, 28, 'ACTIVE', 'https://images.foodie.local/pizza/supreme.jpg', NOW(), NOW()),
(1007, 11, 14, 'DR-PEACH-1', 'Trà Đào',                'Trà đào mát lạnh',                        32000,  'VND', 350, 70, 'ACTIVE', 'https://images.foodie.local/drink/peach-tea.jpg', NOW(), NOW()),
(1008, 11, 15, 'DS-CHS-02',  'Cheesecake Dâu',         'Bánh phô mai kem dâu',                    59000,  'VND', 120, 22, 'ACTIVE', 'https://images.foodie.local/dessert/strawberry-cheesecake.jpg', NOW(), NOW());

-- 8. Products for Store 12 (Sushi)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1101, 12, 12, 'SS-SAL-01', 'Sushi Cá Hồi 8 miếng',   'Cá hồi Na Uy, cơm Nhật',     149000, 'VND', 380, 30, 'ACTIVE', 'https://images.foodie.local/sushi/salmon-nigiri.jpg', NOW(), NOW()),
(1102, 12, 12, 'SS-TUN-01', 'Sushi Cá Ngừ 8 miếng',   'Cá ngừ đại dương',           139000, 'VND', 360, 30, 'ACTIVE', 'https://images.foodie.local/sushi/tuna-nigiri.jpg', NOW(), NOW()),
(1103, 12, 15, 'DS-MOCH-1', 'Mochi Kem (2 viên)',     'Matcha/đậu đỏ',               49000,  'VND', 100, 40, 'ACTIVE', 'https://images.foodie.local/dessert/mochi.jpg', NOW(), NOW()),
(1104, 12, 14, 'CF-MATCH',  'Matcha Latte',           'Trà xanh matcha sữa',         45000,  'VND', 350, 45, 'ACTIVE', 'https://images.foodie.local/drink/matcha-latte.jpg', NOW(), NOW()),
(1105, 12, 12, 'SS-EEL-01', 'Sushi Lươn 8 miếng',     'Lươn nướng sốt tare',         159000, 'VND', 360, 28, 'ACTIVE', 'https://images.foodie.local/sushi/unagi.jpg', NOW(), NOW()),
(1106, 12, 12, 'SS-MIX-01', 'Sashimi Thập Cẩm',       'Cá hồi/cá ngừ/mực',           199000, 'VND', 420, 20, 'ACTIVE', 'https://images.foodie.local/sushi/sashimi-platter.jpg', NOW(), NOW()),
(1107, 12, 14, 'DR-OOL-01', 'Trà Oolong Sữa',         'Trà ô long sữa thơm, ít đá',   42000,  'VND', 350, 50, 'ACTIVE', 'https://images.foodie.local/drink/oolong-milk-tea.jpg', NOW(), NOW()),
(1108, 12, 15, 'DS-CHS-01', 'Cheesecake Matcha',      'Bánh phô mai vị matcha',       59000,  'VND', 120, 30, 'ACTIVE', 'https://images.foodie.local/dessert/matcha-cheesecake.jpg', NOW(), NOW());

-- 9. Products for Store 13 (Bánh mì)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1201, 13, 13, 'BM-TRUY-01', 'Bánh Mì Truyền Thống',   'Pate, chả lụa, dưa leo, đồ chua', 28000, 'VND', 250, 80, 'ACTIVE', 'https://images.foodie.local/banhmi/traditional.jpg', NOW(), NOW()),
(1202, 13, 13, 'BM-BO-01',   'Bánh Mì Bò Nướng',       'Bò ướp ngũ vị, sốt đặc biệt',   39000, 'VND', 300, 60, 'ACTIVE', 'https://images.foodie.local/banhmi/beef.jpg', NOW(), NOW()),
(1203, 13, 14, 'CF-SUA-01',  'Cà Phê Sữa Đá',          'Robusta pha phin, sữa đặc',     25000, 'VND', 300, 120, 'ACTIVE', 'https://images.foodie.local/drink/iced-milk-coffee.jpg', NOW(), NOW()),
(1204, 13, 13, 'BM-GA-01',   'Bánh Mì Gà Xé',          'Gà xé, sốt mayo, rau thơm',     32000, 'VND', 280, 70,  'ACTIVE', 'https://images.foodie.local/banhmi/chicken.jpg', NOW(), NOW()),
(1205, 13, 13, 'BM-TR-01',   'Bánh Mì Trứng Ốp La',    'Trứng ốp la, pate, dưa leo',    30000, 'VND', 260, 70,  'ACTIVE', 'https://images.foodie.local/banhmi/egg.jpg', NOW(), NOW()),
(1206, 13, 14, 'DR-TDC-01',  'Trà Đá Chanh',           'Chanh tươi, ít đường',          15000, 'VND', 350, 100, 'ACTIVE', 'https://images.foodie.local/drink/lemon-ice-tea.jpg', NOW(), NOW()),
(1207, 13, 15, 'DS-FLAN-01', 'Bánh Flan',              'Bánh flan caramel mềm mịn',     18000, 'VND', 100, 80,  'ACTIVE', 'https://images.foodie.local/dessert/flan.jpg', NOW(), NOW());

-- 10. User addresses (khác vị trí, mặc định ở Hà Nội)
-- User addresses adapted to UserAddress entity (address_line, ward, district, city, country, latitude, longitude, is_default)
INSERT IGNORE INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(11, 11, 'Nhà',       'Nguyễn Ngọc Linh', '0912000111', '18 Lý Thái Tổ', NULL, 'Hoàn Kiếm', 'Hà Nội', 'Việt Nam', 21.031200, 105.849900, 1),
(12, 11, 'Văn phòng', 'Nguyễn Ngọc Linh', '0912000111', '210 Đội Cấn',   NULL, 'Ba Đình',   'Hà Nội', 'Việt Nam', 21.036800, 105.819000, 0);

-- 11. Cart cho Linh
INSERT IGNORE INTO cart (id, user_id, status, created_at, updated_at)
VALUES (11, 11, 'ACTIVE', NOW(), NOW());

-- 12. Drones (để phục vụ auto-assign và tracking)
INSERT IGNORE INTO drone
  (id, code, model, max_payload_gram, status, current_battery_percent, last_latitude, last_longitude, last_telemetry_at, created_at, updated_at)
VALUES
  (201, 'DRN-PZ-01', 'DJI Mini 3', 1500, 'AVAILABLE', 95, 21.028800, 105.852000, NOW(), NOW(), NOW()),
  (202, 'DRN-SS-01', 'DJI Air 2S', 2000, 'AVAILABLE', 92, 16.067800, 108.222000, NOW(), NOW(), NOW()),
  (203, 'DRN-BM-01', 'Autel Evo Lite+', 1200, 'AVAILABLE', 90, 21.029900, 105.853500, NOW(), NOW(), NOW());

-- 13. Orders mẫu (đã thanh toán) để admin xem chi tiết và tracking
-- Note: Bảng là 'orders' theo entity Order.java
INSERT IGNORE INTO orders
  (id, user_id, store_id, order_code, status, payment_status,
   total_item_amount, discount_amount, shipping_fee, tax_amount, total_payable,
   delivery_address_snapshot, created_at, updated_at)
VALUES
  (
    2001, 11, 11, 'ORD-2001', 'IN_DELIVERY', 'PAID',
    218000, 0, 15000, 0, 233000,
    '{"label":"Nhà","receiver_name":"Nguyễn Ngọc Linh","phone":"0912000111","address_line":"18 Lý Thái Tổ","district":"Hoàn Kiếm","city":"Hà Nội","country":"Việt Nam","latitude":21.031200,"longitude":105.849900}',
    NOW(), NOW()
  );

-- 13.1 Order items cho đơn 2001
INSERT IGNORE INTO order_item
  (id, order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, total_price)
VALUES
  (3001, 2001, 1001, 'Pizza Margherita 30cm', 99000, 1, 99000),
  (3002, 2001, 1002, 'Pizza Pepperoni 30cm', 119000, 1, 119000);

-- 14. Payment transaction mẫu cho đơn 2001 (VNPay thành công)
INSERT IGNORE INTO payment_transaction
  (id, order_id, provider, method, amount, currency, status, provider_transaction_id, vnp_txn_ref,
   request_payload, response_payload, created_at, updated_at, completed_at)
VALUES
  (
    4001, 2001, 'VNPAY', 'QR', 233000, 'VND', 'SUCCESS', 'VNP-TRX-2001', 'VNP-ORD-2001',
    '{"note":"demo request"}', '{"note":"demo success"}', NOW(), NOW(), NOW()
  );

-- 15. Delivery mẫu cho đơn 2001 (đang trong trạng thái LAUNCHED)
INSERT IGNORE INTO delivery
  (id, order_id, drone_id, current_status, pickup_store_id, dropoff_address_snapshot,
   actual_departure_time, actual_arrival_time, confirmation_method, created_at, updated_at)
VALUES
  (
    5001, 2001, 201, 'LAUNCHED', 11,
    '{"label":"Nhà","receiver_name":"Nguyễn Ngọc Linh","phone":"0912000111","address_line":"18 Lý Thái Tổ","district":"Hoàn Kiếm","city":"Hà Nội","country":"Việt Nam","latitude":21.031200,"longitude":105.849900}',
    NOW(), NULL, 'GEOFENCE', NOW(), NOW()
  );

-- ============================================
-- VERIFICATION
-- ============================================
SELECT '=== USERS (NEW) ===' AS '';
SELECT id, username, email, full_name, status FROM users WHERE id IN (11,12);

SELECT '=== STORES (NEW) ===' AS '';
SELECT id, name, status FROM store WHERE id IN (11,12,13);

SELECT '=== PRODUCTS (NEW) ===' AS '';
SELECT p.id, s.name AS store_name, p.name AS product_name, p.base_price, p.quantity_available, p.status
FROM product p
JOIN store s ON p.store_id = s.id
WHERE p.id BETWEEN 1001 AND 1299
ORDER BY s.id, p.id;

SELECT '=== USER ADDRESSES (NEW) ===' AS '';
SELECT id, user_id, label, address_line, is_default FROM user_address WHERE id IN (11,12);

SELECT '=== READY TO TEST (NEW) ===' AS '';
SELECT 'New test data inserted successfully!' AS message;
SELECT CONCAT('New Stores: ', COUNT(*))  AS info FROM store   WHERE id IN (11,12,13);
SELECT CONCAT('New Products: ', COUNT(*)) AS info FROM product WHERE id BETWEEN 1001 AND 1299;
SELECT CONCAT('New Users: ', COUNT(*))   AS info FROM users   WHERE id IN (11,12);

-- ============================================
-- NOTES (NEW)
-- ============================================
-- Đăng nhập nhanh:
-- - linh.ng / password  (ROLE: USER)
-- - quang.pham / password (ROLE: USER + STORE_OWNER)
--
-- Vị trí cửa hàng:
-- - Pizza Tháp Rùa (Hà Nội):    21.028800, 105.852000
-- - Sushi Cầu Rồng (Đà Nẵng):   16.067800, 108.222000
-- - Bánh Mì Hồ Gươm (Hà Nội):   21.029900, 105.853500
--
-- Địa chỉ khách:
-- - Nhà (mặc định): 21.031200, 105.849900
-- ============================================
