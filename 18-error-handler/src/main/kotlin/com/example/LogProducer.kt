package com.example

import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
import org.apache.kafka.clients.producer.RecordMetadata

@Service
class LogProducer(
    private val template: KafkaTemplate<String, String>
){
    private val topic = KafkaConfig.TOPIC

    fun send(event: Log){
        val sendResult = template.send(topic, event.key, event.message).get()
        logSuccess(event.key, sendResult.recordMetadata, "Log")
    }

    private fun logSuccess(key: String, md: RecordMetadata?, eventType: String) {
        if (md != null)
            println("$eventType sent: key=$key -> ${md.topic()} p=${md.partition()} off=${md.offset()}")
    }
}

data class Log(val key: String, val message: String)