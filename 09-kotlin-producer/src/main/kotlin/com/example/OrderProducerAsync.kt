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
    }

    val producer = KafkaProducer<String,String>(props)
    repeat(15){
        val randomNumber = Random.nextInt(1, 4)
        val user = "user-${(it % 3) + 1}"
        val order="""{"orderId":"o-$it","user":"${user}","amount":${randomNumber}}"""
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
}