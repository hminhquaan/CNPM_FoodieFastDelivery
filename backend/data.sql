-- ============================================
-- TEST DATA (REWRITTEN) - COMPLETE ORDER FLOW
-- ============================================

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
-- Include slug column to avoid duplicate category creation by ApplicationRunner
INSERT IGNORE INTO product_category (id, name, slug, description, status, created_at, updated_at) VALUES
(11, 'Pizza',        'pizza',       'Các món pizza Ý',                'ACTIVE', NOW(), NOW()),
(12, 'Sushi',        'sushi',       'Các món sushi Nhật',             'ACTIVE', NOW(), NOW()),
(13, 'Bánh mì',      'banh-mi',     'Bánh mì Việt Nam',               'ACTIVE', NOW(), NOW()),
(14, 'Cà phê',       'ca-phe',      'Đồ uống cà phê',                 'ACTIVE', NOW(), NOW()),
(15, 'Tráng miệng',  'trang-mieng', 'Món ngọt, bánh, kem',            'ACTIVE', NOW(), NOW());

-- 5. Stores (khác hoàn toàn, đặt tại Hà Nội/Đà Nẵng)
INSERT IGNORE INTO store
(id, owner_user_id, name, description, phone_number, email, logo_url, rating, status, created_at, updated_at)
VALUES
(11, 12, 'Pizza Tháp Rùa', 'Pizza nướng lò đá phong cách Ý tại Hà Nội', '0241234567', 'pizza@thaprua.vn', NULL, 4.6, 'OPEN', NOW(), NOW()),
(12, 12, 'Sushi Cầu Rồng', 'Sushi & sashimi tươi mỗi ngày ở Đà Nẵng',   '02363556677', 'hello@sushicaurong.vn', NULL, 4.8, 'OPEN', NOW(), NOW()),
(13, 12, 'Bánh Mì Hồ Gươm', 'Bánh mì kẹp nóng giòn, pate/bò nướng',     '0243344556', 'contact@banhmi-hoguom.vn', NULL, 4.4, 'OPEN', NOW(), NOW());

-- 6. Store addresses (khớp schema entity StoreAddress - address_line, ward, country, flight_corridor_radius)
INSERT IGNORE INTO store_address
(id, store_id, address_line, ward, district, city, country, latitude, longitude, flight_corridor_radius, created_at, updated_at)
VALUES
(11, 11, '25 Hàng Trống', NULL, 'Hoàn Kiếm', 'Hà Nội', 'VN', 21.028800, 105.852000, 2.0, NOW(), NOW()),
(12, 12, '99 Bạch Đằng',  NULL, 'Hải Châu',  'Đà Nẵng','VN', 16.067800, 108.222000, 2.0, NOW(), NOW()),
(13, 13, '12 Lò Sũ',      NULL, 'Hoàn Kiếm', 'Hà Nội', 'VN', 21.029900, 105.853500, 2.0, NOW(), NOW());

-- 7. Products for Store 11 (Pizza)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, created_at, updated_at)
VALUES
(1001, 11, 11, 'PZ-MAR-001', 'Pizza Margherita 30cm', 'Cà chua San Marzano, mozzarella, basil', 99000, 'VND', 700, 40, 'ACTIVE', NOW(), NOW()),
(1002, 11, 11, 'PZ-PEP-002', 'Pizza Pepperoni 30cm',  'Pepperoni bò, phô mai béo',              119000, 'VND', 750, 35, 'ACTIVE', NOW(), NOW()),
(1003, 11, 15, 'DS-TIRA-01', 'Tiramisu',               'Bánh tiramisu cacao, mascarpone',        59000, 'VND', 120, 25, 'ACTIVE', NOW(), NOW()),
(1004, 11, 14, 'CF-ICE-01',  'Cà phê Ý đá',            'Cà phê espresso pha lạnh',               39000, 'VND', 300, 60, 'ACTIVE', NOW(), NOW());

-- 8. Products for Store 12 (Sushi)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, created_at, updated_at)
VALUES
(1101, 12, 12, 'SS-SAL-01', 'Sushi Cá Hồi 8 miếng',   'Cá hồi Na Uy, cơm Nhật',     149000, 'VND', 380, 30, 'ACTIVE', NOW(), NOW()),
(1102, 12, 12, 'SS-TUN-01', 'Sushi Cá Ngừ 8 miếng',   'Cá ngừ đại dương',           139000, 'VND', 360, 30, 'ACTIVE', NOW(), NOW()),
(1103, 12, 15, 'DS-MOCH-1', 'Mochi Kem (2 viên)',     'Matcha/đậu đỏ',               49000,  'VND', 100, 40, 'ACTIVE', NOW(), NOW()),
(1104, 12, 14, 'CF-MATCH',  'Matcha Latte',           'Trà xanh matcha sữa',         45000,  'VND', 350, 45, 'ACTIVE', NOW(), NOW());

-- 9. Products for Store 13 (Bánh mì)
INSERT IGNORE INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, created_at, updated_at)
VALUES
(1201, 13, 13, 'BM-TRUY-01', 'Bánh Mì Truyền Thống',   'Pate, chả lụa, dưa leo, đồ chua', 28000, 'VND', 250, 80, 'ACTIVE', NOW(), NOW()),
(1202, 13, 13, 'BM-BO-01',   'Bánh Mì Bò Nướng',       'Bò ướp ngũ vị, sốt đặc biệt',   39000, 'VND', 300, 60, 'ACTIVE', NOW(), NOW()),
(1203, 13, 14, 'CF-SUA-01',  'Cà Phê Sữa Đá',          'Robusta pha phin, sữa đặc',     25000, 'VND', 300, 120, 'ACTIVE', NOW(), NOW());

-- 10. User addresses (khác vị trí, mặc định ở Hà Nội)
INSERT IGNORE INTO user_address
(id, user_id, label, street, district, city, latitude, longitude, full_address, is_default, created_at, updated_at)
VALUES
(11, 11, 'Nhà',       '18 Lý Thái Tổ',  'Hoàn Kiếm', 'Hà Nội', 21.031200, 105.849900, '18 Lý Thái Tổ, Hoàn Kiếm, Hà Nội', 1, NOW(), NOW()),
(12, 11, 'Văn phòng', '210 Đội Cấn',    'Ba Đình',   'Hà Nội', 21.036800, 105.819000, '210 Đội Cấn, Ba Đình, Hà Nội',     0, NOW(), NOW());

-- 11. Cart cho Linh
INSERT IGNORE INTO cart (id, user_id, status, created_at, updated_at)
VALUES (11, 11, 'ACTIVE', NOW(), NOW());

-- ============================================
-- VERIFICATION
-- ============================================
SELECT '=== USERS (NEW) ===' AS '';
SELECT id, username, email, full_name, status FROM users WHERE id IN (11,12);

SELECT '=== STORES (NEW) ===' AS '';
SELECT id, name, status, rating FROM store WHERE id IN (11,12,13);

SELECT '=== PRODUCTS (NEW) ===' AS '';
SELECT p.id, s.name AS store_name, p.name AS product_name, p.base_price, p.quantity_available, p.status
FROM product p
JOIN store s ON p.store_id = s.id
WHERE p.id BETWEEN 1001 AND 1299
ORDER BY s.id, p.id;

SELECT '=== USER ADDRESSES (NEW) ===' AS '';
SELECT id, user_id, label, full_address, is_default FROM user_address WHERE id IN (11,12);

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