package repository.user;

import entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserId(Long userId);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.user.id = :userId")
    void clearDefaultAddressForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(ua) FROM UserAddress ua WHERE ua.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
