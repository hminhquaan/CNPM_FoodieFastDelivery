package dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneStationUpdateRequest {
    @Size(max = 200)
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double radiusKm;
}
