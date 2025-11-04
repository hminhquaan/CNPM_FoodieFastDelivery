package entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(name = "user_address")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "label", length = 50)
    String label;

    @Column(name = "receiver_name", length = 150)
    String receiverName;

    @Column(name = "phone", length = 20)
    String phone;

    @Column(name = "address_line", length = 255)
    String addressLine;

    @Column(name = "ward", length = 100)
    String ward;

    @Column(name = "district", length = 100)
    String district;

    @Column(name = "city", length = 100)
    String city;

    @Column(name = "country", length = 100)
    String country;

    @Column(name = "latitude", precision = 10, scale = 7)
    BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    Boolean isDefault = false;
}
