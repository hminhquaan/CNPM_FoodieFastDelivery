import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.Order;
import enums.OrderStatus;
import enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import repository.order.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AppMain.class)
@ActiveProfiles("dev")
@AutoConfigureMockMvc
public class PaymentInitIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void initPayment_shouldReturnPaymentUrl() throws Exception {
        // Arrange: create a minimal order with amount >= 1000 VND
        String orderCode = ("ORD" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20).toUpperCase();
        Order order = Order.builder()
                .userId(11L)
                .storeId(11L)
                .orderCode(orderCode)
                .status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalItemAmount(new BigDecimal("120000"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalPayable(new BigDecimal("120000"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);

        String body = "{" +
                "\"orderId\":" + order.getId() + "," +
                "\"provider\":\"VNPAY\"," +
                "\"method\":\"QR\"," +
                "\"bankCode\":\"\"" +
                "}";

        // Act
        String response = mockMvc.perform(post("/api/v1/payments/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Assert
        JsonNode root = objectMapper.readTree(response);
        assertThat(root.get("code").asInt()).isEqualTo(200);
        assertThat(root.get("result").get("paymentUrl").asText()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        System.out.println("Payment URL => " + root.get("result").get("paymentUrl").asText());
    }
}
