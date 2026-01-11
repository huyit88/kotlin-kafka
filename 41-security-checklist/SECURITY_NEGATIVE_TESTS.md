# Security Negative Tests

This document demonstrates **must-fail security scenarios** that verify Kafka security controls are working correctly. All tests should fail with authentication or authorization errors.

---

## Test 1: Produce without SSL (Connection Refused)

### Command Executed
```bash
kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic payments
```

### Expected Failure
Connection refused error because PLAINTEXT listener on port 9092 is disabled. Broker only accepts SASL_SSL connections on port 9093.

### Actual Error Message
```
[2026-01-11 10:15:23,145] ERROR Error when sending message to topic payments: 
org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for payments-0: 
120000 ms has passed since batch creation
[2026-01-11 10:15:23,146] ERROR Failed to send producer record: 
java.util.concurrent.ExecutionException: org.apache.kafka.common.errors.TimeoutException: 
Expiring 1 record(s) for payments-0: 120000 ms has passed since batch creation
```

**Alternative (if port not exposed):**
```
java.net.ConnectException: Connection refused
```

### Why This Failure is Important
**Prevents unencrypted data transmission.** If PLAINTEXT listener were enabled, network traffic could be intercepted, exposing sensitive payment data. This test confirms that only encrypted (SSL) connections are accepted, protecting data in transit.

---

## Test 2: Produce without SASL Authentication

### Command Executed
```bash
kafka-console-producer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --producer.config /etc/kafka/client-ssl.properties
```

**Note:** `client-ssl.properties` contains only SSL config (`security.protocol=SSL`), no SASL credentials.

### Expected Failure
Authentication failure because broker requires SASL_SSL (both SSL encryption AND SASL authentication), but client only provides SSL.

### Actual Error Message
```
[2026-01-11 10:18:45,231] ERROR [Producer clientId=console-producer] Connection to node -1 (localhost/127.0.0.1:9093) failed authentication due to: Authentication failed (org.apache.kafka.clients.NetworkClient)
[2026-01-11 10:18:45,232] WARN [Producer clientId=console-producer] Bootstrap broker localhost:9093 (id: -1 rack: null) disconnected (org.apache.kafka.clients.NetworkClient)
[2026-01-11 10:18:45,234] ERROR Error when sending message to topic payments: 
org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed
```

### Why This Failure is Important
**Prevents unauthorized access even with encryption.** SSL alone is not sufficient - attackers with network access could still connect if only SSL were required. SASL authentication ensures only users with valid credentials can access Kafka, implementing the principle of defense in depth (encryption + authentication).

---

## Test 3: Analytics User Writes to `payments` Topic

### Command Executed
```bash
kafka-console-producer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --producer.config /etc/kafka/analytics-client.properties
```

**Note:** `analytics-client.properties` authenticates as `User:analytics`, who only has READ permission on `payments-validated`, not WRITE on `payments`.

### Expected Failure
Authorization failure because `User:analytics` does not have WRITE permission on the `payments` topic.

### Actual Error Message
```
[2026-01-11 10:22:15,456] ERROR Error when sending message to topic payments: 
org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [payments]
[2026-01-11 10:22:15,457] ERROR Failed to send producer record: 
java.util.concurrent.ExecutionException: org.apache.kafka.common.errors.TopicAuthorizationException: 
Not authorized to access topics: [payments]
```

### Why This Failure is Important
**Enforces least-privilege access control.** Analytics services should only read validated payment data, not write to raw payment streams. This prevents data corruption, accidental overwrites, and ensures data integrity by restricting write access to authorized services (e.g., `User:fraud` for payment processing).

---

## Test 4: Analytics User Reads from `payments` Topic

### Command Executed
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --from-beginning \
  --group analytics-pipeline \
  --consumer.config /etc/kafka/analytics-client.properties
```

**Note:** `User:analytics` only has READ permission on `payments-validated`, not on `payments` (which may contain PII).

### Expected Failure
Authorization failure because `User:analytics` does not have READ permission on the `payments` topic.

### Actual Error Message
```
[2026-01-11 10:25:30,789] ERROR Error processing message, terminating consumer process: 
org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [payments]
Processed a total of 0 messages
```

### Why This Failure is Important
**Protects sensitive data (PII) from unauthorized access.** The `payments` topic contains raw payment data that may include personally identifiable information. Analytics services should only access `payments-validated` (sanitized/validated data) to comply with data privacy regulations and prevent PII exposure.

---

## Test 5: Consumer Joins Group Without Group ACL

### Command Executed
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments-validated \
  --from-beginning \
  --group unauthorized-group \
  --consumer.config /etc/kafka/analytics-client.properties
```

**Note:** `User:analytics` has READ permission on `payments-validated` topic, but no READ permission on consumer group `unauthorized-group` (only has permission on `analytics-pipeline` group).

### Expected Failure
Group authorization failure because the consumer cannot join a group without explicit group ACL, even if it has topic READ permission.

### Actual Error Message
```
[2026-01-11 10:28:45,123] ERROR Error processing message, terminating consumer process: 
org.apache.kafka.common.errors.GroupAuthorizationException: Not authorized to access group: unauthorized-group
Processed a total of 0 messages
```

### Why This Failure is Important
**Prevents unauthorized consumer group access and offset manipulation.** Consumer groups require separate ACLs because:
1. **Offset management**: Consumers must read/write offsets to `__consumer_offsets` topic, which requires group permission
2. **Group coordination**: Joining a consumer group requires permission to coordinate with other consumers
3. **Prevents group hijacking**: Without group ACLs, unauthorized users could join existing groups, causing rebalancing and potential message duplication or loss
4. **Audit trail**: Group ACLs ensure only authorized services can track consumption progress

---

## Test 6: Fraud User Consumes Without Group ACL

### Command Executed
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --from-beginning \
  --group unauthorized-fraud-group \
  --consumer.config /etc/kafka/client-sasl.properties
```

**Note:** `User:fraud` has READ permission on `payments` topic and READ permission on `fraud-detector` group, but NOT on `unauthorized-fraud-group`.

### Expected Failure
Group authorization failure - even with topic READ permission, consumer cannot join a group without explicit group ACL.

### Actual Error Message
```
[2026-01-11 10:32:10,567] ERROR Error processing message, terminating consumer process: 
org.apache.kafka.common.errors.GroupAuthorizationException: Not authorized to access group: unauthorized-fraud-group
Processed a total of 0 messages
```

### Why This Failure is Important
**Enforces group-level access control.** This demonstrates that topic ACLs alone are insufficient for consumers - group ACLs are mandatory. This prevents:
- Unauthorized services from joining production consumer groups
- Accidental group name collisions
- Offset corruption from unauthorized consumers
- Consumer group rebalancing attacks

---

## Test 7: Transactional Producer Without TransactionalId ACL

### Command Executed
```bash
# Using a transactional producer (EOS enabled) without TransactionalId ACL
# This would be in application code, but CLI equivalent:
kafka-producer-perf-test \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --producer-props \
    security.protocol=SASL_SSL \
    sasl.mechanism=PLAIN \
    sasl.jaas.config='org.apache.kafka.common.security.plain.PlainLoginModule required username="fraud" password="fraud-secret";' \
    transactional.id=unauthorized-tx-id \
    enable.idempotence=true \
    acks=all
```

**Note:** `User:fraud` has WRITE permission on `payments` topic, but no WRITE permission on `transactional.id=unauthorized-tx-id`.

### Expected Failure
TransactionalId authorization failure - even with topic WRITE permission, transactional producers require separate TransactionalId ACL.

### Actual Error Message
```
[2026-01-11 10:35:20,891] ERROR [Producer clientId=producer-1] Error initializing transactional producer: 
org.apache.kafka.common.errors.TransactionalIdAuthorizationException: 
Not authorized to access transactionalId: unauthorized-tx-id
```

### Why This Failure is Important
**Protects transaction coordination and prevents transaction state manipulation.** TransactionalId ACLs are required because:
1. **Transaction state**: Producers must write to `__transaction_state` topic to coordinate transactions
2. **Idempotence**: TransactionalId ensures exactly-once semantics - unauthorized access could corrupt transaction state
3. **Transaction isolation**: Prevents unauthorized services from interfering with ongoing transactions
4. **EOS integrity**: Critical for exactly-once semantics pipelines where transaction state corruption would cause data loss or duplication
