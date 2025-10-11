# `CHALLENGE.md`

### Dependencies

*(same as before, no new deps if you already added kafka-clients + junit + testcontainers)*

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.testcontainers:kafka:1.20.1")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A – Observe Offsets

#### Requirement

* Produce 10 messages into topic `logs-offset`.
* Create a consumer with **`auto.offset.reset=earliest`** and `enable.auto.commit=true`.
* Print each message’s `key`, `value`, `partition`, and `offset`.

#### Acceptance criteria

* Exactly 10 messages consumed.
* Offsets printed in **increasing order per partition**.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
```

#### Command to verify/run

```bash
./gradlew :04-offsets:test --tests OffsetObservationTest
```

---

### Problem B – Auto Commit Behavior

#### Requirement

* Produce 5 messages into `logs-auto`.
* Start consumer with `enable.auto.commit=true`.
* Poll once, consume messages, then **exit without committing manually**.
* Restart consumer with same group → consume again.

#### Acceptance criteria

* Depending on `auto.commit.interval.ms`, you may see **some duplicates** or **some skips**.
* Document the observed behavior.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
```

#### Command to verify/run

```bash
./gradlew :04-offsets:test --tests AutoCommitTest
```

---

### Problem C – Manual Commit

#### Requirement

* Produce 5 messages into `logs-manual`.
* Start consumer with `enable.auto.commit=false`.
* For each batch:

  * Process messages (print them).
  * Call `commitSync()` after successful processing.
* Restart consumer with same group → consume again.

#### Acceptance criteria

* No duplicates or skips after restart.
* Consumer resumes from **the last committed offset**.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
```

#### Command to verify/run

```bash
./gradlew :04-offsets:test --tests ManualCommitTest
```

---

### Problem D – Reset Offsets

#### Requirement

* Produce 5 messages into `logs-reset`.
* Start a **new group** with `auto.offset.reset=earliest`.
* Consume → should get all 5 messages.
* Start another **new group** with `auto.offset.reset=latest`.
* Consume → should see **0 old messages**, only new ones.

#### Acceptance criteria

* `earliest` group sees 5 messages.
* `latest` group sees 0 old ones.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
```

#### Command to verify/run

```bash
./gradlew test --tests OffsetResetTest
```

---

⚡ **Hints**

* Use fresh `group.id` per test (`"group-" + System.nanoTime()`) to simulate new consumers.
* Remember: offsets are **per group, per partition**.
* Auto-commit may surprise you (duplicates/skips) because commit happens on a timer, not tied to processing.
* Manual commit = safest, you decide exactly when offsets are advanced.