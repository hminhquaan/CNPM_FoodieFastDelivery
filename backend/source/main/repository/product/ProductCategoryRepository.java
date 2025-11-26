package repository.product;

import entity.ProductCategory;
import enums.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    boolean existsBySlug(String slug);

    ProductCategory findFirstByNameIgnoreCase(String name);

    List<ProductCategory> findAllByStatus(CategoryStatus status);
}