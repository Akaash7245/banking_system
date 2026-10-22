# Banking & UPI Management System

A full-stack banking and UPI simulation system featuring a Java HTTP backend and a modern web dashboard.

## Features

- **Account Management**: Create savings, current, and business accounts with PIN protection and UPI ID generation.
- **Deposit & Withdrawal**: Perform secure balance adjustments with validation checks.
- **Transfers**: Transfer funds via Account Number or UPI ID with PIN authorization.
- **Transaction History**: Real-time statement viewing and filtering with transaction status tracking.
- **Modern Web Interface**: Responsive dashboard built with vanilla HTML5, CSS3, and modern JavaScript.
- **RESTful API**: Embedded Java HTTP Server handling banking requests with JSON responses.

## Tech Stack

- **Backend**: Java (JDK 17+) using built-in `com.sun.net.httpserver.HttpServer`
- **Frontend**: HTML5, Modern CSS3, JavaScript (ES6+)
- **Architecture**: Clean layered architecture (Model, Repository, Service, Web/REST layer)

## Getting Started

### Prerequisites

- Java JDK 17 or higher

### Compiling and Running

1. **Compile the Java sources:**
   ```bash
   javac -d out src/com/banking/model/*.java src/com/banking/repository/*.java src/com/banking/service/*.java src/com/banking/web/*.java src/com/banking/app/*.java
   ```

2. **Run the Application:**
   ```bash
   java -cp out com.banking.app.BankingApp
   ```

3. **Access the Web Dashboard:**
   Open your browser and navigate to:
   ```
   http://localhost:3000/
   ```
