### Dependencies

* `implementation("org.springframework.boot:spring-boot-starter-data-jpa")`
* `runtimeOnly("com.h2database:h2")`

---

### Problem A – Basic Idempotent Payment Processing

#### Requirement

Implement an **idempotent payment pipeline**:

* Topic: `payments`
* REST endpoint: `POST /payments`

  * Request body:

    ```json
    {
      "paymentId": "p-1001",
      "userId": "u-1",
      "amount": 5000
    }
    ```
* Producer:

  * Sends messages to topic `payments`
  * **Key** = `paymentId`
  * **Value** = `"paymentId,userId,amount"` as a `String`
* Consumer:

  * Listens to topic `payments`
  * Parses the string into `paymentId`, `userId`, `amount`
  * Calls `PaymentService.processPayment(paymentId, userId, amount)`
* Persistence:

  * JPA entity `ProcessedPayment(paymentId: String, processedAt: Instant)`
  * JPA repository `ProcessedPaymentRepository : CrudRepository<ProcessedPayment, String>`
* Idempotent logic in `PaymentService`:

  * If `ProcessedPaymentRepository.existsById(paymentId)` is `true` → **log and skip**
  * Else:

    * Log `"Processing NEW payment: $paymentId"`
    * Simulate side effect (e.g., println)
    * Save `ProcessedPayment(paymentId)` to DB

#### Acceptance criteria

* First `POST /payments` with a **new** `paymentId`:

  * Logs:

    * `📥 Received from Kafka: ...`
    * `Processing NEW payment: p-1001`
* Second `POST /payments` with the **same** `paymentId`:

  * Logs:

    * `📥 Received from Kafka: ...`
    * `Duplicate payment detected, skipping: p-1001`
  * No second “Processing NEW payment” line.
* DB table `PROCESSED_PAYMENT` contains **exactly one** row for `paymentId = 'p-1001'`.

#### Suggested Import Path

```kotlin
// ProcessedPayment.kt
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant

// ProcessedPaymentRepository.kt
import org.springframework.data.repository.CrudRepository

// PaymentRequest.kt
// (no imports needed beyond package & data class)

// PaymentProducerController.kt
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// PaymentService.kt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// PaymentConsumer.kt
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
```

#### Command to verify/run

```bash
# Run application
./gradlew :20-idempotency:bootRun

# First time: should be processed
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-1001","userId":"u-1","amount":5000}'

# Second time with same paymentId: should be skipped
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-1001","userId":"u-1","amount":5000}'
# Expected logs: 'Duplicate payment detected, skipping: p-1001'
```

---

### Problem B – Idempotent User Balance Ledger

#### Requirement

Extend the system to maintain a **user balance ledger** with idempotent updates:

* JPA entity `UserBalance(userId: String, balance: Long)`

  * `userId` is the primary key
  * `balance` is the total amount of processed payments
* Extend `PaymentService.processPayment`:

  * After confirming `paymentId` is not processed:

    * Load existing `UserBalance` by `userId` (or create new with `balance = 0`)
    * Increase `balance` by `amount`
    * Save updated `UserBalance`
* Add REST endpoint `GET /balances/{userId}` returning:

  ```json
  {
    "userId": "u-1",
    "balance": 5000
  }
  ```

#### Acceptance criteria

* After sending the **same** payment (`paymentId = p-2001`) **twice**:

  * `UserBalance` for `userId = u-1` shows `balance = amount` (not doubled).
  * Example:

    * amount = 5000
    * After 2 POSTs for same paymentId:

      * `GET /balances/u-1` → `{"userId":"u-1","balance":5000}`

#### Suggested Import Path

```kotlin
// UserBalance.kt
import jakarta.persistence.Entity
import jakarta.persistence.Id

// UserBalanceRepository.kt
import org.springframework.data.repository.CrudRepository

// BalanceController.kt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```

#### Command to verify/run

```bash
./gradlew :20-idempotency:bootRun

# Send same payment ID twice
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-2001","userId":"u-1","amount":5000}'

curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-2001","userId":"u-1","amount":5000}'

# Check balance
curl http://localhost:8080/balances/u-1
# Expected: {"userId":"u-1","balance":5000}
```

---

### Problem C – Simulate Duplicates via “Replay” Endpoint

#### Requirement

Add a way to **intentionally send duplicates** for a previously used `paymentId`:

* New endpoint: `POST /payments/replay/{paymentId}`

  * Looks up an existing processed payment’s details (you can:

    * Either store full payment data in `ProcessedPayment`
    * Or more simply: accept a **new body** that reuses `paymentId`)
  * Produces **another Kafka message** to topic `payments` with:

    * Same `paymentId`
    * Same `userId`
    * Same `amount`
* The consumer logic remains unchanged (idempotent service).

> To keep it simple, you may implement `replay` as:
>
> * Request body `{ "userId": "...", "amount": 5000 }`
> * Path variable is the existing `paymentId` you want to reuse.

#### Acceptance criteria

* After **first** POST `/payments` with `paymentId = p-3001`, balance increases.
* After **POST** `/payments/replay/p-3001` with same payload:

  * `PaymentService` logs:

    * `Duplicate payment detected, skipping: p-3001`
  * `UserBalance` for that user remains unchanged.

#### Suggested Import Path

```kotlin
// PaymentsReplayController.kt
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```

#### Command to verify/run

```bash
./gradlew :20-idempotency:bootRun

# Original payment
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-3001","userId":"u-1","amount":1000}'

# Replay duplicate by same paymentId
curl -X POST http://localhost:8080/payments/replay/p-3001 \
  -H "Content-Type: application/json" \
  -d '{"userId":"u-1","amount":1000}'

# Balance should stay 1000, not 2000
curl http://localhost:8080/balances/u-1
# Expected: {"userId":"u-1","balance":1000}
```

---

### Problem D – TTL-Based Idempotency Cleanup (Optional)

#### Requirement

Add **time-based cleanup** for old processed payments:

* Extend `ProcessedPayment` with `processedAt: Instant` (if not already).
* Add a scheduled cleanup that:

  * Runs every minute.
  * Deletes rows where `processedAt` < now - 10 minutes.
* Use `@EnableScheduling` and `@Scheduled` in Spring.
* Expose `GET /payments/processed` returning the **count** of rows currently in `PROCESSED_PAYMENT`.

> This simulates a real-world setup where you only need idempotency protection within a limited window.

#### Acceptance criteria

* After sending several payments, `GET /payments/processed` > 0.
* Wait > 10 minutes (or configure a shorter TTL for testing, e.g. 30 seconds).
* After TTL passes, `GET /payments/processed` eventually returns `0`.

#### Suggested Import Path

```kotlin
// ProcessedPayment cleanup scheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

// Controller for /payments/processed
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
```

#### Command to verify/run

```bash
./gradlew :20-idempotency:bootRun

# Produce a few payments
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-4001","userId":"u-1","amount":100}'

curl http://localhost:8080/payments/processed
# Expected: > 0

# After TTL passes (e.g. 30s–10min depending on your config)
curl http://localhost:8080/payments/processed
# Expected: 0
```
