Dockerized Kafka + CPU Metrics Producer

### Dependencies

*(Only list new ones. If you already have `kafka-clients` and JUnit from earlier days, you don’t need to re-add them.)*

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A — Spin up Kafka (Docker) and verify health

#### Requirement

* Create `docker-compose.yml` with **1 ZooKeeper + 1 Kafka** (PLAINTEXT).
* Start with `docker compose up -d`.
* Verify both containers are healthy/accepting connections.

#### Acceptance criteria

* `docker ps` shows both `zookeeper` and `kafka` running.
* `kafka-topics --bootstrap-server localhost:9092 --list` runs without error.

#### Suggested Import Path

*(N/A — CLI task)*

#### Command to verify/run

```bash
# from project root (where docker-compose.yml lives)
docker compose up -d
docker ps --format "table {{.Names}}\t{{.Status}}"
docker exec -it kafka bash -lc 'kafka-topics --bootstrap-server localhost:9092 --list'
# expected: command prints (possibly empty) list without errors
```

---

### Problem B — Create and verify topic via CLI

#### Requirement

* Create topic `cpu-metrics` with **1 partition**, **RF=1** using CLI inside the `kafka` container.
* Describe the topic and print its metadata.

#### Acceptance criteria

* `PartitionCount: 1`, `ReplicationFactor: 1` shown in `--describe`.
* Topic appears in `--list`.

#### Suggested Import Path

*(N/A — CLI task)*

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create \
    --topic cpu-metrics --partitions 1 --replication-factor 1
  kafka-topics --bootstrap-server localhost:9092 --list | grep "^cpu-metrics$"
  kafka-topics --bootstrap-server localhost:9092 --describe --topic cpu-metrics
'
# expected: topic listed; describe shows PartitionCount: 1, ReplicationFactor: 1
```

---

### Problem C — Kotlin CPU Metrics Producer (10 events)

#### Requirement

* Implement `CpuMetricsProducer.kt` that:

  * Connects to `localhost:9092`.
  * Sends **10** messages to `cpu-metrics`, key = host id (e.g., `"host-A"`).
  * Value format: `ts=<epochMillis>,load=<double>`.
  * Use `com.sun.management.OperatingSystemMXBean` when available; if not available on the platform, fall back to a random/placeholder value.

#### Acceptance criteria

* Program exits **0**.
* Produces **10** records (no exceptions).
* Logs show each `RecordMetadata(topic=cpu-metrics, partition=0, offset=<increasing>)` or your own println.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
```

#### Command to verify/run

```bash
./gradlew :06-spin-up-docker:run  -q  --args="CpuMetricsProducer"  # if you wire an application plugin entry, or just run the class from IDE
# or compile & run your main() directly from IDE
```

**Minimal producer hint (drop-in main):**

```kotlin
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.management.ManagementFactory
import java.util.Properties
import kotlin.math.round

fun main() {
    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    }
    KafkaProducer<String, String>(props).use { p ->
        val os = ManagementFactory.getOperatingSystemMXBean()
        val host = "host-A"
        repeat(10) {
            val ts = System.currentTimeMillis()
            val load = try {
                val mx = os as? com.sun.management.OperatingSystemMXBean
                val v = mx?.systemCpuLoad ?: -1.0
                if (v >= 0) round(v * 1000) / 1000 else (0..100).random() / 100.0
            } catch (_: Throwable) { (0..100).random() / 100.0 }
            val value = "ts=$ts,load=$load"
            val rec = ProducerRecord("cpu-metrics", host, value)
            val md = p.send(rec).get()
            println("sent partition=${md.partition()} offset=${md.offset()} value=$value")
            Thread.sleep(200)
        }
    }
}
```

---

### Problem D — End-to-end verification via CLI consumer

#### Requirement

* Use Kafka CLI consumer (inside the `kafka` container) to read from the beginning and count at least **10** messages for `cpu-metrics` after running your producer.

#### Acceptance criteria

* At least **10** lines printed, matching your `ts=...,load=...` format.
* If you run the producer again, total increases accordingly.

#### Suggested Import Path

*(N/A — CLI task)*

#### Command to verify/run

```bash
# in terminal A: run consumer (Ctrl+C to stop after you see >=10 lines)
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic cpu-metrics --from-beginning'

# in terminal B: run your producer (Problem C), then watch terminal A print messages.
```

---

### (Optional) Problem E — Key stickiness & partitioning

#### Requirement

* Update producer to send:

  * 5 messages with key `"host-A"`, 5 with key `"host-B"`.
* Create **another topic** `cpu-metrics-2` with **3 partitions**.
* Send the same 10 messages there and print the partition for each send.

#### Acceptance criteria

* For `cpu-metrics-2`, all `"host-A"` messages land on **the same partition**; all `"host-B"` on **one (possibly different) partition**.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
```

#### Command to verify/run

```bash
docker exec -it kafka bash -lc \
  'kafka-topics --bootstrap-server localhost:9092 --create --topic cpu-metrics-2 --partitions 3 --replication-factor 1'
./gradlew :06-spin-up-docker:run -PmainClass=com.example.CpuMetricsProducerPartitionedKt -q
# expected: logs show partition numbers; same key -> same partition
```

---

**Troubleshooting tips**

* If `kafka-topics` fails to connect, wait a few seconds after `docker compose up -d` and retry.
* Ensure your app uses `localhost:9092` (host listener) as configured in `KAFKA_ADVERTISED_LISTENERS`.
* If `systemCpuLoad` is `-1.0` on your OS, your fallback value will be used — that’s OK for this exercise.
