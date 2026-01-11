## Adding TransactionalId ACL

Add ACL for transactionalId with prefix:
```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:fraud \
  --operation WRITE \
  --transactional-id fraud-pipeline-tx-
```

## Why TransactionalId Needs ACLs

The `transactionalId` is a unique identifier used in Kafka's **Exactly-Once Semantics (EOS)** to ensure:
1. **Idempotent writes** - Prevents duplicate messages even if a producer retries
2. **Transactional coordination** - Coordinates writes across multiple partitions/topics atomically
3. **Transaction state management** - Kafka stores transaction metadata in `__transaction_state` topic

When ACLs are enabled, Kafka requires **WRITE permission on the transactionalId resource** because:
- The producer must **register the transactionalId** with the transaction coordinator
- The producer must **write transaction markers** to commit/abort transactions
- The producer must **update transaction state** in the `__transaction_state` topic

Without this ACL, even if the user has WRITE permission on topics, they cannot initiate or commit transactions.

## Symptoms When Missing

When the transactionalId ACL is missing, you'll see authorization errors:

**Error Message:**
```
org.apache.kafka.common.errors.TransactionalIdAuthorizationException: 
  TransactionalId authorization failed
```

**Common scenarios:**
- Producer fails during `initTransactions()` call
- Producer fails during `beginTransaction()` 
- Producer fails during `commitTransaction()` or `abortTransaction()`
- Error occurs even if the user has WRITE permission on all target topics

**Example error in logs:**
```
[ERROR] TransactionalId authorization failed for transactionalId: fraud-pipeline-tx-001
org.apache.kafka.common.errors.TransactionalIdAuthorizationException: 
  Not authorized to access transactionalId: fraud-pipeline-tx-001
```

## How This Relates to `sendOffsetsToTransaction`

The `sendOffsetsToTransaction()` method is part of Kafka's **transactional read-process-write** pattern:

1. **Consumer reads messages** from a topic (requires topic READ + group READ ACLs)
2. **Producer processes and writes** to output topics (requires topic WRITE ACLs)
3. **Producer sends offsets** to transaction using `sendOffsetsToTransaction()` 
4. **Transaction commits atomically** - both output messages AND consumer offsets

**Why transactionalId ACL is critical here:**

When `sendOffsetsToTransaction()` is called, the producer must:
- **Write offset commits** to the `__consumer_offsets` topic as part of the transaction
- **Coordinate the transaction** that includes both output messages and offset commits
- **Ensure atomicity** - either all writes succeed (messages + offsets) or all fail

**Without transactionalId WRITE permission:**
- The transaction cannot be initiated or committed
- `sendOffsetsToTransaction()` will fail with `TransactionalIdAuthorizationException`
- Even though the user might have:
  - Topic WRITE permissions (for output messages)
  - Group READ permissions (for consuming)
  - Group WRITE permissions (for offset commits)

**Key Takeaway:** The transactionalId ACL is a **separate authorization check** that controls whether a producer can manage transactions, independent of topic/group permissions. It's required for any transactional producer, especially when using `sendOffsetsToTransaction()` for exactly-once processing.
