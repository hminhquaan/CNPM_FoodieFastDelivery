package dto.request.User;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAddressCreationRequest {
    String label;
    String receiverName;
    String phone;
    String addressLine;
    String ward;
    String district;
    String city;
    String country;
    BigDecimal latitude;
    BigDecimal longitude;
    Boolean isDefault;
}
