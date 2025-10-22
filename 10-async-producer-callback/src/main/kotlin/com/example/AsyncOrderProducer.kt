package com.example

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import kotlin.random.Random
import java.util.Properties
import java.util.concurrent.TimeUnit

fun main(){
    val bootstrapServers = "localhost:19092"
    val topic = "orders"
    val props = Properties().apply{
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.RETRIES_CONFIG, Int.MAX_VALUE.toString())
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
    }

    val n = 20
    val latch = java.util.concurrent.CountDownLatch(n)
    val producer = KafkaProducer<String,String>(props)
    repeat(n){i->
        val randomNumber = Random.nextInt(1, 4)
        val user = "user-${(i % 5) + 1}" // user-1..user-5
        val order="""{"orderId":"o-$i","user":"${user}","amount":${randomNumber}}"""
        val record = ProducerRecord(topic, user, order)
        producer.send(record){metadata, ex->
            try {
                if (ex != null) {
                    System.err.println("FAIL: ${ex::class.java.simpleName}: ${ex.message}")
                } else if (metadata != null) {
                    println("sent topic=${metadata.topic()} partition=${metadata.partition()} offset=${metadata.offset()} timestamp=${metadata.timestamp()}")
                }
            } finally {
                latch.countDown()
            }
        }
    }

    // Await callbacks before closing
    val completed = latch.await(30, TimeUnit.SECONDS)
    if (!completed) System.err.println("WARN: Latch did not reach zero within timeout. Remaining=${latch.count}")
    producer.flush()
    producer.close()
}