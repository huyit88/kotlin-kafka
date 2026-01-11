### Dependencies

* *(No new dependencies — this challenge is **audit + verification focused**)*

---

## Problem A

### Requirement

Create a **production-grade Kafka Security Checklist** file.

Create a file named:

```
KAFKA_SECURITY_CHECKLIST.md
```

It must contain **explicit YES / NO / N/A answers** for the following sections:

#### A. Network & Listeners

* PLAINTEXT listener disabled in prod
* Only required ports exposed
* Correct `advertised.listeners`

#### B. TLS / SSL

* Client ↔ Broker encryption
* Broker ↔ Broker encryption
* Certificate SAN / hostname validation
* Certificate rotation plan

#### C. Authentication (SASL / mTLS)

* One principal per service
* No shared credentials
* Secrets not committed to git
* Super-users explicitly listed

#### D. Authorization (ACLs)

* Authorizer enabled
* Default deny enabled
* Topic ACLs scoped per service
* Consumer group ACLs present
* TransactionalId ACLs (if EOS)

#### E. Operational Hygiene

* Auth failures logged
* Authorization failures logged
* Periodic ACL review process

Each checklist item must include **one line of evidence**, for example:

* config key
* CLI command
* observed behavior

---

### Acceptance criteria

* File exists and is named exactly `KAFKA_SECURITY_CHECKLIST.md`
* Every checklist item has:

  * YES / NO / N/A
  * at least one concrete evidence line
* No vague answers like “probably” or “should be”

---

### Command to verify/run

```bash
cat KAFKA_SECURITY_CHECKLIST.md
```

---

## Problem B

### Requirement

Perform **negative security tests** (must-fail scenarios).

Create a file:

```
SECURITY_NEGATIVE_TESTS.md
```

Document **at least 5** negative tests that must fail in a secure cluster.

Examples (you must actually try them):

1. Produce without SSL
2. Produce without SASL
3. Analytics user writes to `payments`
4. Analytics user reads `payments`
5. Consumer joins group without group ACL

For each test:

* Command executed
* Expected failure
* Actual error message
* Why this failure is important

---

### Acceptance criteria

* ≥ 5 negative tests
* Errors are Kafka authorization/authentication errors
* Each test explains the security risk it prevents

---

### Command to verify/run

```bash
cat SECURITY_NEGATIVE_TESTS.md
```

---

## Problem C

### Requirement

Audit **principals and privileges** (least privilege review).

Create:

```
PRINCIPAL_ACCESS_MATRIX.md
```

Table format:

| Principal      | Topic    | Operation | Group | Allowed? | Why               |
| -------------- | -------- | --------- | ----- | -------- | ----------------- |
| User:fraud     | payments | WRITE     | –     | YES      | Produces payments |
| User:analytics | payments | READ      | –     | NO       | PII protection    |

Must include:

* `admin`
* `fraud`
* `analytics`

---

### Acceptance criteria

* All principals accounted for
* No principal has wildcard access
* Justifications align with business role

---

### Command to verify/run

```bash
cat PRINCIPAL_ACCESS_MATRIX.md
```

---

## Problem D

### Requirement

Document **Kafka Security Best-Practice Anti-Patterns**.

Create:

```
KAFKA_SECURITY_ANTIPATTERNS.md
```

Include at least **5 real-world anti-patterns**, for example:

* Shared service credentials
* Leaving PLAINTEXT listener enabled
* Missing group ACLs
* Overusing super-users
* No secret rotation

For each:

* What it looks like
* Why teams do it
* Why it’s dangerous
* How to fix it

---

### Acceptance criteria

* ≥ 5 anti-patterns
* Fixes are concrete (config / process)
* Clearly shows understanding of Kafka internals

---

### Command to verify/run

```bash
cat KAFKA_SECURITY_ANTIPATTERNS.md
```

---

## Problem E

### Requirement

Prepare **interview-grade Kafka security answers**.

Create:

```
KAFKA_SECURITY_BEST_PRACTICES_INTERVIEW.md
```

Answer concisely (≤ 5 lines each):

1. How do you secure Kafka in production?
2. Why is SSL enabled before SASL?
3. Why are group ACLs required for consumers?
4. How do you secure EOS pipelines?
5. What’s the most common Kafka security mistake?

---

### Acceptance criteria

* Clear, precise, no buzzwords
* Mentions SSL → SASL → ACLs ordering
* Correct use of Kafka terminology

---

### Command to verify/run

```bash
cat KAFKA_SECURITY_BEST_PRACTICES_INTERVIEW.md
```

---

## ✅ What You’ll Achieve After This Challenge

* You can **audit Kafka security like a senior engineer**
* You can explain *why* each layer exists
* You can confidently answer Kafka security interview questions
* You avoid the most common production security mistakes
