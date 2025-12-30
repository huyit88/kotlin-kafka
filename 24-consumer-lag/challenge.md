## Topic: **Monitoring Lag → Detect Slow Consumers**

---

### Dependencies

* *(No new dependencies)*

  > Assume `spring-kafka`, `spring-boot-starter-web`, and Kafka CLI are already available.

---

## Problem A

### Requirement

Create a setup to **observe consumer lag via Kafka CLI**.

Implement:

1. Topic:

   * `notifications`
   * **3 partitions**

2. Consumer:

   * `@KafkaListener(topics = ["notifications"], groupId = "notif-service")`
   * Artificial delay:

     ```kotlin
     Thread.sleep(1500)
     ```

3. Producer:

   * Reuse `POST /notify`
   * Produce messages fast (no delay)

Goal:

* Make consumer **slower than producer** so lag accumulates.

---

### Acceptance criteria

* Running `kafka-consumer-groups --describe` shows:

  * `LAG > 0` for at least one partition
* Lag **increases** while producing
* Lag **decreases** after producer stops

---

### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.ConsumerRecord
```

---

### Command to verify/run

```bash
# Create topic
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic notifications --partitions 3 --replication-factor 1
'

# Run consumer
./gradlew :24-consumer-lag:bootRun

# Produce messages quickly
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u$i\",\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"msg\"}" > /dev/null
done

# Observe lag
docker exec -it kafka bash -lc '
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group notif-service \
  --describe
  '
```

Expected:

```
LAG > 0
```

---

## Problem B

### Requirement

Detect a **hot partition** using lag metrics.

Steps:

1. Modify producer:

   * Add endpoint `POST /notify/hot`
   * Always use:

     ```
     userId = "HOT"
     ```

2. Keep consumer slow (`Thread.sleep(1500)`).

3. Produce:

   * 50 HOT messages
   * 50 normal (random userId) messages

---

### Acceptance criteria

* `kafka-consumer-groups --describe` shows:

  * One partition with **significantly higher lag**
  * Other partitions with low or zero lag
* HOT messages always go to the **same partition**

---

### Suggested Import Path

```kotlin
// No new imports
```

---

### Command to verify/run

```bash
# HOT messages
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/notify/hot \
    -H "Content-Type: application/json" \
    -d "{\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"hot-$i\"}" > /dev/null
done

# Normal messages
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u$i\",\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"msg\"}" > /dev/null
done

kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group notif-service \
  --describe
```

Expected:

* One partition has much higher `LAG`.

---

## Problem C

### Requirement

Observe **lag spike during rebalancing**.

Steps:

1. Run **one consumer instance**
2. Produce messages continuously
3. Start **second consumer instance**
4. Observe lag behavior

---

### Acceptance criteria

* When second instance starts:

  * Consumption pauses briefly
  * Lag **spikes**
  * Then resumes and starts decreasing
* No message loss

---

### Suggested Import Path

```kotlin
// No new imports
```

---

### Command to verify/run

```bash
# Terminal A
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :24-consumer-lag:bootRun


# Produce continuously
while true; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d '{"userId":"u1","seq":1,"channel":"EMAIL","message":"loop"}' > /dev/null
done

# Terminal B (start later)
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :24-consumer-lag:bootRun


# Watch lag
docker exec -it kafka bash -lc '
watch -n 2 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group notif-service \
  --describe
  '
```

---

## Problem D

### Requirement

Compare **total lag vs max partition lag**.

Implement:

* Endpoint: `GET /debug/lag-explanation`
* Returns JSON:

  ```json
  {
    "totalLag": "sum of all partition lags",
    "maxPartitionLag": "largest single partition lag",
    "whyMaxMatters": "text explanation"
  }
  ```

> You may hardcode the explanation text; the key is conceptual clarity.

---

### Acceptance criteria

* Endpoint exists and returns all three fields
* Explanation clearly states:

  * why max partition lag is often more important

---

### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
```

---

### Command to verify/run

```bash
curl http://localhost:8080/debug/lag-explanation
```

Expected:

```json
{
  "totalLag": "...",
  "maxPartitionLag": "...",
  "whyMaxMatters": "..."
}
```

---

## Problem E

### Requirement

Create `LAG_MONITORING.md` answering:

1. What consumer lag is
2. Why lag is not always bad
3. Difference between increasing vs decreasing lag
4. Why max partition lag matters
5. Common real-world causes of lag
6. How teams alert on lag in production

---

### Acceptance criteria

* Uses correct Kafka terminology
* Clear, concise explanations
* At least one example involving rebalancing or hot partitions

---

## ✅ What You’ll Master After This Challenge

* How to **measure lag**
* How to **interpret lag correctly**
* How to detect:

  * slow consumers
  * hot partitions
  * rebalance impact
* How lag is used in **real production alerts**
