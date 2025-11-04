import entity.Roles;
import entity.User;
import entity.Store;
import entity.ProductCategory;
import entity.Product;
import enums.UserStatus;
import enums.StoreStatus;
import repository.store.StoreRepository;
import repository.product.ProductCategoryRepository;
import repository.product.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import repository.user.RoleRepository;
import repository.user.UserRepository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@SpringBootApplication(
    scanBasePackages = {
        "controller",
        "service",
        "repository",
        "mapper",
        "config",
        "entity",
        "dto",
        "enums",
        "exception"
    }
)
@EnableJpaRepositories(basePackages = {"repository"})
@EntityScan(basePackages = {"entity"})
public class AppMain {
    public static void main(String[] args) {
        SpringApplication.run(AppMain.class, args);
    }

    @Bean
    CommandLineRunner seedDefaultAccounts(RoleRepository roleRepository,
                                          UserRepository userRepository,
                                          PasswordEncoder passwordEncoder,
                                          StoreRepository storeRepository,
                                          ProductCategoryRepository productCategoryRepository,
                                          ProductRepository productRepository) {
        return args -> {
            // Ensure roles exist
            Roles adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(Roles.builder().name("ADMIN").build()));
        Roles userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(Roles.builder().name("USER").build()));

            // Seed admin account
            if (!userRepository.existsByUsername("admin")) {
                Set<Roles> roles = new HashSet<>();
                roles.add(adminRole);
                User admin = User.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .fullName("System Administrator")
                        .phone("0900000001")
                        .status(UserStatus.ACTIVE)
                        .roles(roles)
                        .build();
                userRepository.save(admin);
            }

            // Seed normal user account
            if (!userRepository.existsByUsername("user")) {
                Set<Roles> roles = new HashSet<>();
                roles.add(userRole);
                User user = User.builder()
                        .username("user")
                        .email("user@example.com")
                        .passwordHash(passwordEncoder.encode("user123"))
                        .fullName("Demo User")
                        .phone("0900000002")
                        .status(UserStatus.ACTIVE)
                        .roles(roles)
                        .build();
                userRepository.save(user);
            }

        // Seed demo categories if none exist
        if (productCategoryRepository.count() == 0) {
        ProductCategory fastFood = ProductCategory.builder()
            .name("Fast Food")
            .slug("fast-food")
            .description("Đồ ăn nhanh")
            .build();
        ProductCategory drinks = ProductCategory.builder()
            .name("Drinks")
            .slug("drinks")
            .description("Thức uống")
            .build();
        productCategoryRepository.save(fastFood);
        productCategoryRepository.save(drinks);
        }

        // Fetch a user to own demo stores
        User demoOwner = userRepository.findByUsername("user").orElseGet(() ->
            userRepository.findByUsername("admin").orElse(null)
        );

        // Seed demo stores and products if none exist
        if (demoOwner != null && storeRepository.count() == 0) {
        // Create stores
        Store pizzaPlanet = Store.builder()
            .ownerUserId(demoOwner.getId())
            .name("Pizza Planet")
            .description("Pizza nóng giòn và nhiều topping")
            .storeStatus(StoreStatus.ACTIVE)
            .build();
        Store sushiHouse = Store.builder()
            .ownerUserId(demoOwner.getId())
            .name("Sushi House")
            .description("Sushi tươi ngon mỗi ngày")
            .storeStatus(StoreStatus.ACTIVE)
            .build();

        pizzaPlanet = storeRepository.save(pizzaPlanet);
        sushiHouse = storeRepository.save(sushiHouse);

        // Load categories
        ProductCategory fastFood = productCategoryRepository.findAll().stream().findFirst().orElse(null);
        ProductCategory drinks = productCategoryRepository.findAll().stream().skip(1).findFirst().orElse(fastFood);

        // Create products
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
        };
    }
}
