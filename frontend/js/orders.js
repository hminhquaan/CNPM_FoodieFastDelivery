// Orders.js - Orders Page Logic

let selectedOrder = null;
let deliveryInfo = null;
let deliveryPath = [];
let deliveryMap = null;
let deliveryPolyline = null;
let droneMarker = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    // Disable service worker caching in dev to avoid stale assets
    if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.getRegistrations().then(regs => regs.forEach(r => r.unregister()));
            if (window.caches && window.caches.keys) {
                caches.keys().then(keys => keys.forEach(k => caches.delete(k)));
            }
        }
    }
    checkAuthAndLoadOrders();
    checkAuthStatus();
    updateCartBadge();
});

// -- Map & Path helpers (global) --
async function ensureDeliveryPath(delivery) {
    if (deliveryPath && deliveryPath.length) return;
    if (!delivery || !delivery.id) return;
    try {
        const resp = await fetch(`${API_CONFIG.BASE_URL}/api/v1/deliveries/${delivery.id}/flight-plan`, {
            headers: APIHelper.getAuthHeaders()
        });
        if (resp.ok) {
            const data = await resp.json();
            const points = (data.result || data || []).map(p => [Number(p.latitude), Number(p.longitude)]).filter(a => !a.some(isNaN));
            if (points.length) {
                deliveryPath = points;
                return;
            }
        }
    } catch(e) {
        console.warn('Flight plan fetch failed, fallback to synthetic path', e);
    }
    // Fallback synthetic path (legacy)
    let dropLat = null, dropLng = null;
    try {
        const snap = delivery && delivery.dropoffAddressSnapshot;
        if (snap && snap.includes('lat') && snap.includes('lng')) {
            const latMatch = snap.match(/"lat"\s*:\s*([-0-9.]+)/);
            const lngMatch = snap.match(/"lng"\s*:\s*([-0-9.]+)/);
            if (latMatch) dropLat = parseFloat(latMatch[1]);
            if (lngMatch) dropLng = parseFloat(lngMatch[1]);
        }
    } catch (_) {}
    const storeLat = 10.762622, storeLng = 106.660172;
    if (dropLat == null || isNaN(dropLat)) dropLat = 10.772622;
    if (dropLng == null || isNaN(dropLng)) dropLng = 106.670172;
    const start = [storeLat, storeLng];
    const end = [dropLat, dropLng];
    const points = [];
    const steps = 12;
    for (let i=0;i<=steps;i++) {
        const t = i/steps;
        const lat = start[0] + (end[0]-start[0])*t + (Math.sin(t*Math.PI)*0.0025);
        const lng = start[1] + (end[1]-start[1])*t + (Math.sin(t*Math.PI)*0.0025);
        points.push([lat,lng]);
    }
    deliveryPath = points;
}

function initDeliveryMap(delivery) {
    const mapEl = document.getElementById('deliveryMap');
    if (!mapEl) return;
    mapEl.style.display = 'block';
    // If the container was re-rendered, Leaflet map's container may differ. Recreate map if needed.
    if (deliveryMap && deliveryMap._container !== mapEl) {
        try { deliveryMap.remove(); } catch(_) {}
        deliveryMap = null;
        deliveryPolyline = null;
        droneMarker = null;
    }
    if (!deliveryMap) {
        deliveryMap = L.map('deliveryMap');
        deliveryMap.setView(deliveryPath[0], 13);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(deliveryMap);
        deliveryPolyline = L.polyline(deliveryPath, { color: '#2563eb', weight: 4, opacity: .8 }).addTo(deliveryMap);
        droneMarker = L.marker(deliveryPath[0], { title: 'Drone' }).addTo(deliveryMap);
        deliveryMap.fitBounds(deliveryPolyline.getBounds(), { padding: [20,20] });
    }
}

function updateDroneOnPath(delivery) {
    if (!droneMarker || !deliveryPath.length) return;
    const order = ['QUEUED','ASSIGNED','LAUNCHED','ARRIVING','COMPLETED'];
    const idx = order.indexOf(delivery.currentStatus);
    if (idx < 0) return;
    // Convert to fraction (0..1)
    let fraction = idx/(order.length-1);
    // If in flight statuses, add smooth progression using time since departure
    if (delivery.actualDepartureTime && ['LAUNCHED','ARRIVING','COMPLETED'].includes(delivery.currentStatus)) {
        try {
            const dep = new Date(delivery.actualDepartureTime).getTime();
            const now = Date.now();
            const elapsed = (now - dep)/1000; // seconds
            const estTotal = deliveryPath.length * 5; // demo speed
            const dynamicProgress = Math.min(1, elapsed/estTotal);
            fraction = Math.max(fraction, dynamicProgress);
        } catch(_) {}
    }
    const targetIndex = Math.min(deliveryPath.length-1, Math.round(fraction*(deliveryPath.length-1)) );
    const coord = deliveryPath[targetIndex];
    droneMarker.setLatLng(coord);
}

// Check auth and redirect if not logged in
function checkAuthAndLoadOrders() {
    if (!AuthHelper.isLoggedIn()) {
        Toast.warning('Vui lòng đăng nhập để xem đơn hàng');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 1500);
        return;
    }

    loadOrders();
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

// Load orders
async function loadOrders() {
    try {
        Loading.show();

        const user = AuthHelper.getUser();
        if (!user) {
            throw new Error('User not found');
        }

        // Get user ID from stored user data
        const userId = user.id;
        if (!userId) {
            throw new Error('Missing user id in session. Please log in again.');
        }

        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.USER_ORDERS(userId));
        const orders = response.result || [];

        if (orders.length === 0) {
            showEmptyOrders();
        } else {
            displayOrders(orders);
        }

    } catch (error) {
        console.error('Error loading orders:', error);
        Toast.error('Không thể tải danh sách đơn hàng');
        showEmptyOrders();
    } finally {
        Loading.hide();
    }
}

// Display orders
function displayOrders(orders) {
    const container = document.getElementById('ordersContainer');
    const emptyOrders = document.getElementById('emptyOrders');

    container.style.display = 'block';
    emptyOrders.style.display = 'none';

    container.innerHTML = orders.map(order => `
        <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-body">
                <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 1rem;">
                    <div>
                        <h3 style="margin: 0;">${order.orderCode || 'ORD' + order.id}</h3>
                        <p style="color: var(--gray); margin: 0.25rem 0;">
                            <i class="fas fa-store"></i> ${order.storeName || 'Cửa hàng'}
                        </p>
                        <p style="color: var(--gray); margin: 0.25rem 0; font-size: 0.9rem;">
                            <i class="fas fa-calendar"></i> ${FormatHelper.date(order.createdAt)}
                        </p>
                    </div>
                    <span class="${getOrderStatusClass(order.status)}">${getOrderStatusText(order.status)}</span>
                </div>
                
                <div style="border-top: 1px solid var(--light); padding-top: 1rem; margin-top: 1rem;">
                    ${order.items && order.items.length > 0 ? order.items.map(item => `
                        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
                            <span>${item.productName || 'Sản phẩm'} x ${item.quantity || 1}</span>
                            <span class="text-gray">${FormatHelper.currency(item.totalPrice || 0)}</span>
                        </div>
                    `).join('') : '<p class="text-gray">Không có sản phẩm</p>'}
                </div>
                
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; padding-top: 1rem; border-top: 2px solid var(--light);">
                    <span style="font-size: 1.2rem; font-weight: bold;">
                        Tổng: <span class="text-primary">${FormatHelper.currency(order.totalPayable || order.totalAmount)}</span>
                    </span>
                    <div style="display: flex; gap: 0.5rem;">
                        <button class="btn btn-outline btn-sm" onclick="viewOrderDetail(${order.id})">
                            <i class="fas fa-eye"></i> Chi tiết
                        </button>
                        ${(order.status === 'PENDING_PAYMENT' || (order.status === 'CREATED' && (order.paymentStatus === 'FAILED' || order.paymentStatus === 'PENDING'))) ? `
                            <button class="btn btn-primary btn-sm" onclick="retryPayment(${order.id})">
                                <i class="fas fa-redo"></i> Thanh toán lại
                            </button>
                        ` : ''}
                        ${order.status === 'IN_DELIVERY' || order.status === 'PAID' ? `
                            <button class="btn btn-primary btn-sm" onclick="trackDelivery(${order.id})">
                                <i class="fas fa-drone"></i> Theo dõi
                            </button>
                        ` : ''}
                    </div>
                </div>
            </div>
        </div>
    `).join('');
}

// Show empty orders
function showEmptyOrders() {
    document.getElementById('ordersContainer').style.display = 'none';
    document.getElementById('emptyOrders').style.display = 'block';
}

// Get order status class
function getOrderStatusClass(status) {
    const statusMap = {
        'CREATED': 'order-status status-pending',
        'PENDING_PAYMENT': 'order-status status-pending',
        'PAID': 'order-status status-paid',
        'CONFIRMED': 'order-status status-paid',
        'IN_DELIVERY': 'order-status status-in-delivery',
        'DELIVERED': 'order-status status-delivered',
        'CANCELLED': 'order-status status-cancelled',
        'REFUNDED': 'order-status status-cancelled'
    };
    return statusMap[status] || 'order-status status-pending';
}

// Get order status text
function getOrderStatusText(status) {
    const statusMap = {
        'CREATED': 'Đã tạo',
        'PENDING_PAYMENT': 'Chờ thanh toán',
        'PAID': 'Đã thanh toán',
        'CONFIRMED': 'Đã xác nhận',
        'IN_DELIVERY': 'Đang giao hàng',
        'DELIVERED': 'Đã giao',
        'CANCELLED': 'Đã hủy',
        'REFUNDED': 'Đã hoàn tiền'
    };
    return statusMap[status] || status;
}

// View order detail
async function viewOrderDetail(orderId) {
    try {
        Loading.show();

        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.ORDER_BY_ID(orderId));
        selectedOrder = response.result;

        if (!selectedOrder) {
            Toast.error('Không tìm thấy đơn hàng');
            return;
        }

        displayOrderDetail(selectedOrder);
        document.getElementById('orderDetailModal').classList.add('show');

    } catch (error) {
        console.error('Error loading order detail:', error);
        Toast.error('Không thể tải chi tiết đơn hàng');
    } finally {
        Loading.hide();
    }
}

// Retry payment for an order in PENDING_PAYMENT
async function retryPayment(orderId) {
    try {
        Loading.show();
        const payload = {
            orderId: orderId,
            provider: 'VNPAY',
            method: 'QR'
        };
        const res = await APIHelper.post(API_CONFIG.ENDPOINTS.PAYMENT_INIT, payload);
        const payment = res && res.result ? res.result : res;
        if (payment && payment.paymentUrl) {
            window.location.href = payment.paymentUrl;
        } else {
            Toast.error('Không lấy được link thanh toán');
        }
    } catch (err) {
        console.error('Retry payment error:', err);
        Toast.error(err.message || 'Không thể khởi tạo lại thanh toán');
    } finally {
        Loading.hide();
    }
}

// Display order detail
function displayOrderDetail(order) {
    const content = document.getElementById('orderDetailContent');

    content.innerHTML = `
        <div style="display: grid; gap: 1.5rem;">
            <!-- Order Info -->
            <div class="card">
                <div class="card-body">
                    <h4 style="margin-bottom: 1rem;">
                        <i class="fas fa-info-circle"></i> Thông Tin Đơn Hàng
                    </h4>
                    <div style="display: grid; gap: 0.5rem;">
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Mã đơn:</span>
                            <strong>${order.orderCode}</strong>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Trạng thái:</span>
                            <span class="${getOrderStatusClass(order.status)}">${getOrderStatusText(order.status)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Ngày đặt:</span>
                            <span>${FormatHelper.date(order.createdAt)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Cửa hàng:</span>
                            <strong>${order.storeName || 'Cửa hàng'}</strong>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Order Items -->
            <div class="card">
                <div class="card-body">
                    <h4 style="margin-bottom: 1rem;">
                        <i class="fas fa-utensils"></i> Sản Phẩm
                    </h4>
                    ${order.items && order.items.length > 0 ? order.items.map(item => `
                        <div style="display: flex; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid var(--light);">
                            <div>
                                <div style="font-weight: 500;">${item.productName}</div>
                                <div class="text-gray" style="font-size: 0.9rem;">
                                    ${FormatHelper.currency(item.unitPrice || 0)} x ${item.quantity}
                                </div>
                            </div>
                            <div style="font-weight: bold; color: var(--primary-color);">
                                ${FormatHelper.currency(item.totalPrice || 0)}
                            </div>
                        </div>
                    `).join('') : '<p class="text-gray">Không có sản phẩm</p>'}
                </div>
            </div>
            
            <!-- Summary -->
            <div class="card">
                <div class="card-body">
                    <h4 style="margin-bottom: 1rem;">
                        <i class="fas fa-receipt"></i> Tổng Kết
                    </h4>
                    <div style="display: grid; gap: 0.5rem;">
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Tạm tính:</span>
                            <span>${FormatHelper.currency(order.totalItemAmount || 0)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Phí vận chuyển:</span>
                            <span>${FormatHelper.currency(order.shippingFee || 0)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Giảm giá:</span>
                            <span class="text-success">-${FormatHelper.currency(order.discountAmount || 0)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; padding-top: 0.75rem; margin-top: 0.75rem; border-top: 2px solid var(--light); font-size: 1.2rem;">
                            <strong>Tổng cộng:</strong>
                            <strong class="text-primary">${FormatHelper.currency(order.totalPayable)}</strong>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Track delivery
async function trackDelivery(orderId) {
    try {
        Loading.show();
        console.log('[trackDelivery] orderId=', orderId);

        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(orderId));
        console.log('[trackDelivery] deliveryByOrder response=', response);
        deliveryInfo = response && response.result ? response.result : null;

        if (!deliveryInfo) {
            Toast.warning('Đơn hàng chưa tạo Delivery. Vui lòng thử lại sau khi thanh toán thành công.');
            return;
        }
        // Show tracking immediately
        await ensureDeliveryPath(deliveryInfo);
    displayDeliveryTracking(deliveryInfo);
        document.getElementById('trackingModal').classList.add('show');
        initDeliveryMap(deliveryInfo);
        updateDroneOnPath(deliveryInfo);
    // Khởi động polling một lần sau khi hiển thị ban đầu
    startDeliveryPolling(deliveryInfo.id, deliveryInfo.currentStatus);
        return;

    } catch (error) {
        console.error('Error loading delivery:', error);
        Toast.error('Không thể tải thông tin giao hàng');
    } finally {
        Loading.hide();
    }
}

// Display delivery tracking
function displayDeliveryTracking(delivery) {
    const content = document.getElementById('trackingContent');
    // Preserve existing map element so we don't lose it when updating content
    const existingMap = document.getElementById('deliveryMap');

    const statusSteps = [
        { key: 'QUEUED', label: 'Đang chờ', icon: 'clock' },
        { key: 'ASSIGNED', label: 'Đã gán drone', icon: 'drone' },
        { key: 'LAUNCHED', label: 'Đang bay đến', icon: 'rocket' },
        { key: 'ARRIVING', label: 'Sắp đến', icon: 'map-marker-alt' },
        { key: 'COMPLETED', label: 'Đã giao', icon: 'check-circle' }
    ];

    const currentIndex = statusSteps.findIndex(s => s.key === delivery.currentStatus);

    const statusHtml = `
        <div style="padding: 1rem 0;">
            <!-- Status Timeline -->
            <div style="margin-bottom: 2rem;">
                ${statusSteps.map((step, index) => `
                    <div style="display: flex; align-items: center; margin-bottom: 1rem; ${index <= currentIndex ? 'color: var(--primary-color);' : 'color: var(--gray);'}">
                        <div style="width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; 
                                    background: ${index <= currentIndex ? 'var(--primary-color)' : 'var(--light)'}; 
                                    color: ${index <= currentIndex ? 'white' : 'var(--gray)'};">
                            <i class="fas fa-${step.icon}"></i>
                        </div>
                        <div style="flex: 1; margin-left: 1rem;">
                            <div style="font-weight: ${index === currentIndex ? 'bold' : 'normal'};">${step.label}</div>
                            ${index === currentIndex ? `<div style="font-size: 0.9rem; color: var(--gray);">Hiện tại</div>` : ''}
                        </div>
                        ${index === currentIndex ? `<i class="fas fa-spinner fa-spin"></i>` : ''}
                    </div>
                    ${index < statusSteps.length - 1 ? `
                        <div style="width: 2px; height: 20px; background: ${index < currentIndex ? 'var(--primary-color)' : 'var(--light)'}; margin-left: 19px;"></div>
                    ` : ''}
                `).join('')}
            </div>
            
            <!-- Delivery Info -->
            <div class="card">
                <div class="card-body">
                    <h4 style="margin-bottom: 1rem;">
                        <i class="fas fa-info-circle"></i> Thông Tin Giao Hàng
                    </h4>
                    <div style="display: grid; gap: 0.5rem;">
                        ${delivery.droneCode ? `
                            <div style="display: flex; justify-content: space-between;">
                                <span class="text-gray">Mã drone:</span>
                                <strong>${delivery.droneCode}</strong>
                            </div>
                        ` : ''}
                        <div style="display: flex; justify-content: space-between;">
                            <span class="text-gray">Trạng thái:</span>
                            <span class="text-primary">${getDeliveryStatusText(delivery.currentStatus)}</span>
                        </div>
                        ${delivery.actualDepartureTime ? `
                            <div style="display: flex; justify-content: space-between;">
                                <span class="text-gray">Thời gian khởi hành:</span>
                                <span>${FormatHelper.date(delivery.actualDepartureTime)}</span>
                            </div>
                        ` : ''}
                        ${delivery.estimatedArrivalTime ? `
                            <div style="display: flex; justify-content: space-between;">
                                <span class="text-gray">Dự kiến đến:</span>
                                <span class="text-success">${FormatHelper.date(delivery.estimatedArrivalTime)}</span>
                            </div>
                        ` : ''}
                    </div>
                </div>
            </div>
            
            ${delivery.currentStatus === 'COMPLETED' ? `
                <div class="card" style="background: #E8F5E9; border: 1px solid #51CF66; margin-top: 1rem;">
                    <div class="card-body text-center">
                        <i class="fas fa-check-circle" style="font-size: 3rem; color: #51CF66;"></i>
                        <h3 style="color: #2E7D32; margin-top: 1rem;">Giao Hàng Thành Công!</h3>
                        <p style="color: #2E7D32;">Cảm ơn bạn đã sử dụng dịch vụ FoodFast</p>
                    </div>
                </div>
            ` : ''}
        </div>
    `;
    content.innerHTML = statusHtml;
    // Re-attach map if it existed (or if not, keep placeholder)
    if (existingMap) {
        content.appendChild(existingMap);
        existingMap.style.display = 'block';
    } else {
        const mapHolder = document.createElement('div');
        mapHolder.id = 'deliveryMap';
        mapHolder.style.cssText = 'margin-top:1rem; height:320px; border:1px solid #eee; border-radius:8px; display:block;';
        content.appendChild(mapHolder);
    }

    // Lưu ý: Polling sẽ được kích hoạt một lần trong trackDelivery sau khi hiển thị lần đầu
}

function startDeliveryPolling(deliveryId, status) {
    if (['COMPLETED','FAILED','RETURNED'].includes(status)) return;
    if (window.__deliveryPollInterval) clearInterval(window.__deliveryPollInterval);
    let attempts = 0;
    const baseDelay = status === 'QUEUED' ? 4000 : 1500; // chậm hơn khi đang chờ gán drone
    window.__deliveryPollInterval = setInterval(async () => {
        try {
            const res = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(deliveryInfo.orderId));
            const updated = res.result;
            if (!updated) return;
            deliveryInfo = updated;
            // Stop polling if terminal
            if (['COMPLETED','FAILED','RETURNED'].includes(updated.currentStatus)) {
                displayDeliveryTracking(updated);
                clearInterval(window.__deliveryPollInterval);
                return;
            }
            // If stuck in QUEUED too long => show warning and stop
            if (updated.currentStatus === 'QUEUED') {
                attempts++;
                // Sau 5 lần (~20s) dừng polling để tránh spam (không tự chuyển sang FAILED)
                if (attempts > 5) {
                    Toast.warning('Đang chờ drone khả dụng. Bạn có thể thử gán lại.');
                    displayDeliveryTracking(updated);
                    clearInterval(window.__deliveryPollInterval);
                    return;
                }
            } else {
                // Once past QUEUED ensure flight path loaded
                if (!deliveryPath || deliveryPath.length === 0) {
                    await ensureDeliveryPath(updated);
                }
                initDeliveryMap(updated);
                updateDroneOnPath(updated);
            }
            displayDeliveryTracking(updated);
        } catch (e) {
            console.warn('Polling delivery failed:', e.message);
        }
    }, baseDelay);
}

// Get delivery status text
function getDeliveryStatusText(status) {
    const statusMap = {
        'QUEUED': 'Đang chờ',
        'ASSIGNED': 'Đã gán drone',
        'LAUNCHED': 'Đang bay',
        'ARRIVING': 'Sắp đến',
        'COMPLETED': 'Đã giao',
        'FAILED': 'Thất bại',
        'RETURNED': 'Đã quay về'
    };
    return statusMap[status] || status;
}

// Close modals
function closeOrderModal() {
    document.getElementById('orderDetailModal').classList.remove('show');
    selectedOrder = null;
}

function closeTrackingModal() {
    document.getElementById('trackingModal').classList.remove('show');
    deliveryInfo = null;
    if (window.__deliveryPollInterval) {
        clearInterval(window.__deliveryPollInterval);
        window.__deliveryPollInterval = null;
    }
}

// Update cart badge
async function updateCartBadge() {
    if (!AuthHelper.isLoggedIn()) {
        document.getElementById('cartBadge').textContent = '0';
        return;
    }

    try {
        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.CART_COUNT);
        const count = response || 0;
        document.getElementById('cartBadge').textContent = count;
    } catch (error) {
        console.error('Error updating cart badge:', error);
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

// Close modal on backdrop click
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) {
        e.target.classList.remove('show');
    }
});
 

