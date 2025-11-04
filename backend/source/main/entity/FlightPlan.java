package entity;

import enums.FlightPlanStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flight_plan",
        uniqueConstraints = @UniqueConstraint(name = "uk_flight_plan_delivery", columnNames = {"delivery_id"}))
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "delivery_id", nullable = false, unique = true)
    Long deliveryId;

    @Column(name = "drone_id", nullable = false)
    Long droneId;

    @Column(name = "planned_departure_time", nullable = false)
    LocalDateTime plannedDepartureTime;

    @Column(name = "planned_arrival_time")
    LocalDateTime plannedArrivalTime;

    @Column(name = "route_summary", columnDefinition = "text")
    String routeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('PLANNED','IN_FLIGHT','COMPLETED','ABORTED') default 'PLANNED'")
    @Builder.Default
    FlightPlanStatus status = FlightPlanStatus.PLANNED;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_flight_plan_delivery"))
    Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drone_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_flight_plan_drone"))
    Drone drone;

    @OneToMany(mappedBy = "flightPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<FlightPlanPoint> flightPlanPoints;

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
