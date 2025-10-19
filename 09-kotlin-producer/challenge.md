# Day 09: Kotlin Producer (Orders)

### Dependencies

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A — Minimal Producer (sync send)

#### Requirement

* Create `OrderProducer.kt` with a `main()` that:

  * Connects to `localhost:9092`
  * Ensures topic `orders` exists (or assume pre-created)
  * Sends **5** order events **synchronously** (use `.get()`), keys `user-1..user-3`, values JSON strings:

    * `{"orderId":"o-<n>","user":"user-<1..3>","amount":<double>}`
  * Print `partition`, `offset`, and `timestamp` from `RecordMetadata`.
* Producer props: `acks=all`, String serializers.

#### Acceptance criteria

* Program exits 0.
* Console shows **5** lines with partition & offset strictly increasing per partition.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
```

#### Command to verify/run

```bash
./gradlew :09-kotlin-producer:run -q -PmainClass=com.example.OrderProducerKt
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning --property print.key=true --timeout-ms 5000'
```

---

### Problem B — Async Producer with Callbacks

#### Requirement

* Create `OrderProducerAsync.kt` that sends **15** events asynchronously with a **callback** printing:

  * `topic`, `partition`, `offset` on success
  * stack trace on failure
* Use keys cyclically: `user-1`, `user-2`, `user-3` (to show per-user stickiness).
* Close the producer only **after** all futures complete (`flush()` then `close()`).

#### Acceptance criteria

* All callbacks log success (no exceptions).
* `user-1` events always land on the **same partition**, same for `user-2`, `user-3`.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.RecordMetadata
```

#### Command to verify/run

```bash
./gradlew :09-kotlin-producer:run -q -PmainClass=com.example.OrderProducerAsyncKt

docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning --property print.key=true --property print.partition=true --timeout-ms 5000'
```

---

### Problem C — Batching & Throughput (linger/batch/compression)

#### Requirement

* Create `OrderProducerTuned.kt` with props:

  * `acks=all`
  * `linger.ms=20`
  * `batch.size=65536`
  * `compression.type=lz4` (or `snappy`)
* Send **1000** orders (random user keys among `user-1..user-50`).
* Measure wall-clock time (ms) from first send to `flush()` complete.
* Print throughput: `records/sec`.

#### Acceptance criteria

* Program completes without error.
* Prints total time and throughput.
* (Optional) Compare with default config and note the difference.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.ProducerConfig
```

#### Command to verify/run

```bash
./gradlew :09-kotlin-producer:run -q -PmainClass=com.example.OrderProducerTunedKt

```

---

### Problem D — Idempotent Producer (retry duplicate-safety)

#### Requirement

* Create `OrderProducerIdem.kt` with props:

  * `enable.idempotence=true`
  * `acks=all`
  * `retries=Integer.MAX_VALUE`
  * `max.in.flight.requests.per.connection=5`
* Send **20** orders **twice intentionally** (loop the same (key,value) pairs two times).
* Add a simple retry wrapper around `send().get()` that retries on transient exceptions (e.g., `TimeoutException`).
* Verify on the consumer side that **no duplicate offsets** exist for the same records (duplicates should not be written due to idempotence; you’ll see continuous offsets, not duplicated messages in the log).

#### Acceptance criteria

* Console consumer shows **20** logical records (not 40), even though you sent twice.
* Producer logs show retries (if you simulate intermittent network hiccup by pausing broker briefly, optional).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TimeoutException
```

#### Command to verify/run

```bash
./gradlew :09-kotlin-producer:run -q -PmainClass=com.example.OrderProducerIdemKt

docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders2 --from-beginning --property print.headers=true  --timeout-ms 5000'
```

---

### (Optional) Problem E — Headers & Schema-ish payload

#### Requirement

* Extend any producer to attach headers:

  * `eventType=ORDER_CREATED`
  * `source=OrderService`
  * `traceId=<uuid>`
* Value is JSON string with fields: `orderId`, `user`, `amount`, `createdAt`.
* Print headers in the consumer (`--property print.headers=true` via CLI or write a small consumer).

#### Acceptance criteria

* Consumer output shows headers along with the payload.

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.header.internals.RecordHeader
```

#### Command to verify/run

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning --property print.headers=true --timeout-ms 5000'
```

---

## Hints

* **Topic:** If `orders` doesn’t exist, create it (1–3 partitions works). Example (CLI):

  ```bash
  docker exec -it kafka bash -lc \
    'kafka-topics --bootstrap-server localhost:9092 --create --topic orders --partitions 3 --replication-factor 1 || true'
  ```
* **Sync vs Async:** `.get()` blocks and surfaces exceptions quickly; callbacks are non-blocking—use `flush()` before `close()`.
* **Key stickiness:** same key → same partition → ordering per user.
* **Idempotence scope:** prevents **retry duplicates per producer session & partition**; for cross-restart exactly-once and consume-transform-produce, you’ll use **transactions** later.
* **Throughput timing:** capture `val t0=System.nanoTime()` before sends; `val t1=System.nanoTime()` after `flush()`; compute `records/sec`.

---

## Expected CLI outputs (samples)

* Describe topic:

  ```
  kafka-topics --bootstrap-server localhost:9092 --describe --topic orders
  # PartitionCount: 3, ReplicationFactor: 1
  ```
* Consumer (partition & key visible):

  ```
  key = user-1, partition = 2, value = {"orderId":"o-7","user":"user-1","amount":42.5}
  ```
