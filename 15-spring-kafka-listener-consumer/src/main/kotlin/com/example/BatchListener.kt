package com.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory


@Component
class BatchListener {

    private val mapper = jacksonObjectMapper()
    private val stockStore = mutableMapOf<String, Int>()
    val logger = LoggerFactory.getLogger(BatchListener::class.java)

    @KafkaListener(topics = ["inventory.events"], containerFactory = "batchFactory", groupId = "inventory-consumer-batch")
    fun onBatch(records: List<ConsumerRecord<String, String>>, ack: Acknowledgment) {
        logger.info("Processing batch of ${records.size} records")
        for(record in records){
            val json = record.value()
            val evt = mapper.readValue<StockChanged>(json)
            if(evt.reason == "RESERVE" && evt.delta == -13){
                throw IllegalStateException("test failure")
            }
            val newQty = stockStore.getOrDefault(evt.sku, 0) + evt.delta
            stockStore[evt.sku] = newQty
            logger.info("${Thread.currentThread().name} Apply ${evt.reason} ${evt.delta} to ${evt.sku} -> qty=$newQty  [p=${record.partition()} off=${record.offset()}]")
        }
        ack.acknowledge()
    }
}