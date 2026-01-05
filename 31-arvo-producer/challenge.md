### Dependencies

* `implementation("io.confluent:kafka-avro-serializer:7.6.0")`
* `implementation("org.apache.avro:avro:1.11.3")`
* `id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"` *(Gradle plugin)*

---

## Problem A

### Requirement

Set up **Specific Avro code generation** for a Transaction schema.

1. Create Avro schema file:

* Path: `src/main/avro/Transaction.avsc`
* Record:

  * namespace: `com.example`
  * name: `Transaction`
  * fields:

    * `transactionId` (string)
    * `amount` (double)
    * `currency` (string)

2. Configure Gradle to generate sources:

* Apply `com.github.davidmc24.gradle.plugin.avro`
* Ensure generated classes appear under `build/generated-main-avro-java`
* Ensure compilation includes generated sources

3. Add a small sanity check (anywhere in app startup):

* Print the generated class full name:

  ```
  com.example.Transaction
  ```

### Acceptance criteria

* `./gradlew :31-arvo-producer:generateAvroJava` succeeds
* Generated class exists:

  * `build/generated-main-avro-java/com/example/Transaction.java`
* App compiles and can reference `com.example.Transaction`

### Suggested Import Path

```kotlin
// For sanity check usage in code
import com.example.Transaction
```

### Command to verify/run

```bash
./gradlew :31-arvo-producer:clean :31-arvo-producer:generateAvroJava
ls build/generated-main-avro-java/com/example/Transaction.java
./gradlew test
```

---

## Problem B

### Requirement

Implement an **Avro producer** using `KafkaTemplate<String, Transaction>`.

1. Topic: `transactions-avro` (3 partitions)
2. Producer configuration:

* `KafkaAvroSerializer`
* `schema.registry.url=http://localhost:8081`

3. REST endpoint:

* `POST /tx/avro`
* Body:

  ```json
  { "transactionId":"tx-200", "amount":42.0, "currency":"USD" }
  ```

4. Producer logic:

* Build `Transaction` via builder
* Send to Kafka:

  * key = `transactionId`
  * value = `Transaction`

Log:

```
AVRO_PRODUCED id=<transactionId>
```

### Acceptance criteria

* POSTing to `/tx/avro` returns 200
* Logs contain `AVRO_PRODUCED id=tx-200`
* Schema appears in Schema Registry under subject `transactions-avro-value`

### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*
```

### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic transactions-avro --partitions 3 --replication-factor 1

./gradlew 31-arvo-producer:bootRun

curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-200","amount":42.0,"currency":"USD"}'

curl -s http://localhost:8081/subjects | grep transactions-avro
# Expected: transactions-avro-value
```

---

## Problem C

### Requirement

Implement an **Avro consumer** using Schema Registry (GenericRecord is OK).

1. Consumer group: `fraud-detector`
2. Deserialize with:

* `KafkaAvroDeserializer`
* `schema.registry.url=http://localhost:8081`

3. Consume from `transactions-avro`
4. Log:

```
AVRO_CONSUMED id=<id> amount=<amount> currency=<currency>
```

### Acceptance criteria

* Posting to `/tx/avro` triggers `AVRO_CONSUMED ...` log
* Consumer does **not** parse JSON manually
* Consumer can read messages even after app restart

### Suggested Import Path

```kotlin
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.avro.generic.GenericRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
```

### Command to verify/run

```bash
curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-201","amount":99.0,"currency":"EUR"}'
# Expected logs: AVRO_CONSUMED id=tx-201 amount=99.0 currency=EUR
```

---

## Problem D

### Requirement

Add **producer safety configs** that are typical for payment/fraud pipelines.

Configure producer:

* `acks=all`
* `enable.idempotence=true`
* `retries=Integer.MAX_VALUE`
* `max.in.flight.requests.per.connection=5`

Add a comment explaining why each exists.

### Acceptance criteria

* Producer factory contains all configs above
* App still produces successfully
* Comments are meaningful and correct

### Suggested Import Path

```kotlin
// No new imports
```

### Command to verify/run

```bash
./gradlew test
./gradlew bootRun
curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-202","amount":1.0,"currency":"USD"}'
```

---

## Problem E

### Requirement

Create `AVRO_PRODUCER.md` answering:

1. Generic vs Specific Avro
2. How schema registration happens on first produce
3. What’s inside a Kafka Avro message (magic byte + schema id + data)
4. Why keys matter for ordering (transactionId)
5. What configs make producer safer (acks/idempotence/retries)

### Acceptance criteria

* Correct terminology
* Clear explanation
* Includes an ASCII flow showing:

  ```
  REST -> KafkaTemplate -> Schema Registry -> Kafka topic
  ```

### Command to verify/run

```bash
cat AVRO_PRODUCER.md
```
