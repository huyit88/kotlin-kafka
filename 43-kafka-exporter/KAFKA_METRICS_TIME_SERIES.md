# Kafka Metrics Time-Series Observation

## Which metrics increased over time

**During message production:**

1. **`kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`**
   - **Before**: 0.0 (no messages)
   - **During production**: Increased to positive values (e.g., 0.5-2.0 messages/sec depending on production rate)
   - **Behavior**: This rate metric shows the current throughput. It increases as messages are produced and reflects the actual production rate.

2. **`kafka_log_Log_LogEndOffset_topic_metrics_test_partition_0`**
   - **Before**: 0.0 (no messages, empty partition)
   - **During production**: Continuously increased (1, 2, 3, 4, ...) as each new message was appended
   - **Behavior**: This is a cumulative counter that only increases. Each message increments the log end offset by 1.

3. **`kafka_log_Log_Size_topic_metrics_test_partition_0`**
   - **Before**: 0.0 (empty log)
   - **During production**: Increased as messages accumulated (e.g., 297 bytes after 4 messages)
   - **Behavior**: Log size grows with each message written, representing the physical storage used.

---

## Which metrics stabilized

**After stopping message production:**

1. **`kafka_server_BrokerTopicMetrics_MessagesInPerSec_one_minute_rate`**
   - **After stopping**: Gradually decreased and stabilized near 0.0
   - **Behavior**: Rate metrics decay over time. After production stops, the one-minute rolling average decreases as the time window moves past the production period, eventually stabilizing at 0.0.

2. **`kafka_server_BrokerTopicMetrics_MessagesInPerSec_mean_rate`**
   - **After stopping**: Stabilized at a low value (close to 0.0)
   - **Behavior**: Mean rate represents the average since broker start. After production stops, it stabilizes at the historical average, which slowly approaches 0.0 over time.

3. **`kafka_log_Log_LogEndOffset_topic_metrics_test_partition_0`**
   - **After stopping**: Stabilized at the final offset value (e.g., 4.0)
   - **Behavior**: Unlike rate metrics, log end offset is a cumulative value that doesn't decrease. It stabilizes at the last written offset and remains constant until new messages arrive.

---

## Why time-series observation is critical

Time-series observation of Kafka metrics is critical for several operational reasons:

1. **Detecting anomalies and trends**: Single point-in-time values don't reveal problems. A message rate of 0.0 could be normal (no traffic) or critical (producer failure). Time-series shows the pattern - a sudden drop from high to zero indicates a failure, while a gradual decrease might be normal traffic patterns.

2. **Capacity planning and scaling decisions**: Observing metrics over time (hours, days, weeks) reveals traffic patterns, peak hours, and growth trends. This enables proactive scaling before hitting capacity limits, rather than reactive scaling after performance degrades.

3. **Distinguishing normal vs. problematic behavior**: A high message rate might be normal during peak hours but problematic if it persists and causes broker overload. Time-series context shows whether high values are expected (daily pattern) or unexpected (anomaly requiring investigation).

4. **Alerting and incident response**: Time-series data enables intelligent alerting (e.g., alert only if metric exceeds threshold AND deviates from historical pattern). During incidents, historical context helps diagnose whether current behavior is unprecedented or part of a known pattern.

5. **Performance optimization**: By observing how metrics change in response to events (e.g., message production starting/stopping), operators can understand system behavior, tune configurations, and optimize resource allocation based on actual usage patterns rather than assumptions.
