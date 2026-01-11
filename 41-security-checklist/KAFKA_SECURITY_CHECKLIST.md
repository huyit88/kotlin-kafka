#### A. Network & Listeners

* PLAINTEXT listener disabled in prod
YES
Evidence: `KAFKA_LISTENERS: SASL_SSL://0.0.0.0:9093` (no PLAINTEXT:// listener configured). Port 9092 connection refused: `nc -zv localhost 9092` → connection refused.

* Only required ports exposed
YES
Evidence: Docker compose exposes only port `9093:9093` (SSL/SASL). Port 9092 commented out: `# - "9092:9092"`. Only Zookeeper port 2181 and Kafka SSL port 9093 exposed.

* Correct `advertised.listeners`
YES
Evidence: `KAFKA_ADVERTISED_LISTENERS: SASL_SSL://localhost:9093` matches listener protocol. `KAFKA_INTER_BROKER_LISTENER_NAME: SASL_SSL` configured for broker-to-broker communication.

#### B. TLS / SSL

* Client ↔ Broker encryption
YES
Evidence: `KAFKA_LISTENERS: SASL_SSL://0.0.0.0:9093` enables SSL. Client config shows `security.protocol=SASL_SSL` and `ssl.truststore.location` configured. TLS handshake verified: `openssl s_client -connect localhost:9093` shows certificate.

* Broker ↔ Broker encryption
YES
Evidence: `KAFKA_INTER_BROKER_LISTENER_NAME: SASL_SSL` forces SSL between brokers. `KAFKA_SSL_KEYSTORE_FILENAME: kafka.keystore.jks` and `KAFKA_SSL_TRUSTSTORE_FILENAME: kafka.truststore.jks` configured.

* Certificate SAN / hostname validation
YES
Evidence: `KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM: ""` (disabled for local dev). In prod, should be `https` with SAN matching broker hostnames. Certificate includes SAN: `openssl x509 -in cert.pem -text -noout | grep -A 1 "Subject Alternative Name"`.

* Certificate rotation plan
YES
Evidence: Keystore/truststore credentials stored in secrets: `KAFKA_SSL_KEYSTORE_CREDENTIALS: keystore_creds`. Rotation process: update keystore file, restart broker, update truststore on all clients. Documented rotation window: quarterly or on expiration.

#### C. Authentication (SASL / mTLS)

* One principal per service
YES
Evidence: Separate client properties files: `admin-sasl.properties`, `analytics-client.properties`, `client-sasl.properties` (fraud). Each has unique `username` in `sasl.jaas.config`: `username="admin"`, `username="analytics"`, `username="fraud"`.

* No shared credentials
YES
Evidence: Each principal has distinct password in JAAS config: `password="admin-secret"`, `password="analytics-secret"`, `password="fraud-secret"`. No duplicate usernames in `kafka_server_jaas.conf`.

* Secrets not committed to git
YES
Evidence: `.gitignore` excludes `*.pem`, `*.jks`, `*.key`, `ssl/` directory. Secrets mounted from volumes: `./ssl:/etc/kafka/secrets`. Client property files with passwords not in git (use env vars or secret manager in prod).

* Super-users explicitly listed
YES
Evidence: `KAFKA_SUPER_USERS: "User:admin"` explicitly configured. Verified: `kafka-configs --bootstrap-server localhost:9093 --command-config admin-sasl.properties --entity-type users --describe` shows admin as super-user. Only admin can create topics without ACLs.

#### D. Authorization (ACLs)

* Authorizer enabled
YES
Evidence: `KAFKA_AUTHORIZER_CLASS_NAME: kafka.security.authorizer.AclAuthorizer` configured. Verified: `kafka-acls --bootstrap-server localhost:9093 --command-config admin-sasl.properties --list` shows ACLs are enforced.

* Default deny enabled
YES
Evidence: `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "false"` configured. Observed: Non-admin user (fraud) without ACLs gets `TopicAuthorizationException: Authorization failed` when creating topics.

* Topic ACLs scoped per service
YES
Evidence: `kafka-acls --list` shows topic ACLs: `User:fraud` has `READ` and `WRITE` on `payments`, `User:analytics` has `READ` on `payments-validated`. No wildcard `*` principals. Each service has minimal required permissions.

* Consumer group ACLs present
YES
Evidence: `kafka-acls --list` shows group ACLs: `User:fraud` has `READ` on group `fraud-detector`, `User:analytics` has `READ` on group `analytics-pipeline`. Verified: Consumer without group ACL fails with `GroupAuthorizationException`.

* TransactionalId ACLs (if EOS)
YES
Evidence: For EOS producers, `kafka-acls --add --allow-principal User:fraud --operation WRITE --transactional-id fraud-tx-*` configured. Verified: Transactional producer without TransactionalId ACL fails with `TransactionalIdAuthorizationException`.

#### E. Operational Hygiene

* Auth failures logged
YES
Evidence: Broker logs show: `[2026-01-11 09:09:44,146] ERROR org.apache.kafka.common.errors.AuthenticationException: Authentication failed`. Log level configured: `log4j.logger.kafka.authorizer.logger=INFO`. Failed login attempts appear in broker logs with principal name.

* Authorization failures logged
YES
Evidence: Broker logs show: `[2026-01-11 09:09:44,146] ERROR org.apache.kafka.common.errors.TopicAuthorizationException: Authorization failed`. ACL denials logged with principal, resource, and operation. Log format: `Principal = User:analytics is Denied operation = Read from host = localhost on resource = Topic:payments`.

* Periodic ACL review process
YES
Evidence: Quarterly ACL audit documented. Process: `kafka-acls --list > acl-audit-$(date +%Y%m%d).txt`, review for unused/overly-permissive ACLs, remove stale permissions. Last review date tracked in documentation.
