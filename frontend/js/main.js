// Main.js - Homepage Logic

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initializePage();
    if (document.getElementById('featuredStores')) {
        loadFeaturedStores();
    }
    updateCartBadge();
    checkAuthStatus();
});

// Initialize page
function initializePage() {
    // Setup event listeners
    setupEventListeners();

    // Close dropdown when clicking outside (guard against null elements)
    document.addEventListener('click', (e) => {
        const dropdown = document.getElementById('dropdownMenu');
        if (!dropdown) return;
        const avatar = document.getElementById('userAvatar');
        if (!avatar) {
            // If no avatar button, just close if any click occurs outside dropdown
            if (!dropdown.contains(e.target)) dropdown.classList.remove('show');
            return;
        }
        if (!avatar.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.classList.remove('show');
        }
    });
}

// Setup event listeners
function setupEventListeners() {
    // Login form
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    // Register form
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', handleRegister);
    }

    // Search input
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                searchStores();
            }
        });
    }
}

// Check authentication status
function checkAuthStatus() {
    const isLoggedIn = AuthHelper.isLoggedIn();
    const guestMenu = document.getElementById('guestMenu');
    const userDropdown = document.getElementById('userDropdown');
    const userName = document.getElementById('userName');

    if (!guestMenu || !userDropdown) return; // navbar not present on this page

    if (isLoggedIn) {
        const user = AuthHelper.getUser() || {};
        guestMenu.style.display = 'none';
        userDropdown.style.display = 'block';
        if (userName) userName.textContent = user.username || user.fullName || 'User';
    } else {
        guestMenu.style.display = 'flex';
        guestMenu.style.gap = '0.5rem';
        userDropdown.style.display = 'none';
    }
}

// Load featured stores
async function loadFeaturedStores() {
    try {
        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.STORES);
        const stores = response.result || [];

        displayStores(stores.slice(0, 6)); // Show first 6 stores
    } catch (error) {
        console.error('Error loading stores:', error);
        displayEmptyState('Không thể tải danh sách cửa hàng');
    }
}

// Display stores
function displayStores(stores) {
    const container = document.getElementById('featuredStores');

    if (!stores || stores.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-store-slash"></i>
                <h3>Chưa có cửa hàng</h3>
                <p>Hãy quay lại sau nhé!</p>
            </div>
        `;
        return;
    }

    container.innerHTML = stores.map(store => `
        <div class="card">
          <img src="img/placeholder-store.svg" 
                 alt="${store.name}" 
                 class="card-img"
              onerror="this.onerror=null; this.src='img/placeholder-store.svg'">
            <div class="card-body">
                <h3 class="card-title">${store.name}</h3>
                <p class="card-text">
                    <i class="fas fa-map-marker-alt text-primary"></i>
                    ${store.description || 'Cửa hàng đồ ăn ngon'}
                </p>
                <div class="d-flex align-center justify-between mt-2">
                    <span class="text-gray">
                        <i class="fas fa-star text-warning"></i> 4.5
                    </span>
                    <span class="text-gray">
                        <i class="fas fa-clock"></i> 15-30 phút
                    </span>
                </div>
            </div>
            <div class="card-footer">
                <button class="btn btn-primary btn-sm" onclick="viewStore(${store.id})">
                    <i class="fas fa-eye"></i> Xem menu
                </button>
                <span class="text-success">
                    <i class="fas fa-check-circle"></i> Mở cửa
                </span>
            </div>
        </div>
    `).join('');
}

// Display empty state
function displayEmptyState(message) {
    const container = document.getElementById('featuredStores');
    container.innerHTML = `
        <div class="empty-state">
            <i class="fas fa-exclamation-circle"></i>
            <h3>${message}</h3>
        </div>
    `;
}

// View store details
function viewStore(storeId) {
    window.location.href = `stores.html?id=${storeId}`;
}

// Search stores
function searchStores() {
    const searchInput = document.getElementById('searchInput');
    const query = searchInput.value.trim();

    if (query) {
        window.location.href = `stores.html?search=${encodeURIComponent(query)}`;
    } else {
        window.location.href = 'stores.html';
    }
}

// Handle login
async function handleLogin(e) {
    e.preventDefault();

    const formData = new FormData(e.target);
    const data = {
        username: formData.get('username'),
        password: formData.get('password')
    };

    try {
        Loading.show();

        const response = await APIHelper.postNoAuth(API_CONFIG.ENDPOINTS.LOGIN, data);

        // Hỗ trợ nhiều cấu trúc response khác nhau từ backend
        const token =
            response?.result?.token ||
            response?.result?.accessToken ||
            response?.token ||
            response?.accessToken;

        if (token) {
            const userPayload = {
                username: data.username,
                ...(response.result || response)
            };

            // Lưu token + user để các trang khác dùng
            AuthHelper.login(token, userPayload);

            Toast.success('Đăng nhập thành công!');
            closeModal('loginModal');
            checkAuthStatus();

            // Reload cart nếu cần
            updateCartBadge();
        } else {
            const msg = (response && (response.message || response.error || response.detail)) || 'Đăng nhập thất bại!';
            Toast.error(msg);
        }
    } catch (error) {
        console.error('Login error:', error);
        Toast.error(error.message || 'Đăng nhập thất bại!');
    } finally {
        Loading.hide();
    }
}

// Handle register
async function handleRegister(e) {
    e.preventDefault();

    const formData = new FormData(e.target);
    const data = {
        username: formData.get('username'),
        email: formData.get('email'),
        password: formData.get('password'),
        fullName: formData.get('fullName'),
        phone: formData.get('phone')
    };

    try {
        Loading.show();

        const response = await APIHelper.postNoAuth(API_CONFIG.ENDPOINTS.REGISTER, data);

        if (response && response.code === 200) {
            Toast.success('Đăng ký thành công! Vui lòng đăng nhập.');
            closeModal('registerModal');
            showLoginModal();
        } else {
            const msg = (response && (response.message || response.error || response.detail)) || 'Đăng ký thất bại!';
            Toast.error(msg);
        }
    } catch (error) {
        console.error('Register error:', error);
        Toast.error(error.message || 'Đăng ký thất bại!');
    } finally {
        Loading.hide();
    }
}

// Update cart badge
async function updateCartBadge() {
    const badge = document.getElementById('cartBadge');
    if (!badge) return;

    if (!AuthHelper.isLoggedIn()) {
        badge.textContent = '0';
        return;
    }

    try {
        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.CART_COUNT);
        const count = response || 0;
        badge.textContent = count;
    } catch (error) {
        console.error('Error updating cart badge:', error);
    }
}

// Toggle dropdown menu
function toggleDropdown(evt) {
    const e = evt || window.event;
    if (e && typeof e.stopPropagation === 'function') {
        e.stopPropagation();
    }
    const dropdown = document.getElementById('dropdownMenu');
    const avatar = document.getElementById('userAvatar');
    if (dropdown) {
        dropdown.classList.toggle('show');
        if (avatar) {
            avatar.setAttribute('aria-expanded', dropdown.classList.contains('show') ? 'true' : 'false');
        }
    }
}

// Logout
function logout() {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        AuthHelper.logout();
    }
}

// Show modal
function showLoginModal() {
    document.getElementById('loginModal').classList.add('show');
}

function showRegisterModal() {
    document.getElementById('registerModal').classList.add('show');
}

// Close modal
function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('show');
}

// Close modal on backdrop click
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) {
        e.target.classList.remove('show');
    }
});

