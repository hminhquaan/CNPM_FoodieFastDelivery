// Authentication Manager
class AuthManager {
    constructor() {
        this.token = localStorage.getItem('authToken');
        this.user = JSON.parse(localStorage.getItem('user') || 'null');
    }

    isAuthenticated() {
        return !!this.token;
    }

    getUser() {
        return this.user;
    }

    async login(username, password) {
        try {
            const response = await api.login(username, password);

            if (response.result && response.result.token) {
                const r = response.result;
                this.token = r.token;
                // Persist a richer user object including roles if present
                this.user = r.user || {
                    username: r.username || username,
                    fullName: r.fullName || r.username || username,
                    email: r.email || '',
                    roles: r.roles || []
                };

                // Write to legacy keys
                localStorage.setItem('authToken', this.token);
                localStorage.setItem('user', JSON.stringify(this.user));
                // Also write to shared STORAGE_KEYS for consistency across modules
                try {
                    if (typeof STORAGE_KEYS !== 'undefined') {
                        if (STORAGE_KEYS.TOKEN) localStorage.setItem(STORAGE_KEYS.TOKEN, this.token);
                        if (STORAGE_KEYS.USER) localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(this.user));
                    }
                } catch (_) { /* ignore */ }

                return { success: true, user: this.user };
            } else {
                throw new Error('Invalid login response');
            }
        } catch (error) {
            console.error('Login error:', error);
            return { success: false, message: error.message };
        }
    }

    async signup(userData) {
        try {
            const response = await api.signup(userData);

            if (response.code === 200 || response.result) {
                return { success: true, message: 'Đăng ký thành công! Vui lòng đăng nhập.' };
            } else {
                throw new Error(response.message || 'Đăng ký thất bại');
            }
        } catch (error) {
            console.error('Signup error:', error);
            return { success: false, message: error.message };
        }
    }

    logout() {
        this.token = null;
        this.user = null;
        // Remove legacy keys
        localStorage.removeItem('authToken');
        localStorage.removeItem('user');
        // Also remove shared STORAGE_KEYS
        try {
            if (typeof STORAGE_KEYS !== 'undefined') {
                if (STORAGE_KEYS.TOKEN) localStorage.removeItem(STORAGE_KEYS.TOKEN);
                if (STORAGE_KEYS.USER) localStorage.removeItem(STORAGE_KEYS.USER);
            }
        } catch (_) { /* ignore */ }
        window.location.href = 'index.html';
    }

    updateUI() {
        const userMenu = document.getElementById('userMenu');
        if (!userMenu) return;

        if (this.isAuthenticated()) {
            userMenu.innerHTML = `
                <div class="user-dropdown" id="userDropdown">
                    <button class="user-avatar" id="userAvatar">
                        <i class="fas fa-user-circle"></i>
                        <span>${this.user?.username || 'User'}</span>
                    </button>
                    <div class="dropdown-menu" id="dropdownMenu">
                        <a href="profile.html"><i class="fas fa-user"></i> Tài khoản</a>
                        <a href="orders.html"><i class="fas fa-box"></i> Đơn hàng của tôi</a>
                        <a href="#" id="logoutBtn"><i class="fas fa-sign-out-alt"></i> Đăng xuất</a>
                    </div>
                </div>
            `;

            // Add dropdown toggle
            const userAvatar = document.getElementById('userAvatar');
            const dropdownMenu = document.getElementById('dropdownMenu');

            if (userAvatar && dropdownMenu) {
                userAvatar.addEventListener('click', (e) => {
                    e.stopPropagation();
                    dropdownMenu.classList.toggle('active');
                });

                document.addEventListener('click', () => {
                    dropdownMenu.classList.remove('active');
                });
            }

            // Add logout handler
            const logoutBtn = document.getElementById('logoutBtn');
            if (logoutBtn) {
                logoutBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.logout();
                });
            }
        } else {
            userMenu.innerHTML = `
                <button class="btn btn-primary" id="loginBtn">Đăng nhập</button>
                <button class="btn btn-outline" id="signupBtn">Đăng ký</button>
            `;

            // Add modal handlers
            const loginBtn = document.getElementById('loginBtn');
            const signupBtn = document.getElementById('signupBtn');

            if (loginBtn) {
                loginBtn.addEventListener('click', () => {
                    showLoginModal();
                });
            }

            if (signupBtn) {
                signupBtn.addEventListener('click', () => {
                    showSignupModal();
                });
            }
        }
    }
}

// Create global auth manager
const auth = new AuthManager();

// Modal functions
function showLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) {
        modal.style.display = 'block';
    }
}

function hideLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function showSignupModal() {
    const modal = document.getElementById('signupModal');
    if (modal) {
        modal.style.display = 'block';
    }
}

function hideSignupModal() {
    const modal = document.getElementById('signupModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

// Initialize auth UI when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    auth.updateUI();

    // Login modal handlers
    const closeLoginModal = document.getElementById('closeLoginModal');
    if (closeLoginModal) {
        closeLoginModal.addEventListener('click', hideLoginModal);
    }

    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const username = document.getElementById('loginUsername').value;
            const password = document.getElementById('loginPassword').value;

            const result = await auth.login(username, password);
            if (result.success) {
                hideLoginModal();
                auth.updateUI();
                window.location.reload();
            } else {
                alert('Đăng nhập thất bại: ' + result.message);
            }
        });
    }

    // Signup modal handlers
    const closeSignupModal = document.getElementById('closeSignupModal');
    if (closeSignupModal) {
        closeSignupModal.addEventListener('click', hideSignupModal);
    }

    const signupForm = document.getElementById('signupForm');
    if (signupForm) {
        signupForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const userData = {
                username: document.getElementById('signupUsername').value,
                email: document.getElementById('signupEmail').value,
                phone: document.getElementById('signupPhone').value,
                fullName: document.getElementById('signupFullName').value,
                password: document.getElementById('signupPassword').value
            };

            const result = await auth.signup(userData);
            if (result.success) {
                alert(result.message);
                hideSignupModal();
                showLoginModal();
            } else {
                alert('Đăng ký thất bại: ' + result.message);
            }
        });
    }

    // Switch between login and signup
    const switchToSignup = document.getElementById('switchToSignup');
    if (switchToSignup) {
        switchToSignup.addEventListener('click', (e) => {
            e.preventDefault();
            hideLoginModal();
            showSignupModal();
        });
    }

    const switchToLogin = document.getElementById('switchToLogin');
    if (switchToLogin) {
        switchToLogin.addEventListener('click', (e) => {
            e.preventDefault();
            hideSignupModal();
            showLoginModal();
        });
    }

    // Close modals when clicking outside
    window.addEventListener('click', (e) => {
        const loginModal = document.getElementById('loginModal');
        const signupModal = document.getElementById('signupModal');

        if (e.target === loginModal) {
            hideLoginModal();
        }
        if (e.target === signupModal) {
            hideSignupModal();
        }
    });
});
// Helper function to build URL with parameters
function buildUrl(endpoint, params = {}) {
    let url = API_CONFIG.BASE_URL + endpoint;
    Object.keys(params).forEach(key => {
        url = url.replace(`{${key}}`, params[key]);
    });
    return url;
}

