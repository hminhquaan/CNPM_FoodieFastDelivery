package repository.payment;

import entity.PayoutBatch;
import enums.PayoutBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, Long> {

    // Tìm tất cả payout batch của một store (sắp xếp theo ngày tạo)
    List<PayoutBatch> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    // Tìm tất cả payout batch của một store (không sắp xếp)
    List<PayoutBatch> findByStoreId(Long storeId);

    // Tìm payout batch theo status
    List<PayoutBatch> findByStatusOrderByCreatedAtDesc(PayoutBatchStatus status);

    // Tìm payout batch theo store và status
    List<PayoutBatch> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, PayoutBatchStatus status);

    // Tìm payout batch theo transaction code
    Optional<PayoutBatch> findByTransactionCode(String transactionCode);

    // Tìm payout batch trong khoảng thời gian
    @Query("SELECT pb FROM PayoutBatch pb WHERE pb.createdAt BETWEEN :startDate AND :endDate ORDER BY pb.createdAt DESC")
    List<PayoutBatch> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Tổng tiền đã chi trả cho store
    @Query("SELECT COALESCE(SUM(pb.totalPayoutAmount), 0) FROM PayoutBatch pb WHERE pb.storeId = :storeId AND pb.status = 'PAID'")
    Double getTotalPaidAmountByStore(@Param("storeId") Long storeId);

    // Đếm số lượng payout batch theo status
    Long countByStatus(PayoutBatchStatus status);
}

