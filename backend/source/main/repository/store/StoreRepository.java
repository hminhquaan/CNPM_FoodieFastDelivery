package repository.store;

import entity.Store;
import enums.StoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store,Long> {
    
    List<Store> findByOwnerUserId(Long ownerUserId);
    
    List<Store> findByNameContainingIgnoreCase(String name);
    
    List<Store> findByStatus(StoreStatus status);

    List<Store> findByNameContainingIgnoreCaseAndStatus(String name, StoreStatus status);
}
