# ✅ **Day 41: Kafka Metrics → Inspect Broker & Consumer Health**

## **CHALLENGE.md**

---

### Dependencies

* *(No new application dependencies)*
* Uses Kafka CLI + JMX (already available with Kafka)

---

## Problem A

### Requirement

Enable **JMX metrics** on your Kafka broker and verify access.

1. Configure broker with:

   * `KAFKA_JMX_PORT=9999`
   * `KAFKA_JMX_HOSTNAME=localhost`
2. Restart Kafka
3. Verify JMX is reachable

---

### Acceptance criteria

* Kafka broker starts successfully
* JMX port `9999` is open
* You can connect using `jconsole` or equivalent
* JMX tree shows:

  * `kafka.server`
  * `kafka.network`
  * `kafka.log`

---

### Command to verify/run

```bash
docker compose down
docker compose up -d

jconsole localhost:9999
# Connect to localhost:9999
```

---

## Problem B

### Requirement

Inspect **broker health metrics** and identify risk signals.

Using JMX, locate and record values for:

1. `UnderReplicatedPartitions`
2. `ActiveControllerCount`
3. `RequestHandlerAvgIdlePercent`
4. `LogDirSize`

Create file:

```
BROKER_METRICS_OBSERVATION.md
```

For each metric:

* Current value
* What “healthy” looks like
* What a bad value means

---

### Acceptance criteria

* All 4 metrics documented
* Correct interpretation of each metric
* No generic explanations (“high is bad” is not enough)

---

### Command to verify/run

```bash
cat BROKER_METRICS_OBSERVATION.md
```

---

## Problem C

### Requirement

Observe **consumer lag** and explain it.

1. Create topic:

   * `metrics-test` (3 partitions)
2. Create consumer group:

   * `metrics-consumer`
3. Produce at least 50 messages
4. Start consumer **slowly** (add sleep in processing)
5. Observe lag growth

---

### Acceptance criteria

* `kafka-consumer-groups --describe` shows lag > 0
* Lag decreases once consumer catches up
* You can explain:

  * current offset
  * log end offset
  * lag

---

### Suggested Import Path

```kotlin
import kotlin.concurrent.thread
```

---

### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic metrics-test --partitions 3 --replication-factor 1

kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test

kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic metrics-test --group metrics-consumer --from-beginning --timeout-ms 5000

kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group metrics-consumer
```

---

## Problem D

### Requirement

Simulate a **consumer lag incident** and diagnose the root cause.

Scenario:

* Consumer stops processing (pause or kill it)
* Producer continues producing
* Lag grows steadily

Create file:

```
CONSUMER_LAG_INCIDENT.md
```

Include:

* What metric detected the problem
* Why lag increased
* What action fixes it
* What action does **not** fix it

---

### Acceptance criteria

* Clear cause → symptom → fix chain
* Mentions that lag is a *symptom*, not the root cause
* Correct operational response

---

### Command to verify/run

```bash
cat CONSUMER_LAG_INCIDENT.md
```

---

## Problem E

### Requirement

Create a **Kafka Metrics Interview Cheat Sheet**.

Create file:

```
KAFKA_METRICS_INTERVIEW.md
```

Answer concisely:

1. What is consumer lag?
2. Is consumer lag always bad?
3. What does under-replicated partitions mean?
4. Which metric indicates broker overload?
5. Which metric would you alert on first?

Each answer:

* ≤ 4 lines
* Uses correct Kafka terminology
* Includes one practical insight

---

### Acceptance criteria

* All questions answered
* No vague wording
* Interview-ready clarity

---

### Command to verify/run

```bash
cat KAFKA_METRICS_INTERVIEW.md
```

---

## ✅ After This Challenge You Can

* Read Kafka’s “vital signs”
* Diagnose lag incidents confidently
* Explain Kafka health in interviews
* Avoid blind Kafka failures in production
