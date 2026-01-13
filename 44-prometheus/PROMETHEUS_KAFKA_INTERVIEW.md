# Prometheus in Kafka Monitoring - Interview Notes

## 1. Why Kafka needs Prometheus (not just JMX tools)

**Answer:**
Prometheus provides **time-series storage** and **historical context** that JMX tools lack. While JMX shows current values, Prometheus stores metrics over time, enabling trend analysis and incident investigation. It offers **centralized collection** across multiple brokers, **PromQL querying** for complex analysis, and **alerting** based on historical patterns. The **pull-based model** scales better than push-based systems and provides a single source of truth for all Kafka metrics.

---

## 2. What Prometheus "scraping" means

**Answer:**
Scraping is Prometheus's **pull-based collection mechanism** where it periodically (e.g., every 15s) makes HTTP GET requests to exporter endpoints (like `/metrics` on the JMX exporter). 
The exporter returns metrics in **Prometheus text format**, which Prometheus parses and stores as **time-series data**. 
This is different from push-based systems where applications send metrics to a collector. 
Scraping allows Prometheus to control collection frequency and handle failures gracefully.

---

## 3. Why targets should be Docker service names, not localhost

**Answer:**
Docker containers run in **isolated networks** where `localhost` refers to the container itself, not other containers. 
Using Docker service names (e.g., `kafka-jmx-exporter:9404`) leverages Docker's **internal DNS** to resolve the correct container IP within the same Docker network. 
This ensures Prometheus (running in its own container) can reach the JMX exporter. Using `localhost:9404` would fail because Prometheus would try to connect to itself, not the exporter container.

---

## 4. Two Kafka metrics you would alert on first, and why

**Answer:**
1. **`kafka_server_ReplicaManager_UnderReplicatedPartitions`** - Alert when > 0. This indicates broker failures or replication lag, which can lead to data loss if the leader fails. It's a critical availability metric that requires immediate attention.

2. **`kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`** - Alert on sudden drops (e.g., > 50% decrease) or sustained zero values when production is expected. 
This detects producer failures, network issues, or application problems that prevent data ingestion, impacting downstream consumers.
