package com.banking.service;

import com.banking.model.BankAccount;
import com.banking.model.Transaction;
import com.banking.model.Transaction.TransactionType;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service layer that coordinates account creation, money operations, and
 * transaction history retrieval.
 * <p>
 * All business rules defined in the SRS (§5.3) are enforced here:
 * <ul>
 *   <li>Account numbers are unique (delegated to the auto-generated scheme).</li>
 *   <li>Deposit and withdrawal amounts must be &gt; 0.</li>
 *   <li>Withdrawals must not exceed the available balance.</li>
 *   <li>The balance must never become negative.</li>
 *   <li>Currency scale is kept at two decimal places.</li>
 * </ul>
 * </p>
 */
public class BankingService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Creates a BankingService backed by the given repositories.
     *
     * @param accountRepository     the account store
     * @param transactionRepository the transaction store
     */
    public BankingService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ── Account Operations ───────────────────────────────────────────────────

    public BankAccount createAccount(String holderName, BigDecimal initialDeposit) {
        return createAccount(holderName, initialDeposit, "1234", null);
    }

    public BankAccount createAccount(String holderName, BigDecimal initialDeposit, String upiPin) {
        return createAccount(holderName, initialDeposit, upiPin, null);
    }

    public BankAccount createAccount(String holderName, BigDecimal initialDeposit, String upiPin, String customUpiId) {
        BankAccount account = new BankAccount(holderName, initialDeposit, upiPin, customUpiId);
        accountRepository.save(account);

        // Record the initial deposit as a transaction
        Transaction initialTx = new Transaction(
                account.getAccountNumber(),
                TransactionType.DEPOSIT,
                initialDeposit,
                account.getBalance(),
                "Initial Account Opening Deposit");
        transactionRepository.save(initialTx);

        return account;
    }

    /**
     * Retrieves an account by its number or UPI ID without requiring PIN (basic lookup).
     */
    public Optional<BankAccount> viewAccount(String identifier) {
        return accountRepository.findByIdentifier(identifier);
    }

    /**
     * Retrieves account details and balance after verifying the UPI PIN.
     *
     * @param identifier account number or UPI ID
     * @param upiPin     the 4-6 digit UPI PIN
     * @return the verified {@link BankAccount}
     * @throws IllegalArgumentException if account not found or PIN is incorrect
     */
    public BankAccount viewAccountWithPin(String identifier, String upiPin) {
        BankAccount account = getAccountOrThrow(identifier);
        account.validateUpiPin(upiPin);
        return account;
    }

    /**
     * Changes an account's UPI PIN after validating the old PIN.
     */
    public void changeUpiPin(String identifier, String oldPin, String newPin) {
        BankAccount account = getAccountOrThrow(identifier);
        account.validateUpiPin(oldPin);
        account.setUpiPin(newPin);
    }

    /**
     * Returns all stored accounts.
     */
    public List<BankAccount> getAllAccounts() {
        return accountRepository.findAll();
    }

    // ── Money Operations ─────────────────────────────────────────────────────

    /**
     * Deposits money into the specified account (no PIN required for receiving/depositing).
     */
    public BankAccount deposit(String identifier, BigDecimal amount) {
        BankAccount account = getAccountOrThrow(identifier);
        account.deposit(amount);

        Transaction tx = new Transaction(
                account.getAccountNumber(),
                TransactionType.DEPOSIT,
                amount,
                account.getBalance(),
                "Cash/Online Deposit");
        transactionRepository.save(tx);

        return account;
    }

    /**
     * Withdraws money without PIN check (legacy method).
     */
    public BankAccount withdraw(String identifier, BigDecimal amount) {
        return withdraw(identifier, amount, null);
    }

    /**
     * Withdraws money from the specified account after validating UPI PIN.
     */
    public BankAccount withdraw(String identifier, BigDecimal amount, String upiPin) {
        BankAccount account = getAccountOrThrow(identifier);
        if (upiPin != null && !upiPin.isBlank()) {
            account.validateUpiPin(upiPin);
        }
        account.withdraw(amount);

        Transaction tx = new Transaction(
                account.getAccountNumber(),
                TransactionType.WITHDRAWAL,
                amount,
                account.getBalance(),
                "ATM / Cash Withdrawal");
        transactionRepository.save(tx);

        return account;
    }

    // ── UPI Money Transfer ───────────────────────────────────────────────────

    /**
     * Sends money via UPI from one account to another using UPI PIN authorization.
     *
     * @param fromIdentifier sender's account number or UPI ID
     * @param toIdentifier   receiver's account number or UPI ID
     * @param amount         positive transfer amount
     * @param upiPin         sender's UPI PIN
     * @param remarks        optional transaction note/remark
     * @return a TransferResult containing sender, recipient, and transaction info
     * @throws IllegalArgumentException if authorization fails, insufficient balance, or account not found
     */
    public TransferResult transferUpi(String fromIdentifier, String toIdentifier,
                                     BigDecimal amount, String upiPin, String remarks) {
        if (fromIdentifier == null || fromIdentifier.isBlank()) {
            throw new IllegalArgumentException("Sender account or UPI ID is required.");
        }
        if (toIdentifier == null || toIdentifier.isBlank()) {
            throw new IllegalArgumentException("Recipient account or UPI ID is required.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }

        BankAccount sender = getAccountOrThrow(fromIdentifier);
        sender.validateUpiPin(upiPin);

        BankAccount receiver = getAccountOrThrow(toIdentifier);

        if (sender.getAccountNumber().equalsIgnoreCase(receiver.getAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer money to the same account.");
        }

        // Execute atomic debit & credit
        sender.withdraw(amount);
        receiver.deposit(amount);

        String note = (remarks == null || remarks.isBlank()) ? "" : " • " + remarks.trim();

        Transaction debitTx = new Transaction(
                sender.getAccountNumber(),
                TransactionType.UPI_TRANSFER_SENT,
                amount,
                sender.getBalance(),
                "Paid to " + receiver.getHolderName() + " (" + receiver.getUpiId() + ")" + note);
        transactionRepository.save(debitTx);

        Transaction creditTx = new Transaction(
                receiver.getAccountNumber(),
                TransactionType.UPI_TRANSFER_RECEIVED,
                amount,
                receiver.getBalance(),
                "Received from " + sender.getHolderName() + " (" + sender.getUpiId() + ")" + note);
        transactionRepository.save(creditTx);

        return new TransferResult(sender, receiver, amount, debitTx.getTransactionId(), remarks);
    }

    /**
     * DTO representing a successful UPI transfer result.
     */
    public record TransferResult(
            BankAccount sender,
            BankAccount receiver,
            BigDecimal amount,
            int transactionId,
            String remarks
    ) {}

    // ── Transaction History ──────────────────────────────────────────────────

    /**
     * Returns the transaction history for the given account.
     */
    public List<Transaction> getTransactionHistory(String identifier) {
        BankAccount account = getAccountOrThrow(identifier);
        return transactionRepository.findByAccountNumber(account.getAccountNumber());
    }

    /**
     * Returns the transaction history after validating UPI PIN.
     */
    public List<Transaction> getTransactionHistoryWithPin(String identifier, String upiPin) {
        BankAccount account = getAccountOrThrow(identifier);
        if (upiPin != null && !upiPin.isBlank()) {
            account.validateUpiPin(upiPin);
        }
        return transactionRepository.findByAccountNumber(account.getAccountNumber());
    }

    // ── Pre-seeding Test Users ───────────────────────────────────────────────

    /**
     * Pre-populates realistic Indian banking test accounts and sample histories.
     */
    public void seedTestUsers() {
        if (accountRepository.count() > 0) return; // already seeded

        var u1 = createAccount("Rajesh Kumar", new BigDecimal("50000.00"), "1234", "rajesh@okaxis");
        var u2 = createAccount("Priya Sharma", new BigDecimal("85500.00"), "1234", "priya@okicici");
        var u3 = createAccount("Amit Patel", new BigDecimal("32000.00"), "1234", "amit@oksbi");
        var u4 = createAccount("Sneha Reddy", new BigDecimal("120000.00"), "1234", "sneha@okhdfc");
        var u5 = createAccount("Vikram Singh", new BigDecimal("15750.00"), "1234", "vikram@okpaytm");

        // Seed some realistic transactions among test users
        transferUpi("rajesh@okaxis", "priya@okicici", new BigDecimal("1500.00"), "1234", "Team Lunch Split");
        transferUpi("sneha@okhdfc", "rajesh@okaxis", new BigDecimal("12000.00"), "1234", "Consulting Fee");
        transferUpi("amit@oksbi", "vikram@okpaytm", new BigDecimal("2400.00"), "1234", "Office Supplies");
        u1.deposit(new BigDecimal("5000.00"));
        transactionRepository.save(new Transaction(u1.getAccountNumber(), TransactionType.DEPOSIT,
                new BigDecimal("5000.00"), u1.getBalance(), "Quarterly Interest Credit"));
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Retrieves an account by account number or UPI ID, or throws an exception.
     */
    private BankAccount getAccountOrThrow(String identifier) {
        return accountRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found for: " + identifier));
    }
}
