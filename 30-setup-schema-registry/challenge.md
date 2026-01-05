
### Dependencies

* `implementation("io.confluent:kafka-avro-serializer:7.6.0")`
* `implementation("org.apache.avro:avro:1.11.3")`

---

## Problem A

### Requirement

Set up **Schema Registry** and **pre-register a schema** for transactions.

1. Run Kafka + Schema Registry (Docker).
2. Create topic `transactions-avro` (3 partitions).
3. Register schema **manually** via Schema Registry REST API:

   * Subject: `transactions-avro-value`
   * Schema: `Transaction.avsc` with fields:

     * `transactionId` (string)
     * `amount` (double)
     * `currency` (string)

### Acceptance criteria

* `GET /subjects` contains `transactions-avro-value`
* `GET /subjects/transactions-avro-value/versions` returns `[1]`
* Schema is retrievable via:

  ```
  GET /subjects/transactions-avro-value/versions/1
  ```

### Suggested Import Path

* *(None — REST + Docker only)*

### Command to verify/run

```bash
docker compose up -d

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic transactions-avro --partitions 3 --replication-factor 1

curl -X POST http://localhost:8081/subjects/transactions-avro-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"Transaction\",\"namespace\":\"com.example.fraud\",\"fields\":[{\"name\":\"transactionId\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"double\"},{\"name\":\"currency\",\"type\":\"string\"}]}"
  }'

curl http://localhost:8081/subjects
# Expected: ["transactions-avro-value"]
```

---

## Problem B

### Requirement

Implement an **Avro producer** that uses the **pre-registered schema**.

1. REST endpoint:

   * `POST /tx/avro`
2. Request body:

   ```json
   {
     "transactionId": "tx-100",
     "amount": 120.5,
     "currency": "USD"
   }
   ```
3. Producer config:

   * `KafkaAvroSerializer`
   * `schema.registry.url=http://localhost:8081`
4. Produce to topic `transactions-avro`.

Log:

```
AVRO_PRODUCED id=<transactionId>
```

### Acceptance criteria

* Calling `/tx/avro` successfully produces a message.
* Schema Registry **does not create a new version** (still version `1`).
* Kafka topic receives the message.

### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
```

### Command to verify/run

```bash
./gradlew 30-setup-schema-registry:build
./gradlew 30-setup-schema-registry:bootRun

curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-100","amount":120.5,"currency":"USD"}'

curl http://localhost:8081/subjects/transactions-avro-value/versions
# Expected: [1]
```

---

## Problem C

### Requirement

Implement an **Avro consumer** that deserializes using **schema ID**.

1. Consumer group: `fraud-detector`
2. Use:

   * `KafkaAvroDeserializer`
   * `specific.avro.reader = true`
3. Extract and log:

```
AVRO_CONSUMED id=<id> amount=<amount> currency=<currency>
```

### Acceptance criteria

* Producing via `/tx/avro` results in consumer log `AVRO_CONSUMED ...`
* Consumer code contains **no hardcoded schema definition**

### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.avro.generic.GenericRecord
import org.springframework.kafka.annotation.KafkaListener
```

### Command to verify/run

```bash
curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-101","amount":77.0,"currency":"EUR"}'
# Expected logs: AVRO_CONSUMED id=tx-101 amount=77.0 currency=EUR
```

---

## Problem D

### Requirement

Demonstrate **compatibility enforcement**.

1. Try to register an **incompatible schema**:

   * Change `amount` from `double` → `string`
2. Use the same subject: `transactions-avro-value`

### Acceptance criteria

* Schema Registry **rejects** the schema with HTTP `409`
* Error message indicates **incompatible schema**

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
curl -X POST http://localhost:8081/subjects/transactions-avro-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"Transaction\",\"namespace\":\"com.example.fraud\",\"fields\":[{\"name\":\"transactionId\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"string\"},{\"name\":\"currency\",\"type\":\"string\"}]}"
  }'
# Expected: HTTP 409 conflict
```

---

## Problem E

### Requirement

Create `SCHEMA_REGISTRATION.md` answering:

1. What “schema registration” means
2. Why producers (not consumers) register schemas
3. What a schema ID is and why it’s embedded in messages
4. Subject naming strategy and why `topic-value` is common
5. How Schema Registry prevents production incidents

### Acceptance criteria

* Correct Kafka + Schema Registry terminology
* Clear step-by-step explanation
* Includes one ASCII flow:

  ```
  Producer → Schema Registry → Kafka → Consumer
  ```

### Command to verify/run

```bash
cat SCHEMA_REGISTRATION.md
```
