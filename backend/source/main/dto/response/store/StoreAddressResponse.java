package dto.response.store;

import lombok.*;

// Response
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreAddressResponse {
    private Long id;
    private String addressLine;
    private String city;
    private String district;
    private String ward;
    private Double latitude;
    private Double longitude;
    private Long storeId;
    private Double Distanct;
}
