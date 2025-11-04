package entity;

import enums.OrderStatus;
import enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "store_id", nullable = false)
    Long storeId;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('CREATED','PENDING_PAYMENT','PAID','IN_DELIVERY','DELIVERED','CANCELLED','REFUNDED') default 'CREATED'")
    @Builder.Default
    OrderStatus status = OrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false,
            columnDefinition = "enum('PENDING','PAID','FAILED','REFUNDED') default 'PENDING'")
    @Builder.Default
    PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "total_item_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal totalItemAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 12, scale = 2)
    @Builder.Default
    BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    @Builder.Default
    BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_payable", nullable = false, precision = 12, scale = 2)
    BigDecimal totalPayable;

    @Column(name = "delivery_address_snapshot", columnDefinition = "json")
    String deliveryAddressSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "orders_ibfk_1"))
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "orders_ibfk_2"))
    Store store;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<OrderItem> orderItems;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    Delivery delivery;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<PaymentTransaction> paymentTransactions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
