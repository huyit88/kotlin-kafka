### Dependencies

## Problem A

### Requirement

Configure a **transactional Kafka producer** for a payment pipeline.

1. Topics:

* `payments`
* `payment-audit`
  (both with 3 partitions)

2. Producer configuration:

* `enable.idempotence=true`
* `transactional.id=payment-service-tx-1`
* `acks=all`
* `retries=Integer.MAX_VALUE`

3. KafkaTemplate:

* Must support transactions
* Uses a **transaction id prefix**

4. Add startup verification log:

```
TX_PRODUCER_READY transactional.id=<id>
```

Files:

* `KafkaProducerConfig.kt`

---

### Acceptance criteria

* App starts without error
* Producer initializes transactions successfully
* Log contains `TX_PRODUCER_READY`

---

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
```

---

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic payments --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic payment-audit --partitions 3 --replication-factor 1
'
./gradlew 33-kafka-transaction:bootRun
# Expected log: TX_PRODUCER_READY transactional.id=payment-service-tx-1
```

---

## Problem B

### Requirement

Implement **atomic payment writes** using a Kafka transaction.

1. REST endpoint:

```
POST /pay
```

2. Request body:

```json
{
  "paymentId": "p-1",
  "amount": 100.0,
  "fail": false
}
```

3. Processing logic:

* Begin transaction
* Send:

  * `payments` → `PAYMENT:<id>:<amount>`
  * `payment-audit` → `AUDIT:<id>`
* If `fail=true` → throw exception

4. Use:

* `KafkaTemplate.executeInTransaction {}`

Logs:

```
PAYMENT_BEGIN id=<id>
PAYMENT_COMMIT id=<id>
PAYMENT_ABORT id=<id>
```

Files:

* `PaymentController.kt`
* `PaymentService.kt`

---

### Acceptance criteria

* For `fail=false`:

  * Messages appear in **both topics**
  * `PAYMENT_COMMIT` logged
* For `fail=true`:

  * **No messages** appear in either topic
  * `PAYMENT_ABORT` logged

---

### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
```

---

### Command to verify/run

```bash
# Success case
curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-1","amount":100.0,"fail":false}'

# Failure case
curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-2","amount":200.0,"fail":true}'
```

Verify topics:

```bash
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payments --from-beginning

kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payment-audit --from-beginning
  '
```

Expected:

* Only `p-1` messages exist

---

## Problem C

### Requirement

Configure a **read-committed consumer**.

1. Consumer group:

* `payment-consumer`

2. Consumer config:

* `isolation.level=read_committed`

3. Listener:

* Reads from both:

  * `payments`
  * `payment-audit`

4. Log:

```
CONSUMED topic=<topic> value=<value>
```

---

### Acceptance criteria

* Consumer **never** logs records from aborted transactions
* Only committed payments are visible

---

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
```

---

### Command to verify/run

```bash
# Trigger both success and failure again
curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-3","amount":300.0,"fail":false}'

curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-4","amount":400.0,"fail":true}'

# Expected: only p-3 is consumed
```

---

## Problem D

### Requirement

Demonstrate **dirty read risk**.

1. Create a second consumer:

* Group: `payment-consumer-dirty`
* `isolation.level=read_uncommitted`

2. Log:

```
DIRTY_READ topic=<topic> value=<value>
```

---

### Acceptance criteria

* Dirty consumer **may see** aborted records
* Read-committed consumer **never sees** them
* Difference is clearly observable in logs

---

### Suggested Import Path

```kotlin
// Same as Problem C
```

---

### Command to verify/run

```bash
# Trigger aborted transaction
curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"p-5","amount":500.0,"fail":true}'

# Expected:
# DIRTY_READ logs show p-5
# CONSUMED logs do NOT show p-5
```

---

## Problem E

### Requirement

Create `TRANSACTIONAL_PRODUCER.md` answering:

1. Idempotence vs Transactions
2. Why `transactional.id` is required
3. What happens on producer crash mid-transaction
4. Why consumers must use `read_committed`
5. When **not** to use transactions

---

### Acceptance criteria

* Uses correct Kafka terminology
* Clear, interview-ready explanations
* Includes an ASCII flow:

```
begin -> send -> send -> commit
```

---

## ✅ What You’ll Master After This Challenge

* Atomic multi-topic writes
* Exactly-once foundations
* Payment-grade Kafka pipelines
* Interview-level Kafka mastery
