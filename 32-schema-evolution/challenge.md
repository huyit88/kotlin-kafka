### Dependencies

* `implementation("io.confluent:kafka-avro-serializer:7.6.0")`
* `implementation("org.apache.avro:avro:1.11.3")`

---

## Problem A

### Requirement

Configure **schema compatibility** for the `transactions-avro` subject.

1. Subject:

* `transactions-avro-value`

2. Set compatibility mode to:

* `BACKWARD`

3. Verify current compatibility via REST API.

### Acceptance criteria

* Schema Registry reports compatibility as `BACKWARD`
* No errors when querying config

### Suggested Import Path

* *(None — REST only)*

### Command to verify/run

```bash
./gradlew :32-schema-evolution:bootRun

curl -X PUT http://localhost:8081/config/transactions-avro-value \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"compatibility":"BACKWARD"}'

curl http://localhost:8081/config/transactions-avro-value
# Expected: {"compatibilityLevel":"BACKWARD"}
```

---

## Problem B

### Requirement

Evolve the **Transaction schema from v1 → v2** in a backward-compatible way.

1. Update `Transaction.avsc` by adding:

```json
{ "name": "merchantId", "type": ["null", "string"], "default": null }
```

2. Regenerate Avro classes.
3. Produce a **v2 transaction** with `merchantId` populated.

Log:

```
AVRO_PRODUCED_V2 id=<transactionId> merchantId=<merchantId>
```

### Acceptance criteria

* Schema Registry shows **two versions** for subject:

  * `transactions-avro-value`: `[1,2]`
* Producing v2 records succeeds without errors
* Consumer still reads records successfully

### Suggested Import Path

```kotlin
import com.example.Transaction
```

### Command to verify/run

```bash
./gradlew :32-schema-evolution:generateAvroJava
./gradlew :32-schema-evolution:bootRun

curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-300","amount":55.0,"currency":"USD","merchantId":"m-123"}'

curl http://localhost:8081/subjects/transactions-avro-value/versions
# Expected: [1,2]
```

---

## Problem C

### Requirement

Prove **backward compatibility in practice**.

Scenario:

* Consumer code **does not reference** `merchantId`
* Producer sends **v2 records**

Consumer log:

```
AVRO_CONSUMED id=<id> amount=<amount> currency=<currency>
```

### Acceptance criteria

* Consumer does **not crash**
* Old fields deserialize correctly
* `merchantId` is ignored or null in consumer

### Suggested Import Path

```kotlin
import org.apache.avro.generic.GenericRecord
```

### Command to verify/run

```bash
# Produce multiple v2 records
curl -X POST http://localhost:8080/tx/avro \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-301","amount":10.0,"currency":"EUR","merchantId":"m-999"}'
```

---

## Problem D

### Requirement

Attempt a **breaking schema change** and observe rejection.

1. Modify schema:

* Change `amount` from `double` → `string`

2. Try to register this schema under:

* `transactions-avro-value`

### Acceptance criteria

* Schema Registry rejects schema with **HTTP 409**
* Error message indicates incompatibility

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
curl -X POST http://localhost:8081/subjects/transactions-avro-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"Transaction\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"transactionId\",\"type\":\"string\"},{\"name\":\"amount\",\"type\":\"string\"},{\"name\":\"currency\",\"type\":\"string\"}]}"
  }'
# Expected: HTTP 409 Conflict
```

---

## Problem E

### Requirement

Experiment with **compatibility modes**.

1. Change compatibility to:

* `NONE`

2. Re-attempt the breaking schema registration.
3. Restore compatibility to `BACKWARD`.

Document findings in `SCHEMA_EVOLUTION.md`:

* What changed when compatibility was `NONE`
* Why this is dangerous in production
* Recommended compatibility strategy for fraud/payment pipelines

### Acceptance criteria

* Breaking schema registers successfully when compatibility = NONE
* Compatibility restored to BACKWARD afterward
* `SCHEMA_EVOLUTION.md` clearly explains risks and best practices

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Set NONE
curl -X PUT http://localhost:8081/config/transactions-avro-value \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"compatibility":"NONE"}'

# Re-try breaking schema (should succeed now)

# Restore BACKWARD
curl -X PUT http://localhost:8081/config/transactions-avro-value \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"compatibility":"BACKWARD"}'

cat SCHEMA_EVOLUTION.md
```
