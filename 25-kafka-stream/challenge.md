### Dependencies

* `implementation("org.apache.kafka:kafka-streams:3.7.0")`

---

### Problem A

#### Requirement

Build a **Kafka Streams chat forwarder**:

1. Topics:

* `chat-input` (3 partitions)
* `chat-output` (3 partitions)

2. Streams app:

* `application.id = "chat-forwarder"`
* Reads from `chat-input` as `KStream<String, String>`
* Writes to `chat-output` **unchanged**
* Key represents `roomId`

3. Logging:

* For every input record, log:

  ```
  IN key=<roomId> value=<message>
  ```
* For every output write, log:

  ```
  OUT key=<roomId> value=<message>
  ```

Files:

* `KafkaStreamConfig.kt` (props + topology)

#### Acceptance criteria

* Producing:

  ```
  room1:hello
  room2:hi
  room1:bye
  ```

  results in `chat-output` containing the same keys/values.
* Logs show matching `IN` and `OUT` lines for each record.

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import java.util.Properties
```

#### Command to verify/run

```bash
# Create topics
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic chat-input --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic chat-output --partitions 3 --replication-factor 1
'
# Run the streams app
./gradlew :25-kafka-stream:bootRun

# Produce keyed messages
docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic chat-input \
  --property parse.key=true --property key.separator=:
'
# Paste:
# room1:hello
# room2:hi
# room1:bye

# Consume output
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
  '
# Expected: room1  hello ... room2  hi ... room1  bye
```

---

### Problem B

#### Requirement

Add a **content filter** that blocks “toxic” messages:

* If `value` contains any of:

  * `"spam"`
  * `"hack"`
  * `"scam"`
* Then drop it (do not forward to output)
* Also write dropped messages to a separate topic:

  * `chat-moderation` (same partitions as input)

Logs:

* For forwarded:

  ```
  FORWARD key=<roomId> value=<message>
  ```
* For dropped:

  ```
  DROP key=<roomId> value=<message> reason=toxic
  ```

#### Acceptance criteria

* Message `room1:this is spam` never appears in `chat-output`
* It appears in `chat-moderation`
* Forwarding still works for normal messages

#### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.KStream
```

#### Command to verify/run

```bash
# Create moderation topic
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic chat-moderation --partitions 3 --replication-factor 1
'
# Produce:
# room1:this is spam
# room1:hello again

# Consume outputs
docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
'

docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-moderation \
  --from-beginning --property print.key=true
  '
# Expected: spam message only in moderation topic
```

---

### Problem C

#### Requirement

Prove **partition-based scaling** of Kafka Streams.

Run **two instances** of the Streams app with:

* Same `application.id = "chat-forwarder"`
* Different `server.port` (if using Spring Boot)
* Environment variable `INSTANCE_ID` included in logs:

  ```
  instance=<id> IN key=... partition=<p>
  instance=<id> OUT key=... partition=<p>
  ```

Implementation detail:

* Log partition using `record.metadata()`:

  * Use `peek` and include partition (via Processor API or by enabling record metadata access)
* If you don’t want Processor API yet:

  * Log at least instance + key + value and prove scaling by observing two processes both logging.

#### Acceptance criteria

* With 3 partitions and 2 instances:

  * both instances process some records
  * each key/partition is handled by only one instance at a time
* Stopping one instance triggers reassignment and the other continues.

#### Suggested Import Path

```kotlin
// Minimal
import java.lang.System.getenv
```

#### Command to verify/run

```bash
# Terminal A
INSTANCE_ID=A SERVER_PORT=8080 ./gradlew :25-kafka-stream:bootRun


# Terminal B
INSTANCE_ID=B SERVER_PORT=8081 ./gradlew :25-kafka-stream:bootRun

docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic chat-input \
  --property parse.key=true --property key.separator=:
'
# Produce many messages with varied keys:
# room1:msg1
# room2:msg2
# room3:msg3
# room4:msg4
```

Expected: logs show both A and B doing work.

---

### Problem D

#### Requirement

Add a **simple enrichment** stage:

* Input message format:

  ```
  <sender>|<text>
  ```

  Example:

  ```
  alice|hello
  ```

* Transform output value to:

  ```
  <sender>|<text>|ts=<epochMillis>
  ```

* Keep key as `roomId`

Example:

* Input:

  ```
  room1:alice|hello
  ```
* Output value:

  ```
  alice|hello|ts=1730000000000
  ```

#### Acceptance criteria

* Every forwarded output record ends with `|ts=<number>`
* Dropped toxic messages still go to `chat-moderation` (unenriched is fine, your choice—just be consistent)

#### Suggested Import Path

```kotlin
import java.time.Instant
```

#### Command to verify/run

```bash
# Produce
# room1:alice|hello
# room1:alice|this is scam

# Consume chat-output and verify ts appended
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
```

---

### Problem E

#### Requirement

Create `STREAMS_INTRO.md` answering:

1. Kafka Streams vs plain consumer+producer
2. What `KStream` represents
3. How scaling works (application.id + partitions)
4. What guarantees ordering and where it can break
5. What happens on restart (state, offsets)

#### Acceptance criteria

* Correct terminology
* Clear, concise answers (bullet points OK)
* Includes one diagram-like ascii flow for your topology

#### Command to verify/run

```bash
cat STREAMS_INTRO.md
```
