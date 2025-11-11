package com.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.Instant


@Component
class InventoryListener {

    private val mapper = jacksonObjectMapper()
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val stockStore = mutableMapOf<String, Int>()
    val logger = LoggerFactory.getLogger(InventoryListener::class.java)
    
    // Throughput tracking for sync consumer
    private var syncProcessedCount = java.util.concurrent.atomic.AtomicLong(0)
    private var syncLastLogTime = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private val syncLogInterval = 1000L // Log every 1 second
    
    // Throughput tracking for async consumer
    private var asyncProcessedCount = java.util.concurrent.atomic.AtomicLong(0)
    private var asyncLastLogTime = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private val asyncLogInterval = 1000L // Log every 1 second

    private fun shouldFail(key: String, times: Int): Boolean {
        val attempt = attempts.merge(key, 1, Int::plus) ?: 1
        return attempt <= times
    }

    @KafkaListener(topics = ["inventory.events.json"], groupId = "inventory-consumer-retry-sync")
    fun onEvent(record: ConsumerRecord<String, StockChanged>, ack: Acknowledgment){
        val evt = record.value()
        val newQty = stockStore.getOrDefault(evt.sku, 0) + evt.delta
        if(evt.delta % 20 == 0){
            logger.info("[${Instant.now()}] FAILURE for ${evt.sku} - throwing transient exception")
            throw java.lang.IllegalStateException("transient")
        }
        if(evt.sku == "SKU-RETRY"){
            logger.info("[${Instant.now()}] FAILURE for ${evt.sku} - throwing transient exception")
            throw java.lang.IllegalStateException("transient")
        }

        if(evt.sku == "SKU-RETRY-2"){
            if(shouldFail(evt.sku, 2)){
                val attempt = attempts[evt.sku] ?: 1
                logger.info("[${Instant.now()}] FAILURE attempt $attempt for ${evt.sku} - throwing transient exception")
                throw java.lang.IllegalStateException("transient")
            } else {
                // Reset after successful processing
                attempts.remove(evt.sku)
                logger.info("[${Instant.now()}] SUCCESS for ${evt.sku} after retries")
            }
        }
        
        if(evt.sku == "SKU-NO-RETRY"){
            logger.info("[${Instant.now()}] FAILURE for ${evt.sku} - throwing non-retryable exception")
            throw java.lang.IllegalArgumentException("invalid argument")
        }
        
        stockStore[evt.sku] = newQty
        
        // Track throughput for sync consumer
        val count = syncProcessedCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val lastLog = syncLastLogTime.get()
        if (now - lastLog >= syncLogInterval) {
            if (syncLastLogTime.compareAndSet(lastLog, now)) {
                val elapsed = (now - lastLog) / 1000.0
                val throughput = (count / elapsed).toInt()
                logger.info("[SYNC THROUGHPUT] Processed $count messages in ${elapsed}s = $throughput msg/s")
                syncProcessedCount.set(0)
            }
        }
        
        logger.info("${Thread.currentThread().name} Apply ${evt.reason} ${evt.delta} to ${evt.sku} -> qty=$newQty  [p=${record.partition()} off=${record.offset()}]")
        ack.acknowledge()
    }

    @KafkaListener(topics = ["inventory.events.json"], groupId = "inventory-consumer-retry-async")
    fun onRetryAsyncEvent(record: ConsumerRecord<String, StockChanged>, ack: Acknowledgment){
        val evt = record.value()
        if(evt.delta % 20 == 0){
            logger.info("[${Instant.now()}] FAILURE for ${evt.sku} - throwing transient exception")
            throw java.lang.IllegalStateException("transient")
        }

        val newQty = stockStore.getOrDefault(evt.sku, 0) + evt.delta
        if(evt.sku == "SKU-ASYNC"){
            throw java.lang.IllegalStateException("transient")
        }
        stockStore[evt.sku] = newQty
        
        // Track throughput for async consumer
        val count = asyncProcessedCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val lastLog = asyncLastLogTime.get()
        if (now - lastLog >= asyncLogInterval) {
            if (asyncLastLogTime.compareAndSet(lastLog, now)) {
                val elapsed = (now - lastLog) / 1000.0
                val throughput = (count / elapsed).toInt()
                logger.info("[ASYNC THROUGHPUT] Processed $count messages in ${elapsed}s = $throughput msg/s")
                asyncProcessedCount.set(0)
            }
        }
        
        logger.info("${Thread.currentThread().name} Apply ${evt.reason} ${evt.delta} to ${evt.sku} -> qty=$newQty  [p=${record.partition()} off=${record.offset()}]")
        ack.acknowledge()
    }
}