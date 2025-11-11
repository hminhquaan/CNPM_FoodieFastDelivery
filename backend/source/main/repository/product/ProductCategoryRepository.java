package repository.product;

import entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    boolean existsBySlug(String slug);

    ProductCategory findFirstByNameIgnoreCase(String name);
}