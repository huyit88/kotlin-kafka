# Solution: Processing Health Panel in Grafana

## Problem
`deriv(kafka_log_Log_LogEndOffset[5m])` shows no data because:
1. The metric is `untyped`, not a counter
2. `deriv()` may not work with untyped metrics in your Prometheus version
3. The offset might not have changed (still at 5)

## Solution 1: Use Raw Metric + Grafana Transform (Recommended)

### Step 1: Basic Query
1. **Query:** `kafka_log_Log_LogEndOffset`
2. **Panel Type:** Time series
3. **Legend:** `{{topic}}/{{partition}}`

### Step 2: Add Transform to Calculate Rate

**If you see this UI:**
- **Operation:** (Time/metrics-test/0) + Arithmetic operation + (Time/metrics-test/0)
- **Alias:**
- **Replace all fields**

**Do this:**
1. **First field (left side):** Select `kafka_log_Log_LogEndOffset` from dropdown
2. **Arithmetic operation:** Select `-` (minus/subtraction)
3. **Second field (right side):** Select `kafka_log_Log_LogEndOffset` again (same field)
4. **Alias:** Leave empty or set to "Offset Growth Rate"
5. **Replace all fields:** Uncheck this (keep original field)
6. Click **Apply**

**What this does:** Calculates the difference between the current value and the previous value, showing the change over time.

**Note:** This calculates point-to-point differences. For a smoother rate, you may need to use Solution 4 (manual query) or Solution 2 (visual slope).

### Step 3: Visualize
- The calculated field shows the rate of change
- Compare with Input Throughput panel

---

## Solution 2: Use Visual Slope (Simplest - Recommended)

### In Grafana Panel:
1. **Query:** `kafka_log_Log_LogEndOffset`
2. **Panel Type:** Time series
3. **Legend:** `{{topic}}/{{partition}}`
4. **Visual Interpretation:**
   - The **steepness of the line** shows the growth rate
   - **Steep upward line** = Fast growth (many messages)
   - **Flat line** = No growth
   - **Gradual slope** = Steady growth
5. **Compare:** Place this panel next to Input Throughput panel
   - If LogEndOffset slope is steeper → Backlog building
   - If slopes match → Healthy
   - If LogEndOffset slope is flatter → Consumers catching up

**This is the simplest approach and meets the requirement to "visually detect backlog"**

---

## Solution 3: Produce Messages First

The offset needs to change for any rate calculation to work:

```bash
# Produce some messages to make offset grow
for i in {1..50}; do
  echo "message-$i-$(date +%s)" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test"
  sleep 0.2
done
```

Then check if offset changed:
```bash
curl -s 'http://localhost:9090/api/v1/query?query=kafka_log_Log_LogEndOffset' | jq '.data.result[0].value[1]'
```

After producing messages, the offset should increase, and you'll see the visual slope in Grafana.

---

## Solution 4: Manual Calculation Query (If You Need Numeric Values)

Try this query in Grafana (after producing messages):

```promql
(kafka_log_Log_LogEndOffset - kafka_log_Log_LogEndOffset offset 1m) / 60
```

This calculates: (current_value - value_1_minute_ago) / 60 seconds = messages per second

**Or use a shorter window:**
```promql
(kafka_log_Log_LogEndOffset - kafka_log_Log_LogEndOffset offset 30s) / 30
```

**Note:** 
- This only works if the offset has changed in the specified time window
- You must produce messages first for the offset to change
- If offset stays constant, result will be 0

---

## Recommended Approach

**For Problem C, use Solution 2 (Visual Slope) - Simplest:**

1. **Query:** `kafka_log_Log_LogEndOffset`
2. **Panel Type:** Time series
3. **Visual Interpretation:** The slope of the line shows growth rate
4. **Compare:** With Input Throughput panel visually

This approach:
- ✅ Works immediately (no transforms needed)
- ✅ Meets requirement: "visually detect backlog"
- ✅ Easy to compare slopes between panels
- ✅ No complex PromQL or transforms needed
- ✅ The visual slope is the "offset growth slope" mentioned in requirements

**If you need numeric values, try Solution 4 (Manual Calculation Query) after producing messages.**

---

## Quick Test

1. First, produce some messages (Solution 3)
2. Then use Solution 1 (Grafana Transform)
3. You should see the rate of change calculated

