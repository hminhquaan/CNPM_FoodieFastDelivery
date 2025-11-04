package service.store;

import dto.request.store.StoreAddressRequest;
import dto.response.store.StoreAddressResponse;
import entity.Store;
import entity.StoreAddress;
import exception.AppException;
import exception.ErrorCode;
import mapper.StoreMapper;
import repository.store.StoreAddressRepository;
import repository.store.StoreRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoreAddressImpl extends StoreAddress {
    StoreMapper storeMapper;
    StoreRepository storeRepository;
    StoreAddressRepository storeAddressRepository;

    public StoreAddressResponse createAddress(Long storeId, StoreAddressRequest request){
        Store store= storeRepository.findById(storeId).orElseThrow(() ->   new AppException(ErrorCode.STORE_NOT_EXISTED));

        StoreAddress storeAddress= storeMapper.toStoreAddress(request);
        storeAddress.setStore(store);
        storeAddressRepository.save(storeAddress);
        return storeMapper.toStoreAddressResponse(storeAddress);
    }

    public StoreAddressResponse updateAddress(Long addressId, StoreAddressRequest request){
        StoreAddress storeAddress= storeAddressRepository.findById(addressId).orElseThrow(() ->  new AppException(ErrorCode.ADDREESS_NOT_EXISTED));
        storeMapper.updateStoreAddress(storeAddress,request);
        storeAddressRepository.save(storeAddress);
        return storeMapper.toStoreAddressResponse(storeAddress);
    }

    public List<StoreAddressResponse> getAddressesByStore(Long storeId){
        return storeAddressRepository.findByStore_Id(storeId)
                .stream()
                .map(storeMapper::toStoreAddressResponse)
                .toList();
    }

    public void deleteAddress(Long addressId){
        StoreAddress storeAddress= storeAddressRepository.findById(addressId).orElseThrow(() -> new AppException(ErrorCode.ADDREESS_NOT_EXISTED));
        storeAddressRepository.delete(storeAddress);
    }

}
