// Orders.js - Orders Page Logic

let selectedOrder = null;
let deliveryInfo = null;
let deliveryPath = [];
// OpenLayers map + layers
let deliveryMap = null;
let deliveryVectorSource = null;
let deliveryVectorLayer = null;
let routeFeature = null;
let droneFeature = null;
let destFeature = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    // Guard against double initialization if this script is accidentally loaded twice
    if (window.__ordersPageInitialized) return;
    window.__ordersPageInitialized = true;
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
    // Always compute a clear, straight path: pickup (store) → dropoff (customer)
    if (deliveryPath && deliveryPath.length) return;
    if (!delivery) return;

    // Dropoff from snapshot
    let dropLat = null, dropLng = null;
    try {
        const snap = delivery && delivery.dropoffAddressSnapshot;
        if (snap && snap.includes('{')) {
            let m;
            m = snap.match(/"lat"\s*:\s*([-0-9.]+)/) || snap.match(/"latitude"\s*:\s*([-0-9.]+)/);
            if (m) dropLat = parseFloat(m[1]);
            m = snap.match(/"lng"\s*:\s*([-0-9.]+)/) || snap.match(/"longitude"\s*:\s*([-0-9.]+)/);
            if (m) dropLng = parseFloat(m[1]);
        }
    } catch(_) {}

    // Pickup from store address
    let storeLat = 10.762622, storeLng = 106.660172;
    try {
        const storeId = delivery.pickupStoreId || (selectedOrder && selectedOrder.storeId);
        if (storeId) {
            const addrList = await APIHelper.get(`/api/stores/${storeId}/addresses`);
            const first = (addrList && addrList.result && addrList.result[0]) || (Array.isArray(addrList) ? addrList[0] : null);
            if (first && typeof first.longitude !== 'undefined' && typeof first.latitude !== 'undefined') {
                storeLng = Number(first.longitude);
                storeLat = Number(first.latitude);
            }
        }
    } catch(e) { console.warn('Could not fetch store address, using defaults'); }

    if (dropLat == null || isNaN(dropLat)) dropLat = storeLat + 0.01;
    if (dropLng == null || isNaN(dropLng)) dropLng = storeLng + 0.01;

    const start = [storeLat, storeLng]; // [lat, lng]
    const end = [dropLat, dropLng];
    const points = [];
    const steps = 40; // denser straight line
    for (let i=0;i<=steps;i++) {
        const t = i/steps;
        const lat = start[0] + (end[0]-start[0])*t;
        const lng = start[1] + (end[1]-start[1])*t;
        points.push([lat,lng]);
    }
    deliveryPath = points;
}

function initDeliveryMap(delivery) {
    const mapEl = document.getElementById('deliveryMap');
    if (!mapEl) return;
    mapEl.style.display = 'block';
    // Nếu thư viện chưa tải (bị chặn Tracking Prevention) thì bỏ qua và sẽ thử lại ở polling
    if (!window.ol) {
        console.warn('OpenLayers library (window.ol) chưa sẵn sàng, bỏ qua initDeliveryMap tạm thời');
        return;
    }

    // Convert [lat,lng] path -> [lon,lat] and project to map coordinates
    const lonLatPath = (deliveryPath || []).map(([lat, lng]) => [Number(lng), Number(lat)]);
    const projPath = lonLatPath.map(([lon, lat]) => ol.proj.fromLonLat([lon, lat]));
    const startCoord = projPath[0] || ol.proj.fromLonLat([106.660172, 10.762622]);

    if (!deliveryMap) {
        // Base OSM layer
        const tileLayer = new ol.layer.Tile({ source: new ol.source.OSM() });
        // Vector source/layer for route + drone
        deliveryVectorSource = new ol.source.Vector();
        deliveryVectorLayer = new ol.layer.Vector({ source: deliveryVectorSource });

        deliveryMap = new ol.Map({
            target: mapEl,
            layers: [tileLayer, deliveryVectorLayer],
            view: new ol.View({ center: startCoord, zoom: 13 })
        });
    } else {
        // If map exists but vector source not, recreate vector layer
        if (!deliveryVectorSource) {
            deliveryVectorSource = new ol.source.Vector();
            deliveryVectorLayer = new ol.layer.Vector({ source: deliveryVectorSource });
            deliveryMap.addLayer(deliveryVectorLayer);
        }
    }

    // Clear previous features
    deliveryVectorSource.clear();

    // Route feature
    if (projPath.length >= 2) {
        routeFeature = new ol.Feature({
            geometry: new ol.geom.LineString(projPath)
        });
        routeFeature.setStyle(new ol.style.Style({
            stroke: new ol.style.Stroke({ color: '#2563eb', width: 4 })
        }));
        deliveryVectorSource.addFeature(routeFeature);
        // Fit view to route
        const extent = routeFeature.getGeometry().getExtent();
        deliveryMap.getView().fit(extent, { padding: [20, 20, 20, 20], maxZoom: 16 });
    }

    // Drone marker feature
    const dronePoint = projPath[0] || startCoord;
    droneFeature = new ol.Feature({
        geometry: new ol.geom.Point(dronePoint)
    });
    droneFeature.setStyle(new ol.style.Style({
        image: new ol.style.Circle({
            radius: 6,
            fill: new ol.style.Fill({ color: '#ff3d00' }),
            stroke: new ol.style.Stroke({ color: '#fff', width: 2 })
        })
    }));
    deliveryVectorSource.addFeature(droneFeature);

    // Destination marker feature (for clarity)
    const destPoint = projPath[projPath.length - 1] || startCoord;
    destFeature = new ol.Feature({ geometry: new ol.geom.Point(destPoint) });
    destFeature.setStyle(new ol.style.Style({
        image: new ol.style.Icon({
            src: 'data:image/svg+xml;utf8,<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\"><circle cx=\"12\" cy=\"12\" r=\"6\" fill=\"%232563eb\"/></svg>'
        })
    }));
    deliveryVectorSource.addFeature(destFeature);

    // Ensure map renders correctly after modal becomes visible
    setTimeout(() => deliveryMap.updateSize(), 0);
}

function updateDroneOnPath(delivery) {
    if (!droneFeature || !deliveryPath.length) return;
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
    const [lat, lng] = deliveryPath[targetIndex];
    const proj = ol.proj.fromLonLat([Number(lng), Number(lat)]);
    droneFeature.getGeometry().setCoordinates(proj);
    // Compute distance from current drone position to destination (meters)
    try {
        if (delivery.currentStatus === 'ARRIVING' && !window.__autoMarkDeliveredTriggered) {
            const [destLat, destLng] = deliveryPath[deliveryPath.length - 1];
            const dLat = (lat - destLat) * Math.PI / 180;
            const dLng = (lng - destLng) * Math.PI / 180;
            const a = Math.sin(dLat/2)**2 + Math.cos(lat*Math.PI/180) * Math.cos(destLat*Math.PI/180) * Math.sin(dLng/2)**2;
            const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
            const distanceMeters = 6371000 * c; // Earth radius meters
            if (distanceMeters < 30) { // threshold for arrival
                window.__autoMarkDeliveredTriggered = true;
                attemptAutoMarkDelivered(delivery);
            }
        }
    } catch(e) {
        console.warn('Distance calc failed', e);
    }
}

async function attemptAutoMarkDelivered(delivery) {
    try {
        // Prefer updating delivery to COMPLETED so backend also updates order status
        console.log('[autoMarkDelivered] attempting completion for delivery', delivery.id);
        const completeResp = await APIHelper.post(`/api/v1/deliveries/${delivery.id}/complete`, {});
        console.log('[autoMarkDelivered] completion response', completeResp);
        // Refresh delivery info to reflect change
        const refreshed = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(delivery.orderId));
        if (refreshed && refreshed.result) {
            deliveryInfo = refreshed.result;
            displayDeliveryTracking(deliveryInfo);
        }
    } catch (e) {
        console.warn('Auto mark delivered failed:', e.message);
        // In dev, try force-complete to keep backend state aligned with the simulation
        const isDevViaProxy = (typeof API_CONFIG !== 'undefined' && API_CONFIG.BASE_URL === '');
        if (isDevViaProxy && delivery && delivery.id) {
            try {
                console.log('[autoMarkDelivered][dev] attempting force-complete', delivery.id);
                await APIHelper.post(`/api/v1/deliveries/dev/${delivery.id}/force-complete`, {});
                const refreshed = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(delivery.orderId));
                if (refreshed && refreshed.result) {
                    deliveryInfo = refreshed.result;
                    displayDeliveryTracking(deliveryInfo);
                    return;
                }
            } catch (fe) {
                console.warn('Force-complete (dev) failed:', fe.message);
            }
        }
        // Allow retry once if backend rejected due to timing; reset flag
        setTimeout(() => { if (deliveryInfo && deliveryInfo.currentStatus === 'ARRIVING') window.__autoMarkDeliveredTriggered = false; }, 4000);
    }
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

    // Prevent duplicate requests if called multiple times
    if (window.__ordersLoadRequested) return;
    window.__ordersLoadRequested = true;
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
        // Mark finished to allow manual refresh when needed
        window.__ordersLoaded = true;
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
                        ${(order.paymentStatus === 'PAID' && !['DELIVERED','CANCELLED','REFUNDED'].includes(order.status)) ? `
                            <button class="btn btn-primary btn-sm" onclick="trackDelivery(${order.id}, '${order.status}', '${order.paymentStatus || ''}')" title="Theo dõi tiến trình giao hàng">
                                <i class="fas fa-location-arrow"></i> Theo dõi đơn
                            </button>
                        ` : ''}
                        ${(order.status === 'DELIVERED') ? `
                            <button class="btn btn-secondary btn-sm" onclick="window.location.href='tracking.html?orderId=${order.id}'" title="Xem kết quả giao hàng">
                                <i class="fas fa-location-arrow"></i> Xem kết quả giao hàng
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
        'PREPARING': 'order-status status-paid',
        'ACCEPT': 'order-status status-paid',
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
        'PREPARING': 'Đang chuẩn bị',
        'ACCEPT': 'Bếp đã nhận',
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
/**
 * Theo dõi đơn hàng. Nếu đơn vẫn ở trạng thái PAID (chưa ACCEPT) và chưa tạo Delivery
 * hiển thị thông báo: "Chưa xem được do bên bếp chưa nhận đơn".
 */
function trackDelivery(orderId, orderStatus, paymentStatus) {
    // Nếu đã giao, hiển thị thông báo hoàn thành ngay tại đây
    if (orderStatus === 'DELIVERED') {
        showOrderCompletedModal(orderId);
        return;
    }
    // Điều hướng trực tiếp sang trang tracking; trang đó sẽ tải order + delivery nếu có
    window.location.href = `tracking.html?orderId=${orderId}`;
}

// Hiển thị modal đơn hàng đã hoàn thành (tại trang orders)
async function showOrderCompletedModal(orderId){
    let order = null;
    try {
        const resp = await APIHelper.get(API_CONFIG.ENDPOINTS.ORDER_BY_ID(orderId));
        order = resp && resp.result ? resp.result : null;
    } catch(_) {}

    const existing = document.getElementById('ordersCompletedModal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.id = 'ordersCompletedModal';
    Object.assign(modal.style, { position:'fixed', inset:'0', background:'rgba(0,0,0,0.45)', display:'flex', alignItems:'center', justifyContent:'center', zIndex:'9999' });
    const card = document.createElement('div');
    Object.assign(card.style, { background:'#fff', borderRadius:'12px', padding:'24px', width:'min(520px,92vw)', boxShadow:'0 12px 32px rgba(0,0,0,0.2)' });
    card.innerHTML = `
        <div style="text-align:center">
            <i class="fas fa-check-circle" style="color:#2e7d32;font-size:3rem;"></i>
            <h2 style="margin:12px 0 4px 0;color:#1b5e20;">Đơn hàng đã hoàn thành</h2>
            <p style="margin:12px 0 0 0;color:#444;">Cảm ơn bạn đã đặt hàng. Chúc ngon miệng!</p>
            <div style="margin-top:16px; color:#666;">
                <div><strong>Mã đơn:</strong> ${order?.orderCode || ('ORD' + (orderId||''))}</div>
                ${order?.updatedAt ? `<div><strong>Hoàn tất:</strong> ${FormatHelper.date(order.updatedAt)}</div>` : ''}
                ${order?.deliveredDroneCode ? `<div><strong>Drone giao:</strong> ${order.deliveredDroneCode}</div>` : ''}
            </div>
            <div style="display:flex; gap:10px; justify-content:center; margin-top:20px;">
                <button id="ordersCompletedView" class="btn btn-primary"><i class="fas fa-box"></i> Xem đơn hàng</button>
                <button id="ordersCompletedTrack" class="btn btn-secondary"><i class="fas fa-location-arrow"></i> Xem kết quả giao hàng</button>
                <button id="ordersCompletedHome" class="btn btn-outline"><i class="fas fa-home"></i> Về trang chủ</button>
            </div>
        </div>`;
    modal.appendChild(card);
    modal.addEventListener('click', (e)=>{ if (e.target===modal) modal.remove(); });
    document.body.appendChild(modal);

    document.getElementById('ordersCompletedView')?.addEventListener('click', ()=>{ modal.remove(); window.location.href='orders.html'; });
    document.getElementById('ordersCompletedTrack')?.addEventListener('click', ()=>{ modal.remove(); window.location.href = `tracking.html?orderId=${orderId}`; });
    document.getElementById('ordersCompletedHome')?.addEventListener('click', ()=>{ modal.remove(); window.location.href='index.html'; });
}

// Display delivery tracking
function displayDeliveryTracking(delivery) {
    const content = document.getElementById('trackingContent');
    // Tạo vùng chứa trạng thái riêng để không ghi đè node bản đồ
    const statusWrap = document.createElement('div');

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
    statusWrap.innerHTML = statusHtml;
    let mapEl = document.getElementById('deliveryMap');
    if (!mapEl) {
        mapEl = document.createElement('div');
        mapEl.id = 'deliveryMap';
        mapEl.style.cssText = 'margin-top:1rem; height:320px; border:1px solid #eee; border-radius:8px; display:block;';
    } else {
        mapEl.style.display = 'block';
    }
    // Thay nội dung bằng status + map (giữ node map ổn định)
    content.replaceChildren(statusWrap, mapEl);

    // Lưu ý: Polling sẽ được kích hoạt một lần trong trackDelivery sau khi hiển thị lần đầu
}

function startDeliveryPolling(deliveryId, status) {
    if (['COMPLETED','FAILED','RETURNED'].includes(status)) return;
    if (window.__deliveryPollInterval) clearInterval(window.__deliveryPollInterval);
    let attempts = 0;
    let delay = status === 'QUEUED' ? 4000 : 1500; // chậm hơn khi đang chờ gán drone
    const tick = async () => {
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
                // Sau 5 lần (~20s) chuyển sang polling chậm thay vì dừng hẳn
                if (attempts === 5) {
                    Toast.warning('Đang chờ drone khả dụng. Hệ thống sẽ tiếp tục theo dõi chậm hơn.');
                    delay = 6000;
                    // reset interval với delay mới
                    clearInterval(window.__deliveryPollInterval);
                    window.__deliveryPollInterval = setInterval(tick, delay);
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
    };
    window.__deliveryPollInterval = setInterval(tick, delay);
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
 

