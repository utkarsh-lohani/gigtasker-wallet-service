# GigTasker Wallet Service 💸

The **Wallet Service** is the financial backbone of the GigTasker platform. It acts as an internal banking system that manages user funds, ensuring that payments are securely held in escrow until a task is successfully completed.

## 🚀 Core Concepts

This service implements an **Escrow Pattern** to protect both the Gig Poster and the Worker.

### 1. The Dual-Balance Model
Every wallet has two separate balances:
* **`Available Balance`**: Money the user actually owns and can withdraw or spend on new tasks.
* **`Held Funds` (Escrow)**: Money that has been committed to active tasks. It is deducted from the *Available Balance* but hasn't been paid to the worker yet.

### 2. The Transaction Flow
1.  **Deposit:** User adds money. (`Balance` ↑)
2.  **Bid Accepted:** Poster's money is moved to Escrow. (`Balance` ↓, `Held` ↑)
3.  **Task Completed:** Money moves from Poster's Escrow to Worker's Balance. (`Held` ↓, `Worker Balance` ↑)
4.  **Task Cancelled:** Money moves back to Poster. (`Held` ↓, `Balance` ↑)

---

## 🛠️ Tech Stack

* **Java:** 25 (Amazon Corretto)
* **Framework:** Spring Boot 3.4+
* **Database:** PostgreSQL 18 (`wallet_db`)
* **ORM:** Spring Data JPA (Hibernate)
* **Security:** Spring Security 6 + OAuth2 Resource Server (Keycloak)
* **Messaging:** RabbitMQ (for async transaction events)
* **Documentation:** SpringDoc OpenAPI (Swagger)

---

## 📂 Data Model

### Wallet Entity
Since authentication tokens provide emails, wallets are linked via **Email** rather than numeric IDs to decouple this service from the User Service.

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | Long | Primary Key |
| `userEmail` | String | Unique identifier (from Keycloak Token) |
| `balance` | BigDecimal | Liquid funds |
| `heldFunds` | BigDecimal | Locked funds |

### Transaction Entity
An immutable ledger of every movement.

| Field | Type | Description |
| :--- | :--- | :--- |
| `type` | Enum | `DEPOSIT`, `WITHDRAWAL`, `HOLD`, `RELEASE`, `REFUND` |
| `amount` | BigDecimal | The value moved |
| `timestamp` | LocalDateTime | Audit timestamp |

---

## 📦 Installation & Run

This service depends on the **GigTasker Common Library**.

### 1. Build the Shared Library
Ensure the common library is installed in your local Maven repository first.
```bash
cd ../gigtasker-common
mvn clean install
```

### 2. Build the Service
```bash
mvn clean package -DskipTests
```

###3. Run via Docker
This service is orchestrated via the root docker-compose.yml.

```bash
cd ../../gigtasker-config
docker-compose up -d --build wallet-service
```

---

## 🔌 API Documentation

Once the application is running, the API documentation is available via the **Centralized Gateway Portal**.

### 📘 Swagger UI
Access the Swagger UI at:

👉 **http://localhost:9090/swagger-ui.html**

From the dropdown, select **"Wallet Service"**.

---

## 🔐 Configuration

Configuration is managed by the **Config Server**.

- **Local Config:** `gigtasker-config/wallet-service.yml`
- **Docker Config:** `gigtasker-config/application-docker.yml`  
  *(Inherits DB/Rabbit settings)*
- **Docker Specific Overrides:** `gigtasker-config/wallet-service-docker.yml`  
  *(Overrides DB URL to `wallet_db`)*

---

## 🤝 Contributing

### Flow
Ensure that any logic change maintains the equation:

**Total System Money = Sum(Balances) + Sum(HeldFunds)**

### Precision
- Always use **BigDecimal** for currency.  
- **Never** use `Double`.


### 📄 Direct JSON (Internal)
The raw OpenAPI JSON is available at:

👉 **http://localhost:8080/v3/api-docs**
