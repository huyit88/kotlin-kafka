### Dependencies

* *(No new dependencies — this is a **concept + inspection** challenge)*

---

## Problem A

### Requirement

Create a **Kafka Security Inventory** for your current setup.

Produce a file named `KAFKA_SECURITY_INVENTORY.md` that answers **for your current local Kafka setup**:

1. Is traffic **encrypted in transit**?
2. Is **client authentication** enabled?
3. Is **authorization (ACLs)** enforced?
4. Who can:

   * produce to a topic?
   * consume from a topic?
   * create topics?

Use a simple checklist format:

```md
## Encryption (SSL)
- Enabled: YES / NO
- Evidence:

## Authentication (SASL)
- Enabled: YES / NO
- Mechanism: NONE / PLAIN / SCRAM / OAUTH / mTLS

## Authorization (ACLs)
- Enabled: YES / NO
- Evidence:
```

### Acceptance criteria

* File exists: `KAFKA_SECURITY_INVENTORY.md`
* Answers reflect your **actual running Kafka**, not “desired state”
* Each section has at least **one concrete observation**

### Command to verify/run

```bash
cat KAFKA_SECURITY_INVENTORY.md
```

---

## Problem B

### Requirement

Prove that **your Kafka is currently insecure** (baseline).

Demonstrate **at least two** of the following:

1. Produce to any topic **without credentials**
2. Consume from any topic **without credentials**
3. Create a new topic without authentication
4. Read messages from a topic you “shouldn’t” have access to

Document:

* Command used
* Why this is dangerous

Add to file: `SECURITY_BASELINE.md`

Example:

```md
### Finding 1: Anonymous Produce Allowed
Command:
kafka-console-producer ...

Risk:
Any service can inject fake events
```

### Acceptance criteria

* At least **2 concrete insecure behaviors**
* Clear explanation of risk

### Command to verify/run

```bash
cat SECURITY_BASELINE.md
```

---

## Problem C

### Requirement

Map **Kafka Security Concepts → Real Incidents**.

Create `SECURITY_THREATS.md` with **3 realistic scenarios**, for example:

1. Fake payment events injected
2. PII leaked to unauthorized consumer
3. Rogue service deleting consumer group offsets

For each scenario:

* What security layer prevents it?

  * SSL
  * SASL
  * ACLs
* What happens **if that layer is missing**?

### Acceptance criteria

* 3 scenarios
* Each mapped to a specific Kafka security layer
* Clear cause → effect explanation

### Command to verify/run

```bash
cat SECURITY_THREATS.md
```

---

## Problem D

### Requirement

Create a **Kafka Security Rollout Plan** (very interview-relevant).

Create `SECURITY_ROLLOUT_PLAN.md` answering:

1. Why SSL should be enabled **before** SASL
2. Why SASL should be enabled **before** ACLs
3. What breaks if ACLs are enabled too early
4. How you would roll this out in production **without downtime**

Include an ASCII rollout:

```text
PLAINTEXT
  ↓
SSL
  ↓
SASL
  ↓
ACLs
```

### Acceptance criteria

* Mentions rollout order explicitly
* Mentions rollback strategy
* Mentions validation after each step

### Command to verify/run

```bash
cat SECURITY_ROLLOUT_PLAN.md
```

---

## Problem E

### Requirement

Prepare **interview-grade answers** for Kafka Security fundamentals.

Create `KAFKA_SECURITY_INTERVIEW.md` answering **concisely**:

1. What are the 3 pillars of Kafka security?
2. Is SSL alone sufficient? Why not?
3. Difference between authentication and authorization
4. Why ACLs must include **consumer group permissions**
5. Most common Kafka security misconfiguration you’ve seen

Each answer:

* ≤ 4 lines
* No buzzwords
* Clear and precise

### Acceptance criteria

* All 5 questions answered
* Answers are technically correct
* No vague wording (“it depends” without explanation)

### Command to verify/run

```bash
cat KAFKA_SECURITY_INTERVIEW.md
```

---

## ✅ What You’ll Gain from This Challenge

* Clear security mental model
* Ability to **audit Kafka security**
* Strong interview answers
* Confidence before touching SSL/SASL configs

---

### Next topic

👉 **Day 38 – SSL → Enable Encryption in Kafka**

Say **“next”** when you’re ready 🚀
