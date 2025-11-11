// Cart.js - Shopping Cart Logic

let cartData = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    checkAuthAndLoadCart();
    checkAuthStatus();
});

// Check auth and redirect if not logged in
function checkAuthAndLoadCart() {
    if (!AuthHelper.isLoggedIn()) {
        Toast.warning('Vui lòng đăng nhập để xem giỏ hàng');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 1500);
        return;
    }

    loadCart();
}

// Check authentication status
function checkAuthStatus() {
    const isLoggedIn = AuthHelper.isLoggedIn();
    const guestMenu = document.getElementById('guestMenu');
    const userDropdown = document.getElementById('userDropdown');

    if (isLoggedIn) {
        const user = AuthHelper.getUser();
        guestMenu.style.display = 'none';
        userDropdown.style.display = 'block';
        document.getElementById('userName').textContent = user.username || user.fullName || 'User';
    }
}

// Load cart
async function loadCart() {
    try {
        Loading.show();

        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.CART);
        cartData = response;

        // Backend returns `cartItems` (not `items`)
        const items = (cartData && cartData.cartItems) || [];
        if (!cartData || items.length === 0) {
            showEmptyCart();
        } else {
            displayCartItems(items);
            updateSummary(cartData);
        }

    } catch (error) {
        console.error('Error loading cart:', error);
        Toast.error('Không thể tải giỏ hàng');
        showEmptyCart();
    } finally {
        Loading.hide();
    }
}

// Display cart items
function displayCartItems(items) {
    const container = document.getElementById('cartItems');
    const emptyCart = document.getElementById('emptyCart');
    const cartLayout = document.getElementById('cartLayout');

    cartLayout.style.display = 'grid';
    emptyCart.style.display = 'none';

    container.innerHTML = items.map((item, index) => `
        <div class="cart-item" id="cartItem${item.productId}">
            <img src="${item.productImageUrl || '../img/placeholder-food.svg'}" 
                 alt="${item.productName}" 
                 class="cart-item-img"
                 onerror="this.onerror=null; this.src='../img/placeholder-food.svg'">
            <div class="cart-item-info">
                <h4 class="cart-item-title">${item.productName}</h4>
                <p class="text-gray">${item.storeName || 'Cửa hàng'}</p>
                <div class="cart-item-price">${FormatHelper.currency(item.unitPrice || 0)}</div>
                <div class="quantity-control">
                    <button onclick="updateQuantity(${item.productId}, ${item.quantity - 1})">
                        <i class="fas fa-minus"></i>
                    </button>
                    <span>${item.quantity}</span>
                    <button onclick="updateQuantity(${item.productId}, ${item.quantity + 1})">
                        <i class="fas fa-plus"></i>
                    </button>
                </div>
            </div>
            <div style="display: flex; flex-direction: column; justify-content: space-between; align-items: flex-end;">
                <button class="btn btn-sm" style="background: none; color: var(--danger-color);" 
                        onclick="removeFromCart(${item.productId})">
                    <i class="fas fa-trash"></i>
                </button>
                <div style="font-size: 1.2rem; font-weight: bold; color: var(--primary-color);">
                    ${FormatHelper.currency(item.totalPrice || 0)}
                </div>
            </div>
        </div>
    `).join('');

    // Update cart badge
    updateCartBadge(items.length);
}

// Update cart summary
function updateSummary(cart) {
    const subtotal = cart.totalAmount || 0;
    const shippingFee = 0; // Free shipping
    const total = subtotal + shippingFee;

    document.getElementById('subtotal').textContent = FormatHelper.currency(subtotal);
    document.getElementById('total').textContent = FormatHelper.currency(total);

    // Enable checkout button if cart has items
    const checkoutBtn = document.getElementById('checkoutBtn');
    if (cart.cartItems && cart.cartItems.length > 0) {
        checkoutBtn.disabled = false;
    } else {
        checkoutBtn.disabled = true;
    }
}

// Show empty cart
function showEmptyCart() {
    document.getElementById('cartLayout').style.display = 'none';
    document.getElementById('emptyCart').style.display = 'block';
    updateCartBadge(0);
}

// Update quantity
async function updateQuantity(productId, newQuantity) {
    if (newQuantity < 1) {
        if (confirm('Bạn muốn xóa sản phẩm này khỏi giỏ hàng?')) {
            removeFromCart(productId);
        }
        return;
    }

    if (newQuantity > 99) {
        Toast.warning('Số lượng tối đa là 99');
        return;
    }

    try {
        Loading.show();

        await APIHelper.put(API_CONFIG.ENDPOINTS.CART_UPDATE(productId), {
            quantity: newQuantity
        });

        // Reload cart
        await loadCart();

    } catch (error) {
        console.error('Error updating quantity:', error);
        Toast.error('Không thể cập nhật số lượng');
    } finally {
        Loading.hide();
    }
}

// Remove from cart
async function removeFromCart(productId) {
    try {
        Loading.show();

        await APIHelper.delete(API_CONFIG.ENDPOINTS.CART_REMOVE(productId));

        Toast.success('Đã xóa sản phẩm khỏi giỏ hàng');

        // Reload cart
        await loadCart();

    } catch (error) {
        console.error('Error removing from cart:', error);
        Toast.error('Không thể xóa sản phẩm');
    } finally {
        Loading.hide();
    }
}

// Update cart badge
function updateCartBadge(count) {
    document.getElementById('cartBadge').textContent = count || 0;
}

// Proceed to checkout
async function proceedToCheckout() {
    if (!cartData || !cartData.cartItems || cartData.cartItems.length === 0) {
        Toast.warning('Giỏ hàng trống!');
        return;
    }

    if (!confirm('Xác nhận tạo đơn hàng?')) {
        return;
    }

    try {
        Loading.show();

        // Create order from cart
        const response = await APIHelper.post(API_CONFIG.ENDPOINTS.ORDERS);

        if (response.result && response.result.length > 0) {
            const orders = response.result;
            Toast.success('Tạo đơn hàng thành công!');

            // Get first order to proceed to payment
            const firstOrder = orders[0];

            // Initialize payment
            setTimeout(async () => {
                try {
                    const paymentResponse = await APIHelper.post(API_CONFIG.ENDPOINTS.PAYMENT_INIT, {
                        orderId: firstOrder.id,  // Fix: Use 'id' instead of 'orderId'
                        provider: 'VNPAY',
                        method: 'QR',
                        returnUrl: window.location.origin + '/home/orders.html'
                    });

                    if (paymentResponse.result && paymentResponse.result.paymentUrl) {
                        // Redirect to VNPay
                        window.location.href = paymentResponse.result.paymentUrl;
                    } else {
                        console.error('Payment response:', paymentResponse);
                        Toast.error('Không thể khởi tạo thanh toán');
                    }
                } catch (error) {
                    console.error('Payment error:', error);
                    Toast.error('Lỗi thanh toán: ' + (error.message || 'Vui lòng thử lại!'));
                    // Redirect to orders page anyway
                    setTimeout(() => {
                        window.location.href = 'orders.html';
                    }, 2000);
                }
            }, 1000);
        } else {
            Toast.error('Không thể tạo đơn hàng');
        }

    } catch (error) {
        console.error('Error creating order:', error);
        Toast.error(error.message || 'Không thể tạo đơn hàng');
    } finally {
        Loading.hide();
    }
}

// Clear cart
async function clearCart() {
    if (!confirm('Bạn có chắc muốn xóa toàn bộ giỏ hàng?')) {
        return;
    }

    try {
        Loading.show();

        await APIHelper.delete(API_CONFIG.ENDPOINTS.CART_CLEAR);

        Toast.success('Đã xóa toàn bộ giỏ hàng');
        showEmptyCart();

    } catch (error) {
        console.error('Error clearing cart:', error);
        Toast.error('Không thể xóa giỏ hàng');
    } finally {
        Loading.hide();
    }
}

// Toggle dropdown
function toggleDropdown() {
    const dropdown = document.getElementById('dropdownMenu');
    dropdown.classList.toggle('show');
}

// Logout
function logout() {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        AuthHelper.logout();
    }
}

// Close dropdown when clicking outside
document.addEventListener('click', (e) => {
    const dropdown = document.getElementById('dropdownMenu');
    const avatar = document.getElementById('userAvatar');
    if (dropdown && avatar && !avatar.contains(e.target)) {
        dropdown.classList.remove('show');
    }
});

