# Exactly-Once Semantics (EOS) in Kafka

## 1. Define at-most-once / at-least-once / exactly-once

### At-Most-Once
Messages are delivered **at most once** - meaning they may be lost but never duplicated. This occurs when:
- Consumer commits offsets **before** processing the message
- If processing fails, the message is lost because the offset was already committed
- Trade-off: No duplicates, but potential data loss

### At-Least-Once
Messages are delivered **at least once** - meaning they may be duplicated but never lost. This occurs when:
- Consumer commits offsets **after** processing the message
- If processing fails after commit, the consumer retries and processes the same message again
- Trade-off: No data loss, but potential duplicates

### Exactly-Once
Messages are delivered **exactly once** - no loss, no duplicates. Achieved through:
- **Transactional producers**: All messages in a transaction are atomically committed or aborted
- **Transactional consumers**: Use `isolation.level=read_committed` to only read committed messages
- **Atomic offset commits**: Offsets are committed as part of the transaction via `sendOffsetsToTransaction`
- If any part fails, the entire transaction (messages + offsets) is aborted and retried


---

## 2. What pieces Kafka combines to achieve EOS

Kafka combines several mechanisms to achieve exactly-once semantics:

1. **Idempotent Producer** (`enable.idempotence=true`)
   - Producer assigns unique sequence numbers to each message
   - Broker deduplicates based on (producerId, partition, sequence)
   - Prevents duplicate messages from producer retries

2. **Transactional Producer** (`transactional.id` configured)
   - Groups multiple messages into atomic transactions
   - All-or-nothing: either all messages commit or all abort
   - Prevents partial writes across multiple partitions

3. **Transactional Consumer** (`isolation.level=read_committed`)
   - Only reads messages from committed transactions
   - Filters out messages from aborted/in-flight transactions
   - Prevents "dirty reads" of uncommitted data

4. **Atomic Offset Commit** (`sendOffsetsToTransaction`)
   - Consumer offsets are included in the producer transaction
   - Offsets commit atomically with produced messages
   - Ensures "consume-process-produce" is atomic


---

## 3. What `sendOffsetsToTransaction` guarantees

`sendOffsetsToTransaction` ensures **atomicity between message consumption and production**:

- **Atomic Commit**: Consumer offsets are committed **as part of the producer transaction**
- **All-or-Nothing**: If the transaction commits, both produced messages AND offsets are committed together
- **Abort Safety**: If the transaction aborts, both produced messages AND offsets are rolled back
- **No Duplicate Processing**: Prevents the same message from being reprocessed after a crash, because offsets only commit when the transaction succeeds

**Key guarantee**: The "consume → process → produce" cycle is atomic. Either all succeed (messages produced + offsets committed) or all fail (transaction aborted, offsets not committed, message reprocessed on retry).

---

## 4. EOS scope (Kafka-only, not external DB/API)

**EOS guarantees are limited to Kafka's internal operations only.**

### What EOS Covers:
- ✅ Message delivery within Kafka (producer → broker → consumer)
- ✅ Offset commits within Kafka
- ✅ Atomic transactions across Kafka topics/partitions

### What EOS Does NOT Cover:
- ❌ **External side effects**: Database writes, API calls, file operations
- ❌ **Client retries**: If a client sends the same request twice, EOS won't prevent duplicate processing
- ❌ **Cross-system transactions**: EOS doesn't coordinate with external systems

### Why This Matters:
If your application:
1. Consumes from Kafka (EOS protected)
2. Writes to a database (NOT protected)
3. Produces back to Kafka (EOS protected)

A crash between steps 2 and 3 could result in:
- Database written (external side effect occurred)
- Transaction aborted (Kafka rollback)
- On retry: Database written again (duplicate!)

**Solution**: Use idempotency keys or application-level deduplication for external side effects.

---

## 5. Practical rollout strategy (consumer-first vs producer-first)

### Consumer-First Strategy (Recommended)
1. **Phase 1**: Enable `isolation.level=read_committed` on consumers
   - Consumers now filter uncommitted messages
   - No behavior change if producers aren't transactional yet
   - Safe to deploy first

2. **Phase 2**: Enable transactional producers
   - Producers start using transactions
   - Consumers (already on read_committed) see only committed messages
   - EOS is now active

**Advantages**: 
- Lower risk: Consumers can be upgraded independently
- Backward compatible: Works even if some producers aren't transactional yet
- Easier rollback: Can disable transactions on producers without affecting consumers

### Producer-First Strategy (Not Recommended)
1. Enable transactional producers first
2. Then enable read_committed consumers

**Disadvantages**:
- Higher risk: If consumers aren't ready, they may read uncommitted messages
- Requires coordinated deployment
- Harder to rollback

**Best Practice**: Always start with consumers (`read_committed`), then enable transactional producers.

---

## 6. Common pitfalls (auto-commit on, read_uncommitted, missing transactional.id)

### Pitfall 1: Auto-Commit Enabled
**Problem**: `enable.auto.commit=true` commits offsets outside the transaction
- Offsets commit independently of transaction commit
- If transaction aborts, offsets are already committed
- Message is lost (never reprocessed) or causes duplicate processing

**How to Detect**:
- Check consumer config: `enable.auto.commit` must be `false`
- Monitor logs: Look for offset commits happening before transaction commits
- Use `/eos/check` endpoint to validate `autoCommit: false`

**Fix**: Set `enable.auto.commit=false` and commit offsets via `sendOffsetsToTransaction`

### Pitfall 2: Read Uncommitted Isolation Level
**Problem**: `isolation.level=read_uncommitted` reads messages from aborted transactions
- Consumer sees "dirty reads" - messages that will be rolled back
- Causes duplicate processing when transaction retries
- Breaks exactly-once guarantee

**How to Detect**:
- Check consumer config: `isolation.level` must be `read_committed`
- Test with aborted transactions: Uncommitted consumer should see messages, committed should not
- Use `/eos/check` endpoint to validate `isolationLevel: "read_committed"`

**Fix**: Set `isolation.level=read_committed` on all consumers

### Pitfall 3: Missing Transactional ID
**Problem**: `transactional.id` not configured on producer
- Producer cannot participate in transactions
- `sendOffsetsToTransaction` will fail
- EOS is not active

**How to Detect**:
- Check producer config: `transactional.id` must be set and non-empty
- Runtime error: `sendOffsetsToTransaction` throws exception if transactional.id is missing
- Use `/eos/check` endpoint to validate `transactionalId` is present

**Fix**: Configure unique `transactional.id` per producer instance (e.g., `fraud-pipeline-tx-1`)

**Score: 2/2** ✓ All three pitfalls explained with detection methods

---

## ASCII Diagram

```
consume -> process -> produce -> offsets -> commit
   |         |          |          |          |
   |         |          |          |          +-> All succeed atomically
   |         |          |          +-> sendOffsetsToTransaction
   |         |          +-> kafkaTemplate.send() (in transaction)
   |         +-> Business logic
   +-> KafkaListener reads message (read_committed)

If any step fails:
   abort -> Transaction aborted, offsets not committed, message reprocessed
```