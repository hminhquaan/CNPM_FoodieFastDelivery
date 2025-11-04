package mapper;

import dto.request.store.StoreAddressRequest;
import dto.request.store.StoreRequest;
import dto.response.store.StoreAddressResponse;
import dto.response.store.StoreResponse;
import entity.Store;
import entity.StoreAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StoreMapper {
    @Mapping(target = "id", ignore = true)           // DB tự sinh
    @Mapping(target = "storeStatus", ignore = true) // set trong service
    @Mapping(target = "createdAt", ignore = true)   // DB tự sinh
    @Mapping(target = "updatedAt", ignore = true)   // DB tự sinh
    Store toStore(StoreRequest request);


    StoreResponse toStoreResponse(Store store);
    List<StoreResponse> toStoreResponseList(List<Store> stores);
    @Mapping(target = "id", ignore = true)           // DB tự sinh
    @Mapping(target = "storeStatus", ignore = true) // set trong service
    @Mapping(target = "createdAt", ignore = true)   // DB tự sinh
    @Mapping(target = "updatedAt", ignore = true)   // DB tự sinh
    void updateStore(@MappingTarget Store store, StoreRequest request);

    @Mapping(target = "id", ignore = true)
    StoreAddress toStoreAddress(StoreAddressRequest request);

    @Mapping(target = "storeId", source = "store.id")
    StoreAddressResponse toStoreAddressResponse(StoreAddress storeAddress);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "store", ignore = true)
    void updateStoreAddress(@MappingTarget StoreAddress entity, StoreAddressRequest request);
}