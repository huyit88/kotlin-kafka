package com.example

import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
@KafkaListener(topics = ["inventory.events.json"], groupId = "inventory-mixed")
class InventoryPolymorphicListener {

    private val logger = LoggerFactory.getLogger(InventoryPolymorphicListener::class.java)
    private val store = mutableMapOf<String, Int>()

    @KafkaHandler
    fun handleChanged(@Payload evt: StockChanged, ack: Acknowledgment) {
        logger.debug("Received StockChanged: ${evt::class.qualifiedName}")
        val newQty = (store[evt.sku] ?: 0) + evt.delta
        store[evt.sku] = newQty
        logger.info("🔄 CHANGED ${evt.sku}: ${evt.delta} → qty=$newQty  reason=${evt.reason}")
        ack.acknowledge()
    }

    @KafkaHandler
    fun handleAdjusted(@Payload evt: StockAdjusted, ack: Acknowledgment) {
        logger.debug("Received StockAdjusted: ${evt::class.qualifiedName}")
        val newQty = (store[evt.sku] ?: 0) + evt.amount
        store[evt.sku] = newQty
        logger.info("🛠  ADJUSTED ${evt.sku}: ${evt.amount} → qty=$newQty")
        ack.acknowledge()
    }

    // Optional: fallback if a new/unknown type appears
    @KafkaHandler(isDefault = true)
    fun handleUnknown(@Payload payload: Any, ack: Acknowledgment) {
        logger.warn("❓ Unknown payload type: ${payload::class.qualifiedName} -> $payload")
        ack.acknowledge()
    }
}
