# `CHALLENGE.md` — Day 17: Retry Configs (Spring Kafka)

### Dependencies

```kotlin
implementation("org.springframework.kafka:spring-kafka")
implementation("org.springframework.boot:spring-boot-starter")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

---

### Problem A — Synchronous retry with `DefaultErrorHandler`

#### Requirement

* Add a config bean that installs a `DefaultErrorHandler` with **exponential backoff** and **3 retries**.
* Mark `IllegalArgumentException` and `SerializationException` as **not retryable**.
* In your listener, toggle a transient failure (e.g., throw `IllegalStateException("transient")` when `sku == "SKU-RETRY"`).
* Keep listener group `inventory-consumer-retry-sync`.

#### Acceptance criteria

* When sending a message that triggers `IllegalStateException`, logs show **retry attempts** with backoff, then success (if you stop throwing).
* When throwing `IllegalArgumentException`, **no retries** occur (goes straight to failure log).

#### Suggested Import Path

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries
import org.apache.kafka.common.errors.SerializationException
```

#### Command to verify/run

```bash
./gradlew :17-retry-config:bootRun
# produce transient failure
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-RETRY&qty=2"
# exception with backoff
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-NORETRY&qty=5"
```

---

### Problem B — Measure sync retry behavior (timing)

#### Requirement

* Log timestamps on each failure to confirm exponential backoff sequence ~200ms → ~400ms → ~800ms.
* Add a simple counter in the listener to **fail N times then succeed** (e.g., fail twice for `SKU-RETRY-2`).

#### Acceptance criteria

* Logs show ~3 handler invocations for the same record with increasing delays, then a success log.
* Confirm **partition remains blocked** during retries (other messages on same partition wait).

#### Suggested Import Path

```kotlin
import java.time.Instant
```

#### Command to verify/run

```bash
./gradlew :17-retry-config:bootRun
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-RETRY-2&qty=1"
```

---

### Problem C — Non-blocking retries with **Retry Topics**

#### Requirement

* Add a `RetryTopicConfiguration` bean using **exponentialBackoff(500, 2.0, 10000)** and `maxAttempts(4)`.
* Use a separate group id, e.g. `inventory-consumer-retry-async`.
* Keep `IllegalArgumentException` and `SerializationException` as **not retryable**.
* Make your listener **throw** `IllegalStateException("transient")` for `sku == "SKU-ASYNC"`.

#### Acceptance criteria

* On failure, record is **republished** to a retry topic (name includes `-retry-`); main consumer **continues** with other messages.
* After a few minutes or when retries exhaust, record reaches **DLT** (will wire DLT consumer tomorrow; for now, verify retry topic traffic).

#### Suggested Import Path

```kotlin
import org.springframework.kafka.retrytopic.RetryTopicConfiguration
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder
```

#### Command to verify/run

```bash
./gradlew :17-retry-config:bootRun
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-ASYNC&qty=1"
# list topics to see created retry topics
docker exec -it kafka bash -lc 'kafka-topics --bootstrap-server localhost:9092 --list | grep inventory.events'
```

---

### Problem D — Compare sync vs async under load

#### Requirement

* Add an endpoint `/api/stock/bulkRetry?mode=sync|async&sku=...&n=200`:

  * Sends `n` events; for **every 20th** event, trigger the failure condition for the selected mode.
* Measure:

  * end-to-end time for request
  * number of messages processed per second by the consumer (log a rolling counter).
* Keep `inventory.events` with ≥3 partitions so other partitions can progress.

#### Acceptance criteria

* **Sync mode**: throughput dips when failures land; per-partition blocking visible.
* **Async mode**: main topic keeps flowing while failures show up on retry topics.

#### Suggested Import Path

```kotlin
import kotlin.system.measureTimeMillis
```

#### Command to verify/run

```bash
./gradlew :17-retry-config:bootRun
curl -X POST "http://localhost:8080/api/stock/bulkRetry?mode=sync&sku=SKU-LD&n=200"
curl -X POST "http://localhost:8080/api/stock/bulkRetry?mode=async&sku=SKU-LD&n=200"
```

---

### Problem E (Optional) — Mark poison messages as non-retryable & tag headers

#### Requirement

* In the listener, when payload violates a rule (e.g., `qty < 0`), throw `IllegalArgumentException("poison")`.
* Ensure `DefaultErrorHandler.addNotRetryableExceptions(IllegalArgumentException::class.java)` is set.
* For async retries, configure a recoverer that adds headers (`errorType`, `errorMessage`, `originalTopic`) when routing to DLT (you’ll consume DLT tomorrow).

#### Acceptance criteria

* Poison messages **skip retries** and appear directly on DLT with headers.
* Transient errors still retry according to your configs.

#### Suggested Import Path

```kotlin
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.core.KafkaTemplate
import org.apache.kafka.common.header.internals.RecordHeader
```

#### Command to verify/run

```bash
# send a poison event (e.g., negative qty for your rule)
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-POISON&qty=-5"
# (DLT consumption will be verified in Day 19)
```

---

## Hints

* **DefaultErrorHandler (sync)**:

  ```kotlin
  @Bean
  fun defaultErrorHandler(): DefaultErrorHandler =
      DefaultErrorHandler(
          ExponentialBackOffWithMaxRetries(3).apply {
              initialInterval = 200; multiplier = 2.0; maxInterval = 2000
          }
      ).apply {
          addNotRetryableExceptions(IllegalArgumentException::class.java,
                                    SerializationException::class.java)
      }
  ```

* **Retry Topics (async)**:

  ```kotlin
  @Bean
  fun retryTopicConfig(tpl: org.springframework.kafka.core.KafkaTemplate<String, Any>)
      : RetryTopicConfiguration =
      RetryTopicConfigurationBuilder
          .newInstance()
          .exponentialBackoff(500, 2.0, 10_000)
          .maxAttempts(4)
          .retryOn(org.springframework.kafka.listener.ListenerExecutionFailedException::class.java)
          .notRetryOn(IllegalArgumentException::class.java,
                      SerializationException::class.java)
          .create(tpl)
  ```

* **Toggle failures**: Gate throws by key/sku or with a small in-memory counter so you can test retries then success:

  ```kotlin
  private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
  private fun shouldFail(key: String, times: Int) =
      (attempts.merge(key, 1, Int::plus) ?: 1) <= times
  ```

* **Observe retry topics**:

  ```bash
  docker exec -it kafka bash -lc \
    'kafka-topics --bootstrap-server localhost:9092 --list | grep inventory.events'
  ```

---

### Suggested Import Path (consolidated)

```kotlin
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.retrytopic.RetryTopicConfiguration
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder
import org.apache.kafka.common.errors.SerializationException
```

---

### Expected Results

* Clear visibility of **sync retries (seek/backoff)** vs **async retries (retry topics)**.
* Understanding of when to mark exceptions as **not retryable**.
* Confidence to choose the right retry strategy per use case before wiring **DLT** in Day 19.
