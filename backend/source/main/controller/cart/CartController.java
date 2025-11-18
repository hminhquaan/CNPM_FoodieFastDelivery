package controller.cart;

import service.cart.CartService;
import dto.request.Cart.AddToCartRequest;
import dto.request.Cart.UpdateCartItemRequest;
import dto.response.Cart.CartResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import repository.user.UserRepository;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;
    private final UserRepository userRepository;

    /**
     * Add item to cart
     */
    @PostMapping("/add")
    public ResponseEntity<CartResponse> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CartResponse response = cartService.addToCart(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Get user's active cart
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        Long userId = getUserId(authentication);
        CartResponse response = cartService.getActiveCart(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Update cart item quantity by product ID
     */
    @PutMapping("/products/{productId}")
    public ResponseEntity<CartResponse> updateCartItemByProduct(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CartResponse response = cartService.updateCartItemByProductId(userId, productId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Remove item from cart by product ID
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<CartResponse> removeCartItemByProduct(
            @PathVariable Long productId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CartResponse response = cartService.removeCartItemByProductId(userId, productId);

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all items from cart
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long userId = getUserId(authentication);
        cartService.clearCart(userId);

        return ResponseEntity.ok().build();
    }

    /**
     * Get cart item count
     */
    @GetMapping("/count")
    public ResponseEntity<Integer> getCartItemCount(Authentication authentication) {
        Long userId = getUserId(authentication);
        int count = cartService.getCartItemCount(userId);
        return ResponseEntity.ok(count);
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return Long.valueOf(userRepository.findIdByUsername(authentication.getName()));
    }
}
