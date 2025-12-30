# Kafka Consumer Lag - Interview Guide

## 1. What consumer lag is
**Answer:**
Consumer lag is the difference between the **end offset** (latest message available in a partition) and the **committed offset** (last offset the consumer group has successfully processed). It measures how many unprocessed messages a consumer group is behind. Lag is calculated per partition: `lag = end_offset - committed_offset`. Total lag is the sum across all partitions.

---

## 2. Why lag is not always bad
**Answer:**
Lag is not always bad because:

* **Temporary spikes are normal**: During traffic bursts, producers may temporarily outpace consumers. As long as lag **decreases** after the spike, this is acceptable.
* **Rebalancing**: When consumers join/leave (scaling, deployments), consumption pauses briefly, causing temporary lag accumulation.
* **Expected behavior**: Some systems are designed to handle lag (e.g., batch processing, eventual consistency).
* **Key indicator**: The **trend** matters more than absolute value - increasing lag = problem, decreasing lag = recovering.

---

## 3. Difference between increasing vs decreasing lag
**Answer:**

**Decreasing Lag:**
* **Consumer speed > Producer speed**: Consumers process messages faster than new ones arrive
* **Indicates**: System is **catching up**, recovering from spikes, healthy state
* **Example**: After a traffic spike, lag decreases as consumers process the backlog

**Increasing Lag:**
* **Producer speed > Consumer speed**: New messages arrive faster than consumers can process
* **Indicates**: **Problem** - consumers can't keep up, potential bottleneck
* **Example**: Hot partition, slow consumer, or consumer failure

**Key Insight**: The **rate of change** (lag velocity) is critical - rapidly increasing lag requires immediate attention.

---

## 4. Why max partition lag matters

**Answer:**
Max partition lag matters more than total lag because:

* **Bottleneck Detection**: A single partition with high lag indicates a specific problem (hot partition, slow consumer processing that partition). Total lag can hide this - you might have low total lag but one partition severely behind.

* **Consumer Parallelism**: Consumers process partitions in parallel. If one partition has high lag, it becomes the bottleneck. Other partitions may be caught up, but the slow partition limits overall progress.

* **Hot Partition Indicator**: Max lag often reveals hot partitions (many messages with same key → same partition), which can't be solved by adding more consumers.

* **Alerting Strategy**: Production systems typically alert on max partition lag, not total lag, because it identifies the actual problem. Example: Total lag = 1000 (distributed), vs Max partition lag = 950 (one partition) - the latter is more concerning.

* **User Impact**: For keyed messages, high lag on one partition means specific users/entities experience delays, even if total lag looks acceptable.

---

## 5. Common real-world causes of lag

**Answer:**

* **Traffic/Throughput Spikes**: Sudden increase in message production rate temporarily outpaces consumer capacity.

* **Rebalancing**: Consumer group membership changes (scaling, deployments, pod restarts in K8s) cause consumption pauses, accumulating lag during rebalance.

* **Hot Partition**: Many messages use the same key, all going to one partition. That partition becomes a bottleneck, causing high lag on that specific partition.

* **Slow Consumer Processing**: Processing time exceeds `max.poll.interval.ms`, causing consumer to be considered dead, triggering rebalance and lag accumulation.

* **Consumer Failures/Crashes**: Consumer instances crash or are killed, their assigned partitions stop processing until rebalance reassigns them.

* **Downstream System Slowness**: Consumer waits for external systems (database, APIs) that are slow, causing processing delays.

* **Insufficient Consumer Instances**: Not enough consumers to handle partition count (max parallelism = partition count).

---

## 6. How teams alert on lag in production

**Answer:**

**Primary Metrics:**

* **Max Partition Lag**: Most important - alerts when any single partition exceeds threshold (e.g., > 10,000 messages). Identifies bottlenecks and hot partitions.

* **Average Partition Lag**: Useful for overall health, but can hide individual partition problems.

* **Lag Velocity (Rate of Change)**: Alert on rapidly increasing lag (e.g., > 1,000 messages/minute), even if absolute lag is low. Indicates sudden problems.

**Supporting Metrics:**

* **Commit Latency**: Time between message consumption and offset commit. High latency indicates slow processing.

* **Poll Latency**: Time between poll() calls. High latency indicates consumer is stuck or processing slowly.

**Alerting Strategy:**

* **Warning**: Max partition lag > 5,000 messages OR lag increasing > 500/min
* **Critical**: Max partition lag > 50,000 messages OR lag increasing > 5,000/min
* **Alert on trends**: Not just absolute values - rapidly increasing lag requires immediate attention

**Real-world Example:**
```
Alert: max_partition_lag > 10,000
Action: Check for hot partition, slow consumer, or rebalancing issues
```
