1. Remove (or do not add) the group ACL for `User:analytics` temporarily.
```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --remove --allow-principal User:analytics \
  --operation READ \
  --group analytics-pipeline
```
```output
Are you sure you want to remove ACLs: 
        (principal=User:analytics, host=*, operation=READ, permissionType=ALLOW) 
 from resource filter `ResourcePattern(resourceType=GROUP, name=analytics-pipeline, patternType=LITERAL)`? (y/n)
y
```
2. Attempt to consume `payments-validated` with group `analytics-pipeline`.
```bash
kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic payments-validated \
  --group analytics-pipeline \
  --from-beginning \
  --consumer.config /etc/kafka/client-sasl.properties
```
```output
[2026-01-11 09:21:55,782] ERROR Error processing message, terminating consumer process:  (kafka.tools.ConsoleConsumer$)
org.apache.kafka.common.errors.GroupAuthorizationException: Not authorized to access group: analytics-pipeline
Processed a total of 0 messages
```

3. Restore the the group ACL for `User:analytics`

```bash
kafka-acls --bootstrap-server localhost:9093 \
  --command-config admin-sasl.properties \
  --add --allow-principal User:analytics \
  --operation READ \
  --group analytics-pipeline

kafka-console-consumer --bootstrap-server localhost:9093 \
  --topic payments-validated --from-beginning \
  --group analytics-pipeline \
  --consumer.config /etc/kafka/analytics-client.properties
```

## Explanation

### What Operation Was Denied?

The error `GroupAuthorizationException: Not authorized to access group: analytics-pipeline` indicates that the consumer was denied access to the **consumer group** resource, not the topic itself.

Even though `User:analytics` has READ permission on the `payments-validated` topic, the consumer failed because it needs to:
1. **Join the consumer group** (`analytics-pipeline`)
2. **Read and write consumer group offsets** stored in the internal `__consumer_offsets` topic

### Why Group Permissions Are Needed for Offsets

When a consumer uses a consumer group, Kafka needs to:
- **Store offset information** in the `__consumer_offsets` topic
- **Manage group membership** and coordinate partition assignments
- **Read previous offsets** when the consumer starts or rebalances

These operations require **READ permission on the GROUP resource**, not just READ permission on the topic. The group ACL controls:
- Access to the consumer group metadata
- Permission to commit offsets to `__consumer_offsets`
- Permission to read committed offsets from `__consumer_offsets`

**Key Takeaway:** Topic READ permission alone is insufficient. Consumers using consumer groups must also have READ permission on the GROUP resource to manage offsets and group membership.