### Challenge

### Dependencies

*(No new ones if you already added kafka-clients + testcontainers + junit in Day 01)*

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.testcontainers:kafka:1.20.1")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A – Create Partitioned Topic

#### Requirement

* Use **AdminClient API** to create topic `logs` with **3 partitions**, replication factor = 1.
* Print partition metadata after creation.

#### Acceptance criteria

* Topic `logs` exists with exactly **3 partitions**.
* Test asserts partition count = 3.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.AdminClientConfig
```

#### Command to verify/run

```bash
./gradlew :02-partition-key-group:test --tests TopicCreationTest
```

---

### Problem B – Produce with Keys

#### Requirement

* Extend your `LogProducer` → `PartitionedLogProducer`.
* Send 10 logs with key=`app-A`, 10 with key=`app-B`, 10 with key=`app-C`.
* Print which partition each message lands in.

#### Acceptance criteria

* All `"app-A"` logs go to the **same partition**.
* All `"app-B"` logs go to another single partition.
* Same for `"app-C"`.
* Total = 30 logs produced.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
```

#### Command to verify/run

```bash
./gradlew :02-partition-key-group:test --tests PartitionedLogProducerTest
```

---

### Problem C – Two Consumers in Same Group

#### Requirement

* Create **two consumers** (`Consumer1`, `Consumer2`) with the **same group.id** = `log-group`.
* Consume the 30 logs from Problem B.
* Print the partitions assigned to each consumer.

#### Acceptance criteria

* Total consumed = **30 logs** (no loss, no duplicates).
* Consumers split partitions between them (e.g. Consumer1 → p0,p1; Consumer2 → p2).
* Ordering preserved for each app key.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
```

#### Command to verify/run

```bash
./gradlew :02-partition-key-group:test --tests SameGroupConsumersTest
```

---

### Problem D – Two Consumers in Different Groups

#### Requirement

* Start 2 consumers with **different group ids** (`group-A`, `group-B`).
* Each consumes from `logs`.

#### Acceptance criteria

* Each group consumes all 30 messages.
* Together = 60 messages consumed (duplicate across groups, but not within a group).

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
```

#### Command to verify/run

```bash
./gradlew :02-partition-key-group:test --tests MultiGroupBroadcastTest
```

---

⚡ **Hints**:

* Same `group.id` = **work sharing** (each message delivered once per group).
* Different `group.id`s = **broadcast** (each group gets the full stream).
* Use `assignment()` to print partition ownership and confirm distribution.
* Use `AUTO_OFFSET_RESET_CONFIG = earliest` to re-consume from the beginning in tests.
