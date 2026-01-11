### Dependencies

* *(No new application dependencies — ACLs are broker + CLI configuration)*

---

## Problem A

### Requirement

Enable **ACL authorization** on the broker with **default deny**.

Update `docker-compose.yml` Kafka broker environment to include:

* `KAFKA_AUTHORIZER_CLASS_NAME=kafka.security.authorizer.AclAuthorizer`
* `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=false`
* `KAFKA_SUPER_USERS=User:admin`

Restart Kafka and keep:

* `SASL_SSL` enabled (from Day 39)
* Users: `admin`, `fraud`, `analytics`

### Acceptance criteria

* Broker starts successfully
* Without any ACLs added yet:

  * non-admin users cannot create topics
  * produce/consume attempts fail with authorization errors
* `admin` can still administer the cluster

### Suggested Import Path

* *(None — infra only)*

### Command to verify/run

```bash
docker compose down
docker compose up -d

# As fraud (should FAIL to create topic)
kafka-topics --bootstrap-server localhost:9093 \
  --command-config /etc/kafka/client-sasl.properties \
  --create --topic should-fail --partitions 1 --replication-factor 1
# Expected: authorization error
Error while executing topic command : Authorization failed.
[2026-01-11 09:09:44,146] ERROR org.apache.kafka.common.errors.TopicAuthorizationException: Authorization failed.
`
```

---

## Problem B

### Requirement

Create an **admin client config** and create required topics as admin.

1. Create `admin-sasl.properties` using:

* `security.protocol=SASL_SSL`
* `sasl.mechanism=PLAIN`
* admin credentials
* SSL truststore config

2. As admin, create topics:

* `payments` (3 partitions)
* `payments-validated` (3 partitions)

### Acceptance criteria

* Topics are created successfully with admin config
* Creating topics as non-admin still fails

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
kafka-topics --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --create --topic payments --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --create --topic payments-validated --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --list

#payments
#payments-validated
```

---

## Problem C

### Requirement

Implement **least-privilege ACLs** for `fraud` and `analytics`.

Rules:

* `User:fraud`

  * READ + WRITE on topic `payments`
  * WRITE on topic `payments-validated`
  * READ on consumer group `fraud-detector`
* `User:analytics`

  * READ on topic `payments-validated`
  * READ on consumer group `analytics-pipeline`

Add ACLs using `kafka-acls` with admin config.

### Acceptance criteria

* Fraud can produce to `payments`
* Fraud can consume from `payments` with group `fraud-detector`
* Analytics can consume from `payments-validated` with group `analytics-pipeline`
* Analytics cannot read from `payments`
* Fraud cannot read from `payments-validated` unless explicitly granted

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Add ACLs (examples; you must implement them)
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:fraud \
  --operation READ --operation WRITE \
  --topic payments

kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:fraud \
  --operation WRITE \
  --topic payments-validated

kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:fraud \
  --operation READ \
  --group fraud-detector

kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:analytics \
  --operation READ \
  --topic payments-validated

kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:analytics \
  --operation READ \
  --group analytics-pipeline
```

---

## Problem D

### Requirement

Prove the **group ACL gotcha**.

1. Remove (or do not add) the group ACL for `User:analytics` temporarily.
```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --remove --allow-principal User:analytics \
  --operation READ \
  --group analytics-pipeline
```
2. Attempt to consume `payments-validated` with group `analytics-pipeline`.
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments-validated \
  --group analytics-pipeline \
  --from-beginning \
  --consumer.config /etc/kafka/client-sasl.properties
```
Document what fails and why in `GROUP_ACL_GOTCHA.md`.

Then restore the group ACL and re-test.
```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:analytics \
  --operation READ \
  --group analytics-pipeline
```
### Acceptance criteria

* Without group ACL:

  * consumer fails (even with topic READ)
* After adding group ACL:

  * consumer succeeds
* `GROUP_ACL_GOTCHA.md` explains:

  * what operation was denied
  * why group permissions are needed for offsets

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Attempt consume as analytics (should FAIL without group ACL)
kafka-console-consumer --bootstrap-server localhost:9093 \
  --topic payments-validated --from-beginning \
  --group analytics-pipeline \
  --consumer.config /etc/kafka/analytics-client.properties
```

---

## Problem E

### Requirement

Add **transactionalId ACLs** for an EOS pipeline user.

Scenario:

* Principal: `User:fraud`
* transactional.id prefix: `fraud-pipeline-tx-`

Grant:

* `WRITE` on transactionalId (prefixed)

Then document in `TXID_ACL_NOTES.md`:

* why transactionalId needs ACLs
* symptoms when missing (what error you see)
* how this relates to `sendOffsetsToTransaction`

### Acceptance criteria

* ACL exists for transactionalId prefix
* `TXID_ACL_NOTES.md` is correct and practical

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config /etc/kafka/admin-sasl.properties \
  --add --allow-principal User:fraud \
  --operation WRITE \
  --transactional-id fraud-pipeline-tx-
# Note: use the correct CLI flag for prefixed transactional IDs if your Kafka CLI requires it.
```
