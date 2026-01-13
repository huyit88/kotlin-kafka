# Prometheus Queries for Kafka Monitoring

## 1. Under-replicated partitions

```promql
kafka_server_ReplicaManager_UnderReplicatedPartitions
```
```bash
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_ReplicaManager_UnderReplicatedPartitions' | jq 
```
**Query Result:**
```
kafka_server_ReplicaManager_UnderReplicatedPartitions{exporter="jmx", instance="kafka-jmx-exporter:9404", job="kafka-jmx-exporter", service="kafka"} = 0
```
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "kafka_server_ReplicaManager_UnderReplicatedPartitions",
          "exporter": "jmx",
          "instance": "kafka-jmx-exporter:9404",
          "job": "kafka-jmx-exporter",
          "service": "kafka"
        },
        "value": [
          1768289001.727,
          "0"
        ]
      }
    ]
  }
}
```
**What it means:**
This metric counts the number of partitions that have fewer in-sync replicas than the configured replication factor. Under-replicated partitions indicate that some brokers may be down, experiencing network issues, or unable to keep up with replication.

**What "healthy" looks like:**
- **Healthy:** Value is `0` - all partitions have the expected number of in-sync replicas
- **Unhealthy:** Any value > 0 indicates replication lag or broker failures. Values should return to 0 after broker recovery.

---

## 2. Incoming messages rate

```promql
kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate
```
```bash
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate' | jq 
```
**Query Result:**
```
kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate{exporter="jmx", instance="kafka-jmx-exporter:9404", job="kafka-jmx-exporter", service="kafka"} = 0.00021976066768657126
```
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate",
          "exporter": "jmx",
          "instance": "kafka-jmx-exporter:9404",
          "job": "kafka-jmx-exporter",
          "service": "kafka"
        },
        "value": [
          1768288887.499,
          "7.770187994614655e-09"
        ]
      }
    ]
  }
}
```
**What it means:**
This metric measures the rate at which messages are being produced to all topics on this broker, averaged over one minute. It's a key throughput indicator showing how much data is flowing into Kafka.

**What "healthy" looks like:**
- **Healthy:** Any positive value indicates active message production. The value should match your expected production rate for your workload.
- **Unhealthy:** 
  - Sudden drops to near-zero when production is expected indicates producer issues
  - Sustained high values without corresponding consumer lag may indicate capacity issues
  - Monitor for unexpected spikes that could indicate traffic anomalies

---

## 3. JVM metric - Heap memory usage

```promql
jvm_memory_heap_used_bytes
```

**Query Result:**
```
jvm_memory_heap_used_bytes{exporter="jmx", instance="kafka-jmx-exporter:9404", job="kafka-jmx-exporter", service="kafka"} = 656123856
```

**Additional Query for Context:**
```promql
jvm_memory_heap_max_bytes
```

**Query Result:**
```
jvm_memory_heap_max_bytes{exporter="jmx", instance="kafka-jmx-exporter:9404", job="kafka-jmx-exporter", service="kafka"} = 1073741824
```

**What it means:**
This metric shows the amount of heap memory currently used by the Kafka JVM process. Heap memory is where Java objects are allocated. Monitoring this helps prevent OutOfMemoryErrors and ensures Kafka has sufficient memory for its operations.

**What "healthy" looks like:**
- **Healthy:** 
  - Heap usage should be well below the maximum (e.g., < 80% of max heap)
  - In this case: 656MB used / 1024MB max ≈ 64% usage, which is healthy
  - Usage should stabilize after initial startup and not continuously grow
- **Unhealthy:**
  - Usage consistently > 90% of max heap indicates risk of OutOfMemoryError
  - Continuous growth without stabilization suggests memory leaks
  - Frequent garbage collection pauses can indicate memory pressure
