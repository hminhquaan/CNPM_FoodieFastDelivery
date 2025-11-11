package entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_address")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    Store store;

    @Column(name = "address_line", nullable = false)
    String addressLine;

    @Column(name = "ward", length = 255)
    String ward;

    @Column(name = "district", length = 255)
    String district;

    @Column(name = "city", length = 255)
    String city;

    @Column(name = "country", length = 255)
    String country;

    @Column(name = "latitude")
    Double latitude;

    @Column(name = "longitude")
    Double longitude;

    @Column(name = "flight_corridor_radius")
    Double flightCorridorRadius; // Bán kính hành lang bay an toàn (km)

    @Column(name = "updated_at",updatable = false, insertable = false)
    LocalDateTime updatedAt;

    @Column(name = "created_at",updatable = false, insertable = false)
    LocalDateTime createdAt;
}
