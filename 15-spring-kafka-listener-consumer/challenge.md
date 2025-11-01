# Day 15: `@KafkaListener` Consumer (InventoryService)

### Dependencies

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.kafka:spring-kafka")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.+")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

---

### Problem A — Minimal `@KafkaListener` (manual ack)

#### Requirement

* In `application.yml` set:

  * `spring.kafka.bootstrap-servers=localhost:9092`
  * `spring.kafka.consumer.group-id=inventory-consumer`
  * `spring.kafka.consumer.auto-offset-reset=earliest`
  * `spring.kafka.listener.ack-mode=MANUAL_IMMEDIATE`
* Create `InventoryListener.kt` with:

  * `@KafkaListener(topics=["inventory.events"], groupId="inventory-consumer")`
  * Method args: `ConsumerRecord<String,String>` and `Acknowledgment`
  * Parse JSON into `StockChanged(eventId, sku, delta, reason, occurredAt)`
  * Update in-memory map `sku -> qty` and `ack.acknowledge()` **after** successful processing
  * Log: `sku, delta, newQty, partition, offset`

#### Acceptance criteria

* App starts and **consumes** events sent from Day 14 producer.
* Each processed record logs and **commits** (no replay on restart for already acked records).

#### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
```

#### Command to verify/run

```bash
// run producer 8080
./gradlew :14-spring-kafka-producer:bootRun

// run consumer on 8081
./gradlew :15-spring-kafka-listener-consumer:bootRun
# in another terminal, send a couple of events
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-123&qty=2"
curl -X POST "http://localhost:8080/api/stock/release?sku=SKU-123&qty=1"
```

---

### Problem B — Concurrency (intra-app parallelism)

#### Requirement

* In `application.yml`, set `spring.kafka.listener.concurrency=3`.
* Ensure `inventory.events` has ≥3 partitions (from Day 13).
* Add a log on listener start indicating thread name to observe parallelism.

#### Acceptance criteria

* Logs show processing from multiple threads (e.g., `container-0-C-1`, `container-0-C-2`).
* All events for the **same `sku`** remain ordered (same partition).

#### Suggested Import Path

```kotlin
import org.slf4j.LoggerFactory
```

#### Command to verify/run

```bash
./gradlew bootRun
# send a burst for two SKUs to see threads work in parallel
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-A&n=30"
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-B&n=30"
```

---

### Problem C — Induce failure & observe retry (no DLT yet)

#### Requirement

* Add a guard: if `reason == "RESERVE"` and `delta == -13`, throw `IllegalStateException("test failure")`.
* Configure a basic error handler/backoff:

  ```kotlin
  @Bean
  fun listenerFactory(factory: ConcurrentKafkaListenerContainerFactory<String, String>):
      ConcurrentKafkaListenerContainerFactory<String, String> {
      factory.setCommonErrorHandler(
          DefaultErrorHandler(
              ExponentialBackOffWithMaxRetries(3).apply {
                  initialInterval = 200; multiplier = 2.0; maxInterval = 2000
              }
          )
      )
      return factory
  }
  ```
* Send an event that triggers the failure: `reserve qty=13` (delta = -13).

#### Acceptance criteria

* The failing record is **retried** (see repeated logs) and finally **surfaces** as an error after retries.
* Subsequent records for other partitions still progress.

#### Suggested Import Path

```kotlin
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries
```

#### Command to verify/run

```bash
./gradlew :15-spring-kafka-listener-consumer:bootRun
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-ERR&qty=13"
```

---

### Problem D — Rebalance-safe processing (commit on revoke)

#### Requirement

* Add a `ConsumerRebalanceListener` via a container factory customizer:

  * On `onPartitionsRevoked`, log partitions and ensure any pending work is committed (manual ack mode already commits per record; just log here).
* Run **two instances** of the app (same group) and **start/stop** one to force rebalances while sending events.

#### Acceptance criteria

* Logs show `onPartitionsRevoked` and `onPartitionsAssigned` during instance churn.
* Minimal replay (only at-least-once duplicates around rebalance boundaries).

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener
```

#### Command to verify/run

```bash
# terminal 1
./gradlew :15-spring-kafka-listener-consumer:bootRun
# terminal 2 (same project)
./gradlew :15-spring-kafka-listener-consumer:bootRun --args='--server.port=8082'
# produce while starting/stopping terminal 2
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-REB&n=20"
```

---

### Problem E — Batch listener (throughput mode)

#### Requirement

* Create a **batch** container factory:

  ```kotlin
  @Bean(name = ["batchFactory"])
  fun batchFactory(cf: ConcurrentKafkaListenerContainerFactory<String, String>):
      ConcurrentKafkaListenerContainerFactory<String, String> {
      cf.isBatchListener = true
      // keep MANUAL or use BATCH ack-mode via yml
      return cf
  }
  ```
* Add a batch listener:

  ```kotlin
  @KafkaListener(topics = ["inventory.events"], containerFactory = "batchFactory")
  fun onBatch(records: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
      // parse, aggregate per SKU, update store, then ack
      ack.acknowledge()
  }
  ```
* Compare logs vs single-record listener under `/bulk` load.

#### Acceptance criteria

* Batch listener receives **lists** (log size).
* Throughput improves (fewer ack calls, fewer logs).

#### Suggested Import Path

```kotlin
import org.springframework.context.annotation.Bean
```

#### Command to verify/run

```bash
./gradlew bootRun
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-BATCH&n=200"
```

---

## Hints

* **Listener method signatures** you can use:

  ```kotlin
  fun onEvent(value: String)
  fun onEvent(rec: ConsumerRecord<String, String>)
  fun onEvent(rec: ConsumerRecord<String, String>, ack: Acknowledgment)
  fun onBatch(records: List<ConsumerRecord<String, String>>, ack: Acknowledgment)
  ```

* **Manual ack rule of thumb**: *process → ack*. If an exception is thrown **before** `ack()`, the record will be retried (at-least-once).

* **Concurrency** works only up to the number of partitions of `inventory.events` (we set it to 3 earlier).

* **Deserialization**: we’re parsing JSON by hand with Jackson now; later we’ll switch to `JsonDeserializer` and then Avro/Protobuf + Schema Registry.

* **Inspect consumer group**:

  ```bash
  docker exec -it kafka bash -lc \
    'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group inventory-consumer'
  ```

---

## Suggested Import Path (consolidated)

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
```

---

## Expected quick checks

* Manual ack: restart app ⇒ already-acked records don’t replay.
* Concurrency: logs show multiple container threads.
* Failure: retries logged, then error after max attempts.
* Rebalance: revoke/assign logs during instance churn.
* Batch: logs show `records.size()` > 1 and fewer commits.

This challenge gives you a **production-grade Spring listener** you can extend with **DLT, retries, and transactions** in the next topics.
