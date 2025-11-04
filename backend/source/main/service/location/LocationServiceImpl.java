package service.location;

import dto.request.location.LocationRequest;
import dto.response.store.StoreAddressResponse;
import entity.StoreAddress;
import repository.store.StoreAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final StoreAddressRepository storeAddressRepository;

    @Override
    public List<StoreAddressResponse> findStoresWithinFlightCorridor(LocationRequest locationRequest) {
        List<StoreAddress> storeAddresses = storeAddressRepository.findStoresWithinFlightCorridor(
                locationRequest.getLatitude(),
                locationRequest.getLongitude()
        );
        
        return storeAddresses.stream()
                .map(address -> {
                    // Tính khoảng cách từ vị trí người dùng đến cửa hàng
                    double distance = calculateDistance(
                            locationRequest.getLatitude(),
                            locationRequest.getLongitude(),
                            address.getLatitude(),
                            address.getLongitude()
                    );
                    
                    return StoreAddressResponse.builder()
                            .id(address.getId())
                            .addressLine(address.getAddressLine())
                            .city(address.getCity())
                            .district(address.getDistrict())
                            .ward(address.getWard())
                            .latitude(address.getLatitude())
                            .longitude(address.getLongitude())
                            .storeId(address.getStore().getId())
                            .Distanct(distance)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Tính khoảng cách giữa hai điểm trên mặt cầu sử dụng công thức Haversine
     * @param lat1 Vĩ độ điểm 1
     * @param lon1 Kinh độ điểm 1
     * @param lat2 Vĩ độ điểm 2
     * @param lon2 Kinh độ điểm 2
     * @return Khoảng cách giữa hai điểm (km)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Bán kính trái đất (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}