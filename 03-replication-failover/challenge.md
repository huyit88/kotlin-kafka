# `CHALLENGE.md`

### Dependencies

*(same as Day 02; no new libs required)*

```kotlin
implementation("org.apache.kafka:kafka-clients:3.7.0")
testImplementation("org.testcontainers:kafka:1.20.1")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
```

---

### Problem A – Create Replicated Topic

#### Requirement

* Use **AdminClient API** to create a topic `logs-repl` with:

  * Partitions = 3
  * Replication factor = 2
* Print leader & replica assignments for each partition.

#### Acceptance criteria

* `logs-repl` exists with **3 partitions** and **RF=2**.
* Each partition shows **1 leader + 1 follower**.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.TopicPartitionInfo
```

#### Command to verify/run

```bash
./gradlew 03-replication-failover:test --tests ReplicatedTopicCreationTest
```

---

### Problem B – Produce Durable Messages

#### Requirement

* Produce 20 messages into `logs-repl`.
* Configure producer for durability:

  * `acks=all`
  * `enable.idempotence=true`
* Print the partition each message goes to.

#### Acceptance criteria

* All 20 messages acknowledged successfully.
* No duplicate sends.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
```

#### Command to verify/run

```bash
./gradlew :03-replication-failover:test --tests DurableProducerTest
```

---

### Problem C – Consume and Verify Replication

#### Requirement

* Create one consumer group (`replica-test`).
* Consume all 20 messages.
* Print `partition`, `offset`, and `key`.

#### Acceptance criteria

* Exactly **20 messages** consumed (no loss, no duplicates).
* Ordering per partition preserved.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
```

#### Command to verify/run

```bash
./gradlew :03-replication-failover:test --tests ReplicaConsumerTest
```

---

### Problem D – Broker Failover (Core of Replication Challenge)

#### Requirement

* Start cluster with at least **2 brokers**.
* Produce 20 messages to `logs-repl`.
* Stop broker 1 (simulate crash).
* Consume from surviving broker(s).

#### Acceptance criteria

* Still consume all **20 messages** after broker1 is down.
* New leader automatically elected (verify with `AdminClient.describeTopics()`).
* No lost messages.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.TopicPartitionInfo
```

#### Command to verify/run

```bash
./gradlew :03-replication-failover:test --tests ReplicationFailoverTest
```

---

⚡ **Hints**

* Use `AdminClient.describeTopics()` to fetch partition info and show leader/follower assignment before and after broker stop.
* Use Testcontainers or Docker Compose for multi-broker cluster.
* Always `acks=all` when producing to replicated topics.
