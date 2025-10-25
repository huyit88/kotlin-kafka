package com.example

import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val props = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
        put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-service-manual")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }

    val consumer = KafkaConsumer<String, String>(props)
    val running = AtomicBoolean(true)

    Runtime.getRuntime().addShutdownHook(Thread {
        running.set(false);
        consumer.wakeup()
    })

    consumer.subscribe(listOf("orders"))

    try {
        val toCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        while (running.get()) {
            val records = consumer.poll(Duration.ofMillis(1000))
            for (rec in records) {
                println("orders[${rec.partition()}@${rec.offset()}] key=${rec.key()} value=${rec.value()}")
                val tp = TopicPartition(rec.topic(), rec.partition())
                toCommit[tp] = OffsetAndMetadata(rec.offset() + 1)
            }
            if (toCommit.isNotEmpty()) {
                consumer.commitSync(toCommit)
                println("Committed: $toCommit")
                toCommit.clear()
            }
        }
    } catch (_: WakeupException) {
        // graceful shutdown
    } finally {
        consumer.close()
    }
}