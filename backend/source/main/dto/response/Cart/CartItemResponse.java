package dto.response.Cart;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItemResponse {

    Long id;
    Long productId;
    String productName;
    String productDescription;
    String productImageUrl;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;
    LocalDateTime createdAt;
}
