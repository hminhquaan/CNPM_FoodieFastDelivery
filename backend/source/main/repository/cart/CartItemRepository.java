package repository.cart;

import entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Find cart item by cart and product
     */
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * Find all cart items for a specific cart
     */
    List<CartItem> findByCartId(Long cartId);

    /**
     * Find cart items with product details
     */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.cartId = :cartId")
    List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);

    /**
     * Delete all cart items for a specific cart
     */
    @Modifying
    @Transactional
    void deleteByCartId(Long cartId);

    /**
     * Count items in cart
     */
    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cartId = :cartId")
    Long countByCartId(@Param("cartId") Long cartId);
}
