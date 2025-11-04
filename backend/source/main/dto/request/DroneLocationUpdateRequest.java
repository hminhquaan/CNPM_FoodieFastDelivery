package dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneLocationUpdateRequest {
    
    private BigDecimal latitude;
    
    private BigDecimal longitude;
    
    private Integer batteryPercent; // Optional
}
