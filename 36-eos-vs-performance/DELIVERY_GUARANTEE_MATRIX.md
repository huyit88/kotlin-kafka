# Delivery Guarantee Decision Matrix

This document outlines the delivery guarantees chosen for different use cases in a Kafka-based system, along with justifications for each choice.

---

## Payments

**Chosen Guarantee:** Exactly-Once Semantics (EOS) with transactional producer, `isolation.level=read_committed`, and atomic offset commits.

**Reason:** Financial correctness is critical. Duplicate payments or lost payments are unacceptable. EOS ensures each payment is processed exactly once, preventing both duplicates and data loss.

**Why Other Guarantees Were Rejected:** At-least-once would allow duplicate payments (customer charged twice). At-most-once would allow lost payments (customer not charged). Idempotent-only prevents producer duplicates but doesn't guarantee atomicity across partitions or with offset commits.

---

## Inventory

**Chosen Guarantee:** Exactly-Once Semantics (EOS) with transactional producer, `isolation.level=read_committed`, and atomic offset commits.

**Reason:** Stock levels must be accurate. Duplicate inventory deductions (overselling) or lost deductions (underselling) cause business problems. EOS ensures stock updates are atomic and correct.

**Why Other Guarantees Were Rejected:** At-least-once could cause duplicate deductions leading to overselling (sell 100 units when only 50 available). At-most-once could cause lost deductions leading to underselling (show 50 available when 0 available). Idempotent-only doesn't guarantee atomicity when updating multiple partitions (e.g., inventory + audit log).

---

## Fraud

**Chosen Guarantee:** Exactly-Once Semantics (EOS) with transactional producer, `isolation.level=read_committed`, and atomic offset commits.

**Reason:** Fraud detection state must be consistent. Duplicate fraud flags (false positives) or missed fraud (false negatives) have serious consequences. EOS ensures fraud decisions are processed exactly once with atomic state updates.

**Why Other Guarantees Were Rejected:** At-least-once could cause duplicate processing, flagging legitimate transactions as fraud multiple times. At-most-once could cause lost processing, missing actual fraud. Idempotent-only doesn't guarantee atomicity when updating fraud state across multiple topics/partitions.

---

## Orders

**Chosen Guarantee:** At-Least-Once with idempotent producer (`enable.idempotence=true`), no transactions, and consumer commits after processing.

**Reason:** Orders can tolerate duplicates if handled idempotently (business key deduplication). Performance is important for order processing throughput. Idempotent producer prevents producer-side duplicates, while application-level deduplication handles consumer retries.

**Why Other Guarantees Were Rejected:** EOS transaction overhead would slow down order processing unnecessarily. Orders can be deduplicated by order ID at application level. At-most-once would allow lost orders (customer orders disappear). No idempotence would allow duplicate orders from producer retries.

---

## Notifications

**Chosen Guarantee:** At-Least-Once with standard producer (no idempotence needed) and consumer commits after processing.

**Reason:** Notifications are idempotent by nature (sending an email twice is acceptable). Performance and simplicity are prioritized. Duplicate notifications are better than missed notifications.

**Why Other Guarantees Were Rejected:** EOS transaction overhead is unnecessary when duplicates are harmless. At-most-once would allow lost notifications, which is worse than duplicates (user doesn't receive important alerts). Idempotent producer is not needed since notification systems typically deduplicate at application level (e.g., "already sent" checks).

---

## Metrics

**Chosen Guarantee:** At-Least-Once with standard producer and consumer commits after processing.

**Reason:** Metrics are aggregated data where occasional duplicates are acceptable (averaged out). High throughput is more important than perfect accuracy. Lost metrics are worse than duplicate metrics.

**Why Other Guarantees Were Rejected:** EOS transaction overhead would reduce metrics collection throughput significantly. Aggregation functions (sum, avg) naturally handle duplicates. At-most-once would allow lost metrics, which is worse than duplicates (missing data points in time series). Idempotent producer is not needed since metrics are typically aggregated and duplicates don't significantly impact results.

---

## Logs

**Chosen Guarantee:** At-Most-Once with standard producer, consumer commits before processing, and `acks=1` or `acks=0`.

**Reason:** Logs prioritize speed over accuracy. Lost log entries are acceptable (logs are typically sampled/aggregated). Duplicate logs add noise. High throughput is critical for log ingestion.

**Why Other Guarantees Were Rejected:** At-least-once would create duplicate logs, adding noise and wasting storage. Logs are typically aggregated, so duplicates don't add value. EOS transaction overhead would significantly slow down log ingestion. Logs are not critical data - losing some entries is acceptable. Idempotent producer is not needed - logs are fire-and-forget, duplicates are just noise.

---

## Key Takeaways

1. **EOS (Exactly-Once Semantics)**: Use for financial, inventory, and fraud use cases where correctness is critical and duplicates/losses have serious consequences.

2. **At-Least-Once + Idempotent Producer**: Use for orders and similar use cases where duplicates can be handled idempotently at application level, but performance matters.

3. **At-Least-Once**: Use for notifications and metrics where duplicates are acceptable or can be handled, but data loss is not.

4. **At-Most-Once**: Use for logs and similar use cases where speed is prioritized over accuracy and some data loss is acceptable.

## Performance vs. Correctness Trade-off

- **EOS**: Highest correctness, highest overhead (transactions, `acks=all`, `read_committed`)
- **At-Least-Once + Idempotent**: Good correctness, moderate overhead (idempotence, `acks=all`)
- **At-Least-Once**: Acceptable correctness, low overhead (standard producer, `acks=1` or `acks=all`)
- **At-Most-Once**: Lowest correctness, lowest overhead (standard producer, `acks=1` or `acks=0`)
