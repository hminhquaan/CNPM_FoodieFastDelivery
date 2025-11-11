package service.delivery;

import dto.response.FlightPlanPointResponse;
import entity.Delivery;
import entity.FlightPlan;
import entity.FlightPlanPoint;
import enums.FlightPlanStatus;
import exception.AppException;
import exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.delivery.FlightPlanPointRepository;
import repository.delivery.FlightPlanRepository;
import repository.delivery.DeliveryRepository;
import repository.store.StoreAddressRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FlightPlanService {

    FlightPlanRepository flightPlanRepository;
    FlightPlanPointRepository flightPlanPointRepository;
    DeliveryRepository deliveryRepository;
    StoreAddressRepository storeAddressRepository;

    public List<FlightPlanPointResponse> getFlightPlanPoints(Long deliveryId) {
        FlightPlan plan = flightPlanRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));
        List<FlightPlanPoint> points = flightPlanPointRepository.findByFlightPlanIdOrderBySequenceNoAsc(plan.getId());
        return points.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public List<FlightPlanPointResponse> ensurePlanForDelivery(Long deliveryId) {
        FlightPlan plan = flightPlanRepository.findByDeliveryId(deliveryId).orElse(null);
        if (plan == null) {
            Delivery delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new AppException(ErrorCode.DELIVERY_NOT_FOUND));

            // Resolve origin (store) coordinates
            Double storeLat = null, storeLng = null;
            try {
                var addresses = storeAddressRepository.findByStore_Id(delivery.getPickupStoreId());
                if (!addresses.isEmpty()) {
                    var addr = addresses.get(0);
                    storeLat = addr.getLatitude();
                    storeLng = addr.getLongitude();
                }
            } catch (Exception ignored) {}
            if (storeLat == null || storeLng == null) { storeLat = 10.762622; storeLng = 106.660172; }

            // Resolve destination (dropoff) coordinates from snapshot JSON
            Double dropLat = null, dropLng = null;
            try {
                String snap = delivery.getDropoffAddressSnapshot();
                if (snap != null && snap.contains("lat") && snap.contains("lng")) {
                    int li = snap.indexOf("\"lat\":");
                    int gi = snap.indexOf("\"lng\":");
                    if (li >= 0) {
                        int end = snap.indexOf(',', li);
                        String val = (end>li? snap.substring(li+6,end): snap.substring(li+6))
                                .replaceAll("[^0-9.\\-]", " ")
                                .trim();
                        dropLat = Double.valueOf(val);
                    }
                    if (gi >= 0) {
                        int end = snap.indexOf('}', gi);
                        String val = (end>gi? snap.substring(gi+6,end): snap.substring(gi+6))
                                .replaceAll("[^0-9.\\-]", " ")
                                .trim();
                        dropLng = Double.valueOf(val);
                    }
                }
            } catch (Exception ignored) {}
            if (dropLat == null || dropLng == null) { dropLat = 10.772622; dropLng = 106.670172; }

            // If drone is not yet assigned, don't persist plan (drone_id FK not satisfied). Return preview points only.
            if (delivery.getDroneId() == null) {
                return generatePathBetweenResponses(storeLat, storeLng, dropLat, dropLng);
            }

            plan = FlightPlan.builder()
                    .deliveryId(delivery.getId())
                    .droneId(delivery.getDroneId())
                    .plannedDepartureTime(LocalDateTime.now())
                    .plannedArrivalTime(LocalDateTime.now().plusMinutes(15))
                    .routeSummary("Auto-generated plan from store to customer")
                    .status(FlightPlanStatus.PLANNED)
                    .build();
            plan = flightPlanRepository.save(plan);

            List<FlightPlanPoint> generated = generatePathBetween(plan.getId(), storeLat, storeLng, dropLat, dropLng);
            flightPlanPointRepository.saveAll(generated);
        }
        return getFlightPlanPoints(deliveryId);
    }

    private List<FlightPlanPoint> generatePathBetween(Long planId, double sLat, double sLng, double eLat, double eLng) {
        // Generate a smooth path with slight sinusoidal variation between start and end
        int steps = 16;
        List<FlightPlanPoint> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();
        for (int i=0;i<=steps;i++) {
            double t = i/(double)steps;
            double lat = sLat + (eLat - sLat) * t + Math.sin(t*Math.PI) * 0.0020;
            double lng = sLng + (eLng - sLng) * t + Math.sin(t*Math.PI) * 0.0020;
            FlightPlanPoint p = FlightPlanPoint.builder()
                    .flightPlanId(planId)
                    .sequenceNo(i+1)
                    .latitude(BigDecimal.valueOf(lat))
                    .longitude(BigDecimal.valueOf(lng))
                    .altitudeM(BigDecimal.valueOf(120.0))
                    .etaTime(base.plusSeconds(i*20L))
                    .build();
            list.add(p);
        }
        return list;
    }

    private List<FlightPlanPointResponse> generatePathBetweenResponses(double sLat, double sLng, double eLat, double eLng) {
        int steps = 16;
        List<FlightPlanPointResponse> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();
        for (int i=0;i<=steps;i++) {
            double t = i/(double)steps;
            double lat = sLat + (eLat - sLat) * t + Math.sin(t*Math.PI) * 0.0020;
            double lng = sLng + (eLng - sLng) * t + Math.sin(t*Math.PI) * 0.0020;
            list.add(FlightPlanPointResponse.builder()
                    .sequenceNo(i+1)
                    .latitude(BigDecimal.valueOf(lat))
                    .longitude(BigDecimal.valueOf(lng))
                    .altitudeM(BigDecimal.valueOf(120.0))
                    .etaTime(base.plusSeconds(i*20L))
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
