package controller.drone;

import service.drone.DroneService;
import dto.request.DroneLocationUpdateRequest;
import dto.request.DroneRegisterRequest;
import dto.request.DroneStatusUpdateRequest;
import dto.response.API.APIResponse;
import dto.response.DroneResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/drones")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneController {

    DroneService droneService;

    /**
     * Register a new drone (for phone simulator)
     */
    @PostMapping("/register")
    public APIResponse<DroneResponse> registerDrone(@Valid @RequestBody DroneRegisterRequest request) {
        return APIResponse.<DroneResponse>builder()
                .message("Drone registered successfully")
                .result(droneService.registerDrone(request))
                .build();
    }

    /**
     * Get all drones
     */
    @GetMapping
    public APIResponse<List<DroneResponse>> getAllDrones() {
        return APIResponse.<List<DroneResponse>>builder()
                .result(droneService.getAllDrones())
                .build();
    }

    /**
     * Get drone by code (for phone simulator login)
     */
    @GetMapping("/{code}")
    public APIResponse<DroneResponse> getDroneByCode(@PathVariable String code) {
        return APIResponse.<DroneResponse>builder()
                .result(droneService.getDroneByCode(code))
                .build();
    }

    /**
     * Update drone location (GPS from phone)
     */
    @PostMapping("/{code}/location")
    public APIResponse<DroneResponse> updateLocation(
            @PathVariable String code,
            @RequestBody DroneLocationUpdateRequest request) {
        return APIResponse.<DroneResponse>builder()
                .message("Location updated")
                .result(droneService.updateLocation(code, request))
                .build();
    }

    /**
     * Update drone status
     */
    @PostMapping("/{code}/status")
    public APIResponse<DroneResponse> updateStatus(
            @PathVariable String code,
            @RequestBody DroneStatusUpdateRequest request) {
        return APIResponse.<DroneResponse>builder()
                .message("Status updated")
                .result(droneService.updateStatus(code, request))
                .build();
    }

    /**
     * Get current delivery for drone
     */
    @GetMapping("/{code}/current-delivery")
    public APIResponse<Object> getCurrentDelivery(@PathVariable String code) {
        return APIResponse.builder()
                .result(droneService.getCurrentDelivery(code))
                .build();
    }

    /**
     * Find available drone for delivery
     * Query params: weightGram, fromLat, fromLng, toLat, toLng
     */
    @GetMapping("/find-available")
    public APIResponse<DroneResponse> findAvailableDrone(
            @RequestParam Integer weightGram,
            @RequestParam Double fromLat,
            @RequestParam Double fromLng,
            @RequestParam Double toLat,
            @RequestParam Double toLng) {
        return APIResponse.<DroneResponse>builder()
                .code(1000)
                .message("Available drone found")
                .result(droneService.findAvailableDroneForDelivery(weightGram, fromLat, fromLng, toLat, toLng))
                .build();
    }

    /**
     * Monitor drone battery
     */
    @PostMapping("/{code}/monitor-battery")
    public APIResponse<DroneResponse> monitorBattery(@PathVariable String code) {
        return APIResponse.<DroneResponse>builder()
                .code(1000)
                .message("Battery monitored")
                .result(droneService.monitorBattery(code))
                .build();
    }

    /**
     * Enable safety mode
     */
    @PostMapping("/{code}/safety-mode")
    public APIResponse<DroneResponse> enableSafetyMode(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "Manual safety mode") String reason) {
        return APIResponse.<DroneResponse>builder()
                .code(1000)
                .message("Safety mode enabled")
                .result(droneService.enableSafetyMode(code, reason))
                .build();
    }

    /**
     * Get drones within radius
     */
    @GetMapping("/nearby")
    public APIResponse<List<DroneResponse>> getDronesNearby(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radiusKm) {
        return APIResponse.<List<DroneResponse>>builder()
                .code(1000)
                .result(droneService.getDronesWithinRadius(lat, lng, radiusKm))
                .build();
    }

    /**
     * Check drone health
     */
    @GetMapping("/{code}/health")
    public APIResponse<Object> checkDroneHealth(@PathVariable String code) {
        return APIResponse.builder()
                .code(1000)
                .result(droneService.checkDroneHealth(code))
                .build();
    }

    /**
     * Calculate flight distance between two points
     */
    @GetMapping("/calculate-distance")
    public APIResponse<Object> calculateDistance(
            @RequestParam Double fromLat,
            @RequestParam Double fromLng,
            @RequestParam Double toLat,
            @RequestParam Double toLng) {
        double distance = droneService.calculateFlightDistance(fromLat, fromLng, toLat, toLng);
        int estimatedTime = droneService.estimateFlightTime(distance);

        return APIResponse.builder()
                .code(1000)
                .result(new java.util.HashMap<String, Object>() {{
                    put("distanceKm", Math.round(distance * 100.0) / 100.0);
                    put("estimatedTimeMinutes", estimatedTime);
                }})
                .build();
    }
}
