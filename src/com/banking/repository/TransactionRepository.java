package com.banking.repository;

import com.banking.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory repository for {@link Transaction} entities.
 * <p>
 * Transactions are stored in a {@link LinkedHashMap} keyed by account number,
 * where each key maps to an ordered list of transactions for that account.
 * </p>
 */
public class TransactionRepository {

    /** Transactions grouped by account number, each list in chronological order. */
    private final Map<String, List<Transaction>> transactionsByAccount = new LinkedHashMap<>();

    /**
     * Records a transaction for the associated account.
     *
     * @param transaction the transaction to save
     */
    public void save(Transaction transaction) {
        transactionsByAccount
                .computeIfAbsent(transaction.getAccountNumber(), k -> new ArrayList<>())
                .add(transaction);
    }

    /**
     * Retrieves all transactions for a given account in chronological order.
     *
     * @param accountNumber the account number to look up
     * @return an unmodifiable list of transactions, or an empty list if none exist
     */
    public List<Transaction> findByAccountNumber(String accountNumber) {
        List<Transaction> transactions = transactionsByAccount.get(accountNumber);
        if (transactions == null || transactions.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }

    /**
     * Returns the total number of transactions across all accounts.
     *
     * @return total transaction count
     */
    public int count() {
        return transactionsByAccount.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}
