// tracking.js - Dedicated order tracking view after payment

let trackOrder = null;
let trackDeliveryInfo = null;
// Store polyline as array of [lon, lat] to match OpenLayers fromLonLat
let trackDeliveryPath = [];
let trackMap = null;
let trackVectorSource = null;
let trackVectorLayer = null;
let trackRouteFeature = null;
let trackDroneFeature = null;
let trackDestFeature = null;
let trackTrailFeature = null;

// Animation state for simulation
let __animStartTime = null;
let __animSpeedMps = 36; // faster simulation (~130 km/h)
let __animPath = [];// [lon,lat]
let __animCumDist = [];// meters cumulative
let __animRunning = false;
let __animWobble = 0; // 0 = straight line (no curve)
let __autoCompleted = false; // ensure complete is triggered once
let __deferComplete = false; // wait for eligible backend status
let __autoCompleteAttempted = false; // one-time attempt guard
let __lastAttemptStatus = null; // last delivery status we attempted complete for
let __notifiedDeferred = false; // show defer toast once
let __clientForcedComplete = false; // front-end override delivered state when backend rejects

// --- Completion UX helpers ----------------------------------------------------
function stopTrackingAndHideMap(){
    try { __animRunning = false; } catch(_){}
    try {
        const box = document.getElementById('trackingMapContainer');
        if (box) box.style.display = 'none';
    } catch(_){}
}

function showCompletionModal({simulated=false}={}){
    // Remove any existing modal
    const old = document.getElementById('orderCompleteModal');
    if (old) old.remove();

    const modal = document.createElement('div');
    modal.id = 'orderCompleteModal';
    modal.style.position = 'fixed';
    modal.style.inset = '0';
    modal.style.background = 'rgba(0,0,0,0.45)';
    modal.style.display = 'flex';
    modal.style.alignItems = 'center';
    modal.style.justifyContent = 'center';
    modal.style.zIndex = '9999';

    const card = document.createElement('div');
    card.style.background = '#fff';
    card.style.borderRadius = '12px';
    card.style.padding = '24px';
    card.style.width = 'min(520px, 92vw)';
    card.style.boxShadow = '0 12px 32px rgba(0,0,0,0.2)';
    card.innerHTML = `
        <div style="text-align:center">
            <i class="fas fa-check-circle" style="color:#2e7d32;font-size:3rem;"></i>
            <h2 style="margin:12px 0 4px 0;color:#1b5e20;">Đơn hàng đã hoàn thành</h2>
            ${simulated ? '<div style="color:#777; font-size: 0.9rem;">(Mô phỏng)</div>' : ''}
            <p style="margin:12px 0 0 0;color:#444;">Cảm ơn bạn đã đặt hàng. Chúc ngon miệng!</p>
            <div style="margin-top:16px; color:#666;">
                <div><strong>Mã đơn:</strong> ${trackOrder?.orderCode || ('ORD' + (trackOrder?.id||''))}</div>
                ${trackOrder?.updatedAt ? `<div><strong>Hoàn tất:</strong> ${FormatHelper.date(trackOrder.updatedAt)}</div>` : ''}
            </div>
            <div style="display:flex; gap:10px; justify-content:center; margin-top:20px;">
                <button id="btnViewOrders" class="btn btn-primary"><i class="fas fa-box"></i> Xem đơn hàng</button>
                <button id="btnGoHome" class="btn btn-outline"><i class="fas fa-home"></i> Về trang chủ</button>
            </div>
        </div>`;
    modal.appendChild(card);

    modal.addEventListener('click', (e)=>{ if(e.target===modal) modal.remove(); });
    document.body.appendChild(modal);

    // Hide map and stop animation
    stopTrackingAndHideMap();

    // Wire buttons
    document.getElementById('btnViewOrders')?.addEventListener('click', ()=>{
        modal.remove();
        window.location.href = 'orders.html';
    });
    document.getElementById('btnGoHome')?.addEventListener('click', ()=>{
        modal.remove();
        window.location.href = 'index.html';
    });
}

// When page loads, fetch orderId from query string and start tracking
window.addEventListener('DOMContentLoaded', async () => {
    if (!AuthHelper.isLoggedIn()) {
        Toast.warning('Vui lòng đăng nhập để theo dõi đơn hàng');
        setTimeout(() => window.location.href = 'index.html', 1500);
        return;
    }

    const params = new URLSearchParams(window.location.search);
    const orderId = params.get('orderId');
    const speedParam = params.get('speed');
    if (speedParam && !isNaN(parseFloat(speedParam))) {
        __animSpeedMps = Math.max(5, Math.min(200, parseFloat(speedParam)));
    }
    if (!orderId) {
        Toast.error('Thiếu thông tin orderId để theo dõi');
        return;
    }

    await updateCartBadge();
    checkAuthStatus();

    await loadTrackingOrder(orderId);
});

async function loadTrackingOrder(orderId) {
    try {
        Loading.show();
        // 1) Load order
        const orderResp = await APIHelper.get(API_CONFIG.ENDPOINTS.ORDER_BY_ID(orderId));
        trackOrder = orderResp && orderResp.result ? orderResp.result : null;
        if (!trackOrder) {
            Toast.error('Không tìm thấy đơn hàng');
            return;
        }
        renderOrderSummary(trackOrder);
        renderOrderTimeline(trackOrder, null);
        // New details panels
        try { renderOrderDetailsPanel(trackOrder); } catch(_) {}

        // Nếu đơn đã giao: hiển thị modal lần đầu, các lần sau (hoặc khi suppress) chỉ hiển thị thông tin cuối cùng
        const params = new URLSearchParams(window.location.search);
        const suppress = params.get('reopen') === '1';
        const shownKey = 'tracking_completed_shown_' + trackOrder.id;
        const alreadyShown = !!localStorage.getItem(shownKey);
        const isDelivered = trackOrder.status === 'DELIVERED';
        if (isDelivered) {
            if (!alreadyShown && !suppress) {
                stopTrackingAndHideMap();
                showCompletionModal({simulated:false});
                localStorage.setItem(shownKey, '1');
                return; // lần đầu vẫn giữ hành vi cũ
            } else {
                // Lần mở lại: đảm bảo modal bị tắt nếu còn, vẫn tải delivery để gán thông tin drone hoàn tất
                try { document.getElementById('orderCompleteModal')?.remove(); } catch(_){}
            }
        }

        // 2) Load delivery (if exists)
        try {
            const delResp = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(orderId));
            trackDeliveryInfo = delResp && delResp.result ? delResp.result : null;
        } catch (e) {
            console.warn('Không tìm thấy thông tin giao hàng cho orderId', orderId, e.message);
        }

        if (trackDeliveryInfo) {
            await ensureTrackDeliveryPath(trackDeliveryInfo);
            buildSmoothAnimationPath();
            await ensureOpenLayersReady();
            initTrackMap(trackDeliveryInfo);
            // Nếu đã giao thì không cần animation nữa, chỉ hiển thị trạng thái cuối cùng
            if (!isDelivered) {
                startDroneAnimation();
                startTrackPolling(trackDeliveryInfo.id, trackDeliveryInfo.currentStatus);
            } else {
                stopTrackingAndHideMap(); // giữ map ẩn như sau khi hoàn thành (có thể đổi theo yêu cầu UX)
            }
            try { renderDronePanel(trackDeliveryInfo, trackOrder); } catch(_) {}
        } else {
            // Chưa có delivery: tuỳ theo trạng thái order mà show message
            const status = trackOrder.status;
            if (status === 'PAID') {
                Toast.info('Đơn đã thanh toán, đang chờ bếp nhận đơn. Bản đồ sẽ xuất hiện khi drone được tạo.');
            } else if (status === 'ACCEPT') {
                Toast.info('Bếp đang chuẩn bị món ăn. Bản đồ sẽ xuất hiện khi drone được điều phối.');
            }
            // Even without delivery, show requirement estimate if possible
            try { renderDronePanel(null, trackOrder); } catch(_) {}
        }
    } catch (e) {
        console.error('Lỗi load tracking order:', e);
        Toast.error('Không thể tải thông tin theo dõi đơn hàng');
    } finally {
        Loading.hide();
    }
}

// --- Order summary + timeline -------------------------------------------------

function renderOrderSummary(order) {
    const container = document.getElementById('orderSummary');
    const html = `
        <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem;">
            <div>
                <h2 style="margin: 0;">${order.orderCode || ('ORD' + order.id)}</h2>
                <p style="margin: 0.3rem 0; color: var(--gray);">
                    <i class="fas fa-store"></i> ${order.storeName || 'Cửa hàng'}
                </p>
                <p style="margin: 0.3rem 0; color: var(--gray); font-size: 0.9rem;">
                    <i class="fas fa-calendar"></i> ${FormatHelper.date(order.createdAt)}
                </p>
            </div>
            <div style="text-align: right;">
                <div>${getOrderStatusBadge(order)}</div>
                <div style="margin-top: 0.5rem; font-weight: bold; font-size: 1.1rem;">
                    Tổng: <span class="text-primary">${FormatHelper.currency(order.totalPayable || order.totalAmount)}</span>
                </div>
            </div>
        </div>
    `;
    container.innerHTML = html;
}

function getOrderStatusBadge(order) {
    const statusClass = getOrderStatusClass(order.status);
    const statusText = getOrderStatusText(order.status);
    return `<span class="${statusClass}">${statusText}</span>`;
}

// Status helpers (mirrored from orders.js)
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

// --- Details panels (Order + Drone) -----------------------------------------

function renderOrderDetailsPanel(order){
    const el = document.getElementById('orderDetailsBox');
    if (!el || !order) return;
    const items = Array.isArray(order.items) ? order.items : [];
    const rows = items.length ? items.map(it => `
        <div style="display:flex; justify-content:space-between; padding:6px 0; border-bottom:1px solid var(--light);">
            <div>
                <div style="font-weight:500;">${it.productName || 'Món'}</div>
                <div class="text-gray" style="font-size:0.9rem;">${FormatHelper.currency(it.unitPrice||0)} x ${it.quantity||1}</div>
            </div>
            <div style="font-weight:600; color:var(--primary-color);">${FormatHelper.currency(it.totalPrice||0)}</div>
        </div>
    `).join('') : '<div class="empty-state"><i class="fas fa-box-open"></i> Không có sản phẩm</div>';
    const shipping = order.shippingFee || 0;
    const discount = order.discountAmount || 0;
    const subtotal = order.totalItemAmount || (items.reduce((s,it)=> s + (Number(it.totalPrice)||0), 0));
    const total = order.totalPayable || order.totalAmount || (subtotal + shipping - discount);
    el.innerHTML = `
        <div style="display:grid; gap:10px;">
            <div style="display:flex; justify-content:space-between; color:var(--gray);">
                <span><i class="fas fa-store"></i> ${order.storeName || 'Cửa hàng'}</span>
                <span><i class="fas fa-calendar"></i> ${FormatHelper.date(order.createdAt)}</span>
            </div>
            ${rows}
            <div style="display:grid; gap:6px; margin-top:6px;">
                <div style="display:flex; justify-content:space-between;"><span class="text-gray">Tạm tính:</span><span>${FormatHelper.currency(subtotal)}</span></div>
                <div style="display:flex; justify-content:space-between;"><span class="text-gray">Phí vận chuyển:</span><span>${FormatHelper.currency(shipping)}</span></div>
                <div style="display:flex; justify-content:space-between;"><span class="text-gray">Giảm giá:</span><span class="text-success">-${FormatHelper.currency(discount)}</span></div>
                <div style="display:flex; justify-content:space-between; border-top:2px solid var(--light); padding-top:8px; font-weight:700;">
                    <span>Tổng cộng:</span><span class="text-primary">${FormatHelper.currency(total)}</span>
                </div>
            </div>
        </div>`;
}

function getDroneStatusText(status){
    const map = {
        'AVAILABLE': 'Sẵn sàng',
        'IN_FLIGHT': 'Đang bay',
        'ASSIGNED': 'Đã gán',
        'CHARGING': 'Đang sạc',
        'MAINTENANCE': 'Bảo trì',
        'BUSY': 'Bận'
    };
    return map[status] || status || '—';
}

async function renderDronePanel(deliveryOrNull, order){
    const box = document.getElementById('droneDetailsBox');
    if (!box) return;

    // Helper: estimate distance and battery
    const estimateFromOrderOrDelivery = async ()=>{
        try {
            // Store coords
            let storeLon = 106.660172, storeLat = 10.762622;
            const storeId = (deliveryOrNull && deliveryOrNull.pickupStoreId) || (order && order.storeId);
            if (storeId){
                try {
                    const addrList = await APIHelper.get(`/api/stores/${storeId}/addresses`);
                    const first = (addrList && addrList.result && addrList.result[0]) || (Array.isArray(addrList) ? addrList[0] : null);
                    if (first && typeof first.longitude !== 'undefined' && typeof first.latitude !== 'undefined'){
                        storeLon = Number(first.longitude); storeLat = Number(first.latitude);
                    }
                } catch(_) {}
            }
            // Dropoff from snapshot (prefer delivery, else order)
            let dropLat = null, dropLon = null;
            let snap = (deliveryOrNull && deliveryOrNull.dropoffAddressSnapshot) || (order && order.deliveryAddressSnapshot) || null;
            if (snap && typeof snap === 'string' && snap.includes('{')){
                let m = snap.match(/"lat"\s*:\s*([-0-9.]+)/) || snap.match(/"latitude"\s*:\s*([-0-9.]+)/);
                if (m) dropLat = parseFloat(m[1]);
                m = snap.match(/"lng"\s*:\s*([-0-9.]+)/) || snap.match(/"longitude"\s*:\s*([-0-9.]+)/);
                if (m) dropLon = parseFloat(m[1]);
            }
            if (dropLat == null || isNaN(dropLat)) dropLat = storeLat + 0.01;
            if (dropLon == null || isNaN(dropLon)) dropLon = storeLon + 0.01;
            const km = haversineMeters([storeLon, storeLat],[dropLon, dropLat]) / 1000;
            const need = estimateBatteryForDistance(km);
            return { km, need };
        } catch(_) { return null; }
    };

    // If delivery exists and has droneCode → fetch drone detail
    if (deliveryOrNull && deliveryOrNull.droneCode){
        try {
            const resp = await APIHelper.get(API_CONFIG.ENDPOINTS.DRONE_BY_CODE(deliveryOrNull.droneCode));
            const d = resp?.result || resp || null;
            if (!d) throw new Error('No drone');
            const battery = (d.currentBatteryPercent != null) ? `${d.currentBatteryPercent}%` : '—';
            const loc = (d.lastLatitude != null && d.lastLongitude != null) ? `${d.lastLatitude}, ${d.lastLongitude}` : '—';
            const tele = d.lastTelemetryAt ? new Date(d.lastTelemetryAt).toLocaleString('vi-VN') : '—';
            box.innerHTML = `
                <div style="display:grid; gap:8px;">
                    <div style="display:flex; justify-content:space-between;"><span class="text-gray">Mã drone:</span><strong>${d.code}</strong></div>
                    <div style="display:flex; justify-content:space-between;"><span class="text-gray">Trạng thái:</span><span class="text-primary">${getDroneStatusText(d.status)}</span></div>
                    <div style="display:flex; justify-content:space-between;"><span class="text-gray">Pin hiện tại:</span><span>${battery}</span></div>
                                    ${deliveryOrNull && deliveryOrNull.batteryUsedPercent != null && String(deliveryOrNull.currentStatus)==='COMPLETED' ? `<div style="display:flex; justify-content:space-between;"><span class="text-gray">Pin đã sử dụng:</span><span class="${batteryUsageClass(deliveryOrNull.batteryUsedPercent)}">${deliveryOrNull.batteryUsedPercent}%</span></div>` : ''}
                    ${deliveryOrNull && deliveryOrNull.distanceKm != null && String(deliveryOrNull.currentStatus)==='COMPLETED' ? `<div style="display:flex; justify-content:space-between;"><span class="text-gray">Quãng đường:</span><span>${FormatHelper.distance(deliveryOrNull.distanceKm)}</span></div>` : ''}
                    ${deliveryOrNull && deliveryOrNull.actualFlightTimeSeconds != null && String(deliveryOrNull.currentStatus)==='COMPLETED' ? `<div style="display:flex; justify-content:space-between;"><span class="text-gray">Thời gian bay:</span><span>${formatFlightTime(deliveryOrNull.actualFlightTimeSeconds)}</span></div>` : ''}
                    <div style="display:flex; justify-content:space-between;"><span class="text-gray">Drone Station:</span><span>${loc}</span></div>
                    <div style="display:flex; justify-content:space-between;"><span class="text-gray">Cập nhật:</span><span>${tele}</span></div>
                </div>`;
            return;
        } catch(e){
            console.warn('Fetch drone detail failed', e?.message);
        }
    }

    // No assigned drone yet → show estimate if possible
    const est = await estimateFromOrderOrDelivery();
    if (est){
        box.innerHTML = `
            <div style="display:grid; gap:8px;">
                <div class="text-gray">Chưa có drone được gán.</div>
                <div>Quãng đường ước tính: <strong>${FormatHelper.distance(est.km)}</strong></div>
                <div>Pin cần tối thiểu (ước tính): <strong class="text-primary">${est.need}%</strong></div>
                <div class="text-gray" style="font-size:0.9rem;">Bao gồm dự phòng an toàn.</div>
            </div>`;
    } else {
        box.innerHTML = '<div class="empty-state"><i class="fas fa-info-circle"></i> Chưa có thông tin drone</div>';
    }
}

function estimateBatteryForDistance(km){
    if (!isFinite(km) || km <= 0) return 25;
    // Match backend: ~12%/km + 15% safety, clamp 25..100
    const usage = Math.ceil(km * 12 + 15);
    return Math.max(25, Math.min(100, usage));
}

function formatFlightTime(seconds){
    if (!isFinite(seconds) || seconds < 0) return '0s';
    if (seconds < 60) return `${Math.round(seconds)}s`;
    const m = Math.floor(seconds / 60);
    const s = Math.round(seconds % 60);
    if (m < 60) return s ? `${m}m ${s}s` : `${m}m`;
    const h = Math.floor(m / 60);
    const mm = m % 60;
    return mm ? `${h}h ${mm}m` : `${h}h`;
}

function batteryUsageClass(pct){
    const v = Number(pct);
    if (!isFinite(v)) return 'text-gray';
    if (v <= 30) return 'text-success';
    if (v <= 60) return 'text-warning';
    return 'text-danger';
}

function deriveOrderStage(order, delivery) {
    const status = order.status;
    const paymentStatus = order.paymentStatus;

    // Terminal states
    if (status === 'CANCELLED') return 'CANCELLED';
    if (status === 'REFUNDED') return 'REFUNDED';

    // Completed (including client-side forced)
    if (__clientForcedComplete || status === 'DELIVERED' || (delivery && (delivery.currentStatus === 'COMPLETED' || delivery.currentStatus === 'COMPLETED_SIM'))) {
        return 'COMPLETED';
    }

    if (status === 'IN_DELIVERY') return 'DELIVERING';
    if (status === 'ACCEPT' || status === 'PREPARING') return 'PREPARING';
    if (status === 'PAID') return 'WAITING_CONFIRM';

    // Default
    return 'ORDERED';
}

function renderOrderTimeline(order, delivery) {
    const stage = deriveOrderStage(order, delivery);
    const container = document.getElementById('orderTimeline');

    if (stage === 'CANCELLED' || stage === 'REFUNDED') {
        const isRefunded = stage === 'REFUNDED';
        container.innerHTML = `
            <div class="card" style="border: 1px solid ${isRefunded ? '#1e88e5' : '#f44336'}; background: ${isRefunded ? '#E3F2FD' : '#FFEBEE'};">
                <div class="card-body text-center">
                    <i class="fas ${isRefunded ? 'fa-money-bill-wave' : 'fa-ban'}" style="font-size: 3rem; color: ${isRefunded ? '#1e88e5' : '#f44336'};"></i>
                    <h3 style="margin-top: 1rem; color: ${isRefunded ? '#0d47a1' : '#c62828'};">
                        ${isRefunded ? 'Đơn hàng đã được hoàn tiền' : 'Đơn hàng đã bị huỷ'}
                    </h3>
                </div>
            </div>
        `;
        return;
    }

    const steps = [
        { key: 'ORDERED', label: 'Đã đặt hàng', icon: 'list-alt' },
        { key: 'WAITING_CONFIRM', label: 'Chờ xác nhận', icon: 'hourglass-half' },
        { key: 'PREPARING', label: 'Đang chuẩn bị', icon: 'utensils' },
        { key: 'DELIVERING', label: 'Đang giao', icon: 'truck' },
        { key: 'COMPLETED', label: 'Hoàn thành', icon: 'check-circle' }
    ];

    const currentIndex = steps.findIndex(s => s.key === stage);

    // Inject minimal CSS once for horizontal stepper
    if (!document.getElementById('ff-stepper-styles')) {
        const style = document.createElement('style');
        style.id = 'ff-stepper-styles';
        style.textContent = `
        .ff-stepper{display:grid;grid-template-columns:repeat(${steps.length},1fr);gap:10px;align-items:center}
        .ff-step{position:relative;text-align:center}
        .ff-dot{width:36px;height:36px;border-radius:50%;margin:0 auto 8px;display:flex;align-items:center;justify-content:center;border:2px solid var(--line);background:var(--surface)}
        .ff-label{font-size:.85rem;color:var(--gray)}
        .ff-line{position:absolute;top:18px;left:50%;right:-50%;height:4px;background:var(--line);z-index:0}
        .ff-step:last-child .ff-line{display:none}
        .ff-step.completed .ff-dot{background:var(--primary-color);color:#fff;border-color:var(--primary-color)}
        .ff-step.completed .ff-label{color:var(--primary-color)}
        .ff-step.active .ff-dot{background:linear-gradient(90deg,var(--primary-color),#7ea0ff);color:#fff;border-color:transparent}
        .ff-step.active .ff-label{color:var(--primary-color);font-weight:600}
        .ff-line-fill{position:absolute;top:18px;left:50%;height:4px;background:var(--primary-color);z-index:1}
        `;
        document.head.appendChild(style);
    }

    let html = `
        <div class="ff-stepper">
            ${steps.map((s, i) => {
                const state = i < currentIndex ? 'completed' : (i === currentIndex ? 'active' : '');
                const fillWidth = i < currentIndex ? '100%' : (i === currentIndex ? '50%' : '0');
                return `
                <div class="ff-step ${state}">
                    <div class="ff-dot"><i class="fas fa-${s.icon}"></i></div>
                    <div class="ff-label">${s.label}</div>
                    <div class="ff-line"></div>
                    <div class="ff-line-fill" style="width:${fillWidth};"></div>
                </div>`;
            }).join('')}
        </div>
    `;

    container.innerHTML = html;
}

// --- Map tracking (based on orders.js) ---------------------------------------

async function ensureTrackDeliveryPath(delivery) {
    // Always compute a clear, straight path: pickup (store) → dropoff (customer)
    if (trackDeliveryPath && trackDeliveryPath.length) return;
    if (!delivery) return;

    // Derive dropoff from snapshot
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
    } catch (_) {}

    // Pickup coordinates from store address
    let storeLon = 106.660172, storeLat = 10.762622; // default center
    try {
        const storeId = delivery.pickupStoreId || (trackOrder && trackOrder.storeId);
        if (storeId) {
            const addrList = await APIHelper.get(`/api/stores/${storeId}/addresses`);
            const first = (addrList && addrList.result && addrList.result[0]) || (Array.isArray(addrList) ? addrList[0] : null);
            if (first && typeof first.longitude !== 'undefined' && typeof first.latitude !== 'undefined') {
                storeLon = Number(first.longitude);
                storeLat = Number(first.latitude);
            }
        }
    } catch (e) {
        console.warn('Could not fetch store address for pickup, using defaults');
    }

    if (dropLat == null || isNaN(dropLat)) dropLat = storeLat + 0.01;
    if (dropLng == null || isNaN(dropLng)) dropLng = storeLon + 0.01;

    const start = [storeLon, storeLat];
    const end = [dropLng, dropLat];
    const points = [];
    const stepsN = 40; // denser for smoother but straight line
    for (let i = 0; i <= stepsN; i++) {
        const t = i / stepsN;
        const lon = start[0] + (end[0] - start[0]) * t;
        const lat = start[1] + (end[1] - start[1]) * t;
        points.push([lon, lat]);
    }
    trackDeliveryPath = points;
}

function initTrackMap(delivery) {
    const mapEl = document.getElementById('deliveryMap');
    if (!mapEl) return;
    mapEl.style.display = 'block';
    if (!window.ol) {
        console.warn('OpenLayers (window.ol) chưa sẵn sàng ở tracking view');
        Toast.info('Đang tải thư viện bản đồ, vui lòng thử lại sau.');
        return;
    }

    const projPath = (trackDeliveryPath || []).map(([lon, lat]) => ol.proj.fromLonLat([Number(lon), Number(lat)]));
    const startCoord = projPath[0] || ol.proj.fromLonLat([106.660172, 10.762622]);

    if (!trackMap) {
        const tileLayer = new ol.layer.Tile({ source: new ol.source.OSM() });
        trackVectorSource = new ol.source.Vector();
        trackVectorLayer = new ol.layer.Vector({ source: trackVectorSource });
        trackMap = new ol.Map({
            target: mapEl,
            layers: [tileLayer, trackVectorLayer],
            view: new ol.View({ center: startCoord, zoom: 13 })
        });
    } else {
        if (!trackVectorSource) {
            trackVectorSource = new ol.source.Vector();
            trackVectorLayer = new ol.layer.Vector({ source: trackVectorSource });
            trackMap.addLayer(trackVectorLayer);
        }
    }

    trackVectorSource.clear();

    if (projPath.length >= 2) {
        trackRouteFeature = new ol.Feature({
            geometry: new ol.geom.LineString(projPath)
        });
        trackRouteFeature.setStyle(new ol.style.Style({
            stroke: new ol.style.Stroke({ color: '#2563eb', width: 4 })
        }));
        trackVectorSource.addFeature(trackRouteFeature);
        const extent = trackRouteFeature.getGeometry().getExtent();
        trackMap.getView().fit(extent, { padding: [20, 20, 20, 20], maxZoom: 16 });
    }

    const dronePoint = projPath[0] || startCoord;
    trackDroneFeature = new ol.Feature({ geometry: new ol.geom.Point(dronePoint) });
    trackDroneFeature.setStyle(droneIconStyle(0));
    trackVectorSource.addFeature(trackDroneFeature);

    const destPoint = projPath[projPath.length - 1] || startCoord;
    trackDestFeature = new ol.Feature({ geometry: new ol.geom.Point(destPoint) });
    trackDestFeature.setStyle(new ol.style.Style({
        image: new ol.style.Icon({
            src: 'data:image/svg+xml;utf8,<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\"><circle cx=\"12\" cy=\"12\" r=\"6\" fill=\"%232563eb\"/></svg>'
        })
    }));
    trackVectorSource.addFeature(trackDestFeature);

    // Trail showing travelled segment
    trackTrailFeature = new ol.Feature({ geometry: new ol.geom.LineString([dronePoint]) });
    trackTrailFeature.setStyle(new ol.style.Style({
        stroke: new ol.style.Stroke({ color: '#34d399', width: 3, lineCap: 'round' })
    }));
    trackVectorSource.addFeature(trackTrailFeature);

    setTimeout(() => trackMap.updateSize(), 0);
}

function updateTrackDrone(delivery) {
    if (!trackDroneFeature || !trackDeliveryPath.length || !window.ol) return;
    const statusOrder = ['QUEUED', 'ASSIGNED', 'LAUNCHED', 'ARRIVING', 'COMPLETED'];
    const idx = statusOrder.indexOf(delivery.currentStatus);
    if (idx < 0) return;

    let fraction = idx / (statusOrder.length - 1);
    if (delivery.actualDepartureTime && ['LAUNCHED', 'ARRIVING', 'COMPLETED'].includes(delivery.currentStatus)) {
        try {
            const dep = new Date(delivery.actualDepartureTime).getTime();
            const now = Date.now();
            const elapsed = (now - dep) / 1000;
            const estTotal = trackDeliveryPath.length * 5;
            const dynamicProgress = Math.min(1, elapsed / estTotal);
            fraction = Math.max(fraction, dynamicProgress);
        } catch (_) {}
    }

    const targetIndex = Math.min(trackDeliveryPath.length - 1, Math.round(fraction * (trackDeliveryPath.length - 1)));
    const [lon, lat] = trackDeliveryPath[targetIndex];
    const proj = ol.proj.fromLonLat([Number(lon), Number(lat)]);
    trackDroneFeature.getGeometry().setCoordinates(proj);
}

function startTrackPolling(deliveryId, status) {
    if (['COMPLETED', 'FAILED', 'RETURNED'].includes(status)) return;
    if (window.__trackPollInterval) clearInterval(window.__trackPollInterval);
    let attempts = 0;
    let delay = status === 'QUEUED' ? 4000 : 1500;

    const tick = async () => {
        try {
            const res = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(trackOrder.id));
            const updated = res.result;
            if (!updated) return;
            trackDeliveryInfo = updated;

            // update timeline dựa theo order + delivery mới
            renderOrderTimeline(trackOrder, trackDeliveryInfo);
            try { renderDronePanel(trackDeliveryInfo, trackOrder); } catch(_) {}

            if (['COMPLETED', 'FAILED', 'RETURNED'].includes(updated.currentStatus)) {
                updateTrackDrone(updated);
                clearInterval(window.__trackPollInterval);
                return;
            }

            if (updated.currentStatus === 'QUEUED') {
                attempts++;
                if (attempts === 5) {
                    Toast.warning('Đang chờ drone khả dụng. Hệ thống sẽ theo dõi chậm hơn.');
                    delay = 6000;
                    clearInterval(window.__trackPollInterval);
                    window.__trackPollInterval = setInterval(tick, delay);
                }
            } else {
                if (!trackDeliveryPath || trackDeliveryPath.length === 0) {
                    await ensureTrackDeliveryPath(updated);
                    buildSmoothAnimationPath();
                }
                if (!trackMap) {
                    try { await ensureOpenLayersReady(); } catch(_) {}
                    initTrackMap(updated);
                }
                if (!__animRunning) {
                    startDroneAnimation();
                }
                // If arrival happened earlier but backend wasn't eligible, try once when eligible and only on status change
                // If we previously deferred and backend status changed, no longer needed: completion now handled purely by arrival animation.
            }
        } catch (e) {
            console.warn('Polling tracking view lỗi:', e.message);
        }
    };

    window.__trackPollInterval = setInterval(tick, delay);
}

// --- Shared helpers reused from other pages ----------------------------------

async function updateCartBadge() {
    if (!AuthHelper.isLoggedIn()) {
        const el = document.getElementById('cartBadge');
        if (el) el.textContent = '0';
        return;
    }
    try {
        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.CART_COUNT);
        const count = response || 0;
        const el = document.getElementById('cartBadge');
        if (el) el.textContent = count;
    } catch (error) {
        console.error('Error updating cart badge (tracking):', error);
    }
}

function checkAuthStatus() {
    const isLoggedIn = AuthHelper.isLoggedIn();
    const guestMenu = document.getElementById('guestMenu');
    const userDropdown = document.getElementById('userDropdown');

    if (isLoggedIn) {
        const user = AuthHelper.getUser();
        if (guestMenu) guestMenu.style.display = 'none';
        if (userDropdown) userDropdown.style.display = 'block';
        const userNameEl = document.getElementById('userName');
        if (userNameEl) userNameEl.textContent = user.username || user.fullName || 'User';
    }
}

function toggleDropdown(evt) {
    try { evt?.stopPropagation?.(); } catch(_) {}
    const dropdown = document.getElementById('dropdownMenu');
    const avatar = document.getElementById('userAvatar');
    if (dropdown) {
        dropdown.classList.toggle('show');
        if (avatar) avatar.setAttribute('aria-expanded', dropdown.classList.contains('show') ? 'true' : 'false');
    }
}

function logout() {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        AuthHelper.logout();
    }
}

// Close dropdown when clicking outside
window.addEventListener('click', (e) => {
    const dropdown = document.getElementById('dropdownMenu');
    const avatar = document.getElementById('userAvatar');
    if (dropdown && avatar && !avatar.contains(e.target)) {
        dropdown.classList.remove('show');
    }
});

// ---------- Simulation helpers ----------
function buildSmoothAnimationPath() {
    const base = (trackDeliveryPath || []).map(p=>[Number(p[0]), Number(p[1])]);
    if (base.length < 2){ __animPath = base; __animCumDist = [0]; return; }
    const smooth = [];
    const offsetScale = __animWobble; // zero = straight line
    for (let i = 0; i < base.length - 1; i++) {
        const A = base[i], B = base[i + 1];
        const [lon1, lat1] = A; const [lon2, lat2] = B;
        const dx = lon2 - lon1, dy = lat2 - lat1;
        const len = Math.sqrt(dx*dx + dy*dy) || 1e-9;
        const ux = -dy/len, uy = dx/len;
        const steps = 28; // densify for smooth animation
        for (let s = 0; s < steps; s++) {
            const t = s/steps;
            let lon = lon1 + dx*t;
            let lat = lat1 + dy*t;
            const wobble = Math.sin(Math.PI*t) * offsetScale;
            lon += ux * wobble;
            lat += uy * wobble;
            smooth.push([lon, lat]);
        }
    }
    smooth.push(base[base.length-1]);
    __animPath = smooth;
    __animCumDist = [0];
    for (let i=1;i<smooth.length;i++){
        __animCumDist.push(__animCumDist[i-1] + haversineMeters(smooth[i-1], smooth[i]));
    }
}

function startDroneAnimation(){
    if (!__animPath.length || !trackDroneFeature || !window.ol) return;
    __animStartTime = performance.now();
    __animRunning = true;
    const totalDist = __animCumDist[__animCumDist.length-1] || 1;
    const loop = (now)=>{
        if (!__animRunning) return;
        const elapsed = (now - __animStartTime)/1000;
        const travelled = Math.min(totalDist, elapsed * __animSpeedMps);
        let idx = binarySearchCum(__animCumDist, travelled);
        // finalize when very close to end or index at last segment
        const nearEnd = (totalDist - travelled) < 2 || idx >= __animPath.length-2;
        if (nearEnd){
            // snap to destination, draw full trail, and mark arrival
            const dest = __animPath[__animPath.length-1];
            const destProj = ol.proj.fromLonLat(dest);
            trackDroneFeature.getGeometry().setCoordinates(destProj);
            try { trackTrailFeature.getGeometry().setCoordinates(__animPath.map(([lo,la])=> ol.proj.fromLonLat([lo,la]))); } catch(_){ }
            __animRunning = false;
                if (!__autoCompleted){
                    __autoCompleted = true;
                    tryAutoCompleteOnArrival(false);
                }
            return;
        }
        const curr = __animPath[idx];
        const next = __animPath[idx+1];
        const segLen = __animCumDist[idx+1]-__animCumDist[idx] || 1;
        const remain = travelled - __animCumDist[idx];
        const k = Math.max(0, Math.min(1, remain/segLen));
        const lon = curr[0] + (next[0]-curr[0])*k;
        const lat = curr[1] + (next[1]-curr[1])*k;
        const bearing = computeBearing(curr, next);
        const point = ol.proj.fromLonLat([lon, lat]);
        trackDroneFeature.getGeometry().setCoordinates(point);
        trackDroneFeature.setStyle(droneIconStyle(bearing));
        try {
            const slice = __animPath.slice(0, idx+1).map(([lo,la])=> ol.proj.fromLonLat([lo,la]));
            trackTrailFeature.getGeometry().setCoordinates(slice);
        } catch(_){}
        requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
}

function droneIconStyle(bearingDeg){
    // Represent drone as a simple dot (circle) without text label
    return [new ol.style.Style({
        image: new ol.style.Circle({
            radius: 6,
            fill: new ol.style.Fill({ color: '#2563eb' }),
            stroke: new ol.style.Stroke({ color: '#ffffff', width: 2 })
        })
    })];
}

function binarySearchCum(arr, val){
    let lo=0, hi=arr.length-1;
    while (lo < hi){
        const mid = Math.floor((lo+hi+1)/2);
        if (arr[mid] <= val) lo = mid; else hi = mid-1;
    }
    return lo;
}

function haversineMeters(a, b){
    const [lon1, lat1] = a.map(Number); const [lon2, lat2] = b.map(Number);
    const R = 6371000;
    const dLat = (lat2-lat1) * Math.PI/180;
    const dLon = (lon2-lon1) * Math.PI/180;
    const s1 = Math.sin(dLat/2), s2 = Math.sin(dLon/2);
    const A = s1*s1 + Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*s2*s2;
    return 2 * R * Math.atan2(Math.sqrt(A), Math.sqrt(1-A));
}

function computeBearing(a, b){
    const [lon1, lat1] = a.map(Number); const [lon2, lat2] = b.map(Number);
    const φ1 = lat1*Math.PI/180, φ2 = lat2*Math.PI/180;
    const λ1 = lon1*Math.PI/180, λ2 = lon2*Math.PI/180;
    const y = Math.sin(λ2-λ1) * Math.cos(φ2);
    const x = Math.cos(φ1)*Math.sin(φ2) - Math.sin(φ1)*Math.cos(φ2)*Math.cos(λ2-λ1);
    const θ = Math.atan2(y, x);
    return (θ*180/Math.PI + 360) % 360;
}

// Delivery eligibility gating removed: front-end simulation decides arrival.
function isDeliveryEligibleForComplete(status){ return true; }

async function tryAutoCompleteOnArrival(fromPolling=false){
    try {
        if (!trackDeliveryInfo) return;
        __lastAttemptStatus = trackDeliveryInfo.currentStatus;
        await APIHelper.post(`/api/v1/deliveries/${trackDeliveryInfo.id}/complete`, {});
        const refreshed = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(trackOrder.id));
        if (refreshed && refreshed.result){
            trackDeliveryInfo = refreshed.result;
        }
        // Also refresh order to reflect DELIVERED status if backend updates it
        try {
            const orderResp = await APIHelper.get(API_CONFIG.ENDPOINTS.ORDER_BY_ID(trackOrder.id));
            if (orderResp && orderResp.result) {
                trackOrder = orderResp.result;
            }
        } catch(_) {}
        renderOrderSummary(trackOrder);
        renderOrderTimeline(trackOrder, trackDeliveryInfo);
        Toast.success('Giao hàng thành công!');
        showCompletionModal({simulated:false});
    } catch(e){
        console.warn('Auto-complete on arrival failed', e.message);
        // In dev environment, attempt force-complete to keep backend in sync
        const isDevViaProxy = (typeof API_CONFIG !== 'undefined' && API_CONFIG.BASE_URL === '');
        if (isDevViaProxy) {
            try {
                await APIHelper.post(`/api/v1/deliveries/dev/${trackDeliveryInfo.id}/force-complete`, {});
                const refreshed = await APIHelper.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(trackOrder.id));
                if (refreshed && refreshed.result){ trackDeliveryInfo = refreshed.result; }
                try {
                    const orderResp = await APIHelper.get(API_CONFIG.ENDPOINTS.ORDER_BY_ID(trackOrder.id));
                    if (orderResp && orderResp.result) { trackOrder = orderResp.result; }
                } catch(_) {}
                renderOrderSummary(trackOrder);
                renderOrderTimeline(trackOrder, trackDeliveryInfo);
                // Dev-only success toast removed per request; rely on completion modal
                showCompletionModal({simulated:false});
                return;
            } catch (fe) {
                console.warn('Force-complete (dev) failed', fe.message);
            }
        }
        if (!__clientForcedComplete){
            __clientForcedComplete = true;
            Toast.info('Đã đến nơi. Backend từ chối xác nhận. Đánh dấu hoàn thành cục bộ (mô phỏng).');
            renderOrderTimeline(trackOrder, trackDeliveryInfo);
            showCompletionModal({simulated:true});
        }
    }
}

// Wait until OpenLayers is available (loader is async)
function ensureOpenLayersReady(timeoutMs = 5000){
    if (window.ol) return Promise.resolve();
    return new Promise((resolve, reject)=>{
        const start = Date.now();
        const itv = setInterval(()=>{
            if (window.ol){ clearInterval(itv); resolve(); }
            else if (Date.now() - start > timeoutMs){ clearInterval(itv); reject(new Error('OpenLayers not ready')); }
        }, 100);
    });
}
