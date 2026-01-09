# Kafka Security Rollout Plan

## 1. Why SSL should be enabled **before** SASL

###  Answer

**SSL must be enabled before SASL because SASL credentials (usernames, passwords, tokens) are transmitted in plaintext over the network.**

If you enable SASL before SSL:
- **Credential exposure:** Authentication credentials are sent unencrypted and can be intercepted
- **Man-in-the-middle attacks:** Attackers can capture credentials and impersonate legitimate services
- **Defeats the purpose:** You're authenticating, but the authentication itself is insecure

**Correct order:** Enable SSL first to encrypt the channel, then enable SASL so credentials are protected during transmission.

---

## 2. Why SASL should be enabled **before** ACLs

### Answer 

**SASL must be enabled before ACLs because ACLs require authenticated principals (identities) to function.**

Technical dependency:
- **ACLs grant permissions to specific principals** (e.g., `User:payment-service`, `User:analytics-service`)
- Without SASL, all clients are anonymous (`ANONYMOUS` principal)
- You cannot create meaningful ACLs for anonymous users - you'd have to grant permissions to `ANONYMOUS`, which defeats the purpose

**If you enable ACLs before SASL:**
- All existing services appear as `ANONYMOUS`
- You cannot distinguish between legitimate services
- Either you grant `ANONYMOUS` full access (insecure) or deny all access (breaks everything)

**Correct order:** Enable SASL first to establish authenticated identities, then use ACLs to grant specific permissions to those identities.

---

## 3. What breaks if ACLs are enabled too early

###  Answer

**If ACLs are enabled too early (before SASL), all services will be denied access, causing immediate production outage.**

What breaks:
1. **Immediate service failure:** All producers and consumers are denied access by default (ACLs deny-by-default)
2. **No authenticated principals:** Without SASL, all services appear as `ANONYMOUS`, so you can't grant them permissions
3. **Message loss:** Consumers can't read, causing processing delays and potential data loss
4. **Producer failures:** Producers can't write, causing application errors and data pipeline breaks
5. **Topic operations blocked:** Services can't create topics, alter configurations, or manage consumer groups
6. **Cascading failures:** Downstream services fail because upstream services can't produce messages

**The fix:** Enable SASL first to authenticate services, then gradually add ACLs with proper permissions for each authenticated principal.

---

## 4. How you would roll this out in production **without downtime**

### Answer

**Zero-downtime rollout requires dual listeners, gradual migration, and careful validation at each step.**

### Phase 1: Enable SSL (Dual Listeners)

**Broker Configuration:**
```properties
# Keep existing PLAINTEXT listener for backward compatibility
KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092,SSL://kafka:9093
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,SSL:SSL
```

**Rollout Steps:**
1. Configure SSL listener alongside PLAINTEXT (dual listeners)
2. Deploy broker changes (existing clients continue on PLAINTEXT)
3. **Validate:** Test SSL connection with new clients, verify PLAINTEXT still works
4. Gradually migrate clients to SSL (one service at a time)
5. **Rollback:** If issues occur, clients can revert to PLAINTEXT listener

**Validation:**
- Monitor both listeners for traffic
- Verify SSL handshake succeeds
- Check for SSL-related errors in logs

---

### Phase 2: Enable SASL (Dual Protocol Support)

**Broker Configuration:**
```properties
# Support both PLAINTEXT and SASL_SSL
KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,SASL_SSL://0.0.0.0:9094
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,SASL_SSL:SASL_SSL
KAFKA_SASL_ENABLED_MECHANISMS=PLAIN,SCRAM-SHA-256
```

**Rollout Steps:**
1. Add SASL_SSL listener (keep PLAINTEXT for non-migrated services)
2. Create SASL users/credentials for services
3. **Validate:** Test SASL authentication works
4. Migrate services one-by-one to SASL_SSL
5. **Rollback:** Services can revert to PLAINTEXT if authentication fails

**Validation:**
- Verify services authenticate successfully
- Check for authentication failures in logs
- Monitor both listener traffic

---

### Phase 3: Enable ACLs (Gradual Permission Grant)

**Broker Configuration:**
```properties
KAFKA_AUTHORIZER_CLASS_NAME=kafka.security.authorizer.AclAuthorizer
KAFKA_SUPER_USERS=User:admin
```

**Rollout Steps:**
1. Enable ACL authorizer (but grant permissions BEFORE enabling)
2. **Critical:** Pre-create ACLs for all existing services:
   ```bash
   # Grant permissions to authenticated principals BEFORE enabling ACLs
   kafka-acls --authorizer-properties zookeeper.connect=zookeeper:2181 \
     --add --allow-principal User:payment-service \
     --operation Read --operation Write --topic payments
   ```
3. Enable ACL enforcement
4. **Validate:** Verify all services still work (they should, since ACLs are pre-granted)
5. **Rollback:** Disable authorizer if services are denied access

**Validation:**
- Monitor for ACL denial errors
- Verify all services continue operating
- Check consumer lag (shouldn't increase)
- Verify producers aren't failing

---

### Phase 4: Disable PLAINTEXT (Final Cleanup)

**Only after all services migrated:**
1. Remove PLAINTEXT listener from configuration
2. Restart brokers
3. **Validate:** All services using SSL/SASL, no PLAINTEXT traffic

---

## Rollout Strategy Summary

```text
PLAINTEXT (Current State)
  ↓
  [Add SSL listener, migrate clients gradually]
  ↓
SSL (Dual: PLAINTEXT + SSL)
  ↓
  [Add SASL_SSL listener, migrate clients gradually]
  ↓
SASL_SSL (Dual: PLAINTEXT + SASL_SSL)
  ↓
  [Pre-create ACLs, then enable ACL enforcement]
  ↓
SASL_SSL + ACLs (Full Security)
  ↓
  [Remove PLAINTEXT listener]
  ↓
SASL_SSL + ACLs (Production Secure)
```

## Key Principles for Zero-Downtime Rollout

1. **Dual Listeners:** Always support both old and new protocols during migration
2. **Gradual Migration:** Move services one-by-one, not all at once
3. **Pre-create Permissions:** For ACLs, grant permissions BEFORE enabling enforcement
4. **Validation at Each Step:** Test thoroughly before proceeding
5. **Rollback Plan:** Always have a way to revert if issues occur
6. **Monitor Closely:** Watch for errors, lag, and failures during migration

## Rollback Strategy

**If issues occur at any phase:**
- **SSL issues:** Revert clients to PLAINTEXT listener
- **SASL issues:** Revert clients to PLAINTEXT or SSL (non-SASL)
- **ACL issues:** Disable authorizer, services continue with authentication only
- **Broker config:** Revert to previous configuration and restart

## Validation Checklist

After each phase:
- [ ] All services operational
- [ ] No increase in consumer lag
- [ ] No producer failures
- [ ] No authentication/authorization errors in logs
- [ ] Traffic metrics normal
- [ ] Can rollback if needed