## Problem A

### Requirement

Generate **SSL certificates** for Kafka using a **local Certificate Authority (CA)**.

You must create:

1. CA key + certificate
2. Kafka broker keystore
3. Kafka broker truststore

Files to generate:

* `ca.pem`
* `kafka.keystore.jks`
* `kafka.truststore.jks`

Passwords:

* Use `password` for all stores (explicitly for learning)

### Acceptance criteria

* All three files exist under a local `ssl/` directory
* Keystore contains:

  * broker private key
  * broker certificate
  * CA certificate
* Truststore contains:

  * CA certificate only

### Suggested Import Path

* *(None — CLI only)*

### Command to verify/run

```bash
cd ssl

# List entries
keytool -list -keystore kafka.keystore.jks -storepass password
keytool -list -keystore kafka.truststore.jks -storepass password

# Expected:
# - kafka.keystore.jks contains alias "kafka" and "CARoot"
# - kafka.truststore.jks contains alias "CARoot"
```

---

## Problem B

### Requirement

Enable **SSL listeners** on the Kafka broker while keeping PLAINTEXT.

Kafka must expose:

* `PLAINTEXT://localhost:9092`
* `SSL://localhost:9093`

Update `docker-compose.yml`:

* Add SSL listener
* Mount `ssl/` directory
* Configure keystore & truststore

### Acceptance criteria

* Kafka container starts successfully
* Logs show SSL listener bound to `9093`
* PLAINTEXT still works on `9092`

### Suggested Import Path

* *(None — Docker only)*

### Command to verify/run

```bash
docker compose down
docker compose up -d

docker logs kafka-38 | grep SSL
# Expected: SSL listener startup messages
```

---

## Problem C

### Requirement

Verify **encryption enforcement** using SSL vs PLAINTEXT.

1. Create topic:

* `ssl-test`
```
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic ssl-test --partitions 3 --replication-factor 1
```

2. Test PLAINTEXT produce:

```bash
kafka-console-producer --bootstrap-server localhost:9092 --topic ssl-test
```

3. Test SSL produce:

```bash
docker exec -it kafka-38 bash -lc '
kafka-console-producer \
  --bootstrap-server localhost:9093 \
  --topic ssl-test \
  --producer.config /etc/kafka/client-ssl.properties
  '
```

### Acceptance criteria

* PLAINTEXT producer works without SSL config
* SSL producer works **only** with SSL config
* SSL producer fails if `client-ssl.properties` is missing

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
# Create topic
kafka-topics --bootstrap-server localhost:9092 --create \
  --topic ssl-test --partitions 1 --replication-factor 1

# SSL config test
kafka-console-producer --bootstrap-server localhost:9093 --topic ssl-test
# Expected: failure (no SSL config)
```

---

## Problem D

### Requirement

Force **SSL-only access** for Kafka clients.

Modify broker config:

* Remove `PLAINTEXT` listener
* Keep only:

  ```
  SSL://0.0.0.0:9093
  ```

### Acceptance criteria

* Kafka **refuses all PLAINTEXT connections**
* Only SSL-configured clients can connect
* Existing topics remain intact

### Suggested Import Path

* *(None)*

### Command to verify/run

```bash
docker compose down
docker compose up -d

# This MUST fail
kafka-console-producer --bootstrap-server localhost:9092 --topic ssl-test

# This MUST work
docker exec -it kafka-38 bash -lc '
kafka-console-producer \
  --bootstrap-server localhost:9093 \
  --topic ssl-test \
  --producer.config /etc/kafka/client-ssl.properties
  '
```

---

## Problem E

### Requirement

Create `SSL_NOTES.md` explaining **Kafka SSL fundamentals**.

Answer concisely:

1. Difference between keystore and truststore
2. What SSL protects in Kafka
3. Why SAN / hostname matters
4. Why SSL is enabled before SASL
5. One common SSL misconfiguration and its symptom

Include ASCII diagram:

```text
Producer --SSL--> Broker --SSL--> Broker --SSL--> Consumer
```

### Acceptance criteria

* Correct terminology
* No confusion between encryption and authentication
* Practical, not theoretical

### Command to verify/run

```bash
cat SSL_NOTES.md
```

---

## ✅ What You’ll Gain from This Challenge

* Real Kafka SSL experience (not toy configs)
* Ability to debug SSL handshake issues
* Production-grade mental model
* Strong interview confidence for Kafka security
