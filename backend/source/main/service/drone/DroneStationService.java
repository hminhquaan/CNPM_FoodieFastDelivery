package service.drone;

import dto.response.DroneStationResponse;
import entity.DroneStation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.drone.DroneStationRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DroneStationService {
    private final DroneStationRepository stationRepository;

    public DroneStationResponse getOrCreateDefault()
    {
        return stationRepository.findAll().stream().findFirst()
                .map(this::toResponse)
                .orElseGet(() -> toResponse(createDefaultStation()));
    }

    @Transactional
    public DroneStationResponse updateStation(String name, BigDecimal lat, BigDecimal lng, Double radiusKm) {
        DroneStation station = stationRepository.findAll().stream().findFirst()
                .orElseGet(this::createDefaultStation);
        if (name != null) station.setName(name);
        if (lat != null) station.setLatitude(lat);
        if (lng != null) station.setLongitude(lng);
        if (radiusKm != null) station.setRadiusKm(radiusKm);
        return toResponse(stationRepository.save(station));
    }

    private DroneStation createDefaultStation() {
        DroneStation station = DroneStation.builder()
                .name("Default Station")
                .latitude(new BigDecimal("10.776"))
                .longitude(new BigDecimal("106.700"))
                .radiusKm(8.0)
                .build();
        return stationRepository.save(station);
    }

    private DroneStationResponse toResponse(DroneStation s) {
        return DroneStationResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .radiusKm(s.getRadiusKm())
                .build();
    }
}
