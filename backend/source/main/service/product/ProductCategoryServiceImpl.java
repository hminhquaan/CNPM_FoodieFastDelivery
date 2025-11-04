package service.product;


import dto.request.category.ProductCategoryRequest;
import dto.response.category.ProductCategoryResponse;
import entity.ProductCategory;
import enums.CategoryStatus;
import exception.AppException;
import exception.ErrorCode;
import mapper.ProductCategoryMapper;
import repository.product.ProductCategoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductCategoryServiceImpl implements ProductCategoryService {
    ProductCategoryRepository productCategoryRepository;
    ProductCategoryMapper productCategoryMapper;

    @Override
    public ProductCategoryResponse createCategory(ProductCategoryRequest request) {
        // Kiểm tra slug đã tồn tại chưa
        if (productCategoryRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.CATEGORY_SLUG_EXISTED);
        }

        ProductCategory productCategory = productCategoryMapper.toProductCategory(request);
        productCategory.setStatus(CategoryStatus.ACTIVE);

        ProductCategory saved = productCategoryRepository.save(productCategory);
        return productCategoryMapper.toProductCategoryResponse(saved);
    }

    @Override
    public ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest request) {
        ProductCategory productCategory = productCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_EXISTED));

        // Kiểm tra slug mới có bị trùng không (trừ chính nó)
        if (request.getSlug() != null && !request.getSlug().equals(productCategory.getSlug())) {
            if (productCategoryRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.CATEGORY_SLUG_EXISTED);
            }
        }

        productCategoryMapper.updateProductCategory(productCategory, request);
        ProductCategory saved = productCategoryRepository.save(productCategory);

        return productCategoryMapper.toProductCategoryResponse(saved);
    }

    @Override
    public void deleteCategory(Long id) {
        ProductCategory productCategory = productCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_EXISTED));

        productCategoryRepository.delete(productCategory);
    }

    @Override
    public List<ProductCategoryResponse> getAllCategories() {
        List<ProductCategory> categories = productCategoryRepository.findAll();
        return productCategoryMapper.toProductCategoryResponse(categories);
    }
}
