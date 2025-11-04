package service.product;

import dto.request.product.ProductRequest;
import dto.response.product.ProductResponse;

import java.util.List;

public interface ProductService {
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long productId, ProductRequest request);
    void delete(Long productId);
    ProductResponse getById(Long productId);
    List<ProductResponse> getByCategory(Long categoryId);
    List<ProductResponse> getByStore(Long storeId);
    List<ProductResponse> getAll();
    List<ProductResponse> searchProducts(String keyword);
}
