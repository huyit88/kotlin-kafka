
### 1. Does the exporter affect Kafka throughput?

**No, the exporter does not affect Kafka throughput.** The JMX exporter connects to Kafka's JMX port (9999), which is completely separate from Kafka's data path (port 9092). 
JMX provides read-only access to metrics that Kafka already collects internally for management purposes. 
The exporter only queries these pre-existing metrics - it does not intercept, modify, or interfere with message production, consumption, or replication. 
Since JMX operations are lightweight metadata queries and Kafka's JMX implementation is optimized for minimal overhead, the exporter has negligible impact on broker performance.

### 2. What happens if the exporter crashes?

**The exporter crash has zero impact on Kafka broker, consumers, or producers.** The exporter runs as a completely separate process/container with no dependencies on Kafka's core functionality. 
If the exporter crashes, Kafka continues operating normally - messages are still produced, consumed, and replicated. 
The only impact is that metrics collection stops temporarily until the exporter is restarted, meaning you lose visibility during the downtime but no operational functionality is affected. This isolation is a key safety feature - monitoring failures cannot cause application failures.

### 3. Why it should not expose Kafka protocol ports

**Kafka protocol ports (e.g., 9092) should never be exposed through the exporter because they serve different purposes and have different security implications.** 
The Kafka protocol port handles actual message data (produces, consumes, replication) and requires authentication, authorization, and encryption in production. Exposing it through the exporter would create an unnecessary attack surface and violate the principle of least privilege. 
The exporter should only expose its own HTTP metrics endpoint (9404), which serves read-only Prometheus-formatted data. Keeping Kafka protocol ports separate ensures that even if the exporter is compromised, attackers cannot access Kafka's data plane - they can only read metrics, not produce/consume messages.

### 4. Why exporters are read-only

**Exporters are read-only by design because JMX itself provides read-only access to metrics.** JMX (Java Management Extensions) is a monitoring and management interface that exposes metrics, attributes, and operations for observation - it is not designed for modifying application state or data. The JMX exporter can only query metrics that Kafka already exposes through its MBeans; it cannot write data, change configurations, or perform any operations that affect Kafka's behavior. This read-only nature is a fundamental safety feature: even if the exporter is misconfigured or compromised, it cannot corrupt data, change broker settings, or disrupt operations. It can only observe and report what Kafka chooses to expose.
