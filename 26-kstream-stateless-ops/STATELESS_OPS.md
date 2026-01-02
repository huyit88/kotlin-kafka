# Stateless Operations in Kafka Streams

## 1. What "stateless" means in Kafka Streams

**Stateless operations** process each record independently without requiring any stored state or context from previous records.

* Operations don't maintain state between records
* Each record can be processed in isolation
* No state stores or stateful stores are required
* Examples: `filter`, `mapValues`, `peek`, `map`, `flatMap`

**Key characteristic**: The output for a given record depends only on that record's key and value, not on any previous records.

## 2. Why stateless ops scale easily

* **Independent processing**: Each record can be processed independently without coordination
* **Horizontal scaling**: Can add more instances/threads without state synchronization overhead
* **Partition-level parallelism**: Each partition can be processed in parallel by different threads
* **No state store overhead**: No need to maintain, replicate, or recover state stores
* **Linear scaling**: More partitions = more parallelism = better throughput

**Example**: With 3 partitions, you can have 3 threads processing in parallel, and adding more instances automatically distributes the load.

## 3. Common stateless operators

* **`filter`**: Filters messages based on a predicate condition (keeps records that match)
* **`mapValues`**: Transforms the value of each message without changing the key
* **`branch`** (deprecated, use `filter`/`filterNot`): Splits messages into different branches based on conditions
* **`peek`**: Side-effect operation used for debugging/logging (doesn't modify the stream)
* **`map`**: Transforms both key and value of records
* **`flatMap`**: Transforms one record into zero or more records

## 4. Why ordering is preserved

* **Per-partition ordering**: Kafka guarantees ordering within each partition
* **Partition assignment**: Each Kafka Streams thread is assigned specific partitions
* **Sequential processing**: Records from the same partition are processed sequentially by the assigned thread
* **Key-based routing**: Records with the same key always go to the same partition (via key hashing)

**Important**: Ordering is preserved **per partition**, not globally across all partitions.

## 5. What changes when you move to aggregations (stateful)

**Stateful operations** (aggregations, joins, windowing) introduce:

* **State stores**: Require local state stores (RocksDB) to maintain aggregated data
* **State recovery**: Need changelog topics for fault tolerance and state recovery
* **Rebalancing complexity**: State stores must be migrated/rebuilt during rebalancing
* **Exactly-once semantics**: More complex to achieve (requires transactional producers)
* **Scaling limitations**: State stores are tied to specific partitions, limiting flexibility
* **Resource overhead**: Higher memory and disk usage for state stores

**Note**: Ordering is **still preserved** per partition even with stateful operations. The main changes are complexity, resource usage, and fault tolerance requirements.

## Topology Diagram

```
chat-input (3 partitions)
    |
    | [peek: IN log]
    |
    | [filter: isToxic]
    |    |
    |    +---> toxicMessages
    |    |         |
    |    |         | [peek: DROP log]
    |    |         |
    |    |         +---> chat-moderation
    |    |
    |    +---> nonToxicMessages
    |              |
    |              | [filter: isSystemSender]
    |              |    |
    |              |    +---> systemMessages
    |              |    |         |
    |              |    |         | [peek: ROUTE_SYSTEM log]
    |              |    |         |
    |              |    |         +---> chat-system
    |              |    |
    |              |    +---> normalMessages
    |              |              |
    |              |              | [peek: ROUTE_USER log]
    |              |              | [mapValues: add timestamp]
    |              |              |
    |              |              +---> chat-output
```