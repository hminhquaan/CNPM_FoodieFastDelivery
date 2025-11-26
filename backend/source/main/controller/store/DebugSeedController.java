package controller.store;

import dto.response.API.APIResponse;
import entity.Product;
import entity.ProductCategory;
import entity.Store;
import entity.User;
import enums.StoreStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.product.ProductCategoryRepository;
import repository.product.ProductRepository;
import repository.store.StoreRepository;
import repository.user.UserRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugSeedController {

    private final StoreRepository storeRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @GetMapping("/seed")
    public APIResponse<Map<String, Object>> seed() {
        Map<String, Object> result = new HashMap<>();

        // Find owner
        User owner = userRepository.findByUsername("user")
                .orElseGet(() -> userRepository.findByUsername("admin").orElse(null));
        if (owner == null) {
            return APIResponse.<Map<String, Object>>builder()
                    .code(400)
                    .message("No demo user found (user/admin). Please restart app to run initial seeding.")
                    .result(result)
                    .build();
        }

        // Seed categories
        if (productCategoryRepository.count() == 0) {
            productCategoryRepository.save(ProductCategory.builder()
                    .name("Fast Food").slug("fast-food").description("Đồ ăn nhanh").build());
            productCategoryRepository.save(ProductCategory.builder()
                    .name("Drinks").slug("drinks").description("Thức uống").build());
        }

        // Seed stores
        if (storeRepository.count() == 0) {
            Store pizzaPlanet = storeRepository.save(Store.builder()
                    .ownerUserId(owner.getId())
                    .name("Pizza Planet")
                    .description("Pizza nóng giòn và nhiều topping")
                    .status(StoreStatus.ACTIVE)
                    .build());

            Store sushiHouse = storeRepository.save(Store.builder()
                    .ownerUserId(owner.getId())
                    .name("Sushi House")
                    .description("Sushi tươi ngon mỗi ngày")
                    .status(StoreStatus.ACTIVE)
                    .build());

            // Attach products
            ProductCategory fastFood = productCategoryRepository.findAll().stream().findFirst().orElse(null);
            ProductCategory drinks = productCategoryRepository.findAll().stream().skip(1).findFirst().orElse(fastFood);

            if (fastFood != null) {
                productRepository.save(Product.builder()
                        .category(fastFood)
                        .store(pizzaPlanet)
                        .sku("PZ-001")
                        .name("Pizza Margherita")
                        .description("Cà chua, phô mai mozzarella, lá basil")
                        .basePrice(new BigDecimal("120000"))
                        .currency("VND")
                        .quantityAvailable(50)
                        .build());

                productRepository.save(Product.builder()
                        .category(fastFood)
                        .store(pizzaPlanet)
                        .sku("PZ-002")
                        .name("Pizza Pepperoni")
                        .description("Thịt bò pepperoni, phô mai")
                        .basePrice(new BigDecimal("145000"))
                        .currency("VND")
                        .quantityAvailable(40)
                        .build());
            }

            if (drinks != null) {
                productRepository.save(Product.builder()
                        .category(drinks)
                        .store(sushiHouse)
                        .sku("SH-001")
                        .name("Sushi Salmon Set")
                        .description("Combo sushi cá hồi 8 miếng")
                        .basePrice(new BigDecimal("99000"))
                        .currency("VND")
                        .quantityAvailable(30)
                        .build());

                productRepository.save(Product.builder()
                        .category(drinks)
                        .store(sushiHouse)
                        .sku("DR-TEA-001")
                        .name("Trà xanh Nhật Bản")
                        .description("Trà xanh thơm mát")
                        .basePrice(new BigDecimal("25000"))
                        .currency("VND")
                        .quantityAvailable(100)
                        .build());
            }
        }

        result.put("stores", storeRepository.count());
        result.put("categories", productCategoryRepository.count());
        result.put("products", productRepository.count());

        return APIResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Seed completed")
                .result(result)
                .build();
    }
}
