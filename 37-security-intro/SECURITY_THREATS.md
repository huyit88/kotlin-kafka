# Security Threats - Real-World Scenarios

This document maps Kafka security concepts to realistic incident scenarios, demonstrating how each security layer prevents specific attacks.

---

## Scenario 1: Fake Payment Events Injected

### The Attack
An attacker on the same network (or compromised service) produces fraudulent payment confirmation events directly to the `payments-confirmed` topic, bypassing the actual payment processing service.

**Example:**
```bash
# Attacker injects fake payment
kafka-console-producer --bootstrap-server kafka:9092 --topic payments-confirmed
> {"orderId": "12345", "amount": 1000.00, "status": "PAID", "timestamp": "2024-01-15T10:00:00Z"}
```

### What Security Layer Prevents It?
**SASL (Authentication)** - Requires clients to authenticate before producing messages.

- With SASL enabled, only authenticated services (e.g., `payment-service`) can produce to `payments-confirmed`
- The attacker cannot produce messages without valid credentials
- Each message can be traced to an authenticated principal

### What Happens If That Layer Is Missing?
**Without SASL:**
- **Any client can produce** - No authentication required, anyone on the network can inject messages
- **Data integrity compromised** - Fraudulent payment confirmations are processed as legitimate
- **Business impact** - Orders marked as paid without actual payment, leading to financial losses
- **No accountability** - Cannot identify who produced the fake events
- **Attack vector** - Compromised development service, malicious insider, or network attacker

**Real-world impact:** E-commerce platform processes $50,000 in fake orders because attacker injected payment confirmations.

---

## Scenario 2: PII Leaked to Unauthorized Consumer

### The Attack
A data analytics service (intended only for aggregated metrics) consumes from the `user-events` topic, which contains personally identifiable information (PII) like email addresses, phone numbers, and purchase history.

**Example:**
```bash
# Unauthorized service consumes sensitive data
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic user-events --group analytics-service \
  --from-beginning
```

### What Security Layer Prevents It?
**ACLs (Authorization)** - Controls which authenticated users/services can perform specific operations on specific topics.

- With ACLs enabled, only authorized services (e.g., `recommendation-service`) can consume from `user-events`
- The `analytics-service` would be denied read access to PII topics
- Fine-grained permissions: read-only access to `metrics-aggregated` topic, but not `user-events`

### What Happens If That Layer Is Missing?
**Without ACLs:**
- **Any authenticated user can read any topic** - Authentication alone doesn't restrict access
- **Data breach** - PII exposed to services that shouldn't have access
- **Compliance violations** - GDPR, HIPAA, PCI-DSS violations due to unauthorized data access
- **Privacy violation** - Customer data leaked to unintended services
- **Attack vector** - Legitimate but overprivileged service, or compromised service account

**Real-world impact:** Analytics team accidentally processes customer PII, triggering GDPR violation and $2M fine.

---

## Scenario 3: Rogue Service Deleting Consumer Group Offsets

### The Attack
A compromised or misconfigured service deletes consumer group offsets, causing all consumers to reprocess messages from the beginning, leading to duplicate processing and system overload.

**Example:**
```bash
# Malicious service deletes offsets
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group payment-processor --delete-offsets --topic payments
```

### What Security Layer Prevents It?
**ACLs (Authorization)** - Restricts which services can modify consumer group offsets and administrative operations.

- With ACLs, only authorized admin services can delete offsets
- Regular application services have read/write permissions for their topics but cannot modify consumer groups
- Separate permissions for: `Read`, `Write`, `Delete`, `Alter`, `Describe`, `ClusterAction`

### What Happens If That Layer Is Missing?
**Without ACLs:**
- **Any authenticated service can delete offsets** - No distinction between application and admin services
- **Service disruption** - Consumers restart from beginning, causing duplicate processing
- **Data corruption** - Idempotent operations become non-idempotent when reprocessed
- **System overload** - Reprocessing millions of messages overwhelms downstream services
- **Attack vector** - Compromised application service, misconfigured deployment, or malicious insider

**Real-world impact:** Payment processor reprocesses 1M transactions, causing duplicate charges and system outage.

---

## Additional Scenario: Network Traffic Interception

### The Attack
An attacker on the same network (e.g., compromised WiFi, shared infrastructure) intercepts unencrypted Kafka traffic using packet sniffing tools, reading sensitive messages in transit.

**Example:**
```bash
# Attacker captures network traffic
tcpdump -i eth0 -w kafka-traffic.pcap port 9092
# Analyzes captured packets to extract message content
```

### What Security Layer Prevents It?
**SSL/TLS (Encryption)** - Encrypts all data in transit between clients and brokers.

- With SSL enabled, all messages are encrypted using TLS
- Even if traffic is intercepted, attackers see only encrypted ciphertext
- Requires proper certificate management and trust stores

### What Happens If That Layer Is Missing?
**Without SSL:**
- **Messages readable in plaintext** - Anyone with network access can read message content
- **Man-in-the-middle attacks** - Attackers can intercept and modify messages
- **Data exposure** - PII, credentials, business secrets visible in network traffic
- **Compliance violations** - Encryption requirements not met for sensitive data
- **Attack vector** - Compromised network segment, public WiFi, or insider with network access

**Real-world impact:** Attacker on shared cloud network intercepts customer credit card numbers from unencrypted Kafka traffic.

---

## Security Layer Summary

| Security Layer | Prevents | Without It |
|---------------|----------|------------|
| **SSL/TLS** | Network interception, man-in-the-middle attacks | Messages readable in plaintext on network |
| **SASL** | Unauthenticated access, fake message injection | Anyone can produce/consume without credentials |
| **ACLs** | Unauthorized operations, privilege escalation | Authenticated users can access any topic/operation |

**Key Insight:** All three layers work together:
- **SSL** protects data in transit
- **SASL** ensures only legitimate services connect
- **ACLs** ensure connected services can only access what they need