# Kafka SASL Fundamentals

## 1. Difference between SSL and SASL
**Answer:**

**SSL (Secure Sockets Layer / TLS):**
- **Encryption**: Protects data in transit from eavesdropping
- **Integrity**: Detects tampering/modification of messages
- **Server Authentication**: Verifies broker identity using certificates
- **Does NOT authenticate clients** (unless using mTLS with client certificates)

**SASL (Simple Authentication and Security Layer):**
- **Client Authentication**: Verifies who the client is (username/password)
- **Runs over SSL** in production (SASL_SSL) for secure credential transmission
- **Creates a principal** (authenticated identity) used for authorization

**Key Relationship:** SSL provides the secure channel, SASL provides client authentication. In production, you use **SASL_SSL** which combines both: SSL encrypts the connection, then SASL authenticates the client over that encrypted connection.

---

## 2. What a Kafka principal is

**Answer:**

A **Kafka principal** is the authenticated identity of a client after successful SASL authentication. It's the string representation of "who you are" that Kafka uses for authorization decisions.

**Format:** `User:<username>` (e.g., `User:fraud`, `User:analytics`)

**How it works:**
1. Client authenticates with SASL using username/password
2. Broker validates credentials against  (Java Authentication and Authorization Service file)
3. Broker creates principal: `User:<username>`
4. Principal is used to check ACLs (Access Control Lists) for authorization
5. ACLs define what operations the principal can perform (read, write, etc.)

**Example:** After `fraud` service authenticates, it becomes principal `User:fraud`, which ACLs can grant permission to write to `payments` topic but deny access to `analytics` topics.

---

## 3. Why SASL alone is not enough
**Answer:**

SASL only handles **authentication** (verifying identity), not **authorization** (what you're allowed to do). Without additional controls, all authenticated users have the same permissions.

**The Problem:**
- SASL authenticates: "Yes, you are `User:fraud`"
- But it doesn't answer: "Can `User:fraud` read topic `payments`?"
- Without ACLs, every authenticated user can access everything

**The Solution:**
You need **ACLs (Access Control Lists)** to define permissions:
- `User:fraud` can write to `payments` topic
- `User:analytics` can read from `payments` but not write
- `User:admin` can manage topics and consumer groups

**Flow:** Authentication (SASL) → Principal → Authorization (ACLs) → Access Granted/Denied

---

## 4. Why one username per service is critical

**Answer:**

**One username per service is critical for security and operational reasons:**

1. **Principle of Least Privilege:**
   - Each service gets only the permissions it needs
   - `fraud` service can write to `payments` but can't read `analytics` topics
   - Limits blast radius if credentials are compromised

2. **Service Isolation:**
   - Prevents one service from accessing another service's data
   - If `fraud` and `analytics` share credentials, either can read all messages
   - Separate credentials enforce data boundaries

3. **Audit Trail:**
   - Kafka logs show which principal performed each operation
   - With shared credentials, you can't tell which service actually did something
   - With unique credentials, logs show: `User:fraud wrote to payments` vs `User:analytics read from payments`

4. **Credential Rotation:**
   - If `fraud` credentials are compromised, you can rotate just that service
   - With shared credentials, rotating affects all services, causing downtime

**Example:** If `fraud` and `analytics` both use `shared-service` username, either can read sensitive payment data meant only for fraud detection.

---

## 5. When mTLS might replace SASL
**Answer:**

**mTLS (mutual TLS)** uses client certificates for authentication instead of username/password. It might replace SASL when:

1. **Certificate-Based Infrastructure:**
   - Organization already uses PKI (Public Key Infrastructure)
   - Services already have certificates for other systems
   - Easier to manage certificates than username/password per service

2. **Stronger Authentication Requirements:**
   - Certificates are harder to steal than passwords (stored in keystores)
   - Certificate expiration provides automatic credential rotation
   - No passwords to manage or leak

3. **Compliance Requirements:**
   - Some regulations require certificate-based authentication
   - Certificates provide stronger non-repudiation (proving who did what)

4. **Service Mesh Environments:**
   - Service meshes (Istio, Linkerd) often use mTLS by default
   - Consistent security model across all service-to-service communication

**Trade-offs:**
- **mTLS Pros:** Stronger security, no password management, automatic rotation
- **mTLS Cons:** More complex setup, certificate management overhead, harder to debug
- **SASL Pros:** Simpler setup, easier to understand, flexible (multiple mechanisms)
- **SASL Cons:** Password management, manual rotation, weaker if passwords leak

**When to use each:**
- **SASL/PLAIN:** Good for development, simple setups, when passwords are managed securely
- **mTLS:** Better for production, large organizations, service meshes, compliance-heavy environments

**Note:** You can also use both - mTLS for service-to-service, SASL for human/admin access.

---

## Authentication Flow

```text
Client --(SASL/PLAIN)--> Broker --principal--> ACLs --> Access Granted/Denied
         (username/pwd)      (User:fraud)      (permissions)
```

**Step-by-step:**
1. Client sends SASL credentials over SSL-encrypted connection
2. Broker validates credentials against JAAS file
3. Broker creates principal: `User:<username>`
4. Broker checks ACLs for that principal
5. ACLs determine if operation (read/write) is allowed
6. Access granted or denied based on ACL rules