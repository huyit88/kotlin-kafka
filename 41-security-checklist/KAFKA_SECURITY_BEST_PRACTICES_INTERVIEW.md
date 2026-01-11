# Kafka Security Best Practices - Interview Answers

## 1. How do you secure Kafka in production?

**Better Answer:**

Secure Kafka in production using a layered approach: **SSL → SASL → ACLs**. First, disable PLAINTEXT listeners and enable SSL/TLS for encryption in transit. Second, enable SASL authentication (PLAIN, SCRAM, or mTLS) so only authenticated principals can connect. Third, enable ACL authorization with `KAFKA_AUTHORIZER_CLASS_NAME` and `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=false` to enforce least-privilege access. Additionally, configure proper `advertised.listeners`, use separate principals per service, and enable audit logging for authentication/authorization failures.

---

## 2. Why is SSL enabled before SASL?


**Better Answer:**

SSL must be enabled before SASL because **SASL credentials are sent over the network during authentication**. Without SSL encryption, usernames and passwords travel in plaintext and can be intercepted by attackers on the same network segment. Enabling SSL first ensures the authentication handshake (including credential exchange) is encrypted, protecting credentials even if network traffic is captured. This follows the defense-in-depth principle: encrypt first, then authenticate over the encrypted channel.

---

## 3. Why are group ACLs required for consumers?

**Better Answer:**

Group ACLs are required because consumers must **read and write to the `__consumer_offsets` internal topic** to join consumer groups and commit offsets. Even with topic READ permission, Kafka denies group operations without explicit group ACL because offset management requires write access to `__consumer_offsets`. This is a common gotcha: topic ACLs alone are insufficient for consumers. Group ACLs also prevent unauthorized services from joining production consumer groups, which could cause rebalancing and message duplication.

---

## 4. How do you secure EOS pipelines?
**Better Answer:**

Secure EOS pipelines by granting **TransactionalId ACLs** in addition to topic WRITE permissions. Transactional producers require WRITE permission on their `transactional.id` resource (e.g., `fraud-pipeline-tx-*`) because they must register with the transaction coordinator and write transaction markers to the `__transaction_state` internal topic. Without TransactionalId ACLs, producers fail with `TransactionalIdAuthorizationException` even if they have topic WRITE permission. This ensures transaction state integrity and prevents unauthorized services from interfering with ongoing transactions.

---

## 5. What's the most common Kafka security mistake?
**Better Answer:**

The most common mistakes are: 
(1) **Enabling SASL without SSL** - credentials sent in plaintext, 
(2) **Setting `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=true`** - allows access when ACLs are missing instead of default deny, 
(3) **Leaving PLAINTEXT listener enabled in production** - exposes unencrypted data, 
(4) **Forgetting consumer group ACLs** - consumers fail even with topic READ permission 
(5) **Sharing principals across services** - credential compromise affects multiple services and makes auditing impossible. The most critical is #2 (default allow) because it silently grants access when ACLs are misconfigured.
