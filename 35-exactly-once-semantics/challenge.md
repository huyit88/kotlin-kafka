### Dependencies

* *(No new dependencies)*

---

## Problem A

### Requirement

Create a **minimal EOS checklist endpoint** in your app that validates (at runtime) the most important configs for exactly-once processing.

Implement:

* `GET /eos/check`

It must return JSON like:

```json
{
  "producer": {
    "idempotence": true,
    "transactionalId": "fraud-pipeline-tx-1"
  },
  "consumer": {
    "autoCommit": false,
    "isolationLevel": "read_committed"
  },
  "pipeline": {
    "usesSendOffsetsToTransaction": true
  }
}
```

Rules:

* `producer.transactionalId` must not be blank
* `consumer.isolationLevel` must be exactly `"read_committed"`
* `consumer.autoCommit` must be `false`

> For `usesSendOffsetsToTransaction`, you may hardcode `true` but you must include a comment pointing to the exact method where it is called.

### Acceptance criteria

* `curl http://localhost:8080/eos/check` returns HTTP 200
* JSON fields exist exactly as shown (values must reflect your app config)
* If any required config is missing, endpoint returns HTTP 500 with a meaningful error message

### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.core.env.Environment
```

### Command to verify/run

```bash
./gradlew 35-exactly-once-semantics:bootRun
curl -s http://localhost:8080/eos/check | jq .
```

---

## Problem B

### Requirement

Prove **no duplicates** under crash using an EOS pipeline.

Pipeline:

* Input topic: `payments`
* Output topic: `payments-validated`
* Group: `fraud-detector`
* Filter out records containing `"FRAUD"` (same as Day 34)

Add a crash switch:

* Env var: `CRASH_POINT`
* Behavior:

  * `CRASH_POINT=afterProduce` → throw after producing output but before `sendOffsetsToTransaction`
  * `CRASH_POINT=afterOffsets` → throw after `sendOffsetsToTransaction` but before commit
  * unset → normal processing

Log:

```
CRASH_POINT=<value>
```

### Acceptance criteria

* With `CRASH_POINT=afterProduce`:

  * app crashes
  * **no new output becomes visible** (read_committed consumer sees nothing)
* With `CRASH_POINT=afterOffsets`:

  * app crashes
  * **no new output becomes visible**
* After restarting with `CRASH_POINT` unset:

  * outputs appear
  * each valid input affects output **exactly once**
  * no duplicates in `payments-validated`

### Suggested Import Path

```kotlin
import java.lang.System.getenv
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.consumer.OffsetAndMetadata
```

### Command to verify/run

```bash
# Ensure topics exist
kafka-topics --bootstrap-server localhost:9092 --create --topic payments --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic payments-validated --partitions 3 --replication-factor 1

# Produce test input
kafka-console-producer --bootstrap-server localhost:9092 --topic payments \
  --property parse.key=true --property key.separator=:
# p10:PAYMENT:10
# p11:PAYMENT:11
# p12:FRAUD:999
# p13:PAYMENT:13

# Run with crash
CRASH_POINT=afterProduce ./gradlew 35-exactly-once-semantics:bootRun
# Expect crash

# Verify output (should be empty / unchanged)
kafka-console-consumer --bootstrap-server localhost:9092 --topic payments-validated --from-beginning

# Restart normally
./gradlew 35-exactly-once-semantics:bootRun

# Verify output contains p10, p11, p13 exactly once each
kafka-console-consumer --bootstrap-server localhost:9092 --topic payments-validated --from-beginning
```

---

## Problem C

### Requirement

Demonstrate **dirty reads vs read committed** during aborted transactions.

Implement two consumers for `payments-validated`:

1. Group: `validated-read-committed`

   * `isolation.level=read_committed`
   * Log:

     ```
     RC value=<value>
     ```
2. Group: `validated-read-uncommitted`

   * `isolation.level=read_uncommitted`
   * Log:

     ```
     RU value=<value>
     ```

Trigger an aborted transaction using `CRASH_POINT=afterProduce` or `afterOffsets`.

### Acceptance criteria

* `RC` consumer does **not** log records from aborted transactions
* `RU` consumer **may** log records from aborted transactions
* Behavior difference is visible in logs

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
```

### Command to verify/run

```bash
CRASH_POINT=afterOffsets ./gradlew 35-exactly-once-semantics:bootRun
# Observe logs:
# - RU may show values
# - RC shows none
```

---

## Problem D

### Requirement

Add a **dedupe safety net** at the application layer and explain why it is still useful.

Implement:

* In-memory `ConcurrentHashMap<String, Boolean>` named `processedPaymentIds`
* If you see the same `paymentId` twice in the consumer:

  * log:

    ```
    DEDUPE_DROP id=<paymentId>
    ```
  * do not produce output again

Add `DEDUPE.md` describing:

* why dedupe can still be needed even with EOS (hint: external side effects)
* limitations of in-memory dedupe

### Acceptance criteria

* If you manually produce duplicate inputs with same key:

  * app logs `DEDUPE_DROP ...` at least once
* `DEDUPE.md` exists with correct reasoning

### Suggested Import Path

```kotlin
import java.util.concurrent.ConcurrentHashMap
```

### Command to verify/run

```bash
# Produce duplicates
kafka-console-producer --bootstrap-server localhost:9092 --topic payments \
  --property parse.key=true --property key.separator=:
# p20:PAYMENT:20
# p20:PAYMENT:20

# Expected: one output, plus a DEDUPE_DROP log
```

---

## Problem E

### Requirement

Create `EOS.md` answering:

1. Define at-most-once / at-least-once / exactly-once
2. What pieces Kafka combines to achieve EOS
3. What `sendOffsetsToTransaction` guarantees
4. EOS scope (Kafka-only, not external DB/API)
5. Practical rollout strategy (consumer-first vs producer-first)
6. Common pitfalls (auto-commit on, read_uncommitted, missing transactional.id)

Include an ASCII diagram:

```
consume -> process -> produce -> offsets -> commit
```

### Acceptance criteria

* Clear, concise, interview-ready
* No incorrect claims about EOS being end-to-end outside Kafka
* Includes at least 2 pitfalls and how to detect them

### Command to verify/run

```bash
cat EOS.md
```
