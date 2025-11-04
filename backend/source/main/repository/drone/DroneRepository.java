package repository.drone;

import entity.Drone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DroneRepository extends JpaRepository<Drone, Long> {
    
    Optional<Drone> findByCode(String code);
    
    boolean existsByCode(String code);
}
