package com.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a successful deposit or withdrawal recorded against a BankAccount.
 * <p>
 * Each transaction captures the type (DEPOSIT / WITHDRAWAL), the monetary amount,
 * the timestamp of the operation, and the resulting account balance after the
 * transaction was applied. Instances are immutable once created.
 * </p>
 *
 * @see TransactionType
 * @see com.banking.model.BankAccount
 */
public class Transaction {

    /** Supported transaction types. */
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        UPI_TRANSFER_SENT,
        UPI_TRANSFER_RECEIVED
    }

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static int nextId = 1;

    private final int transactionId;
    private final String accountNumber;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String description;
    private final LocalDateTime timestamp;

    /**
     * Creates a new Transaction with default description.
     */
    public Transaction(String accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter) {
        this(accountNumber, type, amount, balanceAfter, "");
    }

    /**
     * Creates a new Transaction with description.
     */
    public Transaction(String accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, String description) {
        this(accountNumber, type, amount, balanceAfter, description, LocalDateTime.now());
    }

    /**
     * Creates a new Transaction with specific timestamp (useful for seeding test histories).
     */
    public Transaction(String accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, String description,
                       LocalDateTime timestamp) {
        this.transactionId = nextId++;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description == null ? "" : description.trim();
        this.timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int getTransactionId() {
        return transactionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // ── Display ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String descStr = description.isEmpty() ? "" : " (" + description + ")";
        return String.format("  %-4d | %-21s | %12s | %12s | %s%s",
                transactionId,
                type,
                amount.toPlainString(),
                balanceAfter.toPlainString(),
                timestamp.format(DISPLAY_FORMAT),
                descStr);
    }
}
