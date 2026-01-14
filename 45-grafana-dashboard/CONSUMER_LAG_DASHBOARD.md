# Consumer Lag Dashboard - Grafana Visualization Guide

## ⚠️ Important Note: Consumer Lag Metrics Setup

**kafka-exporter is now configured!** Consumer lag metrics are available, but they only appear when you have **active consumer groups with lag**.

**Current Available Metrics:**
- ✅ `kafka_log_Log_LogEndOffset` - Log end offset (from JMX exporter)
- ✅ `kafka_consumergroup_lag` - Consumer lag (from kafka-exporter, requires consumer groups)
- ✅ `kafka_consumergroup_current_offset` - Consumer offsets (from kafka-exporter)

**To see consumer lag metrics, you need to:**
1. Create a consumer group
2. Produce messages to a topic
3. Create lag by consuming slowly or stopping the consumer

See **"Generating Consumer Lag Data"** section below for step-by-step instructions.

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

**Grafana Configuration:**
1. **Panel Type:** Stat
2. **Query:** `sum(kafka_consumergroup_lag) by (consumergroup)`
3. **Data Source:** Prometheus
4. **Time Range:** Last 5 minutes or Last 15 minutes
5. **Value Options:**
   - **Value:** Last
   - **Unit:** short (or none)
6. **Thresholds:**
   - Green: < 1,000 messages
   - Yellow: 1,000 - 10,000 messages
   - Red: > 10,000 messages

**Troubleshooting "No Data":**
- ✅ **Verify query works in Prometheus:** `http://localhost:9090/graph?g0.expr=sum(kafka_consumergroup_lag)%20by%20(consumergroup)`
- ✅ **Check time range:** Use "Last 15 minutes" or "Last 1 hour" (not "Last 5 minutes" if metrics are older)
- ✅ **Verify data source:** Ensure Prometheus data source is selected and connected
- ✅ **Check legend:** If using "Value options → Show", ensure "All values" is selected
- ✅ **Try without aggregation:** First test with `kafka_consumergroup_lag` to verify data exists

**What it shows:**
- Total unprocessed messages across all partitions for each consumer group
- Quick overview of which consumer groups are behind

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

**Troubleshooting "No Data":**
- ⚠️ **Note:** `rate()` requires at least 2 data points within the time window
- ✅ **Use longer time range:** "Last 15 minutes" or "Last 1 hour"
- ✅ **Alternative:** Use `deriv()` instead: `deriv(kafka_consumergroup_lag[5m])`
- ✅ **Or use raw metric:** Just show `kafka_consumergroup_lag` and visually interpret the slope

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

**Grafana Configuration:**
1. **Panel Type:** Bar chart or Time series
2. **Query:** 
`sum by(topic) (kafka_consumergroup_lag)`
`sum(kafka_consumergroup_lag) by (topic)`
3. **Time Range:** Last 15 minutes or Last 1 hour
4. **Legend:** `{{topic}}`

**Troubleshooting "No Data":**
- ✅ **Verify query:** `http://localhost:9090/graph?g0.expr=sum(kafka_consumergroup_lag)%20by%20(topic)`
- ✅ **Check time range:** Ensure it covers when lag data exists
- ✅ **Try without aggregation:** Test with `kafka_consumergroup_lag` first

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

---

## Generating Consumer Lag Data

### Step 1: Create a Topic (if needed)

```bash
cd 45-grafana-dashboard

# Create topic with multiple partitions for better lag visualization
docker exec -it kafka-45 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 \
    --create --topic lag-test --partitions 3 --replication-factor 1 2>/dev/null || true
'
```

### Step 2: Produce Messages to Create Backlog

```bash
# Produce 100 messages to create a backlog
for i in {1..100}; do
  echo "message-$i-$(date +%s)" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
  sleep 0.1
done

# Verify messages were produced
docker exec -it kafka-45 bash -lc '
  kafka-run-class kafka.tools.GetOffsetShell \
    --broker-list localhost:9092 \
    --topic lag-test --time -1
'
```

### Step 3: Create Consumer Group and Consume Slowly (to create lag)

**Option A: Start consumer, then stop it (creates lag):**

```bash
# Terminal 1: Start consumer (will consume some messages)
docker exec -it kafka-45 bash -lc '
  timeout 5 kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic lag-test \
    --group lag-monitor-group \
    --from-beginning || true
'

# Terminal 2: Produce more messages while consumer is stopped
for i in {101..200}; do
  echo "message-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
done
```

**Option B: Use a slow consumer (creates lag over time):**

```bash
# Start a slow consumer (processes 1 message per second)
docker exec -it kafka-45 bash -lc '
  kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic lag-test \
    --group lag-monitor-group \
    --from-beginning &
  CONSUMER_PID=$!
  sleep 10
  kill $CONSUMER_PID 2>/dev/null || true
'

# Produce messages faster than consumer can process
for i in {201..300}; do
  echo "message-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
  sleep 0.05  # Faster than consumer
done
```

### Step 4: Verify Consumer Lag Metrics

**Check if metrics are available:**

```bash
# Check kafka-exporter metrics
curl -s http://localhost:9308/metrics | grep kafka_consumergroup_lag

# Check in Prometheus
curl -s 'http://localhost:9090/api/v1/query?query=kafka_consumergroup_lag' | jq '.data.result | length'

# Check consumer group lag using Kafka CLI
docker exec -it kafka-45 bash -lc '
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --describe --group lag-monitor-group
'
```

**Expected output should show:**
- `LAG` column > 0
- Metrics in kafka-exporter: `kafka_consumergroup_lag{consumergroup="lag-monitor-group", ...}`

### Step 5: Verify in Grafana

1. Open Grafana: `http://localhost:3000`
2. Go to your Consumer Lag Dashboard
3. Use query: `kafka_consumergroup_lag`
4. You should see data points with labels:
   - `consumergroup="lag-monitor-group"`
   - `topic="lag-test"`
   - `partition="0"`, `partition="1"`, `partition="2"`

### Step 6: Create More Lag (Optional)

To see lag increase over time:

```bash
# Produce more messages (consumer is stopped, so lag increases)
for i in {301..500}; do
  echo "message-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
  sleep 0.1
done

# Check lag increased
docker exec -it kafka-45 bash -lc '
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --describe --group lag-monitor-group | grep lag-test
'
```

### Troubleshooting: No Data in Dashboard

**If metrics still show "No data":**

1. **Verify kafka-exporter is running:**
   ```bash
   docker compose ps kafka-exporter
   curl http://localhost:9308/metrics | grep kafka_consumergroup
   ```

2. **Verify consumer group exists:**
   ```bash
   docker exec -it kafka-45 bash -lc '
     kafka-consumer-groups --bootstrap-server localhost:9092 --list
   '
   ```

3. **Verify consumer group has lag:**
   ```bash
   docker exec -it kafka-45 bash -lc '
     kafka-consumer-groups --bootstrap-server localhost:9092 \
       --describe --group lag-monitor-group
   '
   ```
   Look for `LAG` column > 0

4. **Check Prometheus is scraping:**
   - Open `http://localhost:9090/targets`
   - Verify `kafka-exporter` target is UP

5. **Wait a few seconds:** kafka-exporter polls consumer groups periodically (default ~30s)

6. **Verify query in Prometheus:**
   ```bash
   curl -s 'http://localhost:9090/api/v1/query?query=kafka_consumergroup_lag' | jq
   ```

### Quick Test Script

Save this as `generate-lag.sh` and run it:

```bash
#!/bin/bash
# Complete test script to generate consumer lag

cd 45-grafana-dashboard

echo "1. Creating topic..."
docker exec -it kafka-45 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 \
    --create --topic lag-test --partitions 3 --replication-factor 1 2>/dev/null || true
'

echo "2. Producing 50 messages..."
for i in {1..50}; do
  echo "msg-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
done

echo "3. Starting consumer (will consume some messages)..."
docker exec -it kafka-45 bash -lc '
  timeout 3 kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic lag-test \
    --group lag-test-group \
    --from-beginning 2>/dev/null || true
'

echo "4. Producing more messages (creates lag)..."
for i in {51..100}; do
  echo "msg-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test"
done

echo "5. Checking lag..."
docker exec -it kafka-45 bash -lc '
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --describe --group lag-test-group
'

echo "6. Checking metrics..."
sleep 5
curl -s http://localhost:9308/metrics | grep "kafka_consumergroup_lag.*lag-test-group" | head -3

echo ""
echo "✅ Done! Check Grafana dashboard now."
echo "Query: kafka_consumergroup_lag{consumergroup=\"lag-test-group\"}"
```

**To run:**
```bash
chmod +x generate-lag.sh
./generate-lag.sh
```

