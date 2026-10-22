package com.banking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a customer bank account with a unique account number, holder name,
 * and a non-negative balance.
 * <p>
 * All monetary operations use {@link BigDecimal} with a scale of 2 and
 * {@link RoundingMode#HALF_UP} to guarantee consistent two-decimal-place precision
 * as required by the SRS business rules.
 * </p>
 *
 * @see com.banking.service.BankingService
 */
public class BankAccount {

    /** Number of decimal places for all monetary values. */
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static int nextAccountNumber = 1001;

    private final String accountNumber;
    private final String holderName;
    private final String upiId;
    private String upiPin;
    private BigDecimal balance;

    /**
     * Creates a new account with default UPI PIN ("1234") and generated UPI ID.
     */
    public BankAccount(String holderName, BigDecimal initialDeposit) {
        this(holderName, initialDeposit, "1234", null);
    }

    /**
     * Creates a new account with custom UPI PIN and generated UPI ID.
     */
    public BankAccount(String holderName, BigDecimal initialDeposit, String upiPin) {
        this(holderName, initialDeposit, upiPin, null);
    }

    /**
     * Creates a new account with holder name, initial deposit, UPI PIN, and optional custom UPI ID.
     *
     * @param holderName     the non-empty name of the account holder
     * @param initialDeposit the opening balance (must be > 0)
     * @param upiPin         4 or 6 digit UPI PIN (defaults to 1234 if null/blank)
     * @param customUpiId    custom UPI ID or null to auto-generate
     */
    public BankAccount(String holderName, BigDecimal initialDeposit, String upiPin, String customUpiId) {
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Account holder name cannot be empty.");
        }
        if (initialDeposit == null || initialDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial deposit must be greater than zero.");
        }

        this.accountNumber = "ACC" + nextAccountNumber++;
        this.holderName = holderName.trim();
        this.balance = initialDeposit.setScale(SCALE, ROUNDING);

        String cleanPin = (upiPin == null || upiPin.isBlank()) ? "1234" : upiPin.trim();
        if (!cleanPin.matches("\\d{4,6}")) {
            throw new IllegalArgumentException("UPI PIN must be 4 to 6 digits.");
        }
        this.upiPin = cleanPin;

        if (customUpiId != null && !customUpiId.isBlank()) {
            this.upiId = customUpiId.trim().toLowerCase();
        } else {
            String sanitized = this.holderName.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (sanitized.isEmpty()) sanitized = "user";
            this.upiId = sanitized + "@okbank";
        }
    }

    // ── Monetary Operations ──────────────────────────────────────────────────

    /**
     * Adds the given amount to the account balance.
     *
     * @param amount the deposit amount (must be &gt; 0)
     * @throws IllegalArgumentException if amount &le; 0
     */
    public void deposit(BigDecimal amount) {
        validatePositiveAmount(amount, "Deposit");
        this.balance = this.balance.add(amount.setScale(SCALE, ROUNDING));
    }

    /**
     * Subtracts the given amount from the account balance.
     *
     * @param amount the withdrawal amount (must be &gt; 0 and &le; balance)
     * @throws IllegalArgumentException if amount &le; 0 or exceeds the available balance
     */
    public void withdraw(BigDecimal amount) {
        validatePositiveAmount(amount, "Withdrawal");
        BigDecimal scaledAmount = amount.setScale(SCALE, ROUNDING);
        if (scaledAmount.compareTo(this.balance) > 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Available balance: %s", balance.toPlainString()));
        }
        this.balance = this.balance.subtract(scaledAmount);
    }

    // ── UPI & Security ───────────────────────────────────────────────────────

    public String getUpiId() {
        return upiId;
    }

    public String getUpiPin() {
        return upiPin;
    }

    public void setUpiPin(String newPin) {
        if (newPin == null || !newPin.trim().matches("\\d{4,6}")) {
            throw new IllegalArgumentException("UPI PIN must be 4 to 6 numeric digits.");
        }
        this.upiPin = newPin.trim();
    }

    /**
     * Validates whether the supplied PIN matches the account's UPI PIN.
     *
     * @param pin the PIN to verify
     * @throws IllegalArgumentException if pin does not match
     */
    public void validateUpiPin(String pin) {
        if (pin == null || !this.upiPin.equals(pin.trim())) {
            throw new IllegalArgumentException("Incorrect UPI PIN. Access denied.");
        }
    }

    public boolean isUpiPinValid(String pin) {
        return pin != null && this.upiPin.equals(pin.trim());
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    // ── Display ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                """
                ┌──────────────────────────────────────────────┐
                │  Account Number : %-26s │
                │  Holder Name    : %-26s │
                │  UPI ID         : %-26s │
                │  Balance        : ₹%-25s │
                └──────────────────────────────────────────────┘""",
                accountNumber, holderName, upiId, balance.toPlainString());
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private void validatePositiveAmount(BigDecimal amount, String operation) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(operation + " amount must be greater than zero.");
        }
    }
}
