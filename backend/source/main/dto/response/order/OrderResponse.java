package dto.response.order;

import enums.OrderStatus;
import enums.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderResponse {

    Long id;
    String orderCode;
    Long userId;
    Long storeId;
    String storeName;

    OrderStatus status;
    PaymentStatus paymentStatus;

    BigDecimal totalItemAmount;
    BigDecimal discountAmount;
    BigDecimal shippingFee;
    BigDecimal taxAmount;
    BigDecimal totalPayable;

    String deliveryAddressSnapshot;

    List<OrderItemResponse> items;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

