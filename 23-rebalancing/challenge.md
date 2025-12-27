## Topic: **Rebalancing → Add / Remove Consumer Instances Safely**

---

### Dependencies

* *(No new dependencies)*

  > Assume `spring-kafka` is already present.

---

## Problem A

### Requirement

Instrument a Kafka consumer to **log rebalance events** clearly.

Implement:

1. Topic:

   * `notifications`
   * **3 partitions**

2. Consumer:

   * `@KafkaListener(topics = ["notifications"], groupId = "notif-service")`
   * Artificial delay: `Thread.sleep(1000)` to make pauses visible

3. Custom `ConsumerRebalanceListener` via `ConcurrentKafkaListenerContainerFactory`:

   * Log when partitions are:

     * **revoked**
     * **assigned**

4. Log format (exact intent, wording can vary):

   ```
   🔴 revoked partitions=[...]
   🟢 assigned partitions=[...]
   ```

Files:

* `KafkaConfig.kt`
* `NotificationConsumer.kt`

---

### Acceptance criteria

* Starting **one instance** logs:

  * assigned partitions `[0,1,2]`
* Starting a **second instance**:

  * triggers `revoked` on instance A
  * triggers `assigned` on both A and B
* During rebalance:

  * message consumption **pauses briefly**
* After rebalance:

  * consumption resumes normally

---

### Suggested Import Path

```kotlin
// KafkaConfig.kt
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition

// NotificationConsumer.kt
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.ConsumerRecord
```

---

### Command to verify/run

```bash
# Create topic
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic notifications --partitions 3 --replication-factor 1

# Terminal A
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew 23-rebalancing:bootRun

# Terminal B (start AFTER A)
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew 23-rebalancing:bootRun
```

Expected logs (example):

```
🔴 revoked partitions=[notifications-0, notifications-1, notifications-2]
🟢 assigned partitions=[notifications-0, notifications-1]
```

---

## Problem B

### Requirement

Demonstrate **rebalance on consumer shutdown**.

Steps:

1. Run **two instances** (A & B)
2. Stop instance B
3. Observe logs on instance A

Expected behavior:

* Instance A logs:

  ```
  🔴 revoked partitions=[...]
  🟢 assigned partitions=[0,1,2]
  ```

---

### Acceptance criteria

* When instance B stops:

  * Instance A takes over **all partitions**
  * Consumption resumes without restarting A
* No data loss (messages continue from last committed offsets)

---

### Suggested Import Path

*(Same as Problem A)*

---

### Command to verify/run

```bash
# Stop instance B (Ctrl+C)
# Watch instance A logs
```

---

## Problem C

### Requirement

Simulate **rebalance caused by slow consumer**.

Modify consumer:

* Increase processing time:

  ```kotlin
  Thread.sleep(15000)
  ```
* Set consumer property:

  ```
  max.poll.interval.ms = 10000
  ```

Expected:

* Consumer exceeds poll interval
* Kafka considers it dead
* Rebalance is triggered

---

### Acceptance criteria

* Logs show:

  * partitions revoked from slow consumer
  * partitions assigned to another instance
* Slow consumer stops processing messages

---

### Suggested Import Path

```kotlin
// KafkaConfig.kt
import org.apache.kafka.clients.consumer.ConsumerConfig
```

---

### Command to verify/run

```bash
# Run two instances
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew 23-rebalancing:bootRun

# Terminal B (start AFTER A)
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew 23-rebalancing:bootRun

curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","channel":"EMAIL","message":"hello"}'
# Produce messages and observe rebalance after delay
```

---

## Problem D

### Requirement

Switch to **Cooperative Rebalancing** to reduce disruption.

Update consumer config:

```kotlin
ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG =
  "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
```

Re-run scaling experiment (add/remove instances).

---

### Acceptance criteria

* Rebalance logs show:

  * **partial partition revocation**, not full stop-the-world
* Consumption pauses are **shorter** than in eager rebalancing
* System remains responsive during scale-up/down

---

### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.ConsumerConfig
```

---

## Problem E

### Requirement

Create `REBALANCING.md` answering:

1. What triggers a Kafka rebalance?
2. Why consumption pauses during rebalance?
3. Difference between eager vs cooperative rebalancing
4. Why rebalances are dangerous in Kubernetes
5. How to minimize rebalance impact in production

---

### Acceptance criteria

* Answers are concise and correct
* Uses Kafka terminology correctly
* Includes at least one real-world scenario (e.g., rolling deploy)

---

## ✅ What You’ll Master After This Challenge

* Why Kafka consumers sometimes “freeze”
* How scaling triggers rebalances
* How slow consumers cause hidden rebalances
* Why **cooperative rebalancing** matters in production
* How to explain rebalance issues confidently in interviews
