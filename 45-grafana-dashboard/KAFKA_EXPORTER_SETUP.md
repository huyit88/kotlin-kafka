# Kafka Exporter Setup - Consumer Lag Metrics

## Overview

You can use **both** `kafka-jmx-exporter` and `kafka-exporter` together. They serve different purposes:

- **kafka-jmx-exporter**: Exposes broker JMX metrics (server, network, log metrics)
- **kafka-exporter**: Exposes consumer lag metrics by querying Kafka Admin API

---

## Configuration Added

### 1. Docker Compose Service

Added `kafka-exporter` service to `docker-compose.yml`:

```yaml
kafka-exporter:
  image: danielqsj/kafka-exporter:latest
  depends_on: [kafka-45]
  ports:
    - "9308:9308"
  environment:
    - KAFKA_BROKERS=kafka-45:9092
    - LOG_LEVEL=info
  command:
    - '--kafka.server=kafka-45:9092'
    - '--web.listen-address=:9308'
```

### 2. Prometheus Configuration

Added scrape job in `prometheus.yml`:

```yaml
- job_name: 'kafka-exporter'
  static_configs:
    - targets: ['kafka-exporter:9308']
      labels:
        service: 'kafka'
        exporter: 'kafka-exporter'
```

---

## Metrics Provided by Kafka Exporter

### Consumer Lag Metrics

**Main metrics (available when consumer groups exist):**
```promql
# Consumer lag per partition
kafka_consumergroup_lag{consumergroup="<group>", topic="<topic>", partition="<partition>"}

# Consumer current offset
kafka_consumergroup_current_offset{consumergroup="<group>", topic="<topic>", partition="<partition>"}
```

**With labels:**
- `consumergroup` - Consumer group name
- `topic` - Topic name
- `partition` - Partition number

**Example queries:**
```promql
# Total lag per consumer group
sum(kafka_consumergroup_lag) by (consumergroup)

# Lag per topic/partition
kafka_consumergroup_lag

# Max partition lag
max(kafka_consumergroup_lag) by (consumergroup, topic)
```

### Topic Metrics (Always Available)

- `kafka_topic_partition_current_offset` - Current offset (log end offset) per topic/partition
- `kafka_topic_partition_in_sync_replica` - Number of in-sync replicas
- `kafka_topic_partition_leader` - Leader broker ID

**Note:** Consumer lag metrics (`kafka_consumergroup_lag`) only appear when you have **active consumer groups**. Without consumer groups, you'll only see topic metrics.

---

## How to Use

### 1. Start the Services

```bash
cd 45-grafana-dashboard
docker compose up -d
```

### 2. Verify Kafka Exporter is Running

```bash
# Check container status
docker compose ps kafka-exporter

# Check metrics endpoint
curl http://localhost:9308/metrics | grep kafka_consumergroup
```

### 3. Verify Prometheus is Scraping

1. Open Prometheus: `http://localhost:9090`
2. Go to **Status → Targets**
3. You should see:
   - `kafka-jmx-exporter` (UP)
   - `kafka-exporter` (UP)

### 4. Query Consumer Lag in Grafana

Now you can use the queries from `CONSUMER_LAG_DASHBOARD.md`:

```promql
# Total lag per consumer group
sum(kafka_consumergroup_lag) by (consumergroup)

# Max partition lag
max(kafka_consumergroup_lag) by (consumergroup, topic)

# Lag trend
kafka_consumergroup_lag
```

**Note:** These metrics will only appear after you create a consumer group and consume messages.

---

## Important Notes

### Prerequisites for Consumer Lag Metrics

**You need active consumer groups** for lag metrics to appear:

1. **Create a consumer group:**
   ```bash
   # Start a consumer (creates consumer group)
   docker exec -it kafka-45 bash -lc '
     kafka-console-consumer --bootstrap-server localhost:9092 \
       --topic metrics-test --group my-consumer-group
   '
   ```

2. **Produce some messages:**
   ```bash
   # In another terminal
   docker exec -it kafka-45 bash -lc '
     echo "test-message" | \
     kafka-console-producer --bootstrap-server localhost:9092 --topic metrics-test
   '
   ```

3. **Check metrics:**
   ```bash
   curl http://localhost:9308/metrics | grep kafka_consumergroup_lag
   ```

### No Consumer Groups = No Lag Metrics

If you don't have any active consumer groups, `kafka-exporter` won't expose lag metrics. The exporter queries Kafka's Admin API to discover consumer groups and their offsets.

---

## Both Exporters Working Together

### Metrics from kafka-jmx-exporter (Port 9404)
- `kafka_server_*` - Broker metrics
- `kafka_network_*` - Network metrics
- `kafka_log_*` - Log metrics
- `jvm_*` - JVM metrics

### Metrics from kafka-exporter (Port 9308)
- `kafka_consumergroup_lag` - Consumer lag per partition (requires active consumer groups)
- `kafka_consumergroup_current_offset` - Consumer offsets (requires active consumer groups)
- `kafka_topic_partition_current_offset` - Topic partition offsets (always available)
- `kafka_topic_partition_in_sync_replica` - In-sync replica count

### Combined Dashboard

You can now create dashboards that combine:
- **Broker health** (from JMX exporter)
- **Consumer lag** (from kafka-exporter)
- **Throughput** (from JMX exporter)

This gives you complete visibility into your Kafka cluster!

---

## Troubleshooting

### Kafka Exporter Shows No Metrics

1. **Check if consumer groups exist:**
   ```bash
   docker exec -it kafka-45 bash -lc '
     kafka-consumer-groups --bootstrap-server localhost:9092 --list
   '
   ```

2. **Check exporter logs:**
   ```bash
   docker compose logs kafka-exporter
   ```

3. **Verify connectivity:**
   ```bash
   # Test if exporter can reach Kafka
   docker compose exec kafka-exporter ping kafka-45
   ```

### Prometheus Not Scraping

1. Check Prometheus targets: `http://localhost:9090/targets`
2. Verify `kafka-exporter` target shows as UP
3. Check Prometheus logs: `docker compose logs prometheus`

---

## Summary

✅ **Both exporters can run simultaneously**
✅ **They expose different metrics** (broker vs consumer)
✅ **Prometheus scrapes both** (different jobs)
✅ **Grafana can query both** (unified view)

This setup gives you complete Kafka monitoring: broker health + consumer lag!

