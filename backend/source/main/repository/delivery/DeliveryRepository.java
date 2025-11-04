package repository.delivery;

import entity.Delivery;
import enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    List<Delivery> findByDroneId(Long droneId);

    List<Delivery> findByCurrentStatus(DeliveryStatus status);

    @Query("SELECT d FROM Delivery d WHERE d.droneId = :droneId AND d.currentStatus IN :statuses ORDER BY d.actualDepartureTime DESC")
    List<Delivery> findActiveDeliveriesByDrone(@Param("droneId") Long droneId, @Param("statuses") List<DeliveryStatus> statuses);

    @Query("SELECT d FROM Delivery d WHERE d.currentStatus = 'QUEUED' ORDER BY d.createdAt ASC")
    List<Delivery> findQueuedDeliveries();

    boolean existsByOrderId(Long orderId);
}

