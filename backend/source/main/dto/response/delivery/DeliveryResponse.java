package dto.response.delivery;

import enums.ConfirmationMethod;
import enums.DeliveryStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeliveryResponse {

    Long id;
    Long orderId;
    String orderCode;
    Long droneId;
    String droneCode;
    DeliveryStatus currentStatus;
    Long pickupStoreId;
    String pickupStoreName;
    String dropoffAddressSnapshot;
    LocalDateTime actualDepartureTime;
    LocalDateTime actualArrivalTime;
    LocalDateTime estimatedArrivalTime;
    ConfirmationMethod confirmationMethod;
    Integer estimatedFlightTimeMinutes;
    Double distanceKm;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

