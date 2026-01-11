# Principal Access Matrix

**Least Privilege Audit** - Complete access control review for all principals in the secure Kafka cluster.

## Access Matrix

| Principal      | Resource Type | Resource Name        | Operation | Allowed? | Why                                    |
| -------------- | ------------ | -------------------- | --------- | -------- | -------------------------------------- |
| User:admin     | CLUSTER      | kafka-cluster        | ALL       | YES      | Super-user for cluster administration  |
| User:admin     | TOPIC        | *                    | ALL       | YES      | Super-user can manage all topics       |
| User:admin     | GROUP        | *                    | ALL       | YES      | Super-user can manage all groups       |
| User:admin     | TRANSACTIONAL_ID | *              | ALL       | YES      | Super-user can manage all transactions |
| User:fraud     | TOPIC        | payments             | READ      | YES      | Consumes raw payments for fraud detection |
| User:fraud     | TOPIC        | payments             | WRITE     | YES      | Produces payment events to processing pipeline |
| User:fraud     | TOPIC        | payments-validated   | WRITE     | YES      | Writes validated payments after fraud check |
| User:fraud     | TOPIC        | payments-validated   | READ      | NO       | No need to read validated (only writes) |
| User:fraud     | GROUP        | fraud-detector       | READ      | YES      | Required for consumer group coordination and offset commits |
| User:fraud     | TRANSACTIONAL_ID | fraud-pipeline-tx-* | WRITE     | YES      | Required for EOS transactional producer |
| User:analytics | TOPIC        | payments             | READ      | NO       | PII protection - raw payments contain sensitive data |
| User:analytics | TOPIC        | payments             | WRITE     | NO       | Analytics should not write to payment streams |
| User:analytics | TOPIC        | payments-validated   | READ      | YES      | Reads sanitized/validated payment data for analytics |
| User:analytics | TOPIC        | payments-validated   | WRITE     | NO       | Analytics is read-only service |
| User:analytics | GROUP        | analytics-pipeline   | READ      | YES      | Required for consumer group coordination and offset commits |
| User:analytics | GROUP        | fraud-detector       | READ      | NO       | Cannot access fraud service's consumer group |
| User:analytics | TRANSACTIONAL_ID | *                | WRITE     | NO       | Analytics does not use transactions |

## Summary by Principal

### User:admin (Super-User)
- **Purpose**: Cluster administration and emergency access
- **Permissions**: Wildcard access to all resources (CLUSTER, TOPIC, GROUP, TRANSACTIONAL_ID)
- **Justification**: Super-user bypasses ACL checks for operational tasks (topic creation, ACL management, cluster configuration)
- **Evidence**: `KAFKA_SUPER_USERS: "User:admin"` in broker config
- **Risk**: High privilege - use only for administrative operations, not application access

### User:fraud
- **Purpose**: Payment processing and fraud detection service
- **Topic Permissions**:
  - ✅ READ + WRITE on `payments` (processes raw payment events)
  - ✅ WRITE on `payments-validated` (writes validated payments)
  - ❌ READ on `payments-validated` (no need to read back validated data)
- **Group Permissions**:
  - ✅ READ on `fraud-detector` (required for consumer group operations)
- **TransactionalId Permissions**:
  - ✅ WRITE on `fraud-pipeline-tx-*` (for EOS transactional producer)
- **Justification**: Fraud service needs to read raw payments, detect fraud, and write validated results. Requires transactional guarantees for exactly-once processing.

### User:analytics
- **Purpose**: Analytics and reporting service (read-only)
- **Topic Permissions**:
  - ❌ READ on `payments` (PII protection - raw payments contain sensitive data)
  - ✅ READ on `payments-validated` (reads sanitized data for analytics)
  - ❌ WRITE on any topic (analytics is read-only)
- **Group Permissions**:
  - ✅ READ on `analytics-pipeline` (required for consumer group operations)
  - ❌ READ on other groups (cannot access other services' groups)
- **TransactionalId Permissions**:
  - ❌ WRITE on any TransactionalId (analytics does not use transactions)
- **Justification**: Analytics service only needs read access to validated (non-PII) payment data. No write permissions prevent accidental data corruption.

## Access Control Verification

### Verify ACLs
```bash
# List all ACLs
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --list

# Verify fraud permissions
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --list --principal User:fraud

# Verify analytics permissions
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --list --principal User:analytics
```

### Expected ACL Output
```
Current ACLs for resource `ResourcePattern(resourceType=TOPIC, name=payments, patternType=LITERAL)`:
  (principal=User:fraud, host=*, operation=READ, permissionType=ALLOW)
  (principal=User:fraud, host=*, operation=WRITE, permissionType=ALLOW)

Current ACLs for resource `ResourcePattern(resourceType=TOPIC, name=payments-validated, patternType=LITERAL)`:
  (principal=User:fraud, host=*, operation=WRITE, permissionType=ALLOW)
  (principal=User:analytics, host=*, operation=READ, permissionType=ALLOW)

Current ACLs for resource `ResourcePattern(resourceType=GROUP, name=fraud-detector, patternType=LITERAL)`:
  (principal=User:fraud, host=*, operation=READ, permissionType=ALLOW)

Current ACLs for resource `ResourcePattern(resourceType=GROUP, name=analytics-pipeline, patternType=LITERAL)`:
  (principal=User:analytics, host=*, operation=READ, permissionType=ALLOW)

Current ACLs for resource `ResourcePattern(resourceType=TRANSACTIONAL_ID, name=fraud-pipeline-tx-, patternType=PREFIXED)`:
  (principal=User:fraud, host=*, operation=WRITE, permissionType=ALLOW)
```

## Security Compliance Checklist

- ✅ **No wildcard principals**: All ACLs use specific principals (`User:fraud`, `User:analytics`), no `User:*`
- ✅ **No wildcard topics**: All topic ACLs are scoped to specific topics (`payments`, `payments-validated`), no `*` topic access
- ✅ **Least privilege**: Each principal has minimum required permissions
- ✅ **Group ACLs present**: All consumers have explicit group ACLs
- ✅ **TransactionalId ACLs**: EOS producers have TransactionalId ACLs
- ✅ **Read-only services**: Analytics has no write permissions
- ✅ **PII protection**: Analytics cannot access raw `payments` topic
- ✅ **Super-user documented**: Admin privileges explicitly listed and justified

## Notes

1. **Admin super-user**: Has implicit wildcard access due to `KAFKA_SUPER_USERS` configuration. This is acceptable for administrative operations but should not be used for application access.

2. **Group ACL requirement**: Even with topic READ permission, consumers require explicit group ACLs to join consumer groups and commit offsets. This is a common gotcha.

3. **TransactionalId ACLs**: Required for exactly-once semantics (EOS) producers. Without this ACL, transactional producers fail even with topic WRITE permission.

4. **Default deny**: With `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=false`, any operation not explicitly allowed is denied.
