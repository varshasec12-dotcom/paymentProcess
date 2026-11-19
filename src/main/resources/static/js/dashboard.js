(() => {
    const message = document.getElementById('message');
    const money = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    const TYPE_LABELS = { SAVINGS: 'Savings', CURRENT: 'Current', SALARY: 'Salary' };

    let accounts = [];

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

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function typeLabel(type) {
        return TYPE_LABELS[type] || type;
    }

    // Show only the last 4 digits in account pickers, e.g. 100000000008 -> XXXXXXXX0008
    function maskAccountNumber(accountNumber) {
        const visible = 4;
        return accountNumber.length <= visible
            ? accountNumber
            : 'X'.repeat(accountNumber.length - visible) + accountNumber.slice(-visible);
    }

    // ---------- Navigation ----------

    function showView(name) {
        document.querySelectorAll('.view').forEach(view => {
            view.hidden = view.id !== 'view-' + name;
        });
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.toggle('active', link.dataset.view === name);
        });
    }

    // Each view change adds a browser history entry so Back/Forward move between views
    function navigate(name) {
        if (location.hash !== '#' + name) {
            history.pushState(null, '', '#' + name);
        }
        showView(name);
    }

    function viewFromHash() {
        const name = location.hash.slice(1);
        return document.getElementById('view-' + name) ? name : 'overview';
    }

    window.addEventListener('popstate', () => showView(viewFromHash()));

    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            navigate(link.dataset.view);
        });
    });

    // ---------- Profile ----------

    function showProfile(customer) {
        document.getElementById('greeting').textContent = 'Welcome, ' + customer.firstName;
        const avatar = document.getElementById('avatar');
        avatar.textContent = (customer.firstName[0] || '') + (customer.lastName ? customer.lastName[0] : '');
        // Full name as a tooltip when hovering over the initials
        avatar.title = [customer.firstName, customer.lastName].filter(Boolean).join(' ');
        document.getElementById('customerId').textContent = customer.customerId;
        document.getElementById('name').textContent = customer.firstName + ' ' + customer.lastName;
        document.getElementById('email').textContent = customer.email;
        document.getElementById('phone').textContent = customer.phoneNumber;
    }

    // ---------- Hidden balances ----------

    const MASK = 'XXX';
    const EYE_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" '
        + 'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
        + '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    const EYE_OFF_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" '
        + 'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
        + '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>'
        + '<path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>'
        + '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

    // Fills `target` with a balance shown as XXX plus an eye button that reveals or hides it.
    // Balances start hidden every time they are drawn (page load, or after a payment).
    function maskedBalance(target, amount, label) {
        const value = el('span', 'balance-value', MASK);
        const toggle = el('button', 'balance-toggle');
        toggle.type = 'button';

        function setVisible(visible) {
            value.textContent = visible ? money.format(amount) : MASK;
            toggle.innerHTML = visible ? EYE_OFF_ICON : EYE_ICON;
            toggle.setAttribute('aria-label', (visible ? 'Hide ' : 'Show ') + label);
            toggle.setAttribute('aria-pressed', String(visible));
            toggle.title = visible ? 'Hide balance' : 'Show balance';
        }

        toggle.addEventListener('click', () => setVisible(toggle.getAttribute('aria-pressed') !== 'true'));
        setVisible(false);
        target.replaceChildren(value, toggle);
        return target;
    }

    // ---------- Accounts ----------

    function accountCard(account) {
        const card = el('article', 'account-card type-' + account.accountType.toLowerCase());
        card.append(
            el('p', 'account-type', typeLabel(account.accountType) + ' account'),
            el('p', 'account-number', account.accountNumber),
            el('p', 'account-balance-label', 'Available balance'),
            maskedBalance(el('p', 'account-balance'), account.balance,
                'balance of account ' + account.accountNumber)
        );
        const actions = el('div', 'card-actions');
        [['deposit', 'Deposit'], ['withdraw', 'Withdraw'], ['transfer', 'Transfer'], ['history', 'History']]
            .forEach(([action, label]) => {
                const button = el('button', 'card-btn', label);
                button.type = 'button';
                button.dataset.action = action;
                button.dataset.account = account.accountNumber;
                actions.append(button);
            });
        card.append(actions);
        return card;
    }

    function renderAccounts() {
        const total = accounts.reduce((sum, account) => sum + Number(account.balance), 0);
        maskedBalance(document.getElementById('totalBalance'), total, 'total balance');
        document.getElementById('accountCount').textContent =
            accounts.length + (accounts.length === 1 ? ' account' : ' accounts');

        ['overviewAccounts', 'accountsList'].forEach(id => {
            const container = document.getElementById(id);
            container.replaceChildren(...accounts.map(accountCard));
            if (accounts.length === 0) {
                container.append(el('p', 'empty', "You don't have any accounts yet."));
            }
        });

        const datalist = document.getElementById('ownAccounts');
        datalist.replaceChildren(...accounts.map(account => {
            const option = el('option');
            option.value = account.accountNumber;
            option.label = typeLabel(account.accountType);
            return option;
        }));

        ['overviewAccountSelect', 'historyAccountSelect', 'dlgAccount'].forEach(id => {
            const select = document.getElementById(id);
            const previous = select.value;
            select.replaceChildren(...accounts.map(account => {
                const option = el('option', null,
                    typeLabel(account.accountType) + ' - ' + maskAccountNumber(account.accountNumber));
                option.value = account.accountNumber;
                return option;
            }));
            if (accounts.some(account => account.accountNumber === previous)) {
                select.value = previous;
            }
        });
    }

    async function refreshAccounts() {
        const result = await load('/api/me/accounts');
        if (result) {
            accounts = result;
            renderAccounts();
            await Promise.all([loadRecent(), loadHistory()]);
        }
    }

    // ---------- Transactions ----------

    function describe(tx) {
        if (tx.type === 'DEPOSIT') {
            return 'Cash deposit';
        }
        if (tx.type === 'WITHDRAW') {
            return 'Cash withdrawal';
        }
        const other = tx.counterpartyAccount || 'another account';
        return tx.direction === 'DEBIT' ? 'Transfer to ' + other : 'Transfer from ' + other;
    }

    function signedAmount(tx) {
        return (tx.direction === 'DEBIT' ? '- ' : '+ ') + money.format(tx.amount);
    }

    function formatTime(value) {
        return value ? dateFormat.format(new Date(value)) : '-';
    }

    async function loadRecent() {
        const list = document.getElementById('overviewTx');
        const accountNumber = document.getElementById('overviewAccountSelect').value;
        if (!accountNumber) {
            list.replaceChildren(el('li', 'empty', 'Open an account to see activity here.'));
            return;
        }
        const transactions = await load('/api/transactions/account/' + encodeURIComponent(accountNumber)
            + '/mini-statement');
        if (!transactions) {
            return;
        }
        if (transactions.length === 0) {
            list.replaceChildren(el('li', 'empty', 'No transactions yet.'));
            return;
        }
        list.replaceChildren(...transactions.map(tx => {
            const item = el('li', 'tx-item');
            const icon = el('span', 'tx-icon ' + tx.direction.toLowerCase(), tx.direction === 'DEBIT' ? '↑' : '↓');
            icon.setAttribute('aria-hidden', 'true');
            const info = el('div', 'tx-info');
            info.append(el('p', 'tx-title', describe(tx)), el('p', 'tx-date', formatTime(tx.transactionTime)));
            item.append(icon, info, el('span', 'tx-amount ' + tx.direction.toLowerCase(), signedAmount(tx)));
            return item;
        }));
    }

    async function loadHistory() {
        const body = document.getElementById('historyBody');
        const empty = document.getElementById('historyEmpty');
        const accountNumber = document.getElementById('historyAccountSelect').value;
        body.replaceChildren();
        if (!accountNumber) {
            empty.hidden = false;
            return;
        }
        const transactions = await load('/api/transactions/account/' + encodeURIComponent(accountNumber));
        if (!transactions) {
            return;
        }
        empty.hidden = transactions.length > 0;
        transactions.forEach(tx => {
            const row = body.insertRow();
            row.insertCell().textContent = formatTime(tx.transactionTime);
            row.insertCell().textContent = describe(tx);
            row.insertCell().textContent = tx.counterpartyAccount || '-';
            const status = row.insertCell();
            status.append(el('span', 'badge ' + String(tx.status).toLowerCase(), tx.status));
            const amount = row.insertCell();
            amount.className = 'amount tx-amount ' + tx.direction.toLowerCase();
            amount.textContent = signedAmount(tx);
        });
    }

    function safely(task) {
        return () => task().catch(error => Api.showMessage(message, error.message, 'error'));
    }

    document.getElementById('overviewAccountSelect').addEventListener('change', safely(loadRecent));
    document.getElementById('historyAccountSelect').addEventListener('change', safely(loadHistory));

    // ---------- Actions dialog ----------

    const dialog = document.getElementById('actionDialog');
    const dialogForm = document.getElementById('actionForm');
    const dialogMessage = document.getElementById('dialogMessage');
    const dialogSubmit = document.getElementById('dialogSubmit');
    const fields = {
        account: document.getElementById('dlgAccount'),
        to: document.getElementById('dlgTo'),
        amount: document.getElementById('dlgAmount'),
        type: document.getElementById('dlgType')
    };

    const ACTIONS = {
        deposit: { title: 'Deposit money', accountLabel: 'Into account', fields: ['account', 'amount'], button: 'Deposit' },
        withdraw: { title: 'Withdraw money', accountLabel: 'From account', fields: ['account', 'amount'], button: 'Withdraw' },
        transfer: { title: 'Transfer money', accountLabel: 'From account', fields: ['account', 'to', 'amount'], button: 'Send' },
        open: { title: 'Open a new account', fields: ['type'], button: 'Open account' }
    };

    let currentAction = null;

    function openDialog(action, accountNumber) {
        const config = ACTIONS[action];
        if (action !== 'open' && accounts.length === 0) {
            Api.showMessage(message, 'Open an account first.', 'info');
            return;
        }
        currentAction = action;
        document.getElementById('dialogTitle').textContent = config.title;
        document.getElementById('dlgAccountLabel').textContent = config.accountLabel || 'Account';
        dialogSubmit.textContent = config.button;
        dialogForm.querySelectorAll('[data-for]').forEach(field => {
            field.hidden = !config.fields.includes(field.dataset.for);
        });
        fields.to.value = '';
        fields.amount.value = '';
        if (accountNumber) {
            fields.account.value = accountNumber;
        }
        Api.showMessage(dialogMessage, '', 'info');
        dialog.showModal();
        (config.fields.includes('amount') ? fields.amount : fields.type).focus();
    }

    function closeDialog() {
        dialog.close();
        currentAction = null;
    }

    document.getElementById('dialogCancel').addEventListener('click', closeDialog);

    function validateAmount() {
        const value = fields.amount.value.trim();
        if (!/^\d+(\.\d{1,2})?$/.test(value) || Number(value) <= 0) {
            return null;
        }
        return value;
    }

    function request(action) {
        const from = fields.account.value;
        if (action === 'open') {
            return { url: '/api/accounts', body: { accountType: fields.type.value } };
        }
        const amount = validateAmount();
        if (amount === null) {
            throw new Error('Enter an amount greater than 0 with up to 2 decimal places.');
        }
        if (action === 'transfer') {
            const to = fields.to.value.trim();
            if (!to) {
                throw new Error('Enter the account number to send money to.');
            }
            if (to === from) {
                throw new Error('Choose a different account to send money to.');
            }
            return { url: '/api/accounts/transfer', body: { fromAccountNumber: from, toAccountNumber: to, amount } };
        }
        return { url: '/api/accounts/' + encodeURIComponent(from) + '/' + action, body: { amount } };
    }

    // Confirmation that repeats what the customer entered, e.g.
    // "Deposit successful: 250.00 credited to account XXXXXXXX0008."
    function successMessage(action, body) {
        if (action === 'open') {
            return 'Your new account is ready.';
        }
        const amount = money.format(Number(body.amount));
        const account = maskAccountNumber(fields.account.value);
        if (action === 'deposit') {
            return 'Deposit successful: ' + amount + ' credited to account ' + account + '.';
        }
        if (action === 'withdraw') {
            return 'Withdrawal successful: ' + amount + ' debited from account ' + account + '.';
        }
        return 'Transfer successful: ' + amount + ' debited from account ' + maskAccountNumber(body.fromAccountNumber)
            + ' and credited to account ' + maskAccountNumber(body.toAccountNumber) + '.';
    }

    const SUCCESS_DISPLAY_MS = 3000;
    let successTimer = null;

    // Show a success message briefly, then hide it. If another message has replaced it
    // in the meantime (e.g. an error), leave that one alone.
    function flashSuccess(text) {
        clearTimeout(successTimer);
        Api.showMessage(message, text, 'success');
        successTimer = setTimeout(() => {
            if (message.textContent === text) {
                Api.showMessage(message, '', 'success');
            }
        }, SUCCESS_DISPLAY_MS);
    }

    dialogForm.addEventListener('submit', async event => {
        event.preventDefault();
        const action = currentAction;
        let call;
        try {
            call = request(action);
        } catch (error) {
            Api.showMessage(dialogMessage, error.message, 'error');
            return;
        }

        dialogSubmit.disabled = true;
        try {
            const response = await Api.post(call.url, call.body);
            if (response.status === 401) {
                toLogin('expired');
                return;
            }
            if (!response.ok) {
                Api.showMessage(dialogMessage, await Api.errorMessage(response), 'error');
                return;
            }
            const confirmation = successMessage(action, call.body);
            closeDialog();
            flashSuccess(confirmation);
            await refreshAccounts();
        } catch (error) {
            Api.showMessage(dialogMessage, error.message, 'error');
        } finally {
            dialogSubmit.disabled = false;
        }
    });

    // Quick actions and the buttons on each account card
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-action]');
        if (!button) {
            return;
        }
        const { action } = button.dataset;
        if (action === 'history') {
            event.preventDefault();
            // Card buttons name their account; the statement link follows the Recent activity picker
            const account = button.dataset.account || document.getElementById('overviewAccountSelect').value;
            if (!account) {
                Api.showMessage(message, 'Open an account first.', 'info');
                return;
            }
            document.getElementById('historyAccountSelect').value = account;
            navigate('transactions');
            safely(loadHistory)();
            return;
        }
        openDialog(action, button.dataset.account);
    });

    // ---------- Start up ----------

    document.getElementById('logoutButton').addEventListener('click', async () => {
        try {
            await Api.post('/api/auth/logout');
        } finally {
            toLogin('loggedOut');
        }
    });

    document.getElementById('today').textContent =
        new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(new Date());

    showView(viewFromHash());

    async function init() {
        try {
            const customer = await load('/api/me');
            if (!customer) {
                return;
            }
            showProfile(customer);
            await refreshAccounts();
        } catch (error) {
            Api.showMessage(message, error.message, 'error');
        }
    }

    init();
})();
