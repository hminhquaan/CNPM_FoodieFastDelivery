-- =====================================================
-- DEMO DATABASE SETUP SCRIPT (ADAPTED)
-- Drone Delivery Payment System
-- Rewritten test data set (completely different from original HCMC set)
-- Created: 2025-10-31
-- =====================================================

-- 1) CLEAN UP (Optional)
-- DELETE FROM payout_batch;
-- DELETE FROM store_ledger;
-- DELETE FROM payment_transaction;
-- DELETE FROM order_item;
-- DELETE FROM orders;
-- DELETE FROM product WHERE id BETWEEN 1001 AND 1299;
-- DELETE FROM product_category WHERE id BETWEEN 11 AND 15;
-- DELETE FROM store WHERE id IN (11,12,13);
-- DELETE FROM users WHERE id IN (11,12,13);

-- =====================================================
-- 2) CREATE DEMO DATA (NEW SET)
-- =====================================================

-- 2.1 Users (khác hoàn toàn)
INSERT INTO users (id, username, email, password, full_name, phone_number, status, created_at)
VALUES
(11, 'linh.ng',     'linh.nguyen@test.vn',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Nguyễn Ngọc Linh', '0912000111', 'ACTIVE', NOW()),
(12, 'quang.pham',  'quang.pham@test.vn',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Phạm Minh Quang',  '0988000222', 'ACTIVE', NOW()),
(13, 'thu.do',      'thu.do@test.vn',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Đỗ Thu Thảo',      '0909000333', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- 2.2 Stores (Hà Nội/Đà Nẵng, có thông tin ngân hàng & payout email)
INSERT INTO store
(id, owner_user_id, name, description, bank_account_name, bank_account_number, bank_name, bank_branch, payout_email, status, created_at)
VALUES
(11, 12, 'Pizza Tháp Rùa',   'Pizza nướng lò đá phong cách Ý tại Hà Nội',
 'PHAM MINH QUANG', '111222333', 'Vietcombank', 'Sở giao dịch Hà Nội', 'payout@thaprua.vn', 'ACTIVE', NOW()),
(12, 12, 'Sushi Cầu Rồng',   'Sushi & sashimi tươi mỗi ngày ở Đà Nẵng',
 'PHAM MINH QUANG', '444555666', 'Techcombank', 'CN Đà Nẵng',          'payout@sushicaurong.vn', 'ACTIVE', NOW()),
(13, 12, 'Bánh Mì Hồ Gươm',  'Bánh mì nóng giòn, pate/bò nướng',
 'PHAM MINH QUANG', '777888999', 'MB Bank',     'CN Hoàn Kiếm',        'payout@banhmi-hoguom.vn', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2.3 Product Categories (mới, có slug)
INSERT INTO product_category (id, name, slug, status, description, created_at)
VALUES
(11, 'Pizza',        'pizza',        'ACTIVE', 'Các món pizza Ý',           NOW()),
(12, 'Sushi',        'sushi',        'ACTIVE', 'Các món sushi Nhật',        NOW()),
(13, 'Bánh mì',      'banh-mi',      'ACTIVE', 'Bánh mì Việt Nam',          NOW()),
(14, 'Cà phê',       'ca-phe',       'ACTIVE', 'Đồ uống cà phê',            NOW()),
(15, 'Tráng miệng',  'trang-mieng',  'ACTIVE', 'Món ngọt, bánh, kem',       NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2.4 Products (khớp model: có reserved_quantity, safety_stock, weight_gram)
-- Store 11: Pizza Tháp Rùa
INSERT INTO product
(id, category_id, store_id, sku, name, description, base_price, currency,
 quantity_available, reserved_quantity, safety_stock, status, weight_gram, created_at)
VALUES
(1001, 11, 11, 'PZ-MAR-001', 'Pizza Margherita 30cm', 'Cà chua San Marzano, mozzarella, basil',
  99000,  'VND', 40, 0, 8, 'ACTIVE', 700, NOW()),
(1002, 11, 11, 'PZ-PEP-002', 'Pizza Pepperoni 30cm',  'Pepperoni bò, phô mai béo',
 119000,  'VND', 35, 0, 8, 'ACTIVE', 750, NOW()),
(1003, 15, 11, 'DS-TIRA-01', 'Tiramisu',              'Bánh tiramisu cacao, mascarpone',
  59000,  'VND', 25, 0, 5, 'ACTIVE', 120, NOW()),
(1004, 14, 11, 'CF-ICE-01',  'Cà phê Ý đá',           'Cà phê espresso pha lạnh',
  39000,  'VND', 60, 0, 10,'ACTIVE', 300, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Store 12: Sushi Cầu Rồng
INSERT INTO product
(id, category_id, store_id, sku, name, description, base_price, currency,
 quantity_available, reserved_quantity, safety_stock, status, weight_gram, created_at)
VALUES
(1101, 12, 12, 'SS-SAL-01', 'Sushi Cá Hồi (8 miếng)', 'Cá hồi Na Uy, cơm Nhật',
 149000, 'VND', 30, 0, 6, 'ACTIVE', 380, NOW()),
(1102, 12, 12, 'SS-TUN-01', 'Sushi Cá Ngừ (8 miếng)', 'Cá ngừ đại dương',
 139000, 'VND', 30, 0, 6, 'ACTIVE', 360, NOW()),
(1103, 15, 12, 'DS-MOCH-1', 'Mochi Kem (2 viên)',     'Matcha/đậu đỏ',
  49000, 'VND', 40, 0, 8, 'ACTIVE', 100, NOW()),
(1104, 14, 12, 'CF-MATCH',  'Matcha Latte',           'Trà xanh matcha sữa',
  45000, 'VND', 45, 0, 8, 'ACTIVE', 350, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Store 13: Bánh Mì Hồ Gươm
INSERT INTO product
(id, category_id, store_id, sku, name, description, base_price, currency,
 quantity_available, reserved_quantity, safety_stock, status, weight_gram, created_at)
VALUES
(1201, 13, 13, 'BM-TRUY-01', 'Bánh Mì Truyền Thống', 'Pate, chả lụa, dưa leo, đồ chua',
 28000, 'VND', 80, 0, 16, 'ACTIVE', 250, NOW()),
(1202, 13, 13, 'BM-BO-01',   'Bánh Mì Bò Nướng',     'Bò ướp ngũ vị, sốt đặc biệt',
 39000, 'VND', 60, 0, 12, 'ACTIVE', 300, NOW()),
(1203, 14, 13, 'CF-SUA-01',  'Cà Phê Sữa Đá',        'Robusta pha phin, sữa đặc',
 25000, 'VND',120, 0, 20, 'ACTIVE', 300, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =====================================================
-- 3) DEMO QUERIES (điều chỉnh cho id mới)
-- =====================================================

-- 3.1 Verify counts cho tập mới
SELECT 'Users(new ids)'  AS Table_Name, COUNT(*) AS Count FROM users WHERE id IN (11,12,13)
UNION ALL
SELECT 'Stores(new ids)',      COUNT(*) FROM store WHERE id IN (11,12,13)
UNION ALL
SELECT 'Categories(11..15)',   COUNT(*) FROM product_category WHERE id BETWEEN 11 AND 15
UNION ALL
SELECT 'Products(1001..1299)', COUNT(*) FROM product WHERE id BETWEEN 1001 AND 1299;

-- 3.2 View products by store (áp dụng chung)
SELECT
  s.id  AS store_id,
  s.name AS store_name,
  p.id  AS product_id,
  p.name AS product_name,
  p.base_price,
  p.quantity_available,
  p.reserved_quantity,
  p.safety_stock
FROM store s
JOIN product p ON p.store_id = s.id
WHERE s.id IN (11,12,13)
ORDER BY s.id, p.id;

-- (Phần 4.x/5.x/6.x về Orders/Payments/Ledger/Payout… trong script cũ vẫn dùng được nguyên xi,
-- vì các bảng đó độc lập với seed master data ở trên. Chỉ cần tạo đơn dựa trên product/store mới.)

-- =====================================================
-- NOTES
-- - Login nhanh:
--   * linh.ng / password  (USER)
--   * quang.pham / password (USER + STORE_OWNER)
-- - Bộ dữ liệu hoàn toàn khác TP.HCM trước đây; nay là Hà Nội/Đà Nẵng.
-- =====================================================
