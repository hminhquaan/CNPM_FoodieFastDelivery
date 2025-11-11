package dto.request.store;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO for updating store payment/payout information.
 * All fields are optional; only non-null values will be applied.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorePaymentRequest {
    String bankAccountName;
    String bankAccountNumber;
    String bankName;
    String bankBranch;
    String payoutEmail;
}
