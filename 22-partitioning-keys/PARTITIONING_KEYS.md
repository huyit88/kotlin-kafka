# Kafka Partitioning Keys - Interview Guide

## 1. Why Kafka ordering is only within a partition

Kafka guarantees **message ordering only within a single partition**, not across partitions. This is because:

* **Partitions are independent**: Each partition maintains its own sequence of offsets (0, 1, 2, ...). Messages in different partitions are processed in parallel by different consumers.
* **Parallel processing**: Multiple partitions allow horizontal scaling - different consumers can process different partitions simultaneously.
* **Offset ordering**: Within a partition, messages are stored sequentially by offset. Kafka guarantees that offset N+1 is always processed after offset N in the same partition.

**Example:**
```
Partition 0: [msg1@offset0, msg2@offset1, msg3@offset2]  ← Ordered
Partition 1: [msg4@offset0, msg5@offset1]                ← Ordered
Partition 2: [msg6@offset0]                               ← Ordered

But msg4 might be processed before msg3 (different partitions, parallel processing)
```

---

## 2. How keys ensure per-user ordering

**Partition keys** ensure that all messages for the same user go to the same partition, maintaining ordering:

* **Consistent hashing**: Kafka uses `murmur2` hash of the key + modulo operation to determine partition:
  ```
  partition = (murmur2(key) % partitionCount)
  ```
* **Same key → Same partition**: For a given key (e.g., `userId="u1"`), the hash function always returns the same partition number.
* **Sequential processing**: Since all messages for a user land in the same partition, they're processed in offset order, maintaining per-user ordering.

**Key → Partition Mapping Example:**
```
Topic: notifications (3 partitions)

Key "u1" → murmur2("u1") % 3 = 0  → Partition 0
Key "u2" → murmur2("u2") % 3 = 2  → Partition 2
Key "u3" → murmur2("u3") % 3 = 1  → Partition 1
Key "u1" → murmur2("u1") % 3 = 0  → Partition 0 (always same!)

Partition 0: [u1:seq1, u1:seq2, u1:seq3]  ← Ordered per user
Partition 1: [u3:seq1, u3:seq2]
Partition 2: [u2:seq1, u2:seq2]
```

---

## 3. What happens if you increase partitions later

**⚠️ Critical Issue: Ordering breaks for existing keys**

When you increase partitions (e.g., from 3 to 6):

* **Hash function changes**: The modulo operation changes: `hash % 6` instead of `hash % 3`
* **Key redistribution**: Existing keys may map to different partitions:
  ```
  Before (3 partitions): "u1" → hash % 3 = 0
  After (6 partitions):  "u1" → hash % 6 = 3 (different partition!)
  ```
* **Ordering violation**: Messages for the same user can end up in different partitions, breaking per-user ordering guarantees.
* **Consumer rebalancing**: Consumer groups will rebalance to handle new partitions, but this doesn't fix the ordering issue.

**Best Practice**: Choose partition count upfront based on expected throughput. If you must increase partitions, accept that ordering guarantees are lost for existing keys, or use a custom partitioner that maintains backward compatibility.

---

## 4. What a "hot partition" is and how to mitigate it

**Hot partition** = A single partition receiving disproportionately more messages than others, creating a bottleneck.

**Causes:**
* **Hot key**: Many messages use the same key (e.g., `userId="HOT"`), all hashing to the same partition.
* **Skewed distribution**: Natural key distribution isn't uniform (e.g., 80% of users are "premium" users).

**Impact:**
* **Throughput bottleneck**: One partition processes messages slower than others, limiting overall throughput.
* **Consumer underutilization**: Other consumers/partitions sit idle while one partition is overloaded.
* **Scaling doesn't help**: Adding more consumer instances doesn't improve throughput (max parallelism = partition count).

**Mitigation strategies:**
1. **Increase partition count**: More partitions = better distribution (but see #3 about ordering).
2. **Composite keys**: Use `userId + timestamp` or `userId + random` to distribute load while maintaining some ordering.
3. **Custom partitioner**: Implement logic to distribute hot keys across multiple partitions.
4. **Separate topics**: Move hot entities to a dedicated topic with more partitions.
5. **Accept the limitation**: For critical ordering (payments), accept lower throughput to maintain guarantees.

---

## 5. Why payments often use fewer partitions than notifications

**Trade-off: Ordering guarantees vs. Throughput**

**Payments (fewer partitions):**
* **Strong ordering required**: Payment transactions must be processed in exact order (debit before credit, etc.).
* **Lower volume**: Payment events are less frequent than notifications.
* **Consistency over speed**: Prefer correctness and ordering guarantees over maximum throughput.
* **Example**: 3-5 partitions sufficient for payment processing needs.

**Notifications (more partitions):**
* **Higher volume**: Millions of notifications per second (emails, push, SMS).
* **Ordering less critical**: A notification arriving slightly out of order is usually acceptable.
* **Throughput priority**: Need to handle massive scale, so more partitions = more parallelism.
* **Example**: 50-100+ partitions for high-volume notification systems.

**Key Insight**: 
```
Max Consumer Parallelism = Number of Partitions

Payments:  3 partitions  → max 3 consumers → lower throughput, strong ordering
Notifications: 50 partitions → max 50 consumers → high throughput, relaxed ordering
```

---

## Summary Diagram: Keys → Partitions

```
Producer sends messages with keys:

┌─────────────────────────────────────────────────────────┐
│  Producer                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ u1:seq1  │  │ u2:seq1  │  │ u1:seq2  │  ...         │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
└───────┼─────────────┼─────────────┼────────────────────┘
        │             │             │
        │ Hash(key)   │ Hash(key)   │ Hash(key)
        │             │             │
        ▼             ▼             ▼
┌─────────────────────────────────────────────────────────┐
│  Topic: notifications (3 partitions)                   │
│                                                         │
│  Partition 0        Partition 1        Partition 2     │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐    │
│  │ u1:seq1  │      │ u3:seq1  │      │ u2:seq1  │    │
│  │ u1:seq2  │      │ u3:seq2  │      │ u2:seq2  │    │
│  │ u1:seq3  │      │          │      │          │    │
│  └──────────┘      └──────────┘      └──────────┘    │
│  (u1 always        (u3 always        (u2 always        │
│   same partition)   same partition)   same partition)   │
└─────────────────────────────────────────────────────────┘
        │                   │                   │
        │                   │                   │
        ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────┐
│  Consumer Group (3 consumers, 1 per partition)          │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐   │
│  │Consumer 0│      │Consumer 1│      │Consumer 2│   │
│  │Processes │      │Processes │      │Processes │   │
│  │Partition │      │Partition │      │Partition │   │
│  │    0     │      │    1     │      │    2     │   │
│  └──────────┘      └──────────┘      └──────────┘   │
└─────────────────────────────────────────────────────────┘

Result: Per-user ordering maintained (u1 always in partition 0)
        Parallel processing enabled (3 consumers working simultaneously)
```
