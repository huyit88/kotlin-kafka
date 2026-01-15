# Kafka Alert Severity Model

## Overview

This document defines the severity classification, notification policies, and response time expectations for Kafka alerts. The severity model ensures that alerts are routed to the appropriate teams with clear expectations for response and resolution.

---

## Severity Levels

### `warning` - Degradation / Performance Issues

**Definition:**
- **Non-fatal issues** that indicate degradation or potential problems
- System is **still operational** but performance or functionality is impacted
- **Actionable** but not immediately blocking
- May escalate to critical if not addressed

**Characteristics:**
- System continues to function
- Users may experience slower performance
- No immediate data loss risk
- Can be investigated during business hours
- May self-resolve or require monitoring

**Examples:**
- Consumer lag increasing (but not yet critical)
- Broker CPU usage high
- Network latency increasing
- Disk space approaching limits (but not full)

**Response Time:** **30 minutes - 2 hours** (during business hours)

---

### `critical` - Fatal / Data Risk / Availability Issues

**Definition:**
- **Fatal issues** that pose immediate risk to data durability, availability, or cluster stability
- System may be **partially or fully unavailable**
- **Immediate action required** to prevent data loss or extended downtime
- Cannot wait for business hours

**Characteristics:**
- System may be unavailable or unstable
- Risk of data loss or corruption
- Cluster health compromised
- Requires immediate investigation and resolution
- May require escalation to on-call engineer

**Examples:**
- No active controller (cluster coordination broken)
- Under-replicated partitions (data durability risk)
- Broker failures (reduced redundancy)
- Disk full (writes blocked)
- Zookeeper quorum lost

**Response Time:** **5-15 minutes** (24/7, immediate response)

---

## Alert Classification

### Current Alerts

| Alert Name | Severity | Rationale |
|------------|----------|-----------|
| `KafkaConsumerLagIncreasing` | `warning` | Performance degradation - consumers falling behind, but system still operational. May indicate processing issues but doesn't immediately threaten data. |
| `KafkaUnderReplicatedPartitions` | `critical` | Data durability risk - if another broker fails, data may be lost. Immediate action required to restore replication. |
| `KafkaNoActiveController` | `critical` | Cluster coordination broken - no controller means no leader election, partition management, or broker failure handling. Cluster is effectively broken. |

---

## Notification Policy

### Who Gets Notified

#### `warning` Alerts

**Primary Recipients:**
- **Platform/DevOps Team** (Slack channel: `#kafka-alerts-warning`)
- **Application Team** (if consumer lag affects specific applications)
- **On-call Engineer** (during business hours only)

**Notification Channels:**
- **Slack:** `#kafka-alerts-warning` channel
- **Email:** Platform team distribution list
- **PagerDuty:** Low priority (does not page, only creates incident)

**Escalation:**
- If not acknowledged within 2 hours → Escalate to team lead
- If persists > 4 hours → Escalate to critical

#### `critical` Alerts

**Primary Recipients:**
- **On-call Engineer** (24/7, immediate page)
- **Platform/DevOps Team Lead** (if not acknowledged in 15 minutes)
- **Kafka Infrastructure Team** (if cluster-wide issue)

**Notification Channels:**
- **PagerDuty:** High priority (immediate page to on-call)
- **Slack:** `#kafka-alerts-critical` channel (urgent)
- **Phone/SMS:** On-call engineer (if PagerDuty not acknowledged in 5 minutes)

**Escalation:**
- If not acknowledged within 15 minutes → Escalate to team lead
- If not resolved within 1 hour → Escalate to management
- If cluster-wide → Escalate to CTO/VP Engineering

---

## Alert Routing & Paging

### Which Alerts Page Someone

**Alerts that trigger immediate paging (PagerDuty):**

1. **`KafkaNoActiveController`** (critical)
   - **Why:** Cluster coordination completely broken
   - **Pages:** On-call engineer immediately
   - **Escalation:** 15 minutes if not acknowledged

2. **`KafkaUnderReplicatedPartitions`** (critical)
   - **Why:** Data durability at risk, potential data loss
   - **Pages:** On-call engineer immediately
   - **Escalation:** 15 minutes if not acknowledged

**Alerts that do NOT page (Slack/Email only):**

1. **`KafkaConsumerLagIncreasing`** (warning)
   - **Why:** Performance issue, not immediate threat
   - **Notification:** Slack channel only
   - **Escalation:** If persists > 4 hours, convert to critical

### Paging Decision Matrix

| Severity | Cluster Impact | Data Risk | Pages? | Channel |
|----------|----------------|-----------|--------|---------|
| `critical` | High | Yes | ✅ Yes | PagerDuty + Slack |
| `critical` | Medium | Yes | ✅ Yes | PagerDuty + Slack |
| `warning` | Low | No | ❌ No | Slack only |
| `warning` | Medium | No | ❌ No | Slack only |

---

## Expected Response Times

### `warning` Alerts

**Acknowledgment Time:** **30 minutes**
- Alert should be acknowledged within 30 minutes
- Acknowledgment = Engineer reviews alert and starts investigation

**Response Time:** **2 hours**
- Initial response/action should occur within 2 hours
- Response = Taking action to investigate or resolve

**Resolution Time:** **4-8 hours** (business hours)
- Issue should be resolved or have a mitigation plan within 4-8 hours
- If not resolved, escalate to critical or create follow-up ticket

**Business Hours:** 9 AM - 6 PM (local timezone)
- Outside business hours: Response time extends to next business day
- Exception: If alert escalates to critical, 24/7 response applies

### `critical` Alerts

**Acknowledgment Time:** **5 minutes**
- Alert must be acknowledged within 5 minutes
- Acknowledgment = On-call engineer confirms they're investigating

**Response Time:** **15 minutes**
- Initial response/action must occur within 15 minutes
- Response = Taking immediate action (restarting broker, investigating root cause)

**Resolution Time:** **1 hour**
- Issue should be resolved or have mitigation in place within 1 hour
- If not resolved, escalate to team lead and management

**24/7 Coverage:**
- Critical alerts require 24/7 response
- On-call rotation ensures coverage at all times
- Escalation chain active 24/7

---

## Alert Lifecycle

### Alert States

1. **`inactive`** - Condition not met, alert not triggered
2. **`pending`** - Condition met, but duration requirement not satisfied
3. **`firing`** - Condition met for required duration, alert active

### State Transitions

**`inactive → pending`:**
- Alert condition becomes true
- Notification: None (waiting for duration)

**`pending → firing`:**
- Alert condition true for required duration
- **Notification sent** based on severity:
  - `critical`: Immediate page (PagerDuty)
  - `warning`: Slack notification

**`firing → inactive`:**
- Alert condition becomes false
- Alert resolves automatically
- Resolution notification sent (if configured)

---

## Alert Annotations

### Required Annotations

All alerts must include:

1. **`summary`** - Brief one-line description
   - Example: "Kafka consumer lag increasing"
   - Should be clear and actionable

2. **`description`** - Detailed explanation with context
   - Includes metric values, affected resources
   - Example: "Consumer group etl-consumer is falling behind. Lag increased by 25 messages in the last 5 minutes."
   - Should help engineer understand the issue quickly

### Optional Annotations

- **`runbook`** - Link to runbook for resolution steps
- **`dashboard`** - Link to Grafana dashboard for visualization
- **`team`** - Team responsible for this alert
- **`slack_channel`** - Slack channel for discussion

---

## Runbook Integration

### Runbook Links

Each alert should reference a runbook with resolution steps:

- **`KafkaConsumerLagIncreasing`** → `runbooks/kafka-consumer-lag.md`
- **`KafkaUnderReplicatedPartitions`** → `runbooks/kafka-under-replicated.md`
- **`KafkaNoActiveController`** → `runbooks/kafka-no-controller.md`

### Runbook Contents

Runbooks should include:
1. **Alert description** - What the alert means
2. **Root causes** - Common causes of this alert
3. **Diagnosis steps** - How to investigate
4. **Resolution steps** - How to fix
5. **Prevention** - How to prevent recurrence

---

## Escalation Policy

### Escalation Triggers

**Warning Alerts:**
- Not acknowledged in 2 hours → Escalate to team lead
- Not resolved in 8 hours → Escalate to critical or management

**Critical Alerts:**
- Not acknowledged in 15 minutes → Escalate to team lead
- Not resolved in 1 hour → Escalate to management
- Cluster-wide impact → Escalate to CTO/VP Engineering

### Escalation Chain

1. **Level 1:** On-call engineer (primary responder)
2. **Level 2:** Team lead / Senior engineer (if not acknowledged/resolved)
3. **Level 3:** Engineering management (if critical and unresolved)
4. **Level 4:** CTO/VP Engineering (if cluster-wide or extended outage)

---

## Alert Tuning & Review

### Regular Review Process

**Monthly Review:**
- Review alert frequency and false positive rate
- Adjust thresholds if needed
- Update runbooks based on incidents
- Review response times and escalation effectiveness

**Quarterly Review:**
- Evaluate alert effectiveness
- Add new alerts based on incidents
- Remove or consolidate redundant alerts
- Update severity classifications if needed

### Alert Metrics to Track

- **Alert frequency** - How often each alert fires
- **False positive rate** - Alerts that fire but aren't real issues
- **Mean time to acknowledge (MTTA)** - Average time to acknowledge
- **Mean time to resolve (MTTR)** - Average time to resolve
- **Alert fatigue** - Frequency of alerts causing fatigue

---

## Examples

### Example 1: Warning Alert

**Alert:** `KafkaConsumerLagIncreasing`

**Notification:**
- **Channel:** Slack `#kafka-alerts-warning`
- **Message:** "⚠️ Warning: Consumer lag increasing for group `etl-consumer`. Lag increased by 25 messages in 5 minutes."
- **Action:** Engineer reviews during business hours, investigates consumer performance

**Response:**
- Acknowledged: Within 30 minutes
- Action taken: Within 2 hours
- Resolution: Within 4-8 hours (or escalate if critical)

### Example 2: Critical Alert

**Alert:** `KafkaUnderReplicatedPartitions`

**Notification:**
- **Channel:** PagerDuty (immediate page) + Slack `#kafka-alerts-critical`
- **Message:** "🚨 Critical: 2 under-replicated partitions detected. Broker kafka-46-2 may be down. Data durability at risk."
- **Action:** On-call engineer immediately investigates and restarts broker

**Response:**
- Acknowledged: Within 5 minutes
- Action taken: Within 15 minutes (broker restart initiated)
- Resolution: Within 1 hour (replication restored)

---

## Summary

### Severity Model Summary

| Severity | Impact | Response Time | Notification | Pages? |
|----------|--------|---------------|--------------|--------|
| `warning` | Degradation | 30 min - 2 hours | Slack | ❌ No |
| `critical` | Fatal/Data Risk | 5-15 minutes | PagerDuty + Slack | ✅ Yes |

### Key Principles

1. **`warning`** = Performance issues, non-blocking, business hours response
2. **`critical`** = Data risk or availability issues, immediate 24/7 response
3. **Clear escalation** = Defined escalation chain and triggers
4. **Actionable alerts** = All alerts have clear runbooks and resolution steps
5. **Regular review** = Monthly/quarterly review to tune and improve

---

## References

- **Alert Configuration:** `kafka-alerts.yml`
- **Runbooks:** `runbooks/` directory
- **Grafana Dashboards:** http://localhost:3000/dashboards
- **Prometheus Alerts:** http://localhost:9090/alerts
- **On-call Schedule:** PagerDuty schedule "Kafka On-Call"
