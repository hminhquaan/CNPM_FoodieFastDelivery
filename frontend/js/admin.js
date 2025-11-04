// Admin JavaScript
document.addEventListener('DOMContentLoaded', () => {
    // Require login; if not logged in, show a gentle prompt instead of a silent page
    if (!auth.isAuthenticated()) {
        renderAdminGuard('Bạn cần đăng nhập để vào trang quản trị');
        return;
    }

    // Optional: require ADMIN role
    const roles = (auth.getUser()?.roles) || [];
    if (!roles.includes('ADMIN')) {
        renderAdminGuard('Tài khoản của bạn không có quyền ADMIN');
        return;
    }

    // Initialize admin UI
    initAdminUI();
    loadDashboardStats();

    // Set admin user name
    const adminUserName = document.getElementById('adminUserName');
    if (adminUserName) {
        adminUserName.textContent = auth.getUser()?.username || 'Admin';
    }
});

function renderAdminGuard(message) {
    const main = document.querySelector('.admin-main') || document.body;
    main.innerHTML = `
        <div class="empty-state-admin" style="padding: 3rem; text-align: center;">
            <i class="fas fa-lock" style="font-size: 3rem; color: #9CA3AF;"></i>
            <h3 style="margin: .5rem 0;">${message || 'Bạn cần đăng nhập để vào trang quản trị'}</h3>
            <p style="color:#6B7280; margin-bottom: 1rem;">Vui lòng đăng nhập tài khoản có quyền quản trị để tiếp tục.</p>
            <a href="index.html" class="btn btn-primary">Về trang chủ để đăng nhập</a>
        </div>
    `;
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
        case 'payments':
            loadPayments();
            break;
        case 'ledger':
            loadLedger();
            break;
    }
}

// Load Dashboard Stats
async function loadDashboardStats() {
    try {
        // Load products count
        const productsRes = await api.getProducts();
        document.getElementById('totalProducts').textContent = productsRes.result?.length || 0;

        // Load orders (mock data - adjust based on your API)
        // const ordersRes = await api.get('/api/v1/orders');
        document.getElementById('totalOrders').textContent = '0';

        // Mock stores and revenue
        document.getElementById('totalStores').textContent = '0';
        document.getElementById('totalRevenue').textContent = formatPrice(0);

        // Load recent orders
        loadRecentOrders();
    } catch (error) {
        console.error('Error loading dashboard:', error);
    }
}

// Load recent orders
async function loadRecentOrders() {
    const container = document.getElementById('recentOrdersTable');
    container.innerHTML = '<p class="text-center">Không có dữ liệu đơn hàng</p>';
}

// Load Products
async function loadProducts() {
    const tbody = document.getElementById('productsTableBody');

    try {
        const response = await api.getProducts();
        const products = response.result || [];

        if (products.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center">Không có sản phẩm</td></tr>';
            return;
        }

        tbody.innerHTML = products.map(product => `
            <tr>
                <td>${product.id}</td>
                <td>${product.sku || 'N/A'}</td>
                <td>${product.name}</td>
                <td>${product.categoryName || 'N/A'}</td>
                <td>${formatPrice(product.basePrice)}</td>
                <td>${product.quantityAvailable || 0}</td>
                <td><span class="status-badge ${product.status.toLowerCase()}">${getStatusText(product.status)}</span></td>
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
            </tr>
        `).join('');
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

        tbody.innerHTML = categories.map(category => `
            <tr>
                <td>${category.id}</td>
                <td>${category.name}</td>
                <td>${category.slug}</td>
                <td><span class="status-badge ${category.status.toLowerCase()}">${getStatusText(category.status)}</span></td>
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
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error loading categories:', error);
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">Lỗi tải dữ liệu</td></tr>';
    }
}

// Load Stores
async function loadStores() {
    const tbody = document.getElementById('storesTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Chức năng đang phát triển</td></tr>';
}

// Load Orders
async function loadOrders() {
    const tbody = document.getElementById('ordersTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Chức năng đang phát triển</td></tr>';
}

// Load Users
async function loadUsers() {
    const tbody = document.getElementById('usersTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Chức năng đang phát triển</td></tr>';
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

    closeProductModal?.addEventListener('click', () => {
        productModal.style.display = 'none';
    });

    productForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveProduct();
    });

    // Category Modal
    const categoryModal = document.getElementById('categoryModal');
    const closeCategoryModal = document.getElementById('closeCategoryModal');
    const categoryForm = document.getElementById('categoryForm');

    closeCategoryModal?.addEventListener('click', () => {
        categoryModal.style.display = 'none';
    });

    categoryForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveCategory();
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

        document.getElementById('productModal').style.display = 'block';
    });

    // Add Category
    const addCategoryBtn = document.getElementById('addCategoryBtn');
    addCategoryBtn?.addEventListener('click', () => {
        document.getElementById('categoryModalTitle').textContent = 'Thêm danh mục';
        document.getElementById('categoryForm').reset();
        document.getElementById('categoryId').value = '';
        document.getElementById('categoryModal').style.display = 'block';
    });
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
    // Mock stores - adjust based on your API
    const select = document.getElementById('productStoreId');
    select.innerHTML = '<option value="">Chọn cửa hàng</option><option value="1">Cửa hàng mẫu</option>';
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

        document.getElementById('productModal').style.display = 'none';
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

        document.getElementById('productModal').style.display = 'block';
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

        document.getElementById('categoryModal').style.display = 'none';
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

        document.getElementById('categoryModal').style.display = 'block';
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

// Helper functions
function getStatusText(status) {
    const statusMap = {
        'ACTIVE': 'Hoạt động',
        'INACTIVE': 'Vô hiệu',
        'OUT_OF_STOCK': 'Hết hàng',
        'DISABLED': 'Tắt',
        'PENDING': 'Chờ',
        'CONFIRMED': 'Đã xác nhận',
        'DELIVERED': 'Đã giao'
    };
    return statusMap[status] || status;
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

