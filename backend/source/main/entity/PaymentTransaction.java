package entity;

import enums.PaymentProvider;
import enums.PaymentMethod;
import enums.PaymentTransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transaction")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", nullable = false)
    Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", columnDefinition = "enum('MOMO','VNPAY','OTHER')")
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", columnDefinition = "enum('WALLET','QR','CARD')")
    PaymentMethod method;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('INIT','PENDING','SUCCESS','FAILED','CANCELLED') default 'INIT'")
    @Builder.Default
    PaymentTransactionStatus status = PaymentTransactionStatus.INIT;

    @Column(name = "provider_transaction_id", length = 100)
    String providerTransactionId;

    @Column(name = "vnp_txn_ref", length = 100)
    String vnpTxnRef;  // Mã tham chiếu unique cho VNPay

    @Column(name = "request_payload", columnDefinition = "json")
    String requestPayload;

    @Column(name = "response_payload", columnDefinition = "json")
    String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "payment_transaction_ibfk_1"))
    Order order;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
