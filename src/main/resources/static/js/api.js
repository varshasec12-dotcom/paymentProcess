// Shared helpers for the login, registration and dashboard pages.
const Api = (() => {

    // Every POST needs a fresh CSRF token; the server rotates it at login.
    async function getCsrfToken() {
        const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error('Could not reach the server. Please try again.');
        }
        return response.json();
    }

    async function get(url) {
        return fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
    }

    // asForm: send as a regular form post (needed by the login endpoint)
    async function post(url, body, asForm = false) {
        const csrf = await getCsrfToken();
        const headers = { Accept: 'application/json', [csrf.headerName]: csrf.token };
        let payload;
        if (asForm) {
            headers['Content-Type'] = 'application/x-www-form-urlencoded';
            payload = new URLSearchParams(body);
        } else if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
            payload = JSON.stringify(body);
        }
        return fetch(url, { method: 'POST', credentials: 'same-origin', headers, body: payload });
    }

    async function errorMessage(response) {
        try {
            const body = await response.json();
            return body.message || 'Something went wrong. Please try again.';
        } catch {
            return 'Something went wrong. Please try again.';
        }
    }

    // type: 'error' | 'success' | 'info'; an empty text hides the message
    function showMessage(element, text, type) {
        element.textContent = text;
        element.className = 'message ' + type;
        element.hidden = !text;
    }

    return { get, post, errorMessage, showMessage };
})();
