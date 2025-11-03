# Day 16: JSON Serialization & Typed Events

### Dependencies

```kotlin
implementation("org.springframework.kafka:spring-kafka")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.+")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

---

### Problem A — Typed Producer with `JsonSerializer`

#### Requirement

* Change your producer bean to `KafkaTemplate<String, StockChanged>`.
* In `application.yml`, set:

  ```yaml
  spring:
    kafka:
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  ```
* Implement `StockProducerTyped.send(event: StockChanged)` that calls `template.send("inventory.events", event.sku, event)`.

#### Acceptance criteria

* App starts.
* Sending `/api/stock/reserve?sku=SKU-100&qty=2` produces one **typed** message (no manual `ObjectMapper.writeValueAsString`).

#### Suggested Import Path

```kotlin
import org.springframework.kafka.core.KafkaTemplate
```

#### Command to verify/run

```bash
./gradlew :16-json-serialization:bootRun
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-100&qty=2"
```

---

### Problem B — Typed Listener with `JsonDeserializer`

#### Requirement

* Configure consumer in `application.yml`:

  ```yaml
  spring:
    kafka:
      consumer:
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
        properties:
          spring.json.trusted.packages: "com.example"   # replace with your base package
          spring.json.value.default.type: com.example.inventory.StockChanged
      listener:
        ack-mode: MANUAL_IMMEDIATE
  ```
* Implement `@KafkaListener` that receives `StockChanged` directly (method arg type is `StockChanged`) and acknowledges after processing.

#### Acceptance criteria

* Listener logs the `StockChanged` object (no manual Jackson parsing).
* Restarting the app doesn’t reprocess already-acked records.

#### Suggested Import Path

```kotlin
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
```

#### Command to verify/run

```bash
./gradlew bootRun
curl -X POST "http://localhost:8080/api/stock/release?sku=SKU-100&qty=1"
```

---

### Problem C — Inspect type headers (`__TypeId__`)

#### Requirement

* Keep default JsonSerializer header behavior.
* Consume with headers printed and verify `__TypeId__` exists.

#### Acceptance criteria

* CLI output shows `__TypeId__=com.example.inventory.StockChanged` (or similar).
* Key is the `sku`.

#### Command to verify/run

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events.json \
     --from-beginning --property print.key=true --property print.headers=true --timeout-ms 5000'
```

---

### Problem D — Remove type headers; rely on default type

#### Requirement

* In `application.yml`, disable header addition and keep consumer default type:

  ```yaml
  spring:
    kafka:
      producer:
        properties:
          spring.json.add.type.headers: false
      consumer:
        properties:
          spring.json.value.default.type: com.example.StockChanged
  ```
* Send a new event and verify consumer still deserializes correctly.

#### Acceptance criteria

* New records **lack** `__TypeId__` header.
* Listener still receives `StockChanged` objects.

#### Command to verify/run

```bash
./gradlew bootRun
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-200&qty=3"
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory.events \
     --from-beginning --property print.headers=true --timeout-ms 5000'
```

---

### Problem E (Optional) — Two event types in one topic

#### Requirement

* Add a second event `StockAdjusted(eventId, sku, amount, reason="ADJUST", occurredAt)`.
* Re-enable type headers (`spring.json.add.type.headers=true` on producer).
* Produce both `StockChanged` and `StockAdjusted` to **`inventory.events`**.
* Listener method receives `Any` (or `Object`) and logs the **runtime type** via `__TypeId__` header.

#### Acceptance criteria

* CLI shows mixed events with appropriate `__TypeId__` values.
* Listener correctly branches by runtime type and logs each.

#### Suggested Import Path

```kotlin
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
```

#### Command to verify/run

```bash
curl -X POST "http://localhost:8080/api/stock/reserve?sku=SKU-100&qty=2"
curl -X POST "http://localhost:8080/api/stock/adjust?sku=SKU-100&amount=50"
# send both kinds via your REST endpoints or test code
```

---

## Minimal code hints

**Typed producer (`StockProducerTyped.kt`)**

```kotlin
@Service
class StockProducerTyped(private val template: KafkaTemplate<String, StockChanged>) {
    private val topic = "inventory.events"
    fun send(evt: StockChanged) {
        template.send(topic, evt.sku, evt)
            .addCallback(
                { md -> println("OK sku=${evt.sku} p=${md.partition()} off=${md.offset()}") },
                { ex -> println("FAIL sku=${evt.sku} ${ex.message}") }
            )
    }
}
```

**Typed listener (`InventoryTypedListener.kt`)**

```kotlin
@Component
class InventoryTypedListener {
    private val store = mutableMapOf<String, Int>()

    @KafkaListener(topics = ["inventory.events"], groupId = "inventory-typed")
    fun onStockChanged(evt: StockChanged, ack: Acknowledgment) {
        val newQty = (store[evt.sku] ?: 0) + evt.delta
        store[evt.sku] = newQty
        println("🔄 ${evt.sku}: ${evt.delta} → $newQty  (reason=${evt.reason})")
        ack.acknowledge()
    }
}
```

**YAML (consolidated starting point)**

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        # toggle in Problem D
        spring.json.add.type.headers: true
    consumer:
      group-id: inventory-typed
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example"
        spring.json.value.default.type: com.example.inventory.StockChanged
    listener:
      ack-mode: MANUAL_IMMEDIATE
      concurrency: 2
```

---

## Troubleshooting

* **`ClassNotFoundException` during deserialization** → `spring.json.trusted.packages` doesn’t include your package; set it to `"com.example"` or your real base package.
* **Message shows as String at listener** → verify `value-serializer/deserializer` are **Json** versions on both sides.
* **Headers missing in Problem C** → you may have `spring.json.add.type.headers=false`; set it to `true` for that step.
* **Batch listener + typed arg** works too: use `List<StockChanged>` and a batch container factory if you want throughput.

You now have **typed events** end-to-end — no manual parsing. This sets you up for upcoming topics: **Retries & DLT with Spring**, **Schema Registry + Avro**, and eventually **Transactions (EOS)**.
