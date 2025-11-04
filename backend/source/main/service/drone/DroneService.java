package service.drone;

import repository.drone.DroneRepository;
import dto.request.DroneLocationUpdateRequest;
import dto.request.DroneRegisterRequest;
import dto.request.DroneStatusUpdateRequest;
import dto.response.DroneResponse;
import entity.Delivery;
import entity.Drone;
import enums.DeliveryStatus;
import enums.DroneStatus;
import exception.AppException;
import exception.ErrorCode;
import mapper.DroneMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneService {

    DroneRepository droneRepository;
    DroneMapper droneMapper;

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public DroneResponse registerDrone(DroneRegisterRequest request) {
        // Check if drone code already exists
        if (droneRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.DRONE_ALREADY_EXISTS);
        }

        Drone drone = Drone.builder()
                .code(request.getCode())
                .model(request.getModel())
                .maxPayloadGram(request.getMaxPayloadGram())
                .status(DroneStatus.AVAILABLE)
                .currentBatteryPercent(100)
                .lastLatitude(request.getLatitude())
                .lastLongitude(request.getLongitude())
                .lastTelemetryAt(LocalDateTime.now())
                .build();

        drone = droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    public List<DroneResponse> getAllDrones() {
        return droneRepository.findAll().stream()
                .map(droneMapper::toDroneResponse)
                .collect(Collectors.toList());
    }

    public DroneResponse getDroneByCode(String code) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));
        return droneMapper.toDroneResponse(drone);
    }

    @Transactional
    public DroneResponse updateLocation(String code, DroneLocationUpdateRequest request) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        drone.setLastLatitude(request.getLatitude());
        drone.setLastLongitude(request.getLongitude());
        drone.setLastTelemetryAt(LocalDateTime.now());

        if (request.getBatteryPercent() != null) {
            drone.setCurrentBatteryPercent(request.getBatteryPercent());
        }

        drone = droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    @Transactional
    public DroneResponse updateStatus(String code, DroneStatusUpdateRequest request) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        drone.setStatus(request.getStatus());
        drone.setLastTelemetryAt(LocalDateTime.now());

        drone = droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    public Object getCurrentDelivery(String code) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        // Find current active delivery for this drone
        List<Delivery> deliveries = entityManager.createQuery(
                "SELECT d FROM Delivery d WHERE d.droneId = :droneId " +
                "AND d.currentStatus IN (:statuses) ORDER BY d.actualDepartureTime DESC",
                Delivery.class)
                .setParameter("droneId", drone.getId())
                .setParameter("statuses", List.of(
                        DeliveryStatus.ASSIGNED,
                        DeliveryStatus.LAUNCHED,
                        DeliveryStatus.ARRIVING))
                .setMaxResults(1)
                .getResultList();

        if (deliveries.isEmpty()) {
            return null;
        }

        return deliveries.get(0);
    }

    /**
     * Find available drone suitable for delivery based on weight, distance and battery
     */
    public DroneResponse findAvailableDroneForDelivery(Integer weightGram,
                                                        Double fromLat, Double fromLng,
                                                        Double toLat, Double toLng) {
        double distance = calculateFlightDistance(fromLat, fromLng, toLat, toLng);
        int requiredBattery = estimateBatteryRequired(distance);

        List<Drone> availableDrones = droneRepository.findAll().stream()
                .filter(drone -> drone.getStatus() == DroneStatus.AVAILABLE)
                .filter(drone -> drone.getMaxPayloadGram() >= weightGram)
                .filter(drone -> drone.getCurrentBatteryPercent() >= requiredBattery)
                .sorted((d1, d2) -> {
                    // Sort by: 1) Distance to pickup, 2) Battery level
                    double dist1 = calculateFlightDistance(
                            d1.getLastLatitude().doubleValue(),
                            d1.getLastLongitude().doubleValue(),
                            fromLat, fromLng
                    );
                    double dist2 = calculateFlightDistance(
                            d2.getLastLatitude().doubleValue(),
                            d2.getLastLongitude().doubleValue(),
                            fromLat, fromLng
                    );
                    if (Math.abs(dist1 - dist2) > 0.5) { // If distance difference > 0.5km
                        return Double.compare(dist1, dist2);
                    }
                    return Integer.compare(d2.getCurrentBatteryPercent(), d1.getCurrentBatteryPercent());
                })
                .toList();

        if (availableDrones.isEmpty()) {
            throw new AppException(ErrorCode.NO_AVAILABLE_DRONE);
        }

        return droneMapper.toDroneResponse(availableDrones.get(0));
    }

    /**
     * Monitor battery and auto enable safety mode if needed
     */
    @Transactional
    public DroneResponse monitorBattery(String code) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        int battery = drone.getCurrentBatteryPercent();

        // Critical: < 10% - Force landing/return
        if (battery < 10) {
            drone.setStatus(DroneStatus.MAINTENANCE);
            drone = droneRepository.save(drone);
            return droneMapper.toDroneResponse(drone);
        }

        // Warning: < 20% - Switch to charging mode if not in delivery
        if (battery < 20 && drone.getStatus() != DroneStatus.IN_FLIGHT) {
            drone.setStatus(DroneStatus.CHARGING);
            drone = droneRepository.save(drone);
        }

        return droneMapper.toDroneResponse(drone);
    }

    /**
     * Enable safety mode (force drone to safe state)
     */
    @Transactional
    public DroneResponse enableSafetyMode(String code, String reason) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        // Set to MAINTENANCE mode (safe mode)
        drone.setStatus(DroneStatus.MAINTENANCE);
        drone.setLastTelemetryAt(LocalDateTime.now());

        drone = droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    /**
     * Get drones within radius (in km)
     */
    public List<DroneResponse> getDronesWithinRadius(Double centerLat, Double centerLng, Double radiusKm) {
        return droneRepository.findAll().stream()
                .filter(drone -> {
                    double distance = calculateFlightDistance(
                            drone.getLastLatitude().doubleValue(),
                            drone.getLastLongitude().doubleValue(),
                            centerLat,
                            centerLng
                    );
                    return distance <= radiusKm;
                })
                .map(droneMapper::toDroneResponse)
                .collect(Collectors.toList());
    }

    /**
     * Check drone health status
     */
    public DroneHealthStatus checkDroneHealth(String code) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        DroneHealthStatus health = new DroneHealthStatus();
        health.setDroneCode(code);
        health.setBatteryLevel(drone.getCurrentBatteryPercent());
        health.setStatus(drone.getStatus());
        health.setLastUpdate(drone.getLastTelemetryAt());

        // Check battery health
        if (drone.getCurrentBatteryPercent() < 10) {
            health.setBatteryHealth("CRITICAL");
            health.addIssue("Battery critically low: " + drone.getCurrentBatteryPercent() + "%");
        } else if (drone.getCurrentBatteryPercent() < 20) {
            health.setBatteryHealth("WARNING");
            health.addIssue("Battery low: " + drone.getCurrentBatteryPercent() + "%");
        } else if (drone.getCurrentBatteryPercent() < 50) {
            health.setBatteryHealth("FAIR");
        } else {
            health.setBatteryHealth("GOOD");
        }

        // Check last telemetry time
        if (drone.getLastTelemetryAt() != null) {
            long minutesSinceUpdate = java.time.Duration.between(
                    drone.getLastTelemetryAt(),
                    LocalDateTime.now()
            ).toMinutes();

            if (minutesSinceUpdate > 5) {
                health.setConnectionHealth("POOR");
                health.addIssue("No telemetry for " + minutesSinceUpdate + " minutes");
            } else {
                health.setConnectionHealth("GOOD");
            }
        }

        // Overall health
        if (!health.getIssues().isEmpty()) {
            health.setOverallHealth("NEEDS_ATTENTION");
        } else {
            health.setOverallHealth("HEALTHY");
        }

        return health;
    }

    /**
     * Calculate flight distance using Haversine formula (in km)
     */
    public double calculateFlightDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        final double EARTH_RADIUS_KM = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Estimate flight time in minutes based on distance
     * Assuming average drone speed of 30 km/h
     */
    public int estimateFlightTime(double distanceKm) {
        final double AVERAGE_SPEED_KMH = 30.0;
        return (int) Math.ceil((distanceKm / AVERAGE_SPEED_KMH) * 60);
    }

    /**
     * Estimate battery required for a flight
     * Assuming 10% battery per km + 10% safety margin
     */
    private int estimateBatteryRequired(double distanceKm) {
        int batteryPerKm = 10;
        int safetyMargin = 10;
        return (int) Math.ceil(distanceKm * batteryPerKm) + safetyMargin;
    }

    /**
     * Inner class for drone health status
     */
    @lombok.Data
    public static class DroneHealthStatus {
        private String droneCode;
        private int batteryLevel;
        private DroneStatus status;
        private LocalDateTime lastUpdate;
        private String batteryHealth;
        private String connectionHealth;
        private String overallHealth;
        private java.util.List<String> issues = new java.util.ArrayList<>();

        public void addIssue(String issue) {
            this.issues.add(issue);
        }
    }
}
