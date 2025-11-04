package service.cart;

import dto.request.Cart.AddToCartRequest;
import dto.request.Cart.UpdateCartItemRequest;
import dto.response.Cart.CartItemResponse;
import dto.response.Cart.CartResponse;
import entity.Cart;
import entity.CartItem;
import entity.Product;
import enums.CartStatus;
import exception.ResourceNotFoundException;
import repository.cart.CartRepository;
import repository.cart.CartItemRepository;
import repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        log.info("Adding product {} to cart for user {}", request.getProductId(), userId);

        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        // Get or create active cart for user
        Cart cart = getOrCreateActiveCart(userId);

        // Check if product already exists in cart
        Optional<CartItem> existingCartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId());

        CartItem cartItem;
        if (existingCartItem.isPresent()) {
            // Update existing item quantity
            cartItem = existingCartItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
            cartItem.setTotalPrice(product.getBasePrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        } else {
            // Create new cart item
            cartItem = CartItem.builder()
                    .cartId(cart.getId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .unitPriceSnapshot(product.getBasePrice())
                    .totalPrice(product.getBasePrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                    .build();
        }

        cartItemRepository.save(cartItem);

        log.info("Successfully added product {} to cart for user {}", request.getProductId(), userId);
        return getActiveCart(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getActiveCart(Long userId) {
        log.info("Getting active cart for user {}", userId);

        Optional<Cart> cartOpt = cartRepository.findByUserIdAndStatusWithItems(userId, CartStatus.ACTIVE);

        if (cartOpt.isEmpty()) {
            // Return empty cart response
            return CartResponse.builder()
                    .userId(userId)
                    .status(CartStatus.ACTIVE)
                    .cartItems(List.of())
                    .totalItems(0)
                    .totalAmount(BigDecimal.ZERO)
                    .build();
        }

        Cart cart = cartOpt.get();
        return convertToCartResponse(cart);
    }

    @Override
    public CartResponse updateCartItemByProductId(Long userId, Long productId, UpdateCartItemRequest request) {
        log.info("Updating cart item for product {} for user {}", productId, userId);

        // Get user's active cart
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active cart not found for user: " + userId));

        // Find cart item by productId
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart: " + productId));

        // Update quantity and total price
        cartItem.setQuantity(request.getQuantity());
        cartItem.setTotalPrice(cartItem.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(request.getQuantity())));

        cartItemRepository.save(cartItem);

        log.info("Successfully updated cart item for product {} for user {}", productId, userId);
        return getActiveCart(userId);
    }

    @Override
    public CartResponse removeCartItemByProductId(Long userId, Long productId) {
        log.info("Removing product {} from cart for user {}", productId, userId);

        // Get user's active cart
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active cart not found for user: " + userId));

        // Find cart item by productId
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart: " + productId));

        cartItemRepository.delete(cartItem);

        log.info("Successfully removed product {} from cart for user {}", productId, userId);
        return getActiveCart(userId);
    }

    @Override
    public void clearCart(Long userId) {
        log.info("Clearing cart for user {}", userId);

        Optional<Cart> cartOpt = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);

        if (cartOpt.isPresent()) {
            cartItemRepository.deleteByCartId(cartOpt.get().getId());
            log.info("Successfully cleared cart for user {}", userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int getCartItemCount(Long userId) {
        Optional<Cart> cartOpt = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);

        if (cartOpt.isEmpty()) {
            return 0;
        }

        return cartItemRepository.countByCartId(cartOpt.get().getId()).intValue();
    }

    private Cart getOrCreateActiveCart(Long userId) {
        Optional<Cart> existingCart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);

        if (existingCart.isPresent()) {
            return existingCart.get();
        }

        // Create new cart
        Cart newCart = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();

        return cartRepository.save(newCart);
    }

    private CartResponse convertToCartResponse(Cart cart) {
        // Xử lý trường hợp cartItems là null (khi cart mới tạo)
        List<CartItemResponse> cartItemResponses = (cart.getCartItems() != null)
                ? cart.getCartItems().stream()
                .map(this::convertToCartItemResponse)
                .collect(Collectors.toList())
                : new ArrayList<>();

        int totalItems = cartItemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        BigDecimal totalAmount = cartItemResponses.stream()
                .map(CartItemResponse::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .cartItems(cartItemResponses)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemResponse convertToCartItemResponse(CartItem cartItem) {
        return CartItemResponse.builder()
                .id(cartItem.getId())
                .productId(cartItem.getProductId())
                .productName(cartItem.getProduct() != null ? cartItem.getProduct().getName() : "Unknown Product")
                .productDescription(cartItem.getProduct() != null ? cartItem.getProduct().getDescription() : "")
                .productImageUrl(cartItem.getProduct() != null ? cartItem.getProduct().getMediaPrimaryUrl() : "")
                .quantity(cartItem.getQuantity())
                .unitPrice(cartItem.getUnitPriceSnapshot())
                .totalPrice(cartItem.getTotalPrice())
                .createdAt(cartItem.getCreatedAt())
                .build();
    }
}
