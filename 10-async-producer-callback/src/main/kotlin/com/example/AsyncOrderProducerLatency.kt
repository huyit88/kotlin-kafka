package com.example 

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

fun main() {
    val bootstrap = env("KAFKA_BOOTSTRAP", "localhost:19092")
    val topic     = env("TOPIC", "orders")
    val records   = 1000          // number of messages
    val linger    = 20
    val batchSize = 65536
    val compression = "lz4"           // e.g. lz4, snappy, zstd

    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)

        // Safe-by-default settings
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
        put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000")

        // Optional tunables (set via env vars)
        linger?.let { put(ProducerConfig.LINGER_MS_CONFIG, it.toString()) }
        batchSize?.let { put(ProducerConfig.BATCH_SIZE_CONFIG, it.toString()) }
        compression?.let { put(ProducerConfig.COMPRESSION_TYPE_CONFIG, it) }
    }

    println(
        "AsyncOrderProducerLatency: bootstrap=$bootstrap topic=$topic N=$records " +
        "lingerMs=${linger ?: "-"} batchSize=${batchSize ?: "-"} compression=${compression ?: "-"}"
    )

    KafkaProducer<String, String>(props).use { producer ->
        val latenciesMs = ConcurrentLinkedQueue<Long>()
        val latch = CountDownLatch(records)

        val t0Wall = System.nanoTime()

        repeat(records) { i ->
            val key = "user-${(i % 50) + 1}"
            val value = """{"orderId":"o-${i+1}","user":"$key","amount":${(1000 + i)/10.0}}"""
            val rec = ProducerRecord(topic, key, value)

            val start = System.nanoTime()
            producer.send(rec) { md, ex ->
                try {
                    if (ex == null) {
                        val end = System.nanoTime()
                        latenciesMs.add(nanosToMillis(end - start))
                    } else {
                        // Count failed sends as very high latency so they’re visible (or skip, your choice)
                        latenciesMs.add(Long.MAX_VALUE / 2)
                        System.err.println("Callback error: ${ex::class.simpleName}: ${ex.message}")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all callbacks (with a generous timeout)
        val ok = latch.await(60, TimeUnit.SECONDS)
        producer.flush()
        val t1Wall = System.nanoTime()

        if (!ok) {
            System.err.println("Timed out waiting for callbacks; collected=${latenciesMs.size}")
        }

        val millis = nanosToMillis(t1Wall - t0Wall)
        val sent = latenciesMs.size
        val rps = if (millis > 0) (sent * 1000.0 / millis).roundToLong() else sent.toLong()

        val stats = summary(latenciesMs.filter { it >= 0 && it < Long.MAX_VALUE / 4 })
        println("\n=== Results ===")
        println("Sent: $sent records  |  Total time: ${millis} ms  |  ~Throughput: $rps rec/s")
        println("Latency (ms): p50=${stats.p50}  p95=${stats.p95}  p99=${stats.p99}  min=${stats.min}  max=${stats.max}")
        println("Config: lingerMs=${linger ?: "-"}, batchSize=${batchSize ?: "-"}, compression=${compression ?: "-"}")
    }
}

private fun nanosToMillis(nanos: Long) = (nanos / 1_000_000.0).roundToLong()

private data class PctStats(
    val min: Long, val max: Long, val p50: Long, val p95: Long, val p99: Long
)

private fun summary(xsIn: List<Long>): PctStats {
    if (xsIn.isEmpty()) return PctStats(0, 0, 0, 0, 0)
    val xs = xsIn.sorted()
    fun pct(p: Double): Long = xs[(p * (xs.size - 1)).coerceIn(0.0, 1.0).toInt()]
    return PctStats(
        min = xs.first(),
        max = xs.last(),
        p50 = pct(0.50),
        p95 = pct(0.95),
        p99 = pct(0.99)
    )
}

private fun env(name: String, default: String?): String? =
    System.getenv(name) ?: default
