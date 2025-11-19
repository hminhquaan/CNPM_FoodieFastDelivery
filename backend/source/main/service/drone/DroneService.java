package service.drone;

import repository.drone.DroneRepository;
import repository.delivery.DeliveryRepository;
import dto.request.DroneLocationUpdateRequest;
import dto.request.DroneRegisterRequest;
import dto.request.DroneStatusUpdateRequest;
import dto.request.DroneUpdateRequest;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneService {

    DroneRepository droneRepository;
    DroneMapper droneMapper;
    DroneStationService droneStationService;
    DeliveryRepository deliveryRepository;

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
    public DroneResponse updateDrone(String code, DroneUpdateRequest request) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        if (request.getModel() != null) {
            drone.setModel(request.getModel());
        }
        if (request.getMaxPayloadGram() != null) {
            drone.setMaxPayloadGram(request.getMaxPayloadGram());
        }
        drone.setLastTelemetryAt(LocalDateTime.now());

        drone = droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    @Transactional
    public void deleteDrone(String code) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        // Prevent deleting drones that are active or charging
        if (drone.getStatus() == DroneStatus.IN_FLIGHT || drone.getStatus() == DroneStatus.CHARGING) {
            throw new AppException(ErrorCode.DRONE_NOT_AVAILABLE);
        }

        // Prevent delete if there are active deliveries assigned to this drone
        var active = deliveryRepository.findActiveDeliveriesByDrone(
                drone.getId(),
                java.util.List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.LAUNCHED, DeliveryStatus.ARRIVING)
        );
        if (!active.isEmpty()) {
            throw new AppException(ErrorCode.DRONE_NOT_AVAILABLE);
        }

        droneRepository.delete(drone);
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

    // Track charging threads to prevent duplicates
    private final java.util.concurrent.ConcurrentHashMap<Long, Thread> chargingThreads = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public DroneResponse updateStatus(String code, DroneStatusUpdateRequest request) {
        Drone drone = droneRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.DRONE_NOT_FOUND));

        DroneStatus newStatus = request.getStatus();
        drone.setStatus(newStatus);
        drone.setLastTelemetryAt(LocalDateTime.now());

        // Auto battery behavior when entering CHARGING
        if (newStatus == DroneStatus.CHARGING) {
            Integer current = drone.getCurrentBatteryPercent();
            if (current == null) current = 0;
            // Snap to station location if available
            try {
                var station = droneStationService.getOrCreateDefault();
                if (station != null && station.getLatitude() != null && station.getLongitude() != null) {
                    try {
                        drone.setLastLatitude(station.getLatitude());
                        drone.setLastLongitude(station.getLongitude());
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // Save state before starting thread
            droneRepository.save(drone);
            final long droneIdVal = drone.getId();
            // Start charging loop after transaction commits to avoid stale reads
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        startChargingThread(droneIdVal);
                    }
                });
            } else {
                startChargingThread(droneIdVal);
            }
        } else {
            // Leaving CHARGING -> stop background thread if any
            Thread t = chargingThreads.remove(drone.getId());
            if (t != null && t.isAlive()) {
                t.interrupt();
            }
        }

        // Persist latest state
        droneRepository.save(drone);
        return droneMapper.toDroneResponse(drone);
    }

    /**
     * Convenience: move drone to station and switch to CHARGING, returns updated state.
     */
    @Transactional
    public DroneResponse returnToStationAndCharge(String code) {
        // Delegate to updateStatus which will snap to station and start charging after commit
        return updateStatus(code, new DroneStatusUpdateRequest(DroneStatus.CHARGING));
    }

    private void startChargingThread(long droneIdVal) {
        // Prevent duplicate charging threads
        Thread existing = chargingThreads.get(droneIdVal);
        if (existing != null && existing.isAlive()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Long key = java.util.Objects.requireNonNull(Long.valueOf(droneIdVal));
                    Drone d = droneRepository.findById(key).orElse(null);
                    if (d == null) break;
                    if (d.getStatus() != DroneStatus.CHARGING) break; // charging cancelled externally
                    int lvl = d.getCurrentBatteryPercent() == null ? 0 : d.getCurrentBatteryPercent();
                    if (lvl >= 100) {
                        d.setCurrentBatteryPercent(100);
                        d.setStatus(DroneStatus.AVAILABLE);
                        d.setLastTelemetryAt(LocalDateTime.now());
                        droneRepository.save(d);
                        break;
                    }
                    int next = Math.min(100, lvl + 5);
                    d.setCurrentBatteryPercent(next);
                    d.setLastTelemetryAt(LocalDateTime.now());
                    droneRepository.save(d);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
                // Stop charging loop
            } finally {
                chargingThreads.remove(droneIdVal);
            }
        }, "drone-charging-" + droneIdVal);
        t.setDaemon(true);
        chargingThreads.put(droneIdVal, t);
        t.start();
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
        double distanceDelivery = calculateFlightDistance(fromLat, fromLng, toLat, toLng);
        int requiredBattery = estimateBatteryRequired(distanceDelivery);

        List<Drone> candidates = droneRepository.findAll().stream()
            .filter(drone -> drone.getStatus() == DroneStatus.AVAILABLE)
            .filter(drone -> drone.getMaxPayloadGram() >= weightGram)
            .filter(drone -> drone.getCurrentBatteryPercent() >= requiredBattery)
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new AppException(ErrorCode.NO_AVAILABLE_DRONE);
        }

        // Rank by fastest ETA considering code-based speed and weight-adjusted speed
        Drone best = candidates.stream()
            .min((d1, d2) -> {
                double eta1 = estimateTotalTimeMinutesForDrone(d1, weightGram, fromLat, fromLng, toLat, toLng);
                double eta2 = estimateTotalTimeMinutesForDrone(d2, weightGram, fromLat, fromLng, toLat, toLng);
                int cmp = Double.compare(eta1, eta2);
                if (cmp != 0) return cmp;
                // Tie-breaker: closer to pickup, then higher battery
                double d1Pickup = calculateFlightDistance(
                    d1.getLastLatitude().doubleValue(), d1.getLastLongitude().doubleValue(), fromLat, fromLng);
                double d2Pickup = calculateFlightDistance(
                    d2.getLastLatitude().doubleValue(), d2.getLastLongitude().doubleValue(), fromLat, fromLng);
                int cmp2 = Double.compare(d1Pickup, d2Pickup);
                if (cmp2 != 0) return cmp2;
                return Integer.compare(d2.getCurrentBatteryPercent(), d1.getCurrentBatteryPercent());
            })
            .orElseThrow(() -> new AppException(ErrorCode.NO_AVAILABLE_DRONE));

        return droneMapper.toDroneResponse(best);
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
     * Estimate flight time (minutes) for a specific drone and payload.
     * Uses per-code base speed and reduces speed with higher payload ratio.
     */
    public int estimateFlightTimeForDrone(Drone drone, double distanceKm, int payloadGram) {
        double speed = effectiveSpeedKmh(drone, payloadGram);
        if (speed <= 1.0) speed = 1.0; // avoid div-by-zero
        return (int) Math.ceil((distanceKm / speed) * 60.0);
    }

    private double estimateTotalTimeMinutesForDrone(Drone drone, int payloadGram,
                                                    double fromLat, double fromLng,
                                                    double toLat, double toLng) {
        // Leg 1: drone -> pickup (no payload)
        double distToPickup = calculateFlightDistance(
                drone.getLastLatitude().doubleValue(), drone.getLastLongitude().doubleValue(), fromLat, fromLng);
        double baseSpeed = baseSpeedKmhForCode(drone.getCode());
        double minutesToPickup = (distToPickup / Math.max(baseSpeed, 1.0)) * 60.0;
        // Leg 2: pickup -> dropoff (with payload)
        double distDelivery = calculateFlightDistance(fromLat, fromLng, toLat, toLng);
        double effSpeed = effectiveSpeedKmh(drone, payloadGram);
        double minutesDeliver = (distDelivery / Math.max(effSpeed, 1.0)) * 60.0;
        return Math.ceil(minutesToPickup + minutesDeliver);
    }

    /**
     * Public ETA calculator for a given drone id and route.
     */
    public int estimateTotalTimeMinutesForDroneId(Long droneId, int payloadGram,
                                                  double fromLat, double fromLng,
                                                  double toLat, double toLng) {
        Long key = java.util.Objects.requireNonNull(droneId);
        Drone d = droneRepository.findById(key).orElse(null);
        if (d == null) return estimateFlightTime(calculateFlightDistance(fromLat, fromLng, toLat, toLng));
        return (int) estimateTotalTimeMinutesForDrone(d, payloadGram, fromLat, fromLng, toLat, toLng);
    }

    /**
     * Base speed by code family. Adjust these profiles as needed.
     */
    private double baseSpeedKmhForCode(String code) {
        if (code == null) return 30.0;
        String c = code.toUpperCase();
        if (c.contains("-BM-")) return 42.0; // Banh Mi drones: fast
        if (c.contains("-SS-")) return 36.0; // Sushi drones: medium
        if (c.contains("-PZ-")) return 28.0; // Pizza drones: slower
        return 30.0; // default
    }

    /**
     * Effective speed considering payload ratio. Up to 40% reduction at max load.
     */
    private double effectiveSpeedKmh(Drone drone, int payloadGram) {
        double base = baseSpeedKmhForCode(drone.getCode());
        Integer maxPayload = drone.getMaxPayloadGram();
        if (maxPayload == null || maxPayload <= 0) return base;
        double ratio = Math.min(1.0, Math.max(0.0, ((double) payloadGram) / maxPayload));
        double factor = 1.0 - 0.4 * ratio; // reduce up to 40%
        if (factor < 0.6) factor = 0.6; // clamp
        return base * factor;
    }

    /**
     * Estimate battery required for a flight
     * Assuming 10% battery per km + 10% safety margin
     */
    private int estimateBatteryRequired(double distanceKm) {
        // Điều chỉnh công thức: 3% mỗi km + 7% safety margin, clamp [15,100]
        // Increase consumption to be more visible during demos
        double batteryPerKm = 12.0;
        int safetyMargin = 15;
        int required = (int) Math.ceil(distanceKm * batteryPerKm) + safetyMargin;
        if (required < 25) required = 25; // minimum to avoid too small values
        if (required > 100) required = 100; // không vượt quá 100%
        return required;
    }

    /**
     * Public helper for other services to compute estimated battery usage.
     */
    public int estimateBatteryUsageForDistance(double distanceKm) {
        return estimateBatteryRequired(distanceKm);
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
