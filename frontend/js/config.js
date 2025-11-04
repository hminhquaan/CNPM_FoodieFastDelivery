// API Configuration
const API_CONFIG = {
    BASE_URL: 'http://localhost:8080/home',
    ENDPOINTS: {
        // Authentication
        LOGIN: '/auth/login',
    REGISTER: '/auth/signup',
    SIGNUP: '/auth/signup',
    LOGOUT: '/auth/logout',

        // Stores
        STORES: '/api/stores',
        STORE_BY_ID: (id) => `/api/stores/${id}`,

        // Products
        PRODUCTS_BY_STORE: (storeId) => `/products/store/${storeId}`,
    PRODUCTS: '/products',
    PRODUCT_BY_ID: '/products/{id}',

        // Categories
    CATEGORIES: '/categories',
    CATEGORY_BY_ID: '/categories/{id}',

        // Cart
        CART: '/api/cart',
        CART_ADD: '/api/cart/add',
        CART_UPDATE: (productId) => `/api/cart/products/${productId}`,
        CART_REMOVE: (productId) => `/api/cart/products/${productId}`,
        CART_CLEAR: '/api/cart/clear',
        CART_COUNT: '/api/cart/count',

        // Orders
        ORDERS: '/api/v1/orders',
    ORDER_BY_ID: (id) => `/api/v1/orders/${id}`,
        ORDER_BY_CODE: (code) => `/api/v1/orders/code/${code}`,
        USER_ORDERS: (userId) => `/api/v1/orders/user/${userId}`,

        // Payment
        PAYMENT_INIT: '/api/v1/payments/init',

        // Delivery
        DELIVERY_BY_ORDER: (orderId) => `/api/v1/deliveries/order/${orderId}`,

        // Drones
        DRONES: '/drones',
        DRONE_BY_CODE: (code) => `/drones/${code}`,
        DRONE_LOCATION: (code) => `/drones/${code}/location`
    }
};

// Local Storage Keys
const STORAGE_KEYS = {
    TOKEN: 'foodfast_token',
    USER: 'foodfast_user',
    CART: 'foodfast_cart'
};

// Helper Functions
const APIHelper = {
    // Get auth headers
    getAuthHeaders() {
        const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    },

    // Make API request
    async request(endpoint, options = {}) {
        const url = `${API_CONFIG.BASE_URL}${endpoint}`;
        const config = {
            ...options,
            headers: {
                ...this.getAuthHeaders(),
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, config);

            // Try to parse JSON safely
            const parseJsonSafe = async () => {
                try { return await response.json(); } catch { return null; }
            };

            if (!response.ok) {
                const data = await parseJsonSafe();
                let message = (data && (data.message || data.error || data.detail)) || response.statusText || 'Request failed';
                // Fallback to text if not JSON
                if (!data) {
                    try { message = await response.text(); } catch { /* ignore */ }
                }
                const err = new Error(message);
                err.status = response.status;
                err.url = url;
                throw err;
            }

            const data = await parseJsonSafe();
            return data;
        } catch (error) {
            console.error('API Error:', { url, error });
            throw error;
        }
    },

    // GET request
    async get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    },

    // POST request
    async post(endpoint, data) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    // PUT request
    async put(endpoint, data) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    // DELETE request
    async delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    }
};

// Auth Helper
const AuthHelper = {
    isLoggedIn() {
        return !!localStorage.getItem(STORAGE_KEYS.TOKEN);
    },

    getUser() {
        const user = localStorage.getItem(STORAGE_KEYS.USER);
        return user ? JSON.parse(user) : null;
    },

    login(token, user) {
        localStorage.setItem(STORAGE_KEYS.TOKEN, token);
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    },

    logout() {
        localStorage.removeItem(STORAGE_KEYS.TOKEN);
        localStorage.removeItem(STORAGE_KEYS.USER);
        window.location.href = 'index.html';
    }
};

// Format helpers
const FormatHelper = {
    currency(amount) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND'
        }).format(amount);
    },

    date(dateString) {
        return new Date(dateString).toLocaleDateString('vi-VN', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    },

    distance(km) {
        if (km < 1) {
            return `${(km * 1000).toFixed(0)}m`;
        }
        return `${km.toFixed(1)}km`;
    }
};

// Toast notification
const Toast = {
    show(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <i class="fas fa-${this.getIcon(type)}"></i>
            <span>${message}</span>
        `;

        document.body.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('show');
        }, 100);

        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    },

    getIcon(type) {
        const icons = {
            success: 'check-circle',
            error: 'exclamation-circle',
            warning: 'exclamation-triangle',
            info: 'info-circle'
        };
        return icons[type] || 'info-circle';
    },

    success(message) {
        this.show(message, 'success');
    },

    error(message) {
        this.show(message, 'error');
    },

    warning(message) {
        this.show(message, 'warning');
    },

    info(message) {
        this.show(message, 'info');
    }
};

// Loading spinner
const Loading = {
    show() {
        let loader = document.getElementById('globalLoader');
        if (!loader) {
            loader = document.createElement('div');
            loader.id = 'globalLoader';
            loader.className = 'global-loader';
            loader.innerHTML = `
                <div class="loader-spinner">
                    <i class="fas fa-drone fa-3x"></i>
                    <p>Đang tải...</p>
                </div>
            `;
            document.body.appendChild(loader);
        }
        loader.style.display = 'flex';
    },

    hide() {
        const loader = document.getElementById('globalLoader');
        if (loader) {
            loader.style.display = 'none';
        }
    }
};

