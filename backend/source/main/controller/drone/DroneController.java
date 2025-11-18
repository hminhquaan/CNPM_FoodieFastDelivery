package controller.drone;

import service.drone.DroneService;
import service.drone.DroneStationService;
import dto.request.DroneLocationUpdateRequest;
import dto.request.DroneRegisterRequest;
import dto.request.DroneStatusUpdateRequest;
import dto.response.API.APIResponse;
import dto.response.DroneResponse;
import dto.response.DroneStationResponse;
import dto.request.DroneStationUpdateRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/drones", "/drones"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneController {

    DroneService droneService;
        DroneStationService droneStationService;

    /**
     * Register a new drone (for phone simulator)
     */
                @PostMapping({"", "/register"})
                public ResponseEntity<APIResponse<DroneResponse>> registerDrone(@Valid @RequestBody DroneRegisterRequest request) {
                DroneResponse created = droneService.registerDrone(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<DroneResponse>builder()
                                .code(201)
                                .message("Drone registered successfully")
                                .result(created)
                                .build());
        }

    /**
     * Get all drones
     */
                @GetMapping("")
        public ResponseEntity<APIResponse<List<DroneResponse>>> getAllDrones() {
                List<DroneResponse> list = droneService.getAllDrones();
                return ResponseEntity.ok(APIResponse.<List<DroneResponse>>builder()
                                .code(200)
                                .message("Drones retrieved")
                                .result(list)
                                .build());
        }

    /**
     * Get drone by code (for phone simulator login)
     */
        @GetMapping("/{code}")
        public ResponseEntity<APIResponse<DroneResponse>> getDroneByCode(@PathVariable String code) {
                DroneResponse drone = droneService.getDroneByCode(code);
                return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                                .code(200)
                                .message("Drone retrieved")
                                .result(drone)
                                .build());
        }

        /**
         * Update drone location (GPS from phone)
         * Support both POST and PUT on legacy path /drones/{code}/location
         */
        @RequestMapping(value = "/{code}/location", method = {RequestMethod.PUT, RequestMethod.POST})
        public ResponseEntity<APIResponse<DroneResponse>> updateLocation(
                        @PathVariable String code,
                        @RequestBody @Valid DroneLocationUpdateRequest request) {
                // Basic guards to avoid 500 when legacy clients send incomplete payload
                if (request.getLatitude() == null || request.getLongitude() == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(APIResponse.<DroneResponse>builder()
                                        .code(400)
                                        .message("latitude and longitude are required")
                                        .result(null)
                                        .build());
                }
                DroneResponse updated = droneService.updateLocation(code, request);
                return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                                .code(200)
                                .message("Location updated")
                                .result(updated)
                                .build());
        }

        /**
         * Update drone status
         * Support both POST and PUT on legacy path /drones/{code}/status
         */
        @RequestMapping(value = "/{code}/status", method = {RequestMethod.PUT, RequestMethod.POST})
        public ResponseEntity<APIResponse<DroneResponse>> updateStatus(
                        @PathVariable String code,
                        @RequestBody @Valid DroneStatusUpdateRequest request) {
                if (request.getStatus() == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(APIResponse.<DroneResponse>builder()
                                        .code(400)
                                        .message("status is required")
                                        .result(null)
                                        .build());
                }
                DroneResponse updated = droneService.updateStatus(code, request);
                return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                                .code(200)
                                .message("Status updated")
                                .result(updated)
                                .build());
        }

    /**
     * Get current delivery for drone
     */
        @GetMapping("/{code}/current-delivery")
        public ResponseEntity<APIResponse<Object>> getCurrentDelivery(@PathVariable String code) {
                Object current = droneService.getCurrentDelivery(code);
                return ResponseEntity.ok(APIResponse.builder()
                                .code(200)
                                .message("Current delivery fetched")
                                .result(current)
                                .build());
        }

    /**
     * Find available drone for delivery
     * Query params: weightGram, fromLat, fromLng, toLat, toLng
     */
    @GetMapping("/find-available")
    public ResponseEntity<APIResponse<DroneResponse>> findAvailableDrone(
            @RequestParam Integer weightGram,
            @RequestParam Double fromLat,
            @RequestParam Double fromLng,
            @RequestParam Double toLat,
            @RequestParam Double toLng) {
        DroneResponse found = droneService.findAvailableDroneForDelivery(weightGram, fromLat, fromLng, toLat, toLng);
        return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                .code(200)
                .message("Available drone found")
                .result(found)
                .build());
    }

    /**
     * Monitor drone battery
     */
        @PostMapping("/{code}/monitor-battery")
        public ResponseEntity<APIResponse<DroneResponse>> monitorBattery(@PathVariable String code) {
                DroneResponse resp = droneService.monitorBattery(code);
                return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                                .code(200)
                                .message("Battery monitored")
                                .result(resp)
                                .build());
        }

    /**
     * Enable safety mode
     */
    @PostMapping("/{code}/safety-mode")
    public ResponseEntity<APIResponse<DroneResponse>> enableSafetyMode(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "Manual safety mode") String reason) {
        DroneResponse resp = droneService.enableSafetyMode(code, reason);
        return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                .code(200)
                .message("Safety mode enabled")
                .result(resp)
                .build());
    }

    /**
     * Get drones within radius
     */
    @GetMapping("/nearby")
    public ResponseEntity<APIResponse<List<DroneResponse>>> getDronesNearby(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radiusKm) {
        List<DroneResponse> list = droneService.getDronesWithinRadius(lat, lng, radiusKm);
        return ResponseEntity.ok(APIResponse.<List<DroneResponse>>builder()
                .code(200)
                .message("Nearby drones retrieved")
                .result(list)
                .build());
    }

        /**
         * Return drone to station and start charging.
         */
        @PostMapping("/{code}/return-to-station")
        public ResponseEntity<APIResponse<DroneResponse>> returnToStation(@PathVariable String code) {
                DroneResponse resp = droneService.returnToStationAndCharge(code);
                return ResponseEntity.ok(APIResponse.<DroneResponse>builder()
                                .code(200)
                                .message("Drone returning to station and charging")
                                .result(resp)
                                .build());
        }

    /**
     * Check drone health
     */
        @GetMapping("/{code}/health")
        public ResponseEntity<APIResponse<Object>> checkDroneHealth(@PathVariable String code) {
                Object health = droneService.checkDroneHealth(code);
                return ResponseEntity.ok(APIResponse.builder()
                                .code(200)
                                .message("Drone health ok")
                                .result(health)
                                .build());
        }

    /**
     * Calculate flight distance between two points
     */
        @GetMapping("/calculate-distance")
        public ResponseEntity<APIResponse<Object>> calculateDistance(
                        @RequestParam Double fromLat,
                        @RequestParam Double fromLng,
                        @RequestParam Double toLat,
                        @RequestParam Double toLng) {
                double distance = droneService.calculateFlightDistance(fromLat, fromLng, toLat, toLng);
                int estimatedTime = droneService.estimateFlightTime(distance);
                var payload = new java.util.HashMap<String, Object>();
                payload.put("distanceKm", Math.round(distance * 100.0) / 100.0);
                payload.put("estimatedTimeMinutes", estimatedTime);
                return ResponseEntity.ok(APIResponse.builder()
                                .code(200)
                                .message("Distance calculated")
                                .result(payload)
                                .build());
        }

        /**
         * Single shared drone station (GET/PUT)
         */
        @GetMapping("/station")
        public ResponseEntity<APIResponse<DroneStationResponse>> getStation() {
                DroneStationResponse station = droneStationService.getOrCreateDefault();
                return ResponseEntity.ok(APIResponse.<DroneStationResponse>builder()
                                .code(200)
                                .message("Station retrieved")
                                .result(station)
                                .build());
        }

        @PutMapping("/station")
        public ResponseEntity<APIResponse<DroneStationResponse>> updateStation(@RequestBody DroneStationUpdateRequest req) {
                DroneStationResponse station = droneStationService.updateStation(req.getName(), req.getLatitude(), req.getLongitude(), req.getRadiusKm());
                return ResponseEntity.ok(APIResponse.<DroneStationResponse>builder()
                                .code(200)
                                .message("Station updated")
                                .result(station)
                                .build());
        }
}
