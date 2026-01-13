## Problem A

### Requirement

Add a **Kafka JMX Exporter** to your local Kafka setup.

You must:

1. Enable JMX on the Kafka broker:

   * `KAFKA_JMX_PORT=9999`
   * `KAFKA_JMX_HOSTNAME=localhost`
2. Add a **JMX Exporter container** exposing:

   * HTTP port `9404`
3. Create a JMX exporter config file:

   * `jmx-kafka.yml`

The exporter must scrape:

* `kafka.server.*`
* `kafka.log.*`

---

### Acceptance criteria

* Kafka broker starts successfully
* JMX exporter container starts successfully
* `curl http://localhost:9404/metrics` returns Kafka metrics
* Metrics include at least:

  * `UnderReplicatedPartitions`
  * `LogEndOffset`

---

### Suggested Import Path

* *(None — infra only)*

---

### Command to verify/run

```bash
docker compose down
docker compose up -d

curl http://localhost:9404/metrics | grep kafka
```

---

## Problem B

### Requirement

Verify **broker health metrics** via Prometheus format.

From `/metrics`, locate and document the **Prometheus metric names** for:

1. Under-replicated partitions
2. Incoming message rate
3. Request latency or queue time
4. Log end offset

Create file:

```
PROMETHEUS_KAFKA_METRICS.md
```

For each metric:

* Metric name
* What it measures
* Why it matters operationally

---

### Acceptance criteria

* All 4 metrics documented
* Metric names match actual `/metrics` output
* Explanations are operational (not definitions)

---

### Command to verify/run

```bash
cat PROMETHEUS_KAFKA_METRICS.md
```

---

## Problem C

### Requirement

Demonstrate **time-series behavior** of Kafka metrics.

1. Start producing messages continuously to any topic
2. Observe metrics:

   * messages/sec
   * log end offset 
3. Stop producing
4. Observe metrics stabilize

Create file:

```
KAFKA_METRICS_TIME_SERIES.md
```

Document:

* Which metrics increased over time
* Which metrics stabilized
* Why time-series observation is critical

---

### Acceptance criteria

* At least 2 metrics observed changing over time
* Clear explanation of before vs after
* Correct interpretation of behavior

---

### Suggested Import Path

* *(None)*

---

### Command to verify/run

```bash
kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test

# In another terminal
watch -n 2 "curl -s http://localhost:9404/metrics | grep MessagesInPerSec"
```

---

## Problem D

### Requirement

Explain **why JMX Exporter is safe in production**.

Create file:

```
JMX_EXPORTER_SAFETY.md
```

Answer concisely:

1. Does the exporter affect Kafka throughput?
2. What happens if the exporter crashes?
3. Why it should not expose Kafka protocol ports
4. Why exporters are read-only

---

### Acceptance criteria

* Correct safety reasoning
* No incorrect claims about data path
* Clear distinction between Kafka protocol and JMX

---

### Command to verify/run

```bash
cat JMX_EXPORTER_SAFETY.md
```

---

## Problem E

### Requirement

Prepare **interview-ready explanations** for Kafka exporters.

Create file:

```
KAFKA_EXPORTER_INTERVIEW.md
```

Answer concisely (≤ 4 lines each):

1. Why Kafka needs exporters for monitoring
2. Difference between JMX Exporter and Kafka Exporter
3. What metric you alert on first in Kafka
4. One risk of misconfigured exporters

---

### Acceptance criteria

* Answers are concise and precise
* Uses correct Kafka + Prometheus terminology
* Clearly interview-ready

---

### Command to verify/run

```bash
cat KAFKA_EXPORTER_INTERVIEW.md
```

---

## ✅ What You’ll Master After This Challenge

* Exposing Kafka metrics to Prometheus
* Understanding Kafka metrics in time-series form
* Production-safe observability patterns
* Confident Kafka monitoring interview answers
