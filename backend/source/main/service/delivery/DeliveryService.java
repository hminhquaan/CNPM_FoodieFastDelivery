package service.delivery;

import repository.delivery.DeliveryRepository;
import repository.drone.DroneRepository;
import service.drone.DroneService;
import repository.order.OrderRepository;
import repository.store.StoreRepository;
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

    /**
     * Tạo delivery mới khi order được thanh toán thành công
     */
    @Transactional
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {
        log.info("Creating delivery for order: {}", request.getOrderId());

        // Kiểm tra order tồn tại và đã thanh toán
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if (order.getStatus() != OrderStatus.PAID) {
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

        // Parse delivery address để lấy tọa độ
        // TODO: Implement proper address parsing
        // Giả định có lat/lng trong deliveryAddressSnapshot
        Double storeLat = 10.762622; // TODO: Get from store
        Double storeLng = 106.660172;
        Double customerLat = 10.772622; // TODO: Parse from delivery address
        Double customerLng = 106.670172;

        // Tìm drone phù hợp (giả định trọng lượng 500g)
        var droneResponse = droneService.findAvailableDroneForDelivery(
                500, // TODO: Calculate actual weight from order items
                storeLat, storeLng,
                customerLat, customerLng
        );

        // Gán drone tìm được
        return assignDrone(deliveryId, droneResponse.getId());
    }

    /**
     * Cập nhật trạng thái delivery
     */
    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, UpdateDeliveryStatusRequest request) {
        log.info("Updating delivery {} status to {}", deliveryId, request.getStatus());

        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        Order order = orderRepository.findById(delivery.getOrderId())
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
                order.setStatus(OrderStatus.DELIVERED);

                // Cập nhật drone về trạng thái AVAILABLE
                if (delivery.getDroneId() != null) {
                    Drone drone = droneRepository.findById(delivery.getDroneId())
                            .orElse(null);
                    if (drone != null) {
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                    }
                }
                break;

            case FAILED:
                // Giao hàng thất bại
                order.setStatus(OrderStatus.CANCELLED);

                // Cập nhật drone về trạng thái AVAILABLE
                if (delivery.getDroneId() != null) {
                    Drone drone = droneRepository.findById(delivery.getDroneId())
                            .orElse(null);
                    if (drone != null) {
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                    }
                }
                break;

            case RETURNED:
                // Drone quay về vì lý do nào đó
                order.setStatus(OrderStatus.CANCELLED);

                if (delivery.getDroneId() != null) {
                    Drone drone = droneRepository.findById(delivery.getDroneId())
                            .orElse(null);
                    if (drone != null) {
                        drone.setStatus(DroneStatus.AVAILABLE);
                        droneRepository.save(drone);
                    }
                }
                break;
        }

        orderRepository.save(order);
        delivery = deliveryRepository.save(delivery);

        log.info("Delivery {} status updated from {} to {}", deliveryId, oldStatus, newStatus);

        return toDeliveryResponse(delivery);
    }

    /**
     * Lấy thông tin delivery theo order ID
     */
    public DeliveryResponse getDeliveryByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        return toDeliveryResponse(delivery);
    }

    /**
     * Lấy thông tin delivery theo ID
     */
    public DeliveryResponse getDeliveryById(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
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
            case QUEUED -> to == DeliveryStatus.ASSIGNED;
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
                .updatedAt(delivery.getUpdatedAt());

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

