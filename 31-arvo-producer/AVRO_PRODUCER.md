# Avro Producer Guide

## 1. Generic vs Specific Avro

### Specific Avro
- **Type-safe**: Uses generated classes (e.g., `Transaction`) with compile-time checking
- **Less boilerplate**: Automatic deserialization to specific classes
- **Better IDE support**: Autocomplete and refactoring capabilities
- **Requires code generation**: Schema must be known at compile time
- **Example**: `Transaction.newBuilder().setTransactionId("tx-1").build()`

### Generic Avro
- **Uses `GenericRecord`**: Runtime type checking, weaker type safety
- **More flexible**: Can handle schemas not known at compile time
- **Fine-grained control**: Manual field access via `get("fieldName")`
- **Better for**: Dynamic schemas, schema evolution scenarios, or when schema changes frequently
- **Example**: `GenericData.Record(schema).apply { put("transactionId", "tx-1") }`

**When to use each:**
- **Specific Avro**: Production systems with stable schemas, type safety is critical
- **Generic Avro**: Dynamic systems, frequent schema changes, or when schema is discovered at runtime

---

## 2. How Schema Registration Happens on First Produce

When a producer sends an Avro message for the first time:

1. **Producer checks Schema Registry** for schema under subject `{topic-name}-value` (e.g., `transactions-avro-value`)
2. **If schema doesn't exist**: 
   - Schema Registry registers the new schema
   - Assigns a unique schema ID (integer) and version number
   - Returns the schema ID to the producer
3. **If schema exists**: 
   - Schema Registry validates compatibility with existing schema
   - Returns the existing schema ID
4. **Producer embeds schema ID** (not the full schema) in the message payload
5. **Message is sent to Kafka** with the schema ID embedded in the binary format

**Key points:**
- Schema registration happens automatically on first produce (unless `auto.register.schemas=false`)
- Only the schema ID (4 bytes) is embedded, not the full schema definition
- Consumers use the schema ID to fetch the complete schema from Schema Registry for deserialization

---

## 3. What's Inside a Kafka Avro Message

A Kafka Avro message contains three components in binary format:

### Message Structure
1. **Magic byte** (1 byte): Fixed value `0x0` to identify Avro format
2. **Schema ID** (4 bytes): Integer ID from Schema Registry (big-endian format)
3. **Avro data** (variable length): Binary-encoded Avro record data

### Binary Layout
```
[Magic Byte: 0x0][Schema ID: 4 bytes][Avro Binary Data: variable]
```

**Total overhead**: 5 bytes (1 byte magic + 4 bytes schema ID) + Avro binary-encoded data

**Why this format:**
- **Magic byte**: Identifies the message as Avro format (enables format detection)
- **Schema ID**: Small identifier instead of full schema (reduces message size)
- **Binary encoding**: Efficient compact representation of data

---

## 4. Why Keys Matter for Ordering (transactionId)

Kafka guarantees ordering **per partition**. Messages with the same key are always assigned to the same partition via hash partitioning.

### For `transactionId` as Key:
- **Same transaction = Same partition**: All messages for transaction `tx-200` go to the same partition
- **Ordered processing**: Events for the same transaction are processed in order
- **Critical for payment systems**: Ensures correct sequence (e.g., debit before credit, validation before processing)

### Example:
```
Transaction tx-200 events:
1. tx-200: CREATED    → Partition 0
2. tx-200: VALIDATED  → Partition 0 (same partition, maintains order)
3. tx-200: PROCESSED  → Partition 0 (same partition, maintains order)
```

**Without keys**: Messages would be distributed randomly across partitions, losing ordering guarantees.

---

## 5. What Configs Make Producer Safer

### acks=all
**What it does**: Wait for all in-sync replicas to acknowledge before considering write successful.

**Why it matters**: Prevents data loss if the leader broker fails before replicas are updated. Critical for financial transactions where data loss is unacceptable.

### enable.idempotence=true
**What it does**: Prevents duplicate messages on retries using producer ID and sequence numbers.

**Why it matters**: Ensures exactly-once semantics. Essential for payment systems where duplicate transactions could cause financial discrepancies or double-charging.

### retries=Integer.MAX_VALUE
**What it does**: Retry indefinitely until message succeeds or delivery timeout expires.

**Why it matters**: Prevents data loss from transient network or broker issues. Ensures no transaction data is lost in critical payment pipelines.

### max.in.flight.requests.per.connection=5
**What it does**: Allows up to 5 unacknowledged requests per connection.

**Why it matters**: With idempotence enabled, this maintains ordering guarantees while improving throughput for high-volume payment processing. Without idempotence, this would be limited to 1 to preserve ordering.

---

## ASCII Flow

```
REST -> KafkaTemplate -> Schema Registry -> Kafka topic
```

### Detailed Flow:

1. **REST endpoint** receives JSON payload: `{"transactionId":"tx-200","amount":42.0,"currency":"USD"}`
2. **KafkaTemplate** converts JSON to Avro `Transaction` object using builder pattern
3. **KafkaTemplate → Schema Registry**: 
   - Checks if schema exists for subject `transactions-avro-value`
   - If not exists: Registers schema and gets schema ID
   - If exists: Retrieves existing schema ID
4. **KafkaTemplate** embeds schema ID (4 bytes) + magic byte (1 byte) in message
5. **KafkaTemplate → Kafka topic**: Sends message to `transactions-avro` topic with:
   - Key: `transactionId` (for partitioning)
   - Value: `[0x0][schemaId][avroBinaryData]`

### Consumer Side:
6. **Consumer** receives message from Kafka
7. **Consumer → Schema Registry**: Uses embedded schema ID to fetch full schema
8. **Consumer** deserializes Avro binary data using fetched schema
9. **Consumer** processes `Transaction` object with type-safe access
