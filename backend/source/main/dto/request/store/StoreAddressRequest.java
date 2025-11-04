package dto.request.store;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreAddressRequest {

    String addressLine;


    String city;


    String district;


    String ward;


    String country;

    Double latitude;

    Double longitude;
}
