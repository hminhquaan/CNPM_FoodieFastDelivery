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

## Quick start

1) Install deps

```powershell
npm install
```

2) Setup DB (SQLite) and generate client

```powershell
npx prisma generate
npx prisma migrate dev --name init
```

3) Run dev server

```powershell
npm run dev
```

4) Seed demo data (dev only)

```powershell
# POST http://localhost:4000/api/dev/seed
```

5) Try APIs
- GET http://localhost:4000/health
- GET http://localhost:4000/api/restaurants/near?lat=10.7769&lng=106.7009&radiusKm=3
- GET http://localhost:4000/api/menu/{restaurantId}
- POST http://localhost:4000/api/orders
- GET http://localhost:4000/api/orders/my (JWT required)

Pagination (page, pageSize) có ở các endpoint:
- GET /api/restaurants
- GET /api/menu/{restaurantId}
- GET /api/orders/my

Body example:

```json
{
  "restaurantId": "<id>",
  "dropoffLat": 10.78,
  "dropoffLng": 106.71,
  "items": [
    {"menuItemId":"<menuItemId>","quantity":2}
  ]
}
```

Kết quả tạo đơn sẽ bao gồm `estimatedReadyAt` — thời điểm ước lượng món sẽ sẵn sàng (dựa trên prepTime của món và số lượng). Việc theo dõi thời gian chế biến không cần realtime; hệ thống chỉ phát realtime cho giai đoạn giao hàng (telemetry, delivery_update, order_status).

ETA & Drone scheduling
- ETA bếp (`Order.estimatedReadyAt`) được lưu khi tạo đơn, dùng làm mốc hẹn drone.
- Hệ thống hẹn gán drone gần ETA bếp với "lead động":
  - Lead = max(DRONE_LEAD_MINUTES, ceil(thời gian bay drone rảnh gần nhất tới nhà hàng + DRONE_BUFFER_MINUTES)), kẹp 0–30 phút.
  - Thời điểm hẹn = `estimatedReadyAt - Lead`.
- Khi drone được gán, hệ thống tính `pickupEta` (now) và `dropoffEta` dựa trên quãng đường, và phát realtime.

### Realtime tracking
- Socket.IO server attached to the same HTTP server.
- Cần xác thực Socket.IO bằng JWT trước khi subscribe: emit `authenticate` với accessToken.
- Join room `order:{orderId}` qua sự kiện `subscribe` để nhận updates (hệ thống sẽ kiểm tra quyền: chủ đơn, OWNER/STAFF của nhà hàng, hoặc ADMIN).
- Events: `telemetry` (lat,lng,battery), `order_status` (order status changes), `delivery_update`.

Example (browser):

```html
<script src="https://cdn.socket.io/4.7.5/socket.io.min.js"></script>
<script>
  const socket = io('http://localhost:4000');
  const orderId = '<your-order-id>';
  socket.emit('authenticate', '<accessToken>');
  socket.on('authenticated', () => {
    socket.emit('subscribe', `order:${orderId}`);
  });
  socket.on('telemetry', (t) => console.log('telemetry', t));
  socket.on('order_status', (u) => console.log('order status', u));
  socket.on('delivery_update', (u) => console.log('delivery update', u));
</script>
```

## Auth & Roles

- JWT-based auth. Login returns a token with payload: `{ sub, email, role }`.
- Use `Authorization: Bearer <token>` on protected endpoints.

Tokens & phiên làm việc:
- `POST /api/auth/login` trả về `{ accessToken, refreshToken }`
- `POST /api/auth/refresh` nhận `{ refreshToken }` để cấp mới cặp token (rotate, refresh token 1 lần dùng)
- `POST /api/auth/logout` nhận `{ refreshToken }` để revoke token hiện tại
- Access token mặc định 15 phút; Refresh token mặc định 30 ngày (config cố định trong mã, có thể nâng cấp thành biến môi trường)

Mô hình quyền:
- Role toàn cục: CUSTOMER | STAFF | ADMIN
- Quyền theo nhà hàng: OWNER là người có `ownerId` trùng với `sub`

Endpoint & bảo vệ:
- Tạo nhà hàng: `POST /api/restaurants` (yêu cầu đăng nhập) → user tạo trở thành OWNER của nhà hàng vừa tạo
- Menu: `POST /api/menu/:restaurantId` (OWNER hoặc STAFF của nhà hàng, hoặc ADMIN)
- Đơn hàng: `POST /api/orders` (yêu cầu đăng nhập/CUSTOMER)
- Nhà hàng của tôi: `GET /api/restaurants/mine` (yêu cầu đăng nhập)
- Quản lý nhân viên nhà hàng (OWNER/ADMIN):
  - GET `/api/restaurants/:restaurantId/staff`
  - POST `/api/restaurants/:restaurantId/staff` body `{ userId }`
  - DELETE `/api/restaurants/:restaurantId/staff/:userId`
 - Đơn hàng của nhà hàng (OWNER/STAFF/ADMIN):
   - GET `/api/restaurants/:restaurantId/orders` (có phân trang `page`, `pageSize`)
   - PATCH `/api/restaurants/:restaurantId/orders/:orderId/status` body `{ status }`
     - `status` hợp lệ: `CONFIRMED`, `PREPARING`, `ASSIGNED`, `PICKED_UP`, `DELIVERING`, `DELIVERED`, `CANCELED`
     - Chuyển trạng thái có kiểm soát (ví dụ: `CONFIRMED -> PREPARING -> PICKED_UP -> DELIVERING -> DELIVERED`)

## Config
- See `.env.example`
- `PAYMENT_PROVIDER=FAKE|STRIPE|PAYPAL`
- `DELIVERY_BASE_CENTS` và `DELIVERY_PER_KM_CENTS` để cấu hình giá ship (mặc định 10000 + 5000/km)
 - Rate limiting:
   - `RATE_LIMIT_WINDOW_MS` (mặc định 60000)
   - `RATE_LIMIT_MAX` (mặc định 100)
   - `RATE_LIMIT_LOGIN_MAX` (mặc định 10)
 - Drone scheduling:
   - `DRONE_LEAD_MINUTES` (mặc định 5): lead tối thiểu trước ETA bếp để hẹn drone.
   - `DRONE_BUFFER_MINUTES` (mặc định 1): đệm thêm vào ước tính thời gian bay từ drone → nhà hàng (spin-up/chờ bàn giao).

## Testing
- Jest configured. Add tests under `tests/`.
 - Chạy test: `npm test`

## Postman
- Import file `docs/postman_collection.json`
- Thiết lập variable `baseUrl` và `bearerToken`

## Notes
- Enums are strings in Prisma due to SQLite limitations.
- Shipping cost is estimated before creation and persisted in `Order.shippingCents`.
- Payment gateways are stubs for demo purposes.
=======
# CNPM_FoodieFastDelivery

>>>>>>> 14ac9948f66e61ca0895cf95ff983e6c659289df
