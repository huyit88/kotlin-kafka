# Kafka SSL Fundamentals

## 1. Difference between keystore and truststore
**Answer:**

**Keystore:**
- Contains the **broker's private key and certificate**
- Used by the broker to **prove its identity** to clients during SSL handshake
- The broker presents its certificate from the keystore to establish trust
- Location: `/etc/kafka/secrets/kafka.keystore.jks` (broker side)

**Truststore:**
- Contains **trusted CA certificates** (or trusted peer certificates)
- Used to **validate certificates** presented by other parties
- The broker uses truststore to verify client certificates (if client auth is enabled)
- Clients use truststore to verify the broker's certificate
- Location: `/etc/kafka/secrets/kafka.truststore.jks` (both broker and client side)

**Key Difference:** Keystore = "This is who I am" (identity), Truststore = "These are who I trust" (validation)

---

## 2. What SSL protects in Kafka

**Answer:**

SSL/TLS in Kafka provides three security properties:

1. **Confidentiality (Encryption):**
   - All data in transit is encrypted
   - Prevents network sniffing/eavesdropping
   - Messages, offsets, metadata are encrypted between client and broker

2. **Integrity:**
   - Detects tampering/modification of data in transit
   - Prevents man-in-the-middle attacks from altering messages
   - Uses message authentication codes (MAC) to verify data hasn't changed

3. **Server Authentication:**
   - Verifies the broker's identity using certificates
   - Prevents connection to rogue/impersonated brokers
   - Client validates broker certificate against trusted CA in truststore

**Note:** SSL does NOT authenticate clients by default. Client authentication requires:
- `ssl.client.auth=required` (mutual TLS/mTLS), OR
- SASL (which runs over SSL for SASL_SSL)

---

## 3. Why SAN / hostname matters

**Answer:**

**Subject Alternative Name (SAN) and hostname verification are critical for SSL certificate validation:**

1. **Certificate Validation:**
   - During SSL handshake, client checks if broker's certificate matches the hostname it's connecting to
   - If certificate's CN or SAN doesn't match `localhost:9093` (or the advertised listener), handshake fails
   - Prevents connecting to wrong broker or man-in-the-middle attacks

2. **Common Failure Scenario:**
   - Certificate issued for `kafka.example.com` but client connects to `localhost`
   - Error: `hostname verification failed: Certificate does not match hostname`
   - Solution: Include `localhost` and `127.0.0.1` in certificate SAN, or disable hostname verification (not recommended for production)

3. **Why It Matters in Kafka:**
   - `KAFKA_ADVERTISED_LISTENERS=SSL://localhost:9093` must match certificate hostname
   - Multi-broker clusters need certificates with SAN covering all broker hostnames
   - Inter-broker SSL requires certificates valid for internal broker hostnames

**Example:** If your certificate has SAN `DNS:localhost, DNS:kafka-38`, connections to `localhost:9093` will succeed, but connections to `kafka-38:9093` from other containers will also work.

---

## 4. Why SSL is enabled before SASL
**Answer:**

**SSL must be enabled before SASL because of the layered security model:**

1. **Credential Protection:**
   - SASL credentials (usernames, passwords, tokens) are sent during authentication
   - Without SSL encryption, credentials travel in **plaintext** over the network
   - Attackers can intercept and steal credentials via network sniffing

2. **Security Layer Order:**
   ```
   Application Layer (Kafka messages)
        ↓
   Authentication Layer (SASL) ← Credentials must be encrypted
        ↓
   Encryption Layer (SSL/TLS) ← Must be established first
        ↓
   Network Layer (TCP/IP)
   ```

3. **Protocol Support:**
   - `SASL_PLAINTEXT` = Authentication without encryption (insecure, credentials exposed)
   - `SASL_SSL` = Authentication over encrypted connection (secure)
   - SSL establishes the encrypted tunnel first, then SASL runs inside it

4. **Practical Deployment:**
   - Phase 1: Enable SSL (encrypts all traffic, including unauthenticated connections)
   - Phase 2: Enable SASL over SSL (adds authentication on top of encryption)
   - This allows gradual migration: clients can connect with SSL first, then add SASL credentials later

**Real-world impact:** Without SSL-first approach, enabling SASL exposes all credentials to anyone with network access.

---

## 5. One common SSL misconfiguration and its symptom

**Answer:**

**Common Misconfiguration: Missing or incorrect truststore configuration for inter-broker SSL**

**The Problem:**
When `KAFKA_INTER_BROKER_LISTENER_NAME=SSL` is set but truststore is not properly configured, brokers cannot communicate with each other.

**Specific Misconfiguration:**
```yaml
KAFKA_INTER_BROKER_LISTENER_NAME: SSL
KAFKA_SSL_TRUSTSTORE_FILENAME: kafka.truststore.jks
# Missing: KAFKA_SSL_TRUSTSTORE_LOCATION or truststore password
```

**Symptoms:**
1. **Startup Failure:**
   ```
   ERROR: the trustAnchors parameter must be non-empty
   ConfigException: A client SSLEngine created with the provided settings 
   can't connect to a server SSLEngine created with those settings
   ```

2. **Container Won't Start:**
   - Kafka container exits immediately after startup
   - Logs show SSL handshake validation failure
   - `ssl.truststore.location = null` in broker logs

3. **Root Cause:**
   - Broker tries to validate SSL configuration by creating test SSL connections
   - Truststore location is null, so certificate validation fails
   - Kafka refuses to start because it can't establish secure inter-broker communication

**The Fix:**
```yaml
KAFKA_SSL_TRUSTSTORE_LOCATION: /etc/kafka/secrets/kafka.truststore.jks
KAFKA_SSL_TRUSTSTORE_PASSWORD: password
```

**Why This Happens:**
- Confluent Platform auto-resolves keystore paths from `KAFKA_SSL_KEYSTORE_FILENAME`
- Truststore paths are NOT always auto-resolved, especially for inter-broker SSL
- Explicit truststore configuration is required when using SSL for broker-to-broker communication

---

## SSL Communication Flow

```text
Producer --SSL--> Broker --SSL--> Broker --SSL--> Consumer
```

**Explanation:**
- Producer encrypts messages using SSL before sending to broker
- Broker-to-broker replication uses SSL (when `KAFKA_INTER_BROKER_LISTENER_NAME=SSL`)
- Consumer receives encrypted data from broker over SSL
- All communication is encrypted end-to-end when SSL is properly configured
