package com.example

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class PartitionedLogProducer(
    val bootstrapServers: String
){
    val producer: KafkaProducer<String,String>

    init{
        val props = Properties().apply{
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.LINGER_MS_CONFIG, 0)
        }
        producer = KafkaProducer(props)
    }

    fun send(app: String, message: String): RecordMetadata{
        val record = ProducerRecord("logs", app, message)
        return producer.send(record).get()
    }

    fun close(){
        producer.flush()
        producer.close()
    }
}