package repository.cart;

import entity.Cart;
import enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Find active cart for a specific user
     */
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);

    /**
     * Find cart with items loaded
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems ci LEFT JOIN FETCH ci.product WHERE c.id = :cartId")
    Optional<Cart> findByIdWithItems(@Param("cartId") Long cartId);

    /**
     * Find active cart for user with items loaded
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems ci LEFT JOIN FETCH ci.product " +
           "WHERE c.userId = :userId AND c.status = :status")
    Optional<Cart> findByUserIdAndStatusWithItems(@Param("userId") Long userId, @Param("status") CartStatus status);

    /**
     * Check if user has active cart
     */
    boolean existsByUserIdAndStatus(Long userId, CartStatus status);
}
