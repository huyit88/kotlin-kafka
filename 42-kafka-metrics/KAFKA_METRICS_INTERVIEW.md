# Kafka Metrics Interview Cheat Sheet

## 1. What is consumer lag?

**Answer**
Consumer lag is the difference between the latest message offset and the consumer's committed offset. Formula: `Lag = Log End Offset - Current Offset`. It measures how many unprocessed messages are queued for a consumer group. 
**Practical insight:** Lag of 0 means consumer is caught up, but some lag (e.g., 100-1000 messages) is normal during traffic spikes and indicates healthy throughput.

---

## 2. Is consumer lag always bad?

**Answer**
No, consumer lag is not always bad. It's a symptom indicating the processing rate vs. production rate. 
**Practical insight:** Small, stable lag (hundreds to low thousands) is healthy and shows consumers are keeping up with normal traffic. Lag becomes problematic when it grows continuously (consumer can't catch up) or spikes suddenly (indicates downstream processing bottleneck or consumer failure).

---

## 3. What does under-replicated partitions mean?

**Answer**
Under-replicated partitions means a partition has fewer live replicas than its configured replication factor. For example, a partition with replication-factor=3 but only 2 replicas available is under-replicated. 
**Practical insight:** Value > 0 indicates broker failures or network issues. This is critical because if the leader fails, there may not be enough in-sync replicas (ISR) to maintain availability, risking data unavailability or loss.

---

## 4. Which metric indicates broker overload?

**Answer**
**RequestHandlerAvgIdlePercent** is the primary metric. It shows the percentage of time request handler threads are idle. 
**Practical insight:** Values < 10% indicate broker overload (handlers are 90%+ busy), causing request queuing and increased latency. Low idle percent means the broker can't keep up with request volume and may need more threads (`num.network.threads`) or horizontal scaling.

---

## 5. Which metric would you alert on first?

**Answer**
**ActiveControllerCount** should be alerted first (must be exactly 1). If this is wrong (0 or >1), the cluster is broken. 
**Practical insight:** Alert hierarchy: 
1) ActiveControllerCount (cluster coordination), 
2) UnderReplicatedPartitions (data safety), 
3) RequestHandlerAvgIdlePercent (broker capacity), 
4) Consumer lag (application health). Consumer lag is important but downstream issues can't be fixed if brokers are unhealthy.
