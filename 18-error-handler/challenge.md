### Dependencies

* `org.springframework.boot:spring-boot-starter-actuator`
* *(If your module doesn’t already expose REST)* `org.springframework.boot:spring-boot-starter-web`

---

### Problem A

#### Requirement

Implement a **custom `DefaultErrorHandler`** that:

* Retries a failing record **2 times** with **2s backoff**.
* After retries, **publishes to a DLT** named `<originalTopic>.DLT` using `DeadLetterPublishingRecoverer`.
* Increments **Micrometer counters**:

  * `kafka.errors.retried` (for each handled retry attempt)
  * `kafka.errors.dlt` (when sent to DLT)

Create:

* `KafkaErrorConfig.kt` (config + error handler bean)
* `LogConsumer.kt` (`@KafkaListener` on topic `my-topic`; throw `RuntimeException` if value contains `"fail"`)
* `LogProducerController.kt` (`POST /produce/{msg}` to send to `my-topic`)
* `ErrorStatsController.kt` (`GET /stats/errors` returns the two counters)

#### Acceptance criteria

* Sending `fail` triggers **2 retries**, then message is produced to `my-topic.DLT`.
* `GET /stats/errors` shows `kafka.errors.dlt >= 1` after the failing call.
* Normal messages (`hello`) are consumed once and **not** retried.

#### Suggested Import Path

```kotlin
// KafkaErrorConfig.kt
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.util.backoff.FixedBackOff
import io.micrometer.core.instrument.MeterRegistry
```

```kotlin
// LogConsumer.kt
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
```

```kotlin
// LogProducerController.kt
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```

```kotlin
// ErrorStatsController.kt
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
```

#### Command to verify/run

```bash
# 1) Run the app
./gradlew :18-error-handler:bootRun

# 2) Produce a good message
curl -X POST http://localhost:8080/produce/hello

# 3) Produce a failing message (will retry then go to DLT)
curl -X POST http://localhost:8080/produce/fail

# 4) Check counters (DLT should be >= 1 after step 3)
curl http://localhost:8080/stats/errors
# Expected (example):
# {"retried":2,"dlt":1}
```

---

### Problem B

#### Requirement

Handle **deserialization errors** without killing the container:

* Switch consumer value deserializer to `ErrorHandlingDeserializer` wrapping `StringDeserializer`.
* Add a secondary listener `@KafkaListener(topics = ["my-topic"], errorHandler = ...)` OR rely on the global `DefaultErrorHandler`.
* Ensure deserialization failures are **sent to DLT** with original bytes in header (`KafkaHeaders.DLT_EXCEPTION_FQCN`, `..._CAUSE_FQCN`, `..._EXCEPTION_MESSAGE`, `..._ORIGINAL_TOPIC`, `..._ORIGINAL_PARTITION`, `..._ORIGINAL_OFFSET`, `..._ORIGINAL_VALUE`).
* Expose `GET /stats/headers` that returns whether above headers were seen in the last DLT record.

Create:

* Update `KafkaErrorConfig.kt` to use:

  * `ErrorHandlingDeserializer` for `VALUE_DESERIALIZER_CLASS_CONFIG`
  * `StringDeserializer.VALUE_DESERIALIZER_CLASS` as `spring.deserializer.value.delegate.class`
* `DltProbeService.kt` that consumes `my-topic.DLT` with a lightweight consumer (manual poll) only when `/stats/headers` is requested, reads **latest record**, and extracts DLT headers.

#### Acceptance criteria

* Posting **binary garbage** (simulate by base64 garbage string) to producer still results in a **DLT record**.
* `GET /stats/headers` returns **all listed DLT headers** non-empty.

#### Suggested Import Path

```kotlin
// KafkaErrorConfig.kt additions
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
```

```kotlin
// DltProbeService.kt
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.stereotype.Service
import java.time.Duration
```

#### Command to verify/run

```bash
# 1) Run the app (if not already)
./gradlew :18-error-handler:bootRun

# 2) Send an invalid message (simulating bad bytes; still goes through producer)
curl -X POST http://localhost:8080/produce/invalid-binary-data

# 3) Check DLT headers surfaced by the probe
curl http://localhost:8080/stats/headers
# Expected keys present:
# DLT_EXCEPTION_FQCN, DLT_EXCEPTION_MESSAGE, DLT_ORIGINAL_TOPIC, DLT_ORIGINAL_VALUE, ...
```

---

### Problem C

#### Requirement

Add **exception classification**:

* **Retryable:** `RuntimeException`, `IllegalStateException`
* **Not retryable:** `IllegalArgumentException`
* For not-retryable, **skip retries** and publish directly to DLT.
* Expose `POST /produce/bad-arg` that sends a message which triggers an `IllegalArgumentException` in the consumer.

#### Acceptance criteria

* Calling `/produce/bad-arg` results in **0 retries** and **1 DLT** increment.
* Calling `/produce/fail` (runtime) still does **2 retries** then DLT.

#### Suggested Import Path

```kotlin
// In KafkaErrorConfig.kt where the handler is built
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
```

#### Command to verify/run

```bash
# Not-retryable path
curl -X POST http://localhost:8080/produce/bad-arg
curl http://localhost:8080/stats/errors
# Expected: retried unchanged; dlt incremented by 1

# Retryable path
curl -X POST http://localhost:8080/produce/fail
curl http://localhost:8080/stats/errors
# Expected: retried +2; dlt +1
```

---

### Problem D

#### Requirement

Customize **DLT partition mapping + header propagation**:

* Use a `DeadLetterPublishingRecoverer` mapping lambda: `TopicPartition("${record.topic()}.DLT", record.partition())` to preserve partition.
* Before publishing to DLT, **copy selected headers** from the original record and add a custom header `x-error-category` with values:

  * `"deserialization"` when `DeserializationException`
  * `"business"` for business exceptions
  * `"unexpected"` otherwise
* Expose `GET /stats/last-dlt` to return the last DLT record’s:

  * topic, partition, offset
  * key, value (string-decoded when possible)
  * headers (including `x-error-category`)

#### Acceptance criteria

* After sending `/produce/fail`, `GET /stats/last-dlt` shows:

  * Topic `my-topic.DLT`
  * Same partition number as produced message
  * Header `x-error-category=business`
* After sending invalid/garbage payload, `x-error-category=deserialization`.

#### Suggested Import Path

```kotlin
// Where building the recoverer
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.clients.consumer.ConsumerRecord
```

#### Command to verify/run

```bash
# 1) Business failure → DLT with x-error-category=business
curl -X POST http://localhost:8080/produce/fail
curl http://localhost:8080/stats/last-dlt
# Expected JSON includes: "topic":"my-topic.DLT", "x-error-category":"business"

# 2) Deserialization → DLT with x-error-category=deserialization
curl -X POST http://localhost:8080/produce/invalid-binary-data
curl http://localhost:8080/stats/last-dlt
# Expected JSON includes: "x-error-category":"deserialization"
```

---

### Problem E (Optional, for extra points)

#### Requirement

Add **exponential backoff** based on failure count:

* First retry after **500ms**, second after **2s** (approx).
* Stop retrying if **record header** `x-no-retry=true`.
* Expose `POST /produce/noretry/{msg}` that attaches header `x-no-retry=true`.

#### Acceptance criteria

* `/produce/noretry/fail` results in **0 retries** → DLT.
* `/produce/fail` still follows **2 attempts** with increasing delays.

#### Suggested Import Path

```kotlin
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.ExponentialBackOff
```

#### Command to verify/run

```bash
./gradlew bootRun

# No-retry via header
curl -X POST http://localhost:8080/produce/noretry/fail
curl http://localhost:8080/stats/errors
# Expected: retried unchanged; dlt +1

# Normal business failure (will retry with exponential backoff)
curl -X POST http://localhost:8080/produce/fail
curl http://localhost:8080/stats/errors
# Expected: retried increased by ~2; dlt +1
```

---

**Notes & Hints**

* Ensure your consumer container factory applies `setCommonErrorHandler(customHandler())`.
* For `ErrorHandlingDeserializer`, set:

  * `ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG = ErrorHandlingDeserializer::class.java`
  * `ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS = StringDeserializer::class.java`
* Consider setting `max.poll.interval.ms` and `max.poll.records` conservatively during testing to make retries observable.
* Keep topics pre-created or **auto-create enabled** (dev only).
