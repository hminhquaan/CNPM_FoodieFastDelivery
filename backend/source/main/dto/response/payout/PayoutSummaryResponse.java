package dto.response.payout;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutSummaryResponse {

    private Long storeId;
    private String storeName;

    // Tổng hợp doanh thu
    private BigDecimal totalRevenue;           // Tổng doanh thu
    private BigDecimal totalCommission;        // Tổng hoa hồng app
    private BigDecimal totalGatewayFee;        // Tổng phí gateway
    private BigDecimal totalNetAmount;         // Tổng tiền store nhận

    // Thông tin chi trả
    private BigDecimal totalPaid;              // Đã chi trả
    private BigDecimal totalPending;           // Chờ chi trả
    private BigDecimal availableForPayout;     // Có thể chi trả ngay

    // Số lượng
    private Integer unpaidLedgerCount;         // Số ledger chưa thanh toán
    private Integer totalOrderCount;           // Tổng số đơn hàng

    // Thông tin ngân hàng
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private String payoutEmail;
}

