package dto.response.payment;

import enums.PaymentProvider;
import enums.PaymentTransactionStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentResponse {

    Long id;
    Long orderId;
    PaymentProvider provider;
    BigDecimal amount;
    String currency;
    PaymentTransactionStatus status;
    String providerTransactionId;
    LocalDateTime createdAt;
    LocalDateTime completedAt;

    // For payment initialization
    String paymentUrl;
    String qrCode;
}

