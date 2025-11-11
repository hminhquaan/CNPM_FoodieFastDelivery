package controller.admin;

import entity.Product;
import entity.ProductCategory;
import entity.Store;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import repository.product.ProductCategoryRepository;
import repository.product.ProductRepository;
import repository.store.StoreRepository;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminDiagnosticsController {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public AdminDiagnosticsController(StoreRepository storeRepository,
                                      ProductRepository productRepository,
                                      ProductCategoryRepository categoryRepository) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/diagnostics/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Store> stores = storeRepository.findAll();
        List<Product> products = productRepository.findAll();
        List<ProductCategory> categories = categoryRepository.findAll();

        // Products per store
        Map<Long, Long> productsPerStore = products.stream()
                .collect(Collectors.groupingBy(p -> p.getStore().getId(), Collectors.counting()));
        List<Map<String, Object>> storeStats = new ArrayList<>();
        for (Store s : stores) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeId", s.getId());
            row.put("name", s.getName());
            row.put("products", productsPerStore.getOrDefault(s.getId(), 0L));
            storeStats.add(row);
        }

        // Products per category
        Map<Long, Long> productsPerCategory = products.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getId(), Collectors.counting()));
        List<Map<String, Object>> categoryStats = new ArrayList<>();
        for (ProductCategory c : categories) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", c.getId());
            row.put("name", c.getName());
            row.put("slug", c.getSlug());
            row.put("products", productsPerCategory.getOrDefault(c.getId(), 0L));
            categoryStats.add(row);
        }

        result.put("stores", storeStats);
        result.put("categories", categoryStats);
        result.put("totalProducts", products.size());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/categories/normalize")
    @Transactional
    public ResponseEntity<Map<String, Object>> normalizeCategories(@RequestParam(defaultValue = "true") boolean dryRun) {
        // Canonical names -> slugs
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

        List<Map<String, Object>> actions = new ArrayList<>();

        for (Map.Entry<String, List<ProductCategory>> e : byName.entrySet()) {
            String name = e.getKey();
            List<ProductCategory> list = e.getValue();
            if (list.size() <= 1) {
                // just ensure slug if missing
                ProductCategory only = list.get(0);
                String desired = canonical.get(name);
                if (desired != null && (only.getSlug() == null || only.getSlug().isBlank())) {
                    // avoid unique constraint if another category already holds the desired slug
                    boolean slugInUse = categoryRepository.existsBySlug(desired);
                    if (slugInUse && !desired.equalsIgnoreCase(Objects.toString(only.getSlug(), ""))) {
                        Map<String, Object> skip = new LinkedHashMap<>();
                        skip.put("action", "skip-set-slug-conflict");
                        skip.put("categoryId", only.getId());
                        skip.put("slug", desired);
                        actions.add(skip);
                    } else {
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("action", "set-slug");
                    act.put("categoryId", only.getId());
                    act.put("slug", desired);
                    actions.add(act);
                    if (!dryRun) {
                        only.setSlug(desired);
                        categoryRepository.save(only);
                    }
                    }
                }
                continue;
            }

            // multiple with same name
            list.sort(Comparator.comparing(ProductCategory::getId));
            String desiredSlug = canonical.getOrDefault(name, name.toLowerCase().replace(' ', '-'));

            // Prefer a category that already has the desired slug to avoid unique constraint collisions
            ProductCategory target = list.stream()
                    .filter(c -> desiredSlug.equalsIgnoreCase(Objects.toString(c.getSlug(), "")))
                    .findFirst()
                    .orElse(list.get(0));

            for (int i = 1; i < list.size(); i++) {
                ProductCategory dup = list.get(i);
                if (dup.getId().equals(target.getId())) {
                    continue;
                }
                // move products
                List<Product> toMove = productRepository.findByCategoryId(dup.getId());
                if (!toMove.isEmpty()) {
                    Map<String, Object> move = new LinkedHashMap<>();
                    move.put("action", "reassign-products");
                    move.put("fromCategoryId", dup.getId());
                    move.put("toCategoryId", target.getId());
                    move.put("count", toMove.size());
                    actions.add(move);
                    if (!dryRun) {
                        for (Product p : toMove) {
                            p.setCategory(target);
                        }
                        productRepository.saveAll(toMove);
                    }
                }
                // delete duplicate category
                Map<String, Object> del = new LinkedHashMap<>();
                del.put("action", "delete-category");
                del.put("categoryId", dup.getId());
                del.put("name", dup.getName());
                actions.add(del);
                if (!dryRun) {
                    categoryRepository.delete(dup);
                }
            }

            // After consolidation, if target doesn't have desired slug and it's safe (no external holder), set it
            String targetSlug = Objects.toString(target.getSlug(), "");
            boolean targetHasDesired = desiredSlug.equalsIgnoreCase(targetSlug);
            if (!targetHasDesired) {
                boolean slugInUse = categoryRepository.existsBySlug(desiredSlug);
                boolean desiredHeldInGroup = list.stream().anyMatch(c -> desiredSlug.equalsIgnoreCase(Objects.toString(c.getSlug(), "")));
                // If slug is in use outside the group, skip; if it's not in use or was held by a deleted duplicate, it's safe now
                if (!slugInUse || desiredHeldInGroup) {
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("action", "set-slug");
                    act.put("categoryId", target.getId());
                    act.put("slug", desiredSlug);
                    actions.add(act);
                    if (!dryRun) {
                        target.setSlug(desiredSlug);
                        categoryRepository.save(target);
                    }
                } else {
                    Map<String, Object> skip = new LinkedHashMap<>();
                    skip.put("action", "skip-set-slug-conflict");
                    skip.put("categoryId", target.getId());
                    skip.put("slug", desiredSlug);
                    actions.add(skip);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("actions", actions);
        return ResponseEntity.ok(result);
    }
}
