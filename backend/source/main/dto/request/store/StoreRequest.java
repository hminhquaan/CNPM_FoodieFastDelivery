package dto.request.store;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreRequest {

    @NotNull(message = "Owner user ID is required")
    Long ownerUserId;

    @NotBlank(message = "Store name is required")
    @Size(max = 200, message = "Store name must not exceed 200 characters")
    String name;

    String description;


    String bankAccountName;


    String bankAccountNumber;


    String bankName;


    String bankBranch;


    String payoutEmail;
}
