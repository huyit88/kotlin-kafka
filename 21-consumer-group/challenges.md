
## Topic: **Consumer Groups → Scale Notification Service**

---

### Dependencies

* *(No new dependencies)*

  > Assume `spring-kafka` and `spring-boot-starter-web` already exist in the root.

---

## Problem A — Prove Consumer Group Scaling

### Requirement

Build a **notification pipeline** that demonstrates **horizontal scaling with consumer groups**.

Implement:

1. Topic: `notifications`

   * **3 partitions**
   * Key = `userId`

2. Producer:

   * Endpoint: `POST /notify`
   * Payload:

     ```json
     {
       "userId": "u1",
       "channel": "EMAIL",
       "message": "hello"
     }
     ```
   * Kafka:

     * topic = `notifications`
     * key = `userId`
     * value = `"userId,channel,message"`

3. Consumer:

   * `@KafkaListener`
   * `groupId = "notification-service"`
   * Artificial delay: `Thread.sleep(800)`
   * Log format:

     ```
     instance=<INSTANCE_ID> partition=<partition> message=<value>
     ```

4. Instance identification:

   * Read env var `INSTANCE_ID`
   * Default to `"local-1"` if missing

### Acceptance criteria

* Running **2 app instances**:

  * Messages are processed by **both instances**
  * Each partition is handled by **only one instance**
* With **3 partitions**:

  * Max **3 active consumers**
  * Extra instances stay idle
* Logs clearly show:

  * instance id
  * partition number
  * processed message

### Suggested Import Path

```kotlin
// Producer
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.kafka.core.KafkaTemplate

// Consumer
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.ConsumerRecord

// Config / env
import org.springframework.beans.factory.annotation.Value
```

### Command to verify/run

```bash
# Create topic
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic notifications --partitions 3 --replication-factor 1
'
# Terminal A
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :21-consumer-group:bootRun

# Terminal B
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :21-consumer-group:bootRun

# Produce messages
curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","channel":"EMAIL","message":"hello"}'

curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u2","channel":"SMS","message":"hi"}'

curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u5","channel":"EMAIL","message":"hello"}'
```

Expected logs (example):

```
instance=A partition=0 message=u1,EMAIL,hello
instance=B partition=1 message=u2,SMS,hi
```

---

## Problem B — Single Partition Limitation (Important Insight)

### Requirement

Demonstrate that **scaling is limited by partition count**.

1. Create topic:

   ```
   notifications-single
   ```

   with **1 partition**

2. Reuse the same producer & consumer:

   * Same groupId: `notification-service`
   * Topic switched to `notifications-single`

3. Run **2 instances** again

### Acceptance criteria

* Only **one instance** processes messages
* Other instance remains idle
* After killing the active instance:

  * The idle instance takes over
  * Consumption resumes automatically

### Suggested Import Path

*(Same as Problem A)*

### Command to verify/run

```bash
# Create single-partition topic
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic notifications-single --partitions 1 --replication-factor 1
'

# Run two instances
KAFKA_TOPIC_NOTIFICATIONS=notifications-single INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :21-consumer-group:bootRun

KAFKA_TOPIC_NOTIFICATIONS=notifications-single INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :21-consumer-group:bootRun

# Produce messages
curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","channel":"EMAIL","message":"one-partition"}'
```

Expected:

```
instance=A partition=0 message=...
```

Kill instance A → instance B starts consuming.

---

## Problem C — Multiple Consumer Groups (Fan-out)

### Requirement

Add a **second consumer group** to simulate fan-out.

Implement:

* New consumer:

  * `groupId = "audit-service"`
  * Logs:

    ```
    [AUDIT] partition=<partition> message=<value>
    ```

* Both consumers listen to:

  ```
  notifications
  ```

### Acceptance criteria

* Each message is:

  * Processed **once** by `notification-service`
  * Processed **once** by `audit-service`
* Offsets are independent
* Logs show both pipelines working

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
```

### Command to verify/run

```bash
# Run two instances
KAFKA_TOPIC_NOTIFICATIONS=notifications-single INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :21-consumer-group:bootRun

SPRING_KAFKA_CONSUMER_GROUP_ID=audit-service KAFKA_TOPIC_NOTIFICATIONS=notifications-single INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :21-consumer-group:bootRun

curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u3","channel":"PUSH","message":"fanout"}'
```

Expected logs:

```
instance=A partition=2 message=...
[AUDIT] partition=2 message=...
```

---

## Problem D — Throughput Scaling Experiment (Optional but Recommended)

### Requirement

Measure throughput difference between:

1. **1 consumer instance**
2. **2 consumer instances**

Steps:

* Send **50 messages**
* Measure total processing time
* Keep `Thread.sleep(800)` in consumer

### Acceptance criteria

* With 1 instance:

  * ~40 seconds total (50 × 0.8s)
* With 2 instances:

  * ~20 seconds total
* With 3 instances (3 partitions):

  * ~14 seconds total

### Command to verify/run

```bash
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u$i\",\"channel\":\"EMAIL\",\"message\":\"msg-$i\"}"
done
```

---

## Problem E — Explain It (Interview Simulation)

### Requirement

Create a markdown file `SCALING.md` answering:

1. Why extra consumers don’t help with 1 partition
2. Why teams still run multiple instances
3. How partitions + consumer groups enable scaling
4. What limits max throughput
5. How this applies to payments vs notifications

### Acceptance criteria

* Clear, concise answers
* Uses correct Kafka terminology
* Could be spoken aloud in an interview

---

## ✅ What You’ll Master After This Challenge

* Why **partitions define throughput**
* Why **consumer groups define isolation**
* How **real systems scale safely**
* How to **explain Kafka scaling confidently in interviews**
