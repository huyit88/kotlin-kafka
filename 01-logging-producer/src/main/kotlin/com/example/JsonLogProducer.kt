package com.example

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer


import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Properties

class JsonLogProducer(val bootstrapServers: String){
    val producer: KafkaProducer<String,String>

    init{
        val props = Properties()
        props["bootstrap.servers"] = bootstrapServers
        props["key.serializer"] = StringSerializer::class.java.name
        props["value.serializer"] = StringSerializer::class.java.name
        props["acks"] = "all"
        props["retries"] = 3
        props["linger.ms"] = 0

        producer = KafkaProducer(props)
    }

    

    fun sendLog(event: LogEvent): RecordMetadata{
        val json = Json.encodeToString(event)
        val record = ProducerRecord<String,String>("logs", event.app, json).apply{
            headers().add("level", event.level.toByteArray(Charsets.UTF_8))
        }
        return producer.send(record).get()
    }

    fun close(){
        producer.flush()
        producer.close()
    }
}