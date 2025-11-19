package dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneUpdateRequest {

    @Size(max = 200, message = "Model name must not exceed 200 characters")
    private String model;

    @Min(value = 0, message = "Max payload must be positive")
    private Integer maxPayloadGram;
}
