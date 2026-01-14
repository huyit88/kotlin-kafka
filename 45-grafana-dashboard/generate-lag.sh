#!/bin/bash
# Complete test script to generate consumer lag for Grafana dashboard

cd "$(dirname "$0")"

echo "🚀 Generating consumer lag data..."
echo ""

echo "1. Creating topic 'lag-test'..."
docker exec -it kafka-45 bash -lc '
  kafka-topics --bootstrap-server localhost:9092 \
    --create --topic lag-test --partitions 3 --replication-factor 1 2>/dev/null || true
'

echo "2. Producing 50 messages..."
for i in {1..50}; do
  echo "msg-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test" 2>/dev/null
done

echo "3. Starting consumer (will consume some messages, then stop)..."
docker exec -it kafka-45 bash -lc '
  timeout 3 kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic lag-test \
    --group lag-test-group \
    --from-beginning 2>/dev/null || true
'

echo "4. Producing more messages (creates lag since consumer stopped)..."
for i in {51..100}; do
  echo "msg-$i" | \
  docker exec -i kafka-45 bash -lc \
    "kafka-console-producer --bootstrap-server localhost:9092 --topic lag-test" 2>/dev/null
  sleep 0.05
done

echo "5. Checking lag using Kafka CLI..."
docker exec -it kafka-45 bash -lc '
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --describe --group lag-test-group
'

echo ""
echo "6. Waiting for kafka-exporter to poll (5 seconds)..."
sleep 5

echo "7. Checking metrics in kafka-exporter..."
LAG_METRICS=$(curl -s http://localhost:9308/metrics 2>/dev/null | grep "kafka_consumergroup_lag.*lag-test-group" | head -3)
if [ -z "$LAG_METRICS" ]; then
  echo "⚠️  No lag metrics found yet. Try:"
  echo "   - Wait 30 seconds (kafka-exporter polls every ~30s)"
  echo "   - Check: curl http://localhost:9308/metrics | grep kafka_consumergroup_lag"
else
  echo "$LAG_METRICS"
fi

echo ""
echo "8. Checking Prometheus query..."
PROM_RESULT=$(curl -s 'http://localhost:9090/api/v1/query?query=kafka_consumergroup_lag' 2>/dev/null | jq -r '.data.result | length' 2>/dev/null)
if [ "$PROM_RESULT" = "0" ] || [ -z "$PROM_RESULT" ]; then
  echo "⚠️  No data in Prometheus yet. Metrics may take 15-30 seconds to appear."
else
  echo "✅ Found $PROM_RESULT metric series in Prometheus!"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Done! Next steps:"
echo ""
echo "1. Open Grafana: http://localhost:3000"
echo "2. Go to your Consumer Lag Dashboard"
echo "3. Use query: kafka_consumergroup_lag{consumergroup=\"lag-test-group\"}"
echo ""
echo "If you see 'No data':"
echo "  - Wait 15-30 seconds (kafka-exporter polling interval)"
echo "  - Verify: curl http://localhost:9308/metrics | grep kafka_consumergroup_lag"
echo "  - Check Prometheus targets: http://localhost:9090/targets"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

