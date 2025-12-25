### Problem A

#### Requirement

Build a **notification pipeline** that guarantees **per-user ordering** via **partition key = userId**.

Implement:

1. Topic: `notifications` with **3 partitions**
2. REST endpoint `POST /notify`

   * Body:

     ```json
     { "userId": "u1", "seq": 1, "channel": "EMAIL", "message": "m1" }
     ```
   * Producer sends to Kafka:

     * topic: `notifications`
     * **key = userId**
     * value = `"userId,seq,channel,message"`
3. Consumer listens with:

   * `groupId = "notif-service"`
   * Method signature accepts `ConsumerRecord<String, String>`
   * Logs:

     ```
     key=<userId> partition=<partition> seq=<seq>
     ```
4. Add endpoint `GET /debug/partition/{userId}`

   * Returns the partition number your producer would target for that `userId`
   * (Compute using the same hash logic Kafka uses for String keys: murmur2 + mod)

#### Acceptance criteria

* For a fixed `userId`, all consumed messages always show **the same partition**.
* For the same `userId`, `seq` values are printed **in increasing order** (no gaps, no inversions).
* `GET /debug/partition/u1` returns a stable integer in `[0..2]` and matches the partition seen in consumer logs for `u1`.

#### Suggested Import Path

```kotlin
// Producer + REST
import org.springframework.web.bind.annotation.*
import org.springframework.kafka.core.KafkaTemplate

// Consumer
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.ConsumerRecord

// Partition debug
import org.apache.kafka.common.utils.Utils
import org.apache.kafka.common.serialization.StringSerializer
```

#### Command to verify/run

```bash
# Create topic (3 partitions)
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic notifications --partitions 3 --replication-factor 1
'

# if topic exist, alter the partition only
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --alter --topic notifications --partitions 3 
'

./gradlew :22-partitioning-keys:bootRun

# Send interleaved events (per-user ordering should still hold)
curl -X POST http://localhost:8080/notify -H "Content-Type: application/json" \
  -d '{"userId":"u1","seq":1,"channel":"EMAIL","message":"m1"}'

curl -X POST http://localhost:8080/notify -H "Content-Type: application/json" \
  -d '{"userId":"u2","seq":1,"channel":"SMS","message":"x1"}'

curl -X POST http://localhost:8080/notify -H "Content-Type: application/json" \
  -d '{"userId":"u1","seq":2,"channel":"EMAIL","message":"m2"}'

# Debug partition mapping
curl http://localhost:8080/debug/partition/u1
# Expected: an integer 0..2 and matches consumer logs for u1
```

---

### Problem B

#### Requirement

**Break ordering intentionally** by removing the key.

Add a second endpoint:

* `POST /notify/no-key`

  * Produces the same message value
  * But sends with **null key**

    * `kafkaTemplate.send("notifications", null, value)` or send without key

Add a runtime validator:

* In the consumer, keep an in-memory map `lastSeqByUserId`
* If a consumed `seq` is **<= lastSeq**, log:

  ```
  ❌ OUT_OF_ORDER userId=<u> prev=<prev> now=<now> partition=<p>
  ```

#### Acceptance criteria

* When sending multiple events for the same user via `/notify/no-key`,
  at least one `OUT_OF_ORDER` log can be observed after enough messages.
* When sending via `/notify` (keyed), **no** `OUT_OF_ORDER` log occurs for that user.

#### Suggested Import Path

```kotlin
import java.util.concurrent.ConcurrentHashMap
```

#### Command to verify/run

```bash
./gradlew :22-partitioning-keys:bootRun

# Flood same user without key
for i in {1..30}; do
  curl -s -X POST http://localhost:8080/notify/no-key \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u1\",\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"m$i\"}" > /dev/null
done

# Expected in logs: at least one ❌ OUT_OF_ORDER for u1

# Flood same user WITH key (should not show OUT_OF_ORDER)
for i in {1..30}; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u1\",\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"m$i\"}" > /dev/null
done

# Expected: no ❌ OUT_OF_ORDER logs for keyed path
```

---

### Problem C

#### Requirement

Demonstrate **scaling + ordering together**.

Run **2 app instances** (same `groupId=notif-service`), and show:

* Each user stays on one partition
* Users split across instances due to partition assignment
* Ordering per user still holds

Implementation:

* Add env var `INSTANCE_ID` and include it in logs:

  ```
  instance=<id> key=<userId> partition=<p> seq=<seq>
  ```

#### Acceptance criteria

* With 2 instances and 3 partitions:

  * both instances process some messages
  * each partition belongs to only one instance
* For a given userId, all messages appear on:

  * same partition
  * same instance (because partition is owned by one instance)
* No `OUT_OF_ORDER` logs for keyed endpoint

#### Suggested Import Path

```kotlin
import java.lang.System.getenv
```

#### Command to verify/run

```bash
# Terminal A
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :22-partitioning-keys:bootRun


# Terminal B
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :22-partitioning-keys:bootRun


# Produce mixed users
for i in {1..15}; do
  curl -s -X POST http://localhost:8080/notify \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"u$i\",\"seq\":1,\"channel\":\"PUSH\",\"message\":\"hello\"}" > /dev/null
done

# Expected logs split across A and B; each user consistent per partition/instance.
```

---

### Problem D

#### Requirement

Simulate a **hot key** and observe the bottleneck.

* Add endpoint `POST /notify/hot`

  * Always uses `userId="HOT"`
  * Sends `seq` increasing
* Keep consumer delay `Thread.sleep(200)` to make throughput visible.
* Add endpoint `GET /metrics/hot` returning:

  * total processed count for userId="HOT"
  * processing rate (count / seconds since app start)

#### Acceptance criteria

* With 1 instance vs 2 instances:

  * HOT throughput does **not** improve significantly (still limited to 1 partition / 1 consumer)
* With mixed users (not hot):

  * total throughput improves when adding the second instance

#### Suggested Import Path

```kotlin
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds
```

#### Command to verify/run

```bash
./gradlew bootRun

# Produce HOT messages
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/notify/hot \
    -H "Content-Type: application/json" \
    -d "{\"seq\":$i,\"channel\":\"EMAIL\",\"message\":\"hot-$i\"}" > /dev/null
done

curl http://localhost:8080/metrics/hot
# Expected: count ~50, rate reflects single-partition limitation
```

---

### Problem E

#### Requirement

Write an interview-style explanation in `PARTITIONING_KEYS.md` answering:

1. Why Kafka ordering is only within a partition
2. How keys ensure per-user ordering
3. What happens if you increase partitions later
4. What a “hot partition” is and how to mitigate it
5. Why payments often use fewer partitions than notifications

#### Acceptance criteria

* Correct terminology
* Concise, confident explanations (bullet points OK)
* Includes at least one diagram-like ascii example showing keys → partitions

#### Command to verify/run
