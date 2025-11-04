package dto.response.payout;

import enums.PayoutBatchStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutBatchResponse {

    private Long id;
    private Long storeId;
    private String storeName;
    private BigDecimal totalPayoutAmount;
    private PayoutBatchStatus status;
    private String transactionCode;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    // Thông tin bổ sung
    private Integer ledgerCount; // Số lượng ledger entries
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
}

