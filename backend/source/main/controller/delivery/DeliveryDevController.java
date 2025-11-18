package controller.delivery;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dto.response.API.APIResponse;
import dto.response.delivery.DeliveryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import service.delivery.DeliveryService;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/deliveries/dev")
@RequiredArgsConstructor
@Slf4j
public class DeliveryDevController {

    private final DeliveryService deliveryService;

    /**
     * Force-complete a delivery regardless of current status (DEV only).
     * POST /api/v1/deliveries/dev/{deliveryId}/force-complete
     */
    @PostMapping("/{deliveryId}/force-complete")
    public ResponseEntity<APIResponse<DeliveryResponse>> forceComplete(@PathVariable Long deliveryId) {
        log.warn("[DEV] Force-complete endpoint invoked for delivery {}", deliveryId);
        DeliveryResponse response = deliveryService.forceComplete(deliveryId);
        return ResponseEntity.ok(APIResponse.<DeliveryResponse>builder()
                .code(200)
                .result(response)
                .build());
    }
}
