## Problem A

### Requirement

Add **Prometheus** to your local Docker stack and verify the UI is reachable.

1. Add a `prometheus` service to `docker-compose.yml`:

   * image: `prom/prometheus:latest`
   * port: `9090:9090`
   * mount config file: `./prometheus.yml:/etc/prometheus/prometheus.yml`

2. Start the stack.

### Acceptance criteria

* `http://localhost:9090` loads Prometheus UI
* Status → **Targets** page loads without error

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
docker compose up -d
# Open http://localhost:9090
```

---

## Problem B

### Requirement

Configure Prometheus to **scrape the Kafka JMX Exporter**.

1. Create `prometheus.yml` with:

   * `scrape_interval: 15s`
   * a scrape job named `kafka-jmx`
   * target = your JMX exporter container name + port (example: `kafka-jmx-exporter:9404`)

2. Ensure Prometheus can reach exporter via **Docker network** (not localhost).

### Acceptance criteria

* Prometheus **Targets** shows job `kafka-jmx` as **UP**
* Scrape error is empty / none
* Target endpoint is your exporter container, not `localhost`

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
docker compose up -d
# Prometheus -> Status -> Targets
# Expected: kafka-jmx UP
```

---

## Problem C

### Requirement

Run **3 PromQL queries** and record results.

Create a file:

```
PROMETHEUS_QUERIES.md
```

Include **screens or copied output** (at least one sample value) for:

1. Under-replicated partitions

```promql
kafka_server_ReplicaManager_UnderReplicatedPartitions
```

2. Incoming messages rate (choose one that exists in your metrics)
   Example:

```promql
rate(kafka_server_BrokerTopicMetrics_MessagesInPerSec[1m])
```

3. A JVM metric (from exporter output), e.g. memory or GC if present

For each query:

* What it means
* What “healthy” looks like

### Acceptance criteria

* File exists and contains all 3 queries
* Each query includes at least one observed value
* Explanations are correct and operational

### Command to verify/run

```bash
cat PROMETHEUS_QUERIES.md
```

---

## Problem D

### Requirement

Demonstrate that Prometheus stores **time series history**.

1. Produce messages continuously for ~30–60 seconds to any topic
2. In Prometheus, query the throughput metric using a time range graph
3. Stop producing
4. Confirm the metric trend changes over time

Create file:

```
TIME_SERIES_PROOF.md
```

Include:

* which metric you used
* what you observed during produce vs after stopping
* why history matters for incident investigation

### Acceptance criteria

* Evidence shows metric changes across time
* Explanation correctly states why Prometheus is required (storage + trends)

### Command to verify/run

```bash
cat TIME_SERIES_PROOF.md
```

---

## Problem E

### Requirement

Prepare interview-ready notes: **Prometheus in Kafka monitoring**.

Create:

```
PROMETHEUS_KAFKA_INTERVIEW.md
```

Answer concisely (≤ 4 lines each):

1. Why Kafka needs Prometheus (not just JMX tools)
2. What Prometheus “scraping” means
3. Why targets should be Docker service names, not localhost
4. Two Kafka metrics you would alert on first, and why

### Acceptance criteria

* Correct Prometheus terminology (scrape, target, time-series, PromQL)
* Clear Kafka monitoring reasoning
* No vague answers

### Command to verify/run

```bash
cat PROMETHEUS_KAFKA_INTERVIEW.md
```
