# `CHALLENGE.md` — Day 08: Offsets & Lag (Detecting and Simulating Consumer Lag)

### Dependencies

*(No new dependencies — use your existing Docker-based Kafka setup.)*

---

### Problem A — Observe current offsets and lag

#### Requirement

* Use your existing topic `cpu-metrics-cli` and consumer group `team-metrics`.
* Produce ~10 messages.
* Then run `kafka-consumer-groups --describe` to see **current offset**, **log-end-offset**, and **lag**.

#### Acceptance criteria

* CLI shows 3 partitions.
* Each partition lists correct `CURRENT-OFFSET`, `LOG-END-OFFSET`, and calculated `LAG`.

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
  kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group team-metrics
'
```

Expected output example:

```
GROUP         TOPIC             PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
team-metrics  cpu-metrics-cli   0          5               8               3
team-metrics  cpu-metrics-cli   1          6               6               0
team-metrics  cpu-metrics-cli   2          4               8               4
```

---

### Problem B — Simulate consumer lag (pause consumer)

#### Requirement

* Start your producer to continuously send metrics.
* Stop (or Ctrl+C) your CLI consumer for ~20 seconds.
* Inspect lag again.

#### Acceptance criteria

* Lag increases while consumer is stopped.
* After restarting the consumer, lag decreases as messages are processed.

#### Command to verify/run

```bash
# Start producer (continuous loop)
docker exec -it kafka bash -lc '
  while true; do echo "host-A,ts=$(date +%s%3N),load=$RANDOM"; sleep 1; done \
  | kafka-console-producer --bootstrap-server localhost:9092 \
  --topic cpu-metrics-cli --property parse.key=true --property key.separator=,
'

# start consumer
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group team-metrics \
     --property print.key=true --property print.partition=true --from-beginning'

# Pause consumer for 20s, then run:
docker exec -it kafka bash -lc 'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group team-metrics'
```

```
GROUP           TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID     HOST            CLIENT-ID
team-metrics    cpu-metrics-cli 0          81              104             23              -               -               -
team-metrics    cpu-metrics-cli 1          8               8               0               -               -               -
team-metrics    cpu-metrics-cli 2          15              15              0               -               -               -
```

---

### Problem C — Simulate lag recovery

#### Requirement

* Restart your consumer (`team-metrics`) and observe lag decreasing over time while producer continues.
* Run the describe command periodically to observe offset progress.

#### Acceptance criteria

* `CURRENT-OFFSET` increases until it catches up.
* `LAG` reduces to **0** once all messages are consumed.

#### Command to verify/run

```bash
watch -n 2 \
"docker exec kafka bash -lc 'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group team-metrics'"
```

> Press `Ctrl+C` once you see lag back to 0.

---

### Problem D — Kotlin offset/lag inspector (optional advanced)

#### Requirement

Implement a small Kotlin app using `AdminClient` that lists:

* Group name
* Topic
* Partition
* Current committed offset
* Log-end offset
* Lag per partition

#### Acceptance criteria

* Prints data equivalent to CLI output.
* Works for `team-metrics` group and `cpu-metrics-cli` topic.

#### Suggested Import Path

```kotlin
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions
import org.apache.kafka.common.TopicPartition
```

#### Command to verify/run

```bash
./gradlew :08-offsets-lag:run --args="LagInspector"
```

---

### Problem E (Optional) — Visualize lag change

#### Requirement

Record lag every 5 seconds while consumer is catching up.
Plot or print simple trend lines.

#### Acceptance criteria

* Output shows lag decreasing to 0.
* (Optional) Write data to CSV: `timestamp,partition,lag`.
