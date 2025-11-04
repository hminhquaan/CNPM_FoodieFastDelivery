package service.store;

import dto.request.store.StoreAddressRequest;
import entity.StoreAddress;

import java.util.List;

public interface StoreAddressService {
    StoreAddress createAddress(Long storeId, StoreAddressRequest request);
    List<StoreAddress> getAddressesByStore(Long storeId);
    void deleteAddress(Long addressId);
}
