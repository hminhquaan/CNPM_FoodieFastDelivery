package controller.order;

import dto.request.order.UpdateOrderStatusRequest;
import dto.response.API.APIResponse;
import dto.response.order.OrderResponse;
import service.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * Create new orders from cart (Bước 1 trong sơ đồ)
     * Tạo nhiều đơn hàng, mỗi đơn cho một cửa hàng
     * Lấy userId từ token đăng nhập
     */
    @PostMapping
    public ResponseEntity<APIResponse<List<OrderResponse>>> createOrdersFromCart() {

        // Lấy username từ authentication context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.info("Creating orders from cart for authenticated user: {}", username);

        List<OrderResponse> responses = orderService.createOrdersFromCart(username);

        return ResponseEntity.ok(APIResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Orders created successfully from cart")
                .result(responses)
                .build());
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<APIResponse<OrderResponse>> getOrderById(@PathVariable Long orderId) {
        log.info("Getting order by ID: {}", orderId);

        OrderResponse response = orderService.getOrderById(orderId);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order retrieved successfully")
                .result(response)
                .build());
    }

        /**
         * Get all orders (admin)
         */
        @GetMapping
        public ResponseEntity<APIResponse<List<OrderResponse>>> getAllOrders() {
                log.info("Getting all orders");

                List<OrderResponse> responses = orderService.getAllOrders();

                return ResponseEntity.ok(APIResponse.<List<OrderResponse>>builder()
                                .code(200)
                                .message("Orders retrieved successfully")
                                .result(responses)
                                .build());
        }

    /**
     * Get order by order code
     */
    @GetMapping("/code/{orderCode}")
    public ResponseEntity<APIResponse<OrderResponse>> getOrderByCode(@PathVariable String orderCode) {
        log.info("Getting order by code: {}", orderCode);

        OrderResponse response = orderService.getOrderByCode(orderCode);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Get all orders by user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<APIResponse<List<OrderResponse>>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("Getting orders for user: {}", userId);

        List<OrderResponse> responses = orderService.getOrdersByUserId(userId);

        return ResponseEntity.ok(APIResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Orders retrieved successfully")
                .result(responses)
                .build());
    }

    /**
     * Get all orders by store
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<APIResponse<List<OrderResponse>>> getOrdersByStoreId(@PathVariable Long storeId) {
        log.info("Getting orders for store: {}", storeId);

        List<OrderResponse> responses = orderService.getOrdersByStoreId(storeId);

        return ResponseEntity.ok(APIResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Orders retrieved successfully")
                .result(responses)
                .build());
    }

    /**
     * Cancel order
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<APIResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        log.info("Cancelling order: {}", orderId);

        orderService.cancelOrder(orderId);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .code(200)
                .message("Order cancelled successfully")
                .build());
    }

    /**
     * Update order status (chỉ cho orders đã thanh toán)
     * PUT /api/v1/orders/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<APIResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.info("Updating order {} status to: {}", orderId, request.getStatus());

        OrderResponse response = orderService.updateOrderStatus(orderId, request);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order status updated successfully")
                .result(response)
                .build());
    }

    /**
     * Store chấp nhận đơn hàng (tạo ledger entry tự động)
     * POST /api/v1/orders/{orderId}/accept
     */
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<APIResponse<OrderResponse>> acceptOrder(@PathVariable Long orderId) {
        log.info("Store accepting order: {}", orderId);

        OrderResponse response = orderService.acceptOrder(orderId);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order accepted successfully and ledger entry created")
                .result(response)
                .build());
    }

    /**
     * Store từ chối đơn hàng
     * POST /api/v1/orders/{orderId}/reject
     */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<APIResponse<OrderResponse>> rejectOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason) {
        log.info("Store rejecting order: {}", orderId);

        OrderResponse response = orderService.rejectOrder(orderId, reason);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order rejected successfully")
                .result(response)
                .build());
    }

    /**
     * Chuyển đơn hàng sang trạng thái đang giao
     * POST /api/v1/orders/{orderId}/mark-in-delivery
     */
    @PostMapping("/{orderId}/mark-in-delivery")
    public ResponseEntity<APIResponse<OrderResponse>> markAsInDelivery(@PathVariable Long orderId) {
        log.info("Marking order {} as in delivery", orderId);

        OrderResponse response = orderService.markAsInDelivery(orderId);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order marked as in delivery")
                .result(response)
                .build());
    }

    /**
     * Chuyển đơn hàng sang trạng thái đã giao
     * POST /api/v1/orders/{orderId}/mark-delivered
     */
    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<APIResponse<OrderResponse>> markAsDelivered(@PathVariable Long orderId) {
        log.info("Marking order {} as delivered", orderId);

        OrderResponse response = orderService.markAsDelivered(orderId);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order marked as delivered")
                .result(response)
                .build());
    }

    // ========== ORDER ITEM MANAGEMENT APIs ==========

    /**
     * Cập nhật số lượng món ăn trong đơn hàng
     * PUT /api/v1/orders/{orderId}/items/{productId}
     */
    @PutMapping("/{orderId}/items/{productId}")
    public ResponseEntity<APIResponse<OrderResponse>> updateOrderItemQuantity(
            @PathVariable Long orderId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        log.info("Updating order item quantity - orderId: {}, productId: {}, quantity: {}",
                 orderId, productId, quantity);

        OrderResponse response = orderService.updateOrderItemQuantity(orderId, productId, quantity);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order item quantity updated successfully")
                .result(response)
                .build());
    }

    /**
     * Xóa món ăn khỏi đơn hàng
     * DELETE /api/v1/orders/{orderId}/items/{productId}
     */
    @DeleteMapping("/{orderId}/items/{productId}")
    public ResponseEntity<APIResponse<OrderResponse>> removeOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long productId) {
        log.info("Removing order item - orderId: {}, productId: {}", orderId, productId);

        OrderResponse response = orderService.removeOrderItem(orderId, productId);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order item removed successfully")
                .result(response)
                .build());
    }

    /**
     * Thêm món ăn mới vào đơn hàng
     * POST /api/v1/orders/{orderId}/items
     */
    @PostMapping("/{orderId}/items")
    public ResponseEntity<APIResponse<OrderResponse>> addOrderItem(
            @PathVariable Long orderId,
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        log.info("Adding order item - orderId: {}, productId: {}, quantity: {}",
                 orderId, productId, quantity);

        OrderResponse response = orderService.addOrderItem(orderId, productId, quantity);

        return ResponseEntity.ok(APIResponse.<OrderResponse>builder()
                .code(200)
                .message("Order item added successfully")
                .result(response)
                .build());
    }
}
