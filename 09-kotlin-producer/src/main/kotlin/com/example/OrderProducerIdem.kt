package com.example

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.RetriableException
import kotlin.math.min
import kotlin.random.Random
import java.util.Properties
import java.util.UUID 

fun main(){
    val bootstrapServers = "localhost:19092"
    val topic = "orders"
    val props = Properties().apply{
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.RETRIES_CONFIG, Int.MAX_VALUE.toString())
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")

        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)

        put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000")

        put(ProducerConfig.LINGER_MS_CONFIG, "10")
        put(ProducerConfig.BATCH_SIZE_CONFIG, (64 * 1024).toString())
        put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
    }

    KafkaProducer<String, String>(props).use { producer ->
        // Build a deterministic batch of 20 records (keys cycle to show per-key partition stickiness)
        val batch = (1..20).map { i ->
            val user = "user-${(i % 5) + 1}" // user-1..user-5
            val value = """{"orderId":"o-$i","user":"$user","amount":${(1000 + i)/10.0}}"""
            ProducerRecord(topic, user, value).apply{
                headers().add("eventType", "ORDER_CREATED".toByteArray(Charsets.UTF_8))
                headers().add("source", "OrderService".toByteArray(Charsets.UTF_8))
                headers().add("traceId", UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            }
        }

        sendBatchWithRetry(producer, batch)

        // Ensure all in-flight sends are flushed
        producer.flush()
        println("Done. Inspect with a consumer; you should see 20 logical records (no retry-induced dups).")
    }
}

private fun sendBatchWithRetry(
    producer: KafkaProducer<String, String>,
    records: List<ProducerRecord<String, String>>,
    maxAttempts: Int = 8,
    initialBackoffMs: Long = 100L,
    maxBackoffMs: Long = 3000L
) {
    records.forEachIndexed { idx, rec ->
        var attempt = 1
        var backoff = initialBackoffMs
        while (true) {
            try {
                val md: RecordMetadata = producer.send(rec).get()
                println("ok  : #${idx + 1} key=${rec.key()} -> ${md.topic()} p=${md.partition()} off=${md.offset()}")
                break
            } catch (e: Exception) {
                val retriable = e is TimeoutException || e is RetriableException || e.cause is RetriableException
                if (!retriable || attempt >= maxAttempts) {
                    System.err.println("fail: #${idx + 1} key=${rec.key()} after $attempt attempts: ${e.message}")
                    throw e
                }
                // Backoff with jitter
                val sleepFor = min(backoff, maxBackoffMs) + (0..100).random()
                System.err.println("retry: #${idx + 1} attempt=$attempt sleeping=${sleepFor}ms cause=${e::class.simpleName}")
                Thread.sleep(sleepFor)
                backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
                attempt++
            }
        }
    }
}