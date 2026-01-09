# Security Baseline - Insecure Behaviors Demonstrated

This document demonstrates that the current Kafka setup has no security controls enabled.

---

## Finding 1: Anonymous Produce Allowed

**Command:**
```bash
docker exec -it kafka bash -lc '
  kafka-console-producer --bootstrap-server localhost:9092 --topic payments \
  --property parse.key=true --property key.separator=:
'
```

**Risk:**
- **Data Integrity Compromised:** Any service or attacker on the network can inject fake events into production topics
- **Business Logic Manipulation:** Malicious actors can send fraudulent payment events, order confirmations, or any business-critical messages
- **No Accountability:** Cannot trace who produced messages since no authentication is required
- **Example Attack:** Inject fake "payment successful" events to bypass payment processing

---

## Finding 2: Anonymous Consume Allowed

**Command:**
```bash
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic payments --group fraud-pipeline \
  --property print.key=true --property print.partition=true --from-beginning
```

**Risk:**
- **Data Breach:** Unauthorized access to sensitive data (PII, payment information, business secrets)
- **Privacy Violation:** Anyone can read messages from topics containing personal or confidential information
- **Competitive Intelligence:** Competitors or malicious actors can monitor business events and strategies
- **Compliance Violations:** Violates GDPR, HIPAA, PCI-DSS and other regulations requiring access controls
- **Example Attack:** Read customer payment data, personal information, or internal business events

---

## Finding 3: Topic Creation Without Authentication

**Command:**
```bash
docker exec -it kafka bash -lc '
  kafka-topics --bootstrap-server localhost:9092 --create \
    --topic payments-fast --partitions 3 --replication-factor 1
'
```

**Risk:**
- **Resource Exhaustion:** Attackers can create unlimited topics, consuming disk space and broker resources
- **Topic Deletion:** Malicious actors can delete critical topics, causing data loss and service disruption
- **Configuration Manipulation:** Unauthorized changes to partition counts, replication factors, or retention policies
- **Service Disruption:** Deleting system topics (e.g., `__consumer_offsets`) can break consumer groups
- **Example Attack:** Delete the `payments` topic or create thousands of topics to crash the cluster

---

## Finding 4: Read Messages from Restricted Topics

**Command:**
```bash
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic __consumer_offsets --group admin-tool \
  --property print.key=true --from-beginning
```

**Risk:**
- **System Topic Access:** Can read internal Kafka topics like `__consumer_offsets`, exposing consumer group metadata
- **No Isolation:** Development services can accidentally consume from production topics
- **Data Leakage:** Sensitive topics (e.g., `user-credentials`, `financial-transactions`) are accessible to anyone
- **Operational Secrets:** Internal system events and configurations are exposed
- **Example Attack:** Monitor consumer group offsets to understand system architecture and find vulnerabilities

---

## Summary

**Current State:** Kafka cluster is completely open with zero security controls.

**Critical Vulnerabilities:**
1. No encryption - traffic can be intercepted and read
2. No authentication - anyone can connect
3. No authorization - anyone can perform any operation

**Impact:** This configuration is acceptable only for local development. In production, this would be a critical security vulnerability allowing data breaches, service disruption, and compliance violations.