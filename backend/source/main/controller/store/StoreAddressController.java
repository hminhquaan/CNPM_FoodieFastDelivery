package controller.store;

import dto.request.store.StoreAddressRequest;
import dto.response.API.APIResponse;
import dto.response.store.StoreAddressResponse;
import service.store.StoreAddressImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/storesaddresses")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoreAddressController {

    StoreAddressImpl storeAddressService;

    @PostMapping("{storeId}/addresses")
    public APIResponse<StoreAddressResponse> create(@PathVariable Long storeId, @RequestBody StoreAddressRequest request) {
        return APIResponse.<StoreAddressResponse>builder()
                .result(storeAddressService.createAddress(storeId, request))
                .build();

    }

    @PutMapping("/{addressId}")
    public APIResponse<StoreAddressResponse> update(@PathVariable Long storeId,
                                       @PathVariable Long addressId,
                                       @RequestBody StoreAddressRequest request) {
        // storeId để đảm bảo address này thuộc đúng store
        return APIResponse.<StoreAddressResponse>builder()
                .result(storeAddressService.updateAddress(addressId, request))
                .build();
    }

    @GetMapping
    public APIResponse<List<StoreAddressResponse>> getAddressByStore(@PathVariable Long storeId) {
        return APIResponse.<List<StoreAddressResponse>>builder()
                .result(storeAddressService.getAddressesByStore(storeId))
                .build();
    }

    @DeleteMapping("/{addressId}")
    public APIResponse<Void> deleteAddress(@PathVariable Long addressId, @PathVariable String storeId) {
        storeAddressService.deleteAddress(addressId);
        return APIResponse.<Void>builder()
                .message("Address deleted successfully")
                .build();
    }

}
