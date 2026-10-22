package com.banking.app;

import com.banking.model.BankAccount;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.BankingService;
import com.banking.service.BankingService.TransferResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console-based user interface for the Java Banking & UPI System.
 */
public class BankingApp {

    private static final String MENU = """
            
            ╔══════════════════════════════════════════╗
            ║     JAVA BANKING & UPI SYSTEM v2.0       ║
            ╠══════════════════════════════════════════╣
            ║  1. Send Money (UPI Transfer)            ║
            ║  2. View Account / Check Balance         ║
            ║  3. Create Account & UPI ID              ║
            ║  4. Deposit Money                        ║
            ║  5. Withdraw Money                       ║
            ║  6. Transaction History                  ║
            ║  7. List Demo Test Users                 ║
            ║  8. Exit                                 ║
            ╚══════════════════════════════════════════╝
            """;

    private static final String SEPARATOR =
            "──────────────────────────────────────────";

    private final BankingService bankingService;
    private final Scanner scanner;

    public BankingApp(BankingService bankingService, Scanner scanner) {
        this.bankingService = bankingService;
        this.scanner = scanner;
    }

    // ── Main Loop ────────────────────────────────────────────────────────────

    public void run() {
        boolean running = true;

        while (running) {
            System.out.println(MENU);
            System.out.print("  Enter your choice: ");
            String choice = scanner.nextLine().trim();

            System.out.println(SEPARATOR);

            switch (choice) {
                case "1" -> sendMoneyUpi();
                case "2" -> viewAccount();
                case "3" -> createAccount();
                case "4" -> depositMoney();
                case "5" -> withdrawMoney();
                case "6" -> transactionHistory();
                case "7" -> listTestUsers();
                case "8" -> {
                    running = false;
                    exitApplication();
                }
                default -> System.out.println("  ⚠ Invalid choice. Please enter a number between 1 and 8.");
            }
        }
    }

    // ── Menu Actions ─────────────────────────────────────────────────────────

    /**
     * Send money via UPI with mandatory UPI PIN verification.
     */
    private void sendMoneyUpi() {
        System.out.println("  ── Send Money (UPI Transfer) ──\n");

        System.out.print("  Enter Sender Account / UPI ID (e.g. rajesh@okaxis or ACC1001): ");
        String from = scanner.nextLine().trim();
        if (from.isEmpty()) {
            System.out.println("\n  ✖ Sender cannot be empty.");
            return;
        }

        System.out.print("  Enter Beneficiary Account / UPI ID (e.g. priya@okicici or ACC1002): ");
        String to = scanner.nextLine().trim();
        if (to.isEmpty()) {
            System.out.println("\n  ✖ Recipient cannot be empty.");
            return;
        }

        BigDecimal amount = readAmount("  Enter amount to send (₹): ");
        if (amount == null) return;

        System.out.print("  Enter note / remarks (optional): ");
        String remarks = scanner.nextLine().trim();

        System.out.print("  Enter your 4-digit UPI PIN: ");
        String upiPin = scanner.nextLine().trim();

        try {
            TransferResult result = bankingService.transferUpi(from, to, amount, upiPin, remarks);
            System.out.println("\n  ✔ UPI Transfer Successful! Ref #" + result.transactionId());
            System.out.printf("  Paid ₹%s to %s (%s)%n",
                    result.amount().toPlainString(),
                    result.receiver().getHolderName(),
                    result.receiver().getUpiId());
            System.out.printf("  Remaining Balance: ₹%s%n",
                    result.sender().getBalance().toPlainString());
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ Transfer failed: " + e.getMessage());
        }
    }

    /**
     * View account and balance with UPI PIN check.
     */
    private void viewAccount() {
        System.out.println("  ── View Account & Balance ──\n");

        System.out.print("  Enter Account Number or UPI ID: ");
        String identifier = scanner.nextLine().trim();
        if (identifier.isEmpty()) {
            System.out.println("  ✖ Identifier cannot be empty.");
            return;
        }

        System.out.print("  Enter 4-digit UPI PIN: ");
        String upiPin = scanner.nextLine().trim();

        try {
            BankAccount account = bankingService.viewAccountWithPin(identifier, upiPin);
            System.out.println("\n  ✔ PIN verified successfully:");
            System.out.println(account);
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ " + e.getMessage());
        }
    }

    /**
     * Create Account with UPI PIN.
     */
    private void createAccount() {
        System.out.println("  ── Create New Account & UPI Profile ──\n");

        System.out.print("  Enter account holder name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("\n  ✖ Account holder name cannot be empty.");
            return;
        }

        BigDecimal initialDeposit = readAmount("  Enter initial deposit amount (₹): ");
        if (initialDeposit == null) return;

        System.out.print("  Set your 4-digit UPI PIN (Press Enter for default 1234): ");
        String upiPin = scanner.nextLine().trim();
        if (upiPin.isEmpty()) upiPin = "1234";

        try {
            BankAccount account = bankingService.createAccount(name, initialDeposit, upiPin);
            System.out.println("\n  ✔ Account and UPI Profile created successfully!");
            System.out.println(account);
            System.out.println("  Your UPI PIN: " + account.getUpiPin());
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ " + e.getMessage());
        }
    }

    private void depositMoney() {
        System.out.println("  ── Deposit Money ──\n");

        String accountNumber = readAccountNumber();
        BigDecimal amount = readAmount("  Enter deposit amount (₹): ");
        if (amount == null) return;

        try {
            BankAccount account = bankingService.deposit(accountNumber, amount);
            System.out.printf("\n  ✔ Deposit successful! New balance: ₹%s%n",
                    account.getBalance().toPlainString());
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ " + e.getMessage());
        }
    }

    private void withdrawMoney() {
        System.out.println("  ── Withdraw Money ──\n");

        String accountNumber = readAccountNumber();
        BigDecimal amount = readAmount("  Enter withdrawal amount (₹): ");
        if (amount == null) return;

        System.out.print("  Enter your 4-digit UPI PIN to authorize: ");
        String upiPin = scanner.nextLine().trim();

        try {
            BankAccount account = bankingService.withdraw(accountNumber, amount, upiPin);
            System.out.printf("\n  ✔ Withdrawal successful! New balance: ₹%s%n",
                    account.getBalance().toPlainString());
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ " + e.getMessage());
        }
    }

    private void transactionHistory() {
        System.out.println("  ── Transaction History ──\n");

        System.out.print("  Enter Account Number or UPI ID: ");
        String identifier = scanner.nextLine().trim();

        try {
            List<Transaction> transactions = bankingService.getTransactionHistory(identifier);

            if (transactions.isEmpty()) {
                System.out.println("  No transactions found for: " + identifier);
                return;
            }

            System.out.printf("  %-4s | %-21s | %12s | %12s | %s%n",
                    "ID", "Type", "Amount", "Balance", "Timestamp & Remarks");
            System.out.println("  " + "─".repeat(80));

            for (Transaction tx : transactions) {
                System.out.println(tx);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n  ✖ " + e.getMessage());
        }
    }

    private void listTestUsers() {
        System.out.println("  ── Active Test Users (PIN: 1234 for all) ──\n");
        List<BankAccount> users = bankingService.getAllAccounts();
        for (BankAccount acc : users) {
            System.out.printf("  • %-16s | Acc: %-8s | UPI: %-18s | Bal: ₹%s%n",
                    acc.getHolderName(), acc.getAccountNumber(), acc.getUpiId(), acc.getBalance().toPlainString());
        }
    }

    private void exitApplication() {
        System.out.println("""
                
                  Thank you for using Java Banking & UPI System!
                  Goodbye.
                """);
    }

    private String readAccountNumber() {
        System.out.print("  Enter account number or UPI ID: ");
        return scanner.nextLine().trim();
    }

    private BigDecimal readAmount(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();

        try {
            BigDecimal amount = new BigDecimal(input);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("\n  ✖ Amount must be greater than zero.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            System.out.println("\n  ✖ Invalid amount. Please enter a valid number.");
            return null;
        }
    }

    // ── Entry Point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        AccountRepository accountRepo = new AccountRepository();
        TransactionRepository transactionRepo = new TransactionRepository();
        BankingService service = new BankingService(accountRepo, transactionRepo);

        // Pre-populate test users
        service.seedTestUsers();

        BankingApp app = new BankingApp(service, new Scanner(System.in));
        app.run();
    }
}
