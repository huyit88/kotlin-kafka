# `CHALLENGE.md` — Day 13: Spring Kafka Configuration & Integration

### Dependencies

*(List only what’s new for this challenge.)*

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.kafka:spring-kafka")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

---

### Problem A — Bootstrap a Spring Boot app that connects to Kafka

#### Requirement

* Create a Kotlin Spring Boot app (`InventoryApp`) that **starts successfully** with Kafka config in `application.yml`:

  * `spring.kafka.bootstrap-servers=localhost:19092`
  * consumer: `group-id=inventory-service`, `auto-offset-reset=earliest`
  * serializers/deserializers = `StringSerializer` / `StringDeserializer`
* App should start with no bean creation errors.

#### Acceptance criteria

* App prints “Started InventoryApp” (or similar) and **doesn’t crash**.
* No `NoSuchBean` or `Could not create Kafka` errors.

#### Suggested Import Path

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
```

#### Command to verify/run

```bash
./gradlew :13-spring-kafka-config:bootRun
# expected: Spring Boot banner + app starts and stays up
```

---

### Problem B — Wire a `KafkaTemplate` and send a startup test message

#### Requirement

* Define a `KafkaTemplate<String, String>` bean (if not using Boot autoconfig defaults).
* Implement a `CommandLineRunner` that sends one record to topic `inventory.init` on startup.

  * key = `"app-started"`
  * value = `"Inventory service online"`

#### Acceptance criteria

* On app start, you see a log line “Sent test message to Kafka”.
* Consuming `inventory.init` shows **exactly 1 message**.

#### Suggested Import Path

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.boot.CommandLineRunner
import org.springframework.kafka.core.KafkaTemplate
```

#### Command to verify/run

```bash
# run app
./gradlew :13-spring-kafka-config:bootRun


# verify via CLI (inside container)
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic inventory.init --from-beginning --timeout-ms 5000'
# expected: Inventory service online
```

---

### Problem C — Auto-create topics with `KafkaAdmin`

#### Requirement

* Add a `KafkaAdmin` configuration that **auto-creates**:

  * `inventory.init` (partitions=1, RF=1)
  * `inventory.events` (partitions=3, RF=1)
* Do this via either:

  * `KafkaAdmin` + `NewTopic` beans, **or**
  * `spring.kafka.admin.*` properties and `NewTopic` beans.

#### Acceptance criteria

* On app start, describe shows both topics created with correct partition counts.

#### Suggested Import Path

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.TopicBuilder
import org.apache.kafka.clients.admin.NewTopic
```

#### Command to verify/run

```bash
./gradlew :13-spring-kafka-config:bootRun
docker exec -it kafka bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --describe --topic inventory.init &&
  kafka-topics --bootstrap-server localhost:9092 --describe --topic inventory.events
'
# expected:
#  inventory.init -> PartitionCount:1, ReplicationFactor:1
#  inventory.events -> PartitionCount:3, ReplicationFactor:1
```

---

### Problem D — Safe producer defaults (idempotence, acks) via `application.yml`

#### Requirement

* In `application.yml`, set producer properties:

  * `acks=all`
  * `enable.idempotence=true`
  * `max-in-flight-requests-per-connection=5`
  * (optional throughput) `linger.ms=20`, `batch.size=65536`, `compression.type=lz4`
* Keep consumer defaults from Problem A.

#### Acceptance criteria

* App still starts and sends the startup message.
* Producer properties are applied (visible in logs if you print `KafkaTemplate` configs or by enabling DEBUG for `org.apache.kafka.clients.producer`).

#### Suggested Import Path

*(YAML; no new imports required)*

#### Command to verify/run

```bash
./gradlew :13-spring-kafka-config:bootRun
# (optional) enable debug logs in application.yml to observe producer settings applied
```

---

### Problem E — Profile-based overrides (`application-local.yml`)

#### Requirement

* Create `application-local.yml` that overrides:

  * `spring.kafka.bootstrap-servers` (still `localhost:9092`)
  * `spring.kafka.consumer.group-id` to `inventory-service-local`
* Run with `--spring.profiles.active=local`.

#### Acceptance criteria

* App starts with the **local** group id (verify via:
  `kafka-consumer-groups --describe --group inventory-service-local` after adding a simple @KafkaListener in the next day’s lesson, or just confirm the profile is active through logs).

#### Suggested Import Path

*(YAML; no code imports)*

#### Command to verify/run

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :13-spring-kafka-config:bootRun
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic inventory.init --group inventory-service-local --from-beginning --timeout-ms 5000'
# expected: logs indicate 'local' profile active
```

## Troubleshooting tips

* **Connection refused**: ensure Docker Kafka (Day 06) is up and `bootstrap-servers` points to `localhost:9092`.
* **Topic not found**: make sure `NewTopic` beans exist (Problem C) or create topics via CLI.
* **Serialization errors**: confirm `StringSerializer`/`StringDeserializer` on both producer and consumer (we’ll switch to JSON classes later).
* **Stuck startup**: if you add listeners early, a missing broker may delay startup—keep this day’s app producer-only.
