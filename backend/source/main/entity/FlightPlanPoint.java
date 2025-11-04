package entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flight_plan_point",
        indexes = @Index(name = "idx_fpp_flight_plan", columnList = "flight_plan_id"))
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightPlanPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "flight_plan_id", nullable = false)
    Long flightPlanId;

    @Column(name = "sequence_no", nullable = false)
    Integer sequenceNo;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    BigDecimal longitude;

    @Column(name = "altitude_m", precision = 6, scale = 2)
    BigDecimal altitudeM;

    @Column(name = "eta_time")
    LocalDateTime etaTime;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_plan_id", referencedColumnName = "id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_fpp_flight_plan"))
    FlightPlan flightPlan;
}
