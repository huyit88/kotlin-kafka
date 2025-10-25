#Day 12: Manual Commit & At-Least-Once Processing

### Dependencies

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A — Manual commit (batch)

#### Requirement

* Create `InventoryConsumerManual.kt`:

  * `group.id=inventory-service-manual`
  * `enable.auto.commit=false`
  * `auto.offset.reset=earliest`
  * `max.poll.records=50`
* Loop:

  * `poll(1000ms)` → process all records (print `partition@offset key value`)
  * Call `commitSync()` **once per poll** if `records.count() > 0`.
* Add graceful shutdown (shutdown hook + `consumer.wakeup()`).

#### Acceptance criteria

* Reads from `orders` and prints messages.
* After each non-empty poll, prints “Committed”.
* Restarting the app **never loses** already processed messages (may reprocess some).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
```

#### Command to verify/run

```bash
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerManualKt

```

---

### Problem B — Crash & replay proof (at-least-once)

#### Requirement

* Start `InventoryConsumerManual` and let it consume a few batches.
* **Before** `commitSync()` happens (e.g., put a `Thread.sleep(3000)` inside processing), **kill** the app (Ctrl+C).
* Restart the same app.

#### Acceptance criteria

* Some records from the last uncommitted poll are **reprocessed** (duplicates) → proves at-least-once.
* No messages are **lost** (you eventually see all messages again).

#### Suggested Import Path

*(same as Problem A)*

#### Command to verify/run

```bash
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerManualKt

# while it’s processing (before commit), Ctrl+C, then re-run the same command
```

---

### Problem C — Fine-grained per-partition commit

#### Requirement

* Create `InventoryConsumerPerPartition.kt`:

  * Still manual commit (`enable.auto.commit=false`).
  * Build a `MutableMap<TopicPartition, OffsetAndMetadata>`.
  * For each record processed, update map with **`offset + 1`** for that record’s partition.
  * After processing the poll, `commitSync(map)` and clear it.
* Print the map you commit each time (e.g., `Committed: {orders-0=123, orders-1=78, ...}`).

#### Acceptance criteria

* Commits include only partitions that advanced.
* Restart shows resume from the **last committed offset per partition**.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
```

#### Command to verify/run

```bash
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerPerPartitionKt

```

---

### Problem D — Rebalance-safe commits (commit on revoke)

#### Requirement

* Create `InventoryConsumerRebalance.kt`:

  * Manual commit.
  * Register `ConsumerRebalanceListener`.
  * In `onPartitionsRevoked`, **commitSync()** (or the per-partition map if using Problem C style).
* Run **two instances** with same `group.id` (e.g., `inventory-reb`), then **start/stop** one to trigger rebalances while producing orders.

#### Acceptance criteria

* Logs show `onPartitionsRevoked` → commit happened before partitions move.
* No large replay after rebalances (only at-least-once duplicates at boundaries).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
```

#### Command to verify/run

```bash
# window 1
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerRebalanceKt

# window 2 (second instance, same group)
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerRebalanceKt
# observe rebalances while starting/stopping window 2
```

---

### Problem E — Async commit + periodic sync (hybrid)

#### Requirement

* Create `InventoryConsumerHybrid.kt`:

  * Manual commit.
  * After each poll, call `commitAsync { offsets, ex -> ... }` (log success/failure).
  * Additionally, every **N records (e.g., 200)** or **T seconds (e.g., 5s)**, perform a **`commitSync()`** as a safety barrier.
  * Handle `WakeupException` on shutdown; on exit, do a final `commitSync()`.

#### Acceptance criteria

* Normal operation uses async commits (higher throughput).
* On shutdown or timer/record threshold, a **sync** commit ensures minimal replay.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
```

#### Command to verify/run

```bash
./gradlew :12-manual-commit:run -q -PmainClass=com.example.InventoryConsumerHybridKt

```

---

## Hints

* **Per-partition commit skeleton (Problem C):**

```kotlin
val toCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()
for (rec in records) {
    // process(rec)
    val tp = TopicPartition(rec.topic(), rec.partition())
    toCommit[tp] = OffsetAndMetadata(rec.offset() + 1)
}
if (toCommit.isNotEmpty()) {
    consumer.commitSync(toCommit)
    println("Committed: $toCommit")
    toCommit.clear()
}
```

* **Rebalance listener (Problem D):**

```kotlin
consumer.subscribe(listOf("orders"), object : ConsumerRebalanceListener {
    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        // commit whatever you’ve tracked so far
        consumer.commitSync() // or commitSync(map)
        println("Revoked: $partitions (committed before revoke)")
    }
    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        println("Assigned: $partitions")
    }
})
```

* **Hybrid timer (Problem E):**

```kotlin
var sinceLastSync = 0
var lastSyncTime = System.currentTimeMillis()
for (rec in records) {
    // process...
    sinceLastSync++
}
consumer.commitAsync { _, ex -> if (ex != null) System.err.println("commitAsync fail: ${ex.message}") }

val now = System.currentTimeMillis()
if (sinceLastSync >= 200 || now - lastSyncTime >= 5000) {
    consumer.commitSync()
    sinceLastSync = 0
    lastSyncTime = now
}
```

* **Inspect progress / lag:**

```bash
docker exec -it kafka bash -lc \
  'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group inventory-service-manual'
```

* **Ensure topic partitions (for multi-instance tests):**

```bash
docker exec -it kafka bash -lc \
  'kafka-topics --bootstrap-server localhost:9092 --alter --topic orders --partitions 3 || true'
```
