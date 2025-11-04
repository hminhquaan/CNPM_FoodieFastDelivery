package entity;

import enums.DroneStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "drone")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Drone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    String code;

    @Column(name = "model", length = 200)
    String model;

    @Column(name = "max_payload_gram")
    Integer maxPayloadGram;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            columnDefinition = "enum('AVAILABLE','IN_FLIGHT','CHARGING','MAINTENANCE','OFFLINE') default 'AVAILABLE'")
    @Builder.Default
    DroneStatus status = DroneStatus.AVAILABLE;

    @Column(name = "current_battery_percent")
    Integer currentBatteryPercent;

    @Column(name = "last_latitude", precision = 10, scale = 7)
    BigDecimal lastLatitude;

    @Column(name = "last_longitude", precision = 10, scale = 7)
    BigDecimal lastLongitude;

    @Column(name = "last_telemetry_at")
    LocalDateTime lastTelemetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @OneToMany(mappedBy = "drone", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<Delivery> deliveries;

    @OneToMany(mappedBy = "drone", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<FlightPlan> flightPlans;

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
