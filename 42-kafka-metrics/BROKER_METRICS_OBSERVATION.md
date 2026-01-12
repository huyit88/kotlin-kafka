# Broker Metrics Observation

## How to Access JMX Metrics

### Method 1: Using jconsole
1. Start Kafka: `docker compose up -d`
2. Run: `jconsole localhost:9999`
3. Navigate to MBeans tab
4. Expand the tree: `kafka.server`, `kafka.controller`, `kafka.log`

### Method 2: Using jmxterm (command line)
```bash
# Install jmxterm if needed
wget https://github.com/jiaqi/jmxterm/releases/download/v1.0.4/jmxterm-1.0.4-uber.jar

# Connect and query
java -jar jmxterm-1.0.4-uber.jar
> open localhost:9999
> bean kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
> get Value
```

---

## 1. UnderReplicatedPartitions

**JMX Path:** `kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions`

**Current Value:** 0

**What "Healthy" Looks Like:**
- **Value: 0** - All partitions are fully replicated. This is the ideal state.
- Healthy cluster should maintain 0 under-replicated partitions under normal conditions.

**What a Bad Value Means:**
- **Value > 0** - Indicates one or more partitions have fewer replicas than configured replication factor.
- **Root Causes:**
  - Broker failure: One or more brokers are down, causing replicas to be unavailable
  - Network issues: Brokers cannot communicate to replicate data
  - Disk I/O problems: Brokers cannot write replicas due to disk failures
  - Insufficient brokers: Cluster has fewer brokers than replication factor requires
- **Impact:** 
  - Risk of data loss if the leader fails
  - Reduced fault tolerance
  - Potential unavailability if leader fails and no ISR (In-Sync Replicas) available
- **Action:** Investigate broker health, check logs, verify network connectivity, ensure sufficient brokers exist

---

## 2. ActiveControllerCount

**JMX Path:** `kafka.controller:type=KafkaController,name=ActiveControllerCount`

**Current Value:** 1

**What "Healthy" Looks Like:**
- **Value: 1** - Exactly one broker is acting as the controller. This is correct for any cluster.
- The controller manages partition leadership, broker failures, and cluster metadata.

**What a Bad Value Means:**
- **Value: 0** - No active controller. Cluster is inoperable - cannot elect leaders, handle broker failures, or manage metadata.
  - **Action:** Critical! Restart brokers, check Zookeeper connectivity
- **Value > 1** - Multiple controllers (split-brain scenario). Extremely dangerous - can cause data corruption.
  - **Action:** Emergency! Stop all brokers, investigate Zookeeper quorum, restart one broker at a time
- **Root Causes:**
  - Zookeeper connection issues preventing controller election
  - Network partitions causing multiple brokers to think they're controller
  - Zookeeper session expiration
- **Impact:**
  - Value 0: Complete cluster unavailability
  - Value > 1: Data corruption, inconsistent metadata, potential message loss

---

## 3. RequestHandlerAvgIdlePercent

**JMX Path:** `kafka.server:type=RequestHandlerPool,name=RequestHandlerAvgIdlePercent`

**Current Value:** 1.0 (1.0% idle = 99% busy)

**What "Healthy" Looks Like:**
- **Value: > 20%** - Request handler threads have adequate idle time to process requests without queuing.
- Healthy brokers typically show 20-80% idle time depending on load.
- Higher idle percent = more headroom for traffic spikes.

**What a Bad Value Means:**
- **Value: < 10%** - Request handlers are saturated, requests are queuing.
- **Value: < 5%** - Critical overload - requests are backing up significantly.
- **Value: 0%** - Threads are 100% busy, severe bottleneck.
- **Current Value 1.0%** - ⚠️ **CRITICAL**: Handlers are 99% utilized, indicating severe saturation. Requests are likely queuing, and the broker has minimal capacity for additional load.
- **Root Causes:**
  - High producer/consumer throughput exceeding broker capacity
  - Too few request handler threads (configured via `num.network.threads`)
  - Slow disk I/O causing handlers to wait
  - Network bottlenecks
  - Large message sizes requiring more processing time
- **Impact:**
  - Increased request latency
  - Timeout errors for clients
  - Consumer lag growth
  - Producer backpressure
- **Action:** 
  - Increase `num.network.threads` (default 8, try 16-32)
  - Scale horizontally (add more brokers)
  - Investigate disk I/O performance
  - Check for large message sizes or inefficient serialization

---

## 4. LogDirSize

**JMX Path:** `kafka.log:type=Log,name=Size,topic=<topic>,partition=<partition>`

**Note:** LogDirSize is typically observed per topic/partition. For overall disk usage, check:
- `kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec` (ingress)
- `kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec` (egress)
- Or use OS-level disk monitoring: `df -h` on broker data directories

**Current Value:** [Observe for specific topics/partitions, or overall disk usage]

**What "Healthy" Looks Like:**
- **Disk Usage: < 80%** - Adequate space for log retention and growth.
- Log segments are being cleaned up per retention policy (time or size-based).
- No disk full errors in logs.

**What a Bad Value Means:**
- **Disk Usage: > 85%** - Approaching capacity, risk of disk full.
- **Disk Usage: > 90%** - Critical - broker may stop accepting writes.
- **Disk Usage: 100%** - Broker stops accepting new messages, existing consumers may fail.
- **Root Causes:**
  - Retention policy too long (messages not being deleted)
  - High message volume exceeding cleanup rate
  - Disk too small for workload
  - Log compaction not running (for compacted topics)
  - Multiple topics with large retention
- **Impact:**
  - Broker stops accepting writes (disk full)
  - Existing consumers may fail reading from disk
  - Potential data loss if broker crashes with full disk
  - Cluster instability
- **Action:**
  - Reduce retention time/size: `log.retention.hours` or `log.retention.bytes`
  - Add more disk space
  - Increase cleanup frequency: `log.retention.check.interval.ms`
  - Enable log compaction for appropriate topics
  - Scale out to distribute load across more brokers

---

## Summary

These four metrics provide a comprehensive view of broker health:

1. **UnderReplicatedPartitions** - Data safety and fault tolerance
2. **ActiveControllerCount** - Cluster coordination and availability  
3. **RequestHandlerAvgIdlePercent** - Request processing capacity
4. **LogDirSize** - Storage capacity and retention management

Monitor these metrics continuously in production and set alerts:
- UnderReplicatedPartitions > 0
- ActiveControllerCount != 1
- RequestHandlerAvgIdlePercent < 10%
- Disk usage > 85%
