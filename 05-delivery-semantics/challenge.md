# `CHALLENGE.md`

### Dependencies

*(no new dependencies — same Kafka + JUnit + Testcontainers setup)*

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.testcontainers:kafka:1.20.1")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A – At-most-once Delivery

#### Requirement

* Produce 10 messages to topic `logs-atmost`.
* Consume them with `enable.auto.commit=false`.
* **Commit offsets before processing each record** (simulate at-most-once).
* Stop the app halfway through processing (simulate crash).
* Restart consumer and observe which messages are missing.

#### Acceptance criteria

* Some messages **never reappear** after restart (lost).
* Output shows **gaps in offsets** (e.g., processed 0–3, skipped 4–9).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
```

#### Command to verify/run

```bash
./gradlew :05-delivery-semantics:test --tests AtMostOnceDeliveryTest
```

---

### Problem B – At-least-once Delivery

#### Requirement

* Produce 10 messages to topic `logs-atleast`.
* Consume with `enable.auto.commit=false`.
* **Process first, then commitSync()**.
* Simulate crash before commit (force stop).
* Restart and observe **duplicate reprocessing**.

#### Acceptance criteria

* All messages eventually processed,
* but **some appear twice** in logs (due to uncommitted offsets).

#### Command to verify/run

```bash
./gradlew :05-delivery-semantics:test --tests AtLeastOnceDeliveryTest
```

---

### Problem C – Exactly-once with Idempotent Producer

#### Requirement

* Produce 10 messages using a producer configured with:

  ```kotlin
  props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
  props[ProducerConfig.ACKS_CONFIG] = "all"
  props[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
  props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
  ```
* Simulate network drop / resend logic (duplicate sends).
* Verify that the broker **de-duplicates** and only stores one copy per offset.

#### Acceptance criteria

* No duplicate offsets observed on the topic (all unique).
* Logs show producer retries succeeded without duplication.

#### Command to verify/run

```bash
./gradlew :05-delivery-semantics:test --tests IdempotentProducerTest
```

---

### Problem D – Compare All Modes

#### Requirement

* Run all three consumers (A/B/C) on their respective topics.
* Collect a simple table:

| Mode          | Duplicates | Loss       | Notes                               |
| ------------- | ---------- | ---------- | ----------------------------------- |
| at-most-once  | yes        | ✅ possible | commit before process               |
| at-least-once | ✅ possible | no         | commit after process                |
| exactly-once  | ❌          | ❌          | idempotent producer or transactions |

#### Acceptance criteria

* Output table printed to console after all tests.

#### Command to verify/run

```bash
./gradlew test --tests DeliverySemanticsSummaryTest
```

---

⚡ **Hints**

* You can simulate a crash by forcibly exiting mid-loop (`exitProcess(1)`), or commenting out `commitSync()` and re-running.
* Use `consumer.commitSync()` manually — this is where semantics diverge.
* When testing the idempotent producer, you can resend the same batch twice (`send()` loop twice) and confirm the broker stored unique offsets only.
