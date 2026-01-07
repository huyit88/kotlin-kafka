# Kafka Transactions: Interview-Ready Guide

## 1. Idempotence vs Transactions

**Idempotence** (`enable.idempotence=true`) ensures that retrying a message send operation does not result in duplicate messages. It uses a producer ID (PID) and sequence numbers to detect and filter out duplicate writes at the broker level. This provides **at-least-once semantics** within a single partition.

**Transactions** provide **atomicity** and **exactly-once semantics** across multiple partitions and topics. A transaction ensures that either all messages in the transaction are successfully written (committed) or none are (aborted). Transactions guarantee:
- **Atomic writes**: All-or-nothing semantics across multiple partitions/topics
- **Exactly-once semantics**: No duplicates, no data loss
- **Read-committed isolation**: Consumers only see committed messages

**Key Relationship**: Transactions **include** idempotence (transactions automatically enable idempotence), but idempotence alone does **not** provide transactions. You can have idempotent producers without transactions, but transactional producers are always idempotent.

---

## 2. Why `transactional.id` is Required

The `transactional.id` serves three critical purposes:

1. **Producer Instance Tracking**: The transaction coordinator uses it to uniquely identify and track a producer instance across restarts.

2. **Zombie Fencing**: If a producer crashes mid-transaction, a new producer instance with the same `transactional.id` can "fence out" the old instance. The coordinator ensures only one active producer per `transactional.id`, preventing duplicate transactions from zombie instances.

3. **Exactly-Once Semantics**: The coordinator uses `transactional.id` to maintain transaction state and ensure that even if a producer retries after a network error, the transaction is only committed once.

**Important**: Each `transactional.id` must be unique per producer instance. If multiple instances share the same ID, only one can be active at a time.

---

## 3. What Happens on Producer Crash Mid-Transaction

When a producer crashes in the middle of a transaction:

1. **Transaction Timeout**: Kafka has a `transaction.timeout.ms` configuration (default 60 seconds). If the producer doesn't send a commit or abort within this window, the transaction coordinator will abort the transaction.

2. **Coordinator Detection**: The transaction coordinator detects the producer is no longer responding (via heartbeat failures or timeout).

3. **Automatic Abort**: The coordinator automatically aborts the incomplete transaction, marking all messages in that transaction as aborted.

4. **Message Visibility**: Aborted messages remain in Kafka but are **not visible** to consumers using `read_committed` isolation level. They are effectively "hidden" from committed reads.

5. **Recovery**: When a new producer instance with the same `transactional.id` starts, it can begin new transactions. The coordinator ensures no duplicate processing of the aborted transaction.

**Key Point**: Aborted messages are not deleted from Kafka—they're just marked as aborted and filtered out by read-committed consumers.

---

## 4. Why Consumers Must Use `read_committed`

The `isolation.level=read_committed` configuration ensures consumers only see messages from **committed transactions**, not from aborted or in-progress transactions.

**Without `read_committed`** (using `read_uncommitted`, the default):
- Consumers can see messages from aborted transactions (dirty reads)
- This breaks transactional guarantees and can lead to inconsistent application state
- Example: A payment transaction that was aborted might still be visible, causing incorrect balance calculations

**With `read_committed`**:
- Consumers only see messages from successfully committed transactions
- Maintains transactional consistency guarantees
- Essential for applications requiring exactly-once semantics

**Isolation Levels**:
- `read_uncommitted`: See all messages, including aborted transactions (dirty reads)
- `read_committed`: Only see messages from committed transactions (default for transactional consumers)

**Critical for**: Payment systems, financial transactions, and any use case where seeing aborted data would cause incorrect behavior.

---

## 5. When **Not** to Use Transactions

Transactions add **overhead** and should be avoided when:

1. **Simple Use Cases**:
   - Logging and metrics collection
   - Fire-and-forget messaging patterns
   - Single-partition writes where idempotence is sufficient

2. **Performance-Critical Scenarios**:
   - High-throughput pipelines where latency matters more than exactly-once guarantees
   - Real-time streaming where millisecond delays are unacceptable

3. **When Overhead Outweighs Benefits**:
   - **Latency**: Transactions add ~2-3x latency due to coordination overhead
   - **Throughput**: Reduced throughput due to additional network round-trips to transaction coordinator
   - **Complexity**: More complex error handling and recovery scenarios

4. **Single Message Sends**:
   - If you're only sending one message, transactions provide no benefit over idempotence alone

**Use Transactions When**:
- Atomic writes across multiple partitions/topics are required
- Exactly-once semantics are critical (payment processing, financial systems)
- You need to ensure all-or-nothing message delivery
- Data consistency is more important than raw performance

**Trade-off**: Transactions provide stronger guarantees at the cost of performance. Choose based on your consistency requirements.

---

## ASCII Flow

```
begin -> send -> send -> commit
```

**Detailed Transaction Lifecycle**:

1. **begin**: Producer calls `initTransactions()` → Transaction coordinator assigns producer ID and epoch
2. **send**: Producer sends messages to multiple topics/partitions within transaction boundary
3. **send**: Additional messages added to same transaction (all buffered, not yet visible)
4. **commit**: Producer calls `commitTransaction()` → Coordinator writes commit marker → All messages become visible to read-committed consumers

**On Error**: If exception occurs before commit → `abortTransaction()` → All messages marked as aborted → Not visible to read-committed consumers
