# Kafka Streams - Interview Guide

## 1. Kafka Streams vs plain consumer+producer
**Answer:**

**Kafka Streams advantages over plain consumer+producer:**

* **Stream Processing API**: Declarative DSL for transformations (map, filter, join, aggregate) vs manual loop/if-else logic
* **Stateful Operations**: Built-in state stores for aggregations, windowing, joins - no need for external databases
* **Automatic Scaling**: Parallelism = partition count, automatic rebalancing, no manual partition assignment
* **Fault Tolerance**: Automatic state recovery from changelog topics, exactly-once semantics support
* **Less Boilerplate**: No manual consumer/producer configuration, offset management, or rebalancing listeners
* **Topology Management**: Visual representation of data flow, easier to reason about complex pipelines

**When to use plain consumer+producer:**
* Simple forward-only pipelines
* Need fine-grained control over every aspect
* Don't need stateful operations

---

## 2. What `KStream` represents

**Answer:**

`KStream` represents an **unbounded stream of immutable key-value records** from a Kafka topic.

**Key characteristics:**

* **Abstraction**: High-level API that abstracts away consumer/producer details
* **Unbounded**: Continuous stream of records (vs finite collections)
* **Immutable**: Operations create new streams, original stream unchanged
* **Partition-aware**: Automatically handles partitioning, maintains per-partition ordering
* **Lazy evaluation**: Operations are chained, executed when topology is built

**Example:**
```kotlin
val stream: KStream<String, String> = builder.stream("input-topic")
// stream represents all records from input-topic, partitioned by key
```

**vs KTable**: `KStream` = stream of events, `KTable` = changelog stream (latest value per key)

---

## 3. How scaling works (application.id + partitions)
**Answer:**

**Scaling in Kafka Streams:**

* **application.id**: All instances with the same `application.id` form a consumer group
* **Max Parallelism**: Maximum number of parallel threads = **number of partitions** in input topics
* **Partition Assignment**: Kafka Streams assigns partitions to instances (like consumer groups)
* **Instance Coordination**: Instances coordinate via consumer group protocol to share partitions

**Example with 3 partitions, 2 instances:**
```
Partition 0 → Instance A
Partition 1 → Instance B  
Partition 2 → Instance A (or B, depending on assignment)
```

**Key insight**: 
* 3 partitions = max 3 parallel threads
* 2 instances = 2 processes sharing the 3 partitions
* 5 instances = only 3 active (others idle), limited by partition count

**Scaling strategy**: Increase partitions to increase parallelism, but this breaks ordering for existing keys.

---

## 4. What guarantees ordering and where it can break
**Answer:**

**Ordering guarantees:**

* **Per-partition ordering**: Kafka Streams maintains ordering **within each partition**
* **Key-based ordering**: Records with the same key stay in order (same key → same partition)
* **Within operations**: Transformations maintain order (map, filter, etc.)

**Where ordering breaks:**

* **Repartitioning**: Using `repartition()` or operations that change keys redistributes records across partitions, breaking order
* **Joins**: Records from different partitions are joined, original order not preserved
* **Windowing/Aggregation**: Records are grouped by time windows, original sequence order lost
* **Multiple partitions**: Records in different partitions can be processed out of order (parallel processing)

**Example:**
```kotlin
stream.mapValues { it.uppercase() }  // ✅ Order maintained
stream.selectKey { _, v -> v.substring(0, 1) }  // ❌ Order breaks (repartition)
```

**Key insight**: Ordering is **per-partition**, not global. Operations that don't change partition assignment maintain order.

---

## 5. What happens on restart (state, offsets)
**Answer:**

**On restart:**

**Offsets:**
* **Committed offsets**: Kafka Streams commits offsets automatically (or manually if configured)
* **Resume from committed offset**: On restart, processing resumes from last committed offset
* **No message loss**: Messages after committed offset are reprocessed (at-least-once) or skipped (exactly-once)

**State Stores:**
* **Changelog topics**: State stores are backed by Kafka changelog topics (internal topics)
* **State recovery**: On restart, state is **rebuilt** from changelog topics, not reset
* **Recovery time**: Depends on state size - large state stores take time to recover
* **Fault tolerance**: If local state is lost, it's fully recoverable from changelog topics

**Example:**
```
State Store: word-count (key: word, value: count)
Changelog Topic: app-word-count-changelog

On restart:
1. Read from committed offset
2. Replay changelog topic to rebuild state
3. Resume processing from committed offset
```

**Configuration impact:**
* `processing.guarantee=exactly-once`: More overhead, but no duplicates on restart
* `processing.guarantee=at-least-once`: Faster, but may reprocess some messages


**Example topology diagram:**

```
┌─────────────────────────────────────────────────────────┐
│  Input Topic: chat-input (3 partitions)                 │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  Log IN (with partition) │
         └───────────┬───────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  Branch (toxic/normal) │
         └───────┬───────────┬───┘
                 │           │
        ┌────────┘           └────────┐
        ▼                               ▼
┌───────────────┐            ┌──────────────────┐
│ Toxic Messages│            │ Normal Messages   │
└───────┬───────┘            └────────┬─────────┘
        │                               │
        │                    ┌──────────▼──────────┐
        │                    │ Enrich with ts      │
        │                    └──────────┬───────────┘
        │                               │
        │                    ┌──────────▼──────────-┐
        │                    │ Log OUT (partition)  │
        │                    └──────────┬───────────┘
        │                               │
        ▼                               ▼
┌──────────────────┐        ┌──────────────────┐
│ chat-moderation  │        │  chat-output     │
│  (unenriched)    │        │  (with ts)       │
└──────────────────┘        └──────────────────┘
```
