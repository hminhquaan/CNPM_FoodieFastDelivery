// Store.js - Stores & Products Page Logic

let currentStore = null;
let currentProduct = null;
let modalQuantity = 1;
let isLoadingStore = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initializeStorePage();
    checkAuthStatus();
    updateCartBadge();

    // Check URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    const storeId = urlParams.get('id');
    const searchQuery = urlParams.get('search');

    if (storeId) {
        loadStoreDetails(storeId);
    } else if (searchQuery) {
        searchStores(searchQuery);
    } else {
        loadAllStores();
    }
});

// Initialize page
function initializeStorePage() {
    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        const dropdown = document.getElementById('dropdownMenu');
        const avatar = document.getElementById('userAvatar');
        if (dropdown && avatar && !avatar.contains(e.target)) {
            dropdown.classList.remove('show');
        }
    });
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
    } else {
        guestMenu.style.display = 'flex';
        guestMenu.style.gap = '0.5rem';
        userDropdown.style.display = 'none';
    }
}

// Load all stores
async function loadAllStores() {
    try {
        Loading.show();

        const response = await APIHelper.get(API_CONFIG.ENDPOINTS.STORES);
        const stores = response.result || [];

        displayStoresList(stores);

        // Show stores list view
        document.getElementById('storesListView').style.display = 'block';
        document.getElementById('storeView').style.display = 'none';

    } catch (error) {
        console.error('Error loading stores:', error);
        Toast.error('Không thể tải danh sách cửa hàng');
        displayStoresEmptyState('Không thể tải danh sách cửa hàng');
    } finally {
        Loading.hide();
    }
}

// Display stores list
function displayStoresList(stores) {
    const container = document.getElementById('storesGrid');

    if (!stores || stores.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-store-slash"></i>
                <h3>Không tìm thấy cửa hàng</h3>
                <p>Hãy thử lại sau nhé!</p>
            </div>
        `;
        return;
    }

    container.innerHTML = stores.map(store => `
        <div class="card">
            <img src="../img/placeholder-store.svg" 
                 alt="${store.name}" 
                 class="card-img"
                 onerror="this.onerror=null; this.src='../img/placeholder-store.svg'">
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
                <button class="btn btn-primary btn-sm" onclick="loadStoreDetails(${store.id})">
                    <i class="fas fa-eye"></i> Xem menu
                </button>
                <span class="text-success">
                    <i class="fas fa-check-circle"></i> Mở cửa
                </span>
            </div>
        </div>
    `).join('');
}

// Load store details and products
async function loadStoreDetails(storeId) {
    try {
        if (isLoadingStore) return;
        isLoadingStore = true;
        Loading.show();

        // Use combined endpoint to get store info with all products
        // GET /api/stores/{storeId}/products returns StoreWithProductsResponse (not wrapped in APIResponse)
        const storeWithProducts = await APIHelper.get(`/api/stores/${storeId}/products`);

        // Set current store and products
        currentStore = {
            id: storeWithProducts.id,
            name: storeWithProducts.name,
            description: storeWithProducts.description
        };
        const products = storeWithProducts.products || [];

        // Display store details
        displayStoreDetails(currentStore, products);

        // Show store view
        document.getElementById('storesListView').style.display = 'none';
        document.getElementById('storeView').style.display = 'block';

        // Update breadcrumb
        document.getElementById('breadcrumb').textContent = currentStore.name;

        // Update URL
        const url = new URL(window.location);
        url.searchParams.set('id', storeId);
        window.history.pushState({}, '', url);

    } catch (error) {
        console.error('Error loading store details:', error);
        Toast.error(error.message || 'Không thể tải thông tin cửa hàng');
    } finally {
        Loading.hide();
        isLoadingStore = false;
    }
}

// Display store details
function displayStoreDetails(store, products) {
    // Store info
    document.getElementById('storeName').textContent = store.name;
    document.getElementById('storeDescription').textContent = store.description || 'Cửa hàng đồ ăn ngon';
    const storeImg = document.getElementById('storeImage');
    storeImg.onerror = null;
    storeImg.src = `../img/placeholder-store.svg`;
    storeImg.alt = store.name;

    // Products grid
    displayProducts(products);
}

// Display products
function displayProducts(products) {
    const container = document.getElementById('productsGrid');

    if (!products || products.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-utensils"></i>
                <h3>Chưa có món ăn</h3>
                <p>Cửa hàng chưa có sản phẩm</p>
            </div>
        `;
        return;
    }

    container.innerHTML = products.map(product => `
        <div class="card product-card">
            ${product.discount ? `<span class="product-badge">-${product.discount}%</span>` : ''}
            <img src="${product.mediaPrimaryUrl || '../img/placeholder-food.svg'}" 
                 alt="${product.name}" 
                 class="card-img"
                 onerror="this.onerror=null; this.src='../img/placeholder-food.svg'">
            <div class="card-body">
                <h3 class="card-title">${product.name}</h3>
                <p class="card-text">${product.description || 'Món ăn ngon'}</p>
                <div class="d-flex align-center justify-between mt-2">
                    <span class="product-price">${FormatHelper.currency(product.basePrice)}</span>
                    <button class="btn btn-primary btn-sm" onclick="showProductDetail(${product.id})">
                        <i class="fas fa-plus"></i> Thêm
                    </button>
                </div>
            </div>
        </div>
    `).join('');
}

// Show product detail modal
async function showProductDetail(productId) {
    try {
        // Find product from current store products
    const storeWithProducts = await APIHelper.get(`/api/stores/${currentStore.id}/products`);
    const products = storeWithProducts.products || [];
        currentProduct = products.find(p => p.id === productId);

        if (!currentProduct) {
            Toast.error('Không tìm thấy sản phẩm');
            return;
        }

        // Reset quantity
        modalQuantity = 1;

        // Fill modal
        document.getElementById('productModalTitle').textContent = currentProduct.name;
        document.getElementById('productModalDescription').textContent = currentProduct.description || 'Món ăn ngon';
    document.getElementById('productModalPrice').textContent = FormatHelper.currency(currentProduct.basePrice);
    const modalImg = document.getElementById('productModalImage');
    modalImg.onerror = null;
    modalImg.src = currentProduct.mediaPrimaryUrl || '../img/placeholder-food.svg';
        document.getElementById('modalQuantity').textContent = modalQuantity;

        // Show modal
        document.getElementById('productModal').classList.add('show');

    } catch (error) {
        console.error('Error loading product:', error);
        Toast.error('Không thể tải thông tin sản phẩm');
    }
}

// Close product modal
function closeProductModal() {
    document.getElementById('productModal').classList.remove('show');
    currentProduct = null;
    modalQuantity = 1;
}

// Increase quantity
function increaseQuantity() {
    if (modalQuantity < 99) {
        modalQuantity++;
        document.getElementById('modalQuantity').textContent = modalQuantity;
    }
}

// Decrease quantity
function decreaseQuantity() {
    if (modalQuantity > 1) {
        modalQuantity--;
        document.getElementById('modalQuantity').textContent = modalQuantity;
    }
}

// Add to cart from modal
async function addToCartFromModal() {
    if (!currentProduct) {
        Toast.error('Vui lòng chọn sản phẩm');
        return;
    }

    if (!AuthHelper.isLoggedIn()) {
        Toast.warning('Vui lòng đăng nhập để thêm vào giỏ hàng');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 1500);
        return;
    }

    try {
        Loading.show();

        const data = {
            productId: currentProduct.id,
            quantity: modalQuantity
        };

        await APIHelper.post(API_CONFIG.ENDPOINTS.CART_ADD, data);

        Toast.success(`Đã thêm ${modalQuantity} ${currentProduct.name} vào giỏ hàng!`);
        closeProductModal();
        updateCartBadge();

    } catch (error) {
        console.error('Error adding to cart:', error);
        if (error && (error.status === 401 || error.status === 403)) {
            Toast.warning('Vui lòng đăng nhập để thêm vào giỏ hàng');
            setTimeout(() => { window.location.href = 'index.html'; }, 1200);
        } else {
            Toast.error(error.message || 'Không thể thêm vào giỏ hàng');
        }
    } finally {
        Loading.hide();
    }
}

// Back to stores list
function backToStores() {
    window.location.href = 'stores.html';
}

// Search stores
function searchStores(query) {
    // For now, just load all stores and filter client-side
    // In production, this should be a server-side search
    loadAllStores();
    Toast.info(`Tìm kiếm: ${query}`);
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
function toggleDropdown(evt) {
    try { evt?.stopPropagation?.(); } catch(_) {}
    const dropdown = document.getElementById('dropdownMenu');
    const avatar = document.getElementById('userAvatar');
    if (dropdown) {
        dropdown.classList.toggle('show');
        if (avatar) avatar.setAttribute('aria-expanded', dropdown.classList.contains('show') ? 'true' : 'false');
    }
}

// Logout
function logout() {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        AuthHelper.logout();
    }
}

// Display empty state
function displayStoresEmptyState(message) {
    const container = document.getElementById('storesGrid');
    container.innerHTML = `
        <div class="empty-state">
            <i class="fas fa-exclamation-circle"></i>
            <h3>${message}</h3>
        </div>
    `;
}

// Close modal on backdrop click
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) {
        e.target.classList.remove('show');
    }
});

