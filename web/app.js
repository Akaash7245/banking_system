/* ═══════════════════════════════════════════════════════════════════════
   Java Banking & UPI System — Frontend Logic
   ═══════════════════════════════════════════════════════════════════════ */

const API = '';
const nav = document.getElementById('nav');
const panels = document.querySelectorAll('.panel');
const toastEl = document.getElementById('toast');

let cachedUsers = [];

// ── Navigation ───────────────────────────────────────────────────────

nav.addEventListener('click', (e) => {
    const btn = e.target.closest('.sidebar-btn');
    if (!btn) return;

    nav.querySelectorAll('.sidebar-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    const panelId = 'panel-' + btn.dataset.panel;
    panels.forEach(p => p.classList.remove('active'));
    const target = document.getElementById(panelId);
    if (target) target.classList.add('active');

    if (btn.dataset.panel === 'users') {
        renderTestUsersTable();
    }
});

function switchToPanel(panelName) {
    const btn = document.querySelector(`.sidebar-btn[data-panel="${panelName}"]`);
    if (btn) btn.click();
}

// ── Toast Notifications ──────────────────────────────────────────────

let toastTimer = null;

function showToast(message, type = 'success') {
    clearTimeout(toastTimer);
    toastEl.textContent = message;
    toastEl.className = 'toast toast-' + type + ' show';
    toastTimer = setTimeout(() => toastEl.classList.remove('show'), 3800);
}

// ── PIN Visibility Toggle ────────────────────────────────────────────

function togglePinVisibility(inputId) {
    const el = document.getElementById(inputId);
    if (!el) return;
    el.type = el.type === 'password' ? 'text' : 'password';
}
window.togglePinVisibility = togglePinVisibility;

// ── Fetch & Render Test Accounts ─────────────────────────────────────

async function loadTestUsers() {
    try {
        const res = await fetch(API + '/api/test-users');
        const data = await res.json();
        if (data.success && data.users) {
            cachedUsers = data.users;
            renderTestChips(data.users);
            populateDropdowns(data.users);
            renderTestUsersTable();
        }
    } catch (err) {
        console.error('Failed to load test users:', err);
    }
}

function renderTestChips(users) {
    const container = document.getElementById('test-chips');
    if (!container) return;

    if (users.length === 0) {
        container.innerHTML = '<span>No test accounts available</span>';
        return;
    }

    container.innerHTML = users.map(u => `
        <div class="test-chip" onclick="quickFillUser('${u.accountNumber}', '${u.upiId}')" title="Click to fill in active form">
            <span class="chip-name">${escapeHtml(u.holderName)}</span>
            <span class="chip-upi">${escapeHtml(u.upiId)}</span>
            <span class="chip-bal">₹${formatCurrency(u.balance)}</span>
        </div>
    `).join('');
}

function populateDropdowns(users) {
    const selects = ['select-sender', 'select-receiver', 'select-view-user'];
    selects.forEach(id => {
        const sel = document.getElementById(id);
        if (!sel) return;
        const currentVal = sel.value;
        sel.innerHTML = '<option value="">Quick Select...</option>' +
            users.map(u => `<option value="${escapeHtml(u.upiId)}">${escapeHtml(u.holderName)} (${escapeHtml(u.upiId)})</option>`).join('');
        sel.value = currentVal;
    });
}

function renderTestUsersTable() {
    const tbody = document.getElementById('tbody-test-users');
    if (!tbody) return;

    if (cachedUsers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">No accounts found.</td></tr>';
        return;
    }

    tbody.innerHTML = cachedUsers.map(u => `
        <tr>
            <td><strong>${escapeHtml(u.accountNumber)}</strong></td>
            <td>${escapeHtml(u.holderName)}</td>
            <td><code>${escapeHtml(u.upiId)}</code></td>
            <td><span class="badge-pin-hint">${escapeHtml(u.defaultPin || '1234')}</span></td>
            <td><strong>₹${formatCurrency(u.balance)}</strong></td>
            <td>
                <button class="btn btn-primary btn-sm" onclick="sendMoneyFrom('${u.upiId}')">Send Money</button>
                <button class="btn btn-sm" style="background:#e2e8f0; color:#1e293b; margin-left:4px;" onclick="checkBalanceFor('${u.upiId}')">Check Balance</button>
            </td>
        </tr>
    `).join('');
}

function quickFillUser(accNum, upiId) {
    // Determine active panel and populate sensible fields
    const activePanel = document.querySelector('.panel.active');
    if (!activePanel) return;

    if (activePanel.id === 'panel-send') {
        const fromInput = document.getElementById('send-from');
        const toInput = document.getElementById('send-to');
        if (!fromInput.value) {
            fromInput.value = upiId;
            showToast(`Selected ${upiId} as Sender`, 'success');
        } else if (!toInput.value) {
            toInput.value = upiId;
            showToast(`Selected ${upiId} as Beneficiary`, 'success');
        } else {
            fromInput.value = upiId;
            showToast(`Updated Sender to ${upiId}`, 'success');
        }
    } else if (activePanel.id === 'panel-view') {
        document.getElementById('view-identifier').value = upiId;
        showToast(`Selected ${upiId} for balance check`, 'success');
    } else if (activePanel.id === 'panel-deposit') {
        document.getElementById('deposit-number').value = upiId;
    } else if (activePanel.id === 'panel-withdraw') {
        document.getElementById('withdraw-number').value = upiId;
    } else if (activePanel.id === 'panel-history') {
        document.getElementById('history-number').value = upiId;
    }
}
window.quickFillUser = quickFillUser;

function sendMoneyFrom(upiId) {
    switchToPanel('send');
    document.getElementById('send-from').value = upiId;
    document.getElementById('send-pin').value = '1234';
    showToast(`Ready to send money from ${upiId}`, 'success');
}
window.sendMoneyFrom = sendMoneyFrom;

function checkBalanceFor(upiId) {
    switchToPanel('view');
    document.getElementById('view-identifier').value = upiId;
    document.getElementById('view-pin').value = '1234';
    document.getElementById('btn-view').click();
}
window.checkBalanceFor = checkBalanceFor;

// Quick Select Dropdown Change Handlers
document.getElementById('select-sender')?.addEventListener('change', (e) => {
    if (e.target.value) document.getElementById('send-from').value = e.target.value;
});

document.getElementById('select-receiver')?.addEventListener('change', (e) => {
    if (e.target.value) document.getElementById('send-to').value = e.target.value;
});

document.getElementById('select-view-user')?.addEventListener('change', (e) => {
    if (e.target.value) document.getElementById('view-identifier').value = e.target.value;
});

// ── 1. Send Money (UPI) Form ─────────────────────────────────────────

document.getElementById('form-send').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-send');
    resultEl.innerHTML = '';

    const from = document.getElementById('send-from').value.trim();
    const to = document.getElementById('send-to').value.trim();
    const amount = document.getElementById('send-amount').value.trim();
    const upiPin = document.getElementById('send-pin').value.trim();
    const remarks = document.getElementById('send-remarks').value.trim();

    if (!from || !to || !amount || !upiPin) {
        showToast('Please fill all mandatory fields including UPI PIN', 'error');
        return;
    }

    try {
        const res = await fetch(API + '/api/upi/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ from, to, amount, upiPin, remarks })
        });
        const data = await res.json();

        if (data.success) {
            showToast('UPI Payment Successful!', 'success');
            resultEl.innerHTML = `
                <div class="upi-receipt">
                    <div class="receipt-icon">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
                    </div>
                    <div class="receipt-title">Payment Successful!</div>
                    <div class="receipt-amount">₹${formatCurrency(data.amount)}</div>
                    <p style="color:#64748b; font-size:0.85rem;">Authorized via Secure UPI PIN</p>

                    <div class="receipt-grid">
                        <div class="receipt-item">
                            <label>Sent From</label>
                            <value>${escapeHtml(data.sender.holderName)}</value>
                            <span style="font-size:0.75rem; color:#64748b;">${escapeHtml(data.sender.upiId)}</span>
                        </div>
                        <div class="receipt-item">
                            <label>Paid To</label>
                            <value>${escapeHtml(data.receiver.holderName)}</value>
                            <span style="font-size:0.75rem; color:#64748b;">${escapeHtml(data.receiver.upiId)}</span>
                        </div>
                        <div class="receipt-item">
                            <label>UPI Reference (UTR)</label>
                            <value style="font-family:var(--font-mono);">UPI${data.transactionId}90482</value>
                        </div>
                        <div class="receipt-item">
                            <label>Sender Updated Balance</label>
                            <value style="color:var(--upi-green);">₹${formatCurrency(data.sender.balance)}</value>
                        </div>
                        ${data.remarks ? `
                        <div class="receipt-item" style="grid-column: 1 / -1;">
                            <label>Note / Remarks</label>
                            <value>${escapeHtml(data.remarks)}</value>
                        </div>` : ''}
                    </div>

                    <button class="btn btn-primary" onclick="resetSendForm()">Make Another Transfer</button>
                </div>
            `;
            // Refresh test users list to reflect new balances
            loadTestUsers();
        } else {
            showToast(data.error || 'Payment failed', 'error');
            resultEl.innerHTML = `<div class="result-card result-error"><strong>Payment Failed:</strong> ${escapeHtml(data.error)}</div>`;
        }
    } catch (err) {
        showToast('Network error during transfer', 'error');
        resultEl.innerHTML = `<div class="result-card result-error">Network error. Please try again.</div>`;
    }
});

function resetSendForm() {
    document.getElementById('send-amount').value = '';
    document.getElementById('send-remarks').value = '';
    document.getElementById('result-send').innerHTML = '';
}
window.resetSendForm = resetSendForm;

// ── 2. Check Balance (UPI PIN Protected) ──────────────────────────────

document.getElementById('form-view').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-view');
    resultEl.innerHTML = '';

    const identifier = document.getElementById('view-identifier').value.trim();
    const upiPin = document.getElementById('view-pin').value.trim();

    if (!identifier || !upiPin) {
        showToast('Account Identifier and UPI PIN are required', 'error');
        return;
    }

    try {
        const res = await fetch(API + '/api/accounts/view', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ identifier, upiPin })
        });
        const data = await res.json();

        if (data.success && data.account) {
            showToast('UPI PIN Verified!', 'success');
            const acc = data.account;
            resultEl.innerHTML = `
                <div class="bank-passbook-card">
                    <div class="card-top">
                        <div class="card-bank-name">National Payments Corporation • UPI</div>
                        <div class="card-network-badge">ACTIVE ACCOUNT</div>
                    </div>
                    <div class="card-middle">
                        <div class="card-bal-label">Available Account Balance</div>
                        <div class="card-balance-big">₹${formatCurrency(acc.balance)}</div>
                    </div>
                    <div class="card-bottom">
                        <div>
                            <div class="card-holder-name">${escapeHtml(acc.holderName)}</div>
                            <div class="card-upi-id">${escapeHtml(acc.upiId)}</div>
                        </div>
                        <div style="text-align:right;">
                            <div style="font-size:0.7rem; color:#94a3b8; text-transform:uppercase;">Account No.</div>
                            <div class="card-acc-num">${escapeHtml(acc.accountNumber)}</div>
                        </div>
                    </div>
                </div>
            `;
        } else {
            showToast(data.error || 'Access Denied', 'error');
            resultEl.innerHTML = `
                <div class="result-card result-error">
                    <strong>Authentication Failed:</strong> ${escapeHtml(data.error || 'Incorrect UPI PIN')}
                    <p style="font-size:0.8rem; margin-top:4px; color:#b91c1c;">Tip: The default PIN for all test accounts is <strong>1234</strong>.</p>
                </div>
            `;
        }
    } catch (err) {
        showToast('Network error', 'error');
        resultEl.innerHTML = `<div class="result-card result-error">Network error. Please try again.</div>`;
    }
});

// ── 3. Create Account ─────────────────────────────────────────────────

document.getElementById('form-create').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-create');
    resultEl.innerHTML = '';

    const name = document.getElementById('create-name').value.trim();
    const deposit = document.getElementById('create-deposit').value.trim();
    const upiPin = document.getElementById('create-pin').value.trim();
    const upiId = document.getElementById('create-upi').value.trim();

    try {
        const res = await fetch(API + '/api/accounts/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, deposit, upiPin, upiId })
        });
        const data = await res.json();

        if (data.success && data.account) {
            showToast('Account & UPI Profile Created!', 'success');
            const acc = data.account;
            resultEl.innerHTML = `
                <div class="result-card result-success">
                    <h3 style="color:var(--navy-900); margin-bottom:8px;">✔ Account Created Successfully</h3>
                    <p style="font-size:0.85rem; color:#475569; margin-bottom:12px;">Your account is ready for instant UPI payments.</p>
                    <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; font-size:0.85rem;">
                        <div><strong>Account Number:</strong> <code>${escapeHtml(acc.accountNumber)}</code></div>
                        <div><strong>Account Holder:</strong> ${escapeHtml(acc.holderName)}</div>
                        <div><strong>Assigned UPI ID:</strong> <code>${escapeHtml(acc.upiId)}</code></div>
                        <div><strong>UPI PIN:</strong> <code>${escapeHtml(upiPin || '1234')}</code></div>
                        <div><strong>Opening Balance:</strong> ₹${formatCurrency(acc.balance)}</div>
                    </div>
                </div>
            `;
            document.getElementById('form-create').reset();
            document.getElementById('create-pin').value = '1234';
            loadTestUsers();
        } else {
            showToast(data.error || 'Failed to create account', 'error');
            resultEl.innerHTML = `<div class="result-card result-error">${escapeHtml(data.error)}</div>`;
        }
    } catch (err) {
        showToast('Network error', 'error');
    }
});

// ── 4. Deposit Funds ──────────────────────────────────────────────────

document.getElementById('form-deposit').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-deposit');
    resultEl.innerHTML = '';

    const accountNumber = document.getElementById('deposit-number').value.trim();
    const amount = document.getElementById('deposit-amount').value.trim();

    try {
        const res = await fetch(API + '/api/accounts/deposit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountNumber, amount })
        });
        const data = await res.json();

        if (data.success && data.account) {
            showToast('Deposit Successful!', 'success');
            const acc = data.account;
            resultEl.innerHTML = `
                <div class="result-card result-success">
                    <h3 style="color:var(--navy-900); margin-bottom:4px;">✔ Deposit Successful</h3>
                    <p style="font-size:0.85rem;">₹${formatCurrency(amount)} credited to ${escapeHtml(acc.holderName)} (${escapeHtml(acc.accountNumber)}).</p>
                    <p style="font-size:0.95rem; font-weight:700; color:var(--upi-green); margin-top:6px;">New Balance: ₹${formatCurrency(acc.balance)}</p>
                </div>
            `;
            document.getElementById('deposit-amount').value = '';
            loadTestUsers();
        } else {
            showToast(data.error || 'Deposit failed', 'error');
            resultEl.innerHTML = `<div class="result-card result-error">${escapeHtml(data.error)}</div>`;
        }
    } catch (err) {
        showToast('Network error', 'error');
    }
});

// ── 5. Withdraw Funds (PIN Protected) ─────────────────────────────────

document.getElementById('form-withdraw').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-withdraw');
    resultEl.innerHTML = '';

    const accountNumber = document.getElementById('withdraw-number').value.trim();
    const amount = document.getElementById('withdraw-amount').value.trim();
    const upiPin = document.getElementById('withdraw-pin').value.trim();

    try {
        const res = await fetch(API + '/api/accounts/withdraw', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountNumber, amount, upiPin })
        });
        const data = await res.json();

        if (data.success && data.account) {
            showToast('Withdrawal Successful!', 'success');
            const acc = data.account;
            resultEl.innerHTML = `
                <div class="result-card result-success">
                    <h3 style="color:var(--navy-900); margin-bottom:4px;">✔ Withdrawal Authorized & Complete</h3>
                    <p style="font-size:0.85rem;">₹${formatCurrency(amount)} debited from ${escapeHtml(acc.holderName)} (${escapeHtml(acc.accountNumber)}).</p>
                    <p style="font-size:0.95rem; font-weight:700; color:var(--navy-900); margin-top:6px;">Updated Balance: ₹${formatCurrency(acc.balance)}</p>
                </div>
            `;
            document.getElementById('withdraw-amount').value = '';
            loadTestUsers();
        } else {
            showToast(data.error || 'Withdrawal failed', 'error');
            resultEl.innerHTML = `<div class="result-card result-error">${escapeHtml(data.error)}</div>`;
        }
    } catch (err) {
        showToast('Network error', 'error');
    }
});

// ── 6. Transaction History ────────────────────────────────────────────

document.getElementById('form-history').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('result-history');
    resultEl.innerHTML = '';

    const number = document.getElementById('history-number').value.trim();
    const pin = document.getElementById('history-pin').value.trim();

    if (!number) {
        showToast('Account Number or UPI ID required', 'error');
        return;
    }

    try {
        const res = await fetch(`${API}/api/accounts/history?number=${encodeURIComponent(number)}&pin=${encodeURIComponent(pin)}`);
        const data = await res.json();

        if (data.success && data.transactions) {
            if (data.transactions.length === 0) {
                resultEl.innerHTML = `<div class="result-card">No transactions found for ${escapeHtml(number)}.</div>`;
                return;
            }

            const rows = data.transactions.map(tx => {
                let badgeClass = 'badge-deposit';
                let sign = '';
                if (tx.type === 'UPI_TRANSFER_SENT' || tx.type === 'WITHDRAWAL') {
                    badgeClass = 'badge-debit';
                    sign = '-';
                } else if (tx.type === 'UPI_TRANSFER_RECEIVED' || tx.type === 'DEPOSIT') {
                    badgeClass = 'badge-credit';
                    sign = '+';
                }

                return `
                    <tr>
                        <td>#${tx.id}</td>
                        <td><span class="badge-tx ${badgeClass}">${escapeHtml(tx.type)}</span></td>
                        <td><strong>${sign}₹${formatCurrency(tx.amount)}</strong></td>
                        <td>₹${formatCurrency(tx.balanceAfter)}</td>
                        <td>${escapeHtml(tx.description || '-')}</td>
                        <td style="color:#64748b; font-size:0.75rem;">${escapeHtml(tx.timestamp)}</td>
                    </tr>
                `;
            }).join('');

            resultEl.innerHTML = `
                <div class="card" style="margin-top:16px;">
                    <div style="margin-bottom:12px; font-weight:600; color:var(--navy-900);">Statement for ${escapeHtml(data.identifier)}</div>
                    <div class="table-wrap">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Ref #</th>
                                    <th>Type</th>
                                    <th>Amount</th>
                                    <th>Balance After</th>
                                    <th>Remarks / Note</th>
                                    <th>Date & Time</th>
                                </tr>
                            </thead>
                            <tbody>${rows}</tbody>
                        </table>
                    </div>
                </div>
            `;
        } else {
            showToast(data.error || 'Failed to fetch statement', 'error');
            resultEl.innerHTML = `<div class="result-card result-error">${escapeHtml(data.error)}</div>`;
        }
    } catch (err) {
        showToast('Network error', 'error');
    }
});

// ── Helpers ──────────────────────────────────────────────────────────

function formatCurrency(val) {
    const num = parseFloat(val);
    if (isNaN(num)) return val;
    return num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ── Init ─────────────────────────────────────────────────────────────
loadTestUsers();
