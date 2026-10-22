package com.banking.web;

import com.banking.model.BankAccount;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.BankingService;
import com.banking.service.BankingService.TransferResult;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP server exposing the Banking & UPI System as a REST API
 * and serving the web frontend.
 */
public class WebServer {

    private static final int PORT = 3000;
    private static final DateTimeFormatter JSON_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BankingService bankingService;

    public WebServer(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    /** Starts the HTTP server on port 3000. */
    public void start() throws IOException {
        // Pre-populate test users
        bankingService.seedTestUsers();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API endpoints
        server.createContext("/api/test-users", new TestUsersHandler());
        server.createContext("/api/accounts/create", new CreateAccountHandler());
        server.createContext("/api/accounts/view", new ViewAccountHandler());
        server.createContext("/api/accounts/deposit", new DepositHandler());
        server.createContext("/api/accounts/withdraw", new WithdrawHandler());
        server.createContext("/api/accounts/history", new TransactionHistoryHandler());
        server.createContext("/api/accounts/change-pin", new ChangePinHandler());
        server.createContext("/api/upi/send", new UpiSendHandler());

        // Static file serving (frontend)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Java Banking & UPI System — Web Server");
        System.out.println("  Running at: http://localhost:" + PORT);
        System.out.println("  Pre-seeded with 5 active test users (Default PIN: 1234)");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    // ── API Handlers ─────────────────────────────────────────────────────────

    /** GET /api/test-users — Lists all pre-seeded and registered test accounts */
    private class TestUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            List<BankAccount> accounts = bankingService.getAllAccounts();
            String accListJson = accounts.stream()
                    .map(acc -> String.format(
                            """
                            {"accountNumber":"%s","holderName":"%s","upiId":"%s","balance":"%s","defaultPin":"1234"}""",
                            escapeJson(acc.getAccountNumber()),
                            escapeJson(acc.getHolderName()),
                            escapeJson(acc.getUpiId()),
                            acc.getBalance().toPlainString()))
                    .collect(Collectors.joining(","));

            sendJson(exchange, 200, "{\"success\":true,\"users\":[" + accListJson + "]}");
        }
    }

    /** POST /api/accounts/create  { "name": "...", "deposit": "...", "upiPin": "..." } */
    private class CreateAccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String name = body.getOrDefault("name", "").trim();
                String depositStr = body.getOrDefault("deposit", "").trim();
                String upiPin = body.getOrDefault("upiPin", "1234").trim();
                String customUpiId = body.getOrDefault("upiId", "").trim();

                if (name.isEmpty()) {
                    sendJson(exchange, 400, jsonError("Account holder name cannot be empty."));
                    return;
                }

                BigDecimal deposit;
                try {
                    deposit = new BigDecimal(depositStr);
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, jsonError("Invalid deposit amount. Please enter a valid number."));
                    return;
                }

                BankAccount account = bankingService.createAccount(
                        name, deposit,
                        upiPin.isEmpty() ? "1234" : upiPin,
                        customUpiId.isEmpty() ? null : customUpiId);

                sendJson(exchange, 201, accountToJson(account, "Account and UPI profile created successfully! Default PIN: " + account.getUpiPin()));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /**
     * POST or GET /api/accounts/view
     * Requires UPI PIN to access balance and account security details.
     */
    private class ViewAccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            String identifier;
            String upiPin;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> body = parseJsonBody(exchange);
                identifier = body.getOrDefault("identifier", body.getOrDefault("number", "")).trim();
                upiPin = body.getOrDefault("upiPin", body.getOrDefault("pin", "")).trim();
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                identifier = getQueryParam(exchange, "number");
                if (identifier == null) identifier = getQueryParam(exchange, "identifier");
                upiPin = getQueryParam(exchange, "pin");
            } else {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            if (identifier == null || identifier.isBlank()) {
                sendJson(exchange, 400, jsonError("Account number or UPI ID is required."));
                return;
            }

            // UPI PIN check
            if (upiPin == null || upiPin.isBlank()) {
                sendJson(exchange, 401, jsonError("UPI PIN is required to access bank balance."));
                return;
            }

            try {
                BankAccount account = bankingService.viewAccountWithPin(identifier, upiPin);
                sendJson(exchange, 200, accountToJson(account, "UPI PIN verified. Account balance unlocked."));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /** POST /api/upi/send  { "from": "...", "to": "...", "amount": "...", "upiPin": "...", "remarks": "..." } */
    private class UpiSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String from = body.getOrDefault("from", "").trim();
                String to = body.getOrDefault("to", "").trim();
                String amountStr = body.getOrDefault("amount", "").trim();
                String upiPin = body.getOrDefault("upiPin", "").trim();
                String remarks = body.getOrDefault("remarks", "").trim();

                if (from.isEmpty()) {
                    sendJson(exchange, 400, jsonError("Sender account or UPI ID is required."));
                    return;
                }
                if (to.isEmpty()) {
                    sendJson(exchange, 400, jsonError("Recipient account or UPI ID is required."));
                    return;
                }
                if (upiPin.isEmpty()) {
                    sendJson(exchange, 401, jsonError("UPI PIN is required to authorize transfer."));
                    return;
                }

                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr);
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, jsonError("Invalid amount. Please enter a valid number."));
                    return;
                }

                TransferResult result = bankingService.transferUpi(from, to, amount, upiPin, remarks);

                String json = String.format(
                        """
                        {"success":true,"message":"UPI Transfer Successful!","transactionId":%d,"amount":"%s","sender":{"accountNumber":"%s","holderName":"%s","upiId":"%s","balance":"%s"},"receiver":{"accountNumber":"%s","holderName":"%s","upiId":"%s"},"remarks":"%s"}""",
                        result.transactionId(),
                        result.amount().toPlainString(),
                        escapeJson(result.sender().getAccountNumber()),
                        escapeJson(result.sender().getHolderName()),
                        escapeJson(result.sender().getUpiId()),
                        result.sender().getBalance().toPlainString(),
                        escapeJson(result.receiver().getAccountNumber()),
                        escapeJson(result.receiver().getHolderName()),
                        escapeJson(result.receiver().getUpiId()),
                        escapeJson(result.remarks() == null ? "" : result.remarks()));

                sendJson(exchange, 200, json);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /** POST /api/accounts/deposit  { "accountNumber": "...", "amount": "..." } */
    private class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String accountNumber = body.getOrDefault("accountNumber", body.getOrDefault("identifier", "")).trim();
                String amountStr = body.getOrDefault("amount", "").trim();

                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr);
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, jsonError("Invalid amount. Please enter a valid number."));
                    return;
                }

                BankAccount account = bankingService.deposit(accountNumber, amount);
                sendJson(exchange, 200, accountToJson(account, "Deposit successful!"));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /** POST /api/accounts/withdraw  { "accountNumber": "...", "amount": "...", "upiPin": "..." } */
    private class WithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String accountNumber = body.getOrDefault("accountNumber", body.getOrDefault("identifier", "")).trim();
                String amountStr = body.getOrDefault("amount", "").trim();
                String upiPin = body.getOrDefault("upiPin", "").trim();

                if (upiPin.isEmpty()) {
                    sendJson(exchange, 401, jsonError("UPI PIN is required to authorize withdrawal."));
                    return;
                }

                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr);
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, jsonError("Invalid amount. Please enter a valid number."));
                    return;
                }

                BankAccount account = bankingService.withdraw(accountNumber, amount, upiPin);
                sendJson(exchange, 200, accountToJson(account, "Withdrawal authorized & successful!"));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /** GET /api/accounts/history?number=ACC1001&pin=1234 */
    private class TransactionHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            String identifier = getQueryParam(exchange, "number");
            if (identifier == null) identifier = getQueryParam(exchange, "identifier");

            if (identifier == null || identifier.isBlank()) {
                sendJson(exchange, 400, jsonError("Account number or UPI ID is required."));
                return;
            }

            String pin = getQueryParam(exchange, "pin");

            try {
                List<Transaction> txns = bankingService.getTransactionHistoryWithPin(identifier, pin);

                String txnArray = txns.stream()
                        .map(tx -> String.format(
                                """
                                {"id":%d,"type":"%s","amount":"%s","balanceAfter":"%s","description":"%s","timestamp":"%s"}""",
                                tx.getTransactionId(),
                                tx.getType(),
                                tx.getAmount().toPlainString(),
                                tx.getBalanceAfter().toPlainString(),
                                escapeJson(tx.getDescription()),
                                tx.getTimestamp().format(JSON_DATE_FMT)))
                        .collect(Collectors.joining(","));

                sendJson(exchange, 200,
                        String.format("""
                                {"success":true,"identifier":"%s","transactions":[%s]}""",
                                escapeJson(identifier), txnArray));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    /** POST /api/accounts/change-pin { "identifier": "...", "oldPin": "...", "newPin": "..." } */
    private class ChangePinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCors(exchange);
            if (handlePreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method not allowed"));
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String identifier = body.getOrDefault("identifier", "").trim();
                String oldPin = body.getOrDefault("oldPin", "").trim();
                String newPin = body.getOrDefault("newPin", "").trim();

                bankingService.changeUpiPin(identifier, oldPin, newPin);
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"UPI PIN updated successfully!\"}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            }
        }
    }

    // ── Static File Server ───────────────────────────────────────────────────

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";

            Path filePath = Path.of("web" + path).toAbsolutePath();

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String contentType = getContentType(filePath.toString());
                byte[] content = Files.readAllBytes(filePath);

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                String notFound = "404 — Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    // ── Utility Methods ──────────────────────────────────────────────────────

    private void enableCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String accountToJson(BankAccount account, String message) {
        String msg = message != null ? String.format("""
                ,"message":"%s\"""", escapeJson(message)) : "";
        return String.format("""
                {"success":true,"account":{"accountNumber":"%s","holderName":"%s","upiId":"%s","balance":"%s"}%s}""",
                escapeJson(account.getAccountNumber()),
                escapeJson(account.getHolderName()),
                escapeJson(account.getUpiId()),
                account.getBalance().toPlainString(),
                msg);
    }

    private String jsonError(String message) {
        return String.format("""
                {"success":false,"error":"%s"}""", escapeJson(message));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    private Map<String, String> parseJsonBody(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        Map<String, String> result = new LinkedHashMap<>();
        if (body.isEmpty() || body.equals("{}")) return result;

        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1).trim();
        }

        String[] pairs = body.split(",(?=\\s*\")");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        return result;
    }

    private String getQueryParam(HttpExchange exchange, String paramName) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(paramName)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }

    // ── Entry Point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        AccountRepository accountRepo = new AccountRepository();
        TransactionRepository transactionRepo = new TransactionRepository();
        BankingService service = new BankingService(accountRepo, transactionRepo);

        WebServer webServer = new WebServer(service);
        webServer.start();
    }
}
