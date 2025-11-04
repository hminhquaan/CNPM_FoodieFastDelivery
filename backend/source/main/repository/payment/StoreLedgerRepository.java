package repository.payment;

import entity.StoreLedger;
import enums.StoreLedgerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreLedgerRepository extends JpaRepository<StoreLedger, Long> {

    // Tìm ledger theo order
    Optional<StoreLedger> findByOrderId(Long orderId);
    List<StoreLedger> findByOrderIdIn(List<Long> orderIds);

    // Tìm tất cả ledger của store
    List<StoreLedger> findByStoreId(Long storeId);

    // Tìm ledger theo store và status
    List<StoreLedger> findByStoreIdAndStatus(Long storeId, StoreLedgerStatus status);

    // Tìm ledger theo payout batch
    List<StoreLedger> findByPayoutBatchId(Long payoutBatchId);

    // Đếm số ledger trong payout batch
    @Query("SELECT COUNT(sl) FROM StoreLedger sl WHERE sl.payoutBatchId = :payoutBatchId")
    int countByPayoutBatchId(@Param("payoutBatchId") Long payoutBatchId);

    // Tính tổng tiền theo store và status
    @Query("SELECT SUM(sl.netAmountOwed) FROM StoreLedger sl WHERE sl.storeId = :storeId AND sl.status = :status")
    BigDecimal sumNetAmountOwedByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") StoreLedgerStatus status);

    // Tìm các ledger UNPAID của store
    @Query("SELECT sl FROM StoreLedger sl WHERE sl.storeId = :storeId AND sl.status = 'UNPAID'")
    List<StoreLedger> findUnpaidByStoreId(@Param("storeId") Long storeId);
}

