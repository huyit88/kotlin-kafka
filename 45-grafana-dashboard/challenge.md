## Problem A

### Requirement

Run **Grafana** locally and connect it to Prometheus.

1. Add a Grafana container:

   * Image: `grafana/grafana`
   * Port: `3000`
2. Start Grafana and log in:

   * URL: `http://localhost:3000`
   * User: `admin`
   * Password: `admin`
3. Add **Prometheus** as a data source:

   * URL: `http://prometheus:9090`
   * Access mode: `Server`

---

### Acceptance criteria

* Grafana UI is accessible
* Prometheus data source is **green (connected)**
* No authentication or connection errors

---

### Command to verify/run

```bash
docker compose up -d grafana
# Open http://localhost:3000
```

---

## Problem B

### Requirement

Create a **Kafka Broker Health dashboard**.

Create a new dashboard with **at least 3 panels**:

1. **Under-Replicated Partitions**

   * Query:

     ```promql
     kafka_server_ReplicaManager_UnderReplicatedPartitions
     ```
   * Visualization: Stat
   * Threshold: > 0 = RED

2. **Incoming Messages Rate**

   * Query:

     ```promql
     rate(kafka_server_BrokerTopicMetrics_MessagesInPerSec[1m])
     ```
   * Visualization: Time series

3. **Request Queue Time**

   * Query:

     ```promql
     kafka_network_RequestChannel_RequestQueueSize
     ```
   * Note: `kafka_network_RequestMetrics_RequestQueueTimeMs` doesn't exist. Use `RequestQueueSize` to monitor queue depth, or `kafka_server_KafkaRequestHandlerPool_RequestHandlerAvgIdlePercent_one_minute_rate` for handler utilization (inverse of latency).
   * Visualization: Time series

---

### Acceptance criteria

* All panels render data
* Metrics change when producing data
* Under-replicated partitions panel stays at `0` in healthy state

---

### Command to verify/run

```bash
# Produce messages to generate traffic
kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test
```

---

## Problem C

### Requirement

Create an **ETL Throughput dashboard**.

Dashboard panels:

1. **Input Throughput**

   ```promql
   rate(kafka_server_BrokerTopicMetrics_MessagesInPerSec[1m])
   ```

2. **Log End Offset Growth**

   ```promql
   kafka_log_Log_LogEndOffset
   ```
   
   **Note:** After updating `jmx-kafka.yml` to extract topic/partition as labels, this query will show all LogEndOffset metrics with `topic` and `partition` labels. You can filter by topic:
   ```promql
   kafka_log_Log_LogEndOffset{topic="metrics-test"}
   ```
   
   Or sum all partitions for a topic:
   ```promql
   sum by (topic) (kafka_log_Log_LogEndOffset)
   ```
3. **Processing Health (Lag Proxy)**

   **Query:**
   ```promql
   kafka_log_Log_LogEndOffset
   ```
   
   **Grafana Setup (Simplest - Recommended):**
   1. **Query:** `kafka_log_Log_LogEndOffset`
   2. **Panel Type:** Time series
   3. **Legend:** `{{topic}}/{{partition}}`
   4. **Visual Interpretation:** The **slope** of the line shows growth rate
      - Steep upward = fast growth
      - Flat = no growth
   5. **Compare:** Place next to Input Throughput panel and compare slopes
   
   **Alternative (if you want numeric rate values):**
   - Use Transform: **Add field from calculation**
   - **First field:** `kafka_log_Log_LogEndOffset`
   - **Operation:** `-` (subtraction)
   - **Second field:** `kafka_log_Log_LogEndOffset` (same field)
   - This calculates point-to-point differences
   
   **Or use manual query (after producing messages):**
   ```promql
   (kafka_log_Log_LogEndOffset - kafka_log_Log_LogEndOffset offset 1m) / 60
   ```
   
   **How to detect backlog:**
   - Compare the **rate/slope** of this panel with the **Input Throughput** panel
   - **If LogEndOffset rate > Input Throughput** → Backlog building (consumers falling behind)
   - **If rates match** → Healthy (consumers keeping up)
   - **If LogEndOffset rate < Input Throughput** → Consumers catching up
   
   **Important:** You need to produce messages first for the offset to change. If offset stays constant, the rate will be 0.
   
   **Note:** `deriv()` and `rate()` don't work with this untyped metric. Use Grafana's Transform feature or interpret the visual slope.

---

### Acceptance criteria

* Throughput rises when producing messages
* Log end offset grows monotonically
* You can visually correlate spikes across panels

---

### Command to verify/run

```bash
# Continuous produce
kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test
```

---

## Problem D

### Requirement

Create a **Consumer Lag dashboard** (conceptual if Kafka Exporter not yet added).

Create a dashboard (or panel description) explaining:

* How consumer lag would be visualized
* Which PromQL query you would use (example):

  ```promql
  kafka_consumergroup_lag
  ```
* Why lag **trend** matters more than absolute value

Document this in:

```
CONSUMER_LAG_DASHBOARD.md
```

---

### Acceptance criteria

* File exists
* Correct PromQL query referenced
* Explanation shows understanding of lag behavior

---

### Command to verify/run

```bash
cat CONSUMER_LAG_DASHBOARD.md
```

---

## Problem E

### Requirement

Prepare **Grafana + Kafka interview notes**.

Create:

```
GRAFANA_KAFKA_INTERVIEW.md
```

Answer concisely (≤ 4 lines each):

1. Why Grafana is used with Kafka
2. Most important Kafka dashboard
3. Why alerts should be based on trends
4. One mistake teams make with Kafka dashboards

---

### Acceptance criteria

* All questions answered
* Clear operational reasoning
* Interview-ready phrasing

---

### Command to verify/run

```bash
cat GRAFANA_KAFKA_INTERVIEW.md
```

---

## ✅ What You’ll Master After This Challenge

* Building Kafka dashboards from scratch
* Interpreting broker & ETL health visually
* Turning metrics into operational insight
* Explaining Kafka observability in interviews
