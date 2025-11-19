// API Configuration
const API_CONFIG = {
    // Dynamic base URL:
    // - When running via frontend dev server (localhost:3000), use '' so requests go through the proxy (/api and /auth are proxied)
    // - Otherwise, default to current origin (works when frontend served by backend),
    //   and fallback to http://localhost:8080 if origin is unavailable
    BASE_URL: (() => {
        try {
            const origin = window.location.origin || '';
            // Treat any dev server on port 300x as proxy mode (even when accessed via IP or hostname)
            const port = (window.location && window.location.port) ? String(window.location.port) : '';
            if (/^300\d$/.test(port)) {
                return '';
            }
            if (origin && origin.startsWith('http')) {
                return origin;
            }
        } catch (_) { /* ignore */ }
        return 'http://localhost:8080';
    })(),
    ENDPOINTS: {
        // Authentication
        LOGIN: '/auth/login',
    VALIDATE: '/auth/validate',
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
        ORDERS_BY_STORE: (storeId) => `/api/v1/orders/store/${storeId}`,
        USER_ORDERS: (userId) => `/api/v1/orders/user/${userId}`,
        KITCHEN_QUEUE: (storeId, status) => `/api/v1/orders/store/${storeId}/kitchen-queue${status ? `?status=${status}` : ''}`,
        ORDER_ACCEPT: (orderId) => `/api/v1/orders/${orderId}/accept`,
        ORDER_REJECT: (orderId, reason) => `/api/v1/orders/${orderId}/reject${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
        ORDER_KITCHEN_COMPLETE: (orderId) => `/api/v1/orders/${orderId}/kitchen-complete`,

        // Users (admin)
        USERS: '/users/getAllUser',
        USER_BY_ID: (id) => `/api/v1/users/${id}`,
        USER_UPDATE: (id) => `/api/v1/users/${id}`,
        USER_ADDRESSES: (userId) => `/api/v1/users/${userId}/addresses`,
        USER_ADDRESS_UPDATE: (userId, addressId) => `/api/v1/users/${userId}/addresses/${addressId}`,
        USER_ADDRESS_DELETE: (userId, addressId) => `/api/v1/users/${userId}/addresses/${addressId}`,
        USER_ADDRESS_SET_DEFAULT: (userId, addressId) => `/api/v1/users/${userId}/addresses/${addressId}/set-default`,

        // Payment
        PAYMENT_INIT: '/api/v1/payments/init',

        // Delivery
    DELIVERY_BY_ORDER: (orderId) => `/api/v1/deliveries/order/${orderId}`,
    DELIVERY_DEMO_KICKOFF: (orderId) => `/api/v1/deliveries/demo/kickoff?orderId=${orderId}&autoAssign=true&autoProgress=true`,

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
        let token = null;
        try {
            token = localStorage.getItem(STORAGE_KEYS.TOKEN);
            if (!token) {
                // Fallback to legacy key if present
                token = localStorage.getItem('authToken');
            }
        } catch (_) { /* ignore */ }
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    },

    // Make API request
    async request(endpoint, options = {}) {
        const url = `${API_CONFIG.BASE_URL}${endpoint}`;
        const baseHeaders = (options.auth === false)
            ? { 'Content-Type': 'application/json' }
            : this.getAuthHeaders();
        const config = {
            ...options,
            headers: {
                ...baseHeaders,
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
                // Fallback: when running on a non-proxy server (like http-server), retry to backend directly
                const onDevPort = (() => { try { return /localhost:300\d|127\.0\.0\.1:300\d/.test(window.location.origin || ''); } catch { return false; } })();
                const canFallback = API_CONFIG.BASE_URL === '' && onDevPort && !options.__retried && (endpoint.startsWith('/auth') || endpoint.startsWith('/api') || endpoint.startsWith('/products') || endpoint.startsWith('/categories') || endpoint.startsWith('/users') || endpoint.startsWith('/drones'));
                if (response.status === 404 && canFallback) {
                    const altUrl = `http://localhost:8080${endpoint}`;
                    const retryConfig = {
                        ...options,
                        __retried: true,
                        headers: {
                            ...((options.auth === false) ? { 'Content-Type': 'application/json' } : this.getAuthHeaders()),
                            ...options.headers
                        }
                    };
                    const retryResp = await fetch(altUrl, retryConfig);
                    const retryData = await (async () => { try { return await retryResp.json(); } catch { return null; } })();
                    if (!retryResp.ok) {
                        let message = (retryData && (retryData.message || retryData.error || retryData.detail)) || retryResp.statusText || 'Request failed';
                        const err = new Error(message);
                        err.status = retryResp.status;
                        err.url = altUrl;
                        throw err;
                    }
                    return retryData;
                }
                const data = await parseJsonSafe();
                let message = (data && (data.message || data.error || data.detail)) || response.statusText || 'Request failed';
                if (response.status === 401) {
                    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
                    // Force logout only on authentication expiration
                    try { AuthHelper.logout(); } catch (_) { /* ignore */ }
                } else if (response.status === 403) {
                    // Do not logout on forbidden; let caller handle UX (e.g., warn + revert)
                    message = 'Không đủ quyền truy cập cho thao tác này.';
                }
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

    // POST request without auth header (e.g., login/signup)
    async postNoAuth(endpoint, data) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data),
            auth: false
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
        try {
            return !!(localStorage.getItem(STORAGE_KEYS.TOKEN) || localStorage.getItem('authToken'));
        } catch (_) {
            return false;
        }
    },

    getUser() {
        let user = null;
        try {
            user = localStorage.getItem(STORAGE_KEYS.USER) || localStorage.getItem('user');
        } catch (_) { /* ignore */ }
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

