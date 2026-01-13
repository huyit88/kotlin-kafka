# Kafka Exporter Interview Answers

### 1. Why Kafka needs exporters for monitoring

Kafka exposes metrics through JMX, which is Java-specific and not natively compatible with modern monitoring stacks like Prometheus. Exporters bridge this gap by converting JMX metrics into Prometheus format, enabling integration with time-series databases, alerting systems, and dashboards. Without exporters, teams would need custom tooling to scrape JMX, making monitoring harder to scale and standardize across infrastructure.

### 2. Difference between JMX Exporter and Kafka Exporter

**JMX Exporter** connects to Kafka's JMX port (9999) and queries Java MBeans directly - it's a generic JMX-to-Prometheus converter that works with any Java application. 
**Kafka Exporter** uses Kafka's Admin API and consumer group APIs to fetch metrics - it's Kafka-specific and can access consumer lag and group information that may not be fully exposed via JMX. JMX Exporter is simpler to set up but requires JMX access; Kafka Exporter provides richer consumer metrics but needs Kafka protocol connectivity.

### 3. What metric you alert on first in Kafka

**`kafka_server_ReplicaManager_UnderReplicatedPartitions`** 
- this is the first alert because it indicates cluster health degradation. 
Value of 0 means all partitions are fully replicated (healthy). 
Any value > 0 indicates broker failures, network issues, or disk problems, which risks data loss if leaders fail.
 This metric is critical because it directly reflects fault tolerance - the core value proposition of Kafka's replication.

### 4. One risk of misconfigured exporters

**Security risk**: If exporters expose Kafka protocol ports (9092) instead of keeping them separate, they create an unnecessary attack surface. 
A compromised exporter could potentially be used to access Kafka's data plane (produce/consume messages) rather than just reading metrics. Exporters should only expose their own HTTP endpoints (e.g., 9404) and connect to Kafka via separate, secured channels to maintain the principle of least privilege.
