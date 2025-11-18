-- ============================================
-- QUAN TRỌNG: TẮT CHECK KHÓA NGOẠI ĐỂ TRÁNH LỖI
-- ============================================
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. Users (Thêm 3 chủ quán mới: ID 21, 22, 23)
-- ============================================
INSERT INTO users
(id, username, email, password_hash, full_name, phone, status, date_of_birth, gender, created_at, updated_at)
VALUES
-- User Khách hàng & Admin cũ
(11, 'linh.ng', 'linh.nguyen@test.vn', 'password', 'Nguyễn Ngọc Linh', '0912000111', 'ACTIVE', '1996-03-22', 'FEMALE', NOW(), NOW()),
(12, 'quang.pham', 'quang.pham@test.vn', 'password', 'Phạm Minh Quang', '0988000222', 'ACTIVE', '1992-11-05', 'MALE', NOW(), NOW()),
(50, 'admin', 'admin@test.vn', 'password', 'System Administrator', '0900000000', 'ACTIVE', '1990-01-01', 'OTHER', NOW(), NOW()),

-- [MỚI] 3 TÀI KHOẢN CHỦ NHÀ HÀNG RIÊNG BIỆT
(21, 'owner.pizza',  'pizza@foodie.vn',  'password', 'Chủ Quán Pizza',   '0901111222', 'ACTIVE', '1985-01-01', 'MALE', NOW(), NOW()),
(22, 'owner.sushi',  'sushi@foodie.vn',  'password', 'Chủ Quán Sushi',   '0903333444', 'ACTIVE', '1988-05-05', 'FEMALE', NOW(), NOW()),
(23, 'owner.banhmi', 'banhmi@foodie.vn', 'password', 'Chủ Quán Bánh Mì', '0905555666', 'ACTIVE', '1990-10-10', 'MALE', NOW(), NOW());

-- ============================================
-- 2. Roles
-- ============================================
INSERT INTO roles (id, name) VALUES
(1, 'USER'),
(2, 'STORE_OWNER'),
(3, 'ADMIN');

-- ============================================
-- 3. User ↔ Role (Gán quyền STORE_OWNER cho user mới)
-- ============================================
INSERT INTO user_role (user_id, role_id) VALUES
(11, 1), -- Linh: User
(12, 1), -- Quang: User
(50, 3), -- Admin: Admin

-- Gán quyền Chủ quán (Role ID 2) cho 3 user mới
(21, 2), -- owner.pizza
(22, 2), -- owner.sushi
(23, 2); -- owner.banhmi

-- ============================================
-- 4. Product Categories
-- ============================================
INSERT INTO product_category (id, name, slug, description, status, created_at, updated_at) VALUES
(11, 'Pizza',       'pizza',       'Các món pizza Ý',               'ACTIVE', NOW(), NOW()),
(12, 'Sushi',       'sushi',       'Các món sushi Nhật',             'ACTIVE', NOW(), NOW()),
(13, 'Bánh mì',     'banh-mi',     'Bánh mì Việt Nam',               'ACTIVE', NOW(), NOW()),
(14, 'Cà phê',      'ca-phe',      'Đồ uống cà phê',                 'ACTIVE', NOW(), NOW()),
(15, 'Tráng miệng', 'trang-mieng', 'Món ngọt, bánh, kem',            'ACTIVE', NOW(), NOW()),
(16, 'Đồ chay',     'do-chay',     'Món chay tốt cho sức khỏe',      'ACTIVE', NOW(), NOW()),
(17, 'Nước ép',     'nuoc-ep',     'Nước ép trái cây tươi',          'ACTIVE', NOW(), NOW()),
(18, 'Mì Ý',        'mi-y',        'Pasta, spaghetti, lasagna',      'ACTIVE', NOW(), NOW()),
(19, 'Gà rán',      'ga-ran',      'Các món gà rán, gà sốt',         'ACTIVE', NOW(), NOW());

-- ============================================
-- 5. Stores (Đã cập nhật owner_user_id mới)
-- ============================================
INSERT INTO store
(id, owner_user_id, name, description, bank_account_name, bank_account_number, bank_name, bank_branch, payout_email, status, created_at, updated_at)
VALUES
-- Store 11 thuộc về User 21 (owner.pizza)
(11, 21, 'Pizza Nhà Thờ', 'Pizza nướng lò đá phong cách Ý tại trung tâm Sài Gòn', 'NGUYEN VAN A', '0281234567', 'Vietcombank', 'CN Quận 1', 'payout@pizzanhatho.vn', 'ACTIVE', NOW(), NOW()),

-- Store 12 thuộc về User 22 (owner.sushi)
(12, 22, 'Sushi Bến Nghé', 'Sushi & sashimi tươi mỗi ngày tại quận 1',   'TRAN THI B', '0283556677', 'Techcombank', 'CN Quận 1',  'payout@sushibennge.vn', 'ACTIVE', NOW(), NOW()),

-- Store 13 thuộc về User 23 (owner.banhmi)
(13, 23, 'Bánh Mì Phú Mỹ Hưng', 'Bánh mì nóng giòn khu đô thị PMH',     'LE VAN C', '0283344556', 'MB Bank',    'CN Quận 7',  'payout@banhmipmh.vn', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 6. Store Addresses
-- ============================================
INSERT INTO store_address
(id, store_id, address_line, ward, district, city, country, latitude, longitude, flight_corridor_radius, created_at, updated_at)
VALUES
(11, 11, '2 Công Xã Paris', 'Bến Nghé', 'Quận 1', 'TP. Hồ Chí Minh', 'Việt Nam', 10.779800, 106.699000, NULL, NOW(), NOW()),
(12, 12, '185 Võ Văn Tần',  'Võ Thị Sáu', 'Quận 3',  'TP. Hồ Chí Minh','Việt Nam', 10.781300, 106.686900, NULL, NOW(), NOW()),
(13, 13, '23 Nguyễn Đức Cảnh', 'Tân Phong', 'Quận 7', 'TP. Hồ Chí Minh', 'Việt Nam', 10.735000, 106.714000, NULL, NOW(), NOW());

-- ============================================
-- 7. Products for Store 11 (Pizza - Ảnh xịn, link ổn định)
-- ============================================
INSERT INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1001, 11, 11, 'PZ-MAR-001', 'Pizza Margherita 30cm', 'Cà chua San Marzano, mozzarella, basil', 99000, 'VND', 700, 40, 'ACTIVE', 
 'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?w=600&q=80', NOW(), NOW()),
 
(1002, 11, 11, 'PZ-PEP-002', 'Pizza Pepperoni 30cm',  'Pepperoni bò, phô mai béo ngậy', 119000, 'VND', 750, 35, 'ACTIVE', 
 'https://images.unsplash.com/photo-1628840042765-356cda07504e?w=600&q=80', NOW(), NOW()),
 
(1003, 11, 15, 'DS-TIRA-01', 'Tiramisu', 'Bánh tiramisu cacao, mascarpone truyền thống', 59000, 'VND', 120, 25, 'ACTIVE', 
 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=600&q=80', NOW(), NOW()),
 
(1004, 11, 14, 'CF-ICE-01',  'Cà phê Ý đá', 'Cà phê espresso pha lạnh, đậm đà', 39000, 'VND', 300, 60, 'ACTIVE', 
 'https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?w=600&q=80', NOW(), NOW()),
 
(1005, 11, 11, 'PZ-SEA-01',  'Pizza Hải Sản 30cm', 'Tôm, mực, thanh cua, phô mai mozzarella', 139000, 'VND', 780, 30, 'ACTIVE', 
 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=600&q=80', NOW(), NOW()),
 
(1006, 11, 11, 'PZ-MIX-01',  'Pizza Thập Cẩm 30cm', 'Thịt nguội, nấm, ớt chuông, ô liu', 149000, 'VND', 800, 28, 'ACTIVE', 
 'https://images.unsplash.com/photo-1604382355076-af4b0eb60143?w=600&q=80', NOW(), NOW()),
 
(1007, 11, 14, 'DR-PEACH-1', 'Trà Đào Cam Sả', 'Trà đào mát lạnh với lát cam tươi và sả', 32000,  'VND', 350, 70, 'ACTIVE', 
 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=600&q=80', NOW(), NOW()),
 
(1008, 11, 15, 'DS-CHS-02',  'Cheesecake Dâu', 'Bánh phô mai kem dâu tây', 59000,  'VND', 120, 22, 'ACTIVE', 
 'https://images.unsplash.com/photo-1533134242443-d4fd215305ad?w=600&q=80', NOW(), NOW());

-- ============================================
-- 8. Products for Store 12 (Sushi - Ảnh xịn, link ổn định)
-- ============================================
INSERT INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1101, 12, 12, 'SS-SAL-01', 'Sushi Cá Hồi 8 miếng', 'Cá hồi Na Uy tươi sống, cơm Nhật', 149000, 'VND', 380, 30, 'ACTIVE', 
 'https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=600&q=80', NOW(), NOW()),
 
(1102, 12, 12, 'SS-TUN-01', 'Sushi Cá Ngừ 8 miếng', 'Cá ngừ đại dương tươi ngon', 139000, 'VND', 360, 30, 'ACTIVE', 
 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=600&q=80', NOW(), NOW()),
 
(1103, 12, 15, 'DS-MOCH-1', 'Mochi Kem (2 viên)', 'Vị Matcha và Đậu đỏ truyền thống', 49000,  'VND', 100, 40, 'ACTIVE', 
 'https://images.unsplash.com/photo-1623688842131-167eb9366884?w=600&q=80', NOW(), NOW()),
 
(1104, 12, 14, 'CF-MATCH',  'Matcha Latte', 'Trà xanh matcha Nhật Bản pha sữa', 45000,  'VND', 350, 45, 'ACTIVE', 
 'https://images.unsplash.com/photo-1515823064-d6bf0febcf5f?w=600&q=80', NOW(), NOW()),
 
(1105, 12, 12, 'SS-EEL-01', 'Sushi Lươn 8 miếng', 'Lươn nướng sốt tare đậm đà', 159000, 'VND', 360, 28, 'ACTIVE', 
 'https://images.unsplash.com/photo-1617196034496-64ac7960f271?w=600&q=80', NOW(), NOW()),
 
(1106, 12, 12, 'SS-MIX-01', 'Sashimi Thập Cẩm', 'Combo Cá hồi, Cá ngừ, Bạch tuộc', 199000, 'VND', 420, 20, 'ACTIVE', 
 'https://images.unsplash.com/photo-1553621042-f6e147245754?w=600&q=80', NOW(), NOW()),
 
(1107, 12, 14, 'DR-OOL-01', 'Trà Oolong Sữa', 'Trà ô long sữa nướng thơm ngon', 42000,  'VND', 350, 50, 'ACTIVE', 
 'https://images.unsplash.com/photo-1558160074-4d7d8bdf4256?w=600&q=80', NOW(), NOW()),
 
(1108, 12, 15, 'DS-CHS-01', 'Cheesecake Matcha', 'Bánh phô mai vị trà xanh Nhật Bản', 59000,  'VND', 120, 30, 'ACTIVE', 
 'https://images.unsplash.com/photo-1630612271423-7872bf957b4c?w=600&q=80', NOW(), NOW());

-- ============================================
-- 9. Products for Store 13 (Bánh mì - Ảnh xịn, link ổn định)
-- ============================================
INSERT INTO product
(id, store_id, category_id, sku, name, description, base_price, currency, weight_gram, quantity_available, status, media_primary_url, created_at, updated_at)
VALUES
(1201, 13, 13, 'BM-TRUY-01', 'Bánh Mì Truyền Thống', 'Pate, chả lụa, dưa leo, đồ chua, ngò rí', 28000, 'VND', 250, 80, 'ACTIVE', 
 'https://images.unsplash.com/photo-1635544358626-69b74a02d0c3?w=600&q=80', NOW(), NOW()),
 
(1202, 13, 13, 'BM-BO-01',   'Bánh Mì Bò Nướng', 'Bò nướng sả ướp ngũ vị, sốt đặc biệt', 39000, 'VND', 300, 60, 'ACTIVE', 
 'https://images.unsplash.com/photo-1600454309261-3dc9b7594592?w=600&q=80', NOW(), NOW()),
 
(1203, 13, 14, 'CF-SUA-01',  'Cà Phê Sữa Đá', 'Cà phê Robusta pha phin với sữa đặc', 25000, 'VND', 300, 120, 'ACTIVE', 
 'https://images.unsplash.com/photo-1592319060302-3da845455647?w=600&q=80', NOW(), NOW()),
 
(1204, 13, 13, 'BM-GA-01',   'Bánh Mì Gà Xé', 'Gà xé sợi, sốt bơ trứng, rau thơm', 32000, 'VND', 280, 70,  'ACTIVE', 
 'https://images.unsplash.com/photo-1548352280-c31573545a67?w=600&q=80', NOW(), NOW()),
 
(1205, 13, 13, 'BM-TR-01',   'Bánh Mì Trứng Ốp La', 'Trứng ốp la lòng đào, pate, nước tương', 30000, 'VND', 260, 70,  'ACTIVE', 
 'https://images.unsplash.com/photo-1598155523122-38423fe4d639?w=600&q=80', NOW(), NOW()),
 
(1206, 13, 14, 'DR-TDC-01',  'Trà Đá Chanh', 'Chanh tươi, trà lài, ít đường', 15000, 'VND', 350, 100, 'ACTIVE', 
 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=600&q=80', NOW(), NOW()),
 
(1207, 13, 15, 'DS-FLAN-01', 'Bánh Flan', 'Bánh flan caramel mềm mịn, thơm trứng', 18000, 'VND', 100, 80,  'ACTIVE', 
 'https://images.unsplash.com/photo-1559593525-2d35e364c0c6?w=600&q=80', NOW(), NOW());
-- ============================================
-- 10. User Addresses (Khách hàng & Chủ quán & Admin)
-- ============================================
-- LINH (Khách): Nhà (Q1) & VP (Q4)
INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(11, 11, 'Nhà', 'Nguyễn Ngọc Linh', '0912000111', '135 Nguyễn Thái Bình', 'Phường Nguyễn Thái Bình', 'Quận 1', 'TP. Hồ Chí Minh', 'Việt Nam', 10.769500, 106.698500, 1);

INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(12, 11, 'Văn phòng', 'Nguyễn Ngọc Linh', '0912000111', '50 Hoàng Diệu', 'Phường 12', 'Quận 4', 'TP. Hồ Chí Minh', 'Việt Nam', 10.763200, 106.707500, 0);

-- QUANG (Khách): Nhà Q3 & Căn hộ Q7
INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(21, 12, 'Nhà riêng', 'Phạm Minh Quang', '0988000222', '200 Pasteur', 'Phường 6', 'Quận 3', 'TP. Hồ Chí Minh', 'Việt Nam', 10.785000, 106.695000, 1);

INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(22, 12, 'Căn hộ PMH', 'Phạm Minh Quang', '0988000222', '1058 Nguyễn Văn Linh', 'Tân Phong', 'Quận 7', 'TP. Hồ Chí Minh', 'Việt Nam', 10.730000, 106.705000, 0);

-- ADMIN: Trụ sở & Kho
INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(51, 50, 'Trụ sở chính', 'Admin System', '0900000000', '37 Tôn Đức Thắng', 'Bến Nghé', 'Quận 1', 'TP. Hồ Chí Minh', 'Việt Nam', 10.780000, 106.705000, 1);

INSERT INTO user_address
(id, user_id, label, receiver_name, phone, address_line, ward, district, city, country, latitude, longitude, is_default)
VALUES
(52, 50, 'Kho Test', 'Admin System', '0900000000', '92 Nguyễn Hữu Cảnh', 'Phường 22', 'Bình Thạnh', 'TP. Hồ Chí Minh', 'Việt Nam', 10.790000, 106.720000, 0);

-- ============================================
-- 11. Cart
-- ============================================
INSERT INTO cart (id, user_id, status, created_at, updated_at) VALUES (11, 11, 'ACTIVE', NOW(), NOW());

-- ============================================
-- 12. Drones
-- ============================================
INSERT INTO drone
  (id, code, model, max_payload_gram, status, current_battery_percent, last_latitude, last_longitude, last_telemetry_at, created_at, updated_at)
VALUES
  (201, 'DRN-PZ-01', 'DJI Mini 3', 1500, 'AVAILABLE', 95, 10.779800, 106.699000, NOW(), NOW(), NOW()),
  (202, 'DRN-SS-01', 'DJI Air 2S', 2000, 'AVAILABLE', 92, 10.781300, 106.686900, NOW(), NOW(), NOW()),
  (203, 'DRN-BM-01', 'Autel Evo Lite+', 1200, 'AVAILABLE', 90, 10.735000, 106.714000, NOW(), NOW(), NOW());

-- ============================================
-- BẬT LẠI CHECK KHÓA NGOẠI
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;