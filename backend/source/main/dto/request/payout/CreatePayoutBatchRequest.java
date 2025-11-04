package dto.request.payout;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePayoutBatchRequest {

    @NotNull(message = "Store ID is required")
    private Long storeId;

    private String notes;
}

