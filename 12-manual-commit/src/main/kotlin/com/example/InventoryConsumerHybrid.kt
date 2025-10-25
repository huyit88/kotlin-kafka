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
    val toCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()


    Runtime.getRuntime().addShutdownHook(Thread {
        running.set(false);
        consumer.wakeup()
    })

    consumer.subscribe(listOf("orders"))

    try {
        var sinceLastSync = 0
        var lastSyncTime = System.currentTimeMillis()
        
        while (running.get()) {
            val records = consumer.poll(Duration.ofMillis(1000))
            for (rec in records) {
                println("orders[${rec.partition()}@${rec.offset()}] key=${rec.key()} value=${rec.value()}")
                val tp = TopicPartition(rec.topic(), rec.partition())
                toCommit[tp] = OffsetAndMetadata(rec.offset() + 1)
                sinceLastSync++
            }
            
            if (toCommit.isNotEmpty()) {
                consumer.commitAsync(toCommit) { offsets, ex ->
                    if (ex != null) {
                        System.err.println("commitAsync fail: ${ex.message}")
                    } else {
                        println("Committed: $offsets")
                    }
                }
            }
            
            val now = System.currentTimeMillis()
            if (sinceLastSync >= 200 || now - lastSyncTime >= 5000) {
                consumer.commitSync()
                println("Sync committed after $sinceLastSync records or ${now - lastSyncTime}ms")
                toCommit.clear()
                sinceLastSync = 0
                lastSyncTime = now
            }
        }
    } catch (_: WakeupException) {
        if(toCommit.isNotEmpty()){
            consumer.commitSync(toCommit)
            println("Committed: $toCommit")
            toCommit.clear()
        }
    } finally {
        consumer.close()
    }
}