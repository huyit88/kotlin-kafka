package com.example

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.random.Random
import java.util.Properties

fun main(){
    val bootstrapServers = "localhost:19092"
    val topic = "orders"
    val props = Properties().apply{
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.RETRIES_CONFIG, 3)
        put(ProducerConfig.LINGER_MS_CONFIG, 20)
        put(ProducerConfig.BATCH_SIZE_CONFIG, 65536)
        put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
    }

    val producer = KafkaProducer<String,String>(props)
    val start = System.currentTimeMillis()
    repeat(1000){
        val randomUser = Random.nextInt(1, 51)
        val user = "user-${randomUser}"
        val order="""{"orderId":"o-$it","user":"${user}","amount":${Random.nextInt(1, 4)}}"""
        val record = ProducerRecord(topic, user, order)
        producer.send(record){ metadata, exception ->
            if (exception == null) {
                println("Sent to ${metadata.topic()} partition=${metadata.partition()} offset=${metadata.offset()}")
            } else {
                println("Failed to send key=$user value=$order")
                exception.printStackTrace()
            }
        }        
    }

    producer.flush()
    producer.close()

    val totalTime = System.currentTimeMillis() - start 
    val throughput = 1000.0 / (totalTime / 1000.0)
    println("Total time: ${totalTime}ms")
    println("Throughput: ${String.format("%.2f", throughput)} records/sec")
}