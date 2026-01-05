# SCHEMA_REGISTRATION.md - Grading Report



### 1. What "schema registration" means

**Answer:**
"Schema registration is the process of storing an Avro schema definition in Schema Registry. When a producer registers a schema, Schema Registry assigns it a unique schema ID and version number. This creates a centralized contract that both producers and consumers can reference, ensuring data compatibility across the system."

---

### 2. Why producers (not consumers) register schemas

**Answer:**
"Producers register schemas because they need to obtain a schema ID to embed in each message. When a producer sends a message, it includes the schema ID (not the full schema), allowing consumers to look up the schema from Schema Registry. Consumers don't register because they can read any compatible schema version that's already registered. The producer's registration also triggers Schema Registry to validate the schema for compatibility with existing versions, preventing breaking changes."

---

### 3. What a schema ID is and why it's embedded in messages

**Answer:**
"A schema ID is a unique integer identifier (e.g., 1, 2, 3) assigned by Schema Registry when a schema is first registered. It's embedded in messages (typically 5 bytes) instead of the full schema definition to keep message payloads small. Consumers use this ID to fetch the complete schema from Schema Registry for deserialization. This approach enables schema evolution—different schema versions get different IDs, and consumers can handle multiple versions."

---

### 4. Subject naming strategy and why `topic-value` is common

**Answer:**
"A subject is the unique identifier for a schema in Schema Registry. The subject naming strategy determines how subjects are named. The `TopicNameStrategy` (also called `topic-value`) creates subjects like `my-topic-value`, meaning one schema per topic for message values. This is common because:
1. It's simple and intuitive—one schema per topic
2. Allows independent schema evolution per topic
3. Easy to understand which schema applies to which topic
4. Works well for most use cases

Alternative strategies include `RecordNameStrategy` (one schema per record type across topics) and `TopicRecordNameStrategy` (combination of topic and record name)."

---

### 5. How Schema Registry prevents production incidents
**Answer:**
"Schema Registry prevents production incidents through compatibility checking. When a producer tries to register a new schema version, Schema Registry validates it against existing versions based on the configured compatibility mode (BACKWARD, FORWARD, FULL, or NONE). If the new schema breaks compatibility (e.g., removes a required field, changes a field type), registration is rejected with an HTTP 409 error. This catches breaking changes at development/deployment time rather than at runtime, preventing:
- Consumers from failing to deserialize messages
- Data loss from incompatible schema changes
- Production outages from schema mismatches

The compatibility check ensures that existing consumers can continue reading messages even as schemas evolve."

---

### ASCII Flow Diagram
**Answer:**
```
Producer → Schema Registry:
  1. Register schema → Get schema ID (e.g., ID=5)
  2. Validate compatibility with existing versions

Producer → Kafka:
  3. Send message with embedded schema ID (5 bytes: magic byte + schema ID)

Kafka → Consumer:
  4. Consumer receives message with schema ID

Consumer → Schema Registry:
  5. Fetch full schema using schema ID
  6. Deserialize message using schema
```
