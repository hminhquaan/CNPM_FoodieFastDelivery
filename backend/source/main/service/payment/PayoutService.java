package service.payment;

import dto.request.payout.CreatePayoutBatchRequest;
import dto.response.payout.PayoutBatchResponse;
import dto.response.payout.PayoutSummaryResponse;
import enums.PayoutBatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface PayoutService {

    /**
     * Tạo payout batch mới cho store (tổng hợp từ các ledger UNPAID)
     */
    PayoutBatchResponse createPayoutBatch(CreatePayoutBatchRequest request);

    /**
     * Lấy tổng hợp thông tin payout của store
     */
    PayoutSummaryResponse getPayoutSummary(Long storeId);

    /**
     * Xử lý thanh toán cho một payout batch
     */
    PayoutBatchResponse processPayoutBatch(Long batchId);

    /**
     * Đánh dấu payout batch đã thanh toán thành công
     */
    PayoutBatchResponse markAsPaid(Long batchId, String transactionCode);

    /**
     * Đánh dấu payout batch thất bại
     */
    PayoutBatchResponse markAsFailed(Long batchId, String reason);

    /**
     * Lấy chi tiết một payout batch
     */
    PayoutBatchResponse getPayoutBatchById(Long batchId);

    /**
     * Lấy danh sách payout batch của store
     */
    List<PayoutBatchResponse> getPayoutBatchesByStore(Long storeId);

    /**
     * Lấy danh sách payout batch theo status
     */
    List<PayoutBatchResponse> getPayoutBatchesByStatus(PayoutBatchStatus status);

    /**
     * Lấy danh sách tất cả payout batch
     */
    List<PayoutBatchResponse> getAllPayoutBatches();

    /**
     * Lấy danh sách payout batch theo khoảng thời gian
     */
    List<PayoutBatchResponse> getPayoutBatchesByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Retry thanh toán cho payout batch thất bại
     */
    PayoutBatchResponse retryPayoutBatch(Long batchId);
}

