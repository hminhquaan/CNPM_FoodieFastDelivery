package mapper;

import dto.request.product.ProductRequest;
import dto.response.product.ProductResponse;
import entity.Product;
import entity.Store;
import org.mapstruct.*;
import static org.mapstruct.ReportingPolicy.IGNORE;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface ProductMapper {
    @Mapping(source = "storeId", target = "store")
    @Mapping(source = "categoryId", target = "category.id")
    Product toProduct(ProductRequest request);

    @Mapping(source = "store.id", target = "storeId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "weightGram", target = "weightGram")
    ProductResponse toProductResponse(Product product);

    List<ProductResponse> toProductResponse(List<Product> products);

    @Mapping(source = "storeId", target = "store")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProduct(ProductRequest request, @MappingTarget Product product);


    default Store mapStore(Long storeId) {
        if (storeId == null) return null;
        Store store = new Store();
        store.setId(storeId);
        return store;
    }

}