package controller.product;


import dto.request.product.ProductRequest;
import dto.response.API.APIResponse;
import dto.response.product.ProductResponse;
import service.product.ProductServiceImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import repository.user.UserRepository;
import repository.store.StoreRepository;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
 public class ProductController {
     ProductServiceImpl productService;
     UserRepository userRepository;
     StoreRepository storeRepository;

    private boolean isAdmin(java.util.Set<String> roles){
        if (roles == null) return false;
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
    }

    private java.util.Set<String> getCurrentRoles(){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return java.util.Collections.emptySet();
        return auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
    }

    private Long getCurrentUserId(){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String username = auth.getName();
        try { return userRepository.findIdByUsername(username); } catch(Exception e){ return null; }
    }

    private void assertStoreAccess(Long storeId){
        java.util.Set<String> roles = getCurrentRoles();
        if (isAdmin(roles)) return; // admin can access any store
        Long uid = getCurrentUserId();
        if (uid == null) throw new AccessDeniedException("Unauthorized");
        boolean owns = false;
        try {
            var stores = storeRepository.findByOwnerUserId(uid);
            owns = stores.stream().anyMatch(s -> java.util.Objects.equals(s.getId(), storeId));
        } catch(Exception e){ /* ignore */ }
        if (!owns) throw new AccessDeniedException("You do not have access to this store");
    }

    @PostMapping
    public APIResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request){
        // Only allow creating product for owned store (or admin)
        if (request.getStoreId() != null) {
            assertStoreAccess(request.getStoreId());
        }
        return APIResponse.<ProductResponse>builder()
                .result(productService.create(request))
                .build();
    }

    @PutMapping("/{id}")
    public APIResponse<ProductResponse> updateProduct(@Valid @RequestBody ProductRequest request, @PathVariable Long id) {
        // Verify ownership on current and target store
        try {
            ProductResponse current = productService.getById(id);
            if (current != null && current.getStoreId() != null) assertStoreAccess(current.getStoreId());
        } catch (Exception ignore) { /* fallback to request check */ }
        if (request.getStoreId() != null) {
            assertStoreAccess(request.getStoreId());
        }
        return APIResponse.<ProductResponse>builder()
                .result(productService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public APIResponse<Void> deleteProduct(@PathVariable Long id) {
        // Verify ownership by product's store
        try {
            ProductResponse current = productService.getById(id);
            if (current != null && current.getStoreId() != null) assertStoreAccess(current.getStoreId());
        } catch (Exception e) {
            // If product not found, service layer will throw; no need to assert here
        }
        productService.delete(id);
        return APIResponse.<Void>builder()
                .message("Product deleted successfully")
                .build();
    }

    @GetMapping("/{id}")
    public APIResponse<ProductResponse> getProduct(@PathVariable Long id) {
        return APIResponse.<ProductResponse>builder()
                .result(productService.getById(id))
                .build();
    }

    @GetMapping
    public APIResponse<List<ProductResponse>> getAllProducts() {
        return APIResponse.<List<ProductResponse>>builder()
                .result(productService.getAll())
                .build();
    }

    @GetMapping("/category/{categoryId}")
    public APIResponse<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        return APIResponse.<List<ProductResponse>>builder()
                .result(productService.getByCategory(categoryId))
                .build();
    }

    @GetMapping("/store/{storeId}")
    public APIResponse<List<ProductResponse>> getProductsByStore(@PathVariable Long storeId) {
        return APIResponse.<List<ProductResponse>>builder()
                .result(productService.getByStore(storeId))
                .build();
    }

    @GetMapping("/search")
    public APIResponse<List<ProductResponse>> searchProducts(@RequestParam String keyword) {
        return APIResponse.<List<ProductResponse>>builder()
                .result(productService.searchProducts(keyword))
                .build();
    }

}
