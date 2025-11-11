package repository.drone;

import entity.DroneStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroneStationRepository extends JpaRepository<DroneStation, Long> {
}
