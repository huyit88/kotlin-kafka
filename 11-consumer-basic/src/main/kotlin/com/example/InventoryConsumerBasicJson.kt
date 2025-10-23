package com.example

import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable


fun main() {
    val props = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
        put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-service")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    }

    val consumer = KafkaConsumer<String, String>(props)
    val running = AtomicBoolean(true)

    Runtime.getRuntime().addShutdownHook(Thread {
        running.set(false);
        consumer.wakeup()
    })

    try {
        consumer.subscribe(listOf("orders-json"), object : ConsumerRebalanceListener {
            override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                println("Revoked: $partitions")
            }
            override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                println("Assigned: $partitions")
            }
        })

        while (running.get()) {
            val records = consumer.poll(Duration.ofMillis(500))
            for (rec in records) {
                println("orders[${rec.partition()}@${rec.offset()}] key=${rec.key()} value=${rec.value()}")
                try{
                    val event = Json.decodeFromString<Order>(rec.value())
                    println("orderId: ${event.orderId} user: ${event.user}")
                }catch(ex: Exception){
                    println("can't parse json value:${ex.message}")
                }
            }
        }
    } catch (_: WakeupException) {
        // graceful shutdown
    } finally {
        consumer.close()
    }
}

@Serializable
data class Order(val orderId: String, val user: String, val amount: Double)