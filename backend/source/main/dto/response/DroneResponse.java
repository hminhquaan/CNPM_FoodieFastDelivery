package dto.response;

import enums.DroneStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneResponse {
    
    private Long id;
    
    private String code;
    
    private String model;
    
    private Integer maxPayloadGram;
    
    private DroneStatus status;
    
    private Integer currentBatteryPercent;
    
    private BigDecimal lastLatitude;
    
    private BigDecimal lastLongitude;
    
    private LocalDateTime lastTelemetryAt;
}

