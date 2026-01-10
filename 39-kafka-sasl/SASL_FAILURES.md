# SASL Authentication Failure Scenarios

This document demonstrates authentication failure cases when using SASL/PLAIN authentication with Kafka.

## 1. Wrong Password

### Command Run

Modify `client-sasl.properties` to use an invalid password (e.g., change `password="fraud-secret"` to `password="invalid"`), then run:

```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --from-beginning \
  --consumer.config client-sasl.properties
```

### Error Message

```
[2026-01-10 15:14:54,070] ERROR [Consumer clientId=console-consumer, groupId=console-consumer-58444] Connection to node -1 (localhost/127.0.0.1:9093) failed authentication due to: Authentication failed: Invalid username or password (org.apache.kafka.clients.NetworkClient)
[2026-01-10 15:14:54,071] WARN [Consumer clientId=console-consumer, groupId=console-consumer-58444] Bootstrap broker localhost:9093 (id: -1 rack: null) disconnected (org.apache.kafka.clients.NetworkClient)
[2026-01-10 15:14:54,073] ERROR Error processing message, terminating consumer process:  (kafka.tools.ConsoleConsumer$)
org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed: Invalid username or password
Processed a total of 0 messages
```

### Why This Failure is Important for Security

**Password validation is critical for security because:**

1. **Prevents Unauthorized Access**: Even if an attacker knows a valid username, they cannot access Kafka without the correct password. This protects against credential theft scenarios where usernames might be exposed.

2. **Brute Force Protection**: The immediate rejection of invalid passwords (without revealing whether the username exists) helps prevent brute force attacks. Attackers cannot easily determine which usernames are valid.

3. **Credential Compromise Mitigation**: If a password is leaked or stolen, changing it immediately invalidates all existing connections using that password, forcing re-authentication with the new credentials.

4. **Principle of Least Privilege**: Each service/user should have unique credentials. Password validation ensures that compromised credentials for one service cannot be used to access another service's data.

---

## 2. Unknown Username

### Command Run

Modify `client-sasl.properties` to use a non-existent username (e.g., change `username="fraud"` to `username="no-exist"`), then run:

```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments \
  --from-beginning \
  --consumer.config client-sasl.properties
```

### Error Message

```
[2026-01-10 15:12:13,393] ERROR [Consumer clientId=console-consumer, groupId=console-consumer-84091] Connection to node -1 (localhost/127.0.0.1:9093) failed authentication due to: Authentication failed: Invalid username or password (org.apache.kafka.clients.NetworkClient)
[2026-01-10 15:12:13,394] WARN [Consumer clientId=console-consumer, groupId=console-consumer-84091] Bootstrap broker localhost:9093 (id: -1 rack: null) disconnected (org.apache.kafka.clients.NetworkClient)
[2026-01-10 15:12:13,395] ERROR Error processing message, terminating consumer process:  (kafka.tools.ConsoleConsumer$)
org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed: Invalid username or password
Processed a total of 0 messages
```

### Why This Failure is Important for Security

**Username validation is critical for security because:**

1. **Prevents User Enumeration**: Notice that Kafka returns the same generic error message ("Invalid username or password") for both wrong password and unknown username. This prevents attackers from discovering which usernames exist in the system, making enumeration attacks much harder.

2. **Access Control Enforcement**: Only pre-configured users in the JAAS file can authenticate. This ensures that only authorized services/users can connect to Kafka, enforcing strict access control at the authentication layer.

3. **Service Isolation**: Each service should have its own dedicated username (e.g., `fraud`, `analytics`, `admin`). Rejecting unknown usernames ensures that services cannot impersonate each other, maintaining clear audit trails and access boundaries.

4. **Defense in Depth**: Authentication failures at the username level provide the first line of defense. Even if an attacker bypasses network security, they still cannot access Kafka without valid credentials, adding multiple layers of protection.

---

## Summary

Both authentication failure scenarios demonstrate that Kafka properly enforces authentication before allowing any client operations. The generic error message ("Invalid username or password") is a security best practice that prevents information leakage about which usernames exist in the system, making it harder for attackers to enumerate valid accounts.