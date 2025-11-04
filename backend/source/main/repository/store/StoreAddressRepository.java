package repository.store;

import entity.StoreAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreAddressRepository extends JpaRepository<StoreAddress,Long> {

    List<StoreAddress> findByStore_Id(Long storeId);
    @Query(value = "CALL GetActiveStoresWithinRadius(:latitude, :longitude)", nativeQuery = true)
    List<StoreAddress> findStoresWithinFlightCorridor(@Param("latitude") Double latitude,
                                                       @Param("longitude") Double longitude);
}
