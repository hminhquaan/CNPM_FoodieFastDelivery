package controller.delivery;

import dto.request.delivery.UpdateDeliveryStatusRequest;
import dto.request.DroneRegisterRequest;
import dto.response.API.APIResponse;
import dto.response.delivery.DeliveryResponse;
import entity.Delivery;
import entity.Order;
import enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repository.delivery.DeliveryRepository;
import repository.order.OrderRepository;
import service.delivery.DeliveryService;
import service.drone.DroneService;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/deliveries/demo")
@RequiredArgsConstructor
@Slf4j
public class DeliveryDemoController {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DeliveryService deliveryService;
    private final DroneService droneService;

    @Value("${app.demo.enable:false}")
    private boolean demoEnabled;

    /**
     * Dev/demo only: Ensure a delivery exists for an order, auto-assign a drone, and optionally auto-progress.
     * This bypasses the paid check, so keep it disabled in prod.
     */
    @PostMapping("/kickoff")
    public ResponseEntity<APIResponse<DeliveryResponse>> kickoff(
            @RequestParam("orderId") @NotNull Long orderId,
            @RequestParam(value = "autoAssign", defaultValue = "true") boolean autoAssign,
            @RequestParam(value = "autoProgress", defaultValue = "true") boolean autoProgress
    ) {
        if (!demoEnabled) {
            return ResponseEntity.status(403).body(APIResponse.<DeliveryResponse>builder()
                    .code(403)
                    .message("Demo delivery kickoff disabled. Set app.demo.enable=true in dev config.")
                    .build());
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(APIResponse.<DeliveryResponse>builder()
                    .code(404)
                    .message("Order not found")
                    .build());
        }
        Order order = orderOpt.get();

        // Upsert delivery bypassing paid check (demo only)
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseGet(() -> {
                    Delivery d = Delivery.builder()
                            .orderId(orderId)
                            .pickupStoreId(order.getStoreId())
                            .dropoffAddressSnapshot(order.getDeliveryAddressSnapshot())
                            .currentStatus(DeliveryStatus.QUEUED)
                            .build();
                    return deliveryRepository.save(d);
                });

        DeliveryResponse response = deliveryService.getDeliveryById(delivery.getId());

        // Auto-assign a drone (ensure demo drone exists if none available)
        if (autoAssign && response.getCurrentStatus() == DeliveryStatus.QUEUED) {
            try {
                response = deliveryService.autoAssignDrone(delivery.getId());
            } catch (Exception ex) {
                log.warn("Auto-assign drone failed on first attempt: {}", ex.getMessage());
            }
            // If still queued, provision a demo drone near default location and retry once
            if (response.getCurrentStatus() == DeliveryStatus.QUEUED) {
                try {
                    String code = "DEMO-DRONE-" + (System.currentTimeMillis() % 100000);
                    DroneRegisterRequest req = DroneRegisterRequest.builder()
                            .code(code)
                            .model("Demo-Unit")
                            .maxPayloadGram(5000)
                            .latitude(new java.math.BigDecimal("10.762622"))
                            .longitude(new java.math.BigDecimal("106.660172"))
                            .build();
                    droneService.registerDrone(req);
                    log.info("Provisioned demo drone {} and retrying auto-assign...", code);
                    response = deliveryService.autoAssignDrone(delivery.getId());
                } catch (Exception ex2) {
                    log.warn("Provisioning demo drone or second auto-assign failed: {}", ex2.getMessage());
                }
            }
        }

        // Auto-progress delivery only AFTER assignment succeeded (ASSIGNED -> LAUNCHED -> ARRIVING -> COMPLETED)
        if (autoProgress && response.getCurrentStatus() == DeliveryStatus.ASSIGNED) {
            final long dId = response.getId();
            new Thread(() -> {
                try {
                    // LAUNCHED (valid from ASSIGNED)
                    deliveryService.updateDeliveryStatus(dId, UpdateDeliveryStatusRequest.builder()
                            .status(DeliveryStatus.LAUNCHED)
                            .build());
                    Thread.sleep(1800);
                    // ARRIVING
                    deliveryService.updateDeliveryStatus(dId, UpdateDeliveryStatusRequest.builder()
                            .status(DeliveryStatus.ARRIVING)
                            .build());
                    Thread.sleep(1800);
                    // COMPLETED
                    deliveryService.updateDeliveryStatus(dId, UpdateDeliveryStatusRequest.builder()
                            .status(DeliveryStatus.COMPLETED)
                            .build());
                } catch (Exception e) {
                    log.warn("Auto-progress failed: {}", e.getMessage());
                }
            }, "delivery-demo-progress").start();
        }

        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .message("Delivery kickoff completed")
                .result(response)
                .build());
    }
}
