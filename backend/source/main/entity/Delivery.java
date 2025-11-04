package entity;

import enums.DeliveryStatus;
import enums.ConfirmationMethod;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    Long orderId;

    @Column(name = "drone_id")
    Long droneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status",
            columnDefinition = "enum('QUEUED','ASSIGNED','LAUNCHED','ARRIVING','COMPLETED','FAILED','RETURNED') default 'QUEUED'")
    @Builder.Default
    DeliveryStatus currentStatus = DeliveryStatus.QUEUED;

    @Column(name = "pickup_store_id", nullable = false)
    Long pickupStoreId;

    @Column(name = "dropoff_address_snapshot", columnDefinition = "json")
    String dropoffAddressSnapshot;

    @Column(name = "actual_departure_time")
    LocalDateTime actualDepartureTime;

    @Column(name = "actual_arrival_time")
    LocalDateTime actualArrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_method",
            columnDefinition = "enum('GEOFENCE','OTP','QR')")
    ConfirmationMethod confirmationMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "delivery_ibfk_1"))
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drone_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "delivery_ibfk_2"))
    Drone drone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_store_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "delivery_ibfk_3"))
    Store pickupStore;

    @OneToOne(mappedBy = "delivery", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    FlightPlan flightPlan;

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
