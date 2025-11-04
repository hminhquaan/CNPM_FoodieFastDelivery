package controller.product;

import dto.request.category.ProductCategoryRequest;
import dto.response.API.APIResponse;
import dto.response.category.ProductCategoryResponse;
import service.product.ProductCategoryServiceImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductCategoryController {
    ProductCategoryServiceImpl productCategoryService;

    @PostMapping
    public APIResponse<ProductCategoryResponse> createCategory(@RequestBody ProductCategoryRequest request) {
        return APIResponse.<ProductCategoryResponse>builder()
                .result(productCategoryService.createCategory(request))
                .build();
    }

    @PutMapping("/{id}")
    public APIResponse<ProductCategoryResponse> updateCategory(@PathVariable Long id, @RequestBody ProductCategoryRequest request) {
        return APIResponse.<ProductCategoryResponse>builder()
                .result(productCategoryService.updateCategory(id, request))
                .build();
    }

    @GetMapping
    public APIResponse<List<ProductCategoryResponse>> findAllCategory(){
        return APIResponse.<List<ProductCategoryResponse>>builder()
                .result(productCategoryService.getAllCategories())
                .build();
    }
}
