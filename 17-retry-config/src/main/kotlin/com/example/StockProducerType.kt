package com.example

import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
import org.apache.kafka.clients.producer.RecordMetadata



@Service
class StockProducerTyped(
    private val template: KafkaTemplate<String, Any>
){
    private val topic = "inventory.events.json"

    fun send(event: StockChanged){
        val sendResult = template.send(topic, event.sku, event).get()
        logSuccess(event.sku, sendResult.recordMetadata, "StockChanged")
    }

    private fun logSuccess(key: String, md: RecordMetadata?, eventType: String) {
        if (md != null)
            println("$eventType sent: key=$key -> ${md.topic()} p=${md.partition()} off=${md.offset()}")
    }
}