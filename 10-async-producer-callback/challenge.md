#  Day 10: Async Producer Callbacks & Error Handling

### Dependencies

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A — Async sends with callbacks + latch

#### Requirement

* Implement `AsyncOrderProducer.kt`:

  * Producer props: `acks=all`, `enable.idempotence=true`, `max.in.flight.requests.per.connection=5`.
  * Build 20 order records (keys cycle `user-1..user-5`).
  * Send **asynchronously** with `send(record) { metadata, exception -> ... }`.
  * Use a **CountDownLatch(20)**; `countDown()` in the callback.
  * `flush()` and **await** latch before `close()`.
  * Log on success: `topic, partition, offset, timestamp`; on failure: exception type + message.

#### Acceptance criteria

* Program exits `0`.
* Console shows **20** success lines (no exceptions).
* Latch reached zero before `close()` (no missed callbacks).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.CountDownLatch
```

#### Command to verify/run

```bash
./gradlew :10-async-producer-callback:run -q -PmainClass=com.example.AsyncOrderProducerKt

docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning \
     --property print.key=true --property print.partition=true --timeout-ms 5000'
```

---

### Problem B — Classify errors (retriable vs fatal) + limited app-level retry

#### Requirement

* Add a helper `sendWithAppRetry(producer, record, maxAttempts=3)` that:

  * Calls `send()` with a callback.
  * If `exception is RetriableException` and attempts remain → exponential backoff (100ms → 200ms → 400ms…), then **re-send**.
  * If **fatal** (e.g., `SerializationException`, `InvalidTopicException`, `AuthorizationException`) → **fail fast** (throw) and record the failure.
* Use it to send the same 20 events.

#### Acceptance criteria

* All events delivered; if any retriable error happens, logs show **retry attempts** then success.
* Fatal errors would terminate the run with a clear message (you shouldn’t hit these in a happy path).

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.AuthorizationException
```

#### Command to verify/run

```bash
./gradlew :10-async-producer-callback:run -q -PmainClass=com.example.AsyncOrderProducerRetryKt

```

---

### Problem C — Dead Letter Topic (DLT) for fatal errors

#### Requirement

* Create topic `orders.dlt` (RF=1, 3 partitions).
* Modify your callback: on **fatal** error, publish the original record to `orders.dlt` with headers:

  * `errorType`, `errorMessage`, `originalTopic`, `originalPartition` (if known).
* Keep the main send logic otherwise identical.

#### Acceptance criteria

* On induced fatal errors (see Problem D hints), records appear on **`orders.dlt`**.
* DLT messages contain headers as specified.

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
```

#### Command to verify/run

```bash
# create DLT once
docker exec -it kafka bash -lc \
  'kafka-topics --bootstrap-server localhost:9092 --create --topic orders.dlt --partitions 3 --replication-factor 1 || true'
./gradlew :10-async-producer-callback:run -q -PmainClass=com.example.AsyncOrderProducerDLTKt

docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders.dlt --from-beginning \
     --property print.headers=true --timeout-ms 3000'
```

---

### Problem D — Force transient failure and prove “no duplicate writes”

#### Requirement

* While `AsyncOrderProducerRetry` is sending, **pause** the broker briefly to force timeouts/retries, then **unpause**:

  * `docker pause kafka` → sleep 2s → `docker unpause kafka`
* Because idempotence is enabled, retried sends must **not** produce duplicate writes.
* After unpause, all 20 records should be present **once** (continuous offsets).

#### Acceptance criteria

* Producer logs show **retry** warnings during pause, then success callbacks.
* Consuming from `orders` shows **exactly 20 records** (no duplicates), even though callbacks saw retries.

#### Suggested Import Path

*(N/A — shell interaction)*

#### Command to verify/run

```bash
# terminal A — start producer with retry
./gradlew :10-async-producer-callback:run -q -PmainClass=com.example.AsyncOrderProducerRetryKt

# terminal B — force transient failure
docker pause kafka; sleep 2; docker unpause kafka

# verify no dupes (should be 20 logical records)
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic orders --from-beginning --timeout-ms 3000' | wc -l
```

---

### (Optional) Problem E — Measure callback latency & simple percentiles

#### Requirement

* Record `sendStartNanos` per record; in callback compute `latencyMs`.
* After all callbacks, print count, p50/p95/p99 latency.
* Repeat with/without `linger.ms=20` and `compression.type=lz4` — compare.

#### Acceptance criteria

* Latency stats printed (e.g., `p50=5ms p95=15ms p99=40ms`).
* Short analysis: how `linger` and `compression` affected latency/throughput.

#### Suggested Import Path

```kotlin
import java.util.concurrent.atomic.AtomicInteger
```

#### Command to verify/run

```bash
./gradlew -q run --args="AsyncOrderProducerLatency"
./gradlew :10-async-producer-callback:run -q -PmainClass=com.example.AsyncOrderProducerLatencyKt

```

---

## Hints

* **Latch pattern (Problem A)**:

  ```kotlin
  val latch = java.util.concurrent.CountDownLatch(N)
  producer.send(rec) { md, ex -> /* log */ latch.countDown() }
  latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
  producer.flush()
  ```

* **Retry classifier (Problem B)**:

  ```kotlin
  val retriable = ex is org.apache.kafka.common.errors.RetriableException
  if (retriable) backoffAndResend() else throw ex
  ```

* **DLT publish (Problem C)**:

  ```kotlin
  val dlt = ProducerRecord("orders.dlt", rec.key(), rec.value()).apply {
      headers().add(RecordHeader("errorType", ex::class.java.name.toByteArray()))
      headers().add(RecordHeader("errorMessage", (ex.message ?: "").toByteArray()))
      headers().add(RecordHeader("originalTopic", "orders".toByteArray()))
  }
  producer.send(dlt)
  ```

* **Transient failure (Problem D)**: idempotence drops retry duplicates server-side (same PID/sequence), so topic shows one logical write per send.

* **Latency stats (Problem E)**: keep a `MutableList<Long>` of latencies; sort and index:

  ```kotlin
  fun pct(xs: List<Long>, p: Double) = xs[(p * (xs.size - 1)).toInt()]
  ```

---

### Troubleshooting

* **Callbacks never fire** → forgot `flush()` or app exits before latch reached zero.
* **Duplicates after pause/unpause** → check `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection<=5`.
* **DLT empty** → you didn’t induce a **fatal** error. Try serializing a non-UTF payload with `StringSerializer` (causes `SerializationException`), or use an invalid topic name to simulate failure (for testing only).

This lab cements **high-throughput async sending**, **correct error handling**, **safe retries**, and **DLT hygiene** — production-ready patterns.
