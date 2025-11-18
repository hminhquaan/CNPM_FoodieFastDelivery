package service.order;

import dto.request.order.UpdateOrderStatusRequest;
import dto.response.order.OrderResponse;
import enums.OrderStatus;

import java.util.List;

public interface OrderService {

    /**
     * Create new orders from authenticated user's cart
     * Returns list of orders (one per store)
     */
    List<OrderResponse> createOrdersFromCart(String username);

    /**
     * Create new orders from authenticated user's cart with optional dropoff override
     */
    List<OrderResponse> createOrdersFromCart(String username, dto.request.order.CreateOrdersRequest request);

    /**
     * Get order by ID
     */
    OrderResponse getOrderById(Long orderId);

    /**
     * Get order by order code
     */
    OrderResponse getOrderByCode(String orderCode);

    /**
     * Get all orders by user
     */
    List<OrderResponse> getOrdersByUserId(Long userId);

    /**
     * Get all orders by store
     */
    List<OrderResponse> getOrdersByStoreId(Long storeId);

    /**
     * Get kitchen queue for a store (paid orders awaiting preparation)
        * Default statuses: PAID, PREPARING (and legacy ACCEPT). Optional filter to a single status.
     */
    List<OrderResponse> getKitchenQueue(Long storeId, OrderStatus statusFilter);

    /**
     * Get all orders (admin use)
     */
    List<OrderResponse> getAllOrders();

    /**
     * Cancel order
     */
    void cancelOrder(Long orderId);

    /**
     * Update order status (only if payment_status = PAID)
     */
    OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request);

    /**
     * Store accepts order (creates ledger entry automatically)
     */
    OrderResponse acceptOrder(Long orderId);

    /**
     * Kitchen marks order as prepared; triggers delivery creation/dispatch
     */
    OrderResponse kitchenComplete(Long orderId);

    /**
     * Store rejects order
     */
    OrderResponse rejectOrder(Long orderId, String reason);

    /**
     * Update order to IN_DELIVERY status
     */
    OrderResponse markAsInDelivery(Long orderId);

    /**
     * Update order to DELIVERED status
     */
    OrderResponse markAsDelivered(Long orderId);

    /**
     * Force delete an order and its related data (delivery, items, payment transactions).
     * Use with caution. Intended for admin/cleanup scenarios.
     */
    void hardDeleteOrder(Long orderId);

    /**
     * Update quantity of an item in order
     * Only allowed when order status is CREATED or PENDING_PAYMENT
     */
    OrderResponse updateOrderItemQuantity(Long orderId, Long productId, Integer quantity);

    /**
     * Remove an item from order
     * Only allowed when order status is CREATED or PENDING_PAYMENT
     */
    OrderResponse removeOrderItem(Long orderId, Long productId);

    /**
     * Add new item to order
     * Only allowed when order status is CREATED or PENDING_PAYMENT
     */
    OrderResponse addOrderItem(Long orderId, Long productId, Integer quantity);
}
