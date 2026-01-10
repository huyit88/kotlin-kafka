### Dependencies

* *(No new application dependencies — this challenge is **Kafka + Docker configuration focused**)*

---

## Problem A

### Requirement

Enable **SASL/PLAIN authentication** on your Kafka broker (on top of SSL).

You must:

1. Create a JAAS file `kafka_server_jaas.conf`
2. Define at least **3 users**:

   * `admin`
   * `fraud`
   * `analytics`
3. Configure the broker to:

   * Use `SASL_SSL`
   * Enable `PLAIN` mechanism
   * Use the JAAS file at startup

Files:

* `kafka_server_jaas.conf`
* Updated `docker-compose.yml`

### Acceptance criteria

* Kafka broker starts successfully
* Broker logs show SASL/PLAIN enabled
* No PLAINTEXT listener exists
* Broker does **not** accept unauthenticated clients

### Suggested Import Path

* *(None — infra only)*

### Command to verify/run

```bash
docker compose down
docker compose up -d

docker logs kafka-39 | grep -i sasl
# Expected: logs indicating SASL_SSL and PLAIN enabled
```

---

## Problem B

### Requirement

Authenticate a Kafka **producer using SASL/PLAIN**.

1. Create `client-sasl.properties` for user `fraud`

2. Properties must include:

   * `security.protocol=SASL_SSL`
   * `sasl.mechanism=PLAIN`
   * `sasl.jaas.config` with username/password
   * SSL truststore config

3. Produce messages to topic:

* `payments`

### Acceptance criteria

* Producing **without** SASL config fails
* Producing **with** `client-sasl.properties` succeeds
* Broker logs show authenticated principal:

  ```
  User:fraud
  ```

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Must FAIL
kafka-console-producer --bootstrap-server localhost:9093 --topic payments

# Must SUCCEED
kafka-console-producer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --producer.config /etc/kafka/client-sasl.properties
```

---

## Problem C

### Requirement

Authenticate a Kafka **consumer using SASL/PLAIN**.

1. Use the same `client-sasl.properties`
2. Consume from topic:

* `payments`

3. Ensure consumer group works normally

### Acceptance criteria

* Consumer connects successfully
* Messages produced by `fraud` user are readable
* Broker logs show:

  ```
  Authenticated principal User:fraud
  ```

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --from-beginning \
  --consumer.config /etc/kafka/client-sasl.properties
```

---

## Problem D

### Requirement

Demonstrate **authentication failure cases**.

Test at least **2 failure scenarios**:

1. Wrong password
2. Unknown username

Document failures in `SASL_FAILURES.md`:

* Command run
* Error message
* Why this failure is important for security

### Acceptance criteria

* Both failures are reproducible
* Kafka rejects the connection
* Errors are clearly documented

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Modify password in client-sasl.properties and retry
```

---

## Problem E

### Requirement

Create `SASL_NOTES.md` explaining **Kafka SASL fundamentals**.

Answer concisely:

1. Difference between SSL and SASL
2. What a Kafka principal is
3. Why SASL alone is not enough
4. Why one username per service is critical
5. When mTLS might replace SASL

Include ASCII flow:

```text
Client --(SASL/PLAIN)--> Broker --principal--> ACLs
```

### Acceptance criteria

* Clear separation of authentication vs authorization
* Correct terminology
* Practical explanations (not academic)

### Command to verify/run

```bash
cat SASL_NOTES.md
```

---

## ✅ What You’ll Gain from This Challenge

* Hands-on Kafka authentication
* Ability to debug SASL failures
* Production-ready mental model
* Strong interview answers for Kafka security
