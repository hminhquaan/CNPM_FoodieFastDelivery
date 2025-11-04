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
