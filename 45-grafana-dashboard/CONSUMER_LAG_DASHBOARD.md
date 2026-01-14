# Consumer Lag Dashboard - Grafana Visualization Guide

## ⚠️ Important Note: Conceptual Dashboard

**This is a conceptual dashboard.** The current JMX exporter setup only exposes **broker metrics** (kafka.server, kafka.network, kafka.log) and does **not** expose consumer lag metrics.

**To get consumer lag metrics, you would need:**
- A Kafka Exporter (like `kafka-exporter` or `burrow`) that queries consumer groups
- Or consumer applications that expose their own lag metrics
- Or use Kafka Admin API to calculate lag: `Lag = LogEndOffset - ConsumerOffset`

**Current Available Metrics:**
- ✅ `kafka_log_Log_LogEndOffset` - Log end offset (available)
- ❌ `kafka_consumergroup_lag` - Consumer lag (NOT available with current setup)
- ❌ `kafka_consumer_offset` - Consumer offset (NOT available with current setup)

**This document describes how consumer lag would be visualized IF consumer lag metrics were available.**

---

## Overview

Consumer lag is a critical metric for monitoring Kafka consumer health. This dashboard provides visualization and alerting for consumer lag across consumer groups, topics, and partitions.

---

## Dashboard Panels

### Panel 1: Total Consumer Lag (Stat Panel)

**Query:**
```promql
sum(kafka_consumergroup_lag) by (consumergroup)
```

**Visualization:** Stat panel showing total lag per consumer group

**What it shows:**
- Total unprocessed messages across all partitions for each consumer group
- Quick overview of which consumer groups are behind

**Thresholds:**
- Green: < 1,000 messages
- Yellow: 1,000 - 10,000 messages
- Red: > 10,000 messages

---

### Panel 2: Max Partition Lag (Critical Metric)

**Query:**
```promql
max(kafka_consumergroup_lag) by (consumergroup, topic, partition)
```

**Visualization:** Time series graph

**What it shows:**
- The highest lag across all partitions for each consumer group
- Identifies bottleneck partitions (hot partitions)

**Why it matters:**
- A single partition with high lag can indicate a hot partition or slow consumer
- Total lag can hide individual partition problems
- Max partition lag reveals the actual bottleneck

**Legend:** `{{consumergroup}}/{{topic}}/{{partition}}`

---

### Panel 3: Lag Trend (Time Series)

**Query:**
```promql
kafka_consumergroup_lag
```

**Visualization:** Time series graph with multiple series (one per consumer group/topic/partition)

**What it shows:**
- Historical lag values over time
- Trend direction (increasing = problem, decreasing = recovery)
- Visual comparison across partitions

**Key Insight:** The **slope/trend** is more important than absolute value:
- **Upward slope** = Lag increasing (consumers falling behind) ⚠️
- **Downward slope** = Lag decreasing (consumers catching up) ✅
- **Flat line** = Lag stable (consumers keeping up) ✅

---

### Panel 4: Lag Rate of Change (Lag Velocity)

**Query:**
```promql
rate(kafka_consumergroup_lag[5m])
```

**Visualization:** Time series graph

**What it shows:**
- How fast lag is changing (messages per second)
- Positive values = lag increasing
- Negative values = lag decreasing

**Alerting:** Alert if lag velocity > 100 messages/second (rapidly increasing lag)

---

### Panel 5: Lag by Topic (Bar Chart)

**Query:**
```promql
sum(kafka_consumergroup_lag) by (topic)
```

**Visualization:** Bar chart or time series

**What it shows:**
- Total lag per topic
- Identifies which topics have the most backlog

---

### Panel 6: Lag by Partition (Table)

**Query:**
```promql
kafka_consumergroup_lag
```

**Visualization:** Table

**Columns:**
- Consumer Group
- Topic
- Partition
- Current Lag
- Trend (increasing/decreasing)

**What it shows:**
- Detailed view of lag per partition
- Easy to identify specific partitions with high lag

---

## PromQL Queries Reference

### Basic Lag Query
```promql
kafka_consumergroup_lag
```

### Total Lag per Consumer Group
```promql
sum(kafka_consumergroup_lag) by (consumergroup)
```

### Max Partition Lag
```promql
max(kafka_consumergroup_lag) by (consumergroup, topic)
```

### Lag for Specific Consumer Group
```promql
kafka_consumergroup_lag{consumergroup="my-consumer-group"}
```

### Lag for Specific Topic
```promql
kafka_consumergroup_lag{topic="my-topic"}
```

### Lag Rate of Change
```promql
rate(kafka_consumergroup_lag[5m])
```

### Lag Acceleration (Rate of Change of Rate)
```promql
deriv(rate(kafka_consumergroup_lag[5m])[5m:])
```

---

## Why Lag Trend Matters More Than Absolute Value

### 1. Context-Dependent Absolute Values

**Example:**
- **Lag = 10,000 messages** could be:
  - **Normal** if production rate is 1,000 msg/sec and consumer processes 1,000 msg/sec (10 seconds of buffer)
  - **Critical** if production rate is 10 msg/sec and consumer processes 5 msg/sec (lag will grow to 50,000+)

**Key Insight:** Absolute lag value alone doesn't tell you if it's a problem. You need to know the production/consumption rates.

---

### 2. Trend Reveals System State

**Increasing Lag Trend:**
- **Indicates:** Consumers can't keep up with production
- **Action Required:** Investigate consumer performance, scale up, or optimize processing
- **Urgency:** High - lag will continue growing

**Decreasing Lag Trend:**
- **Indicates:** Consumers are catching up, processing faster than production
- **Action Required:** None - system is recovering
- **Urgency:** Low - system is self-correcting

**Stable Lag Trend:**
- **Indicates:** Consumers keeping up with production
- **Action Required:** None - healthy state
- **Urgency:** None

---

### 3. Trend Detects Problems Early

**Scenario:**
- **Current lag:** 500 messages (seems low)
- **Trend:** Increasing at 100 messages/minute
- **In 10 minutes:** Lag will be 1,500 messages
- **In 1 hour:** Lag will be 6,500 messages

**Without trend:** You might ignore 500 messages as "low"
**With trend:** You see it's increasing and can act proactively

---

### 4. Trend Distinguishes Normal vs Problematic Lag

**Normal Lag Pattern:**
```
Time    Lag
10:00   100
10:05   500  (traffic spike)
10:10   300  (consumers catching up)
10:15   50   (back to normal)
```
**Trend:** Spikes up, then decreases → Normal behavior

**Problematic Lag Pattern:**
```
Time    Lag
10:00   100
10:05   500
10:10   1,000
10:15   2,000
10:20   4,000
```
**Trend:** Continuously increasing → Problem requiring action

---

### 5. Trend-Based Alerting

**Better Alert Strategy:**
- ❌ **Bad:** Alert when lag > 10,000 (absolute threshold)
  - Misses problems when lag is low but increasing rapidly
  - False positives when lag is high but stable/decreasing

- ✅ **Good:** Alert when lag increasing > 500 messages/minute (trend-based)
  - Catches problems early, regardless of absolute value
  - Reduces false positives

**Example Alert Rules:**
```promql
# Alert on rapidly increasing lag
rate(kafka_consumergroup_lag[5m]) > 100

# Alert on high lag that's also increasing
kafka_consumergroup_lag > 10000 AND rate(kafka_consumergroup_lag[5m]) > 50
```

---

## Dashboard Layout Recommendation

```
┌─────────────────────────────────────────────────┐
│  Consumer Lag Dashboard                         │
├─────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Total Lag│  │Max Part  │  │Lag Trend │    │
│  │ (Stat)   │  │Lag (Stat)│  │(Time Ser)│    │
│  └──────────┘  └──────────┘  └──────────┘    │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │  Lag Trend by Consumer Group (Time Series)│ │
│  │  [Multiple series, one per consumer group]│ │
│  └──────────────────────────────────────────┘ │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │  Lag by Topic (Bar Chart)                │ │
│  └──────────────────────────────────────────┘ │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │  Lag by Partition (Table)                 │ │
│  └──────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

---

## Alerting Strategy

### Warning Alerts
- **Max partition lag > 5,000 messages**
- **Lag increasing > 500 messages/minute**

### Critical Alerts
- **Max partition lag > 50,000 messages**
- **Lag increasing > 5,000 messages/minute**
- **Lag > 10,000 AND increasing > 1,000 messages/minute**

### Alert Queries
```promql
# Warning: High lag
max(kafka_consumergroup_lag) by (consumergroup) > 5000

# Critical: Rapidly increasing lag
rate(kafka_consumergroup_lag[5m]) > 100

# Critical: High lag that's growing
max(kafka_consumergroup_lag) by (consumergroup) > 10000 
AND 
rate(kafka_consumergroup_lag[5m]) > 50
```

---

## Key Takeaways

1. **Visualization:** Use time series graphs to show lag trends over time
2. **PromQL:** `kafka_consumergroup_lag` is the base metric, use aggregations for different views
3. **Trend > Absolute Value:** 
   - Increasing trend = problem (act now)
   - Decreasing trend = recovery (monitor)
   - Stable trend = healthy (no action)
4. **Max Partition Lag:** More important than total lag (identifies bottlenecks)
5. **Rate of Change:** Use `rate()` to detect rapidly increasing lag early

---

## Notes

### Metric Availability

**Current Setup (JMX Exporter):**
- ❌ **NOT Available:** `kafka_consumergroup_lag` - Requires a Kafka Exporter (not JMX exporter)
- ❌ **NOT Available:** `kafka_consumer_offset` - Consumer offset metrics not exposed by broker JMX
- ✅ **Available:** `kafka_log_Log_LogEndOffset` - Log end offset (broker metric)

**To Get Consumer Lag Metrics, You Need:**

1. **Kafka Exporter** (separate from JMX exporter):
   - Tools like `kafka-exporter`, `burrow`, or `kafka-lag-exporter`
   - These query Kafka's Admin API to get consumer group offsets
   - Expose metrics like: `kafka_consumergroup_lag`, `kafka_consumer_lag_sum`

2. **Consumer Application Metrics:**
   - Consumer applications can expose their own lag metrics
   - Using libraries like Micrometer or Prometheus client

3. **Manual Calculation (if you have consumer offset data):**
   ```promql
   # Lag = Log End Offset - Consumer Offset
   kafka_log_Log_LogEndOffset - kafka_consumer_offset
   ```
   **Note:** This requires consumer offset metrics which aren't available with current JMX exporter.

### Alternative Metric Names

Different exporters use different metric names:
- `kafka_consumergroup_lag` (kafka-exporter)
- `kafka_consumer_lag_sum` (some exporters)
- `kafka_consumer_lag` (burrow)
- `kafka_consumergroup_lag_sum` (variations)

### What You CAN Monitor Now

With current setup, you can monitor:
- **Log End Offset:** `kafka_log_Log_LogEndOffset` - Shows how many messages exist
- **Message Production Rate:** `kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`
- **Infer Lag:** If you know consumer processing rate, you can estimate lag

But you **cannot directly measure consumer lag** without consumer offset data.

