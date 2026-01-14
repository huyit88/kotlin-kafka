# Grafana + Kafka Interview Notes

## 1. Why Grafana is used with Kafka

**Answer:**
Grafana provides **visualization and alerting** for Kafka time-series metrics from Prometheus. 
It enables **visual correlation** across multiple metrics (e.g., throughput, lag, broker health) to identify root causes quickly. Without Grafana, teams rely on raw metrics or CLI tools, making it hard to spot trends, correlate incidents, or detect slow degradation. Grafana's dashboards turn metrics into **operational insight** - showing not just current values but historical patterns that reveal capacity issues before outages.

---

## 2. Most important Kafka dashboard

**Answer:**
The three most critical dashboards are: 
**Kafka Broker Health** (monitors under-replicated partitions, request queue, broker availability - detects cluster failures), 
**Throughput Dashboard** (tracks message production/consumption rates - identifies capacity issues and bottlenecks), and 
**Consumer Lag Dashboard** (shows consumer group lag - detects when consumers fall behind, causing data staleness). These cover the three critical failure modes: broker failures, capacity exhaustion, and consumer failures.

---

## 3. Why alerts should be based on trends

**Answer:**
Trend-based alerts detect **slow degradation and capacity issues** before they cause outages, while absolute thresholds miss gradual problems and cause false positives. 
For example, lag of 10,000 messages could be normal (if production rate matches consumption) or critical (if lag is increasing rapidly). 
A trend alert on "lag increasing > 500/min" catches problems early regardless of absolute value, whereas "lag > 10,000" misses rapid growth when lag is low and triggers false alarms when lag is high but stable.

---

## 4. One mistake teams make with Kafka dashboards

**Answer:**
Teams often **focus on absolute thresholds** (e.g., "alert when lag > 10,000") instead of **trends** (e.g., "alert when lag increasing > 500/min"), causing missed early warnings and false positives. Other common mistakes: 
**not monitoring max partition lag** (only total lag, hiding hot partition problems), 
**missing visual correlation** (separate dashboards that don't show relationships between metrics), and 
**over-alerting on noise** (alerting on temporary spikes instead of sustained patterns). 
The fix: use trend-based alerts, monitor max partition lag, and create dashboards that show related metrics together for correlation.
