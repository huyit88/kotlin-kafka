## 1. What is a KStream?

A **KStream** is an unbounded stream of immutable records (events). Each record represents a single event, and duplicate keys result in multiple records being emitted. It's like an append-only event log where every event is preserved and processed.

## 2. What is a KTable?

A **KTable** is a changelog stream that represents the latest value per key. It's a materialized view (state table) where duplicate keys update the existing value (upsert), keeping only the most recent state. Think of it as a key-value store that maintains the current snapshot of data.

## 3. Event Log vs State Table Mental Model

**Event Log (KStream):**
- Like a transaction history of a bank account
- Every transaction is recorded as a separate entry
- Shows the complete sequence of events over time
- Example: `alice:ONLINE`, `alice:ONLINE`, `alice:OFFLINE` (all three events are preserved)

**State Table (KTable):**
- Like the current balance of a bank account
- Only the latest value per key is maintained
- Shows the current state, not the history
- Example: `alice:OFFLINE` (only the final state is kept)

## 4. What a Tombstone is and Why it Matters

A **tombstone** is a record with a non-null key and a `null` value. It signals the deletion of a key from a KTable. 

**Why it matters:**
- **Log Compaction**: Kafka uses tombstones to remove old records during log compaction, preventing unbounded growth of changelog topics
- **Deletion Marker**: It explicitly marks a key as deleted, allowing downstream consumers to know when to remove data
- **State Cleanup**: Enables proper cleanup of state stores, ensuring deleted keys don't persist indefinitely

## 5. Real-World Use Cases for Each

### KStream Use Cases:
- **Stateless operations**: Logging, forwarding, filtering, and transforming messages
- **Event routing**: Route events to different topics based on content
- **Real-time monitoring**: Process and alert on events as they occur
- **Data enrichment**: Add metadata to events without maintaining state

### KTable Use Cases:
- **Stateful operations**: Aggregations like count, sum, reduce, which depend on previous message state
- **Reference data**: Maintain lookup tables (user profiles, product catalogs)
- **Current state tracking**: Track current status of entities (user presence, inventory levels)
- **Windowed aggregations**: Time-based aggregations (hourly counts, daily sums)

## 6. Why KTable is Powerful for Joins and Lookups

KTable provides **O(1) key-based lookups**, making it ideal for joins and lookups:

- **Stream-Table Joins**: Enrich a KStream with reference data from a KTable (e.g., enrich order events with customer profiles)
- **Table-Table Joins**: Join two KTables to combine reference data
- **Real-time Enrichment**: No need for external database lookups—data is materialized in the state store
- **Low Latency**: In-memory/disk-backed state stores provide fast access compared to external systems
- **Fault Tolerance**: State is backed by Kafka changelog topics, ensuring durability and recovery

## Comparison Table

| Aspect | KStream | KTable |
|--------|---------|--------|
| **Data Model** | Unbounded event stream | Changelog stream (latest value per key) |
| **Duplicate Keys** | All records emitted | Only updates when value changes |
| **State** | Stateless | Stateful (materialized) |
| **Use Case** | Event processing, routing | Aggregations, lookups, joins |
| **Example** | All login events | Current user status |
| **Memory** | No state storage | Maintains state store |

## ASCII Diagram

```
user-presence -> KTable(user-status-store)
```

More detailed flow:

```
user-presence topic:
  Event1: alice:ONLINE
  Event2: alice:ONLINE  (duplicate - KTable ignores)
  Event3: alice:OFFLINE

         ↓
  KTable(user-status-store)
         ↓
  Final state: key=alice, value=OFFLINE
```