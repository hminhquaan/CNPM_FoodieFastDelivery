# Thiết kế Backend (FoodFast Delivery)

Tài liệu tóm tắt kiến trúc, các quyết định thiết kế, ERD, và các sơ đồ cấu phần chính của hệ thống.

## 1. Tổng quan kiến trúc

- Kiến trúc 3 lớp: Controller → Service → Repository
- Spring Boot 3, Spring Data JPA (Hibernate), Lombok, MapStruct
- Bảo mật: JWT Bearer (Spring Security)
- Thanh toán: VNPay (sandbox) với chữ ký HMAC, xử lý return & IPN
- Giao vận: Tự động tạo Delivery sau thanh toán, gán Drone theo tải trọng, pin và khoảng cách, cập nhật trạng thái tự động
- Hồ sơ chạy: dev (H2, seed demo), prod-like (MySQL)

## 2. Các module chính

- Auth: đăng ký/đăng nhập, refresh token, xác thực
- User: thông tin người dùng, địa chỉ nhận hàng (tọa độ)
- Store: cửa hàng, địa chỉ cửa hàng (tọa độ), sổ cái
- Product: danh mục, sản phẩm, tồn kho đơn giản
- Cart: giỏ hàng của người dùng, item theo sản phẩm và cửa hàng
- Order: tạo đơn theo từng cửa hàng từ giỏ; snapshot địa chỉ nhận (JSON có lat/lng)
- Payment (VNPay): khởi tạo thanh toán, xác thực return & IPN, trạng thái giao dịch
- Delivery: tạo record sau thanh toán thành công, tiến trình vận chuyển
- Drone: quản lý drone (đăng ký, cập nhật vị trí/trạng thái), chọn drone phù hợp
- Seed/Demo: seed dữ liệu mẫu; mô phỏng thanh toán (dev)

Xem sơ đồ Component: `Docucment_PRD/plantuml/component_vi.puml`.

## 3. Dòng dữ liệu/chức năng

1) Khách duyệt cửa hàng/sản phẩm, thêm vào giỏ
2) Gửi yêu cầu tạo đơn → backend tách theo cửa hàng, lưu Order + OrderItem
3) Khởi tạo thanh toán VNPay → redirect khách đến VNPay
4) VNPay return (hiển thị) và IPN (quyết định) → backend đánh dấu thanh toán thành công
5) Tự động tạo Delivery → chọn Drone → tiến trình LAUNCHED/ARRIVING/COMPLETED
6) Frontend polling theo dõi Delivery đến khi hoàn tất

Xem Activity: `Docucment_PRD/plantuml/activity_checkout_delivery_vi.puml` và Sequence tổng: `Docucment_PRD/plantuml/sequence_end_to_end_vi.puml`.

## 4. ERD (mối quan hệ dữ liệu)

Các thực thể chính: User, Roles, UserAddress, Store, StoreAddress, ProductCategory, Product, Cart, CartItem, Order, OrderItem, PaymentTransaction, Delivery, Drone, FlightPlan, FlightPlanPoint, StoreLedger, PayoutBatch.

Xem ERD: `Docucment_PRD/plantuml/erd_vi.puml`.

Lưu ý thiết kế:
- UserAddress và StoreAddress lưu lat/lng để phục vụ lựa chọn Drone và tính khoảng cách
- Order lưu `deliveryAddressSnapshot` để đảm bảo bất biến theo thời điểm đặt
- PaymentTransaction liên kết với Order; VNPay IPN là nguồn quyết định cuối
- Delivery liên kết Order và Drone; trạng thái tiến dần đến COMPLETED

## 5. Thiết kế bảo mật

- JWT Bearer cho các API cần đăng nhập
- CORS mở trong dev để thuận tiện; có thể siết chặt theo domain
- Mật khẩu băm, khóa JWT quản lý qua cấu hình

## 6. Thiết kế thanh toán

- Mỗi lần thanh toán: sinh `vnp_TxnRef` duy nhất
- Thêm `vnp_CreateDate`/`vnp_ExpireDate` để tránh lỗi timeout sandbox
- Không ép `bankCode` để tránh code=76
- Return page chỉ hiển thị; IPN quyết định cập nhật Order/Payment

## 7. Thiết kế giao vận Drone

- Chọn drone dựa trên: trạng thái AVAILABLE, tải trọng tối đa, pin hiện tại, khoảng cách tới điểm lấy hàng
- Ưu tiên drone gần hơn; nếu tương đương, chọn pin cao hơn
- Khi giao xong, đặt drone AVAILABLE

## 8. Cấu hình & Profile

- `application-dev.yaml`: H2 in-memory, seed demo, bật mô phỏng
- `application.yaml`: dùng MySQL và thông số thật (không commit vào repo)
- Flags dev:
  - `app.demo.enable=true` → seed drone mẫu
  - `app.payments.allow-simulate=true` → endpoint mô phỏng thanh toán

## 9. Triển khai (Deployment)

- Mô hình tham chiếu: Browser ↔ Spring Boot ↔ MySQL; tích hợp VNPay; kết nối tới Drone (nếu có API)
- Xem sơ đồ Deployment: `Docucment_PRD/plantuml/deployment_vi.puml`

## 10. Mở rộng tương lai

- WebSocket để realtime thay vì polling
- Tối ưu thuật toán chọn drone (thời tiết, no-fly zone)
- Tối ưu hóa bảo mật, phân quyền chặt cho quản trị cửa hàng
- Hoàn thiện payout, sổ cái theo kỳ
