package mapper;


import dto.request.category.ProductCategoryRequest;
import dto.response.category.ProductCategoryResponse;
import entity.ProductCategory;
import enums.CategoryStatus;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {

    // Map từ request -> entity (create)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(mapStatus(request.getStatus()))")
    ProductCategory toProductCategory(ProductCategoryRequest request);


    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    ProductCategoryResponse toProductCategoryResponse(ProductCategory entity);

    List<ProductCategoryResponse> toProductCategoryResponse(List<ProductCategory> entityList);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE) //nếu null thì giữ nguyên
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(request.getStatus() != null ? mapStatus(request.getStatus()) : productCategory.getStatus())")
    void updateProductCategory(@MappingTarget ProductCategory productCategory, ProductCategoryRequest request);


    default CategoryStatus mapStatus(String status) {
        if (status == null) return CategoryStatus.ACTIVE;
        return CategoryStatus.valueOf(status.toUpperCase());
    }
}