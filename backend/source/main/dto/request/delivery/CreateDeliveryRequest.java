package dto.request.delivery;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateDeliveryRequest {

    @NotNull(message = "Order ID is required")
    Long orderId;

    @NotNull(message = "Pickup store ID is required")
    Long pickupStoreId;

    String dropoffAddressSnapshot;
}

