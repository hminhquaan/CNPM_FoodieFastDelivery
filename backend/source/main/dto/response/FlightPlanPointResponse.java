package dto.response;

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
public class FlightPlanPointResponse {
    Long id;
    Integer sequenceNo;
    BigDecimal latitude;
    BigDecimal longitude;
    BigDecimal altitudeM;
    LocalDateTime etaTime;
}
