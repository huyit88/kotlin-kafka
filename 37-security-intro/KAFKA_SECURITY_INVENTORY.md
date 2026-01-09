# Kafka Security Inventory

## Encryption (SSL/TLS)
- **Enabled:** NO
- **Evidence:**
  - Docker Compose configuration shows `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT`
  - Listeners configured as `PLAINTEXT://0.0.0.0:9092` (no SSL port)
  - No SSL certificates or keystore configuration present
  - Traffic between clients and brokers is unencrypted

## Authentication (SASL)
- **Enabled:** NO
- **Mechanism:** NONE
- **Evidence:**
  - No SASL-related environment variables in Docker Compose (e.g., `KAFKA_SASL_ENABLED_MECHANISMS`, `KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL`)
  - Security protocol map only contains `PLAINTEXT:PLAINTEXT`
  - Clients can connect without providing any credentials
  - No authentication mechanism configured (not PLAIN, SCRAM, OAUTH, or mTLS)

## Authorization (ACLs)
- **Enabled:** NO
- **Evidence:**
  - No `KAFKA_AUTHORIZER_CLASS_NAME` environment variable set
  - No ACL configuration files or settings present
  - Any client can perform any operation (produce, consume, create topics, delete topics)
  - No access control restrictions enforced

## Access Control Summary

### Who can produce to a topic?
**Anyone** - No authentication or authorization required. Any client connecting to `localhost:19092` (or internal `kafka:9092`) can produce messages to any topic.

### Who can consume from a topic?
**Anyone** - No restrictions. Any client can subscribe to any consumer group and read from any topic, including topics they shouldn't have access to.

### Who can create topics?
**Anyone** - No authentication or authorization checks. Any client can create, delete, or modify topics using `kafka-topics` CLI or programmatically.

## Current Security Posture

**Status:** **INSECURE - No security layers enabled**

The current setup is suitable only for local development. In production, this configuration would allow:
- Unauthorized data access
- Message injection/manipulation
- Topic deletion/modification
- Network traffic interception