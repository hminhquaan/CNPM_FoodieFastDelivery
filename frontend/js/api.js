// API Service for making HTTP requests
class APIService {
    constructor() {
        this.baseUrl = API_CONFIG.BASE_URL;
    }

    // Get auth token from localStorage
    getAuthToken() {
        // Prefer shared STORAGE_KEYS.TOKEN from config.js if present, fallback to legacy 'authToken'
        try {
            if (typeof STORAGE_KEYS !== 'undefined' && STORAGE_KEYS.TOKEN) {
                const t = localStorage.getItem(STORAGE_KEYS.TOKEN);
                if (t) return t;
            }
        } catch (_) { /* ignore */ }
        return localStorage.getItem('authToken');
    }

    // Get headers with auth token
    getHeaders(includeAuth = true) {
        const headers = {
            'Content-Type': 'application/json'
        };

        if (includeAuth) {
            const token = this.getAuthToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
        }

        return headers;
    }

    // Generic request method
    async request(url, options = {}) {
        try {
            const response = await fetch(url, {
                ...options,
                headers: this.getHeaders(options.auth !== false)
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || 'Request failed');
            }

            return data;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    // GET request
    async get(endpoint, params = {}) {
        const url = buildUrl(endpoint, params);
        return this.request(url, { method: 'GET' });
    }

    // POST request
    async post(endpoint, body = {}, params = {}) {
        const url = buildUrl(endpoint, params);
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(body)
        });
    }

    // PUT request
    async put(endpoint, body = {}, params = {}) {
        const url = buildUrl(endpoint, params);
        return this.request(url, {
            method: 'PUT',
            body: JSON.stringify(body)
        });
    }

    // DELETE request
    async delete(endpoint, params = {}) {
        const url = buildUrl(endpoint, params);
        return this.request(url, { method: 'DELETE' });
    }

    // Authentication APIs
    async login(username, password) {
        return this.post(API_CONFIG.ENDPOINTS.LOGIN, { username, password }, {}, false);
    }

    async signup(userData) {
        return this.post(API_CONFIG.ENDPOINTS.SIGNUP, userData, {}, false);
    }

    async logout() {
        return this.post(API_CONFIG.ENDPOINTS.LOGOUT);
    }

    // Product APIs
    async getProducts() {
        return this.get(API_CONFIG.ENDPOINTS.PRODUCTS);
    }

    async getProductById(id) {
        return this.get(API_CONFIG.ENDPOINTS.PRODUCT_BY_ID, { id });
    }

    // Category APIs
    async getCategories() {
        return this.get(API_CONFIG.ENDPOINTS.CATEGORIES);
    }

    async getCategoryById(id) {
        return this.get(API_CONFIG.ENDPOINTS.CATEGORY_BY_ID, { id });
    }

    // Cart APIs
    async getCart() {
        return this.get(API_CONFIG.ENDPOINTS.CART);
    }

    async addToCart(productId, quantity, storeId) {
        return this.post(API_CONFIG.ENDPOINTS.CART_ADD, {
            productId,
            quantity,
            storeId
        });
    }

    async updateCartItem(productId, quantity) {
        return this.put(API_CONFIG.ENDPOINTS.CART_UPDATE, { quantity }, { productId });
    }

    async removeCartItem(productId) {
        return this.delete(API_CONFIG.ENDPOINTS.CART_REMOVE, { productId });
    }

    async clearCart() {
        return this.delete(API_CONFIG.ENDPOINTS.CART_CLEAR);
    }

    // Order APIs
    async createOrder(orderData) {
        return this.post(API_CONFIG.ENDPOINTS.ORDERS, orderData);
    }

    async getOrderById(orderId) {
        // Use function form from API_CONFIG to build endpoint
        const endpoint = (typeof API_CONFIG.ENDPOINTS.ORDER_BY_ID === 'function')
            ? API_CONFIG.ENDPOINTS.ORDER_BY_ID(orderId)
            : buildUrl(API_CONFIG.ENDPOINTS.ORDER_BY_ID, { id: orderId, orderId });
        return this.request(this.baseUrl + endpoint, { method: 'GET' });
    }

    async getUserOrders(userId) {
        return this.get(API_CONFIG.ENDPOINTS.USER_ORDERS, { userId });
    }

    async getAllOrders() {
        return this.get(API_CONFIG.ENDPOINTS.ORDERS);
    }

    // Payment APIs
    async initPayment(orderId, provider = 'VNPAY', method = 'QR') {
        return this.post(API_CONFIG.ENDPOINTS.PAYMENT_INIT, {
            orderId,
            provider,
            method
        });
    }

    // Store APIs
    async getStoreByProduct(productId) {
        return this.get(API_CONFIG.ENDPOINTS.STORE_BY_PRODUCT, { productId });
    }

    async getStoreWithProducts(storeId) {
        return this.get(API_CONFIG.ENDPOINTS.STORE_WITH_PRODUCTS, { storeId });
    }

    async getStores() {
        return this.get(API_CONFIG.ENDPOINTS.STORES);
    }

    async getStoreById(id) {
        return this.get(API_CONFIG.ENDPOINTS.STORE_BY_ID(id));
    }

    async createStore(body) {
        return this.post(API_CONFIG.ENDPOINTS.STORES, body);
    }

    async updateStore(id, body) {
        return this.put(API_CONFIG.ENDPOINTS.STORE_BY_ID(id), body);
    }

    async deleteStore(id) {
        return this.delete(API_CONFIG.ENDPOINTS.STORE_BY_ID(id));
    }

    async updateStorePayment(id, body) {
        const endpoint = `/api/stores/${id}/payment`;
        return this.request(`${this.baseUrl}${endpoint}`, { method: 'PATCH', body: JSON.stringify(body) });
    }

    // Store Address APIs
    async getStoreAddresses(storeId) {
        return this.get(`/api/stores/${storeId}/addresses`);
    }

    async createStoreAddress(storeId, body) {
        return this.post(`/api/stores/${storeId}/addresses`, body);
    }

    async updateStoreAddress(storeId, addressId, body) {
        return this.put(`/api/stores/${storeId}/addresses/${addressId}`, body);
    }

    async deleteStoreAddress(storeId, addressId) {
        return this.delete(`/api/stores/${storeId}/addresses/${addressId}`);
    }

    // User APIs (admin)
    async getUsers() {
        return this.get('/users/getAllUser');
    }

    async getUserById(id) {
        return this.get(`/users/GetUserById/${id}`);
    }

    // User Address APIs
    async getUserAddresses(userId) {
        return this.get(`/users/${userId}/addresses`);
    }

    async createUserAddress(userId, body) {
        return this.post(`/users/${userId}/addresses`, body);
    }

    async updateUserAddress(userId, addressId, body) {
        return this.put(`/users/${userId}/addresses/${addressId}`, body);
    }

    async deleteUserAddress(userId, addressId) {
        return this.delete(`/users/${userId}/addresses/${addressId}`);
    }

    async setDefaultUserAddress(userId, addressId) {
        return this.put(`/users/${userId}/addresses/${addressId}/set-default`, {});
    }

    // Drone APIs
    async getDrones() {
        return this.get(API_CONFIG.ENDPOINTS.DRONES);
    }

    async registerDrone(body) {
        return this.post('/drones/register', body);
    }

    async updateDroneStatus(code, status) {
        return this.post(`/drones/${code}/status`, { status });
    }

    async updateDroneLocation(code, latitude, longitude, batteryPercent) {
        const payload = { latitude, longitude };
        if (typeof batteryPercent === 'number') payload.batteryPercent = batteryPercent;
        return this.post(`/drones/${code}/location`, payload);
    }

    async deleteDrone(code) {
        return this.delete(`/drones/${code}`);
    }
}

// Create global API service instance
const api = new APIService();

