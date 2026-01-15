### Problem A

#### Requirement

Add Kafka alert rules to Prometheus.

1. Create `kafka-alerts.yml` with **at least 3 alerts**:

* **KafkaConsumerLagIncreasing** (trend-based)

  * Uses `increase(...)`
  * Window: **5m**
  * `for: 2m`
  * `severity: warning`

* **KafkaUnderReplicatedPartitions**

  * Expression checks `> 0`
  * `for: 1m`
  * `severity: critical`

* **KafkaNoActiveController**

  * Expression checks controller count `!= 1`
  * `for: 30s`
  * `severity: critical`

2. Reference the rules file in `prometheus.yml` via:

* `rule_files: - kafka-alerts.yml`

3. Restart Prometheus so rules are loaded.

#### Acceptance criteria

* Prometheus starts with **no rule parsing errors**
* All 3 alerts appear in **Prometheus → Alerts**
* In a healthy cluster, alerts are **inactive** (not firing immediately)

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
docker compose up -d
# Open:
# http://localhost:9090/alerts
# http://localhost:9090/rules
```

---

### Problem B

#### Requirement

Trigger and observe a **consumer lag alert** end-to-end.

1. Ensure a consumer group exists (e.g. `etl-consumer`)
2. Produce messages continuously to a topic (e.g. `etl-input`)
3. Stop/pause the consumer so lag grows
4. Observe alert state transitions:

* `inactive → pending → firing`

Document your experiment in `CONSUMER_LAG_ALERT.md`:

* topic + group
* how you produced load
* how you stopped the consumer
* evidence of lag growth (`kafka-consumer-groups --describe`)
* the exact time you saw `pending` and `firing`
* why “lag increasing” is actionable (root causes to check)

#### Acceptance criteria

* `KafkaConsumerLagIncreasing` reaches **firing**
* `CONSUMER_LAG_ALERT.md` contains reproducible steps + evidence

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
# Produce load
kafka-console-producer --bootstrap-server localhost:9092 --topic etl-input

# Observe lag (repeat a few times)
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group etl-consumer

# Check alert state
# http://localhost:9090/alerts

cat CONSUMER_LAG_ALERT.md
```

---

### Problem C

#### Requirement

Trigger and observe a **broker failure / data-risk alert**.

Choose one path:

**Path 1 (preferred if multi-broker):**

* Stop one broker and observe:

  * `UnderReplicatedPartitions > 0` (should fire)

**Path 2 (single broker):**

* Force a broker-down scenario by stopping the broker container and document:

  * which alert fired (likely controller-related metric behavior may differ in single-broker setups)
  * what metric stopped updating / became missing
  * what operational action you’d take

Write `BROKER_FAILURE_ALERT.md` with:

* what you did
* which alert fired (and when)
* why it’s critical (durability/unavailability risk)
* what you’d check next (disk, ISR, broker logs, network)

#### Acceptance criteria

* At least **one critical alert** reaches **firing**
* `BROKER_FAILURE_ALERT.md` has clear evidence + diagnosis steps

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
# Example: stop broker container (adjust name)
docker stop kafka

# Check alerts
# http://localhost:9090/alerts

# Bring it back
docker start kafka

cat BROKER_FAILURE_ALERT.md
```

---

### Problem D

#### Requirement

Add an **alert severity model** and notification policy.

1. Create `KAFKA_ALERT_SEVERITY_MODEL.md` defining:

* `warning` vs `critical`
* who gets notified
* which alerts page someone
* expected response time

2. Update `kafka-alerts.yml` so each alert includes:

* `labels.severity`
* `annotations.summary`
* `annotations.description`

#### Acceptance criteria

* Severity model exists and is actionable (not generic)
* All alerts include labels + annotations

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
cat KAFKA_ALERT_SEVERITY_MODEL.md
# http://localhost:9090/alerts (click an alert to see annotations)
```

---

### Problem E

#### Requirement

Create `KAFKA_ALERTING_INTERVIEW.md` answering (≤ 5 lines each):

1. Why trend-based alerts beat absolute thresholds for lag
2. The first Kafka alerts you would implement and why
3. Why `UnderReplicatedPartitions > 0` is dangerous
4. Where alert rules should live (Grafana vs Prometheus) and why
5. One common Kafka alerting mistake + how to avoid it

Include one ASCII diagram:

```
Kafka -> Exporter -> Prometheus (rules) -> Alertmanager -> Slack/PagerDuty
```

#### Acceptance criteria

* Correct Kafka + Prometheus terminology
* Clear, interview-ready answers
* No vague statements without “why”

#### Suggested Import Path

* *(None)*

#### Command to verify/run

```bash
cat KAFKA_ALERTING_INTERVIEW.md
```
