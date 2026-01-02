### Dependencies

* `implementation("org.apache.kafka:kafka-streams:3.7.0")`

---

## Problem A

### Requirement

Build a **stateful Kafka Streams aggregation** that **counts chat messages per user**.

Implement:

1. Input topic: `chat-output` (3 partitions)

   * Key = `roomId`
   * Value format: `<sender>|<text>|ts=<epochMillis>`
2. Topology:

   * Re-key stream so **key = sender (userId)**
   * Group by key
   * `count()` messages per user
   * Materialize state store named `user-message-counts-store`
3. Output topic: `chat-user-counts` (3 partitions)

   * Key = `userId`
   * Value = `Long` count
4. Log every update:

   ```
   COUNT user=<userId> count=<count>
   ```

Files:

* `ChatAggregationTopology.kt`

### Acceptance criteria

* Sending messages:

  ```
  room1:alice|hi|ts=1
  room1:alice|again|ts=2
  room1:bob|yo|ts=3
  ```

  results in `chat-user-counts` emitting:

  * `alice -> 1`
  * `alice -> 2`
  * `bob -> 1`
* Logs show matching `COUNT` lines.

### Suggested Import Path

```kotlin
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
```

### Command to verify/run

```bash
# Create topics (skip if already exist)
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-output --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-user-counts --partitions 3 --replication-factor 1
'

# Run the streams app
./gradlew 27-kstream-aggregation:bootRun

# Produce test data
docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic chat-output \
  --property parse.key=true --property key.separator=:
  '
# Paste:
# room1:alice|hi|ts=1
# room1:alice|again|ts=2
# room1:bob|yo|ts=3

# Consume counts
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-user-counts \
  --from-beginning --property print.key=true
  '
```

---

## Problem B

### Requirement

Prove **repartitioning happens** when re-keying.

Implement:

1. Explicitly name the repartition topic by using:

   * `groupBy { key, value -> sender }` with `Grouped.with(...)`
2. Log a startup message describing the expected internal repartition topic name.

### Acceptance criteria

* Kafka topics list shows an **internal repartition topic** created by Streams (name contains `repartition`).
* Counts still work correctly.

### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.Grouped
```

### Command to verify/run

```bash
# List topics and observe internal repartition topic
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 --list | grep repartition
'
```

---

## Problem C

### Requirement

Add **windowed aggregation**: count messages **per user per 10 seconds**.

Implement:

1. Replace `count()` with:

   * Tumbling window of **10 seconds**
2. Output topic: `chat-user-counts-windowed`

   * Key = `Windowed<String>`
   * Value = `Long`
3. Log format:

   ```
   WINDOW_COUNT user=<userId> window=<start>-<end> count=<count>
   ```

### Acceptance criteria

* Messages sent within the same 10s window are counted together.
* Messages sent after 10s create a **new window** with count reset.

### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.TimeWindows
import java.time.Duration
```

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic chat-user-counts-windowed --partitions 3 --replication-factor 1
'
# Produce messages within 10s
# room1:alice|one|ts=...
# room1:alice|two|ts=...
docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic chat-output \
  --property parse.key=true --property key.separator=:
  '
# Wait >10s, then:
# room1:alice|three|ts=...
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic chat-user-counts-windowed --from-beginning \
  --property print.key=true --property key.separator=" -> "
  '
# Note: Windowed keys may appear as binary data, but the value (count) will be readable
```

---

## Problem D

### Requirement

Demonstrate **state restoration** after restart.

Steps:

1. Run the Streams app and produce messages for multiple users.
2. Stop the app.
3. Restart the app.

### Acceptance criteria

* After restart:

  * Counts **resume from previous values**
  * No reset to zero
* Logs indicate state restoration (INFO-level logs are sufficient).

### Suggested Import Path

```kotlin
// No additional imports required
```

### Command to verify/run

```bash
# Stop app (Ctrl+C)
# Restart
./gradlew 27-kstream-aggregation:bootRun

# Produce one more message
# room1:alice|after-restart|ts=...

# Expected: alice count increments (not reset)
```

---

## Problem E

### Requirement

Create `AGGREGATIONS.md` answering:

1. Why aggregations are stateful
2. Difference between `KStream` and `KTable`
3. Where Kafka Streams stores state
4. What a changelog topic is
5. Why re-keying causes repartitioning
6. Tradeoffs of windowed aggregations

### Acceptance criteria

* Correct Kafka Streams terminology
* Clear, concise explanations
* Includes one ASCII diagram showing:

  ```
  chat-output -> repartition -> state store -> chat-user-counts
  ```

### Command to verify/run

```bash
cat AGGREGATIONS.md
```
