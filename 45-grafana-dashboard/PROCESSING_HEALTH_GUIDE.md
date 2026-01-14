# Processing Health (Lag Proxy) - Implementation Guide

## Concept: Using Offset Growth Slope to Detect Backlog

The idea is to use the **rate of change** (slope) of Log End Offset to detect if consumers are falling behind, even without direct consumer lag metrics.

### Key Insight

- **Log End Offset Growth Rate** = How fast messages are being produced
- **If offset grows faster than consumers can process** → Backlog is building
- **If offset growth matches consumption rate** → Healthy (consumers keeping up)

---

## Implementation Options

### Option 1: Derivative of Log End Offset Growth (Recommended)

**Query:**
```promql
deriv(kafka_log_Log_LogEndOffset[5m])
```

**Note:** Since `kafka_log_Log_LogEndOffset` is an `untyped` metric (not a counter), use `deriv()` instead of `rate()`. The `deriv()` function calculates the per-second rate of change (slope).

**What it shows:**
- The rate at which log end offset is increasing (messages per second)
- **High rate** = Many messages being produced
- **Compare with Input Throughput** - if offset growth rate > input throughput, consumers are falling behind

**Visualization:**
- **Time series graph**
- **Y-axis:** Messages per second (offset growth rate)
- **Compare with:** Input Throughput panel (should be similar in healthy state)

---

### Option 2: Derivative (Slope) of Log End Offset

**Query:**
```promql
deriv(kafka_log_Log_LogEndOffset[5m])
```

**What it shows:**
- The instantaneous rate of change (slope) of log end offset
- **Positive slope** = Offset increasing (messages arriving)
- **Steep slope** = Rapid growth (potential backlog if consumers aren't keeping up)
- **Flat/zero slope** = No new messages or consumers caught up

**Visualization:**
- **Time series graph**
- **Y-axis:** Offset change per second
- **Threshold:** Alert if slope is consistently high while consumers should be processing

---

### Option 3: Compare Offset Growth vs Input Throughput

**Query (Offset Growth Rate):**
```promql
sum(rate(kafka_log_Log_LogEndOffset[5m])) by (topic)
```

**Query (Input Throughput - for comparison):**
```promql
rate(kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate[5m])
```

**What it shows:**
- **If offset growth rate ≈ input throughput**: Healthy (consumers processing at production rate)
- **If offset growth rate > input throughput**: Warning (consumers falling behind, backlog building)
- **If offset growth rate < input throughput**: Consumers catching up or no production

**Visualization:**
- **Time series graph with two series**
- **Series 1:** Offset growth rate
- **Series 2:** Input throughput
- **Visual comparison:** Lines should track each other in healthy state

---

### Option 4: Offset Growth Acceleration (Advanced)

**Query:**
```promql
deriv(rate(kafka_log_Log_LogEndOffset[5m])[5m:])
```

**What it shows:**
- How fast the growth rate itself is changing
- **Positive acceleration** = Growth rate increasing (backlog building faster)
- **Negative acceleration** = Growth rate decreasing (consumers catching up)

---

## Recommended Implementation

### For Problem C, use **Option 1** (simplest and most effective):

**Panel Configuration:**
1. **Title:** "Processing Health (Lag Proxy)"
2. **Query:**
   ```promql
   deriv(kafka_log_Log_LogEndOffset[5m])
   ```
   
   **If `deriv()` doesn't work, use manual calculation:**
   ```promql
   (kafka_log_Log_LogEndOffset - kafka_log_Log_LogEndOffset offset 1m) / 60
   ```
3. **Visualization:** Time series
4. **Y-axis label:** "Offset Growth Rate (messages/sec)"
5. **Legend:** Show topic/partition labels

**What to look for:**
- **Healthy:** Offset growth rate matches or is close to input throughput
- **Warning:** Offset growth rate consistently higher than input throughput
- **Critical:** Offset growth rate increasing over time (backlog building)

---

## Visual Interpretation Guide

### Healthy State
```
Offset Growth Rate: 10 msg/sec
Input Throughput: 10 msg/sec
→ Consumers keeping up ✅
```

### Backlog Building
```
Offset Growth Rate: 15 msg/sec
Input Throughput: 10 msg/sec
→ Consumers falling behind ⚠️
(5 msg/sec backlog building)
```

### Consumers Catching Up
```
Offset Growth Rate: 5 msg/sec
Input Throughput: 10 msg/sec
→ Consumers processing faster than production ✅
(Backlog decreasing)
```

---

## Example Grafana Panel Setup

1. **Panel Type:** Time series
2. **Query:**
   ```promql
   rate(kafka_log_Log_LogEndOffset[5m])
   ```
3. **Legend:** `{{topic}}/{{partition}}`
4. **Y-axis:** "Offset Growth (msg/sec)"
5. **Thresholds (optional):**
   - Green: < 10 msg/sec
   - Yellow: 10-50 msg/sec
   - Red: > 50 msg/sec

---

## Why This Works

- **Without consumer lag metrics**, we can't directly see how far behind consumers are
- **But we can infer backlog** by comparing:
  - How fast messages are being produced (input throughput)
  - How fast log end offset is growing (offset growth rate)
- **If offset grows faster than expected**, it means messages are accumulating (backlog)
- **The slope/rate shows the trend** - increasing slope = problem, decreasing slope = recovery

---

## Summary

**Use this query in Grafana:**
```promql
deriv(kafka_log_Log_LogEndOffset[5m])
```

**If `deriv()` shows no data, try:**
```promql
(kafka_log_Log_LogEndOffset - kafka_log_Log_LogEndOffset offset 1m) / 60
```

**Interpretation:**
- Compare with Input Throughput panel
- If offset growth rate > input throughput → backlog building
- If offset growth rate ≈ input throughput → healthy
- Watch for increasing trends → indicates consumers falling behind

