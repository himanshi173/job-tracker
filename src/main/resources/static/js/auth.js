// Auth Helpers and Forms Handler

const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.hostname === '';
const BACKEND_URL = (isLocalhost && window.location.port !== '8081') ? 'http://localhost:8081' : '';
const API_BASE = `${BACKEND_URL}/api/auth`;

// Email validation regex helper
const EMAIL_REGEX = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$/;

function isValidEmail(email) {
    return EMAIL_REGEX.test(email);
}

// Show Custom Alert Notification
function showAlert(message, type = 'success') {
    const existing = document.querySelector('.alert');
    if (existing) existing.remove();

    const alertEl = document.createElement('div');
    alertEl.className = `alert alert-${type} glass`;
    
    const icon = type === 'success' ? 
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>` :
        `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;

    alertEl.innerHTML = `${icon} <span>${message}</span>`;
    document.body.appendChild(alertEl);

    setTimeout(() => alertEl.classList.add('show'), 50);

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

// ==========================================================================
// Sign Up Flow with OTP Verification
// ==========================================================================
async function sendSignupOtp() {
    const emailInput = document.getElementById('email');
    const sendOtpBtn = document.getElementById('sendOtpBtn');
    const email = emailInput.value.trim();

    if (!email) {
        showAlert('Please enter your email address first', 'error');
        return;
    }

    if (!isValidEmail(email)) {
        showAlert('Please enter a valid email address', 'error');
        return;
    }

    sendOtpBtn.disabled = true;
    sendOtpBtn.innerHTML = 'Sending...';

    try {
        const response = await fetch(`${API_BASE}/send-otp`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, purpose: 'signup' })
        });

        if (response.ok) {
            const data = await response.json();
            showAlert('Verification code sent to your email!', 'success');
            document.getElementById('otpGroup').classList.remove('hidden');
            document.getElementById('registerBtn').disabled = false;
            sendOtpBtn.innerHTML = 'Resend OTP';
            sendOtpBtn.disabled = false;
        } else {
            const errText = await response.text();
            showAlert(errText || 'Failed to send OTP. Try again.', 'error');
            sendOtpBtn.disabled = false;
            sendOtpBtn.innerHTML = 'Send OTP';
        }
    } catch (err) {
        console.error(err);
        showAlert('Failed to connect to server', 'error');
        sendOtpBtn.disabled = false;
        sendOtpBtn.innerHTML = 'Send OTP';
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const usernameInput = document.getElementById('username');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const otpInput = document.getElementById('otp');
    const registerBtn = document.getElementById('registerBtn');

    const username = usernameInput.value.trim();
    const email = emailInput.value.trim();
    const password = passwordInput.value;
    const otp = otpInput.value.trim();

    if (!username || !email || !password || !otp) {
        showAlert('Please fill in all fields including the verification code', 'error');
        return;
    }

    if (!isValidEmail(email)) {
        showAlert('Please enter a valid email address', 'error');
        return;
    }

    if (otp.length !== 6) {
        showAlert('OTP must be a 6-digit code', 'error');
        return;
    }

    registerBtn.disabled = true;
    registerBtn.innerHTML = 'Verifying & Registering...';

    try {
        const response = await fetch(`${API_BASE}/register-with-otp`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password, otp })
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

// ==========================================================================
// Log In Flow
// ==========================================================================
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
            const data = await response.json();
            
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

// ==========================================================================
// Forgot Password Flow
// ==========================================================================
async function sendResetOtp() {
    const emailInput = document.getElementById('forgotEmail');
    const sendResetOtpBtn = document.getElementById('sendResetOtpBtn');
    const email = emailInput.value.trim();

    if (!email) {
        showAlert('Please enter your registered email address first', 'error');
        return;
    }

    if (!isValidEmail(email)) {
        showAlert('Please enter a valid email address', 'error');
        return;
    }

    sendResetOtpBtn.disabled = true;
    sendResetOtpBtn.innerHTML = 'Sending...';

    try {
        const response = await fetch(`${API_BASE}/send-otp`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, purpose: 'reset' })
        });

        if (response.ok) {
            const data = await response.json();
            showAlert('Reset verification code sent to your email!', 'success');
            document.getElementById('resetFieldsGroup').classList.remove('hidden');
            document.getElementById('submitResetBtn').disabled = false;
            sendResetOtpBtn.innerHTML = 'Resend OTP';
            sendResetOtpBtn.disabled = false;
        } else {
            const errText = await response.text();
            showAlert(errText || 'Failed to send OTP. Verify your email.', 'error');
            sendResetOtpBtn.disabled = false;
            sendResetOtpBtn.innerHTML = 'Send OTP';
        }
    } catch (err) {
        console.error(err);
        showAlert('Failed to connect to server', 'error');
        sendResetOtpBtn.disabled = false;
        sendResetOtpBtn.innerHTML = 'Send OTP';
    }
}

async function handleResetPassword(e) {
    e.preventDefault();
    const emailInput = document.getElementById('forgotEmail');
    const otpInput = document.getElementById('resetOtp');
    const newPasswordInput = document.getElementById('newPassword');
    const submitResetBtn = document.getElementById('submitResetBtn');

    const email = emailInput.value.trim();
    const otp = otpInput.value.trim();
    const newPassword = newPasswordInput.value;

    if (!email || !otp || !newPassword) {
        showAlert('Please fill in all fields', 'error');
        return;
    }

    if (!isValidEmail(email)) {
        showAlert('Please enter a valid email address', 'error');
        return;
    }

    if (otp.length !== 6) {
        showAlert('Verification code must be 6 digits', 'error');
        return;
    }

    submitResetBtn.disabled = true;
    submitResetBtn.innerHTML = 'Resetting...';

    try {
        const response = await fetch(`${API_BASE}/reset-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, otp, newPassword })
        });

        const data = await response.text();

        if (response.ok) {
            showAlert('Password reset successful! Please log in.', 'success');
            setTimeout(() => {
                closeForgotPasswordModal();
            }, 1500);
        } else {
            showAlert(data || 'Failed to reset password. Try again.', 'error');
            submitResetBtn.disabled = false;
            submitResetBtn.innerHTML = 'Reset Password';
        }
    } catch (err) {
        console.error(err);
        showAlert('Failed to connect to server', 'error');
        submitResetBtn.disabled = false;
        submitResetBtn.innerHTML = 'Reset Password';
    }
}

function openForgotPasswordModal() {
    const modal = document.getElementById('forgotPasswordModalOverlay');
    if (modal) {
        modal.classList.add('show');
    }
}

function closeForgotPasswordModal() {
    const modal = document.getElementById('forgotPasswordModalOverlay');
    if (modal) {
        modal.classList.remove('show');
        // Reset recovery form
        document.getElementById('forgotPasswordForm').reset();
        document.getElementById('resetFieldsGroup').classList.add('hidden');
        document.getElementById('submitResetBtn').disabled = true;
        const sendResetOtpBtn = document.getElementById('sendResetOtpBtn');
        if (sendResetOtpBtn) {
            sendResetOtpBtn.disabled = false;
            sendResetOtpBtn.innerHTML = 'Send OTP';
        }
    }
}

// ==========================================================================
// Initialization & Bindings
// ==========================================================================
document.addEventListener('DOMContentLoaded', () => {
    // Apply saved theme preference on page load
    const savedTheme = localStorage.getItem('theme');
    const systemPrefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
    if (savedTheme === 'light' || (!savedTheme && systemPrefersLight)) {
        document.body.classList.add('light-theme');
    }

    // Check if user is already logged in
    checkSessionRedirect();

    // Wire up password visibility toggles
    const toggles = document.querySelectorAll('.password-toggle-btn');
    toggles.forEach(toggle => {
        toggle.addEventListener('click', () => {
            const input = toggle.parentElement.querySelector('input');
            const eyeIcon = toggle.querySelector('.eye-icon');
            const eyeOffIcon = toggle.querySelector('.eye-off-icon');
            
            if (input.type === 'password') {
                input.type = 'text';
                eyeIcon.classList.add('hidden');
                eyeOffIcon.classList.remove('hidden');
            } else {
                input.type = 'password';
                eyeOffIcon.classList.add('hidden');
                eyeIcon.classList.remove('hidden');
            }
        });
    });

    // Sign Up page bindings
    const sendOtpBtn = document.getElementById('sendOtpBtn');
    if (sendOtpBtn) {
        sendOtpBtn.addEventListener('click', sendSignupOtp);
    }

    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', handleRegister);
    }

    // Login page bindings
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    const forgotPasswordLink = document.getElementById('forgotPasswordLink');
    if (forgotPasswordLink) {
        forgotPasswordLink.addEventListener('click', (e) => {
            e.preventDefault();
            openForgotPasswordModal();
        });
    }

    const closeResetBtn = document.getElementById('closeForgotPasswordModalBtn');
    if (closeResetBtn) {
        closeResetBtn.addEventListener('click', closeForgotPasswordModal);
    }

    const cancelResetBtn = document.getElementById('cancelResetBtn');
    if (cancelResetBtn) {
        cancelResetBtn.addEventListener('click', closeForgotPasswordModal);
    }

    const forgotPasswordModalOverlay = document.getElementById('forgotPasswordModalOverlay');
    if (forgotPasswordModalOverlay) {
        forgotPasswordModalOverlay.addEventListener('click', (e) => {
            if (e.target === forgotPasswordModalOverlay) {
                closeForgotPasswordModal();
            }
        });
    }

    const sendResetOtpBtn = document.getElementById('sendResetOtpBtn');
    if (sendResetOtpBtn) {
        sendResetOtpBtn.addEventListener('click', sendResetOtp);
    }

    const forgotPasswordForm = document.getElementById('forgotPasswordForm');
    if (forgotPasswordForm) {
        forgotPasswordForm.addEventListener('submit', handleResetPassword);
    }
});
