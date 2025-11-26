package repository.product;


import entity.Product;
import enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {

    List<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status);
    
    // Used for admin/maintenance to find ALL products in a category regardless of status
    List<Product> findByCategoryId(Long categoryId);
    
    List<Product> findByStoreIdAndStatus(Long storeId, ProductStatus status);

    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndStatus(String name, String description, ProductStatus status);
    
    List<Product> findAllByStatus(ProductStatus status);
}
