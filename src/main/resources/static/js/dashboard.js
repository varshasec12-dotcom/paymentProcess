(() => {
    const message = document.getElementById('message');
    const balanceFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // Not logged in, or the session timed out
    function toLogin(reason) {
        window.location.replace('/login.html' + (reason ? '?' + reason : ''));
    }

    async function load(url) {
        const response = await Api.get(url);
        if (response.status === 401) {
            toLogin('expired');
            return null;
        }
        if (!response.ok) {
            throw new Error(await Api.errorMessage(response));
        }
        return response.json();
    }

    function showProfile(customer) {
        document.getElementById('greeting').textContent = 'Welcome, ' + customer.firstName;
        document.getElementById('customerId').textContent = customer.customerId;
        document.getElementById('name').textContent = customer.firstName + ' ' + customer.lastName;
        document.getElementById('email').textContent = customer.email;
        document.getElementById('phone').textContent = customer.phoneNumber;
    }

    function showAccounts(accounts) {
        if (accounts.length === 0) {
            document.getElementById('noAccounts').hidden = false;
            return;
        }
        const body = document.getElementById('accountsBody');
        accounts.forEach(account => {
            const row = body.insertRow();
            row.insertCell().textContent = account.accountNumber;
            row.insertCell().textContent = account.accountType;
            const balance = row.insertCell();
            balance.className = 'amount';
            balance.textContent = balanceFormat.format(account.balance);
        });
        document.getElementById('accountsTable').hidden = false;
    }

    async function init() {
        try {
            const customer = await load('/api/me');
            if (!customer) {
                return;
            }
            showProfile(customer);
            const accounts = await load('/api/me/accounts');
            if (accounts) {
                showAccounts(accounts);
            }
        } catch (error) {
            Api.showMessage(message, error.message, 'error');
        }
    }

    document.getElementById('logoutButton').addEventListener('click', async () => {
        try {
            await Api.post('/api/auth/logout');
        } finally {
            toLogin('loggedOut');
        }
    });

    init();
})();
