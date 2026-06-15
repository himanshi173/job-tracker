// Auth Helpers and Forms Handler

const BACKEND_URL = (window.location.port !== '8081' && window.location.hostname === 'localhost') ? 'http://localhost:8081' : '';
const API_BASE = `${BACKEND_URL}/api/auth`;

// Show Custom Alert Notification
function showAlert(message, type = 'success') {
    // Remove existing alerts
    const existing = document.querySelector('.alert');
    if (existing) existing.remove();

    const alertEl = document.createElement('div');
    alertEl.className = `alert alert-${type} glass`;
    
    const icon = type === 'success' ? 
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>` :
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;

    alertEl.innerHTML = `${icon} <span>${message}</span>`;
    document.body.appendChild(alertEl);

    // Trigger animation
    setTimeout(() => alertEl.classList.add('show'), 50);

    // Hide after 4 seconds
    setTimeout(() => {
        alertEl.classList.remove('show');
        setTimeout(() => alertEl.remove(), 400);
    }, 4000);
}

// Check if already authenticated and redirect
function checkSessionRedirect() {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        window.location.href = '/index.html';
    }
}

// Handle Register Form Submission
async function handleRegister(e) {
    e.preventDefault();
    const usernameInput = document.getElementById('username');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const registerBtn = document.getElementById('registerBtn');

    const username = usernameInput.value.trim();
    const email = emailInput.value.trim();
    const password = passwordInput.value;

    if (!username || !email || !password) {
        showAlert('Please fill in all fields', 'error');
        return;
    }

    registerBtn.disabled = true;
    registerBtn.innerHTML = 'Registering...';

    try {
        const response = await fetch(`${API_BASE}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password })
        });

        const data = await response.text();

        if (response.ok) {
            showAlert('Registration successful! Redirecting to login...', 'success');
            setTimeout(() => {
                window.location.href = '/login.html';
            }, 1500);
        } else {
            showAlert(data || 'Registration failed. Try again.', 'error');
            registerBtn.disabled = false;
            registerBtn.innerHTML = 'Sign Up';
        }
    } catch (err) {
        console.error(err);
        showAlert('Failed to connect to server', 'error');
        registerBtn.disabled = false;
        registerBtn.innerHTML = 'Sign Up';
    }
}

// Handle Login Form Submission
async function handleLogin(e) {
    e.preventDefault();
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const loginBtn = document.getElementById('loginBtn');

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    if (!username || !password) {
        showAlert('Please fill in all fields', 'error');
        return;
    }

    loginBtn.disabled = true;
    loginBtn.innerHTML = 'Logging in...';

    try {
        const response = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            const data = await response.json(); // returns token, userId, username
            
            localStorage.setItem('jwt_token', data.token);
            localStorage.setItem('user_id', data.userId);
            localStorage.setItem('username', data.username);
            
            showAlert('Login successful! Redirecting...', 'success');
            setTimeout(() => {
                window.location.href = '/index.html';
            }, 1000);
        } else {
            const errText = await response.text();
            showAlert(errText || 'Invalid credentials', 'error');
            loginBtn.disabled = false;
            loginBtn.innerHTML = 'Sign In';
        }
    } catch (err) {
        console.error(err);
        showAlert('Failed to connect to server', 'error');
        loginBtn.disabled = false;
        loginBtn.innerHTML = 'Sign In';
    }
}

// Attach Event Listeners on Load
document.addEventListener('DOMContentLoaded', () => {
    // Apply saved theme preference on page load
    const savedTheme = localStorage.getItem('theme');
    const systemPrefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
    if (savedTheme === 'light' || (!savedTheme && systemPrefersLight)) {
        document.body.classList.add('light-theme');
    }

    // Check if user is already logged in
    checkSessionRedirect();

    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', handleRegister);
    }
});
