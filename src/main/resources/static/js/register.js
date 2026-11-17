(() => {
    const form = document.getElementById('registerForm');
    const message = document.getElementById('message');
    const submitButton = document.getElementById('submitButton');
    const passwordFields = [form.password, form.confirmPassword];

    // Same rule as RegisterRequest on the server, checked here for quicker feedback
    const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/;
    // Same rule as ValidationUtil.isValidEmail on the server
    const EMAIL_RULE = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    const ALLOWED_DOMAINS = form.email.dataset.allowedDomains.split(',').map(domain => domain.trim().toLowerCase());

    document.getElementById('showPassword').addEventListener('change', event => {
        passwordFields.forEach(field => field.type = event.target.checked ? 'text' : 'password');
    });

    function validate(data) {
        const missing = Object.entries(data).filter(([, value]) => !value).length;
        if (missing) {
            return 'Please fill in all fields.';
        }
        if (!EMAIL_RULE.test(data.email)) {
            return 'Please enter a valid email address, for example name@gmail.com.';
        }
        const domain = data.email.slice(data.email.lastIndexOf('@') + 1).toLowerCase();
        if (!ALLOWED_DOMAINS.includes(domain)) {
            return 'Email domain not accepted. Please use an address ending in '
                + ALLOWED_DOMAINS.map(allowed => '@' + allowed).join(', ') + '.';
        }
        if (!PASSWORD_RULE.test(data.password)) {
            return 'Password must be 8-64 characters with an uppercase letter, a lowercase letter, '
                + 'a number and a special character.';
        }
        if (data.password !== data.confirmPassword) {
            return 'Passwords do not match.';
        }
        return null;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const data = {
            firstName: form.firstName.value.trim(),
            lastName: form.lastName.value.trim(),
            email: form.email.value.trim(),
            phoneNumber: form.phoneNumber.value.trim(),
            accountType: form.accountType.value,
            password: form.password.value,
            confirmPassword: form.confirmPassword.value
        };

        const problem = validate(data);
        if (problem) {
            Api.showMessage(message, problem, 'error');
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = 'Registering...';
        try {
            const response = await Api.post('/api/auth/register', data);
            if (response.status === 201) {
                window.location.replace('/login.html?registered');
                return;
            }
            Api.showMessage(message, await Api.errorMessage(response), 'error');
        } catch (error) {
            Api.showMessage(message, error.message, 'error');
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = 'Register';
        }
    });
})();
