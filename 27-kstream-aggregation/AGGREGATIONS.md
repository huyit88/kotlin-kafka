# Kafka Streams Aggregations

## 1. Why aggregations are stateful

Aggregations are stateful because they need to **maintain accumulated state** across multiple records. Unlike stateless operations (like `map` or `filter`) that process each record independently, aggregations like `count()`, `sum()`, or `reduce()` must:

- **Remember previous values**: Each new record updates the existing aggregated value (e.g., incrementing a count)
- **Accumulate over time**: The result depends on all records processed so far, not just the current one
- **Maintain per-key state**: For operations like `groupBy().count()`, state is maintained separately for each key

**Example**: When counting messages per user, the state store holds `alice -> 5`, and when a new message arrives, it becomes `alice -> 6`. Without state, we couldn't accumulate these counts.

## 2. Difference between `KStream` and `KTable`

**KStream**:
- Represents an **unbounded stream of immutable records** (events)
- Every record is independent and emitted
- Duplicate keys result in multiple records
- Example: `alice -> message1`, `alice -> message2`, `bob -> message3`

**KTable**:
- Represents a **changelog stream** (latest value per key)
- Only emits updates when a key's value changes
- Duplicate keys update the existing value (only latest is kept)
- Example: `alice -> 2` (count), `bob -> 1` (count) - only emits when counts change

**Key Difference**: `KStream` = stream of events, `KTable` = materialized view/table with latest value per key.

## 3. Where Kafka Streams stores state

Kafka Streams stores state in **two places** for fault tolerance:

1. **Local State Store (RocksDB)**:
   - Stored on disk in the state directory (e.g., `/tmp/kafka-streams/<application-id>/`)
   - Fast in-memory access with disk persistence
   - Used for quick lookups during processing

2. **Remote Changelog Topic (Kafka)**:
   - Internal Kafka topic (e.g., `<application-id>-<store-name>-changelog`)
   - Contains all state updates as records
   - Used for state recovery, replication, and fault tolerance

**Why both?**: Local store provides fast access, changelog topic ensures state can be recovered if local store is lost or when scaling/rebalancing.

## 4. What a changelog topic is

A **changelog topic** is an internal Kafka topic that stores all updates to a state store as a sequence of records. It serves as:

- **Source of truth**: Complete history of all state changes
- **Recovery mechanism**: Used to rebuild state stores after restart or failure
- **Replication**: Enables state migration during rebalancing or scaling
- **Fault tolerance**: If local RocksDB is lost, state can be fully recovered from changelog

**How it works**: Every time a state store is updated (e.g., `count++`), a record is written to the changelog topic. On restart, Kafka Streams replays the changelog to rebuild the state store.

## 5. Why re-keying causes repartitioning

Re-keying causes repartitioning because **records must be redistributed to partitions based on their new keys** to ensure:

1. **Same key → Same partition**: All records with the same key must go to the same partition for proper aggregation
2. **Correct aggregation**: Aggregations like `count()` work per-partition, so all records for a key must be in one partition
3. **Ordering preservation**: Records with the same key are processed in order within their partition

**Example**: If input has key `room1` but we re-key to `alice`, Kafka Streams creates an internal repartition topic to redistribute records so all `alice` records go to the same partition, enabling correct counting.

## 6. Tradeoffs of windowed aggregations

**Advantages**:
- **Time-based analysis**: Enables aggregations over time windows (e.g., "messages per user per 10 seconds")
- **Automatic cleanup**: Old windows are automatically expired based on retention
- **Temporal queries**: Can answer questions like "how many messages in the last minute?"

**Tradeoffs/Disadvantages**:
- **Higher memory usage**: Multiple windows are stored simultaneously (one per time window)
- **Slower processing**: More complex than stateless operations due to window management
- **State overhead**: Requires window stores (more complex than simple key-value stores)
- **Recovery time**: Larger state means longer recovery time on restart
- **Retention complexity**: Must balance retention period (too short = data loss, too long = high memory)
- **Timestamp dependency**: Requires proper timestamp extraction for correct windowing

## Topology Diagram

```
chat-output (input topic)
    |
    | [re-key: roomId -> sender]
    ▼
repartition topic (internal)
    |
    | [groupBy + count]
    ▼
state store (RocksDB + changelog)
    |
    | [toStream]
    ▼
chat-user-counts (output topic)
```

**Flow**: Input messages are re-keyed (triggering repartition), grouped and counted (updating state store), then emitted to output topic.
