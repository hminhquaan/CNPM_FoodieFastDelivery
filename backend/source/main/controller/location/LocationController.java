package controller.location;

import dto.request.location.LocationRequest;
import dto.response.store.StoreAddressResponse;
import service.location.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Tìm kiếm các cửa hàng trong phạm vi hành lang bay an toàn
     * @param locationRequest Vị trí của người dùng (latitude, longitude)
     * @return Danh sách các cửa hàng trong phạm vi hành lang bay với khoảng cách
     */
    @PostMapping("/stores-within-flight-corridor")
    public ResponseEntity<List<StoreAddressResponse>> findStoresWithinFlightCorridor(
            @RequestBody LocationRequest locationRequest) {

        List<StoreAddressResponse> stores = locationService.findStoresWithinFlightCorridor(locationRequest);
        return ResponseEntity.ok(stores);
    }

    /**
     * Tìm kiếm các cửa hàng trong phạm vi hành lang bay an toàn (GET method với query params)
     * @param latitude Vĩ độ của người dùng
     * @param longitude Kinh độ của người dùng
     * @return Danh sách các cửa hàng trong phạm vi hành lang bay với khoảng cách
     */
    @GetMapping("/stores-within-flight-corridor")
    public ResponseEntity<List<StoreAddressResponse>> findStoresWithinFlightCorridorByParams(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        LocationRequest locationRequest = LocationRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();

        List<StoreAddressResponse> stores = locationService.findStoresWithinFlightCorridor(locationRequest);
        return ResponseEntity.ok(stores);
    }
}
