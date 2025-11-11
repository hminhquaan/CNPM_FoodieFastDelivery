package repository.delivery;

import entity.FlightPlanPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlightPlanPointRepository extends JpaRepository<FlightPlanPoint, Long> {
    List<FlightPlanPoint> findByFlightPlanIdOrderBySequenceNoAsc(Long flightPlanId);
}
