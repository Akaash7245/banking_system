package com.banking.repository;

import com.banking.model.BankAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory repository for {@link BankAccount} entities.
 * <p>
 * Stores accounts in a {@link LinkedHashMap} keyed by account number to
 * guarantee O(1) lookups and insertion-order iteration. This implementation
 * will be replaced by a Hibernate-backed repository in the persistence phase.
 * </p>
 */
public class AccountRepository {

    /** Accounts keyed by account number. */
    private final Map<String, BankAccount> accounts = new LinkedHashMap<>();

    /**
     * Persists a new account. The account number must not already exist.
     *
     * @param account the account to save
     * @throws IllegalArgumentException if an account with the same number already exists
     */
    public void save(BankAccount account) {
        if (accounts.containsKey(account.getAccountNumber())) {
            throw new IllegalArgumentException(
                    "Account with number " + account.getAccountNumber() + " already exists.");
        }
        accounts.put(account.getAccountNumber(), account);
    }

    /**
     * Finds an account by its unique account number.
     *
     * @param accountNumber the account number to search for
     * @return an {@link Optional} containing the account, or empty if not found
     */
    public Optional<BankAccount> findByAccountNumber(String accountNumber) {
        if (accountNumber == null) return Optional.empty();
        return Optional.ofNullable(accounts.get(accountNumber.trim().toUpperCase()));
    }

    /**
     * Finds an account by its UPI ID.
     *
     * @param upiId the UPI ID (e.g. rajesh@okaxis)
     * @return an {@link Optional} containing the account, or empty if not found
     */
    public Optional<BankAccount> findByUpiId(String upiId) {
        if (upiId == null) return Optional.empty();
        String normalized = upiId.trim().toLowerCase();
        return accounts.values().stream()
                .filter(acc -> acc.getUpiId().equalsIgnoreCase(normalized))
                .findFirst();
    }

    /**
     * Finds an account by either Account Number (e.g. ACC1001) or UPI ID (e.g. priya@okicici).
     *
     * @param identifier account number or UPI ID
     * @return an {@link Optional} containing the account, or empty if not found
     */
    public Optional<BankAccount> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String trimmed = identifier.trim();
        Optional<BankAccount> byAcc = findByAccountNumber(trimmed);
        if (byAcc.isPresent()) return byAcc;
        return findByUpiId(trimmed);
    }

    /**
     * Returns an unmodifiable list of all stored accounts in insertion order.
     *
     * @return all accounts
     */
    public List<BankAccount> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(accounts.values()));
    }

    /**
     * Checks whether an account with the given number exists.
     *
     * @param accountNumber the account number
     * @return {@code true} if the account exists
     */
    public boolean exists(String accountNumber) {
        return accounts.containsKey(accountNumber);
    }

    /**
     * Returns the total number of accounts in the repository.
     *
     * @return account count
     */
    public int count() {
        return accounts.size();
    }
}
