package controller.delivery;

import dto.response.API.APIResponse;
import dto.response.FlightPlanPointResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import service.delivery.FlightPlanService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FlightPlanController {

    FlightPlanService flightPlanService;

        @GetMapping("/{deliveryId}/flight-plan")
        public APIResponse<List<FlightPlanPointResponse>> getFlightPlan(@PathVariable Long deliveryId) {
            List<FlightPlanPointResponse> points = flightPlanService.ensurePlanForDelivery(deliveryId);
        return APIResponse.<List<FlightPlanPointResponse>>builder()
                .code(1000)
                .message("Flight plan points fetched")
                .result(points)
                .build();
    }
}
