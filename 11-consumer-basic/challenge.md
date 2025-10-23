# Day 11: Consumer Basics (read, process, commit)

### Dependencies

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A — Minimal auto-commit consumer

#### Requirement

* Create `InventoryConsumerBasic.kt` with `main()`:

  * `group.id=inventory-service`
  * `enable.auto.commit=true`, `auto.offset.reset=earliest`
  * `subscribe(listOf("orders"))`
  * In a poll loop, print `partition`, `offset`, `key`, `value`
  * Add graceful shutdown: `wakeup()` in a shutdown hook
* Run a producer (from Day 09/10) to feed data.

#### Acceptance criteria

* Program prints records from `orders`.
* Offsets increase monotonically per partition.

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
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt

# in another terminal (optional view):
docker exec -it kafka bash -lc \
 'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning --timeout-ms 2000'
```

---

### Problem B — Show partition assignment

#### Requirement

* After the first `poll()`, print the consumer’s assignment:

  * `println("Assignment: ${consumer.assignment()}")`
* Also print on rebalance using a `ConsumerRebalanceListener`:

  * `onPartitionsAssigned` / `onPartitionsRevoked`

#### Acceptance criteria

* Console shows something like `Assignment: [orders-0, orders-2]`.
* Rebalance messages appear on start/stop.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
```

#### Command to verify/run

```bash
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt
```

---

### Problem C — Two instances, one group (work sharing)

#### Requirement

* Run **two** copies of `InventoryConsumerBasic` (same `group.id=inventory-service`).
* Ensure topic `orders` has **≥2 partitions** (e.g., 3).
* Produce ~20 messages (Day 09/10 producers).

#### Acceptance criteria

* Across the two consoles, all messages appear **once total** (shared).
* Each process shows a **different** assignment (e.g., C1: `[orders-0, orders-1]`, C2: `[orders-2]`).

#### Suggested Import Path

*(same as A & B)*

#### Command to verify/run

```bash
# window 1
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt
# window 2 (second instance)
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt
# inspect group/lag
docker exec -it kafka bash -lc \
 'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group inventory-service'
```

---

### Problem D — Restart behavior with auto-commit

#### Requirement

* Start one consumer instance, let it read some messages.
* **Stop** the consumer (Ctrl+C). Wait ≥5s to allow auto-commit timer to tick.
* **Restart** the same app and observe where it resumes.
* Repeat but this time **stop immediately** after receiving records (before 5s).

#### Acceptance criteria

* When stopped after ~5s, restart resumes **near the end** (few/no replays).
* When stopped immediately, restart **replays** the last batch (duplicates possible).

#### Suggested Import Path

*(same as A)*

#### Command to verify/run

```bash
# start producer in one cmd
docker exec -it kafka bash -lc '
  { for i in $(seq 1 5); do echo "host-A,ts=$(date +%s%3N),load=$RANDOM"; done;
    sleep 10
    for i in $(seq 1 5); do echo "host-B,ts=$(date +%s%3N),load=$RANDOM"; done;
    sleep 10
    for i in $(seq 1 5); do echo "host-C,ts=$(date +%s%3N),load=$RANDOM"; done; } \
  | kafka-console-producer --bootstrap-server localhost:9092 \
      --topic orders --property parse.key=true --property key.separator=,
'

# start in another cmd, then stop after ~5s
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt
  # start, then stop after ~5s
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicKt
```

---

### Problem E — Batch size & pacing

#### Requirement

* Add configs:

  * `max.poll.records=50`
  * `max.poll.interval.ms=300000`
* In the loop, print `records.count()` per poll and simulate 50–100ms processing delay per record (`Thread.sleep(50)`).

#### Acceptance criteria

* Logs show batches (e.g., “polled 50”), and processing **keeps heartbeating** (no rebalance due to long processing).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerConfig
```

#### Command to verify/run

```bash
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicBatchKt
```

---

### (Optional) Problem F — Simple JSON parsing & basic error guard

#### Requirement

* Parse `value` into a data class `Order(orderId: String, user: String, amount: Double)`.
* If parsing fails, **log error** and continue (don’t crash the loop).

#### Acceptance criteria

* Well-formed events print parsed fields.
* Malformed events print an error line and are **skipped**.

#### Suggested Import Path

```kotlin
// use your preferred JSON lib; if none, simple regex or org.json for quick parsing
```
```bash
docker exec -it kafka bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create \
    --topic orders-json --partitions 3 --replication-factor 1
  kafka-topics --bootstrap-server localhost:9092 --describe --topic orders-json
'
```
docker exec -it kafka bash -lc '
  { for i in $(seq 1 5); do echo "user-$i | {\"orderId\":\"order-$i\", \"user\":\"user-$i\"}"; done;
    sleep 10
    for i in $(seq 1 5); do echo "user-$i | {\"orderId\":\"order-$i\", \"user\":\"user-$i\"}"; done; } \
  | kafka-console-producer --bootstrap-server localhost:9092 \
      --topic orders-json --property parse.key=true --property key.separator="|"
'
#### Command to verify/run

```bash
./gradlew :11-consumer-basic:run -q -PmainClass=com.example.InventoryConsumerBasicJsonKt
```

---

## Hints

* **Graceful shutdown**:

  ```kotlin
  val running = java.util.concurrent.atomic.AtomicBoolean(true)
  Runtime.getRuntime().addShutdownHook(Thread { running.set(false); consumer.wakeup() })
  try { while (running.get()) { val recs = consumer.poll(Duration.ofMillis(500)); /* process */ } }
  catch (_: org.apache.kafka.common.errors.WakeupException) { /* ok */ }
  finally { consumer.close() }
  ```

* **Assignment only appears after poll** — call `poll()` once before printing `consumer.assignment()`.

* **Auto-commit timing**:

  * Default `auto.commit.interval.ms=5000`. Stopping before timer fires → more replay on restart.

* **Group inspection**:

  ```bash
  docker exec -it kafka bash -lc \
    'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group inventory-service'
  ```

* **Ensure topic partitions**:

  ```bash
  docker exec -it kafka bash -lc \
    'kafka-topics --bootstrap-server localhost:9092 --alter --topic orders --partitions 3'  # can only increase
  ```

---

### Suggested Import Path (consolidated)

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
```

---

### Expected Results (samples)

```
Assigned: [orders-0, orders-2]
orders[2@15] key=user-3 value={"orderId":"o-16","user":"user-3","amount":101.6}
Assignment: [orders-2]
polled=50, processing...
```
