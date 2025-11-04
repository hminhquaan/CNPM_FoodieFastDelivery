package service.payment;

import entity.Order;
import entity.PayoutBatch;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerService {

    /**
     * Tạo bản ghi công nợ khi đơn hàng được thanh toán thành công
     */
    void createLedgerEntryForOrder(Order order);

    /**
     * Lấy tổng số tiền cửa hàng chưa được thanh toán
     */
    BigDecimal getUnpaidAmountForStore(Long storeId);

    /**
     * Tạo lô thanh toán cho cửa hàng
     */
    PayoutBatch createPayoutBatchForStore(Long storeId);

    /**
     * Đánh dấu lô thanh toán đã hoàn tất
     */
    void markPayoutBatchAsPaid(Long payoutBatchId, String transactionCode);

    /**
     * Lấy danh sách các lô thanh toán của cửa hàng
     */
    List<PayoutBatch> getPayoutBatchesByStore(Long storeId);
}

