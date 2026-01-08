### Dependencies

* *(No new dependencies)*

  > Assume `spring-kafka` is already present and transactions are enabled via config.

---

## Problem A

### Requirement

Configure an **atomic consume → produce pipeline** for fraud filtering.

1. Topics:

   * `payments`
   * `payments-validated`
     (3 partitions each)

2. Consumer group:

   * `fraud-detector`

3. Consumer config:

   * `enable.auto.commit=false`
   * `isolation.level=read_committed`

4. Producer config:

   * `enable.idempotence=true`
   * `transactional.id=fraud-pipeline-tx-1`

5. Listener method:

   * Batch listener (`List<ConsumerRecord<...>>`)
   * Begin a producer transaction for each batch

Files:

* `KafkaConfig.kt`
* `FraudPipeline.kt`

---

### Acceptance criteria

* Application starts without errors
* Producer initializes transactions
* Consumer receives records in batches
* No offsets are auto-committed

---

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
```

---

### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic payments --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic payments-validated --partitions 3 --replication-factor 1

./gradlew 34:atomic-pipeline:bootRun
```

---

## Problem B

### Requirement

Implement **atomic processing logic**.

Processing rules:

* If record value contains `"FRAUD"` → do NOT forward
* Otherwise → forward to `payments-validated`
* For every processed record:

  * Track `offset + 1` per `(topic, partition)`

Atomic block must:

1. Produce all output records
2. Call `sendOffsetsToTransaction(offsets, "fraud-detector")`
3. Commit transaction

Log:

```
TX_BEGIN batchSize=<n>
TX_COMMIT offsets=<map>
TX_ABORT reason=<exception>
```

---

### Acceptance criteria

* For valid records:

  * Output appears in `payments-validated`
* For fraudulent records:

  * No output appears
* Offsets advance only when output is committed
* Logs show `TX_BEGIN` → `TX_COMMIT` for successful batches

---

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.consumer.OffsetAndMetadata
```

---

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic payments \
  --property parse.key=true --property key.separator=:
'
# Send:
# p1:PAYMENT:100
# p2:FRAUD:999
# p3:PAYMENT:200
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payments-validated --from-beginning
  '
```

Expected:

```
p1 PAYMENT:100
p3 PAYMENT:200
```

---

## Problem C

### Requirement

Prove **exactly-once behavior on crash**.

Steps:

1. Add a flag `failAfterProduce=true` that:

   * Throws an exception **after producing output**
   * But **before** calling `sendOffsetsToTransaction`
2. Restart the application
3. Re-run without failure

---

### Acceptance criteria

* During failure:

  * No output is visible to consumers
* After restart:

  * Records are reprocessed
  * Output appears **once only**
* No duplicate records in `payments-validated`

---

### Suggested Import Path

```kotlin
// No new imports
```

---

### Command to verify/run

```bash
# Trigger failure path
# (however you expose failAfterProduce, e.g. env var)

# Restart app
FAIL_AFTER_PRODUCE=true ./gradlew :34-atomic-pipeline:bootRun

# Consume output again
docker exec -it kafka bash lc '
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payments-validated --from-beginning
  '
```

---

## Problem D

### Requirement

Demonstrate **dirty-read prevention**.

1. Consumer A:

   * `isolation.level=read_committed`
2. Consumer B:

   * `isolation.level=read_uncommitted`
3. Trigger a failed transaction

Logs:

```
READ_COMMITTED value=<value>
DIRTY_READ value=<value>
```

---

### Acceptance criteria

* Dirty consumer **may see** aborted records
* Read-committed consumer **never sees** aborted records
* Difference is clearly visible in logs

---

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
```

---

### Command to verify/run

```bash
# Trigger a failing transaction
# Observe logs from both consumers
```

---

## Problem E

### Requirement

Create `ATOMIC_PIPELINES.md` answering:

1. Why atomic pipelines are needed
2. What `sendOffsetsToTransaction` does
3. Difference between at-least-once and exactly-once
4. What happens on crash mid-batch
5. When atomic pipelines are worth the cost

Include an ASCII flow:

```
consume -> process -> produce -> sendOffsets -> commit
```

---

### Acceptance criteria

* Uses correct Kafka terminology
* Clear, interview-ready explanations
* No conceptual mistakes

---

## ✅ What You’ll Master After This Challenge

* Exactly-once processing fundamentals
* Atomic offset + output commits
* Fraud/payment-grade Kafka pipelines
* One of the **hardest Kafka interview topics**
