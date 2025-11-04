package dto.response.order;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemResponse {

    Long id;
    Long productId;
    String productName;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;
}
