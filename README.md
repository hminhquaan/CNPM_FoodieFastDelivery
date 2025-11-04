# FoodFast Delivery (Spring Boot + Vanilla JS)

Nền tảng đặt món giao nhanh với cửa hàng, sản phẩm, giỏ hàng và đơn hàng. Backend dùng Spring Boot (mô hình 3 lớp: Controller → Service → Repository), frontend là HTML/CSS/JS thuần.

## Kiến trúc & Công nghệ

- Backend: Spring Boot 3, Spring Security (JWT), Spring Data JPA (Hibernate), Lombok, MapStruct, Flyway (tạm tắt), Maven
- CSDL: MySQL 8 (prod), H2 in-memory (dev)
- Frontend: HTML/CSS/JS thuần (không framework)
- Đường dẫn gốc backend: http://localhost:8080/home (context-path `/home`)

Thư mục chính:

```
backend/
  source/
    main/        # Java code (controllers, services, repositories, entities, config)
    resources/   # application.yaml, application-dev.yaml, migrations
frontend/        # HTML, CSS, JS, assets
pom.xml          # Maven build (đóng gói jar vào target/)
```

## Tính năng chính

- Đăng ký/đăng nhập JWT; phản hồi đăng nhập trả về accessToken và thông tin user (có `id`).
- Quản lý cửa hàng và danh mục/sản phẩm.
- Giỏ hàng → tạo nhiều đơn theo từng cửa hàng: `POST /api/v1/orders`.
- Tra cứu đơn theo user, theo cửa hàng; cập nhật trạng thái đơn; tích hợp thanh toán VNPay (sandbox).
- Seed dữ liệu mẫu tự động khi chạy lần đầu (roles, accounts, categories, stores, products) và endpoint seed thủ công.

Tài khoản mẫu (seed):
- Admin: `admin` / `admin123`
- User: `user` / `user123`

## Cấu hình

- File thật: `backend/source/resources/application.yaml` (đang được .gitignore để tránh lộ secret)
- Mẫu cấu hình: `backend/source/resources/application.example.yaml`

Sao chép file mẫu và điền thông tin MySQL + VNPay + JWT:

```
backend/source/resources/application.example.yaml → backend/source/resources/application.yaml
```

Các khóa chính trong cấu hình:
- spring.datasource.url/username/password: kết nối MySQL
- jwt.signerKey: khóa ký JWT
- vnpay.*: thông số sandbox VNPay

Hồ sơ dev (H2 in-memory): `backend/source/resources/application-dev.yaml`.

## Chạy dự án

Yêu cầu: Java 17, Maven, MySQL 8 (nếu chạy profile mặc định). Trên Windows (PowerShell).

1) Build jar (bỏ qua test):

```powershell
mvn -DskipTests package
```

2) Chạy với MySQL (profile mặc định, dùng `application.yaml`):

```powershell
java -jar target\backend-0.0.1-SNAPSHOT.jar
```

3) Hoặc chạy dev với H2 (không cần MySQL):

```powershell
java -jar -Dspring.profiles.active=dev target\backend-0.0.1-SNAPSHOT.jar
```

Sau khi chạy: Backend lắng nghe tại `http://localhost:8080/home`.

### Frontend

- Mở các file trong `frontend/` (ví dụ `index.html`, `stores.html`, `cart.html`, `orders.html`).
- Khuyến nghị dùng “Live Server” của VS Code hoặc bất kỳ static server nào để tránh vấn đề CORS/file://.
- Frontend đã cấu hình BASE_URL = `http://localhost:8080/home` trong `frontend/js/config.js`.

## API quan trọng (tóm tắt)

- Auth: `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/validate`
- Stores: `/api/stores` (CRUD, tìm kiếm, theo owner, kèm sản phẩm)
- Products: `/products` (theo danh mục, theo cửa hàng, tìm kiếm)
- Cart: `/api/cart` (thêm/sửa/xóa/đếm)
- Orders: `/api/v1/orders` (+ `/user/{userId}`, `/store/{storeId}`, cập nhật trạng thái)
- Payment (VNPay): `/api/v1/payments/*`

Lưu ý base URL luôn có tiền tố `/home` do context-path.

## Seed dữ liệu mẫu

- Tự động seed tại `AppMain` (roles, users, categories, stores, products) nếu DB trống.
- Có endpoint thủ công: `GET /home/debug/seed` trả về số lượng đã seed.

## Bảo mật

- JWT Bearer: gửi `Authorization: Bearer <token>`.
- CORS: đã mở cho mọi origin trong dev.
- Hiện tại nhiều endpoint đang `permitAll` để tiện dev. Có thể siết chặt yêu cầu đăng nhập cho `/api/v1/orders/**` sau.

## VS Code Tasks

- Build Backend (Maven package): đóng gói jar vào `target/`.
- Run Backend (Jar): chạy `java -jar target\backend-0.0.1-SNAPSHOT.jar`.

## Khắc phục sự cố

- Jar thoát với mã 1 ngay khi chạy:
  - Kiểm tra cấu hình MySQL trong `application.yaml` (DB chưa tạo/quyền sai/mật khẩu sai).
  - Thử profile dev (H2): `-Dspring.profiles.active=dev`.
- 404 hoặc gọi sai URL: nhớ base path là `/home` (ví dụ: `/home/api/stores`).
- CORS: đã bật trên backend; nếu mở file HTML trực tiếp, hãy dùng Live Server để chắc chắn.

## Phát triển & đóng góp

- Chuẩn Java 17, format theo mặc định của dự án.
- Các package chính: `controller`, `service`, `repository`, `entity`, `dto`, `config`.
- Pull Request chào mừng. Đừng commit `application.yaml` (đã ignore).

---

Copyright © 2025
<<<<<<< HEAD
# Food Fast Delivery with Smart Drones (Backend)

A 3-layer Node.js/TypeScript backend implementing:
- Presentation (Express API): DTOs (Zod), Auth (JWT), Realtime (Socket.IO)
- Business (Services): Auth, Restaurant Nearby, Orders (transaction), Drone assignment + telemetry, Payments (stub)
- Data (Repositories): Prisma ORM (SQLite)
- Infrastructure: Logging (Pino), Config (.env), DI (tsyringe), Caching (in-memory)
