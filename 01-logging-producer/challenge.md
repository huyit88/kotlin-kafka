### Dependencies

* `implementation("org.apache.kafka:kafka-clients:3.7.0")`
* `testImplementation("org.testcontainers:kafka:1.20.1")`
* `testImplementation("org.testcontainers:junit-jupiter:1.20.1")`
* `testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")`
* *(For Problem B only)* `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")`

> Notes:
>
> * Enable JUnit 5 in Gradle:
>   `tasks.test { useJUnitPlatform() }`
> * Kotlin/JVM target 17+ recommended.

---

### Problem A

#### Requirement

Implement a minimal **logging producer** that publishes log lines to Kafka topic **`logs`**.

* Create `LogProducer.kt` with:

  * Producer config: `bootstrap.servers` (from env `KAFKA_BOOTSTRAP`), `key.serializer=StringSerializer`, `value.serializer=StringSerializer`, `acks=all`, `retries=3`, `linger.ms=0`.
  * Function `sendLog(app: String, line: String): RecordMetadata` → produces to topic `logs` with key=`app`, value=`line`.
  * Graceful `close()`.

* Create an **integration test** `LogProducerIT.kt` using **Testcontainers Kafka**:

  * Start a Kafka container.
  * Produce **100** log lines with keys `"app-1"`.
  * Consume from `logs` using a simple Kafka **consumer** in the test and assert **100** messages received **in order per partition** (single partition in Testcontainers by default).

#### Acceptance criteria

* Test passes and prints Kafka bootstrap like `PLAINTEXT://...`.
* Exactly **100** records are received from `logs`.
* For a given partition, the **offsets are strictly increasing** without gaps.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
```

#### Command to verify/run

```bash
# 1) Run only this test
./gradlew test --tests LogProducerIT

# Expected
# BUILD SUCCESSFUL and test report shows 1 passed, 0 failed.
```

---

### Problem B

#### Requirement

Send **structured log events** as JSON with a severity header.

* Add data class:

  ```kotlin
  @kotlinx.serialization.Serializable
  data class LogEvent(
      val app: String,
      val level: String,     // e.g., INFO/WARN/ERROR
      val message: String,
      val tsEpochMs: Long
  )
  ```
* In `JsonLogProducer.kt`:

  * Use `kotlinx.serialization.json.Json` to encode `LogEvent` as a JSON **string** (still `StringSerializer` for value).
  * Add Kafka **header**: `"level"` set to the event level.
  * Function `send(event: LogEvent): RecordMetadata`.
* Test `JsonLogProducerIT.kt`:

  * Produce 3 events with different levels.
  * Consume and assert:

    * Header `"level"` matches `event.level`.
    * Parsed JSON contains the same fields/values.

#### Acceptance criteria

* Test passes with **3** consumed events.
* Each record has header `"level"` equal to the event’s level.
* JSON round-trip verifies equality for all fields.

#### Suggested Import Path

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
```

#### Command to verify/run

```bash
./gradlew test --tests JsonLogProducerIT

# Expected
# BUILD SUCCESSFUL; test report shows headers asserted and JSON equality verified.
```

---

### Problem C

#### Requirement

Introduce **throughput-friendly batching** without changing correctness.

* Extend `LogProducer` → `BatchedLogProducer` with:

  * Config: `batch.size=32768` (32KB), `linger.ms=20`, `compression.type=snappy`.
  * Method `sendBatch(app: String, lines: List<String>)` that sends N lines and finally calls `flush()`.
* Test `BatchedLogProducerIT.kt`:

  * Produce **1000** log lines.
  * Consume and assert **1000** messages received.
  * Record the wall-clock time for `sendBatch` (not a strict perf test) and assert it completes **under 10 seconds** on a typical dev machine (generous bound to avoid flakes).

#### Acceptance criteria

* Test passes and reports **1000** messages.
* `sendBatch` completes under **10s**.
* No duplicates or missing messages observed (count = 1000).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
```

#### Command to verify/run

```bash
./gradlew test --tests BatchedLogProducerIT

# Expected
# BUILD SUCCESSFUL; count = 1000 asserted, duration < 10s.
```

---

### Problem D (Optional)

#### Requirement

Add **graceful shutdown** and **SIGTERM** handling for a small CLI.

* File `LogCli.kt`:

  * Reads `app` from `--app=<name>` arg (default `app-cli`).
  * Reads lines from `stdin` and streams to `logs` via `BatchedLogProducer`.
  * On `SIGTERM`/`Ctrl+C`, flushes and closes producer cleanly.
* Provide a tiny `Main.kt` that invokes `LogCli`.

#### Acceptance criteria

* Running the app and typing a few lines, then pressing `Ctrl+C`, exits without exceptions.
* When consumed in the test, all lines typed before exit are present.

#### Suggested Import Path

```kotlin
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess
```

#### Command to verify/run

```bash
# Run the CLI (uses Testcontainers in a JUnit-based smoke test OR
# point to a real broker via KAFKA_BOOTSTRAP env var)
# run interactly
./gradlew :01-logging-producer:installDist
KAFKA_BOOTSTRAP=localhost:9092 ./01-logging-producer/build/install/01-logging-producer/bin/01-logging-producer --app=demo

# pass the input line
printf "hello\nworld\ntwo\n" | KAFKA_BOOTSTRAP=localhost:9092 ./gradlew :01-logging-producer:run --args="--app=demo"

#verify the message by commands
docker compose -f docker/single-node/docker-compose.yml exec -T kafka \
  bash -lc 'kafka-console-consumer --bootstrap-server localhost:9092 --topic logs --from-beginning --timeout-ms 5000 --property print.key=true --property key.separator=:: | sed -n "1,50p"'

# Type a few lines, hit Ctrl+C
# Expected: clean exit; when consumed later, lines exist in topic 'logs'.
```

---

## Minimal file hints (non-exhaustive)

* `src/main/kotlin/com/example/kafka/LogProducer.kt`
* `src/main/kotlin/com/example/kafka/JsonLogProducer.kt`
* `src/main/kotlin/com/example/kafka/BatchedLogProducer.kt`
* `src/test/kotlin/com/example/kafka/LogProducerIT.kt`
* `src/test/kotlin/com/example/kafka/JsonLogProducerIT.kt`
* `src/test/kotlin/com/example/kafka/BatchedLogProducerIT.kt`
* *(optional)* `src/main/kotlin/com/example/kafka/LogCli.kt`, `Main.kt`

> Tip: In tests, set `System.setProperty("KAFKA_BOOTSTRAP", container.bootstrapServers)` to reuse the same producers without passing configs around.
