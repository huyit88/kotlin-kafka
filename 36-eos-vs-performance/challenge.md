### Dependencies

* *(No new dependencies)*

---

## Problem A

### Requirement

Create a **side-by-side pipeline comparison** to observe **EOS vs non-EOS performance tradeoffs**.

Implement two producers:

1. **Fast Producer (non-EOS)**

   * No transactions
   * No idempotence
2. **Safe Producer (EOS)**

   * `enable.idempotence=true`
   * `transactional.id=eos-benchmark-tx`
   * Uses transactions

Both producers must:

* Write to separate topics:

  * `payments-fast`
  * `payments-safe`
* Send **N messages** (configurable via request param)

Expose REST endpoints:

* `POST /benchmark/fast?n=1000`
* `POST /benchmark/safe?n=1000`

Log for each run:

```
BENCHMARK type=<FAST|SAFE> count=<n> durationMs=<ms>
```

Files:

* `BenchmarkController.kt`
* `FastProducer.kt`
* `SafeProducer.kt`

### Acceptance criteria

* Both endpoints return HTTP 200
* Logs show noticeably higher `durationMs` for `SAFE` compared to `FAST`
* Messages appear in the correct topics

### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.kafka.core.KafkaTemplate
import kotlin.system.measureTimeMillis
```

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 --create --topic payments-fast --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic payments-safe --partitions 3 --replication-factor 1
'
./gradlew :36-eos-vs-performance:bootRun

curl -X POST "http://localhost:8080/benchmark/fast?n=1000"
curl -X POST "http://localhost:8080/benchmark/safe?n=1000"
```

---

## Problem B

### Requirement

Demonstrate **latency impact of read_committed consumers**.

Implement two consumers for `payments-safe`:

1. Group `safe-read-committed`

   * `isolation.level=read_committed`
2. Group `safe-read-uncommitted`

   * `isolation.level=read_uncommitted`

Each consumer logs:

```
CONSUMED_<RC|RU> ts=<eventTime> lagMs=<now - eventTime>
```

(Embed `eventTime` in message value.)

### Acceptance criteria

* `RC` consumer shows **higher average lag**
* `RU` consumer sees records earlier
* Difference is visible in logs

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import java.time.Instant
```

### Command to verify/run

```bash
# After running SAFE benchmark
# Observe logs from both consumer groups
```

---

## Problem C

### Requirement

Replace **full EOS** with **idempotency-only** and compare results.

Implement a third producer:

* Topic: `payments-idempotent`
* Config:

  * `enable.idempotence=true`
  * **No transactions**

Endpoint:

* `POST /benchmark/idempotent?n=1000`
curl -X POST "http://localhost:8080/benchmark/fast?n=1000"
curl -X POST "http://localhost:8080/benchmark/safe?n=1000"
curl -X POST "http://localhost:8080/benchmark/idempotent?n=1000"
Log:

```
BENCHMARK type=IDEMPOTENT count=<n> durationMs=<ms>
```

### Acceptance criteria

* Idempotent producer:

  * Faster than EOS
  * Slower than FAST
* No duplicates on retry (simulate by restarting app mid-send)

### Suggested Import Path

```kotlin
// Same as Problem A
```

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 --create --topic payments-idempotent --partitions 3 --replication-factor 1
'
curl -X POST "http://localhost:8080/benchmark/idempotent?n=1000"
```

---

## Problem D

### Requirement

Create a **decision matrix** for delivery guarantees.

Create `DELIVERY_GUARANTEE_MATRIX.md` with a table covering:

* Use case
* Chosen guarantee
* Reason
* Why other guarantees were rejected

Include at least:

* Payments
* Inventory
* Fraud
* Orders
* Notifications
* Metrics
* Logs

### Acceptance criteria

* Each row has a clear justification
* Uses correct Kafka terminology
* Matches real-world tradeoffs

### Command to verify/run

```bash
cat DELIVERY_GUARANTEE_MATRIX.md
```

---

## Problem E

### Requirement

Create `EOS_TRADEOFFS.md` answering:

1. Why EOS impacts throughput
2. Why read_committed increases latency
3. Difference between:

   * at-least-once + idempotency
   * exactly-once
4. When EOS is a bad idea
5. Real-world architecture pattern to avoid EOS everywhere

Include ASCII diagram comparing:

```
FAST -> Kafka
IDEMPOTENT -> Kafka
EOS -> Kafka (txn + offsets)
```

### Acceptance criteria

* Clear, architecture-level reasoning
* Interview-ready explanations
* No claims that EOS should be used universally

### Command to verify/run

```bash
cat EOS_TRADEOFFS.md
```

---

## ✅ What You’ll Master After This Challenge

* Quantifying EOS cost
* Choosing the right guarantee per use case
* Explaining Kafka tradeoffs like a senior engineer
* Avoiding over-engineering with EOS
