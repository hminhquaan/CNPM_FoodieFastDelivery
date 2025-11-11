# FoodFast Delivery (Spring Boot + Vanilla JS)

Nền tảng đặt món giao nhanh với cửa hàng, sản phẩm, giỏ hàng và đơn hàng. Backend dùng Spring Boot (Controller → Service → Repository), frontend HTML/CSS/JS thuần.

## Kiến trúc & Công nghệ

- Backend: Spring Boot 3, Spring Security (JWT), Spring Data JPA (Hibernate), Lombok, MapStruct, Maven
- CSDL: MySQL 8 (dev/prod). Flyway hiện tắt. Không còn H2.
- Frontend: HTML/CSS/JS thuần (không framework)
- Context-path: `/` (gốc). Base URL: http://localhost:8080

Thư mục chính:

```
backend/
  source/
    main/        # Java code (controllers, services, repositories, entities, config)
    resources/   # application.yaml, application-dev.yaml
frontend/        # HTML, CSS, JS, assets
pom.xml          # Maven build (đóng gói jar vào target/)
```

## Tính năng chính

- Đăng ký/đăng nhập JWT; phản hồi đăng nhập trả về accessToken và thông tin user.
- Quản lý cửa hàng, danh mục, sản phẩm, đơn hàng, người dùng.
- Giỏ hàng → tạo đơn: `POST /api/v1/orders`.
- Tra cứu và theo dõi giao hàng (admin): tiến trình QUEUED → ASSIGNED → LAUNCHED → ARRIVING → COMPLETED.
- Thanh toán VNPay (sandbox) và chế độ mô phỏng thanh toán thành công (dev).
- Quản lý địa chỉ người dùng (CRUD + đặt mặc định).

Tài khoản mẫu (nếu đã nhập data.sql demo):
- Admin: `admin` / `password`
- User: `linh.ng` / `password`

## Cấu hình

- File thật: `backend/source/resources/application.yaml`
- Mẫu: `backend/source/resources/application.example.yaml`

Sao chép file mẫu và điền thông tin MySQL + VNPay + JWT:

```
backend/source/resources/application.example.yaml → backend/source/resources/application.yaml
```

Các khóa chính trong cấu hình:
- spring.datasource.url/username/password: kết nối MySQL
- jwt.signerKey: khóa ký JWT
- vnpay.*: thông số sandbox VNPay
- app.payments.allow-simulate: cho phép mô phỏng thanh toán (dev)

Hồ sơ dev: `backend/source/resources/application-dev.yaml` (context-path `/`, MySQL, ddl-auto=update, spring.sql.init.mode=never).

## Chạy dự án

Yêu cầu: Java 17, Maven, MySQL 8. Trên Windows (PowerShell).

1) Build jar (bỏ qua test):

```powershell
mvn -DskipTests package
```

2) Chạy qua Maven (dev profile):

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'; mvn -q -f .\pom.xml spring-boot:run
```

3) Hoặc chạy jar:

```powershell
java -jar -Dspring.profiles.active=dev target\backend-0.0.1-SNAPSHOT.jar
```

Sau khi chạy: Backend tại `http://localhost:8080`.

### Chạy nhanh bằng VS Code Tasks

Terminal → Run Task…
- Backend (dev): run — chạy Spring Boot với profile `dev`
- Build Backend (Maven package) — đóng gói jar
- Run Backend (Spring Boot) — chạy qua Maven goal `spring-boot:run`
- Run Backend (Jar) — chạy file jar đã build

### Frontend

- Mở các file trong `frontend/` (ví dụ `index.html`, `stores.html`, `cart.html`, `orders.html`, `admin.html`).
- Dùng “Live Server” hoặc một static server để tránh CORS/file://.
- BASE_URL trong `frontend/js/config.js` mặc định: `http://localhost:8080`.

## Dữ liệu mẫu & chính sách seed

- ĐÃ GỠ seed tự động khi khởi động (CommandLineRunner). DB sẽ không bị ghi đè.
- Muốn có dữ liệu demo, nhập file `backend/data.sql` vào MySQL theo nhu cầu (thao tác thủ công 1 lần).
- spring.sql.init.mode=never trong profile dev để tránh chạy lại data.sql.

Các mục đã có trong data.sql (mẫu): users, roles, stores, addresses, categories, products, đơn hàng + thanh toán + delivery demo.

## Thanh toán mô phỏng (dev)

- Bật trong cấu hình: `app.payments.allow-simulate=true` (đã bật trong application-dev.yaml).
- Gọi: `POST /api/v1/payments/simulate-success?orderId={id}` để đánh dấu thanh toán thành công → hệ thống sẽ khởi tạo Delivery (nếu logic của bạn hỗ trợ).
- Gợi ý “test card”: Vì VNPay sandbox cần redirect, với mô phỏng bạn không cần nhập thẻ. Dùng endpoint simulate-success để bỏ qua form thẻ trong dev.

## Theo dõi giao hàng (Admin UI)

- Mở `frontend/admin.html` → Đơn hàng → nút Xem hoặc Theo dõi.
- Modal chi tiết hiển thị timeline giao hàng (QUEUED → ASSIGNED → LAUNCHED → ARRIVING → COMPLETED) và tự động polling 4s khi đang vận hành.
- Bạn có thể dùng data.sql đã có đơn và delivery mẫu để kiểm thử.

## Quản lý địa chỉ người dùng

Backend endpoints (`/users/{userId}/addresses`):
- GET: lấy danh sách địa chỉ
- POST: tạo địa chỉ
- PUT: cập nhật địa chỉ `/users/{userId}/addresses/{addressId}`
- DELETE: xóa địa chỉ `/users/{userId}/addresses/{addressId}`
- PUT: đặt mặc định `/users/{userId}/addresses/{addressId}/set-default`

Admin UI: trong modal người dùng có phần “Địa chỉ người dùng” (thêm/sửa/xóa/đặt mặc định).

## API quan trọng (tóm tắt)

- Auth: `/auth/signup`, `/auth/login`, `/auth/logout`, `/auth/validate`
- Stores: `/api/stores` (CRUD, địa chỉ, thông tin thanh toán)
- Products: `/products` (theo danh mục/cửa hàng)
- Cart: `/api/cart` (thêm/sửa/xóa/đếm)
- Orders: `/api/v1/orders` (+ `/user/{userId}`, `/code/{orderCode}`)
- Delivery: `/api/v1/deliveries/order/{orderId}` (truy vấn trạng thái đơn)
- Payment: `/api/v1/payments/*`, `POST /api/v1/payments/simulate-success?orderId=...` (dev)

Lưu ý: Context-path `/` (không còn `/home`).

## Bảo mật

- JWT Bearer: gửi `Authorization: Bearer <token>`.
- CORS mở cho dev.

## Khắc phục sự cố

- Nếu chạy thất bại: kiểm tra MySQL (DB tồn tại/quyền/mật khẩu), chỉnh spring.datasource trong `application-dev.yaml` hoặc `application.yaml`.
- 404: kiểm tra đúng base URL (`/`) và endpoint.
- CORS: hãy dùng Live Server cho frontend.

## Tài liệu thiết kế (PlantUML)

Thư mục PlantUML: `Docucment_PRD/plantuml`

- Use Case tổng quan: `usecase-nguoi-dung-admin.puml`
- Activity đặt hàng & thanh toán: `activity-dat-hang-thanh-toan.puml`
- Activity vòng đời giao hàng bằng drone: `activity-giao-hang-drone.puml`
- Sequence thanh toán: `sequence-thanh-toan.puml`
- Sequence theo dõi giao hàng: `sequence-theo-doi-giao-hang.puml`
- Backend mô tả thiết kế: `backend-thiet-ke-mo-ta.puml`
- ERD cơ sở dữ liệu: `erd-database.puml`
- Component backend: `component-backend.puml`
- Deployment: `deployment.puml`

Cách xem nhanh (khuyến nghị VS Code):
1) Cài extension PlantUML
2) Cài Graphviz hoặc dùng server render online
3) Mở trực tiếp các file `.puml` để xem/Export PNG/SVG

## Phát triển & đóng góp

- Java 17, cấu trúc chuẩn của dự án.
- Package chính: `controller`, `service`, `repository`, `entity`, `dto`, `config`.
