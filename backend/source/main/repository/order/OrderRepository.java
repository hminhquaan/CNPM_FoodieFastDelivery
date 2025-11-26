package repository.order;

import entity.Order;
import enums.OrderStatus;
import enums.PaymentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    // All orders of a user, newest first
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByStoreId(Long storeId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    boolean existsByStoreIdAndStatusIn(Long storeId, List<OrderStatus> statuses);

    // Kitchen queue: orders of a store that are in relevant statuses
    @Query("SELECT o FROM Order o WHERE o.storeId = :storeId AND o.status IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findKitchenQueue(@Param("storeId") Long storeId,
                                 @Param("statuses") List<OrderStatus> statuses);
}