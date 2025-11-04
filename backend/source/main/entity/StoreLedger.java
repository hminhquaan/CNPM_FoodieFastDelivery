package entity;

import enums.StoreLedgerStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_ledger")
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class StoreLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @Column(name = "store_id", nullable = false)
    Long storeId;

    @Column(name = "order_id", nullable = false, unique = true)
    Long orderId;

    @Column(name = "total_order_amount", nullable = false, precision = 15, scale = 2)
    BigDecimal totalOrderAmount;

    @Column(name = "app_commission_amount", nullable = false, precision = 15, scale = 2)
    BigDecimal appCommissionAmount;

    @Column(name = "payment_gateway_fee", nullable = false, precision = 15, scale = 2)
    BigDecimal paymentGatewayFee;

    @Column(name = "net_amount_owed", nullable = false, precision = 15, scale = 2)
    BigDecimal netAmountOwed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    StoreLedgerStatus status;

    @Column(name = "payout_batch_id")
    Long payoutBatchId;

    @Column(name = "created_at", updatable = false, insertable = false)
    LocalDateTime createdAt;
}

