package dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneRegisterRequest {
    
    @NotBlank(message = "Drone code is required")
    @Size(max = 50, message = "Drone code must not exceed 50 characters")
    private String code;  // Unique drone ID (e.g., "DRONE001")
    
    @NotBlank(message = "Model is required")
    @Size(max = 200, message = "Model name must not exceed 200 characters")
    private String model; // Phone model (e.g., "iPhone 13")
    
    @NotNull(message = "Max payload is required")
    @Min(value = 0, message = "Max payload must be positive")
    private Integer maxPayloadGram; // Max payload (default 2000g)
    
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;  // Initial GPS location
    
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude; // Initial GPS location
}
