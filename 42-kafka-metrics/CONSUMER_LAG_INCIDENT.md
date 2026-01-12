# Consumer Lag Incident Analysis

## What metric detected the problem

**Answer**
Consumer lag is detected using:
- **Command:** `kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group <group-id>` shows `LAG` column
- **JMX Metric:** `kafka.consumer:type=consumer-fetch-manager-metrics,client-id=<client>,topic=<topic>,partition=<partition>` → `records-lag`
- **Monitoring Tools:** Prometheus/Grafana dashboards tracking `kafka_consumer_lag_sum`
- **Formula:** `Lag = Log End Offset - Current Offset` (where current offset is the last committed offset)

---

## Why lag increased

**Answer**
**Root Cause:** Consumer stopped processing (killed, crashed, or paused)
**Why Lag Increased:**
- Consumer stops processing → no more offset commits
- Producer continues producing → log end offset keeps advancing
- **Lag = Log End Offset - Current Offset** grows because:
  - Current offset (last committed) stays constant
  - Log end offset (latest message) keeps increasing
- **Important:** Lag is a **symptom** of the problem, not the root cause. The root cause is the consumer failure/stop.

---

## What action fixes it

**Answer**
1. **Fix the root cause first:**
   - Investigate why consumer stopped (check logs, crashes, bugs)
   - Fix application errors, memory leaks, or configuration issues
   - Ensure consumer code handles exceptions properly

2. **Restart consumer instances:**
   - Restart stopped consumers to resume processing
   - Consumers will catch up by processing messages from their last committed offset

3. **Scale up if needed:**
   - Add more consumer instances (within partition count limits)
   - Increase processing capacity if consumer is too slow
   - Optimize consumer code for faster processing

4. **Monitor recovery:**
   - Watch lag decrease as consumers catch up
   - Verify lag returns to near-zero once caught up

---

## What action does **not** fix it

**Answer**
1. **Restarting consumer without fixing root cause:**
   - If consumer crashes due to a bug, restarting will cause it to crash again
   - Must fix the underlying issue (code bug, configuration error, resource limits)

2. **Adding more consumer instances when:**
   - **Hot partition exists:** All messages go to one partition, so extra consumers can't help (repartition or fix key distribution)
   - **Consumer group already has max consumers:** Can't exceed partition count
   - **Consumer code is fundamentally broken:** More instances = more failures

3. **Increasing producer rate:**
   - Makes lag worse, not better
   - Adds more messages to the backlog

4. **Just waiting:**
   - If consumer is stopped, lag will only grow
   - Consumer must be restarted or fixed to process messages

5. **Ignoring the root cause:**
   - Treating lag as the problem instead of investigating why consumer stopped
   - Lag is a symptom; the real issue is consumer failure
