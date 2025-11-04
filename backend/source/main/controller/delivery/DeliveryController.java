package controller.delivery;

import service.delivery.DeliveryService;
import dto.request.delivery.AssignDroneRequest;
import dto.request.delivery.CreateDeliveryRequest;
import dto.request.delivery.UpdateDeliveryStatusRequest;
import dto.response.API.APIResponse;
import dto.response.delivery.DeliveryResponse;
import enums.DeliveryStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * Tạo delivery mới (tự động sau khi order được thanh toán)
     * POST /api/v1/deliveries
     */
    @PostMapping
    public ResponseEntity<APIResponse<DeliveryResponse>> createDelivery(
            @Valid @RequestBody CreateDeliveryRequest request) {
        log.info("Creating delivery for order: {}", request.getOrderId());

        DeliveryResponse response = deliveryService.createDelivery(request);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery created successfully")
                .result(response)
                .build());
    }

    /**
     * Gán drone cho delivery (manual)
     * POST /api/v1/deliveries/{deliveryId}/assign-drone
     */
    @PostMapping("/{deliveryId}/assign-drone")
    public ResponseEntity<APIResponse<DeliveryResponse>> assignDrone(
            @PathVariable Long deliveryId,
            @RequestParam Long droneId) {
        log.info("Assigning drone {} to delivery {}", droneId, deliveryId);

        DeliveryResponse response = deliveryService.assignDrone(deliveryId, droneId);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Drone assigned successfully")
                .result(response)
                .build());
    }

    /**
     * Tự động gán drone cho delivery
     * POST /api/v1/deliveries/{deliveryId}/auto-assign-drone
     */
    @PostMapping("/{deliveryId}/auto-assign-drone")
    public ResponseEntity<APIResponse<DeliveryResponse>> autoAssignDrone(
            @PathVariable Long deliveryId) {
        log.info("Auto-assigning drone for delivery {}", deliveryId);

        DeliveryResponse response = deliveryService.autoAssignDrone(deliveryId);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Drone auto-assigned successfully")
                .result(response)
                .build());
    }

    /**
     * Cập nhật trạng thái delivery
     * PUT /api/v1/deliveries/{deliveryId}/status
     */
    @PutMapping("/{deliveryId}/status")
    public ResponseEntity<APIResponse<DeliveryResponse>> updateDeliveryStatus(
            @PathVariable Long deliveryId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        log.info("Updating delivery {} status to {}", deliveryId, request.getStatus());

        DeliveryResponse response = deliveryService.updateDeliveryStatus(deliveryId, request);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery status updated successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy thông tin delivery theo order ID
     * GET /api/v1/deliveries/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<APIResponse<DeliveryResponse>> getDeliveryByOrderId(
            @PathVariable Long orderId) {
        log.info("Getting delivery for order: {}", orderId);

        DeliveryResponse response = deliveryService.getDeliveryByOrderId(orderId);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy thông tin delivery theo ID
     * GET /api/v1/deliveries/{deliveryId}
     */
    @GetMapping("/{deliveryId}")
    public ResponseEntity<APIResponse<DeliveryResponse>> getDeliveryById(
            @PathVariable Long deliveryId) {
        log.info("Getting delivery: {}", deliveryId);

        DeliveryResponse response = deliveryService.getDeliveryById(deliveryId);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy danh sách delivery đang chờ xử lý
     * GET /api/v1/deliveries/queued
     */
    @GetMapping("/queued")
    public ResponseEntity<APIResponse<List<DeliveryResponse>>> getQueuedDeliveries() {
        log.info("Getting queued deliveries");

        List<DeliveryResponse> responses = deliveryService.getQueuedDeliveries();

        return ResponseEntity.ok(APIResponse.<List<DeliveryResponse>>builder()
                .code(200)
                .message("Queued deliveries retrieved successfully")
                .result(responses)
                .build());
    }

    /**
     * Lấy danh sách delivery của một drone
     * GET /api/v1/deliveries/drone/{droneId}
     */
    @GetMapping("/drone/{droneId}")
    public ResponseEntity<APIResponse<List<DeliveryResponse>>> getDeliveriesByDrone(
            @PathVariable Long droneId) {
        log.info("Getting deliveries for drone: {}", droneId);

        List<DeliveryResponse> responses = deliveryService.getDeliveriesByDrone(droneId);

        return ResponseEntity.ok(APIResponse.<List<DeliveryResponse>>builder()
                .code(200)
                .message("Deliveries retrieved successfully")
                .result(responses)
                .build());
    }

    /**
     * Launch delivery (Drone cất cánh)
     * POST /api/v1/deliveries/{deliveryId}/launch
     */
    @PostMapping("/{deliveryId}/launch")
    public ResponseEntity<APIResponse<DeliveryResponse>> launchDelivery(
            @PathVariable Long deliveryId) {
        log.info("Launching delivery: {}", deliveryId);

        UpdateDeliveryStatusRequest request = UpdateDeliveryStatusRequest.builder()
                .status(DeliveryStatus.LAUNCHED)
                .build();

        DeliveryResponse response = deliveryService.updateDeliveryStatus(deliveryId, request);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery launched successfully")
                .result(response)
                .build());
    }

    /**
     * Mark delivery as arriving (Drone đang đến)
     * POST /api/v1/deliveries/{deliveryId}/arriving
     */
    @PostMapping("/{deliveryId}/arriving")
    public ResponseEntity<APIResponse<DeliveryResponse>> markAsArriving(
            @PathVariable Long deliveryId) {
        log.info("Marking delivery {} as arriving", deliveryId);

        UpdateDeliveryStatusRequest request = UpdateDeliveryStatusRequest.builder()
                .status(DeliveryStatus.ARRIVING)
                .build();

        DeliveryResponse response = deliveryService.updateDeliveryStatus(deliveryId, request);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery marked as arriving")
                .result(response)
                .build());
    }

    /**
     * Complete delivery (Giao hàng thành công)
     * POST /api/v1/deliveries/{deliveryId}/complete
     */
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<APIResponse<DeliveryResponse>> completeDelivery(
            @PathVariable Long deliveryId) {
        log.info("Completing delivery: {}", deliveryId);

        UpdateDeliveryStatusRequest request = UpdateDeliveryStatusRequest.builder()
                .status(DeliveryStatus.COMPLETED)
                .build();

        DeliveryResponse response = deliveryService.updateDeliveryStatus(deliveryId, request);

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery completed successfully")
                .result(response)
                .build());
    }
}

