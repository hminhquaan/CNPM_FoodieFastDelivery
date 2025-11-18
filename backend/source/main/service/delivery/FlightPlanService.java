package service.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.response.FlightPlanPointResponse;
import entity.Delivery;
import entity.FlightPlan;
import entity.FlightPlanPoint;
import entity.StoreAddress;
import enums.FlightPlanStatus;
import exception.AppException;
import exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.delivery.DeliveryRepository;
import repository.delivery.FlightPlanPointRepository;
import repository.delivery.FlightPlanRepository;
import repository.store.StoreAddressRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FlightPlanService {

    FlightPlanRepository flightPlanRepository;
    FlightPlanPointRepository flightPlanPointRepository;
    DeliveryRepository deliveryRepository;
    StoreAddressRepository storeAddressRepository;

    static final double DEMO_STATION_LAT = 10.760100;
    static final double DEMO_STATION_LNG = 106.659900;

    ObjectMapper mapper = new ObjectMapper();

    /**
     * Ensure a flight plan for a delivery and return its points.
     * - If drone is not yet assigned, returns a preview (not persisted): station→store→customer
     * - If drone is assigned and no plan exists, creates a plan and persists points.
     * - If a plan exists, returns persisted points.
     */
    @Transactional(readOnly = false)
    @SuppressWarnings("null")
    public List<FlightPlanPointResponse> ensurePlanForDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

        // Resolve coordinates
        double[] store = resolveStoreLatLng(delivery.getPickupStoreId())
                .orElseGet(() -> new double[]{DEMO_STATION_LAT + 0.002, DEMO_STATION_LNG + 0.002});
        double[] drop = resolveDropoffLatLng(delivery.getDropoffAddressSnapshot())
                .orElseGet(() -> new double[]{store[0] + 0.004, store[1] + 0.004});

        boolean droneAssigned = delivery.getDroneId() != null;

        if (!droneAssigned) {
            // Preview only, do not persist
            List<FlightPlanPointResponse> leg1 = generatePathBetweenResponses(DEMO_STATION_LAT, DEMO_STATION_LNG,
                    store[0], store[1], 10, 100.0, 0);
            List<FlightPlanPointResponse> leg2 = generatePathBetweenResponses(store[0], store[1],
                    drop[0], drop[1], 16, 120.0, leg1.size());
            List<FlightPlanPointResponse> preview = new ArrayList<>();
            preview.addAll(leg1);
            preview.addAll(leg2);
            // Normalize sequence numbers
            for (int i = 0; i < preview.size(); i++) preview.get(i).setSequenceNo(i + 1);
            return preview;
        }

        // Drone assigned: return existing plan if exists
        Optional<FlightPlan> existing = flightPlanRepository.findByDeliveryId(delivery.getId());
        if (existing.isPresent()) {
            List<FlightPlanPoint> points = flightPlanPointRepository
                    .findByFlightPlanIdOrderBySequenceNoAsc(existing.get().getId());
            return points.stream().map(this::toResponse).collect(Collectors.toList());
        }

        // Create plan and persist points
        FlightPlan plan = FlightPlan.builder()
                .deliveryId(delivery.getId())
                .droneId(delivery.getDroneId())
                .plannedDepartureTime(LocalDateTime.now())
                .plannedArrivalTime(LocalDateTime.now().plusMinutes(20))
                .routeSummary("Auto-generated plan station→store→customer")
                .status(FlightPlanStatus.PLANNED)
                .build();
        plan = flightPlanRepository.save(plan);

        List<FlightPlanPoint> leg1 = generatePathBetween(plan.getId(), DEMO_STATION_LAT, DEMO_STATION_LNG,
                store[0], store[1], 10, 100.0, 0);
        List<FlightPlanPoint> leg2 = generatePathBetween(plan.getId(), store[0], store[1],
                drop[0], drop[1], 16, 120.0, leg1.size());
        List<FlightPlanPoint> all = new ArrayList<>();
        all.addAll(leg1);
        all.addAll(leg2);
        flightPlanPointRepository.saveAll(all);

        return all.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private Optional<double[]> resolveStoreLatLng(Long storeId) {
        if (storeId == null) return Optional.empty();
        List<StoreAddress> list = storeAddressRepository.findByStore_Id(storeId);
        if (list == null || list.isEmpty()) return Optional.empty();
        StoreAddress addr = list.get(0);
        if (addr.getLatitude() == null || addr.getLongitude() == null) return Optional.empty();
        return Optional.of(new double[]{addr.getLatitude(), addr.getLongitude()});
    }

    private Optional<double[]> resolveDropoffLatLng(String dropoffSnapshotJson) {
        try {
            if (dropoffSnapshotJson == null || dropoffSnapshotJson.isBlank()) return Optional.empty();
            JsonNode root = mapper.readTree(dropoffSnapshotJson);
            if (root.hasNonNull("lat") && root.hasNonNull("lng")) {
                return Optional.of(new double[]{root.get("lat").asDouble(), root.get("lng").asDouble()});
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<FlightPlanPoint> generatePathBetween(Long planId,
                                                       double sLat, double sLng,
                                                       double eLat, double eLng,
                                                       int steps, double altitude,
                                                       int seqOffset) {
        List<FlightPlanPoint> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double lat = sLat + (eLat - sLat) * t + Math.sin(t * Math.PI) * 0.0020;
            double lng = sLng + (eLng - sLng) * t + Math.sin(t * Math.PI) * 0.0020;
            FlightPlanPoint p = FlightPlanPoint.builder()
                    .flightPlanId(planId)
                    .sequenceNo(seqOffset + i + 1)
                    .latitude(BigDecimal.valueOf(lat))
                    .longitude(BigDecimal.valueOf(lng))
                    .altitudeM(BigDecimal.valueOf(altitude))
                    .etaTime(base.plusSeconds(i * 20L))
                    .build();
            list.add(p);
        }
        return list;
    }

    private List<FlightPlanPointResponse> generatePathBetweenResponses(double sLat, double sLng,
                                                                       double eLat, double eLng,
                                                                       int steps, double altitude,
                                                                       int seqOffset) {
        List<FlightPlanPointResponse> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double lat = sLat + (eLat - sLat) * t + Math.sin(t * Math.PI) * 0.0020;
            double lng = sLng + (eLng - sLng) * t + Math.sin(t * Math.PI) * 0.0020;
            list.add(FlightPlanPointResponse.builder()
                    .sequenceNo(seqOffset + i + 1)
                    .latitude(BigDecimal.valueOf(lat))
                    .longitude(BigDecimal.valueOf(lng))
                    .altitudeM(BigDecimal.valueOf(altitude))
                    .etaTime(base.plusSeconds(i * 20L))
                    .build());
        }
        return list;
    }

    private FlightPlanPointResponse toResponse(FlightPlanPoint p) {
        return FlightPlanPointResponse.builder()
                .id(p.getId())
                .sequenceNo(p.getSequenceNo())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .altitudeM(p.getAltitudeM())
                .etaTime(p.getEtaTime())
                .build();
    }
}
