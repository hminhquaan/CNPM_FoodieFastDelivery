package repository.delivery;

import entity.FlightPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlightPlanRepository extends JpaRepository<FlightPlan, Long> {
    Optional<FlightPlan> findByDeliveryId(Long deliveryId);
}
