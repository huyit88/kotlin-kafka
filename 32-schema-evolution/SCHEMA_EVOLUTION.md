* What changed when compatibility was `NONE`?
When compatibility was set to `NONE`, the Schema Registry stopped validating schema compatibility entirely. This means breaking changes (like changing `amount` from `double` to `string`) that would normally be rejected with HTTP 409 were allowed to register successfully. The registry essentially disabled all compatibility checks.

* Why this is dangerous in production?
Setting compatibility to `NONE` is extremely dangerous because:
- Consumers will fail to deserialize messages with incompatible schemas, potentially crashing services
- Data corruption can occur silently if type mismatches aren't caught immediately
- Production outages can cascade across multiple services that depend on the topic
- In financial systems, this can lead to incorrect transaction processing, audit trail issues, and regulatory compliance problems
- Rollback becomes difficult once incompatible schemas are registered and messages are produced

* Recommended compatibility strategy for fraud/payment pipelines?
Use **BACKWARD** compatibility for fraud/payment pipelines. This ensures:
- New consumers can read old data (critical for rolling out new services)
- Old consumers continue working when new optional fields are added
- Both old and new consumer instances can consume messages without breaking
- Safe schema evolution without requiring coordinated deployments
- Data integrity is maintained during schema migrations

BACKWARD is preferred over FORWARD or FULL for financial systems because it allows adding optional fields (like `merchantId`) without breaking existing consumers, which is the most common evolution pattern in payment systems.
