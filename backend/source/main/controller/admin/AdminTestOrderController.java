package controller.admin;

import dto.response.API.APIResponse;
import entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.order.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
public class AdminTestOrderController {

    private final OrderRepository orderRepository;

    @PostMapping("/create-order")
    public ResponseEntity<APIResponse<Map<String, Object>>> createTestOrder() {
        // Create a simple test order for seeded user and store
        String orderCode = ("ORD" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20).toUpperCase();

        Order order = Order.builder()
                .userId(11L)
                .storeId(11L)
                .orderCode(orderCode)
                .totalItemAmount(new BigDecimal("120000"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalPayable(new BigDecimal("120000"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        order = orderRepository.save(order);

        Map<String, Object> payload = Map.of(
                "orderId", order.getId(),
                "orderCode", order.getOrderCode(),
                "totalPayable", order.getTotalPayable()
        );

        return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Test order created")
                .result(payload)
                .build());
    }
}
