package dto.request.delivery;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignDroneRequest {

    @NotNull(message = "Delivery ID is required")
    Long deliveryId;

    @NotNull(message = "Drone ID is required")
    Long droneId;
}

