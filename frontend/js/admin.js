// Admin JavaScript
document.addEventListener('DOMContentLoaded', async () => {
    // Single shared charging station for all drones
    window.DRONE_STATION = { lat: 10.776, lng: 106.700 };
    const adminMain = document.querySelector('.admin-main');
    const originalContent = adminMain ? adminMain.innerHTML : '';

    // Unified token/user keys (support both legacy and new STORAGE_KEYS)
    const token = localStorage.getItem('authToken')
        || (typeof STORAGE_KEYS !== 'undefined' && STORAGE_KEYS.TOKEN ? localStorage.getItem(STORAGE_KEYS.TOKEN) : null);
    const userRaw = localStorage.getItem('user')
        || (typeof STORAGE_KEYS !== 'undefined' && STORAGE_KEYS.USER ? localStorage.getItem(STORAGE_KEYS.USER) : null);
    const user = userRaw ? JSON.parse(userRaw) : null;

    if (!token || !user) {
        renderAdminGuard('Bạn cần đăng nhập để vào trang quản trị');
        return;
    }

    // Validate token with backend; backend expects POST /auth/validate { token }
    try {
        const validateResp = await fetch(`${API_CONFIG.BASE_URL}${API_CONFIG.ENDPOINTS.VALIDATE}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token })
        });
        const validateData = await validateResp.json().catch(() => ({ result: false }));
        if (!validateResp.ok || !validateData.result) {
            auth.logout();
            renderAdminGuard('Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.');
            return;
        }
    } catch (e) {
        console.warn('Token validation failed', e);
        auth.logout();
        renderAdminGuard('Không thể xác thực phiên. Đăng nhập lại.');
        return;
    }

    const roles = Array.isArray(user.roles) ? user.roles : [];
    // Also read roles from JWT to avoid stale localStorage
    const payload = decodeJwtSafe(token);
    const tokenRoles = Array.isArray(payload?.roles) ? payload.roles : [];
    const allRoles = [...new Set([...roles, ...tokenRoles])];
    // Accept both 'ADMIN' and 'ROLE_ADMIN'
    const hasAdmin = allRoles.some(r => r === 'ADMIN' || r === 'ROLE_ADMIN');
    // If local user object lacks roles but token has roles, persist it for next loads
    if ((!user.roles || !user.roles.length) && tokenRoles.length) {
        try {
            const merged = { ...user, roles: tokenRoles };
            localStorage.setItem('user', JSON.stringify(merged));
        } catch (_) { /* ignore */ }
    }
    if (!hasAdmin) {
        renderAdminGuard('Tài khoản của bạn không có quyền ADMIN');
        return;
    }

    // Passed guards -> restore main content if altered
    if (adminMain && adminMain.innerHTML !== originalContent) {
        adminMain.innerHTML = originalContent;
    }
    const sidebar = document.querySelector('.admin-sidebar');
    const topbar = document.querySelector('.admin-topbar');
    if (sidebar) sidebar.style.display = '';
    if (topbar) topbar.style.display = '';

    initAdminUI();
    loadDashboardStats();

    const adminUserName = document.getElementById('adminUserName');
    if (adminUserName) {
        adminUserName.textContent = user?.username || 'Admin';
    }
});

// --- Modal helpers (centered using .modal.show from style.css) ---
function showModalById(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add('show');
    el.style.display = 'flex'; // fallback to ensure flex centering
}

function hideModalById(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('show');
    el.style.display = 'none';
}

// --- Geocoding and Map (Leaflet) helpers for Store Address ---
let storeAddressMap = null;
let storeAddressMarker = null;

function ensureStoreAddressMap() {
    const mapEl = document.getElementById('storeAddressMap');
    if (!mapEl) return;
    if (!storeAddressMap) {
        // Default center: Ho Chi Minh City
        storeAddressMap = L.map('storeAddressMap').setView([10.776, 106.700], 12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(storeAddressMap);
    } else {
        setTimeout(() => storeAddressMap.invalidateSize(), 50);
    }
}

function updateStoreAddressMap(lat, lon) {
    if (!storeAddressMap || typeof L === 'undefined') return;
    const latNum = parseFloat(lat);
    const lonNum = parseFloat(lon);
    if (isNaN(latNum) || isNaN(lonNum)) return;
    storeAddressMap.setView([latNum, lonNum], 16);
    if (!storeAddressMarker) {
        storeAddressMarker = L.marker([latNum, lonNum]).addTo(storeAddressMap);
    } else {
        storeAddressMarker.setLatLng([latNum, lonNum]);
    }
}

// --- Geocoding and Map (Leaflet) helpers for User Address ---
let userAddressMap = null;
let userAddressMarker = null;

function ensureUserAddressMap() {
    const mapEl = document.getElementById('userAddressMap');
    if (!mapEl) return;
    if (!userAddressMap) {
        userAddressMap = L.map('userAddressMap').setView([10.776, 106.700], 12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(userAddressMap);
        userAddressMarker = L.marker([10.776, 106.700], { draggable: true }).addTo(userAddressMap);
        userAddressMarker.on('dragend', e => {
            const { lat, lng } = e.target.getLatLng();
            const latEl = document.getElementById('userAddressLatitude');
            const lngEl = document.getElementById('userAddressLongitude');
            if (latEl) latEl.value = lat.toFixed(6);
            if (lngEl) lngEl.value = lng.toFixed(6);
        });
        const lookupBtn = document.getElementById('userAddressLookupBtn');
        lookupBtn?.addEventListener('click', async () => {
            try {
                const line = (document.getElementById('userAddressLine')?.value || '').trim();
                const ward = (document.getElementById('userAddressWard')?.value || '').trim();
                const district = (document.getElementById('userAddressDistrict')?.value || '').trim();
                const city = (document.getElementById('userAddressCity')?.value || '').trim();
                const country = (document.getElementById('userAddressCountry')?.value || 'Việt Nam').trim();
                const q = [line, ward, district, city, country].filter(Boolean).join(', ');
                if (!q) {
                    showNotification('Vui lòng nhập địa chỉ trước khi tra cứu', 'warning');
                    return;
                }
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&q=${encodeURIComponent(q)}`);
                if (!res.ok) throw new Error('Tra cứu thất bại');
                const results = await res.json();
                if (!Array.isArray(results) || results.length === 0) {
                    showNotification('Không tìm thấy vị trí phù hợp', 'warning');
                    return;
                }
                const best = results[0];
                updateUserAddressMap(parseFloat(best.lat), parseFloat(best.lon));
                const latEl = document.getElementById('userAddressLatitude');
                const lngEl = document.getElementById('userAddressLongitude');
                if (latEl) latEl.value = parseFloat(best.lat).toFixed(6);
                if (lngEl) lngEl.value = parseFloat(best.lon).toFixed(6);
                showNotification('Đã điền tọa độ từ địa chỉ', 'success');
            } catch (e) {
                console.warn('User address geocoding failed', e);
                showNotification('Không thể tra cứu vị trí', 'error');
            }
        });
    } else {
        setTimeout(() => userAddressMap.invalidateSize(), 50);
    }
}

function updateUserAddressMap(lat, lon) {
    if (!userAddressMap || typeof L === 'undefined') return;
    const latNum = parseFloat(lat);
    const lonNum = parseFloat(lon);
    if (isNaN(latNum) || isNaN(lonNum)) return;
    userAddressMap.setView([latNum, lonNum], 16);
    if (!userAddressMarker) {
        userAddressMarker = L.marker([latNum, lonNum], { draggable: true }).addTo(userAddressMap);
    } else {
        userAddressMarker.setLatLng([latNum, lonNum]);
    }
}

function composeAddressQuery() {
    const line = (document.getElementById('storeAddressLine')?.value || '').trim();
    const ward = (document.getElementById('storeAddressWard')?.value || '').trim();
    const district = (document.getElementById('storeAddressDistrict')?.value || '').trim();
    const city = (document.getElementById('storeAddressCity')?.value || '').trim();
    const country = (document.getElementById('storeAddressCountry')?.value || 'Việt Nam').trim();
    const parts = [line, ward, district, city, country].filter(Boolean);
    return parts.join(', ');
}

async function lookupAddressAndFill() {
    try {
        const q = composeAddressQuery();
        if (!q) {
            showNotification('Vui lòng nhập địa chỉ trước khi tra cứu', 'warning');
            return;
        }
        const url = `https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&q=${encodeURIComponent(q)}`;
        const res = await fetch(url, {
            headers: {
                // Let browser set UA/Referer. Avoid custom UA due to CORS restrictions.
            }
        });
        if (!res.ok) throw new Error('Tra cứu thất bại');
        const results = await res.json();
        if (!Array.isArray(results) || results.length === 0) {
            showNotification('Không tìm thấy vị trí phù hợp cho địa chỉ này', 'warning');
            return;
        }
        const best = results[0];
        const lat = best.lat;
        const lon = best.lon;
        const latInput = document.getElementById('storeAddressLatitude');
        const lonInput = document.getElementById('storeAddressLongitude');
        if (latInput) latInput.value = lat;
        if (lonInput) lonInput.value = lon;
        updateStoreAddressMap(lat, lon);
        showNotification('Đã điền tọa độ từ địa chỉ', 'success');
    } catch (e) {
        console.warn('Geocoding failed', e);
        showNotification('Không thể tra cứu vị trí. Vui lòng thử lại.', 'error');
    }
}

function renderAdminGuard(message) {
        const main = document.querySelector('.admin-main') || document.body;
        // Hide sidebar + topbar if present to avoid showing logout button
        const sidebar = document.querySelector('.admin-sidebar');
        const topbar = document.querySelector('.admin-topbar');
        if (sidebar) sidebar.style.display = 'none';
        if (topbar) topbar.style.display = 'none';

        main.innerHTML = `
            <div class="empty-state-admin" style="padding:3rem; text-align:center;">
                <i class="fas fa-lock" style="font-size:3rem; color:#9CA3AF;"></i>
                <h3 style="margin:.5rem 0;">${message || 'Bạn cần đăng nhập để vào trang quản trị'}</h3>
                <p style="color:#6B7280; margin-bottom:1rem;">Vui lòng đăng nhập tài khoản có quyền quản trị để tiếp tục.</p>
                <a href="index.html" class="btn btn-primary">Về trang chủ để đăng nhập</a>
            </div>`;
}

// Decode JWT payload safely (base64url)
function decodeJwtSafe(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(atob(base64).split('').map(c =>
            '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
        ).join(''));
        return JSON.parse(json);
    } catch (_) {
        return null;
    }
}

// Initialize Admin UI
function initAdminUI() {
    // Sidebar navigation
    const sidebarLinks = document.querySelectorAll('.sidebar-link');
    sidebarLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const section = link.dataset.section;
            switchSection(section);
        });
    });

    // Sidebar toggle (mobile)
    const sidebarToggle = document.getElementById('sidebarToggle');
    const sidebar = document.querySelector('.admin-sidebar');
    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', () => {
            sidebar.classList.toggle('active');
        });
    }

    // Logout
    const adminLogout = document.getElementById('adminLogout');
    if (adminLogout) {
        adminLogout.addEventListener('click', () => {
            auth.logout();
        });
    }

    // Modal handlers
    setupModals();

    // Button handlers
    setupButtons();
}

// Switch section
function switchSection(sectionName) {
    // Update sidebar active state
    document.querySelectorAll('.sidebar-link').forEach(link => {
        link.classList.remove('active');
    });
    document.querySelector(`[data-section="${sectionName}"]`)?.classList.add('active');

    // Update section visibility
    document.querySelectorAll('.admin-section').forEach(section => {
        section.classList.remove('active');
    });
    document.getElementById(`section-${sectionName}`)?.classList.add('active');

    // Load section data
    loadSectionData(sectionName);
}

// Load section data
function loadSectionData(sectionName) {
    switch(sectionName) {
        case 'dashboard':
            loadDashboardStats();
            break;
        case 'products':
            loadProducts();
            break;
        case 'categories':
            loadCategories();
            break;
        case 'stores':
            loadStores();
            break;
        case 'orders':
            loadOrders();
            break;
        case 'users':
            loadUsers();
            break;
        case 'drones':
            loadDrones();
            break;
    }
}

// Load Dashboard Stats
async function loadDashboardStats() {
    try {
        // Products
        const productsRes = await api.getProducts();
        const products = productsRes.result || [];
        document.getElementById('totalProducts').textContent = products.length;

        // Orders
        const ordersRes = await api.getAllOrders();
        const orders = Array.isArray(ordersRes?.result) ? ordersRes.result : (Array.isArray(ordersRes) ? ordersRes : []);
        document.getElementById('totalOrders').textContent = orders.length;

        // Revenue: chỉ tính đơn đã thanh toán (paymentStatus === 'PAID')
        const totalRevenue = orders
            .filter(o => o.paymentStatus === 'PAID')
            .reduce((sum, o) => sum + (o.totalPayable || o.totalAmount || 0), 0);
        document.getElementById('totalRevenue').textContent = formatPrice(totalRevenue);

        // Stores
        const storesRes = await api.getStores();
        const stores = storesRes.result || [];
        document.getElementById('totalStores').textContent = stores.length;

        // Recent orders preview
        loadRecentOrders(orders.slice(0,5));
    } catch (error) {
        console.error('Error loading dashboard:', error);
    }
}

// Load recent orders
async function loadRecentOrders(recent = []) {
    const container = document.getElementById('recentOrdersTable');
    if (!recent || recent.length === 0) {
        container.innerHTML = '<p class="text-center">Không có dữ liệu đơn hàng</p>';
        return;
    }
    const rows = recent.map(o => `
        <tr>
            <td>#${o.id}</td>
            <td>${o.customerName || o.userName || o.userId || ''}</td>
            <td>${formatPrice(o.totalPayable || o.totalAmount || 0)}</td>
            <td><span class="status-badge ${getStatusClass(o.status)}">${getStatusText(o.status)}</span></td>
        </tr>`).join('');
    container.innerHTML = `
        <div class="table-container">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>Mã</th>
                        <th>Khách hàng</th>
                        <th>Tổng tiền</th>
                        <th>Trạng thái</th>
                    </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>`;
}

// Load Products
async function loadProducts() {
    const tbody = document.getElementById('productsTableBody');
    // Initialize store filter dropdown if present
    const storeFilter = document.getElementById('productStoreFilter');
    if (storeFilter && !storeFilter.dataset.filled) {
        try {
            const storesRes = await api.getStores();
            const stores = storesRes.result || [];
            storeFilter.innerHTML = '<option value="">Tất cả cửa hàng</option>' + stores.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
            storeFilter.dataset.filled = 'true';
            storeFilter.addEventListener('change', () => loadProducts());
        } catch (_) { /* ignore */ }
    }

    try {
        const response = await api.getProducts();
        let products = response.result || [];
        const selectedStore = document.getElementById('productStoreFilter')?.value || '';
        if (selectedStore) {
            products = products.filter(p => String(p.storeId || '') === String(selectedStore));
        }

        if (products.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center">Không có sản phẩm</td></tr>';
            return;
        }

        tbody.innerHTML = products.map(product => {
            // Backend ProductResponse does not include categoryName; show categoryId or placeholder
            const categoryLabel = product.categoryName || product.categoryId || 'N/A';
            const price = product.basePrice !== undefined && product.basePrice !== null ? formatPrice(product.basePrice) : '0';
            return `
                <tr>
                    <td>${product.id}</td>
                    <td>${product.sku || 'N/A'}</td>
                    <td>${product.name}</td>
                    <td>${categoryLabel}</td>
                    <td>${price}</td>
                    <td>${product.quantityAvailable || 0}</td>
                    <td><span class="status-badge ${getStatusClass(product.status)}">${getStatusText(product.status)}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn edit" onclick="editProduct(${product.id})">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="action-btn delete" onclick="deleteProduct(${product.id})">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>`;
        }).join('');
    } catch (error) {
        console.error('Error loading products:', error);
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Load Categories
async function loadCategories() {
    const tbody = document.getElementById('categoriesTableBody');

    try {
        const response = await api.getCategories();
        const categories = response.result || [];

        if (categories.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">Không có danh mục</td></tr>';
            return;
        }

        tbody.innerHTML = categories.map(category => {
            const status = category.status || 'ACTIVE';
            return `
                <tr>
                    <td>${category.id}</td>
                    <td>${category.name}</td>
                    <td>${category.slug}</td>
                    <td><span class="status-badge ${getStatusClass(status)}">${getStatusText(status)}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn edit" onclick="editCategory(${category.id})">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="action-btn delete" onclick="deleteCategory(${category.id})">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>`;
        }).join('');
    } catch (error) {
        console.error('Error loading categories:', error);
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Load Stores
async function loadStores() {
    const tbody = document.getElementById('storesTableBody');
    try {
        const response = await api.getStores();
        const stores = response.result || [];

        if (stores.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">Không có cửa hàng</td></tr>';
            return;
        }

        tbody.innerHTML = stores.map(store => {
            const status = store.storeStatus || store.status || 'ACTIVE';
            const created = store.createdAt || store.createdDate || store.createdOn || '';
            const createdStr = created ? new Date(created).toLocaleString('vi-VN') : '';
            return `
                <tr>
                    <td>${store.id}</td>
                    <td>${store.name}</td>
                    <td>${store.ownerUserId || ''}</td>
                    <td><span class="status-badge ${getStatusClass(status)}">${getStatusText(status)}</span></td>
                    <td>${createdStr}</td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn edit" onclick="editStore(${store.id})" title="Quản lý cửa hàng (thông tin, thanh toán, địa chỉ)">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="action-btn delete" onclick="deleteStore(${store.id})" title="Xóa cửa hàng">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>`;
        }).join('');
    } catch (error) {
        console.error('Error loading stores:', error);
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Load Orders
async function loadOrders() {
    const tbody = document.getElementById('ordersTableBody');
    try {
        const response = await api.getAllOrders();
        // Some APIs wrap result differently; fall back to response if result missing
        const orders = Array.isArray(response?.result) ? response.result : (Array.isArray(response) ? response : []);

        if (orders.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">Không có đơn hàng</td></tr>';
            return;
        }
        tbody.innerHTML = orders.map(o => {
            const created = o.createdAt || o.createdDate || o.createdOn || o.orderDate || '';
            const createdStr = created ? new Date(created).toLocaleString('vi-VN') : '';
            const customer = o.customerName || o.customer || o.userName || o.user?.username || o.user?.email || '';
            const total = o.totalPayable || o.totalAmount || o.total || 0;
            return `
                <tr>
                    <td>${o.id}</td>
                    <td>${customer}</td>
                    <td>${formatPrice(total)}</td>
                    <td><span class="status-badge ${getStatusClass(o.status)}">${getStatusText(o.status)}</span></td>
                    <td>${createdStr}</td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn view" title="Xem" onclick="viewOrder('${o.id}')"><i class="fas fa-eye"></i></button>
                            <button class="action-btn" title="Theo dõi" onclick="viewOrder('${o.id}')"><i class="fas fa-location-arrow"></i></button>
                        </div>
                    </td>
                </tr>`;
        }).join('');
    } catch (error) {
        console.error('Error loading orders:', error);
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

async function fetchAndRenderDroneStation() {
    try {
        const res = await APIHelper.get('/drones/station');
        const st = res?.result || res;
        if (st && typeof st.latitude !== 'undefined' && typeof st.longitude !== 'undefined') {
            window.DRONE_STATION = { lat: Number(st.latitude), lng: Number(st.longitude), radiusKm: st.radiusKm, name: st.name };
            const el = document.getElementById('droneStationInfo');
            if (el) {
                const namePart = st.name ? `${st.name} · ` : '';
                el.innerHTML = `Trạm sạc chung: <strong>${namePart}${st.latitude}, ${st.longitude}</strong>`;
            }
        }
    } catch (e) {
        // leave defaults
    }
}

// Load Drones
async function loadDrones() {
    const tbody = document.getElementById('dronesTableBody');
    if (!tbody) return;
    try {
        await fetchAndRenderDroneStation();
        const resp = await APIHelper.get(API_CONFIG.ENDPOINTS.DRONES);
        const drones = Array.isArray(resp?.result) ? resp.result : (Array.isArray(resp) ? resp : []);
        if (!drones.length) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center">Không có drone</td></tr>';
            return;
        }
        tbody.innerHTML = drones.map(d => {
            const status = d.status || 'AVAILABLE';
            const battery = d.currentBatteryPercent != null ? d.currentBatteryPercent + '%' : 'N/A';
            const loc = (d.lastLatitude != null && d.lastLongitude != null) ? `${d.lastLatitude}, ${d.lastLongitude}` : '';
            const telemetry = d.lastTelemetryAt ? new Date(d.lastTelemetryAt).toLocaleString('vi-VN') : '';
            return `<tr>
                <td>${d.id}</td>
                <td>${d.code}</td>
                <td>${d.model || ''}</td>
                <td>${d.maxPayloadGram || ''}</td>
                <td>${battery}</td>
                <td><span class="status-badge ${getStatusClass(status)}">${getStatusText(status)}</span></td>
                <td>${loc}</td>
                <td>${telemetry}</td>
                <td>
                    <div class="action-buttons drone-actions">
                        <button class="action-btn success" onclick="setDroneAvailable('${d.code}')" title="Đặt sẵn sàng (AVAILABLE)"><i class="fas fa-check-circle"></i></button>
                        <button class="action-btn warning" onclick="returnDroneToStation('${d.code}')" title="Đưa về trạm sạc (CHARGING)"><i class="fas fa-charging-station"></i></button>
                        <button class="action-btn purple" onclick="setDroneMaintenance('${d.code}')" title="Bảo trì (MAINTENANCE)"><i class="fas fa-screwdriver-wrench"></i></button>
                        <button class="action-btn" onclick="simulateDroneBattery('${d.code}')" title="Kiểm tra pin"><i class="fas fa-battery-half"></i></button>
                        <button class="action-btn danger" onclick="confirmDeleteDrone('${d.code}')" title="Xóa drone"><i class="fas fa-trash"></i></button>
                    </div>
                </td>
            </tr>`;
        }).join('');
    } catch (e) {
        console.error('Error loading drones', e);
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Open Drone Register Modal
document.addEventListener('click', (e) => {
    if (e.target && e.target.id === 'addDroneBtn') {
        showModalById('droneModal');
        const form = document.getElementById('droneForm');
        if (form) form.reset();
        // Default coords to station
        const st = window.DRONE_STATION || { lat: 10.776, lng: 106.700 };
        const latEl = document.getElementById('droneLatitude');
        const lngEl = document.getElementById('droneLongitude');
        if (latEl) latEl.value = st.lat;
        if (lngEl) lngEl.value = st.lng;
    }
    if (e.target && e.target.id === 'closeDroneModal') {
        hideModalById('droneModal');
    }
});

// Handle Drone Register Form
const droneForm = document.getElementById('droneForm');
if (droneForm) {
    droneForm.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        try {
            const payload = {
                code: document.getElementById('droneCode').value.trim(),
                model: document.getElementById('droneModel').value.trim(),
                maxPayloadGram: parseInt(document.getElementById('droneMaxPayload').value, 10),
                latitude: document.getElementById('droneLatitude').value,
                longitude: document.getElementById('droneLongitude').value
            };
            if (!payload.code || !payload.model) {
                showNotification('Vui lòng nhập đủ thông tin drone', 'warning');
                return;
            }
            Loading.show();
            await APIHelper.post('/drones/register', payload);
            Loading.hide();
            hideModalById('droneModal');
            showNotification('Đăng ký drone thành công', 'success');
            loadDrones();
        } catch (err) {
            Loading.hide();
            showNotification('Không thể đăng ký drone: ' + (err.message || 'Lỗi'), 'error');
        }
    });
}

// Drone helper actions
window.openUpdateDroneStatus = function(code, currentStatus) {
    const next = prompt(`Nhập trạng thái mới cho drone ${code} (AVAILABLE, IN_FLIGHT, CHARGING, MAINTENANCE, OFFLINE):`, currentStatus || 'AVAILABLE');
    if (!next) return;
    const valid = ['AVAILABLE','IN_FLIGHT','CHARGING','MAINTENANCE','OFFLINE'];
    if (!valid.includes(next)) {
        showNotification('Trạng thái không hợp lệ', 'warning');
        return;
    }
    APIHelper.post(`/drones/${code}/status`, { status: next })
        .then(() => { showNotification('Cập nhật trạng thái thành công', 'success'); loadDrones(); })
        .catch(err => showNotification('Lỗi cập nhật: ' + (err.message||'Lỗi'), 'error'));
};

window.openUpdateDroneLocation = function(code) {
    const lat = prompt('Latitude mới:');
    const lng = prompt('Longitude mới:');
    if (!lat || !lng) return;
    APIHelper.post(`/drones/${code}/location`, { latitude: lat, longitude: lng })
        .then(() => { showNotification('Cập nhật vị trí thành công', 'success'); loadDrones(); })
        .catch(err => showNotification('Lỗi cập nhật vị trí: ' + (err.message||'Lỗi'), 'error'));
};

window.simulateDroneBattery = function(code) {
    APIHelper.post(`/drones/${code}/monitor-battery`, {})
        .then(() => { showNotification('Đã kiểm tra pin / cập nhật trạng thái nếu cần', 'info'); loadDrones(); })
        .catch(err => showNotification('Lỗi kiểm tra pin: ' + (err.message||'Lỗi'), 'error'));
};

// Convenience actions with single shared station
window.returnDroneToStation = function(code) {
    const { lat, lng } = window.DRONE_STATION || { lat: 10.776, lng: 106.7 };
    // Update location then status -> CHARGING
    APIHelper.post(`/drones/${code}/location`, { latitude: lat, longitude: lng })
        .then(() => APIHelper.post(`/drones/${code}/status`, { status: 'CHARGING' }))
        .then(() => { showNotification('Đã đưa drone về trạm sạc', 'success'); loadDrones(); })
        .catch(err => showNotification('Không thể đưa về trạm: ' + (err.message||'Lỗi'), 'error'));
};

window.setDroneAvailable = function(code) {
    APIHelper.post(`/drones/${code}/status`, { status: 'AVAILABLE' })
        .then(() => { showNotification('Đã đặt drone Sẵn sàng', 'success'); loadDrones(); })
        .catch(err => showNotification('Không thể cập nhật: ' + (err.message||'Lỗi'), 'error'));
};

window.setDroneMaintenance = function(code) {
    APIHelper.post(`/drones/${code}/status`, { status: 'MAINTENANCE' })
        .then(() => { showNotification('Đã đặt drone Bảo trì', 'success'); loadDrones(); })
        .catch(err => showNotification('Không thể cập nhật: ' + (err.message||'Lỗi'), 'error'));
};

// Delete a drone with confirmation
window.confirmDeleteDrone = async function(code) {
    try {
        const ok = confirm(`Bạn có chắc muốn xóa drone ${code}?\nLưu ý: Drone đang bay hoặc đang thực hiện đơn sẽ không bị xóa.`);
        if (!ok) return;
        Loading.show();
        await APIHelper.delete(`/drones/${code}`);
        showNotification('Đã xóa drone', 'success');
        await loadDrones();
    } catch (e) {
        const msg = e?.message || 'Không thể xóa drone';
        showNotification(msg, 'error');
    } finally {
        Loading.hide();
    }
};
// User CRUD helpers
async function saveUser() {
    const id = document.getElementById('userId').value.trim();
    const username = document.getElementById('userUsername').value.trim();
    const email = document.getElementById('userEmail').value.trim();
    const password = document.getElementById('userPassword').value.trim();
    const phone = document.getElementById('userPhone').value.trim();
    const fullName = document.getElementById('userFullName').value.trim();
    const rolesSel = document.getElementById('userRolesSelect');
    const roleArray = Array.from(rolesSel?.selectedOptions || []).map(o => o.value);

    // Backend expects UserCreationRequest; adapt keys accordingly (roleIds optional)
    const body = { username, email, phone, fullName, roleIds: roleArray.length ? roleArray.map(r=>parseInt(r,10)).filter(n=>!isNaN(n)) : null };
    try {
        if (id) {
            if (password) body.password = password; // only update password if provided
            await api.put('/users/UpdateUser/' + id, body);
            showNotification('Cập nhật người dùng thành công', 'success');
        } else {
            if (!password) {
                showNotification('Vui lòng nhập mật khẩu cho người dùng mới', 'error');
                return;
            }
            body.password = password;
            await api.post('/users/userCreated', body);
            showNotification('Tạo người dùng thành công', 'success');
        }
        hideModalById('userModal');
        loadUsers();
    } catch (e) {
        console.error('Save user failed', e);
        showNotification('Không thể lưu người dùng', 'error');
    }
}

async function editUser(userId) {
    try {
        const resp = await api.get('/users/GetUserById/' + userId);
        const u = resp.result || resp;
        document.getElementById('userModalTitle').textContent = 'Sửa người dùng';
        document.getElementById('userId').value = u.id;
        document.getElementById('userUsername').value = u.username || '';
        document.getElementById('userEmail').value = u.email || '';
        document.getElementById('userPassword').value = '';
        document.getElementById('userPhone').value = u.phoneNumber || u.phone || '';
        document.getElementById('userFullName').value = u.fullName || '';
        // Preselect roles
        const rolesSel = document.getElementById('userRolesSelect');
        const roles = Array.isArray(u.roles) ? u.roles : (Array.isArray(u.roleNames) ? u.roleNames : []);
        if (rolesSel) {
            Array.from(rolesSel.options).forEach(opt => {
                opt.selected = roles.includes(opt.value);
            });
        }
        showModalById('userModal');
        // Load user addresses into section
        await renderUserAddresses(u.id);
    } catch (e) {
        showNotification('Không thể tải người dùng', 'error');
    }
}

async function deleteUser(userId) {
    if (!confirm('Xóa người dùng này?')) return;
    try {
        await api.delete('/users/deleteUser/' + userId);
        showNotification('Đã xóa người dùng', 'success');
        loadUsers();
    } catch (e) {
        showNotification('Không thể xóa người dùng', 'error');
    }
}

// --- Admin Order Detail & Delivery Tracking ---
let adminDeliveryPollInterval = null;

async function viewOrder(id) {
    // Clear previous poll
    if (adminDeliveryPollInterval) {
        clearInterval(adminDeliveryPollInterval);
        adminDeliveryPollInterval = null;
    }
    try {
        const orderResp = await api.getOrderById(id);
        const order = orderResp.result || orderResp;
        if (!order) {
            showNotification('Không tìm thấy đơn hàng', 'error');
            return;
        }
        // Try load delivery info (ignore errors)
        let delivery = null;
        try {
            const delResp = await api.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(id));
            delivery = delResp.result || delResp;
        } catch (_) { /* ignore if not exists */ }
        renderAdminOrderModal(order, delivery);
        showModalById('adminOrderModal');
        // If delivery is in-flight, start polling
        if (delivery && ['QUEUED','ASSIGNED','LAUNCHED','ARRIVING'].includes(String(delivery.currentStatus))) {
            adminDeliveryPollInterval = setInterval(async () => {
                try {
                    const delResp2 = await api.get(API_CONFIG.ENDPOINTS.DELIVERY_BY_ORDER(id));
                    const newDelivery = delResp2.result || delResp2;
                    if (newDelivery) {
                        delivery = newDelivery;
                        updateDeliverySection(delivery);
                        if (['COMPLETED','FAILED','RETURNED'].includes(String(delivery.currentStatus))) {
                            clearInterval(adminDeliveryPollInterval);
                            adminDeliveryPollInterval = null;
                        }
                    }
                } catch (e) {
                    clearInterval(adminDeliveryPollInterval);
                    adminDeliveryPollInterval = null;
                }
            }, 4000);
        }
    } catch (e) {
        console.error('View order failed', e);
        showNotification('Không thể tải đơn hàng', 'error');
    }
}

function renderAdminOrderModal(order, delivery) {
    const container = document.getElementById('adminOrderContent');
    if (!container) return;

    const itemsHtml = (order.items || []).map(it => `
        <div class="order-item-row">
            <div class="left">
                <div class="name">${it.productName || 'Sản phẩm'}</div>
                <div class="sub">${formatPrice(it.unitPrice || it.unitPriceSnapshot || 0)} x ${it.quantity}</div>
            </div>
            <div class="right">${formatPrice(it.totalPrice || 0)}</div>
        </div>
    `).join('') || '<p class="text-gray">Không có sản phẩm</p>';

    const baseInfoHtml = `
        <div class="grid-two">
            <div><span class="lbl">Mã đơn:</span> <strong>${order.orderCode || ('ORD'+order.id)}</strong></div>
            <div><span class="lbl">Khách hàng:</span> <strong>${order.customerName || order.userName || order.customer || ''}</strong></div>
            <div><span class="lbl">Cửa hàng:</span> <strong>${order.storeName || order.storeId || ''}</strong></div>
            <div><span class="lbl">Ngày đặt:</span> ${new Date(order.createdAt).toLocaleString('vi-VN')}</div>
            <div><span class="lbl">Trạng thái đơn:</span> <span class="status-badge ${getStatusClass(order.status)}">${getStatusText(order.status)}</span></div>
            <div><span class="lbl">Trạng thái thanh toán:</span> <span class="status-badge ${getStatusClass(order.paymentStatus)}">${getStatusText(order.paymentStatus)}</span></div>
        </div>`;

    const summaryHtml = `
        <div class="summary-block">
            <div class="row"><span>Tạm tính:</span><strong>${formatPrice(order.totalItemAmount || 0)}</strong></div>
            <div class="row"><span>Phí vận chuyển:</span><strong>${formatPrice(order.shippingFee || 0)}</strong></div>
            <div class="row"><span>Giảm giá:</span><strong class="text-success">-${formatPrice(order.discountAmount || 0)}</strong></div>
            <div class="row total"><span>Tổng cộng:</span><strong>${formatPrice(order.totalPayable || order.totalAmount || 0)}</strong></div>
        </div>`;

    const deliverySection = buildDeliverySection(delivery);

    container.innerHTML = `
        <div class="admin-order-detail">
            <h3>Thông tin đơn hàng</h3>
            ${baseInfoHtml}
            <h3 style="margin-top:1.25rem;">Sản phẩm</h3>
            <div class="items-wrapper">${itemsHtml}</div>
            <h3 style="margin-top:1.25rem;">Tổng kết</h3>
            ${summaryHtml}
            ${deliverySection}
        </div>
    `;
}

function buildDeliverySection(delivery) {
    if (!delivery) {
        return '<div class="delivery-section"><h3>Giao hàng</h3><p class="text-gray">Chưa có thông tin giao hàng</p></div>';
    }
    const steps = [
        {key:'QUEUED', label:'Chờ'},
        {key:'ASSIGNED', label:'Đã gán'},
        {key:'LAUNCHED', label:'Xuất phát'},
        {key:'ARRIVING', label:'Sắp đến'},
        {key:'COMPLETED', label:'Hoàn thành'}
    ];
    const currentIndex = steps.findIndex(s => s.key === delivery.currentStatus);
    const timeline = steps.map((s,i) => `
        <div class="step ${i<=currentIndex?'active':''}">
            <div class="dot"></div>
            <div class="label">${s.label}</div>
        </div>
        ${i<steps.length-1?'<div class="line '+(i<currentIndex?'active':'')+'"></div>':''}
    `).join('');
    return `
        <div class="delivery-section" id="adminDeliverySection">
            <h3>Giao hàng (Trạng thái: ${getStatusText(delivery.currentStatus)})</h3>
            <div class="delivery-timeline">${timeline}</div>
            <div class="delivery-meta grid-two" style="margin-top:1rem;">
                ${delivery.droneCode?`<div><span class="lbl">Drone:</span> <strong>${delivery.droneCode}</strong></div>`:''}
                ${delivery.distanceKm?`<div><span class="lbl">Khoảng cách:</span> ${delivery.distanceKm.toFixed(2)} km</div>`:''}
                ${delivery.etaMinutes?`<div><span class="lbl">ETA:</span> ~${delivery.etaMinutes} phút</div>`:''}
            </div>
        </div>`;
}

function updateDeliverySection(delivery) {
    const wrapper = document.getElementById('adminDeliverySection');
    if (!wrapper) return;
    wrapper.outerHTML = buildDeliverySection(delivery);
}

// Load Users
async function loadUsers() {
    const tbody = document.getElementById('usersTableBody');
    try {
        const response = await api.getUsers();
        const users = Array.isArray(response?.result) ? response.result : (Array.isArray(response) ? response : []);

        if (users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">Không có người dùng</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(u => {
            const roles = Array.isArray(u.roles) ? u.roles.join(', ') : (Array.isArray(u.roleNames) ? u.roleNames.join(', ') : '');
            const phone = u.phoneNumber || u.phone || u.mobile || '';
            const created = u.createdAt || u.createdDate || u.createdOn || '';
            const createdStr = created ? new Date(created).toLocaleString('vi-VN') : '';
            return `
                <tr>
                    <td>${u.id}</td>
                    <td>${u.username || u.email || ''}</td>
                    <td>${u.email || ''}</td>
                    <td>${phone}</td>
                    <td>${roles}</td>
                    <td>${createdStr}</td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn edit" title="Sửa" onclick="editUser('${u.id}')"><i class="fas fa-edit"></i></button>
                            <button class="action-btn delete" title="Xóa" onclick="deleteUser('${u.id}')"><i class="fas fa-trash"></i></button>
                        </div>
                    </td>
                </tr>`;
        }).join('');
    } catch (error) {
        console.error('Error loading users:', error);
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Load Payments
async function loadPayments() {
    const tbody = document.getElementById('paymentsTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Chức năng đang phát triển</td></tr>';
}

// Load Ledger
async function loadLedger() {
    const tbody = document.getElementById('ledgerTableBody');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center">Chức năng đang phát triển</td></tr>';
}

// Setup Modals
function setupModals() {
    // Product Modal
    const productModal = document.getElementById('productModal');
    const closeProductModal = document.getElementById('closeProductModal');
    const productForm = document.getElementById('productForm');

    closeProductModal?.addEventListener('click', () => hideModalById('productModal'));

    productForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveProduct();
    });

    // Category Modal
    const categoryModal = document.getElementById('categoryModal');
    const closeCategoryModal = document.getElementById('closeCategoryModal');
    const categoryForm = document.getElementById('categoryForm');

    closeCategoryModal?.addEventListener('click', () => hideModalById('categoryModal'));

    categoryForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveCategory();
    });

    // Store Modal
    const storeModal = document.getElementById('storeModal');
    const closeStoreModal = document.getElementById('closeStoreModal');
    const storeForm = document.getElementById('storeForm');
    closeStoreModal?.addEventListener('click', () => hideModalById('storeModal'));
    storeForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveStore();
    });

    // Store Address Modal
    const storeAddressModal = document.getElementById('storeAddressModal');
    const closeStoreAddressModal = document.getElementById('closeStoreAddressModal');
    const storeAddressForm = document.getElementById('storeAddressForm');
    closeStoreAddressModal?.addEventListener('click', () => hideModalById('storeAddressModal'));
    storeAddressForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveStoreAddress();
    });
    // Bind lookup button
    const lookupBtn = document.getElementById('storeAddressLookupBtn');
    lookupBtn?.addEventListener('click', lookupAddressAndFill);

    // Store Payment Modal
    const storePaymentModal = document.getElementById('storePaymentModal');
    const closeStorePaymentModal = document.getElementById('closeStorePaymentModal');
    const storePaymentForm = document.getElementById('storePaymentForm');
    closeStorePaymentModal?.addEventListener('click', () => hideModalById('storePaymentModal'));
    storePaymentForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveStorePayment();
    });

    // User Modal
    const userModal = document.getElementById('userModal');
    const closeUserModal = document.getElementById('closeUserModal');
    const userForm = document.getElementById('userForm');
    closeUserModal?.addEventListener('click', () => hideModalById('userModal'));
    userForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveUser();
    });

    // User Address Modal
    const userAddressModal = document.getElementById('userAddressModal');
    const closeUserAddressModal = document.getElementById('closeUserAddressModal');
    const userAddressForm = document.getElementById('userAddressForm');
    closeUserAddressModal?.addEventListener('click', () => hideModalById('userAddressModal'));
    userAddressForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveUserAddress();
    });
}

// Setup Buttons
function setupButtons() {
    // Add Product
    const addProductBtn = document.getElementById('addProductBtn');
    addProductBtn?.addEventListener('click', async () => {
        document.getElementById('productModalTitle').textContent = 'Thêm sản phẩm';
        document.getElementById('productForm').reset();
        document.getElementById('productId').value = '';

        // Load categories and stores for dropdowns
        await loadCategoriesForDropdown();
        await loadStoresForDropdown();

        showModalById('productModal');
    });

    // Add Category
    const addCategoryBtn = document.getElementById('addCategoryBtn');
    addCategoryBtn?.addEventListener('click', () => {
        document.getElementById('categoryModalTitle').textContent = 'Thêm danh mục';
        document.getElementById('categoryForm').reset();
        document.getElementById('categoryId').value = '';
        showModalById('categoryModal');
    });

    // Add Store
    const addStoreBtn = document.getElementById('addStoreBtn');
    addStoreBtn?.addEventListener('click', async () => {
        document.getElementById('storeModalTitle').textContent = 'Thêm cửa hàng';
        document.getElementById('storeForm').reset();
        document.getElementById('storeId').value = '';
        showModalById('storeModal');
    });

    // Add User
    const addUserBtn = document.getElementById('addUserBtn');
    addUserBtn?.addEventListener('click', () => {
        document.getElementById('userModalTitle').textContent = 'Thêm người dùng';
        document.getElementById('userForm').reset();
        document.getElementById('userId').value = '';
        // Clear user addresses list when creating new user
        const uaList = document.getElementById('userAddressesList');
        if (uaList) uaList.innerHTML = '<p style="color:#666; font-size:.85rem; margin:.5rem 0;">Chưa có dữ liệu địa chỉ.</p>';
        // Clear role selections
        const rolesSel = document.getElementById('userRolesSelect');
        if (rolesSel) Array.from(rolesSel.options).forEach(o => o.selected = false);
        showModalById('userModal');
    });

    // Bind add user address button
    const addUserAddressBtn = document.getElementById('addUserAddressBtn');
    addUserAddressBtn?.addEventListener('click', () => {
        const userId = document.getElementById('userId').value;
        if (!userId) {
            showNotification('Hãy lưu người dùng trước rồi mới thêm địa chỉ', 'warning');
            return;
        }
        openAddUserAddress(userId);
    });

    // Store bottom save button
    const storeSaveBtnBottom = document.getElementById('storeSaveBtnBottom');
    storeSaveBtnBottom?.addEventListener('click', async () => {
        await saveStore();
    });
}

// User Address helpers
function openAddUserAddress(userId) {
    document.getElementById('userAddressForm').reset();
    document.getElementById('userAddressUserId').value = userId;
    document.getElementById('userAddressId').value = '';
    document.getElementById('userAddressModalTitle').textContent = 'Thêm địa chỉ';
    showModalById('userAddressModal');
    // Initialize user address map and set default center
    ensureUserAddressMap();
    setTimeout(() => updateUserAddressMap(10.776, 106.700), 80);
}

function openEditUserAddress(userId, address) {
    document.getElementById('userAddressUserId').value = userId;
    document.getElementById('userAddressId').value = address.id;
    document.getElementById('userAddressLabel').value = address.label || '';
    document.getElementById('userAddressReceiverName').value = address.receiverName || address.name || '';
    document.getElementById('userAddressPhone').value = address.phone || address.phoneNumber || '';
    document.getElementById('userAddressLine').value = address.addressLine || address.street || '';
    document.getElementById('userAddressWard').value = address.ward || '';
    document.getElementById('userAddressDistrict').value = address.district || '';
    document.getElementById('userAddressCity').value = address.city || '';
    document.getElementById('userAddressCountry').value = address.country || 'Việt Nam';
    document.getElementById('userAddressLatitude').value = address.latitude ?? '';
    document.getElementById('userAddressLongitude').value = address.longitude ?? '';
    document.getElementById('userAddressDefault').checked = !!(address.isDefault);
    document.getElementById('userAddressModalTitle').textContent = 'Sửa địa chỉ';
    showModalById('userAddressModal');
    // Initialize map and move to existing coordinates if available
    ensureUserAddressMap();
    const lat = address.latitude ?? null;
    const lon = address.longitude ?? null;
    if (lat != null && lon != null) {
        setTimeout(() => updateUserAddressMap(lat, lon), 80);
    }
}

async function saveUserAddress() {
    const userId = document.getElementById('userAddressUserId').value;
    const id = document.getElementById('userAddressId').value;
    const body = {
        label: document.getElementById('userAddressLabel').value || undefined,
    receiverName: document.getElementById('userAddressReceiverName').value || undefined,
    phone: document.getElementById('userAddressPhone').value || undefined,
        addressLine: document.getElementById('userAddressLine').value,
        ward: document.getElementById('userAddressWard').value || undefined,
        district: document.getElementById('userAddressDistrict').value || undefined,
        city: document.getElementById('userAddressCity').value || undefined,
        country: document.getElementById('userAddressCountry').value || undefined,
        latitude: document.getElementById('userAddressLatitude').value ? parseFloat(document.getElementById('userAddressLatitude').value) : undefined,
        longitude: document.getElementById('userAddressLongitude').value ? parseFloat(document.getElementById('userAddressLongitude').value) : undefined,
        isDefault: document.getElementById('userAddressDefault').checked || undefined,
    };
    try {
        if (id) {
            await api.updateUserAddress(userId, id, body);
        } else {
            await api.createUserAddress(userId, body);
        }
        hideModalById('userAddressModal');
        showNotification('Lưu địa chỉ thành công!', 'success');
        await renderUserAddresses(userId);
        // Refresh map view if latitude/longitude were saved
        if (body.latitude != null && body.longitude != null) {
            ensureUserAddressMap();
            updateUserAddressMap(body.latitude, body.longitude);
        }
    } catch (e) {
        console.error('Save user address failed', e);
        showNotification('Không thể lưu địa chỉ', 'error');
    }
}

async function deleteUserAddress(userId, addressId) {
    if (!confirm('Xóa địa chỉ này?')) return;
    try {
        await api.deleteUserAddress(userId, addressId);
        showNotification('Đã xóa địa chỉ', 'success');
        await renderUserAddresses(userId);
    } catch (e) {
        showNotification('Không thể xóa địa chỉ', 'error');
    }
}

async function setDefaultUserAddress(userId, addressId) {
    try {
        await api.setDefaultUserAddress(userId, addressId);
        await renderUserAddresses(userId);
        showNotification('Đã đặt làm địa chỉ mặc định', 'success');
    } catch (e) {
        showNotification('Không thể đặt mặc định', 'error');
    }
}

async function renderUserAddresses(userId) {
    const listEl = document.getElementById('userAddressesList');
    if (!listEl) return;
    try {
        const resp = await api.getUserAddresses(userId);
        const addresses = resp.result || resp || [];
        if (!Array.isArray(addresses) || addresses.length === 0) {
            listEl.innerHTML = '<p style="color:#666; font-size:.85rem; margin:.5rem 0;">Chưa có địa chỉ nào.</p>';
            const addBtn = document.getElementById('addUserAddressBtn');
            addBtn?.addEventListener('click', () => openAddUserAddress(userId));
            return;
        }
        listEl.innerHTML = '';
        addresses.forEach(a => {
            const row = document.createElement('div');
            row.className = 'user-address-row';
            row.style.cssText = 'display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #f0f0f0; padding:.35rem 0;';
            const left = document.createElement('div');
            left.style.cssText = 'flex:1; min-width:0;';
            left.innerHTML = `<strong>#${a.id}</strong> ${a.label ? '['+a.label+'] ' : ''}${a.addressLine || a.fullAddress || ''}` +
                `<small style="display:block; color:#888;">${[a.ward,a.district,a.city].filter(Boolean).join(', ')}${a.isDefault ? ' • Mặc định' : ''}${a.latitude?` | (${(a.latitude).toFixed?.(5) || a.latitude},${a.longitude? (a.longitude.toFixed?.(5) || a.longitude): ''})`:''}</small>`;
            const actions = document.createElement('div');
            actions.style.cssText = 'display:flex; gap:.4rem;';

            const defaultBtn = document.createElement('button');
            defaultBtn.className = 'action-btn';
            defaultBtn.title = 'Đặt mặc định';
            defaultBtn.innerHTML = '<i class="fas fa-star"></i>';
            defaultBtn.disabled = !!a.isDefault;
            defaultBtn.addEventListener('click', () => setDefaultUserAddress(userId, a.id));

            const editBtn = document.createElement('button');
            editBtn.className = 'action-btn';
            editBtn.title = 'Sửa';
            editBtn.innerHTML = '<i class="fas fa-edit"></i>';
            editBtn.addEventListener('click', () => openEditUserAddress(userId, a));

            const delBtn = document.createElement('button');
            delBtn.className = 'action-btn delete';
            delBtn.title = 'Xóa';
            delBtn.innerHTML = '<i class="fas fa-trash"></i>';
            delBtn.addEventListener('click', () => deleteUserAddress(userId, a.id));

            actions.appendChild(defaultBtn);
            actions.appendChild(editBtn);
            actions.appendChild(delBtn);
            row.appendChild(left);
            row.appendChild(actions);
            listEl.appendChild(row);
        });
        const addBtn = document.getElementById('addUserAddressBtn');
        addBtn?.addEventListener('click', () => openAddUserAddress(userId));
    } catch (e) {
        console.error('Render user addresses failed', e);
        listEl.innerHTML = '<p style="color:#c00; font-size:.85rem;">Lỗi tải địa chỉ</p>';
    }
}

// Load categories for dropdown
async function loadCategoriesForDropdown() {
    try {
        const response = await api.getCategories();
        const categories = response.result || [];
        const select = document.getElementById('productCategoryId');

        select.innerHTML = '<option value="">Chọn danh mục</option>' +
            categories.map(cat => `<option value="${cat.id}">${cat.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading categories:', error);
    }
}

// Load stores for dropdown
async function loadStoresForDropdown() {
    const select = document.getElementById('productStoreId');
    try {
        const response = await api.getStores();
        const stores = response.result || [];
        select.innerHTML = '<option value="">Chọn cửa hàng</option>' +
            stores.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    } catch (e) {
        console.warn('Failed to load stores for dropdown', e);
        select.innerHTML = '<option value="">Chọn cửa hàng</option>';
    }
}

// Save Product
async function saveProduct() {
    const productData = {
        sku: document.getElementById('productSku').value,
        name: document.getElementById('productName').value,
        description: document.getElementById('productDescription').value,
        categoryId: parseInt(document.getElementById('productCategoryId').value),
        storeId: parseInt(document.getElementById('productStoreId').value),
        basePrice: parseFloat(document.getElementById('productBasePrice').value),
        quantityAvailable: parseInt(document.getElementById('productQuantity').value),
        weightGram: parseInt(document.getElementById('productWeight').value) || null,
        status: document.getElementById('productStatus').value
    };
    const imageUrl = document.getElementById('productImageUrl')?.value?.trim();
    if (imageUrl) productData.imageUrl = imageUrl;

    try {
        const productId = document.getElementById('productId').value;

        if (productId) {
            // Update
            await api.put(API_CONFIG.ENDPOINTS.PRODUCT_BY_ID, productData, { id: productId });
            showNotification('Cập nhật sản phẩm thành công!', 'success');
        } else {
            // Create
            await api.post(API_CONFIG.ENDPOINTS.PRODUCTS, productData);
            showNotification('Thêm sản phẩm thành công!', 'success');
        }

    hideModalById('productModal');
        loadProducts();
    } catch (error) {
        console.error('Error saving product:', error);
        showNotification('Lỗi: ' + error.message, 'error');
    }
}

// Edit Product
async function editProduct(id) {
    try {
        const response = await api.getProductById(id);
        const product = response.result;

        document.getElementById('productModalTitle').textContent = 'Sửa sản phẩm';
        document.getElementById('productId').value = product.id;
        document.getElementById('productSku').value = product.sku;
        document.getElementById('productName').value = product.name;
        document.getElementById('productDescription').value = product.description || '';
        document.getElementById('productBasePrice').value = product.basePrice;
        document.getElementById('productQuantity').value = product.quantityAvailable;
        document.getElementById('productWeight').value = product.weightGram || '';
        document.getElementById('productStatus').value = product.status;

        await loadCategoriesForDropdown();
        await loadStoresForDropdown();

        document.getElementById('productCategoryId').value = product.categoryId;
        document.getElementById('productStoreId').value = product.storeId;

        // Prefill image url if available
    document.getElementById('productImageUrl').value = product.imageUrl || '';
    showModalById('productModal');
    } catch (error) {
        console.error('Error loading product:', error);
        showNotification('Không thể tải sản phẩm', 'error');
    }
}

// Delete Product
async function deleteProduct(id) {
    if (!confirm('Bạn có chắc muốn xóa sản phẩm này?')) return;

    try {
        await api.delete(API_CONFIG.ENDPOINTS.PRODUCT_BY_ID, { id });
        showNotification('Xóa sản phẩm thành công!', 'success');
        loadProducts();
    } catch (error) {
        console.error('Error deleting product:', error);
        showNotification('Không thể xóa sản phẩm', 'error');
    }
}

// Save Category
async function saveCategory() {
    const categoryData = {
        name: document.getElementById('categoryName').value,
        slug: document.getElementById('categorySlug').value,
        description: document.getElementById('categoryDescription').value,
        status: document.getElementById('categoryStatus').value
    };

    try {
        const categoryId = document.getElementById('categoryId').value;

        if (categoryId) {
            // Update
            await api.put(API_CONFIG.ENDPOINTS.CATEGORY_BY_ID, categoryData, { id: categoryId });
            showNotification('Cập nhật danh mục thành công!', 'success');
        } else {
            // Create
            await api.post(API_CONFIG.ENDPOINTS.CATEGORIES, categoryData);
            showNotification('Thêm danh mục thành công!', 'success');
        }

    hideModalById('categoryModal');
        loadCategories();
    } catch (error) {
        console.error('Error saving category:', error);
        showNotification('Lỗi: ' + error.message, 'error');
    }
}

// Edit Category
async function editCategory(id) {
    try {
        const response = await api.get(API_CONFIG.ENDPOINTS.CATEGORY_BY_ID, { id });
        const category = response.result;

        document.getElementById('categoryModalTitle').textContent = 'Sửa danh mục';
        document.getElementById('categoryId').value = category.id;
        document.getElementById('categoryName').value = category.name;
        document.getElementById('categorySlug').value = category.slug;
        document.getElementById('categoryDescription').value = category.description || '';
        document.getElementById('categoryStatus').value = category.status;

    showModalById('categoryModal');
    } catch (error) {
        console.error('Error loading category:', error);
        showNotification('Không thể tải danh mục', 'error');
    }
}

// Delete Category
async function deleteCategory(id) {
    if (!confirm('Bạn có chắc muốn xóa danh mục này?')) return;

    try {
        await api.delete(API_CONFIG.ENDPOINTS.CATEGORY_BY_ID, { id });
        showNotification('Xóa danh mục thành công!', 'success');
        loadCategories();
    } catch (error) {
        console.error('Error deleting category:', error);
        showNotification('Không thể xóa danh mục', 'error');
    }
}

// Store actions
async function editStore(id) {
    try {
        const resp = await api.getStoreById(id);
        const store = resp.result || resp;
        document.getElementById('storeModalTitle').textContent = 'Sửa cửa hàng';
        document.getElementById('storeId').value = store.id;
        document.getElementById('storeName').value = store.name || '';
        document.getElementById('storeOwnerUserId').value = store.ownerUserId || '';
        document.getElementById('storeDescription').value = store.description || '';
        const contactEmailEl = document.getElementById('storeContactEmail');
        if (contactEmailEl) contactEmailEl.value = store.email || store.contactEmail || '';
        const statusEl = document.getElementById('storeStatus');
        if (statusEl) statusEl.value = (store.storeStatus || store.status || 'ACTIVE');
        document.getElementById('storeBankAccountName').value = store.bankAccountName || '';
        document.getElementById('storeBankAccountNumber').value = store.bankAccountNumber || '';
        document.getElementById('storeBankName').value = store.bankName || '';
        document.getElementById('storeBankBranch').value = store.bankBranch || '';
        document.getElementById('storePayoutEmail').value = store.payoutEmail || '';
    showModalById('storeModal');
        await renderStoreAddresses(store.id);
    } catch (e) {
        console.error('Edit store failed', e);
        showNotification('Không thể tải thông tin cửa hàng', 'error');
    }
}

async function deleteStore(id) {
    if (!confirm('Xóa cửa hàng này?')) return;
    try {
        await api.deleteStore(id);
        showNotification('Đã xóa cửa hàng (vô hiệu hóa)', 'success');
        loadStores();
    } catch (e) {
        showNotification('Không thể xóa cửa hàng', 'error');
    }
}

// Save Store (create or update)
async function saveStore() {
    const id = document.getElementById('storeId').value;
    const name = document.getElementById('storeName').value;
    const ownerUserId = parseInt(document.getElementById('storeOwnerUserId').value);
    const description = document.getElementById('storeDescription').value || undefined;
    const email = document.getElementById('storeContactEmail')?.value || undefined;
    const status = document.getElementById('storeStatus')?.value || undefined;
    const bankAccountName = document.getElementById('storeBankAccountName').value || undefined;
    const bankAccountNumber = document.getElementById('storeBankAccountNumber').value || undefined;
    const bankName = document.getElementById('storeBankName').value || undefined;
    const bankBranch = document.getElementById('storeBankBranch').value || undefined;
    const payoutEmail = document.getElementById('storePayoutEmail').value || undefined;

    try {
        if (id) {
            await api.updateStore(id, { name, ownerUserId, description, status, email });
            await api.updateStorePayment(id, { bankAccountName, bankAccountNumber, bankName, bankBranch, payoutEmail });
        } else {
            const created = await api.createStore({ name, ownerUserId, description, status, email });
            const storeId = created?.result?.id || created?.id;
            if (storeId && (bankAccountName || bankAccountNumber || bankName || bankBranch || payoutEmail)) {
                await api.updateStorePayment(storeId, { bankAccountName, bankAccountNumber, bankName, bankBranch, payoutEmail });
            }
        }
    hideModalById('storeModal');
        showNotification('Lưu cửa hàng thành công!', 'success');
        loadStores();
    } catch (e) {
        console.error('Save store failed', e);
        showNotification('Không thể lưu cửa hàng', 'error');
    }
}

// Store Address helpers
async function openAddStoreAddress(storeId) {
    document.getElementById('storeAddressForm').reset();
    document.getElementById('storeAddressStoreId').value = storeId;
    document.getElementById('storeAddressId').value = '';
    document.getElementById('storeAddressModalTitle').textContent = 'Thêm địa chỉ';
    showModalById('storeAddressModal');
    ensureStoreAddressMap();
    // Center map to default city on open
    setTimeout(() => updateStoreAddressMap(10.776, 106.700), 100);
}

async function openEditStoreAddress(storeId, address) {
    document.getElementById('storeAddressStoreId').value = storeId;
    document.getElementById('storeAddressId').value = address.id;
    document.getElementById('storeAddressLine').value = address.addressLine || '';
    document.getElementById('storeAddressCity').value = address.city || '';
    document.getElementById('storeAddressDistrict').value = address.district || '';
    document.getElementById('storeAddressWard').value = address.ward || '';
    document.getElementById('storeAddressCountry').value = address.country || 'Việt Nam';
    document.getElementById('storeAddressLatitude').value = address.latitude ?? '';
    document.getElementById('storeAddressLongitude').value = address.longitude ?? '';
    document.getElementById('storeAddressModalTitle').textContent = 'Sửa địa chỉ';
    showModalById('storeAddressModal');
    ensureStoreAddressMap();
    if (address.latitude && address.longitude) {
        setTimeout(() => updateStoreAddressMap(address.latitude, address.longitude), 100);
    } else {
        setTimeout(() => updateStoreAddressMap(10.776, 106.700), 100);
    }
}

async function saveStoreAddress() {
    const storeId = document.getElementById('storeAddressStoreId').value;
    const id = document.getElementById('storeAddressId').value;
    const body = {
        addressLine: document.getElementById('storeAddressLine').value,
        city: document.getElementById('storeAddressCity').value || undefined,
        district: document.getElementById('storeAddressDistrict').value || undefined,
        ward: document.getElementById('storeAddressWard').value || undefined,
        country: document.getElementById('storeAddressCountry').value || undefined,
        latitude: document.getElementById('storeAddressLatitude').value ? parseFloat(document.getElementById('storeAddressLatitude').value) : undefined,
        longitude: document.getElementById('storeAddressLongitude').value ? parseFloat(document.getElementById('storeAddressLongitude').value) : undefined,
    };
    try {
        if (id) {
            await api.put(`/api/stores/${storeId}/addresses/${id}`, body);
        } else {
            await api.post(`/api/stores/${storeId}/addresses`, body);
        }
        hideModalById('storeAddressModal');
        showNotification('Lưu địa chỉ thành công!', 'success');
        await renderStoreAddresses(storeId);
    } catch (e) {
        console.error('Save store address failed', e);
        showNotification('Không thể lưu địa chỉ', 'error');
    }
}

async function deleteStoreAddress(storeId, addressId) {
    if (!confirm('Xóa địa chỉ này?')) return;
    try {
        await api.delete(`/api/stores/${storeId}/addresses/${addressId}`);
        showNotification('Đã xóa địa chỉ', 'success');
        await renderStoreAddresses(storeId);
    } catch (e) {
        showNotification('Không thể xóa địa chỉ', 'error');
    }
}

async function manageStoreAddresses(storeId) {
    try {
        const resp = await api.getStoreById(storeId);
        const store = resp.result || resp;
        document.getElementById('storeModalTitle').textContent = 'Quản lý địa chỉ: ' + (store.name || ('Store #' + storeId));
        document.getElementById('storeId').value = store.id;
        document.getElementById('storeName').value = store.name || '';
        document.getElementById('storeOwnerUserId').value = store.ownerUserId || '';
        document.getElementById('storeDescription').value = store.description || '';
        document.getElementById('storeBankAccountName').value = store.bankAccountName || '';
        document.getElementById('storeBankAccountNumber').value = store.bankAccountNumber || '';
        document.getElementById('storeBankName').value = store.bankName || '';
        document.getElementById('storeBankBranch').value = store.bankBranch || '';
        document.getElementById('storePayoutEmail').value = store.payoutEmail || '';
    showModalById('storeModal');
        await renderStoreAddresses(store.id);
    } catch (e) {
        showNotification('Không thể tải cửa hàng', 'error');
    }
}

async function renderStoreAddresses(storeId) {
    const listEl = document.getElementById('storeAddressesList');
    if (!listEl) return;
    try {
        const resp = await api.getStoreAddresses(storeId);
        const addresses = resp.result || resp || [];
        if (!Array.isArray(addresses) || addresses.length === 0) {
            listEl.innerHTML = '<p style="color:#666; font-size:.85rem; margin:.5rem 0;">Chưa có địa chỉ nào.</p>';
            // Bind add button still
            const addBtn = document.getElementById('addStoreAddressBtn');
            addBtn?.addEventListener('click', () => openAddStoreAddress(storeId));
            return;
        }
        listEl.innerHTML = addresses.map(a => `
            <div class=\"store-address-row\" style=\"display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #f0f0f0; padding:.35rem 0;\">
                <div style=\"flex:1; min-width:0;\">
                    <strong>#${a.id}</strong> ${a.addressLine || ''}
                    <small style=\"display:block; color:#888;\">${[a.ward,a.district,a.city].filter(Boolean).join(', ')}${a.latitude?` | (${(a.latitude).toFixed(5)},${a.longitude? (a.longitude).toFixed(5): ''})`:''}</small>
                </div>
                <div style=\"display:flex; gap:.4rem;\">
                    <button class=\"action-btn\" title=\"Sửa\" onclick='openEditStoreAddress(${storeId}, ${JSON.stringify({id:"${'${a.id}'}"})})'><i class=\"fas fa-edit\"></i></button>
                    <button class=\"action-btn delete\" title=\"Xóa\" onclick=\"deleteStoreAddress(${storeId}, ${'${a.id}'} )\"><i class=\"fas fa-trash\"></i></button>
                </div>
            </div>`).join('');
        // Because of template escaping above, rebind edit buttons using programmatic approach:
        // Replace with safer rendering approach
        listEl.innerHTML = '';
        addresses.forEach(a => {
            const row = document.createElement('div');
            row.className = 'store-address-row';
            row.style.cssText = 'display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #f0f0f0; padding:.35rem 0;';
            const left = document.createElement('div');
            left.style.cssText = 'flex:1; min-width:0;';
            left.innerHTML = `<strong>#${a.id}</strong> ${a.addressLine || ''}` +
                `<small style="display:block; color:#888;">${[a.ward,a.district,a.city].filter(Boolean).join(', ')}${a.latitude?` | (${(a.latitude).toFixed(5)},${a.longitude? (a.longitude).toFixed(5): ''})`:''}</small>`;
            const actions = document.createElement('div');
            actions.style.cssText = 'display:flex; gap:.4rem;';
            const editBtn = document.createElement('button');
            editBtn.className = 'action-btn';
            editBtn.title = 'Sửa';
            editBtn.innerHTML = '<i class="fas fa-edit"></i>';
            editBtn.addEventListener('click', () => openEditStoreAddress(storeId, a));
            const delBtn = document.createElement('button');
            delBtn.className = 'action-btn delete';
            delBtn.title = 'Xóa';
            delBtn.innerHTML = '<i class="fas fa-trash"></i>';
            delBtn.addEventListener('click', () => deleteStoreAddress(storeId, a.id));
            actions.appendChild(editBtn);
            actions.appendChild(delBtn);
            row.appendChild(left);
            row.appendChild(actions);
            listEl.appendChild(row);
        });
        const addBtn = document.getElementById('addStoreAddressBtn');
        addBtn?.addEventListener('click', () => openAddStoreAddress(storeId));
    } catch (e) {
        listEl.innerHTML = '<p style="color:#c00; font-size:.85rem;">Lỗi tải địa chỉ</p>';
    }
}

// Payment helpers
async function openStorePayment(storeId) {
    try {
        const resp = await api.getStoreById(storeId);
        const store = resp.result || resp;
        document.getElementById('storePaymentStoreId').value = storeId;
        document.getElementById('payBankAccountName').value = store.bankAccountName || '';
        document.getElementById('payBankAccountNumber').value = store.bankAccountNumber || '';
        document.getElementById('payBankName').value = store.bankName || '';
        document.getElementById('payBankBranch').value = store.bankBranch || '';
        document.getElementById('payPayoutEmail').value = store.payoutEmail || '';
        showModalById('storePaymentModal');
    } catch (e) {
        showNotification('Không thể tải thông tin cửa hàng', 'error');
    }
}

async function saveStorePayment() {
    const id = document.getElementById('storePaymentStoreId').value;
    const body = {
        bankAccountName: document.getElementById('payBankAccountName').value || undefined,
        bankAccountNumber: document.getElementById('payBankAccountNumber').value || undefined,
        bankName: document.getElementById('payBankName').value || undefined,
        bankBranch: document.getElementById('payBankBranch').value || undefined,
        payoutEmail: document.getElementById('payPayoutEmail').value || undefined,
    };
    try {
        await api.updateStorePayment(id, body);
        hideModalById('storePaymentModal');
        showNotification('Cập nhật thanh toán thành công!', 'success');
        loadStores();
    } catch (e) {
        showNotification('Không thể cập nhật thanh toán', 'error');
    }
}

// Helper functions
function getStatusText(status) {
    const s = String(status || '').toUpperCase();
    const statusMap = {
        // Generic
        'ACTIVE': 'Hoạt động',
        'INACTIVE': 'Vô hiệu',
        'OUT_OF_STOCK': 'Hết hàng',
        'DISABLED': 'Tắt',

        // Drone
        'AVAILABLE': 'Sẵn sàng',
        'IN_FLIGHT': 'Đang bay',
        'CHARGING': 'Sạc pin',
        'MAINTENANCE': 'Bảo trì',
        'OFFLINE': 'Mất kết nối',

        // Order-related
        'CREATED': 'Tạo mới',
        'PENDING_PAYMENT': 'Chờ thanh toán',
        'PAID': 'Đã thanh toán',
        'IN_DELIVERY': 'Đang giao',
        'DELIVERED': 'Đã giao',
        'CANCELLED': 'Hủy',
        'REFUNDED': 'Đã hoàn tiền',
        'ACCEPT': 'Đã xác nhận',

        // Delivery-related
        'QUEUED': 'Chờ xếp lịch',
        'ASSIGNED': 'Đã phân công',
        'LAUNCHED': 'Đã xuất phát',
        'ARRIVING': 'Sắp đến',
        'COMPLETED': 'Hoàn thành',
        'FAILED': 'Thất bại',
        'RETURNED': 'Trả về',

        // Other generic confirmations
        'PENDING': 'Chờ',
        'WAITING_CONFIRMATION': 'Chờ',
        'CONFIRMED': 'Đã xác nhận',
        'DELIVERING': 'Đang giao'
    };
    return statusMap[s] || status;
}

// Map raw status to CSS badge class defined in admin.css
function getStatusClass(status) {
    const s = String(status || '').toUpperCase();
    switch (s) {
        case 'PENDING':
        case 'WAITING_CONFIRMATION':
        case 'CREATED':
        case 'PENDING_PAYMENT':
        case 'QUEUED':
            return 'cho';
        case 'CONFIRMED':
        case 'PAID':
        case 'ACCEPT':
        case 'ASSIGNED':
        case 'AVAILABLE':
            return 'xac-nhan';
        case 'DELIVERING':
        case 'IN_DELIVERY':
        case 'LAUNCHED':
        case 'ARRIVING':
        case 'IN_FLIGHT':
            return 'giao-hang';
        case 'DELIVERED':
        case 'COMPLETED':
            return 'da-giao';
        case 'CANCELLED':
        case 'RETURNED':
            return 'huy';
        case 'FAILED':
        case 'REFUNDED':
            return 'that-bai';
        case 'CHARGING':
        case 'MAINTENANCE':
            return 'cho';
        case 'OFFLINE':
            return 'that-bai';
        default:
            return 'active'; // fallback so badge still styled
    }
}

function formatPrice(price) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(price);
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: ${type === 'success' ? '#00b894' : type === 'error' ? '#d63031' : '#0984e3'};
        color: white;
        padding: 1rem 1.5rem;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        z-index: 10000;
    `;
    notification.textContent = message;

    document.body.appendChild(notification);

    setTimeout(() => notification.remove(), 3000);
}

// Debug snippet removed (unsafe when token missing)

