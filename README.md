<div align="center">

# 🍔🚁 Drone Fastfood — Hệ thống giao đồ ăn bằng drone

**Nền tảng giao đồ ăn full‑stack kết nối nhà hàng, khách hàng và điều phối giao hàng theo thời gian thực.**

<sub>Công nghệ phần mềm — Lớp DCT122C3</sub>

</div>


## ✨ Tính năng chính

- Đăng ký/Đăng nhập, phân quyền (Khách hàng, Quản trị, Chủ cửa hàng)
- Quản lý nhà hàng và thực đơn, giỏ hàng, đặt món, thanh toán giả lập
- Theo dõi đơn hàng thời gian thực trên bản đồ (OpenLayers)
- Mô phỏng giao hàng bằng drone: bay, tiêu hao pin theo quãng đường, về trạm sạc, sạc tăng dần
- Trang quản trị: quản lý người dùng/đơn hàng/thực đơn, theo dõi và điều khiển drone (gọi về trạm, sạc)
- Ghi nhận các chỉ số sau giao hàng: phần trăm pin đã dùng, quãng đường, thời gian bay; lưu drone đã giao đơn

## 🧰 Tech Stack

- Frontend: HTML/CSS/JS thuần + OpenLayers; Dev server: Node.js (Express) + LiveReload
- Backend: Java 17 + Spring Boot 3 (Web, Security, Validation, Data JPA)
- CSDL: MySQL (Flyway sẵn sàng; seed dữ liệu qua `application-dev.yaml` nếu bật)
- Build: Maven

---

## 🚀 Bắt đầu

Yêu cầu trước:
- JDK 17
- Maven 3.8+
- Node.js 18+ (khuyến nghị v20)
- MySQL 8.0 đang chạy tại máy (mặc định `localhost:3306`)

### 1) Cấu hình môi trường Backend (dev)

Chỉnh file `backend/source/resources/application-dev.yaml` cho kết nối MySQL của bạn (user/password/database). Mặc định chạy cổng `8080` với profile `dev`.

Ví dụ cấu hình (rút gọn):

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fast_food_delivery?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&useLegacyDatetimeCode=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8
    username: root
    password: <your_password>
  jpa:
    hibernate:
      ddl-auto: create
```

### 2) Cài dependencies

Backend (Maven):

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery"
mvn -DskipTests package
```

Frontend (Node):

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery\frontend"
npm install
```

### 3) Chạy chế độ phát triển (hot reload)

- Backend (Spring Boot, profile `dev`):

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery"
$env:SPRING_PROFILES_ACTIVE='dev'; mvn -q spring-boot:run
# Backend tại http://localhost:8080
```

- Frontend (Express dev server + LiveReload, proxy về backend):

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery\frontend"
npm run dev
# Frontend tại http://localhost:3000 (proxy /api, /auth, /drones ... → http://localhost:8080)
```

Gợi ý:
- Tắt proxy nếu cần: `npm run dev:noproxy`
- Tắt live-reload: `npm run dev:nolive`
- Đổi backend URL tạm thời: `BACKEND_URL=http://localhost:8080 npm run dev`

### 4) Chạy kiểu “production” cục bộ (không Docker)

Build backend JAR và chạy:

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery"
mvn -DskipTests package
java -jar target\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
# API tại http://localhost:8080
```

Serve frontend tĩnh qua `frontend/server.js` hoặc bất kỳ static server nào:

```powershell
cd "c:\Users\hmquaan\Downloads\SGU 2022-2027\Sem1 2025-2026\CNPM\FoodFastDelivery\frontend"
npm run dev:http
# Mặc định http://localhost:3000 (không proxy). Sử dụng trình duyệt mở các trang .html
```

### 5) Tài khoản và dữ liệu mẫu

- Có thể bật seed trong `application-dev.yaml` (mục `spring.sql.init` → `mode: always`) để nạp dữ liệu demo.
- Người dùng quản trị/owner có thể cấu hình qua bảng CSDL hoặc endpoint quản trị nếu đã bật.

---

## 🗂️ Cấu trúc dự án (rút gọn)

```
FoodFastDelivery/
├─ backend/
│  └─ source/
│     ├─ main/                   # AppMain.java, controllers, services, entities, ...
│     └─ resources/              # application-dev.yaml, data.sql
├─ frontend/
│  ├─ *.html                     # index, admin, tracking, orders, ...
│  ├─ js/                        # main.js, tracking.js, admin.js, api.js, ...
│  ├─ css/, img/, vendor/
│  └─ server.js                  # Dev server (Express + proxy + livereload)
├─ pom.xml                       # Maven (Spring Boot)
└─ package.json                  # Frontend dev scripts
```

## 🧪 Một số API tiêu biểu

- Auth: `POST /auth/login`, `GET /auth/validate`, `POST /auth/logout`
- Sản phẩm/Đơn hàng: `GET /api/v1/products`, `POST /api/v1/orders`, `GET /api/v1/orders/{id}`
- Giao hàng: `GET /api/v1/deliveries/by-order/{orderId}`, `PATCH /api/v1/deliveries/{id}/status`
- Drone: `GET /drones`, `POST /drones/{id}/return-to-station`, `POST /drones/{id}/charge`

Lưu ý: Đường dẫn cụ thể có thể thay đổi theo module, xem mã nguồn controller trong `backend/source/main/controller/**`.

## 📖 Hướng dẫn sử dụng chi tiết

1) Khách hàng
- Đăng ký/Đăng nhập từ `index.html`.
- Chọn món, thêm giỏ hàng, tiến hành thanh toán giả lập.
- Theo dõi đơn hàng tại `tracking.html?orderId=<ID>`:
  - Bản đồ hiển thị lộ trình và drone (dấu chấm).
  - Khi hoàn thành, trang hiển thị thông báo một lần; các lần mở lại sẽ ẩn thông báo và vẫn hiện bảng thông tin drone (pin đã dùng, quãng đường, thời gian bay, mã drone đã giao). Có thể ép ẩn bằng `&reopen=1`.
- Trang `orders.html`: với đơn đã giao, nút “Xem kết quả giao hàng” mở lại tracking cho đơn đó.

2) Quản trị/Chủ cửa hàng
- Mở `admin.html` để theo dõi danh sách drone và đơn hàng.
- Hành động:
  - “Return to station”: gọi drone về trạm; khi về trạm có thể sạc.
  - “Charge”: bắt đầu sạc, pin tăng dần tới 100% (auto-refresh trong khi sạc).
- Lưu ý: Drone tiêu hao pin theo quãng đường khoảng 12%/km + 15% (tối thiểu 25%).

3) Ghi nhận sau giao hàng (telemetry)
- Khi đơn hoàn tất, hệ thống:
  - Trừ pin drone theo quãng đường thực, cập nhật vị trí và thời điểm telemetry.
  - Ghi `batteryUsedPercent`, `distanceKm`, `actualFlightTimeSeconds` vào bản ghi giao hàng.
  - Đánh dấu `deliveredDroneId`/`deliveredDroneCode` trong đơn.

## 🛠️ Khắc phục sự cố nhanh

- Frontend báo proxy lỗi `ECONNREFUSED`: Hãy đảm bảo backend đang chạy ở `http://localhost:8080` hoặc bật `DEV_PROXY=0` để tạm tắt proxy.
- Không kết nối MySQL: Kiểm tra `application-dev.yaml` (URL, user/password), MySQL đang chạy, tài khoản có quyền.
- Seed dữ liệu chạy mỗi lần khởi động: Điều chỉnh `spring.sql.init.mode` và `data-locations` trong `application-dev.yaml`.
- Lỗi biên dịch Lombok/MapStruct: Mở bằng VS Code với Java Extension Pack; chạy `mvn -DskipTests package` để xác nhận build OK.

---

## 👥 Nhóm thực hiện
- Huỳnh Minh Quân - 3122411167
- Lê Duy Tín - 31224111210
> Giảng viên hướng dẫn: TS. Nguyễn Quốc Huy
