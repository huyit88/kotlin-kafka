# Kafka Rebalancing - Interview Guide

## 1. What triggers a Kafka rebalance?

**Answer:**

A Kafka rebalance is triggered when the **consumer group membership changes**:

* **Consumer joins**: New consumer instance joins the group (scale-up, new pod in Kubernetes)
* **Consumer leaves**: Consumer instance stops or crashes (scale-down, pod termination, application restart)
* **Consumer timeout**: Consumer exceeds `max.poll.interval.ms` (slow processing, dead consumer detection)
* **Session timeout**: Consumer fails to send heartbeat within `session.timeout.ms` (network issues, GC pauses)
* **Group coordinator change**: Broker hosting the group coordinator fails

**Real-world scenario**: During a Kubernetes rolling deployment, old pods terminate while new pods start, causing multiple rebalances in quick succession.

---

## 2. Why consumption pauses during rebalance?

**Answer:**

Consumption pauses during rebalance to **maintain ordering guarantees and prevent duplicate processing**:

* **Partition ownership transfer**: Consumers must stop processing before partitions are reassigned to prevent:
  * **Duplicate processing**: Same message processed by multiple consumers
  * **Ordering violations**: Messages processed out of sequence during handoff
* **Offset commit safety**: Consumers commit their current offsets before losing partition ownership
* **Coordinated assignment**: Group coordinator needs time to calculate new partition assignments

**During rebalance:**
1. All consumers stop processing (revoke phase)
2. Group coordinator calculates new assignments
3. Consumers receive new partition assignments (assign phase)
4. Processing resumes from committed offsets

**Impact**: In eager rebalancing, this causes a "stop-the-world" pause. Cooperative rebalancing reduces this by doing incremental reassignments.

---

## 3. Difference between eager vs cooperative rebalancing

**Answer:**

### Eager Rebalancing (Legacy - Range, RoundRobin, Sticky)
* **Full revocation**: All consumers revoke **all** their partitions before new assignment
* **Stop-the-world**: Complete consumption pause during entire rebalance
* **Longer downtime**: All partitions are idle during reassignment
* **Example**: 3 consumers, 6 partitions → all 6 partitions revoked, then reassigned

### Cooperative Rebalancing (Modern - CooperativeStickyAssignor)
* **Incremental revocation**: Only partitions that need to move are revoked
* **Partial pause**: Consumers continue processing partitions they keep
* **Shorter downtime**: Only affected partitions pause briefly
* **Gradual reassignment**: Partitions move incrementally, reducing disruption
* **Example**: 3 consumers, 6 partitions → only 2 partitions need to move, 4 continue processing

**Key difference**: Eager = "revoke everything, then reassign". Cooperative = "revoke only what's needed, reassign incrementally".

**Production impact**: Cooperative rebalancing reduces pause time from seconds to milliseconds, critical for high-throughput systems.

---

## 4. Why rebalances are dangerous in Kubernetes

**Answer:**

Rebalances are particularly dangerous in Kubernetes due to **frequent pod lifecycle events**:

* **Rolling deployments**: Every deployment triggers rebalances as old pods terminate and new pods start
  * **Cascading rebalances**: Multiple rebalances in quick succession (pod1 stops → rebalance → pod2 stops → rebalance → ...)
  * **Extended downtime**: Each rebalance pauses consumption, compounding during rolling updates
* **Pod evictions**: Kubernetes may evict pods (resource pressure, node maintenance), causing unexpected rebalances
* **Health check failures**: Liveness/readiness probe failures restart pods, triggering rebalances
* **Offset lag accumulation**: During rebalance pauses, messages accumulate, creating:
  * **Consumer lag spikes**: Backlog builds up during pauses
  * **Downstream impact**: Delayed processing affects dependent systems
* **Potential offset loss**: If consumers don't commit offsets before termination, some messages may be reprocessed or skipped
* **Resource contention**: Multiple rebalances increase CPU/memory usage on brokers (group coordinator)

**Real-world scenario**: A rolling deployment of 10 pods causes 10 rebalances (one per pod replacement). With eager rebalancing, this could mean 10+ seconds of total pause time, causing significant lag.

---

## 5. How to minimize rebalance impact in production

**Answer:**

**Configuration strategies:**

* **Use Cooperative Rebalancing**: Set `partition.assignment.strategy=CooperativeStickyAssignor`
  * Reduces pause time from seconds to milliseconds
  * Allows incremental partition reassignment

* **Tune timeouts appropriately**:
  * `max.poll.interval.ms` > maximum processing time (prevent false timeouts)
  * `session.timeout.ms` > expected GC/heartbeat delays (prevent false failures)
  * Balance: Too high = slow failure detection, too low = false rebalances

* **Optimize processing time**: 
  * Keep message processing fast (< `max.poll.interval.ms`)
  * Use async processing for long operations
  * Batch processing to improve throughput

**Operational strategies:**

* **Gradual scaling**: Add/remove consumers gradually, not all at once
  * Example: Scale up 1 pod at a time, wait for rebalance to complete, then add next

* **Staggered deployments**: Use deployment strategies that minimize simultaneous pod restarts
  * Example: Rolling updates with `maxSurge=1, maxUnavailable=0`

* **Pre-commit offsets**: Ensure consumers commit offsets frequently (auto-commit or manual commit)
  * Prevents message reprocessing after rebalance

* **Monitor rebalance frequency**: Alert on excessive rebalances
  * Indicates configuration issues or infrastructure problems

* **Use static group membership** (Kafka 2.3+): `group.instance.id` prevents rebalance on restart
  * Pod restarts don't trigger rebalance if same `group.instance.id` is used

**Example production setup:**
```kotlin
props[ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG] = 
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
props[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 300000  // 5 minutes
props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 45000     // 45 seconds
props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
props[ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG] = 5000  // Commit every 5s
```

---

## Summary: Rebalance Best Practices

1. ✅ **Always use Cooperative Rebalancing** in production
2. ✅ **Set timeouts higher than processing time** (prevent false rebalances)
3. ✅ **Scale gradually** (one instance at a time)
4. ✅ **Monitor rebalance frequency** (alert on spikes)
5. ✅ **Commit offsets frequently** (prevent reprocessing)
6. ✅ **Use static group membership** for predictable deployments (Kafka 2.3+)
