### Dependencies

Only include **new** dependencies required for this challenge:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

*(Assuming `spring-kafka` & `spring-web` were already added in previous days.)*

---

# Problem A — Basic DLT Routing

### Requirement

Implement a **payments pipeline** where:

* Topic: `payments`
* If consumer receives the message `"fail"`, it throws a `RuntimeException`.
* The consumer uses a **DefaultErrorHandler** configured to:

  * Retry **2 times** with **1s backoff**
  * After retries fail → publish record into **`payments.DLT`**
* Implement:

  * `PaymentProducerController.kt`
  * `PaymentConsumer.kt`
  * `KafkaErrorConfig.kt`
  * `DltMonitor.kt` (consumer on `payments.DLT`)

### Acceptance criteria

* Sending `/pay/100` → processed once, no retries, no DLT.
* Sending `/pay/fail` → consumer logs 2 retries → message appears in `payments.DLT`.
* `DltMonitor.kt` prints:

  ```
  ⚠️ DLT received: fail
  ```

### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.util.backoff.FixedBackOff
```

### Command to verify/run

```bash
./gradlew :19-dead-letter-topic:bootRun

# Good payment
curl -X POST http://localhost:8080/pay/100

# Failing payment → routed to DLT
curl -X POST http://localhost:8080/pay/fail
```

---

# Problem B — Include Error Metadata (Headers) in DLT Messages

### Requirement

Enhance the `DeadLetterPublishingRecoverer` to attach custom headers:

* `x-error-type`: one of

  * `"runtime"` for `RuntimeException`
  * `"unknown"` otherwise
* `x-original-value`: original message value (String → bytes)

Update your recoverer:

* Add headers before publishing to DLT.
* Implement `DltInspectorController.kt`:

  * `GET /dlt/last`
  * Reads **latest DLT record** via a simple KafkaConsumer
  * Returns JSON:

    ```json
    {
      "value": "...",
      "headers": {
        "x-error-type": "...",
        "x-original-value": "..."
      }
    }
    ```

### Acceptance criteria

* After sending `/pay/fail`, calling `/dlt/last` returns:

  ```json
  {
    "value": "fail",
    "headers": {
        "x-error-type": "runtime",
        "x-original-value": "fail"
    }
  }
  ```

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.header.internals.RecordHeader
import java.time.Duration
```

### Command to verify/run

```bash
# Produce a failure
curl -X POST http://localhost:8080/pay/fail

# Read last DLT message
curl http://localhost:8080/dlt/last
```

Expected JSON contains your added headers.

---

# Problem C — Non-Retryable Exception → Immediate DLT

### Requirement

Modify your consumer:

* When message == `"reject"`, throw `IllegalArgumentException`.

Modify your error handler:

* Mark `IllegalArgumentException` as **NOT retryable**
* Retry only `RuntimeException`

Behavior:

* `"fail"` → retry twice → DLT
* `"reject"` → **no retries**, directly DLT

Create new endpoint:

* `POST /pay/reject` → sends `"reject"` to Kafka

### Acceptance criteria

* `/pay/reject` logs **0 retries**
* `payments.DLT` contains `"reject"`
* `/dlt/last` shows `"x-error-type": "runtime"` (or `"unknown"` depending on your mapping)
* `/pay/fail` still triggers retries

### Suggested Import Path

```kotlin
import java.lang.IllegalArgumentException
```

### Command to verify/run

```bash
# Non-retryable path
curl -X POST http://localhost:8080/pay/reject

# Retryable path
curl -X POST http://localhost:8080/pay/fail
```

---

# Problem D — Partition Preservation + Categorized Headers

### Requirement

Modify your `CustomDeadLetterRecoverer`:

* Publish failed message to:

  ```
  TopicPartition("${record.topic()}.DLT", record.partition())
  ```
* Add header:

  * `x-error-category` with values:

    * `"business"` when message == `"fail"`
    * `"validation"` when message == `"reject"`
    * `"unexpected"` for all others

Modify `/dlt/last` response to include:

* `topic`
* `partition`
* `offset`
* `headers`

Example output:

```json
{
  "topic": "payments.DLT",
  "partition": 0,
  "offset": 42,
  "value": "fail",
  "headers": {
    "x-error-category": "business"
  }
}
```

### Acceptance criteria

* After `/pay/fail`, `x-error-category=business`
* After `/pay/reject`, `x-error-category=validation`
* Partition in DLT equals original partition

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerRecord
```

### Command to verify/run

```bash
curl -X POST http://localhost:8080/pay/fail
curl http://localhost:8080/dlt/last

curl -X POST http://localhost:8080/pay/reject
curl http://localhost:8080/dlt/last
```

---

# Problem E — Replay from DLT Back to Main Topic (Advanced)

### Requirement

Implement DLT replay via:

* Controller: `/dlt/replay`
* Logic:

  * Consume **all records** from `payments.DLT`
  * Re-publish them to `payments` with original key/value
  * Add header `x-replayed=true`
  * Commit offsets manually to avoid duplicates

Files:

* `DltReplayService.kt`
* `ReplayController.kt`

### Acceptance criteria

1. Send `/pay/fail` → goes to DLT
2. Run `POST /dlt/replay`
3. Verify:

   * Message appears **again** on original consumer
   * Debug log:

     ```
     Replayed from DLT: fail
     ```
   * Replay adds header `x-replayed=true`
4. No duplicates in DLT (offsets committed)

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
```

### Command to verify/run

```bash
# Trigger a failure
curl -X POST http://localhost:8080/pay/fail

# Replay to main topic
curl -X POST http://localhost:8080/dlt/replay
```

Expected: consumer processes `"fail"` again.
