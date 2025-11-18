# Thiết kế Backend (FoodFast Delivery)

Tài liệu đặc tả kiến trúc, dữ liệu, bảo mật và các quy tắc nghiệp vụ của backend Spring Boot.

---

## 1) Tổng quan kiến trúc

- Mô hình 3 lớp: Controller → Service → Repository.
- Nền tảng: Spring Boot 3.5, Spring Data JPA (Hibernate), Lombok, MapStruct.
- Bảo mật: Spring Security + JWT Bearer.
- CSDL: MySQL 8.0 (dev dùng MySQL, không dùng H2).
- Triển khai: Maven build JAR; profile chạy chính là `dev` (port 8080).
- Frontend dev: server Express (port 3000) reverse proxy về backend (8080).

Thư mục code chính: `backend/source/main` (Java) và `backend/source/resources` (cấu hình).

### Liên kết sơ đồ chi tiết
- Use Case: `plantuml/usecase_chi_tiet.puml`
- Activity (Đặt hàng & thanh toán): `plantuml/activity_dat_hang_thanh_toan_chi_tiet.puml`
- Activity (Giao hàng & sạc drone): `plantuml/activity_drone_giao_hang_sac_chi_tiet.puml`
- Sequence (Đặt hàng → giao hoàn tất): `plantuml/sequence_dat_hang_giao_hang_chi_tiet.puml`
- Sequence (Drone sạc pin): `plantuml/sequence_drone_sac_pin_chi_tiet.puml`
- Component chi tiết: `plantuml/component_kien_truc_chi_tiet.puml`
- Deployment chi tiết: `plantuml/deployment_kien_truc_chi_tiet.puml`
- ERD cập nhật: `plantuml/erd_cap_nhat.puml`

---

## 2) Module & lớp chịu trách nhiệm

- Auth: đăng nhập/đăng ký, validate token (JWT), phân quyền (CUSTOMER, ADMIN, STORE_OWNER).
- User: hồ sơ người dùng, địa chỉ giao hàng (kinh độ/vĩ độ).
- Store: cửa hàng, địa chỉ cửa hàng, menu.
- Product: danh mục, sản phẩm, tồn kho cơ bản.
- Cart/Order: giỏ hàng, tạo đơn, snapshot địa chỉ giao hàng vào `deliveryAddressSnapshot` (JSON lat/lng).
- Payment: tích hợp VNPay (sandbox), có endpoint mô phỏng thành công trong dev.
- Delivery: vòng đời giao hàng; liên kết với Order và Drone.
- Drone: thông tin drone, vị trí gần nhất, pin, trạng thái, trạm sạc.

Xem các sơ đồ ở `Docucment_PRD/plantuml/*.puml` (component, activity, sequence, deployment, erd).

---

## 3) Dòng nghiệp vụ (happy path)

1. Khách chọn món → tạo Order → chuyển trang thanh toán (VNPay hoặc mô phỏng).
2. Thanh toán thành công → tạo Delivery cho order; gán Drone phù hợp.
3. Delivery chuyển trạng thái qua các bước đến `COMPLETED`.
4. Frontend tracking dùng polling các API để cập nhật tiến trình & hiển thị bản đồ.

Sơ đồ tham khảo: `activity_checkout_delivery_vi.puml`, `sequence_end_to_end_vi.puml`.

---

## 4) Mô hình dữ liệu (ERD rút gọn)

Thực thể chính: `User`, `Role`, `UserAddress`, `Store`, `StoreAddress`, `ProductCategory`, `Product`, `Cart`, `CartItem`, `Order`, `OrderItem`, `PaymentTransaction`, `Delivery`, `Drone`.

Các điểm đáng chú ý đã triển khai trong code:
- `Order` có thêm `deliveredDroneId`, `deliveredDroneCode` (được set khi giao xong).
- `Delivery` có `batteryUsedPercent`, `distanceKm`, `actualFlightTimeSeconds` (ghi lại khi hoàn tất).
- `Drone` có `currentBatteryPercent`, `lastLatitude/lastLongitude`, `lastTelemetryAt`, `status`.

Xem ERD: `Docucment_PRD/plantuml/erd_vi.puml`.

---

## 5) Bảo mật

- JWT Bearer cho các API yêu cầu đăng nhập.
- Dev: có thể bật mật khẩu dạng NoOp (plaintext) và khóa JWT qua `application-dev.yaml` để thuận tiện thử nghiệm.
- CORS mở cho nguồn dev (Frontend 3000) thông qua reverse proxy, hạn chế cấu hình trong prod.

---

## 6) Thanh toán (VNPay + mô phỏng)

- Hỗ trợ mô phỏng thanh toán trong dev: `app.payments.allow-simulate=true` để kích hoạt endpoint giả lập thành công.
- Khi thanh toán xác nhận thành công, backend cập nhật trạng thái Order/Payment và tạo Delivery.
- Return page chỉ hiển thị kết quả; quyết định cuối đến từ backend (IPN/giả lập).

---

## 7) Giao vận Drone – Quy tắc nghiệp vụ

Trạng thái Delivery tiêu biểu: `QUEUED` → `ASSIGNED` → `LAUNCHED` → `ARRIVING` → `COMPLETED`.

Hoàn tất giao hàng (COMPLETED) – xử lý đã hiện thực trong `DeliveryService`:
- Tính khoảng cách từ cửa hàng đến điểm giao dựa trên toạ độ (store address vs. snapshot dropoff).
- Tính tiêu hao pin ước lượng: ~12%/km + 15% (tối thiểu 25%, tối đa 100%).
- Cập nhật Delivery: `batteryUsedPercent`, `distanceKm`, `actualFlightTimeSeconds` (từ departure/arrival).
- Cập nhật Drone:
  - Trừ `currentBatteryPercent` theo usage, không âm.
  - Cập nhật vị trí cuối về điểm giao, `lastTelemetryAt=now`.
  - Đặt `status=AVAILABLE`.
- Cập nhật Order: set `deliveredDroneId` và `deliveredDroneCode`.

Sạc pin (DRONE.CHARGING) – xử lý đã hiện thực trong `DroneService`:
- Khi vào trạng thái `CHARGING`, tiến trình sạc chạy sau khi transaction commit (tránh lỗi cần bấm 2 lần), tăng ~5%/s tới 100%.
- Nếu status đổi khỏi `CHARGING`, vòng sạc dừng ngay.
- Khi `currentBatteryPercent=100`, UI sẽ vô hiệu hóa nút sạc.

Trả drone về trạm:
- Endpoint “return-to-station” đặt vị trí drone về tọa độ trạm và status phù hợp, sau đó có thể sạc.

---

## 8) API (tóm tắt)

Tiền tố phiên bản: đa số dưới `/api/v1/...` (một số root như `/auth`, `/drones` tuỳ module hiện trạng).

- Auth: `/auth/login`, `/auth/validate`, `/auth/logout`.
- Order: `GET /api/v1/orders/{id}`.
- Delivery: `GET /api/v1/deliveries/by-order/{orderId}`, `PATCH /api/v1/deliveries/{id}/status`.
- Drone: `POST /drones/{id}/return-to-station`, `POST /drones/{id}/charge`, `GET /drones`.
- Payment (dev): mô phỏng thành công nếu bật `app.payments.allow-simulate`.

DTO/Mapper:
- MapStruct dùng để map Entity → Response DTO (ví dụ `DeliveryResponse` có thêm `batteryUsedPercent`, `distanceKm`, `actualFlightTimeSeconds`).

---

## 9) Cấu hình & môi trường

`backend/source/resources/application-dev.yaml` (mặc định):
- `server.port=8080`.
- Kết nối MySQL `jdbc:mysql://localhost:3306/fast_food_delivery?...`.
- `spring.jpa.hibernate.ddl-auto=create` (dev) – cẩn trọng dữ liệu.
- Seed dữ liệu demo qua `spring.sql.init.mode=always` và `data-locations` (tuỳ bật/tắt theo nhu cầu).
- JWT: `jwt.signerKey`, `valid-duration`, `refreshable-duration`.
- Frontend base URL cho trang thanh toán: `frontend.base-url=http://localhost:3000`.
- Cờ demo/thanh toán mô phỏng: `app.demo.enable`, `app.payments.allow-simulate=true`.

---

## 10) Giao tiếp Frontend

- Frontend dev server (Node/Express) chạy tại `http://localhost:3000`, proxy các path (`/api`, `/auth`, `/drones`, ...) đến backend `http://localhost:8080`.
- Tracking UI sử dụng OpenLayers hiển thị tuyến đường & drone; polling API để cập nhật tiến trình.
- Sau khi giao xong, UI lưu dấu đã hiển thị thông báo; lần truy cập lại sẽ không bật modal, nhưng vẫn hiển thị thông tin drone và các chỉ số hoàn tất.

---

## 11) Xử lý lỗi & giao dịch

- Service đảm bảo cập nhật atomically các thay đổi khi hoàn tất giao hàng (Delivery, Drone, Order).
- Vòng sạc drone khởi động sau commit để tránh đua giao dịch và lỗi UI “bấm 2 lần mới sạc”.
- Exception được trả về dạng chuẩn hoá (HTTP status + thông điệp) qua lớp xử lý ngoại lệ chung.

---

## 12) Kiểm thử & mở rộng

- Tồn tại các bài test tích hợp/đơn vị mẫu trong `backend/source/test` và `src/test/java` (ví dụ `PaymentInitIT`, `AdminDiagnosticsControllerTest`).
- Mở rộng tương lai:
  - Chuyển polling sang WebSocket/SSE cho realtime mượt hơn.
  - Tối ưu thuật toán chọn drone (khu vực cấm bay, thời tiết, traffic vùng bay...).
  - Hoàn thiện Flyway migration thay vì seed qua `data.sql` trong dev.
  - Chuẩn hóa versioning endpoint và tài liệu OpenAPI.
