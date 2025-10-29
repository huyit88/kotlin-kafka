# Day 14: KafkaTemplate Producer (StockService emits stock events)

### Dependencies

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.kafka:spring-kafka")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.+")

testImplementation("org.springframework.boot:spring-boot-starter-test")
```

---

### Problem A — Wire `KafkaTemplate` and send a StockChanged event

#### Requirement

* Add `StockChanged` data class:

  ```kotlin
  data class StockChanged(
      val eventId: String,
      val sku: String,
      val delta: Int,
      val reason: String,
      val occurredAt: Long = System.currentTimeMillis()
  )
  ```
* Create `StockProducer` Spring `@Service` that:

  * Serializes the event to JSON (Jackson).
  * Sends to topic **`inventory.events`** with **key = `sku`** via `KafkaTemplate<String, String>`.
  * Adds headers: `eventType=StockChanged`, `source=StockService`, `eventId=<uuid>`.
* Add `/api/stock/reserve?sku=...&qty=...` endpoint (POST) that publishes a `StockChanged` with `delta = -qty`, `reason=RESERVE`.

#### Acceptance criteria

* App starts successfully.
* `curl` the endpoint → one record appears on `inventory.events` with key `sku` and headers present.

#### Suggested Import Path

```kotlin
import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.header.internals.RecordHeader
```

#### Command to verify/run

```bash
# run app
./gradlew :14-spring-kafka-producer:bootRun

# trigger one event
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-123&qty=2"

# consume and verify
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events \
     --from-beginning --property print.key=true --property print.headers=true --timeout-ms 5000'
# expected: key=SKU-123 and headers eventType=StockChanged, source=StockService, eventId=<uuid>
```

---

### Problem B — Add `/release` endpoint and prove key-based ordering

#### Requirement

* Add `/api/stock/release?sku=...&qty=...` (POST) → publishes `delta = +qty`, `reason=RELEASE`.
* Send a sequence of events for the **same** `sku`:

  1. `reserve qty=2`
  2. `release qty=1`
  3. `reserve qty=3`
* Consume from the beginning with `--property print.partition=true` and confirm **all events for `SKU-123` land on the same partition** and appear in send order.

#### Acceptance criteria

* CLI output shows a **single partition** for all `SKU-123` events and ordered messages.

#### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.*
```

#### Command to verify/run

```bash
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-123&qty=2"
curl -X POST "http://localhost:8080/api/stock/release?sku=SKU-123&qty=1"
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-123&qty=3"

docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events \
     --from-beginning --property print.key=true --property print.partition=true --timeout-ms 5000'
# expected: same partition id for SKU-123, messages in the order sent
```

---

### Problem C — Bulk publish endpoint (+basic throughput log)

#### Requirement

* Add `/api/stock/bulk?sku=SKU-123&n=100` (POST) that:

  * Sends `n` **RESERVE** events with `delta=-1` for the given `sku` in a loop.
  * Logs total time and approx throughput (records/sec).
* Keep producer safe defaults via `application.yml`:

  * `acks=all`, `enable.idempotence=true`, `max-in-flight-requests-per-connection=5`
  * Optional tuning: `linger.ms=20`, `batch.size=65536`, `compression.type=lz4`

#### Acceptance criteria

* Endpoint returns `{ "sent": n, "ms": <time>, "rps": <calc> }`.
* Records appear on the topic.

#### Suggested Import Path

```kotlin
import kotlin.system.measureTimeMillis
```

#### Command to verify/run

```bash
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-999&n=200"

docker exec -it kafka bash -lc \
  'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group dummy || true'
# (just to keep CLI handy; consume if you want to see data)
```

---

### Problem D — DLT on fatal error (serialization) with headers

#### Requirement

* Create **DLT topic** `inventory.events.dlt` (1 RF, 3 partitions).
* Modify `StockProducer` so that if send **fails with a non-retriable (fatal) exception**, it **publishes to DLT** with headers:

  * `errorType`, `errorMessage`, `originalTopic`, `eventId`, `sku`
* To induce a fatal error for testing, temporarily send a non-UTF value or misconfigure serializer (then restore).

#### Acceptance criteria

* When a fatal error is simulated, a record appears on **`inventory.events.dlt`** with the specified headers.

#### Suggested Import Path

```kotlin
import org.apache.kafka.common.errors.SerializationException
```

#### Command to verify/run

```bash
# create DLT once
docker exec -it kafka bash -lc \
  'kafka-topics --bootstrap-server localhost:9092 --create --topic inventory.events.dlt \
     --partitions 3 --replication-factor 1 || true'

# run app and induce error (temporarily change code to send bad payload), then:
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events.dlt \
     --from-beginning --property print.headers=true --timeout-ms 8000'
# expected: one DLT record with error headers
```

---

### Problem E (Optional) — Transaction wrapper (preview)

#### Requirement

* Add `spring.kafka.producer.transaction-id-prefix: inv-tx-` in `application.yml`.
* Wrap `template` usage in `executeInTransaction { ... }`.
* Still only **producing**, but you’re now ready for **consume→process→produce** exactly-once patterns later.

#### Acceptance criteria

* App starts; messages still flow normally (you’ll wire EOS in Week 9).

#### Suggested Import Path

*(YAML only)*

#### Command to verify/run

```bash
./gradlew bootRun
```

---

## Minimal implementation hints

**`StockProducer.kt`**

```kotlin
@Service
class StockProducer(private val template: KafkaTemplate<String, String>) {
    private val mapper = jacksonObjectMapper()
    private val topic = "inventory.events"

    fun send(evt: StockChanged) {
        val key = evt.sku
        val value = mapper.writeValueAsString(evt)
        val future = template.send(topic, key, value)
        future.addCallback(
            { md -> println("OK key=$key p=${md.partition()} off=${md.offset()}") },
            { ex ->
                // fatal -> publish to DLT
                val dlt = template.send("inventory.events.dlt", key, value)
                println("DLT published for key=$key cause=${ex.javaClass.simpleName}: ${ex.message}")
                dlt.addCallback({},{ err -> println("DLT failed: ${err.message}") })
            }
        )
    }
}
```

**`StockController.kt`**

```kotlin
@RestController
@RequestMapping("/api/stock")
class StockController(private val producer: StockProducer) {

    @PostMapping("/reserve")
    fun reserve(@RequestParam sku: String, @RequestParam qty: Int) =
        send(sku, -qty, "RESERVE")

    @PostMapping("/release")
    fun release(@RequestParam sku: String, @RequestParam qty: Int) =
        send(sku, +qty, "RELEASE")

    @PostMapping("/bulk")
    fun bulk(@RequestParam sku: String, @RequestParam n: Int): Map<String, Any> {
        val start = System.nanoTime()
        repeat(n) {
            producer.send(
                StockChanged(
                    eventId = java.util.UUID.randomUUID().toString(),
                    sku = sku, delta = -1, reason = "RESERVE"
                )
            )
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        val rps = if (ms > 0) (n * 1000.0 / ms).toInt() else n
        return mapOf("sent" to n, "ms" to ms, "rps" to rps)
    }

    private fun send(sku: String, delta: Int, reason: String): Map<String, Any> {
        val evt = StockChanged(
            eventId = java.util.UUID.randomUUID().toString(),
            sku = sku, delta = delta, reason = reason
        )
        producer.send(evt)
        return mapOf("status" to "OK", "eventId" to evt.eventId)
    }
}
```

**`application.yml` (producer bits)**

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 20
        batch.size: 65536
        compression.type: lz4
```

---

## Verification cheat-sheet

```bash
# send test events
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-123&qty=2"
curl -X POST "http://localhost:8080/api/stock/release?sku=SKU-123&qty=1"
curl -X POST "http://localhost:8080/api/stock/bulk?sku=SKU-999&n=50"

# observe key-based partitioning & headers
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events \
     --from-beginning --property print.key=true --property print.partition=true --property print.headers=true --timeout-ms 5000'
```
