package service.product;

import dto.request.category.ProductCategoryRequest;
import dto.response.category.ProductCategoryResponse;

import java.util.List;

public interface ProductCategoryService {
    ProductCategoryResponse createCategory(ProductCategoryRequest category);
    ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest category);
    void deleteCategory(Long id);
    List<ProductCategoryResponse> getAllCategories();
    ProductCategoryResponse getCategoryById(Long id);
//    List<ProductCategory> getRootCategories();
}
