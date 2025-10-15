### Dependencies

*(None beyond your Docker Compose from Day 06. These are all CLI tasks.)*

---

### Problem A — Create a fresh, partitioned topic

#### Requirement

* Create topic **`cpu-metrics-cli`** with **3 partitions**, **RF=1** (Docker single-broker is fine).

#### Acceptance criteria

* `--describe` shows **PartitionCount: 3** and **ReplicationFactor: 1**.

#### Command to verify/run

```bash
docker exec -it kafka bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create \
    --topic cpu-metrics-cli --partitions 3 --replication-factor 1
  kafka-topics --bootstrap-server localhost:9092 --describe --topic cpu-metrics-cli
'
```

---

### Problem B — Produce test data (keys for sticky partitions)

#### Requirement

* Produce **15** messages (5 for each key: `host-A`, `host-B`, `host-C`) so we can see key→partition stickiness.

#### Acceptance criteria

* Messages are written without error.

#### Command to verify/run

**Clean keyed variant (recommended):**

```bash
docker exec -it kafka bash -lc '
  { for i in $(seq 1 5); do echo "host-A,ts=$(date +%s%3N),load=$RANDOM"; done;
    for i in $(seq 1 5); do echo "host-B,ts=$(date +%s%3N),load=$RANDOM"; done;
    for i in $(seq 1 5); do echo "host-C,ts=$(date +%s%3N),load=$RANDOM"; done; } \
  | kafka-console-producer --bootstrap-server localhost:9092 \
      --topic cpu-metrics-cli --property parse.key=true --property key.separator=,
'
```

---

### Problem C — Two consumers in the **same group** (work sharing)

#### Requirement

* Start **two** `kafka-console-consumer` processes with the **same** `--group` (e.g., `team-metrics`).
* Show which **partitions** each consumer reads from.

#### Acceptance criteria

* Combined, both terminals print **all 15** messages once (no duplicates within the group).
* Output shows a **partition split** (e.g., one terminal prints from `partition=0,1`, the other from `partition=2`).

#### Command to verify/run

Open **Terminal 1**:

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group team-metrics \
     --property print.key=true --property print.partition=true --from-beginning'
```
```output
Partition:2     host-B  ts=1760541783740,load=7221
Partition:2     host-B  ts=1760541783742,load=26451
Partition:2     host-B  ts=1760541783743,load=31453
Partition:2     host-B  ts=1760541783744,load=22931
Partition:2     host-B  ts=1760541783745,load=27284
```
Open **Terminal 2**:

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group team-metrics \
     --property print.key=true --property print.partition=true --from-beginning'
```
```output
Partition:0     host-A  ts=1760541783733,load=25072
Partition:0     host-A  ts=1760541783734,load=4048
Partition:0     host-A  ts=1760541783735,load=10675
Partition:0     host-A  ts=1760541783736,load=15849
Partition:0     host-A  ts=1760541783738,load=19056
Partition:0     host-C  ts=1760541783747,load=11977
Partition:0     host-C  ts=1760541783748,load=18338
Partition:0     host-C  ts=1760541783750,load=6321
Partition:0     host-C  ts=1760541783751,load=8315
Partition:0     host-C  ts=1760541783752,load=27616
```
> If you don’t see a split, press **Enter** a few times in each, or produce more data; the group may rebalance on join.

---

### Problem D — Two **different groups** (broadcast)

#### Requirement

* Start one consumer with `--group analytics-A` and another with `--group monitoring-B`.
* Read **from the beginning** on both.

#### Acceptance criteria

* **Each** group prints ~15 historical messages (total ~30 across both terminals).
* Confirms **fan-out** behavior across **different** groups.

#### Command to verify/run

Terminal 1:

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group analytics-A \
     --from-beginning --property print.key=true --property print.partition=true'
```

Terminal 2:

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group monitoring-B \
     --from-beginning --property print.key=true --property print.partition=true'
```

---

### Problem E — Inspect group **lag** and committed offsets

#### Requirement

* Describe `team-metrics` group to see **current-offset**, **log-end-offset**, and **lag** per partition.

#### Acceptance criteria

* Output shows **3 rows** (one per partition) with **lag** values (0 after fully caught up).

#### Command to verify/run

```bash
docker exec -it kafka bash -lc \
  'kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group team-metrics'
```

---

### Problem F (Optional) — Replay by creating a **new group**

#### Requirement

* Start a **brand-new group** (e.g., `replay-${RANDOM}`) with `--from-beginning`.

#### Acceptance criteria

* New group re-reads **all 15** messages from the start.

#### Command to verify/run

```bash
docker exec -it kafka bash -lc \
  'kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic cpu-metrics-cli --group replay-'$RANDOM' \
     --from-beginning --property print.key=true --property print.partition=true'
```

---

### Notes & Tips

* `--from-beginning` only takes effect if the **group has no committed offsets yet**.
* Use `--property print.key=true --property print.partition=true` to visualize key stickiness and partition split.
* For a cleaner key separation in Problem B, always use `--property parse.key=true --property key.separator=,` and send lines like `key,value`.
