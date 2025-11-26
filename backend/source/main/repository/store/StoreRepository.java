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
    
    List<Store> findByStoreStatus(StoreStatus status);

    List<Store> findByNameContainingIgnoreCaseAndStoreStatus(String name, StoreStatus status);
}
