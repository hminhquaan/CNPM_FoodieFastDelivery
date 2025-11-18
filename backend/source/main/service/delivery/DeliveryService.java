package service.delivery;

import repository.delivery.DeliveryRepository;
import repository.drone.DroneRepository;
import service.drone.DroneService;
import repository.order.OrderRepository;
import repository.store.StoreRepository;
import repository.store.StoreAddressRepository;
import repository.order.OrderItemRepository;
import repository.product.ProductRepository;
import dto.request.delivery.AssignDroneRequest;
import dto.request.delivery.CreateDeliveryRequest;
import dto.request.delivery.UpdateDeliveryStatusRequest;
import dto.response.delivery.DeliveryResponse;
import entity.Delivery;
import entity.Drone;
import entity.Order;
import entity.Store;
import enums.DeliveryStatus;
import enums.DroneStatus;
import enums.OrderStatus;
import enums.PaymentStatus;
import exception.AppException;
import exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DroneRepository droneRepository;
    private final StoreRepository storeRepository;
    private final DroneService droneService;
    private final StoreAddressRepository storeAddressRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    /**
     * Tạo delivery mới khi order được thanh toán thành công
     */
    @Transactional
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {
        log.info("Creating delivery for order: {}", request.getOrderId());

        // Kiểm tra order tồn tại và đã thanh toán
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        // Chỉ cần thanh toán thành công (PaymentStatus = PAID),
        // cho phép tạo Delivery ở trạng thái PAID hoặc ACCEPT
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_NOT_PAID);
        }

        // Kiểm tra đã có delivery chưa
        if (deliveryRepository.existsByOrderId(request.getOrderId())) {
            throw new AppException(ErrorCode.DELIVERY_ALREADY_EXISTS);
        }

        // Tạo delivery với status QUEUED (đang chờ xử lý)
        Delivery delivery = Delivery.builder()
                .orderId(request.getOrderId())
                .pickupStoreId(request.getPickupStoreId())
                .dropoffAddressSnapshot(request.getDropoffAddressSnapshot())
                .currentStatus(DeliveryStatus.QUEUED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        delivery = deliveryRepository.save(delivery);
        log.info("Delivery created with ID: {}", delivery.getId());

        // Attempt auto-assign once; if no drone, keep QUEUED (do NOT cancel order or mark FAILED).
        try {
            DeliveryResponse assigned = autoAssignDrone(delivery.getId());
            if (assigned.getCurrentStatus() == DeliveryStatus.ASSIGNED) {
                startAutoProgressThread(assigned.getId());
                return assigned;
            }
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.NO_AVAILABLE_DRONE) {
                log.info("No available drone now; delivery {} remains QUEUED for later assignment", delivery.getId());
            } else {
                log.warn("Auto-assign after create failed (non-drone-availability): {}", ex.getMessage());
            }
        }
        return toDeliveryResponse(delivery);
    }

    /**
     * Gán drone cho delivery (tự động hoặc manual)
     */
    @Transactional
    public DeliveryResponse assignDrone(Long deliveryId, Long droneId) {
        log.info("Assigning drone {} to delivery {}", droneId, deliveryId);

        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        // Kiểm tra drone có sẵn sàng không
        if (drone.getStatus() != DroneStatus.AVAILABLE) {
            throw new AppException(ErrorCode.DRONE_NOT_AVAILABLE);
        }

        // Kiểm tra delivery có đang ở trạng thái QUEUED không
        if (delivery.getCurrentStatus() != DeliveryStatus.QUEUED) {
            throw new AppException(ErrorCode.DELIVERY_ALREADY_ASSIGNED);
        }

        // Gán drone
        delivery.setDroneId(droneId);
        delivery.setCurrentStatus(DeliveryStatus.ASSIGNED);
        delivery.setUpdatedAt(LocalDateTime.now());

        // Cập nhật trạng thái drone
        drone.setStatus(DroneStatus.IN_FLIGHT);
        droneRepository.save(drone);

        delivery = deliveryRepository.save(delivery);
        log.info("Drone {} assigned to delivery {} successfully", droneId, deliveryId);

        return toDeliveryResponse(delivery);
    }

    /**
     * Tự động tìm và gán drone phù hợp
     */
    @Transactional
    public DeliveryResponse autoAssignDrone(Long deliveryId) {
        log.info("Auto-assigning drone for delivery {}", deliveryId);

        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        Order order = orderRepository.findById(delivery.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        Store store = storeRepository.findById(delivery.getPickupStoreId())
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Lấy tọa độ cửa hàng
        Double storeLat = null, storeLng = null;
        var addresses = storeAddressRepository.findByStore_Id(store.getId());
        if (!addresses.isEmpty()) {
            var addr = addresses.get(0);
            storeLat = addr.getLatitude();
            storeLng = addr.getLongitude();
        }

        // Parse tọa độ khách từ snapshot JSON: hỗ trợ cả key lat/lng hoặc latitude/longitude
        Double customerLat = null, customerLng = null;
        try {
            String snap = order.getDeliveryAddressSnapshot();
            if (snap != null && snap.trim().startsWith("{")) {
                // Dùng Jackson nếu có trong classpath (Spring Boot có sẵn). Fallback regex nếu lỗi.
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<String, Object> map = mapper.readValue(snap, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                    Object latObj = map.get("lat");
                    Object lngObj = map.get("lng");
                    // Hỗ trợ cả "latitude" / "longitude" từ dữ liệu seed cũ
                    if (latObj == null) latObj = map.get("latitude");
                    if (lngObj == null) lngObj = map.get("longitude");
                    if (latObj instanceof Number) customerLat = ((Number) latObj).doubleValue();
                    else if (latObj instanceof String) customerLat = Double.parseDouble(((String) latObj).trim());
                    if (lngObj instanceof Number) customerLng = ((Number) lngObj).doubleValue();
                    else if (lngObj instanceof String) customerLng = Double.parseDouble(((String) lngObj).trim());
                } catch (Exception jex) {
                    log.warn("Jackson parse snapshot thất bại: {}", jex.getMessage());
                    // Fallback regex
                    try {
                        java.util.regex.Matcher mLat = java.util.regex.Pattern.compile("\"(lat|latitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                        if (mLat.find()) customerLat = Double.parseDouble(mLat.group(2));
                        java.util.regex.Matcher mLng = java.util.regex.Pattern.compile("\"(lng|longitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                        if (mLng.find()) customerLng = Double.parseDouble(mLng.group(2));
                    } catch (Exception rex) {
                        log.warn("Regex parse snapshot thất bại: {}", rex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Parse tọa độ khách thất bại: {}", e.getMessage());
        }

    // Fallback nếu thiếu dữ liệu (tọa độ demo gần nhau để tránh khoảng cách quá xa)
    if (storeLat == null || storeLng == null) { storeLat = 10.762622; storeLng = 106.660172; }
    if (customerLat == null || customerLng == null) { customerLat = 10.764200; customerLng = 106.662300; }

        // Tính trọng lượng (gram) từ order items
        int weightGram = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(item -> {
                    var p = productRepository.findById(item.getProductId()).orElse(null);
                    int w = (p!=null && p.getWeightGram()!=null) ? p.getWeightGram() : 250;
                    return w * (item.getQuantity()==null?1:item.getQuantity());
                })
                .reduce(0, Integer::sum);
        if (weightGram <= 0) weightGram = 500;

        // Tìm drone phù hợp theo khoảng cách và trọng lượng
        var droneResponse = droneService.findAvailableDroneForDelivery(
                weightGram,
                storeLat, storeLng,
                customerLat, customerLng
        );

        // Gán drone tìm được
        return assignDrone(deliveryId, droneResponse.getId());
    }

    /**
     * Start background thread to auto progress delivery through flight lifecycle.
     */
    private void startAutoProgressThread(Long deliveryId) {
        new Thread(() -> {
            try {
                Thread.sleep(1200); // small delay before launch
                updateDeliveryStatus(deliveryId, UpdateDeliveryStatusRequest.builder().status(DeliveryStatus.LAUNCHED).build());
                Thread.sleep(3000);
                updateDeliveryStatus(deliveryId, UpdateDeliveryStatusRequest.builder().status(DeliveryStatus.ARRIVING).build());
                Thread.sleep(3000);
                updateDeliveryStatus(deliveryId, UpdateDeliveryStatusRequest.builder().status(DeliveryStatus.COMPLETED).build());
            } catch (Exception e) {
                log.warn("Auto progress thread error for delivery {}: {}", deliveryId, e.getMessage());
            }
        }, "delivery-auto-progress-" + deliveryId).start();
    }

    /**
     * Cập nhật trạng thái delivery
     */
    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, UpdateDeliveryStatusRequest request) {
        log.info("Updating delivery {} status to {}", deliveryId, request.getStatus());
        Long delKey = java.util.Objects.requireNonNull(deliveryId);
        Delivery delivery = deliveryRepository.findById(delKey)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        Long ordKey = java.util.Objects.requireNonNull(delivery.getOrderId());
        Order order = orderRepository.findById(ordKey)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        DeliveryStatus oldStatus = delivery.getCurrentStatus();
        DeliveryStatus newStatus = request.getStatus();

        // Validate status transition
        validateStatusTransition(oldStatus, newStatus);

        // Update delivery status
        delivery.setCurrentStatus(newStatus);
        delivery.setUpdatedAt(LocalDateTime.now());

        // Handle specific status changes
        switch (newStatus) {
            case LAUNCHED:
                // Drone đã cất cánh
                delivery.setActualDepartureTime(LocalDateTime.now());
                order.setStatus(OrderStatus.IN_DELIVERY);
                break;

            case ARRIVING:
                // Drone đang đến gần điểm giao
                // No additional action needed
                break;

            case COMPLETED:
                // Giao hàng thành công
                delivery.setActualArrivalTime(LocalDateTime.now());
                try { delivery.setConfirmationMethod(enums.ConfirmationMethod.GEOFENCE); } catch (Exception ignore) {}
                order.setStatus(OrderStatus.DELIVERED);

                // Cập nhật drone về trạng thái AVAILABLE
                if (delivery.getDroneId() != null) {
                    Long drKey = java.util.Objects.requireNonNull(delivery.getDroneId());
                    Drone drone = droneRepository.findById(drKey)
                            .orElse(null);
                    if (drone != null) {
                        // Deduct battery based on flight distance (store -> dropoff)
                        try {
                            // Resolve coordinates
                            Double storeLat = null, storeLng = null;
                            if (delivery.getPickupStoreId() != null) {
                                var addrs = storeAddressRepository.findByStore_Id(delivery.getPickupStoreId());
                                if (!addrs.isEmpty()) {
                                    var a = addrs.get(0);
                                    storeLat = a.getLatitude();
                                    storeLng = a.getLongitude();
                                }
                            }
                            Double customerLat = null, customerLng = null;
                            // Prefer delivery snapshot then order snapshot
                            String snap = delivery.getDropoffAddressSnapshot();
                            if (snap == null || snap.isBlank()) {
                                try { snap = order.getDeliveryAddressSnapshot(); } catch (Exception __) {}
                            }
                            if (snap != null && snap.trim().startsWith("{")) {
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    java.util.Map<String, Object> map = mapper.readValue(snap, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                                    Object latObj = map.get("lat");
                                    Object lngObj = map.get("lng");
                                    if (latObj == null) latObj = map.get("latitude");
                                    if (lngObj == null) lngObj = map.get("longitude");
                                    if (latObj instanceof Number) customerLat = ((Number) latObj).doubleValue();
                                    else if (latObj instanceof String) customerLat = Double.parseDouble(((String) latObj).trim());
                                    if (lngObj instanceof Number) customerLng = ((Number) lngObj).doubleValue();
                                    else if (lngObj instanceof String) customerLng = Double.parseDouble(((String) lngObj).trim());
                                } catch (Exception jex) {
                                    try {
                                        java.util.regex.Matcher mLat = java.util.regex.Pattern.compile("\"(lat|latitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                                        if (mLat.find()) customerLat = Double.parseDouble(mLat.group(2));
                                        java.util.regex.Matcher mLng = java.util.regex.Pattern.compile("\"(lng|longitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                                        if (mLng.find()) customerLng = Double.parseDouble(mLng.group(2));
                                    } catch (Exception rex) { /* ignore */ }
                                }
                            }
                            if (storeLat == null || storeLng == null) { storeLat = 10.762622; storeLng = 106.660172; }
                            if (customerLat == null || customerLng == null) { customerLat = 10.764200; customerLng = 106.662300; }

                            double distanceKm = droneService.calculateFlightDistance(storeLat, storeLng, customerLat, customerLng);
                            int usage = droneService.estimateBatteryUsageForDistance(distanceKm);
                            try { delivery.setDistanceKm(java.math.BigDecimal.valueOf(distanceKm)); } catch (Exception __) {}
                            Integer cur = drone.getCurrentBatteryPercent();
                            if (cur == null) cur = 100;
                            int newLvl = Math.max(0, cur - usage);
                            try { delivery.setBatteryUsedPercent(usage); } catch (Exception ignore) {}
                            drone.setCurrentBatteryPercent(newLvl);
                            // Update last known position to dropoff
                            try {
                                drone.setLastLatitude(java.math.BigDecimal.valueOf(customerLat));
                                drone.setLastLongitude(java.math.BigDecimal.valueOf(customerLng));
                            } catch (Exception __) {}
                            drone.setLastTelemetryAt(LocalDateTime.now());
                            try {
                                order.setDeliveredDroneId(drone.getId());
                                order.setDeliveredDroneCode(drone.getCode());
                            } catch (Exception __) {}
                        } catch (Exception ex) {
                            log.warn("Battery deduction failed for delivery {}: {}", delivery.getId(), ex.getMessage());
                        }
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                        try { orderRepository.save(order); } catch (Exception __) {}
                    }
                }
                break;

            case FAILED:
                // Giao hàng thất bại
                // Do not cancel the order on delivery failure; keep the paid/order state unchanged.
                // Refund or customer support actions should be handled explicitly elsewhere.

                // Cập nhật drone về trạng thái AVAILABLE
                if (delivery.getDroneId() != null) {
                    Long drKey = java.util.Objects.requireNonNull(delivery.getDroneId());
                    Drone drone = droneRepository.findById(drKey)
                            .orElse(null);
                    if (drone != null) {
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                    }
                }
                break;

            case RETURNED:
                // Drone quay về vì lý do nào đó
                // Do not cancel the order automatically when drone returned.
                // Keep current order status to allow retry/re-schedule or manual handling.

                if (delivery.getDroneId() != null) {
                    Long drKey = java.util.Objects.requireNonNull(delivery.getDroneId());
                    Drone drone = droneRepository.findById(drKey)
                            .orElse(null);
                    if (drone != null) {
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                    }
                }
                break;

                default:
                // For other statuses (QUEUED, ASSIGNED) no side-effects here
                break;
        }

            order = java.util.Objects.requireNonNull(orderRepository.save(order));
        delivery = deliveryRepository.save(delivery);

        log.info("Delivery {} status updated from {} to {}", deliveryId, oldStatus, newStatus);

        return toDeliveryResponse(delivery);
    }

    /**
     * Force-complete a delivery ignoring normal transition validation.
     * Dev-profile helper to keep backend state in sync with simulations.
     */
    @Transactional
    public DeliveryResponse forceComplete(Long deliveryId) {
        log.warn("[DEV] Force-completing delivery: {}", deliveryId);

        Long delKey = java.util.Objects.requireNonNull(deliveryId);
        Delivery delivery = deliveryRepository.findById(delKey)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        Long ordKey = java.util.Objects.requireNonNull(delivery.getOrderId());
        Order order = orderRepository.findById(ordKey)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        // Set to COMPLETED directly and apply same side-effects as normal path
        delivery.setCurrentStatus(DeliveryStatus.COMPLETED);
        delivery.setActualArrivalTime(LocalDateTime.now());
        try { delivery.setConfirmationMethod(enums.ConfirmationMethod.GEOFENCE); } catch (Exception ignore) {}
        delivery.setUpdatedAt(LocalDateTime.now());

        // Update order
        order.setStatus(OrderStatus.DELIVERED);

        // Battery deduction + telemetry update similar to updateDeliveryStatus(COMPLETED)
        if (delivery.getDroneId() != null) {
            Long drKey = java.util.Objects.requireNonNull(delivery.getDroneId());
            Drone drone = droneRepository.findById(drKey).orElse(null);
            if (drone != null) {
                try {
                    // Resolve coordinates
                    Double storeLat = null, storeLng = null;
                    if (delivery.getPickupStoreId() != null) {
                        var addrs = storeAddressRepository.findByStore_Id(delivery.getPickupStoreId());
                        if (!addrs.isEmpty()) {
                            var a = addrs.get(0);
                            storeLat = a.getLatitude();
                            storeLng = a.getLongitude();
                        }
                    }
                    Double customerLat = null, customerLng = null;
                    String snap = delivery.getDropoffAddressSnapshot();
                    if (snap == null || snap.isBlank()) {
                        try { snap = order.getDeliveryAddressSnapshot(); } catch (Exception __) {}
                    }
                    if (snap != null && snap.trim().startsWith("{")) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            java.util.Map<String, Object> map = mapper.readValue(snap, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                            Object latObj = map.get("lat");
                            Object lngObj = map.get("lng");
                            if (latObj == null) latObj = map.get("latitude");
                            if (lngObj == null) lngObj = map.get("longitude");
                            if (latObj instanceof Number) customerLat = ((Number) latObj).doubleValue();
                            else if (latObj instanceof String) customerLat = Double.parseDouble(((String) latObj).trim());
                            if (lngObj instanceof Number) customerLng = ((Number) lngObj).doubleValue();
                            else if (lngObj instanceof String) customerLng = Double.parseDouble(((String) lngObj).trim());
                        } catch (Exception jex) {
                            try {
                                java.util.regex.Matcher mLat = java.util.regex.Pattern.compile("\"(lat|latitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                                if (mLat.find()) customerLat = Double.parseDouble(mLat.group(2));
                                java.util.regex.Matcher mLng = java.util.regex.Pattern.compile("\"(lng|longitude)\"\\s*:\\s*([-0-9.]+)").matcher(snap);
                                if (mLng.find()) customerLng = Double.parseDouble(mLng.group(2));
                            } catch (Exception rex) { /* ignore */ }
                        }
                    }
                    if (storeLat == null || storeLng == null) { storeLat = 10.762622; storeLng = 106.660172; }
                    if (customerLat == null || customerLng == null) { customerLat = 10.764200; customerLng = 106.662300; }

                    double distanceKm = droneService.calculateFlightDistance(storeLat, storeLng, customerLat, customerLng);
                    int usage = droneService.estimateBatteryUsageForDistance(distanceKm);
                    try { delivery.setDistanceKm(java.math.BigDecimal.valueOf(distanceKm)); } catch (Exception __) {}
                    Integer cur = drone.getCurrentBatteryPercent();
                    if (cur == null) cur = 100;
                    int newLvl = Math.max(0, cur - usage);
                    try { delivery.setBatteryUsedPercent(usage); } catch (Exception ignore2) {}
                    drone.setCurrentBatteryPercent(newLvl);
                    // Update last known position to dropoff
                    try {
                        drone.setLastLatitude(java.math.BigDecimal.valueOf(customerLat));
                        drone.setLastLongitude(java.math.BigDecimal.valueOf(customerLng));
                    } catch (Exception __) {}
                    drone.setLastTelemetryAt(LocalDateTime.now());
                    // Stamp order with delivered drone
                    try {
                        order.setDeliveredDroneId(drone.getId());
                        order.setDeliveredDroneCode(drone.getCode());
                    } catch (Exception __) {}
                } catch (Exception ex) {
                    log.warn("[DEV] Battery deduction failed (forceComplete) for delivery {}: {}", delivery.getId(), ex.getMessage());
                }
                drone.setStatus(DroneStatus.AVAILABLE);
                droneRepository.save(drone);
            }
        }

        try { orderRepository.save(order); } catch (Exception __) {}
        delivery = deliveryRepository.save(delivery);

        return toDeliveryResponse(delivery);
    }

    /**
     * Lấy thông tin delivery theo order ID
     */
    public DeliveryResponse getDeliveryByOrderId(Long orderId) {
        Long ordKey = java.util.Objects.requireNonNull(orderId);
        Delivery delivery = deliveryRepository.findByOrderId(ordKey)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        return toDeliveryResponse(delivery);
    }

    /**
     * Lấy thông tin delivery theo ID
     */
    public DeliveryResponse getDeliveryById(Long deliveryId) {
        Long delKey2 = java.util.Objects.requireNonNull(deliveryId);
        Delivery delivery = deliveryRepository.findById(delKey2)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        return toDeliveryResponse(delivery);
    }

    /**
     * Lấy danh sách delivery đang chờ (QUEUED)
     */
    public List<DeliveryResponse> getQueuedDeliveries() {
        return deliveryRepository.findQueuedDeliveries().stream()
                .map(this::toDeliveryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách delivery theo drone
     */
    public List<DeliveryResponse> getDeliveriesByDrone(Long droneId) {
        return deliveryRepository.findByDroneId(droneId).stream()
                .map(this::toDeliveryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Validate status transition
     */
    private void validateStatusTransition(DeliveryStatus from, DeliveryStatus to) {
        // Define valid transitions
        boolean isValid = switch (from) {
            case QUEUED -> to == DeliveryStatus.ASSIGNED || to == DeliveryStatus.FAILED;
            case ASSIGNED -> to == DeliveryStatus.LAUNCHED || to == DeliveryStatus.FAILED;
            case LAUNCHED -> to == DeliveryStatus.ARRIVING || to == DeliveryStatus.FAILED || to == DeliveryStatus.RETURNED;
            case ARRIVING -> to == DeliveryStatus.COMPLETED || to == DeliveryStatus.FAILED || to == DeliveryStatus.RETURNED;
            case COMPLETED, FAILED, RETURNED -> false; // Terminal states
        };

        if (!isValid) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /**
     * Convert Delivery entity to DeliveryResponse
     */
    private DeliveryResponse toDeliveryResponse(Delivery delivery) {
        DeliveryResponse.DeliveryResponseBuilder builder = DeliveryResponse.builder()
                .id(delivery.getId())
                .orderId(delivery.getOrderId())
                .droneId(delivery.getDroneId())
                .currentStatus(delivery.getCurrentStatus())
                .pickupStoreId(delivery.getPickupStoreId())
                .dropoffAddressSnapshot(delivery.getDropoffAddressSnapshot())
                .actualDepartureTime(delivery.getActualDepartureTime())
                .actualArrivalTime(delivery.getActualArrivalTime())
                .confirmationMethod(delivery.getConfirmationMethod())
                .createdAt(delivery.getCreatedAt())
            .updatedAt(delivery.getUpdatedAt())
            .batteryUsedPercent(delivery.getBatteryUsedPercent());

        // Actual distance
        if (delivery.getDistanceKm() != null) {
            builder.distanceKm(delivery.getDistanceKm().doubleValue());
        }

        // Actual flight time seconds
        if (delivery.getActualDepartureTime() != null && delivery.getActualArrivalTime() != null) {
            long seconds = java.time.Duration.between(delivery.getActualDepartureTime(), delivery.getActualArrivalTime()).getSeconds();
            if (seconds < 0) seconds = 0;
            builder.actualFlightTimeSeconds((int)Math.min(Integer.MAX_VALUE, seconds));
        }

        // Add order code if order exists
        if (delivery.getOrder() != null) {
            builder.orderCode(delivery.getOrder().getOrderCode());
        }

        // Add drone code if drone exists
        if (delivery.getDrone() != null) {
            builder.droneCode(delivery.getDrone().getCode());
        }

        // Add store name if store exists
        if (delivery.getPickupStore() != null) {
            builder.pickupStoreName(delivery.getPickupStore().getName());
        }

        // Calculate estimated flight time if needed
        // TODO: Implement proper calculation based on distance

        return builder.build();
    }
}

