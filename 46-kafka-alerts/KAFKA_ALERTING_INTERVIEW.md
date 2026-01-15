# Kafka Alerting Interview Questions

## 1. Why trend-based alerts beat absolute thresholds for lag

**Answer:**

Trend-based alerts (e.g., `increase(lag[5m]) > 20`) detect **rate of change** rather than absolute values, providing early warning before lag becomes critical. Absolute thresholds (e.g., `lag > 10000`) only fire when lag is already high, missing gradual degradation. For example, lag growing from 100 → 500 → 2000 messages indicates a problem even if it's below a 10,000 threshold. 
Trend alerts catch consumer slowdowns, processing bottlenecks, or traffic spikes early, allowing proactive intervention before users are impacted. They also reduce false positives from temporary spikes during consumer restarts or rebalancing, focusing on sustained growth patterns that indicate real issues.

---

## 2. The first Kafka alerts you would implement and why

**Answer:**

**Priority 1: `ActiveControllerCount != 1`** - Must be exactly 1. If 0, cluster coordination is broken (no leader election, partition management). If >1, split-brain scenario causing data corruption. This is the foundation - if wrong, nothing else works correctly.

**Priority 2: `UnderReplicatedPartitions > 0`** - Indicates broker failures or replication issues. Data durability is at risk - if another broker fails, data may be lost. 
This directly threatens data safety.

**Priority 3: Consumer lag alerts** - Application-level health, but downstream from broker health. If brokers are unhealthy, consumer lag can't be fixed.

**Why this order:** Cluster coordination and data durability must be healthy before application-level metrics matter. You can't fix consumer lag if the cluster is broken.

---

## 3. Why `UnderReplicatedPartitions > 0` is dangerous

**Answer:**

`UnderReplicatedPartitions > 0` means partitions have fewer replicas than configured (e.g., replication-factor=3 but only 2 replicas available). This is dangerous because: 
(1) **Data durability risk** - if another broker fails, some partitions may lose all replicas, causing data loss. 
(2) **Reduced fault tolerance** - cluster can tolerate fewer broker failures. With 3 replicas, you can lose 1 broker safely; with only 2 replicas, losing 1 more means unavailability. 
(3) **Cascade failure** - if the leader fails and no ISR (In-Sync Replicas) is available, the partition becomes unavailable, blocking producers and consumers. This creates a domino effect where one broker failure can cause widespread unavailability.

---

## 4. Where alert rules should live (Grafana vs Prometheus) and why

**Answer:**

Alert rules should live in **Prometheus**, not Grafana. 
Prometheus evaluates alert rules continuously against time-series data, maintains alert state (inactive/pending/firing), and integrates with Alertmanager for routing. 
Grafana is a visualization tool - it can display alerts but doesn't evaluate them. 
Storing rules in Prometheus provides: 
(1) **Single source of truth** - rules are version-controlled with Prometheus config, not scattered across dashboards. 
(2) **Consistent evaluation** - Prometheus evaluates rules at fixed intervals regardless of dashboard views. 
(3) **Alertmanager integration** - Prometheus directly sends firing alerts to Alertmanager for routing to Slack/PagerDuty. 
(4) **State management** - Prometheus tracks alert lifecycle (pending duration, firing state). Grafana alerts are dashboard-dependent and don't integrate with Alertmanager.

---

## 5. One common Kafka alerting mistake + how to avoid it

**Answer:**

**Mistake: Using absolute thresholds for consumer lag** (e.g., `lag > 10000`) instead of trend-based alerts. This causes false positives during traffic spikes and misses gradual degradation. 
**How to avoid:** Use `increase(lag[5m]) > threshold` to detect rate of change. 
This fires when lag is growing (indicating a problem) rather than when it's already high. For example, lag at 500 messages increasing by 50/minute is a problem even if below 10,000 threshold. Trend alerts catch issues early and reduce noise from temporary spikes.

**Alternative mistake: Alerting on every broker metric individually** without aggregation. In multi-broker clusters, this creates alert storms (one alert per broker). 
**How to avoid:** Use aggregation functions like `max()` or `sum()` to create cluster-level alerts. For example, `max(UnderReplicatedPartitions) > 0` fires once for the cluster, not once per broker.

---

## Alerting Pipeline Diagram

```
Kafka Brokers -> JMX Exporter / Kafka Exporter -> Prometheus (rules evaluation) -> Alertmanager -> Slack/PagerDuty
     ↓                              ↓                        ↓                        ↓
  Metrics                    Scrape metrics          Evaluate alerts          Route notifications
  (JMX/Admin API)            (every 15s)            (check conditions)        (based on severity)
```

**Flow Explanation:**
1. **Kafka Brokers** expose metrics via JMX (broker metrics) and Admin API (consumer lag)
2. **Exporters** (JMX Exporter, Kafka Exporter) scrape and convert metrics to Prometheus format
3. **Prometheus** scrapes exporters, evaluates alert rules, maintains alert state
4. **Alertmanager** receives firing alerts from Prometheus, routes based on severity/labels to Slack (warnings) 