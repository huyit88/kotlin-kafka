1. Produce messages continuously for ~30–60 seconds to any topic

```bash
# Create the topic if it doesn't exist
docker exec -it kafka-44 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create --topic metrics-test --partitions 1 --replication-factor 1 2>/dev/null || true
'

# Produce messages continuously for ~45 seconds
while true; do
    echo "message-$(date +%s%3N)-$(shuf -i 1-1000 -n 1)" | \
    docker exec -it kafka-44 bash -lc \
      "kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test"
    sleep 0.1
  done
```

**Alternative (simpler, using a loop with counter):**
```bash
# Create topic if needed
docker exec -it kafka-44 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create --topic metrics-test --partitions 1 --replication-factor 1 2>/dev/null || true
'

# Produce messages for 45 seconds (450 messages at 0.1s interval)
for i in {1..450}; do
  echo "message-$i-$(date +%s%3N)" | \
  docker exec -T kafka-44 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test"
  sleep 0.1
done
```

**Simplest (run from 44-prometheus directory):**
```bash
cd 44-prometheus

# Create topic if needed
docker exec -T kafka-44 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create --topic metrics-test --partitions 1 --replication-factor 1 2>/dev/null || true
'

# Produce messages for 45 seconds
for i in {1..450}; do
  echo "message-$i-$(date +%s%3N)" | \
  docker compose exec -T kafka-44 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test"
  sleep 0.1
done
```

2. In Prometheus, query the throughput metric using a time range graph

**Steps:**
1. Open Prometheus UI: `http://localhost:9090`
2. Go to the **Graph** tab
3. Enter this query in the expression field:
   use the direct metric:
   ```promql
   kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate
   ```
4. Set the time range to cover the production period (e.g., last 5-10 minutes)
5. Click **Execute** and switch to the **Graph** view to see the time series visualization
6. You should see the message rate increase during production and drop after stopping

**Alternative: Using Prometheus API to query:**
```bash
# Query current value
curl -s 'http://localhost:9090/api/v1/query?query=kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate' | jq

# Or as a one-liner (Mac compatible):
curl -s "http://localhost:9090/api/v1/query_range?query=kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate&start=$(($(date +%s) - 300))&end=$(date +%s)&step=15s" | jq
```

3. Stop producing (press `Ctrl+C` if using the while loop, or wait for the for loop to complete)

4. Confirm the metric trend changes over time
   - After stopping production, refresh the Prometheus graph
   - The message rate should drop to near zero
   - The graph will show the historical trend: high during production, low after stopping

---

## Documentation

### Metric Used

**Metric:** `kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`

**PromQL Query:**
```promql
kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate
```

This metric measures the rate of incoming messages per second, averaged over one minute, across all topics on the Kafka broker.

### Observations

**During Production (30-60 seconds):**
- Message rate increases from near-zero to a positive value (e.g., 0.1-10 messages/sec depending on production rate)
- The graph shows an upward trend or sustained elevated values
- Metric values are consistently above zero while messages are being produced

**After Stopping Production:**
- Message rate gradually decreases and returns to near-zero
- The graph shows a downward trend back to baseline
- Metric values stabilize at very low values (close to 0)

**Time Series Evidence:**
- Prometheus stores historical data points, allowing you to see the complete timeline
- You can zoom into any time range to see exactly when production started and stopped
- The graph clearly shows the correlation between production activity and metric values

### Why History Matters for Incident Investigation

1. **Root Cause Analysis:** Historical data allows you to trace back to when an issue started. For example, if you see a sudden drop in message rate at 2:30 PM, you can correlate it with deployment events, network issues, or producer failures that occurred at that time.

2. **Pattern Recognition:** Long-term trends reveal patterns that aren't visible in real-time. You might discover that message rates drop every day at a specific time, indicating scheduled maintenance or batch job completion.

3. **Capacity Planning:** Historical trends show growth patterns, helping you predict when you'll need to scale infrastructure before problems occur.

4. **Post-Incident Review:** After an incident, you can review historical metrics to understand:
   - What normal behavior looks like
   - When the anomaly started
   - How long it lasted
   - Whether it was a one-time event or recurring pattern

5. **Alert Tuning:** Historical data helps set appropriate alert thresholds. Without history, you might set alerts too sensitive (causing false positives) or too loose (missing real issues).

6. **Compliance and Auditing:** Some industries require historical metrics for compliance. Prometheus provides an audit trail of system behavior over time.

**Without Prometheus time-series storage**, you would only see current values. You couldn't answer questions like "When did the message rate start dropping?" or "What was the normal baseline before the incident?" This is why Prometheus is essential for production monitoring—it provides the temporal context needed for effective incident investigation and system understanding.