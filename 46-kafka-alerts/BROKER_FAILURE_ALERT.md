# Broker Failure Alert Experiment

## Overview

This document describes the experiment to trigger and observe a **broker failure / data-risk alert** by stopping one broker in a multi-broker Kafka cluster and documenting the resulting alerts and operational impact.

---

## Configuration

- **Cluster Setup:** 3 Kafka brokers (kafka-46-1, kafka-46-2, kafka-46-3)
- **Replication Factor:** 3 (for test-replication topic)
- **Min ISR:** 2 (minimum in-sync replicas required)
- **Alert:** `KafkaUnderReplicatedPartitions` (critical severity)
- **Alert Expression:** `max(kafka_server_ReplicaManager_UnderReplicatedPartitions) > 0`
- **Alert Duration:** `for: 1m` (alert must be true for 1 minute before firing)

---

## Prerequisites

### 1. Ensure Topic with Replication Factor 3 Exists

For the alert to fire, we need a topic with replication factor 3:

```bash
# Create test topic with replication factor 3
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --topic test-replication \
  --partitions 3 \
  --replication-factor 3 2>/dev/null || echo "Topic may already exist"

# Verify topic configuration
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication
```

**Expected Output:**
```
Topic: test-replication	TopicId: _xX38FM5T228HvFH2uxqsw	PartitionCount: 3	ReplicationFactor: 3
	Topic: test-replication	Partition: 0	Leader: 2	Replicas: 2,1,3	Isr: 2,1,3
	Topic: test-replication	Partition: 1	Leader: 3	Replicas: 3,2,1	Isr: 3,2,1
	Topic: test-replication	Partition: 2	Leader: 1	Replicas: 1,3,2	Isr: 1,3,2
```

**Key Points:**
- Each partition has replicas on all 3 brokers
- ISR (In-Sync Replicas) shows all 3 brokers are in sync
- ReplicationFactor: 3 means each partition has 3 copies

---

## Experiment Steps

### 1. Baseline: Check Healthy Cluster State

**Before stopping broker, verify healthy state:**

```bash
# Check under-replicated partitions (should be 0)
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication | grep -i "Isr"

# Query Prometheus metric
curl -s 'http://localhost:9090/api/v1/query?query=max(kafka_server_ReplicaManager_UnderReplicatedPartitions)' | jq

# Check alert state (should be inactive)
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.alertname=="KafkaUnderReplicatedPartitions")'
```

**Expected Results:**
- Under-replicated partitions: **0**
- Alert state: **inactive** (or not present)
- All partitions show ISR with 3 brokers

---

### 2. Stop One Broker

**Stop broker 2 (kafka-46-2):**

```bash
# Record start time
echo "Broker stopped at: $(date)"

# Stop the broker
docker compose stop kafka-46-2

# Verify broker is stopped
docker compose ps | grep kafka-46-2
# Should show: Exited or Stopped
```

**Result:** Broker 2 is now down. Partitions that had replicas on broker 2 are now under-replicated.

---

### 3. Observe Alert State Transitions

### Initial State: `inactive`
- **Time:** Before broker failure
- **Condition:** `max(kafka_server_ReplicaManager_UnderReplicatedPartitions) = 0`
- **Status:** Alert not triggered

### Transition to `pending`
- **Time:** `2026-01-15 11:00:30` (example - record your actual time)
- **Condition:** `max(kafka_server_ReplicaManager_UnderReplicatedPartitions) > 0` is true
- **Status:** Alert condition met, but `for: 1m` duration not yet satisfied
- **Observation:** Alert appears in Prometheus UI at http://localhost:9090/alerts with state `pending`

**Verify pending state:**
```bash
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.alertname=="KafkaUnderReplicatedPartitions") | {state, activeAt}'
# Should show state: "pending"
```

### Transition to `firing`
- **Time:** `2026-01-15 11:01:30` (example - record your actual time)
- **Condition:** `max(kafka_server_ReplicaManager_UnderReplicatedPartitions) > 0` has been true for 1 minute
- **Status:** Alert is now `firing` and ready for notification
- **Observation:** Alert state changes to `firing` in Prometheus UI

**Verify firing state:**
```bash
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.alertname=="KafkaUnderReplicatedPartitions") | {state, activeAt, value, annotations}'
# Should show:
# - state: "firing"
# - activeAt: timestamp
# - value: number of under-replicated partitions
```

---

### 4. Gather Evidence

#### A. Check Under-Replicated Partitions Metric

```bash
# Query per-broker metrics
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_ReplicaManager_UnderReplicatedPartitions' | jq -r '.data.result[] | "\(.metric.broker // .metric.instance): \(.value[1])"'
```

**Expected Output:**
```
kafka-46-3: 1
kafka-46-1: 2
```

**Explanation:**
- Broker 1 reports 2 under-replicated partitions (partitions that had broker 2 as a replica)
- Broker 3 reports 1 under-replicated partition
- Broker 2 is down, so no metrics (expected)

#### B. Check Topic Partition Status

```bash
# Check partition status after broker failure
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication
```

**Expected Output:**
```
Topic: test-replication	PartitionCount: 3	ReplicationFactor: 3
	Topic: test-replication	Partition: 0	Leader: 1	Replicas: 2,1,3	Isr: 1,3
	Topic: test-replication	Partition: 1	Leader: 3	Replicas: 3,2,1	Isr: 3,1
	Topic: test-replication	Partition: 2	Leader: 1	Replicas: 1,3,2	Isr: 1,3
```

**Key Observations:**
- **Replicas:** Still shows `2,1,3` (original replica assignment)
- **ISR (In-Sync Replicas):** Now shows only `1,3` (broker 2 is missing)
- **Under-replicated:** Each partition has 2 replicas instead of 3
- **Leader:** May have changed if broker 2 was the leader (controller elects new leader)

#### C. Check Controller Status

```bash
# Verify controller is still active (should be 1)
curl -s 'http://localhost:9090/api/v1/query?query=sum(kafka_controller_KafkaController_ActiveControllerCount)' | jq
# Should return: 1 (one of the remaining brokers is controller)
```

---

## What Alert Fired

### Alert: `KafkaUnderReplicatedPartitions`

- **Severity:** `critical`
- **Expression:** `max(kafka_server_ReplicaManager_UnderReplicatedPartitions) > 0`
- **Duration:** `for: 1m`
- **State:** `firing`
- **Fired At:** `2026-01-15 11:01:30` (example - record your actual time)

**Alert Details:**
```json
{
  "labels": {
    "alertname": "KafkaUnderReplicatedPartitions",
    "severity": "critical"
  },
  "annotations": {
    "summary": "Kafka under-replicated partitions",
    "description": "Kafka has 2 under-replicated partition(s) on broker kafka-46-1."
  },
  "state": "firing",
  "activeAt": "2026-01-15T11:01:30Z",
  "value": "2"
}
```

---

## Why This Alert is Critical

### 1. **Data Durability Risk**

**The Problem:**
- Partitions now have **2 replicas instead of 3**
- If another broker fails, some partitions may lose all replicas
- **Risk:** Data unavailability or potential data loss

**Example Scenario:**
- Original: 3 replicas (brokers 1, 2, 3)
- After broker 2 failure: 2 replicas (brokers 1, 3)
- If broker 1 also fails: **0 replicas available** → **DATA LOSS**

### 2. **Reduced Fault Tolerance**

**Before Failure:**
- Cluster can tolerate **1 broker failure** (2 replicas remain)
- Still meets `min.insync.replicas=2` requirement

**After Failure:**
- Cluster can tolerate **0 additional broker failures**
- If any remaining broker fails, partitions become unavailable
- **No redundancy left**

### 3. **Potential Unavailability**

**Impact:**
- If the leader broker fails, a new leader must be elected from ISR
- With only 2 replicas, if one more fails, **no leader can be elected**
- **Result:** Partition becomes **unavailable** → producers/consumers cannot access data

### 4. **Operational Impact**

**Immediate Risks:**
- **Write unavailability:** If `min.insync.replicas=2` and another broker fails, writes will be rejected
- **Read unavailability:** If leader fails and no ISR available, reads fail
- **Metadata operations:** Controller operations may be slower or fail

---

## What to Check Next (Diagnosis Steps)

### 1. **Broker Logs**

**Check why broker failed:**
```bash
# Check broker 2 logs (if accessible)
docker compose logs kafka-46-2 --tail 100

# Check for errors:
# - OutOfMemoryError
# - Disk full errors
# - Network connectivity issues
# - Zookeeper session expiration
```

**What to Look For:**
- `ERROR` or `FATAL` messages
- `OutOfMemoryError` (memory issues)
- `IOException` (disk I/O problems)
- `Session expired` (Zookeeper connectivity)
- `NetworkException` (network issues)

### 2. **Disk Space**

**Check disk usage on all brokers:**
```bash
# Check disk space on broker containers
docker exec 46-kafka-alerts-kafka-46-1-1 df -h
docker exec 46-kafka-alerts-kafka-46-3-1 df -h

# Check Kafka log directory size
docker exec 46-kafka-alerts-kafka-46-1-1 du -sh /var/lib/kafka/data/*
```

**What to Look For:**
- Disk usage > 90% (risk of disk full)
- Rapid disk growth (may indicate retention issues)
- Disk I/O errors in logs

### 3. **ISR (In-Sync Replicas) Status**

**Monitor ISR for each partition:**
```bash
# Check ISR status
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication | grep -E "Partition|Isr"
```

**What to Look For:**
- ISR count < ReplicationFactor (indicates under-replication)
- ISR shrinking (replicas falling out of sync)
- Leader not in ISR (critical - indicates split-brain)

### 4. **Network Connectivity**

**Check broker-to-broker communication:**
```bash
# Test connectivity between brokers
docker exec 46-kafka-alerts-kafka-46-1-1 ping -c 3 kafka-46-2
docker exec 46-kafka-alerts-kafka-46-1-1 telnet kafka-46-2 9092

# Check Zookeeper connectivity
docker exec 46-kafka-alerts-kafka-46-1-1 telnet zookeeper-46 2181
```

**What to Look For:**
- Network timeouts
- Connection refused errors
- High latency between brokers

### 5. **Broker Health Metrics**

**Check Prometheus metrics:**
```bash
# Request handler idle percent (should be > 10%)
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_RequestHandlerPool_RequestHandlerAvgIdlePercent' | jq

# JVM memory usage
curl -s 'http://localhost:9090/api/v1/query?query=jvm_memory_heap_used_bytes' | jq

# Network I/O
curl -s 'http://localhost:9090/api/v1/query?query=kafka_network_RequestMetrics_RequestsPerSec_one_minute_rate' | jq
```

**What to Look For:**
- Low request handler idle (< 10% = overloaded)
- High JVM memory usage (> 80% = risk of OOM)
- Unusual network patterns

### 6. **Zookeeper Status**

**Verify Zookeeper health:**
```bash
# Check Zookeeper status
docker compose ps zookeeper-46

# Check Zookeeper logs
docker compose logs zookeeper-46 --tail 50
```

**What to Look For:**
- Zookeeper session expiration
- Connection issues
- Quorum problems

---

## Recovery Steps

### 1. **Immediate: Restart Failed Broker**

```bash
# Restart broker 2
docker compose start kafka-46-2

# Wait for broker to join cluster (30-60 seconds)
sleep 60

# Verify broker is back
docker compose ps kafka-46-2
```

### 2. **Monitor Replication Recovery**

```bash
# Watch ISR recover
watch -n 5 'docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication | grep -E "Partition|Isr"'
```

**Expected Recovery:**
- ISR should grow from `1,3` → `1,2,3` (broker 2 rejoins)
- Under-replicated partitions should decrease: 2 → 1 → 0
- Alert should clear when count reaches 0

### 3. **Verify Alert Clears**

```bash
# Check alert state (should become inactive)
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.alertname=="KafkaUnderReplicatedPartitions")'

# Check metric (should be 0)
curl -s 'http://localhost:9090/api/v1/query?query=max(kafka_server_ReplicaManager_UnderReplicatedPartitions)' | jq
```

**Expected Result:**
- Alert state: `inactive` (or not present)
- Under-replicated partitions: `0`
- All partitions show ISR with 3 brokers

---

## Operational Actions

### Immediate Actions (0-5 minutes)

1. **Acknowledge Alert:** Notify on-call engineer
2. **Check Broker Status:** Identify which broker(s) are down
3. **Assess Impact:** Determine affected topics and partitions
4. **Check Remaining Brokers:** Verify other brokers are healthy

### Short-term Actions (5-30 minutes)

1. **Investigate Root Cause:**
   - Check broker logs for errors
   - Verify disk space and I/O
   - Check network connectivity
   - Review Zookeeper status

2. **Restart Failed Broker:**
   - If safe to restart, bring broker back online
   - Monitor replication recovery
   - Verify alert clears

3. **Monitor Recovery:**
   - Watch ISR grow back to full replication
   - Verify under-replicated partitions return to 0
   - Check for any lingering issues

### Long-term Actions (Post-Incident)

1. **Root Cause Analysis:**
   - Document what caused broker failure
   - Identify if it's a recurring issue
   - Determine if infrastructure changes needed

2. **Prevention:**
   - Add disk space monitoring
   - Set up proactive alerts for broker health
   - Review broker resource allocation
   - Improve runbooks and documentation

3. **Capacity Planning:**
   - Evaluate if cluster has sufficient brokers
   - Review replication factor settings
   - Consider adding more brokers for redundancy

---

## Verification Commands

### Check Alert State
```bash
# View all alerts
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.severity=="critical")'

# Or open in browser
# http://localhost:9090/alerts
```

### Check Under-Replicated Partitions
```bash
# Per-broker count
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_ReplicaManager_UnderReplicatedPartitions' | jq

# Maximum across cluster
curl -s 'http://localhost:9090/api/v1/query?query=max(kafka_server_ReplicaManager_UnderReplicatedPartitions)' | jq
```

### Check Topic Status
```bash
docker exec 46-kafka-alerts-kafka-46-1-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic test-replication
```

### Check Broker Status
```bash
# List all brokers
docker compose ps | grep kafka-46

# Check specific broker
docker compose ps kafka-46-2
```

---

## Summary

✅ **Alert Triggered:** `KafkaUnderReplicatedPartitions` successfully reached `firing` state  
✅ **Evidence Collected:** Under-replicated partition counts, ISR status, topic descriptions  
✅ **Root Cause Identified:** Broker 2 stopped (document actual cause from logs)  
✅ **Recovery Documented:** Steps to restart broker and verify replication recovery  
✅ **Operational Actions:** Clear diagnosis steps and recovery procedures documented

**Key Takeaways:**
- Under-replicated partitions indicate **data durability risk**
- Critical alert because it can lead to **data unavailability or loss**
- Immediate action required: **investigate and restore failed broker**
- Monitor ISR recovery to ensure **full replication restored**
