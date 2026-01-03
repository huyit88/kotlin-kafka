### Dependencies

* `implementation("org.apache.kafka:kafka-streams:3.7.0")`
* `implementation("org.springframework.boot:spring-boot-starter-web")`

---

## Problem A

### Requirement

Build a **user presence pipeline using a KTable**.

Implement:

1. Input topic: `user-presence`

   * **Key** = `userId`
   * **Value** = `"ONLINE"` or `"OFFLINE"`
2. Streams topology:

   * Read `user-presence` as a **KTable**
   * Materialize state store named **`user-status-store`**
3. Optional output:

   * Convert KTable to stream
   * Write updates to topic `user-status-view`
4. Log every update:

   ```
   STATUS_UPDATE user=<userId> status=<status>
   ```

Files:

* `UserStatusTopology.kt`

### Acceptance criteria

* Producing:

  ```
  alice:ONLINE
  alice:OFFLINE
  ```

  results in:

  * `user-status-store["alice"] == "OFFLINE"`
* Logs show two `STATUS_UPDATE` entries for `alice`.

### Suggested Import Path

```kotlin
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
```

### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic user-presence --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic user-status-view --partitions 3 --replication-factor 1
'
./gradlew :28-kstream-ktable:bootRun

docker exec -it kafka bash -lc '
kafka-console-producer --bootstrap-server localhost:9092 --topic user-presence \
  --property parse.key=true --property key.separator=:
  '
# alice:ONLINE
# alice:OFFLINE
```

---

## Problem B

### Requirement

Expose the **current user status via REST** by querying the KTable state store.

Implement:

* Controller: `GET /status/{userId}`
* Query store:

  * Name: `user-status-store`
  * Type: `QueryableStoreTypes.keyValueStore()`
* Response:

  ```json
  { "userId": "alice", "status": "OFFLINE" }
  ```

Files:

* `UserStatusController.kt`

### Acceptance criteria

* After producing `alice:OFFLINE`,

  ```
  GET /status/alice
  ```

  returns `"OFFLINE"`.
* For unknown users, status is `null`.

### Suggested Import Path

```kotlin
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
```

### Command to verify/run

```bash
curl http://localhost:8080/status/alice
```

---

## Problem C

### Requirement

Demonstrate **tombstones (deletes)** in a KTable.

Steps:

1. Produce a **tombstone**:

   * Key = `userId`
   * Value = `null`
2. Observe:

   * Entry removed from `user-status-store`
   * A delete event appears in `user-status-view` (if enabled)

### Acceptance criteria

* After producing:

  ```
  alice:null
  ```
* `GET /status/alice` returns:

  ```json
  { "userId": "alice", "status": "unknown" }
  ```

### Suggested Import Path

```kotlin
// No additional imports required
```

### Command to verify/run

curl -X DELETE http://localhost:8080/status/alice

# alice:null

curl http://localhost:8080/status/alice
```

---

## Problem D

### Requirement

Contrast **KStream vs KTable behavior**.

Implement:

1. Additional topology:

   * Read `user-presence` as a **KStream**
   * Log every event:

     ```
     EVENT user=<userId> value=<status>
     ```
2. Run both topologies side-by-side.

### Acceptance criteria

* Producing:

  ```
  alice:ONLINE
  alice:ONLINE
  alice:OFFLINE
  ```
* Logs show:

  * **3 events** from KStream
  * **2 updates** in KTable (ONLINE → OFFLINE)
* REST query returns only the latest state.

### Suggested Import Path

```kotlin
import org.apache.kafka.streams.kstream.KStream
```

### Command to verify/run

```bash
# Produce repeated events
# alice:ONLINE
# alice:ONLINE
# alice:OFFLINE

curl http://localhost:8080/status/alice
# Expected: OFFLINE
```

---

## Problem E

### Requirement

Create `KSTREAM_VS_KTABLE.md` answering:

1. What is a KStream?
2. What is a KTable?
3. Event log vs state table mental model
4. What a tombstone is and why it matters
5. Real-world use cases for each
6. Why KTable is powerful for joins and lookups

### Acceptance criteria

* Uses correct Kafka Streams terminology
* Includes a comparison table
* Includes one ASCII diagram showing:

  ```
  user-presence -> KTable(user-status-store)
  ```
