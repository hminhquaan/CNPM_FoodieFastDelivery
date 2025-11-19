// Cart.js - Shopping Cart Logic

let cartData = null;
// Guard double submit across odd browser events
window.__checkoutInProgress = false;
window.__checkoutInProgressAt = 0;
window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
        window.__checkoutInProgress = false;
    }
});
window.addEventListener('pageshow', () => { // when user navigates back
    window.__checkoutInProgress = false;
});
window.addEventListener('beforeunload', () => {
    window.__checkoutInProgress = false;
});
let checkoutDropoff = null; // { addressId? , lat?, lng?, fullAddress?, label? }
let __map, __mapMarkerLayer, __pickedPoint = null, __pickedAddress = null;
let __searchDebounce;
let __savedAddrs = [];

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
    // Estimate shipping to match backend flat fee (20000 VND per order)
    const shippingFee = (cart.cartItems && cart.cartItems.length > 0) ? 20000 : 0;
    const total = subtotal + shippingFee;

    document.getElementById('subtotal').textContent = FormatHelper.currency(subtotal);
    const feeEl = document.getElementById('shippingFee');
    if (feeEl) feeEl.textContent = FormatHelper.currency(shippingFee);
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
        if (window.__cartMutationInFlight) return;
        window.__cartMutationInFlight = true;
        await APIHelper.delete(API_CONFIG.ENDPOINTS.CART_REMOVE(productId));
        Toast.success('Đã xóa sản phẩm khỏi giỏ hàng');
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

    // Reset stale in-progress state (e.g., user navigated away)
    if (window.__checkoutInProgress && window.__checkoutInProgressAt && (Date.now() - window.__checkoutInProgressAt > 15000)) {
        window.__checkoutInProgress = false;
    }
    if (window.__checkoutInProgress) {
        Toast.info('Đang xử lý thanh toán, vui lòng chờ...');
        return;
    }

    // Preview suitable drone + ETA before confirming
    try {
        await previewDroneForCheckout();
    } catch (e) {
        console.warn('Preview drone failed:', e);
    }

    if (!confirm('Xác nhận tạo đơn hàng?')) {
        return;
    }

    try {
        Loading.show();
        window.__checkoutInProgress = true;
        window.__checkoutInProgressAt = Date.now();
        const response = await APIHelper.post(API_CONFIG.ENDPOINTS.ORDERS, checkoutDropoff || undefined);
        if (response.result && response.result.length > 0) {
            const orders = response.result;
            Toast.success('Tạo đơn hàng thành công!', { duration: 2200 });
            const firstOrder = orders[0];
            setTimeout(async () => {
                try {
                    const paymentResponse = await APIHelper.post(API_CONFIG.ENDPOINTS.PAYMENT_INIT, {
                        orderId: firstOrder.id,
                        provider: 'VNPAY',
                        method: 'QR',
                        returnUrl: window.location.origin + '/home/orders.html'
                    });
                    if (paymentResponse.result && paymentResponse.result.paymentUrl) {
                        window.location.href = paymentResponse.result.paymentUrl;
                    } else {
                        Toast.error('Không thể khởi tạo thanh toán');
                    }
                } catch (error) {
                    console.error('Payment error:', error);
                    Toast.error('Lỗi thanh toán: ' + (error.message || 'Vui lòng thử lại!'));
                    setTimeout(() => { window.location.href = 'orders.html'; }, 2000);
                }
            }, 800);
        } else {
            Toast.error('Không thể tạo đơn hàng');
        }

    } catch (error) {
        console.error('Error creating order:', error);
        Toast.error(error.message || 'Không thể tạo đơn hàng');
    } finally {
        Loading.hide();
        window.__checkoutInProgress = false;
        window.__checkoutInProgressAt = 0;
    }
}

// Preview suitable drone for current cart (first store) and selected dropoff
async function previewDroneForCheckout(){
    try {
        const items = (cartData && cartData.cartItems) ? cartData.cartItems : [];
        if (!items.length) return;

        // Compute approximate payload weight (fallback 250g per item if unknown)
        const weightGram = items.reduce((sum, it) => sum + ((it.weightGram || 250) * (it.quantity || 1)), 0);

        // Resolve dropoff coordinates
        let toLat = null, toLng = null;
        if (checkoutDropoff && typeof checkoutDropoff.lat === 'number' && typeof checkoutDropoff.lng === 'number'){
            toLat = checkoutDropoff.lat; toLng = checkoutDropoff.lng;
        }
        if (!toLat || !toLng){
            // If saved address selected earlier, try to load from server to get lat/lng
            try {
                const user = AuthHelper.getUser();
                if (user && user.id){
                    const res = await APIHelper.get(API_CONFIG.ENDPOINTS.USER_ADDRESSES(user.id));
                    const addrs = (res && res.result) || res || [];
                    const id = checkoutDropoff && checkoutDropoff.addressId;
                    const addr = Array.isArray(addrs) ? addrs.find(a => String(a.id) === String(id)) : null;
                    if (addr && typeof addr.latitude === 'number' && typeof addr.longitude === 'number'){
                        toLat = addr.latitude; toLng = addr.longitude;
                    }
                }
            } catch(_) { /* ignore */ }
        }
        if (!toLat || !toLng){
            // No location selected; skip preview
            return;
        }

        // Resolve store location (match by store name from /api/stores)
        let storeName = items[0].storeName || '';
        let fromLat = null, fromLng = null;
        try {
            const storesResp = await APIHelper.get(API_CONFIG.ENDPOINTS.STORES);
            const stores = (storesResp && storesResp.result) || storesResp || [];
            const store = Array.isArray(stores) ? stores.find(s => (s.name || '').toLowerCase() === (storeName||'').toLowerCase()) : null;
            if (store && store.id){
                const addrList = await APIHelper.get(`/api/stores/${store.id}/addresses`);
                const addrs = (addrList && addrList.result) || addrList || [];
                if (Array.isArray(addrs) && addrs.length){
                    fromLat = addrs[0].latitude; fromLng = addrs[0].longitude;
                }
            }
        } catch(_) { /* ignore */ }
        if (!fromLat || !fromLng){
            // fallback demo coordinates (District 1)
            fromLat = 10.762622; fromLng = 106.660172;
        }

        const qs = new URLSearchParams({
            weightGram: String(weightGram),
            fromLat: String(fromLat), fromLng: String(fromLng),
            toLat: String(toLat), toLng: String(toLng)
        }).toString();
        const resp = await APIHelper.get(`${API_CONFIG.ENDPOINTS.DRONES_CHOOSE}?${qs}`);
        const data = (resp && resp.result) || resp;
        if (data && data.drone && data.drone.code){
            Toast.info(`Drone ${data.drone.code} phù hợp, ETA ~ ${data.etaMinutes} phút`, { duration: 1800 });
        }
    } catch(e){
        console.warn('previewDroneForCheckout error', e);
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

// ========== New: Map picker + geocoding ==========
function initMapPickerIfNeeded(){
    try{
        const mapEl = document.getElementById('mapPicker');
        if (!mapEl) return;
        if (__map) return; // already initialized
        if (!window.ol){
            console.warn('OpenLayers not loaded; map picker will be disabled.');
            return;
        }

        const Map = ol.Map, View = ol.View;
        const TileLayer = ol.layer.Tile, VectorLayer = ol.layer.Vector;
        const OSM = ol.source.OSM, VectorSource = ol.source.Vector;

        __mapMarkerLayer = new VectorLayer({ source: new VectorSource() });
        __map = new Map({
            target: 'mapPicker',
            layers: [ new TileLayer({ source: new OSM() }), __mapMarkerLayer ],
            view: new View({ center: ol.proj.fromLonLat([106.660172,10.762622]), zoom: 13 })
        });

        __map.on('click', async (evt) => {
            const [lon, lat] = ol.proj.toLonLat(evt.coordinate);
            setPickedPoint(lat, lon);
            await reverseGeocode(lat, lon);
        });

        // wire search input
        const input = document.getElementById('addressSearch');
        const box = document.getElementById('addressSuggestions');
        if (input){
            input.addEventListener('input', () => {
                clearTimeout(__searchDebounce);
                const q = input.value.trim();
                if (!q){ box.style.display='none'; box.innerHTML=''; return; }
                __searchDebounce = setTimeout(() => searchAddress(q), 350);
            });
            input.addEventListener('blur', () => setTimeout(()=>{ box.style.display='none'; }, 200));
        }
    }catch(e){ console.warn('initMapPickerIfNeeded error', e); }
}

async function searchAddress(query){
    const box = document.getElementById('addressSuggestions');
    if (!box) return;
    try{
        const url = new URL('https://nominatim.openstreetmap.org/search');
        url.searchParams.set('q', query);
        url.searchParams.set('format', 'jsonv2');
        url.searchParams.set('addressdetails', '1');
        url.searchParams.set('limit', '6');
        url.searchParams.set('accept-language', 'vi');
        url.searchParams.set('countrycodes', 'vn');
        const res = await fetch(url.toString(), { headers: { 'Accept': 'application/json' } });
        const data = await res.json();
        if (!Array.isArray(data) || data.length === 0){ box.style.display='none'; box.innerHTML=''; return; }
        box.innerHTML = data.map((item, idx) => `
            <div onclick="pickSuggestionFromAttr(this)" data-lat="${item.lat}" data-lon="${item.lon}" data-name="${encodeURIComponent(item.display_name)}">
                <i class='fas fa-location-dot' style='color:#888; margin-right:.4rem;'></i>
                ${escapeHtml(item.display_name)}
            </div>
        `).join('');
        box.style.display='block';
    }catch(e){ console.warn('searchAddress error', e); }
}

function pickSuggestionFromAttr(el){
    const lat = parseFloat(el.getAttribute('data-lat'));
    const lon = parseFloat(el.getAttribute('data-lon'));
    const displayName = decodeURIComponent(el.getAttribute('data-name') || '');
    const input = document.getElementById('addressSearch');
    const box = document.getElementById('addressSuggestions');
    if (input){ input.value = displayName; }
    if (box){ box.style.display='none'; box.innerHTML=''; }
    setPickedPoint(lat, lon);
    __pickedAddress = { label: 'Địa chỉ đã chọn', fullAddress: displayName };
}

function setPickedPoint(lat, lon){
    try{
        __pickedPoint = { lat, lon };
        if (!__map || !__mapMarkerLayer || !window.ol) return;
        const Feature = ol.Feature;
        const Point = ol.geom.Point;
        const Style = ol.style.Style, CircleStyle = ol.style.Circle, Fill = ol.style.Fill, Stroke = ol.style.Stroke;
        const VectorSource = ol.source.Vector;
        __mapMarkerLayer.setSource(new VectorSource());
        const feat = new Feature({ geometry: new Point(ol.proj.fromLonLat([lon, lat])) });
        feat.setStyle(new Style({ image: new CircleStyle({ radius: 7, fill: new Fill({ color: '#e74c3c' }), stroke: new Stroke({ color: '#ffffff', width: 2 }) }) }));
        __mapMarkerLayer.getSource().addFeature(feat);
        __map.getView().animate({ center: ol.proj.fromLonLat([lon, lat]), zoom: 16, duration: 300 });
    }catch(e){ console.warn('setPickedPoint error', e); }
}

async function reverseGeocode(lat, lon){
    try{
        const url = new URL('https://nominatim.openstreetmap.org/reverse');
        url.searchParams.set('format', 'jsonv2');
        url.searchParams.set('lat', String(lat));
        url.searchParams.set('lon', String(lon));
        url.searchParams.set('zoom', '18');
        url.searchParams.set('addressdetails', '1');
        url.searchParams.set('accept-language', 'vi');
        const res = await fetch(url.toString(), { headers: { 'Accept': 'application/json' } });
        const data = await res.json();
        const name = data && (data.display_name || data.name);
        if (name){
            const input = document.getElementById('addressSearch');
            if (input) input.value = name;
            __pickedAddress = { label: 'Vị trí bản đồ', fullAddress: name };
        }
    }catch(e){ console.warn('reverseGeocode error', e); }
}

function clearPickedAddress(){
    __pickedPoint = null; __pickedAddress = null;
    if (__mapMarkerLayer && __mapMarkerLayer.getSource){ __mapMarkerLayer.getSource().clear(); }
    const input = document.getElementById('addressSearch');
    if (input) input.value = '';
}

function confirmPickedAddress(){
    if (!__pickedPoint){
        Toast.warning('Vui lòng chọn vị trí trên bản đồ hoặc từ gợi ý địa chỉ');
        return;
    }
    const label = (__pickedAddress && __pickedAddress.label) || 'Điểm giao';
    const full = (__pickedAddress && __pickedAddress.fullAddress) || 'Vị trí đã chọn trên bản đồ';
    const lat = __pickedPoint.lat, lng = __pickedPoint.lon;
    checkoutDropoff = { label, fullAddress: full, lat, lng };
    const display = document.getElementById('dropoffDisplay');
    if (display) display.textContent = `${label}: ${full} (${lat.toFixed(6)}, ${lng.toFixed(6)})`;
    closeAddressModal();
}

function escapeHtml(s) {
    return String(s || '').replace(/[&<>"]+/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
}


// Use browser geolocation to set current position on map
function useMyLocation(){
    if (!('geolocation' in navigator)){
        Toast.error('Trình duyệt không hỗ trợ GPS');
        return;
    }
    Loading.show();
    navigator.geolocation.getCurrentPosition(async (pos)=>{
        try{
            const lat = pos.coords.latitude;
            const lon = pos.coords.longitude;
            setPickedPoint(lat, lon);
            await reverseGeocode(lat, lon);
        } finally {
            Loading.hide();
        }
    }, (err)=>{
        Loading.hide();
        let msg = 'Không thể lấy vị trí. ';
        if (err && err.code === err.PERMISSION_DENIED) msg += 'Bạn đã từ chối quyền truy cập vị trí.';
        else if (err && err.code === err.POSITION_UNAVAILABLE) msg += 'Không có thông tin vị trí.';
        else if (err && err.code === err.TIMEOUT) msg += 'Quá thời gian lấy vị trí.';
        else msg += 'Vui lòng thử lại.';
        Toast.error(msg);
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });
}
// ========== Address modal (open/close + saved addresses) ==========
async function openAddressModal(){
    try {
        const modal = document.getElementById('addressModal');
        if (!modal) return;
        modal.style.display = 'flex';
        // load saved addresses in parallel with map init
        initMapPickerIfNeeded();
        await loadSavedAddresses();
    } catch(e){ console.warn('openAddressModal error', e); }
}

function closeAddressModal(){
    const modal = document.getElementById('addressModal');
    if (modal) modal.style.display = 'none';
}

async function loadSavedAddresses(){
    const list = document.getElementById('addressList');
    if (!list) return;
    try {
        list.innerHTML = '<div class="text-gray">Đang tải địa chỉ...</div>';
        const user = AuthHelper.getUser();
        if (!user || !user.id){ list.innerHTML = '<div class="text-danger">Không xác định người dùng</div>'; return; }
        const res = await APIHelper.get(API_CONFIG.ENDPOINTS.USER_ADDRESSES(user.id));
        const addrs = (res && res.result) || res || [];
        __savedAddrs = Array.isArray(addrs) ? addrs : [];
        if (!__savedAddrs.length){
            list.innerHTML = '<div class="text-gray">Bạn chưa có địa chỉ nào. Hãy thêm ở trang Hồ sơ.</div>';
            return;
        }
        list.innerHTML = __savedAddrs.map(a => {
            const loc = [a.ward, a.district, a.city, a.country].filter(Boolean).join(', ');
            const title = `${escapeHtml(a.label || 'Địa chỉ')}${a.isDefault ? ' <span class="badge badge-success">Mặc định</span>' : ''}`;
            const line = escapeHtml(a.addressLine || '');
            return `
                <div class="mini-row" style="display:flex; justify-content:space-between; align-items:center; gap:.5rem; padding:.45rem .35rem; border-bottom:1px solid #f1f1f1;">
                  <div>
                    <div style="font-weight:600">${title}</div>
                    <div class="text-gray" style="font-size:.9rem">${line}</div>
                    <div class="text-gray" style="font-size:.85rem">${escapeHtml(loc)}</div>
                  </div>
                  <div>
                    <button class="btn btn-outline btn-sm" onclick="chooseSavedAddress(${a.id})"><i class="fas fa-check"></i> Dùng địa chỉ này</button>
                  </div>
                </div>`;
        }).join('');
    } catch(e){
        console.warn('loadSavedAddresses error', e);
        list.innerHTML = '<div class="text-danger">Không thể tải địa chỉ đã lưu</div>';
    }
}

function chooseSavedAddress(addrId){
    try {
        const a = (__savedAddrs || []).find(x => String(x.id) === String(addrId));
        if (!a){ Toast.error('Không tìm thấy địa chỉ'); return; }
        // prefer using addressId for backend to resolve
        checkoutDropoff = { addressId: a.id, lat: a.latitude, lng: a.longitude };
        const loc = [a.ward, a.district, a.city, a.country].filter(Boolean).join(', ');
        const title = a.label || 'Địa chỉ';
        const display = document.getElementById('dropoffDisplay');
        if (display){ display.textContent = `${title}: ${a.addressLine || ''}${loc ? ' - ' + loc : ''}`; }
        closeAddressModal();
    } catch(e){ console.warn('chooseSavedAddress error', e); }
}

