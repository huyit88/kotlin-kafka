### Dependencies

* `implementation("io.confluent:kafka-avro-serializer:7.6.0")`
* `implementation("org.apache.avro:avro:1.11.3")`

---

## Problem A

### Requirement

Run Kafka with **Schema Registry** locally and verify it’s reachable.

1. Update your `docker-compose.yml` to include:

* `kafka`
* `zookeeper`
* `schema-registry` exposed on `localhost:8081`

2. Add a simple health endpoint check:

* `GET http://localhost:8081/subjects` should return `[]` (empty array) at first.

#### Acceptance criteria

* `docker compose up -d` starts all containers successfully.
* `curl http://localhost:8081/subjects` returns HTTP 200 and a JSON array.

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
docker compose up -d
curl -s http://localhost:8081/subjects
# Expected: []
```

---

## Problem B

### Requirement

Implement a **JSON baseline producer/consumer** (no schema registry).

1. Topics:

* `transactions-json`

2. Producer endpoint:

* `POST /tx/json`
* Body:

  ```json
  {
    "transactionId": "tx-1",
    "amount": 100.0,
    "currency": "USD"
  }
  ```
* Produce the JSON string as-is to `transactions-json`

3. Consumer:

* `@KafkaListener(topics=["transactions-json"], groupId="fraud-json")`
* Parse JSON manually (Jackson or simple parsing is fine)
* Log:

  ```
  JSON_TX id=<transactionId> amount=<amount> currency=<currency>
  ```

#### Acceptance criteria

* Posting to `/tx/json` results in consumer log `JSON_TX ...`
* Changing the JSON field name (e.g., `currency` → `ccy`) causes consumer to fail or log parsing error (to demonstrate fragility).

#### Suggested Import Path

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
```

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic transactions-json --partitions 3 --replication-factor 1
'
```
```bash

./gradlew :29-arvo-vs-json:bootRun

curl -X POST http://localhost:8080/tx/json -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-1","amount":100.0,"currency":"USD"}'
# Expected logs: JSON_TX id=tx-1 amount=100.0 currency=USD

docker exec -it kafka bash -lc '
echo "tx-2|{\"transactionId\":\"tx-2\",\"amount\":50.0,\"ccy\":\"USD\"}" | \
  kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic transactions-json \
    --property "parse.key=true" \
    --property "key.separator=|"'
# tx-1|{"transactionId":"tx-1","amount":100.0,"ccy":"USD"}
```

---

## Problem C

### Requirement

Create an **Avro schema** for transactions and register it via producer.

1. Create `Transaction.avsc`:

* Namespace: `com.example.fraud`
* Name: `Transaction`
* Fields:

  * `transactionId` (string)
  * `amount` (double)
  * `currency` (string)

2. Create topic:

* `transactions-avro`

3. Producer endpoint:

* `POST /tx/avro`
* Body same as JSON
* Producer should serialize using **KafkaAvroSerializer**
* Set config:

  * `schema.registry.url=http://localhost:8081`

Log:

```
AVRO_TX produced id=<transactionId>
```

#### Acceptance criteria

* First request to `/tx/avro` causes Schema Registry to contain subject:

  * `transactions-avro-value` (or equivalent)
* `curl http://localhost:8081/subjects` includes that subject.

#### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
```

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic transactions-avro --partitions 3 --replication-factor 1
'
curl -X POST http://localhost:8080/tx/avro -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-2","amount":50.0,"currency":"USD"}'

curl -s http://localhost:8081/subjects | grep transactions-avro
# Expected: contains transactions-avro-value (subject naming may vary)
```

---

## Problem D

### Requirement

Implement an **Avro consumer** that deserializes using Schema Registry.

1. Consumer group:

* `fraud-avro`

2. Consumer:

* Reads from `transactions-avro`
* Uses `KafkaAvroDeserializer`
* Extracts fields:

  * transactionId
  * amount
  * currency
* Log:

  ```
  AVRO_TX consumed id=<id> amount=<amount> currency=<currency>
  ```

#### Acceptance criteria

* Producing via `/tx/avro` results in consumer log `AVRO_TX consumed ...`
* No manual JSON parsing exists in Avro path.

#### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
```

#### Command to verify/run

```bash
curl -X POST http://localhost:8080/tx/avro -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-3","amount":77.0,"currency":"EUR"}'
# Expected logs: AVRO_TX consumed id=tx-3 amount=77.0 currency=EUR
```

---

## Problem E

### Requirement

Demonstrate **why schema evolution is safer than JSON**.

1. Create **v2 schema** `TransactionV2.avsc` by adding field:

* `merchantId` as optional:

  ```json
  { "name": "merchantId", "type": ["null", "string"], "default": null }
  ```

2. Produce a v2 transaction (merchantId present)

3. Consumer should still work (backward compatibility)

Create `SCHEMA_EVOLUTION_NOTES.md` answering:

* Why this change is backward compatible
* What changes would break compatibility (give 2 examples)

#### Acceptance criteria

* Consumer still logs old fields successfully
* Notes file exists with correct reasoning

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
# After producing v2 records, consumer still logs id/amount/currency without crashing
cat SCHEMA_EVOLUTION_NOTES.md
```
