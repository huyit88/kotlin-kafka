### Problem A

#### Requirement

Implement a **stateless Kafka Streams topology** that:

* Reads from `chat-input` as `KStream<String, String>`
* Writes to `chat-output`
* Logs every record:

  ```
  IN key=<roomId> value=<value>
  OUT key=<roomId> value=<value>
  ```

Message conventions:

* Key = `roomId`
* Value format: `<sender>|<text>`

Files:

* `ChatStatelessTopology.kt`

Topics:

* `chat-input` (3 partitions)
* `chat-output` (3 partitions)

#### Acceptance criteria

* Producing `room1:alice|hello` to `chat-input` results in the same record (same key/value) appearing in `chat-output`.
* Logs show an `IN` line and a corresponding `OUT` line for the same record.

#### Suggested Import Path

```kotlin
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
```

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-input --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-output --partitions 3 --replication-factor 1
'
./gradlew 26-kstream-stateless-ops:bootRun

docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic chat-input \
  --property parse.key=true --property key.separator=:
'
# Paste:
# room1:alice|hello

docker exec -it kafka bash -lc '
kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
  '
# Expected: room1  alice|hello
```

---

### Problem B

#### Requirement

Add a **profanity/toxic filter** (stateless):

* Drop any message whose `text` contains any of:

  * `"spam"`, `"scam"`, `"hack"` (case-insensitive)
* Dropped messages must be written to topic `chat-moderation`
* Forwarded messages continue to `chat-output`

Logs:

* Forwarded:

  ```
  FORWARD key=<roomId> value=<value>
  ```
* Dropped:

  ```
  DROP key=<roomId> value=<value> reason=toxic
  ```

Topics:

* `chat-moderation` (3 partitions)

#### Acceptance criteria

* `room1:alice|this is spam` does **not** appear in `chat-output`
* It **does** appear in `chat-moderation`
* Normal messages still appear in `chat-output`

#### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.Produced
```

#### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-moderation --partitions 3 --replication-factor 1

# Produce:
# room1:alice|this is spam
# room1:alice|hello

kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
# Expected: no "spam" message

kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-moderation \
  --from-beginning --property print.key=true
# Expected: contains the "spam" message
```

---

### Problem C

#### Requirement

Add **stateless enrichment**:

* For forwarded messages only (not moderation),
* Append timestamp:

  ```
  <sender>|<text>|ts=<epochMillis>
  ```

Example:

* Input: `room1:alice|hello`
* Output in `chat-output`: `alice|hello|ts=1730000000000`

#### Acceptance criteria

* All records in `chat-output` end with `|ts=<number>`
* Records in `chat-moderation` may be unenriched (OK) OR enriched (OK), but must be consistent with your design and documented in a comment.

#### Suggested Import Path

```kotlin
import java.lang.System.currentTimeMillis
```

#### Command to verify/run

```bash
# Produce:
# room1:alice|hello

kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
# Expected: alice|hello|ts=<number>
```

---

### Problem D

#### Requirement

Add **branching** (stateless routing):

Rules:

* If `sender == "system"` → route to topic `chat-system`
* Otherwise → route to `chat-output`

Topics:

* `chat-system` (3 partitions)

Log:

* System route:

  ```
  ROUTE_SYSTEM key=<roomId> value=<value>
  ```
* User route:

  ```
  ROUTE_USER key=<roomId> value=<value>
  ```

#### Acceptance criteria

* `room1:system|user joined` appears in `chat-system` only
* `room1:alice|hello` appears in `chat-output` only

#### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.KStream
```

#### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9092 --create --topic chat-system --partitions 3 --replication-factor 1

# Produce:
# room1:system|user joined
# room1:alice|hello

kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-system \
  --from-beginning --property print.key=true
# Expected: system message only

kafka-console-consumer --bootstrap-server localhost:9092 --topic chat-output \
  --from-beginning --property print.key=true
# Expected: user message only
```

---

### Problem E

#### Requirement

Create `STATELESS_OPS.md` answering:

1. What “stateless” means in Kafka Streams
2. Why stateless ops scale easily
3. Common stateless operators (`filter`, `mapValues`, `branch`, `peek`)
4. Why ordering is preserved
5. What changes when you move to aggregations (stateful)

#### Acceptance criteria

* Uses correct Kafka Streams terminology
* Clear and concise (bullets OK)
* Includes one ASCII diagram of your topology

#### Command to verify/run

```bash
cat STATELESS_OPS.md
```
