package service.admin;

import entity.Product;
import entity.ProductCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import repository.product.ProductCategoryRepository;
import repository.product.ProductRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.admin", name = "normalize-on-start", havingValue = "true", matchIfMissing = false)
@Slf4j
public class CategoryNormalizationRunner implements org.springframework.boot.ApplicationRunner {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Value("${app.admin.normalize-on-start:false}")
    private boolean normalizeOnStart;

    public CategoryNormalizationRunner(ProductCategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!normalizeOnStart) {
            log.info("Category normalization skipped (app.admin.normalize-on-start = false)");
            return;
        }
        log.info("Starting automatic category normalization...");

        Map<String, String> canonical = Map.of(
                "Pizza", "pizza",
                "Sushi", "sushi",
                "Bánh mì", "banh-mi",
                "Cà phê", "ca-phe",
                "Tráng miệng", "trang-mieng"
        );

    List<ProductCategory> all = categoryRepository.findAll();
    Map<String, List<ProductCategory>> byName = all.stream()
                .collect(Collectors.groupingBy(c -> c.getName().trim(), Collectors.toList()));

        int reassignedProducts = 0;
        int deletedCategories = 0;
        int slugUpdates = 0;

        for (Map.Entry<String, List<ProductCategory>> e : byName.entrySet()) {
            List<ProductCategory> list = e.getValue();
            if (list.isEmpty()) continue;
            // Prefer a category that already has the desired slug as primary to avoid unique constraint conflicts
            String name = list.get(0).getName();
            String desiredSlug = canonical.getOrDefault(name, name.toLowerCase().replace(' ', '-'));

            ProductCategory primary = list.stream()
                    .filter(c -> desiredSlug.equalsIgnoreCase(Objects.toString(c.getSlug(), "")))
                    .findFirst()
                    .orElse(null);
            if (primary == null) {
                list.sort(Comparator.comparing(ProductCategory::getId));
                primary = list.get(0);
            }

            // Ensure primary has the desired slug; if another duplicate holds it, we skip setting here
            if (!desiredSlug.equalsIgnoreCase(Objects.toString(primary.getSlug(), ""))) {
                primary.setSlug(desiredSlug);
                categoryRepository.save(primary);
                slugUpdates++;
            }
            for (int i = 1; i < list.size(); i++) {
                ProductCategory dup = list.get(i);
                if (dup.getId().equals(primary.getId())) {
                    continue;
                }
                List<Product> products = productRepository.findByCategoryId(dup.getId());
                for (Product p : products) {
                    p.setCategory(primary);
                    reassignedProducts++;
                }
                productRepository.saveAll(products);
                categoryRepository.delete(dup);
                deletedCategories++;
            }
        }
        log.info("Category normalization complete. Reassigned products={}, Deleted categories={}, Slug updates={}", reassignedProducts, deletedCategories, slugUpdates);
    }
}
