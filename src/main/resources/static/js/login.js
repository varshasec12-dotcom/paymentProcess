(() => {
    const form = document.getElementById('loginForm');
    const message = document.getElementById('message');
    const submitButton = document.getElementById('submitButton');
    const password = document.getElementById('password');

    const params = new URLSearchParams(window.location.search);
    if (params.has('registered')) {
        Api.showMessage(message, 'Registration successful. Please log in.', 'success');
    } else if (params.has('loggedOut')) {
        Api.showMessage(message, 'You have been logged out.', 'info');
    } else if (params.has('expired')) {
        Api.showMessage(message, 'Your session has expired. Please log in again.', 'info');
    }

    // Already logged in: go straight to the dashboard
    Api.get('/api/me').then(response => {
        if (response.ok) {
            window.location.replace('/dashboard.html');
        }
    }).catch(() => {});

    document.getElementById('showPassword').addEventListener('change', event => {
        password.type = event.target.checked ? 'text' : 'password';
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const email = form.email.value.trim();
        if (!email || !password.value) {
            Api.showMessage(message, 'Please enter your email address and password.', 'error');
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = 'Logging in...';
        try {
            const response = await Api.post('/api/auth/login', { email, password: password.value }, true);
            if (response.ok) {
                window.location.replace('/dashboard.html');
                return;
            }
            Api.showMessage(message, await Api.errorMessage(response), 'error');
            password.value = '';
            password.focus();
        } catch (error) {
            Api.showMessage(message, error.message, 'error');
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = 'Log in';
        }
    });
})();
