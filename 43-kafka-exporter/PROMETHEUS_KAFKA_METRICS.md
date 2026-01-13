# Prometheus Kafka Metrics

## 1. Under-replicated partitions

**Metric Name:** `kafka_server_ReplicaManager_UnderReplicatedPartitions`

**What it measures:**
Count of partitions that have fewer live replicas than the configured replication factor. A partition with replication-factor=3 but only 2 available replicas is under-replicated.

**Why it matters operationally:**
- **Value = 0**: Healthy state - all partitions are fully replicated
- **Value > 0**: Critical alert - indicates broker failures, network issues, or disk problems
- **Operational impact**: If the leader fails, there may not be enough in-sync replicas (ISR) to maintain availability, risking data unavailability or loss. This is a primary indicator of cluster health degradation.

---

## 2. Incoming message rate

**Metric Name:** `kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`

**What it measures:**
The rate of messages (count per second) being produced to all topics on this broker, averaged over one minute. This is a rate metric showing throughput.

**Why it matters operationally:**
- **High rate**: Indicates active producer traffic - normal during peak hours
- **Sudden drop to 0**: May indicate producer failures or network issues
- **Operational insight**: Used to track broker load, capacity planning, and detect anomalies. Combined with `BytesInPerSec`, helps determine if broker can handle current throughput and plan for scaling.

---

## 3. Request latency or queue time

**Metric Name:** `kafka_server_KafkaRequestHandlerPool_RequestHandlerAvgIdlePercent_one_minute_rate`

**What it measures:**
The percentage of time request handler threads are idle (not processing requests), averaged over one minute. Low idle percent means handlers are busy processing requests.

**Why it matters operationally:**
- **High idle percent (>90%)**: Healthy - broker has capacity, requests are processed quickly
- **Low idle percent (<10%)**: Critical - broker is overloaded, requests are queuing, latency increasing
- **Operational impact**: When idle percent drops, it indicates the broker cannot keep up with request volume. This causes request queuing, increased latency, and potential timeouts. May require increasing `num.network.threads` or horizontal scaling.

---

## 4. Log end offset

**Metric Name Pattern:** `kafka_log_Log_LogEndOffset` (per topic/partition)

**What it measures:**
The offset of the last message written to a partition's log. This is the "high water mark" - the next offset that will be assigned to a new message. Note: This metric appears per topic/partition (e.g., `kafka.log:type=Log,name=LogEndOffset,topic=<topic>,partition=<partition>`).

**Why it matters operationally:**
- **Increasing value**: Normal - indicates messages are being produced to the partition
- **Stagnant value**: May indicate no new messages (normal) or producer issues (problematic)
- **Operational insight**: Used to calculate consumer lag (Lag = LogEndOffset - ConsumerOffset). Critical for monitoring consumer health and detecting if consumers are falling behind. Also used to track partition growth and storage planning.
