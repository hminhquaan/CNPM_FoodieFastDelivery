package service.order;

import dto.request.order.UpdateOrderStatusRequest;
import dto.request.order.CreateOrdersRequest;
import dto.response.order.OrderItemResponse;
import dto.response.order.OrderResponse;
import entity.*;
import enums.CartStatus;
import enums.OrderStatus;
import enums.PaymentStatus;
import enums.ProductStatus;
import enums.StoreStatus;
import enums.UserStatus;
import exception.ResourceNotFoundException;
import exception.BadRequestException;
import repository.product.ProductRepository;
import repository.order.OrderItemRepository;
import repository.order.OrderRepository;
import repository.store.StoreRepository;
import repository.cart.CartRepository;
import repository.cart.CartItemRepository;
import repository.user.UserRepository;
import repository.user.UserAddressRepository;
import service.payment.LedgerService;
import repository.delivery.DeliveryRepository;
import repository.payment.PaymentTransactionRepository;
import service.delivery.DeliveryService;
import dto.request.delivery.CreateDeliveryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final DeliveryService deliveryService;
    private final UserAddressRepository userAddressRepository;
    private final DeliveryRepository deliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional
    public List<OrderResponse> createOrdersFromCart(String username) {
        // Backward-compat: no override provided
        return createOrdersFromCart(username, null);
    }

    @Override
    @Transactional
    public List<OrderResponse> createOrdersFromCart(String username, CreateOrdersRequest override) {
        log.info("Creating orders from cart for authenticated user: {}", username);

        try {
            // 1. Lấy user từ username và kiểm tra status
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new BadRequestException("User is not active. Cannot place order.");
            }
            Long userId = user.getId();

            log.info("Found user with ID: {}", userId);

            // 2. Lấy giỏ hàng ACTIVE của user
            Cart cart = cartRepository.findByUserIdAndStatusWithItems(userId, CartStatus.ACTIVE)
                    .orElseThrow(() -> new BadRequestException("No active cart found for user: " + username));

            // 3. Lấy tất cả cart items
            List<CartItem> cartItems = cartItemRepository.findByCartIdWithProduct(cart.getId());
            if (cartItems.isEmpty()) {
                throw new BadRequestException("Cart is empty. Cannot create orders.");
            }

            // 4. GOM CÁC CART ITEMS THEO STORE_ID
            Map<Long, List<CartItem>> itemsByStore = cartItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getProduct().getStoreId()));

            log.info("Found {} items grouped into {} stores", cartItems.size(), itemsByStore.size());

            // 5. TẠO MỘT ĐỜN HÀNG CHO MỖI CỬA HÀNG
            List<OrderResponse> createdOrders = new ArrayList<>();

            for (Map.Entry<Long, List<CartItem>> entry : itemsByStore.entrySet()) {
                Long storeId = entry.getKey();
                List<CartItem> storeItems = entry.getValue();

                log.info("Creating order for store {} with {} items", storeId, storeItems.size());

                // Verify store exists
                Store store = storeRepository.findById(storeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));

                if (store.getStatus() != StoreStatus.ACTIVE) {
                    throw new BadRequestException("Store is not active: " + store.getName());
                }

                // Tạo order code
                String orderCode = generateOrderCode();
                BigDecimal totalItemAmount = BigDecimal.ZERO;

                // Tạo Order entity
            // Snapshot delivery address: use override if provided, otherwise default user's address
            String addressSnapshotJson = buildAddressSnapshotWithOverride(userId, override);

        Order order = Order.builder()
                        .userId(userId)
                        .storeId(storeId)
                        .orderCode(orderCode)
                        .status(OrderStatus.CREATED)
                        .paymentStatus(PaymentStatus.PENDING)
                        .totalItemAmount(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .taxAmount(BigDecimal.ZERO)
                        .totalPayable(BigDecimal.ZERO)
            .deliveryAddressSnapshot(addressSnapshotJson)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                order = orderRepository.save(order);

                // Tạo order items
                for (CartItem cartItem : storeItems) {
                    Product product = cartItem.getProduct();

                    if (product.getStatus() != ProductStatus.ACTIVE) {
                        throw new BadRequestException("Product is not active: " + product.getName());
                    }

                    // Kiểm tra tồn kho
                    if (product.getQuantityAvailable() < cartItem.getQuantity()) {
                        throw new BadRequestException("Insufficient stock for product: " + product.getName() +
                                ". Available: " + product.getQuantityAvailable() + ", Requested: " + cartItem.getQuantity());
                    }

                    // Tính tổng tiền item
                    BigDecimal itemTotal = product.getBasePrice().multiply(new BigDecimal(cartItem.getQuantity()));
                    totalItemAmount = totalItemAmount.add(itemTotal);

                    // Tạo OrderItem
                    OrderItem orderItem = OrderItem.builder()
                            .orderId(order.getId())
                            .productId(product.getId())
                            .productNameSnapshot(product.getName())
                            .unitPriceSnapshot(product.getBasePrice())
                            .quantity(cartItem.getQuantity())
                            .totalPrice(itemTotal)
                            .build();

                    orderItemRepository.save(orderItem);

                    // Cập nhật tồn kho (null-safe for reservedQuantity)
                    int currentReserved = product.getReservedQuantity() == null ? 0 : product.getReservedQuantity();
                    product.setReservedQuantity(currentReserved + cartItem.getQuantity());
                    product.setQuantityAvailable(product.getQuantityAvailable() - cartItem.getQuantity());
                    productRepository.save(product);
                }

                // Tính phí ship và tổng tiền
                BigDecimal shippingFee = new BigDecimal("20000");
                order.setTotalItemAmount(totalItemAmount);
                order.setShippingFee(shippingFee);
                order.setTotalPayable(totalItemAmount.add(shippingFee));
                order = orderRepository.save(order);

                log.info("Order created successfully: {} for store: {}", orderCode, storeId);
                createdOrders.add(buildOrderResponse(order));
            }

            // 6. Xóa cart items và cart sau khi tạo orders
            cartItemRepository.deleteByCartId(cart.getId());
            cartItemRepository.flush();
            cartRepository.delete(cart);
            cartRepository.flush();

            log.info("Created {} orders from cart. Cart deleted.", createdOrders.size());

            return createdOrders;

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("Error creating orders: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating orders: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create orders from cart", e);
        }
    }

    private String buildAddressSnapshot(Long userId) {
        try {
            var def = userAddressRepository.findByUserIdAndIsDefaultTrue(userId);
            if (def.isEmpty()) return null;
            var addr = def.get();
            // Build compact JSON manually to avoid extra dependencies
            String full = safe(addr.getAddressLine()) + (addr.getWard()!=null? (", "+addr.getWard()):"")
                    + (addr.getDistrict()!=null? (", "+addr.getDistrict()):"")
                    + (addr.getCity()!=null? (", "+addr.getCity()):"");
            String lat = addr.getLatitude() != null ? addr.getLatitude().toPlainString() : null;
            String lng = addr.getLongitude() != null ? addr.getLongitude().toPlainString() : null;
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"label\":\"").append(safe(addr.getLabel())).append("\",");
            sb.append("\"fullAddress\":\"").append(full.replace("\"","\\\"")).append("\"");
            if (lat != null && lng != null) {
                sb.append(",\"lat\":").append(lat).append(",\"lng\":").append(lng);
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildAddressSnapshotWithOverride(Long userId, CreateOrdersRequest override) {
        try {
            if (override != null) {
                // Saved addressId takes precedence if provided
                if (override.getAddressId() != null) {
                    var addrOpt = userAddressRepository.findById(override.getAddressId());
                    if (addrOpt.isPresent() && addrOpt.get().getUser() != null &&
                            Objects.equals(addrOpt.get().getUser().getId(), userId)) {
                        var addr = addrOpt.get();
                        String full = safe(addr.getAddressLine()) + (addr.getWard()!=null? (", "+addr.getWard()):"")
                                + (addr.getDistrict()!=null? (", "+addr.getDistrict()):"")
                                + (addr.getCity()!=null? (", "+addr.getCity()):"");
                        String lat = addr.getLatitude() != null ? addr.getLatitude().toPlainString() : null;
                        String lng = addr.getLongitude() != null ? addr.getLongitude().toPlainString() : null;
                        StringBuilder sb = new StringBuilder("{");
                        sb.append("\"label\":\"").append(safe(addr.getLabel())).append("\",");
                        sb.append("\"fullAddress\":\"").append(full.replace("\"","\\\"")).append("\"");
                        if (lat != null && lng != null) {
                            sb.append(",\"lat\":").append(lat).append(",\"lng\":").append(lng);
                        }
                        sb.append("}");
                        return sb.toString();
                    } else {
                        log.warn("AddressId {} not found or not owned by user {}. Ignoring.", override.getAddressId(), userId);
                    }
                }
                // Custom lat/lng override
                if (override.getLat() != null && override.getLng() != null) {
                    StringBuilder sb = new StringBuilder("{");
                    String label = override.getLabel() != null ? override.getLabel() : "Dropoff";
                    String full = override.getFullAddress() != null ? override.getFullAddress() : "Custom location";
                    sb.append("\"label\":\"").append(safe(label)).append("\",");
                    sb.append("\"fullAddress\":\"").append(safe(full)).append("\",");
                    sb.append("\"lat\":").append(override.getLat()).append(",\"lng\":").append(override.getLng());
                    sb.append("}");
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed building override address snapshot: {}", e.getMessage());
        }
        // Fallback to default address
        return buildAddressSnapshot(userId);
    }

    private String safe(String s) { return s==null? "" : s.replace("\"","\\\""); }

    @Override
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return buildOrderResponse(order);
    }

    @Override
    public OrderResponse getOrderByCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with code: " + orderCode));
        return buildOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getOrdersByStoreId(Long storeId) {
        List<Order> orders = orderRepository.findByStoreId(storeId);
        return orders.stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getKitchenQueue(Long storeId, OrderStatus statusFilter) {
        List<OrderStatus> statuses;
        if (statusFilter != null) {
            statuses = java.util.Collections.singletonList(statusFilter);
        } else {
            statuses = java.util.Arrays.asList(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.ACCEPT);
        }
        List<Order> orders = orderRepository.findKitchenQueue(storeId, PaymentStatus.PAID, statuses);
        return orders.stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot cancel paid order");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product != null) {
                product.setReservedQuantity(product.getReservedQuantity() - item.getQuantity());
                product.setQuantityAvailable(product.getQuantityAvailable() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Order cancelled successfully: {}", orderId);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        log.info("Updating order {} status to: {}", orderId, request.getStatus());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Kiểm tra: chỉ orders đã thanh toán mới được cập nhật trạng thái
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be updated. Current payment status: " + order.getPaymentStatus());
        }

        // Validate luồng trạng thái hợp lệ
        validateStatusTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        log.info("Order {} status updated to: {}", orderId, request.getStatus());
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse acceptOrder(Long orderId) {
        log.info("Store accepting order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Kiểm tra: chỉ orders đã thanh toán mới được chấp nhận
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be accepted. Payment status: " + order.getPaymentStatus());
        }

        // Kiểm tra trạng thái hiện tại - phải là PAID
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BadRequestException("Order must be in PAID status to be accepted. Current status: " + order.getStatus());
        }

        // Sau khi cửa hàng chấp nhận, chuyển sang PREPARING (đang chế biến)
        order.setStatus(OrderStatus.PREPARING);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        log.info("Order {} accepted -> moved to PREPARING.", orderId);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse kitchenComplete(Long orderId) {
        log.info("Kitchen marking order {} as prepared; triggering delivery", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be prepared. Payment status: " + order.getPaymentStatus());
        }

        if (order.getStatus() != OrderStatus.PREPARING && order.getStatus() != OrderStatus.ACCEPT) {
            throw new BadRequestException("Order must be in PREPARING status to complete kitchen. Current status: " + order.getStatus());
        }

        // Create delivery if not exists; auto-assign will progress to flight
        try {
            boolean exists = false;
            try {
                deliveryService.getDeliveryByOrderId(order.getId());
                exists = true;
            } catch (Exception ignore) { /* not found -> create */ }

            if (!exists) {
                CreateDeliveryRequest dreq = CreateDeliveryRequest.builder()
                        .orderId(order.getId())
                        .pickupStoreId(order.getStoreId())
                        .dropoffAddressSnapshot(order.getDeliveryAddressSnapshot())
                        .build();
                deliveryService.createDelivery(dreq);
                log.info("Delivery created after kitchen complete for order: {}", orderId);
            } else {
                log.info("Delivery already exists for order: {} — skipping create", orderId);
            }
        } catch (Exception ex) {
            log.warn("Delivery creation after kitchen complete skipped: {}", ex.getMessage());
        }

        // Keep order in PREPARING; DeliveryService will move it to IN_DELIVERY when flight launches
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse rejectOrder(Long orderId, String reason) {
        log.info("Store rejecting order: {} with reason: {}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Kiểm tra trạng thái
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be rejected");
        }

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BadRequestException("Order must be in PAID status to be rejected");
        }

        // Cập nhật trạng thái sang CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // TODO: Có thể cần xử lý hoàn tiền ở đây
        log.info("Order {} rejected by store. Reason: {}", orderId, reason);

        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markAsInDelivery(Long orderId) {
        log.info("Marking order {} as IN_DELIVERY", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Kiểm tra payment status
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be marked as in delivery");
        }

        // Kiểm tra trạng thái hiện tại
        if (order.getStatus() != OrderStatus.PREPARING && order.getStatus() != OrderStatus.ACCEPT) {
            throw new BadRequestException("Order must be in PREPARING status. Current: " + order.getStatus());
        }

        order.setStatus(OrderStatus.IN_DELIVERY);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        log.info("Order {} marked as IN_DELIVERY", orderId);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markAsDelivered(Long orderId) {
        log.info("Marking order {} as DELIVERED", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Kiểm tra payment status
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid orders can be marked as delivered");
        }

        // Kiểm tra trạng thái hiện tại
        if (order.getStatus() != OrderStatus.IN_DELIVERY) {
            throw new BadRequestException("Order must be in IN_DELIVERY status. Current: " + order.getStatus());
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        log.info("Order {} marked as DELIVERED", orderId);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public void hardDeleteOrder(Long orderId) {
        log.warn("Force deleting order {} and related data", orderId);

        // Delete delivery if exists
        try {
            var optDel = deliveryRepository.findByOrderId(orderId);
            optDel.ifPresent(deliveryRepository::delete);
        } catch (Exception e) {
            log.warn("Delete delivery for order {} failed: {}", orderId, e.getMessage());
        }

        // Delete order items
        try {
            var items = orderItemRepository.findByOrderId(orderId);
            if (items != null && !items.isEmpty()) {
                orderItemRepository.deleteAll(items);
            }
        } catch (Exception e) {
            log.warn("Delete order items for order {} failed: {}", orderId, e.getMessage());
        }

        // Delete payment transaction
        try {
            var txOpt = paymentTransactionRepository.findByOrderId(orderId);
            txOpt.ifPresent(paymentTransactionRepository::delete);
        } catch (Exception e) {
            log.warn("Delete payment transaction for order {} failed: {}", orderId, e.getMessage());
        }

        // Finally delete order
        try {
            orderRepository.findById(orderId).ifPresent(orderRepository::delete);
        } catch (Exception e) {
            log.warn("Delete order {} failed: {}", orderId, e.getMessage());
        }
    }

    /**
     * Validate status transition logic
     */
    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Không cho phép chuyển từ DELIVERED hoặc CANCELLED
        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot change status of " + currentStatus + " order");
        }

        // Logic chuyển đổi hợp lệ
        switch (currentStatus) {
            case PAID:
                if (newStatus != OrderStatus.ACCEPT && newStatus != OrderStatus.PREPARING && newStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException("PAID order can only be moved to ACCEPT, PREPARING or CANCELLED");
                }
                break;
            case ACCEPT:
                if (newStatus != OrderStatus.PREPARING && newStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException("ACCEPT order can only be moved to PREPARING or CANCELLED");
                }
                break;
            case PREPARING:
                if (newStatus != OrderStatus.IN_DELIVERY && newStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException("PREPARING order can only be moved to IN_DELIVERY or CANCELLED");
                }
                break;
            case IN_DELIVERY:
                if (newStatus != OrderStatus.DELIVERED && newStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException("IN_DELIVERY order can only be moved to DELIVERED or CANCELLED");
                }
                break;
            default:
                break;
        }
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductNameSnapshot())
                        .unitPrice(item.getUnitPriceSnapshot())
                        .quantity(item.getQuantity())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        // Enrich store and user for display fields
        String storeName = null;
        Long storeId = order.getStoreId();
        if (storeId != null) {
            try {
                Optional<Store> sOpt = storeRepository.findById(storeId);
                if (sOpt.isPresent()) {
                    storeName = sOpt.get().getName();
                }
            } catch (Exception e) {
                log.debug("Unable to fetch store name for storeId={}", storeId);
            }
        }

        String customerName = null;
        String customerEmail = null;
        Long userId = order.getUserId();
        if (userId != null) {
            try {
                Optional<User> uOpt = userRepository.findById(userId);
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    customerName = (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername();
                    customerEmail = u.getEmail();
                }
            } catch (Exception e) {
                log.debug("Unable to fetch user info for userId={}", userId);
            }
        }

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .storeId(order.getStoreId())
                .storeName(storeName)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalItemAmount(order.getTotalItemAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .taxAmount(order.getTaxAmount())
                .totalPayable(order.getTotalPayable())
            .deliveryAddressSnapshot(order.getDeliveryAddressSnapshot())
                .deliveredDroneId(order.getDeliveredDroneId())
                .deliveredDroneCode(order.getDeliveredDroneCode())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public OrderResponse updateOrderItemQuantity(Long orderId, Long productId, Integer quantity) {
        log.info("Updating order item quantity - orderId: {}, productId: {}, newQuantity: {}",
                 orderId, productId, quantity);

        // 1. Lấy order và kiểm tra trạng thái
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Chỉ cho phép chỉnh sửa khi đơn hàng chưa thanh toán hoặc đang chờ thanh toán
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Cannot modify order in status: " + order.getStatus() +
                                        ". Only CREATED or PENDING_PAYMENT orders can be modified.");
        }

        // 2. Tìm order item theo productId
        OrderItem orderItem = orderItemRepository.findByOrderIdAndProductId(orderId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Product " + productId + " not found in order " + orderId));

        // 3. Kiểm tra tồn kho của sản phẩm
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getQuantityAvailable() < quantity) {
            throw new BadRequestException("Not enough stock. Available: " + product.getQuantityAvailable() +
                                        ", Requested: " + quantity);
        }

        // 4. Cập nhật số lượng và tổng tiền
        orderItem.setQuantity(quantity);
        orderItem.setTotalPrice(orderItem.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(quantity)));
        orderItemRepository.save(orderItem);

        // 5. Tính lại tổng tiền của đơn hàng
        recalculateOrderTotal(order);

        log.info("Updated order item quantity successfully for order: {}", orderId);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse removeOrderItem(Long orderId, Long productId) {
        log.info("Removing order item - orderId: {}, productId: {}", orderId, productId);

        // 1. Lấy order và kiểm tra trạng thái
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Cannot modify order in status: " + order.getStatus());
        }

        // 2. Kiểm tra số lượng món trong đơn hàng
        List<OrderItem> currentItems = orderItemRepository.findByOrderId(orderId);
        if (currentItems.size() <= 1) {
            throw new BadRequestException("Cannot remove the last item. Cancel the order instead.");
        }

        // 3. Tìm và xóa order item
        OrderItem orderItem = orderItemRepository.findByOrderIdAndProductId(orderId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Product " + productId + " not found in order " + orderId));

        orderItemRepository.delete(orderItem);

        // 4. Tính lại tổng tiền của đơn hàng
        recalculateOrderTotal(order);

        log.info("Removed order item successfully from order: {}", orderId);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse addOrderItem(Long orderId, Long productId, Integer quantity) {
        log.info("Adding order item - orderId: {}, productId: {}, quantity: {}",
                 orderId, productId, quantity);

        // 1. Lấy order và kiểm tra trạng thái
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Cannot modify order in status: " + order.getStatus());
        }

        // 2. Lấy thông tin sản phẩm
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        // 3. Kiểm tra sản phẩm có thuộc cùng cửa hàng không
        if (!product.getStoreId().equals(order.getStoreId())) {
            throw new BadRequestException("Product does not belong to the same store as the order");
        }

        // 4. Kiểm tra tồn kho
        if (product.getQuantityAvailable() < quantity) {
            throw new BadRequestException("Not enough stock. Available: " + product.getQuantityAvailable() +
                                        ", Requested: " + quantity);
        }

        // 5. Kiểm tra xem sản phẩm đã có trong đơn hàng chưa
        Optional<OrderItem> existingItem = orderItemRepository.findByOrderIdAndProductId(orderId, productId);

        if (existingItem.isPresent()) {
            // Nếu đã có, cập nhật số lượng
            OrderItem orderItem = existingItem.get();
            int newQuantity = orderItem.getQuantity() + quantity;

            if (product.getQuantityAvailable() < newQuantity) {
                throw new BadRequestException("Not enough stock. Available: " + product.getQuantityAvailable() +
                                            ", Total requested: " + newQuantity);
            }

            orderItem.setQuantity(newQuantity);
            orderItem.setTotalPrice(orderItem.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(newQuantity)));
            orderItemRepository.save(orderItem);

            log.info("Updated existing item quantity in order: {}", orderId);
        } else {
            // Nếu chưa có, tạo mới
            OrderItem newOrderItem = OrderItem.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .productNameSnapshot(product.getName())
                    .unitPriceSnapshot(product.getBasePrice())
                    .quantity(quantity)
                    .totalPrice(product.getBasePrice().multiply(BigDecimal.valueOf(quantity)))
                    .build();

            orderItemRepository.save(newOrderItem);
            log.info("Added new item to order: {}", orderId);
        }

        // 6. Tính lại tổng tiền của đơn hàng
        recalculateOrderTotal(order);

        log.info("Added order item successfully to order: {}", orderId);
        return buildOrderResponse(order);
    }

    /**
     * Tính lại tổng tiền của đơn hàng sau khi thay đổi món ăn
     */
    private void recalculateOrderTotal(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        // Tính tổng tiền các món
        BigDecimal totalItemAmount = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalItemAmount(totalItemAmount);

        // Tính tổng cần thanh toán = tổng món + phí ship + thuế - giảm giá
        BigDecimal totalPayable = totalItemAmount
                .add(order.getShippingFee())
                .add(order.getTaxAmount())
                .subtract(order.getDiscountAmount());

        order.setTotalPayable(totalPayable);
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        log.info("Recalculated order total - orderId: {}, newTotal: {}", order.getId(), totalPayable);
    }

    private String generateOrderCode() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
