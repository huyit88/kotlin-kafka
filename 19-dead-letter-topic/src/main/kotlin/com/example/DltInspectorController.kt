package com.example

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.*

@RestController
class DltInspectorController {
    
    @GetMapping("/dlt/last")
    fun getLatest(): ResponseEntity<Map<String, Any>> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
            put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-inspector-${System.currentTimeMillis()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        }
        val topic = "payments.DLT";
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))
            
            // Wait for partition assignment
            consumer.poll(Duration.ofMillis(100))
            
            // Seek to end of all partitions
            val partitions = consumer.assignment()
            if (partitions.isEmpty()) {
                return ResponseEntity.ok(mapOf(
                    "value" to "",
                    "headers" to emptyMap<String, String>()
                ))
            }
            
            consumer.seekToEnd(partitions)
            
            // Find the latest offset for each partition and seek to position - 1
            partitions.forEach { partition ->
                val endPosition = consumer.position(partition)
                if (endPosition > 0) {
                    consumer.seek(partition, endPosition - 1)
                }
            }
            
            // Poll to get the record(s)
            val records = consumer.poll(Duration.ofMillis(1000))
            if (records.isEmpty) {
                return ResponseEntity.ok(mapOf(
                    "value" to "",
                    "headers" to emptyMap<String, String>()
                ))
            }
            
            // Get the record with the highest offset (latest)
            val lastRecord = records.maxByOrNull { it.offset() } ?: records.iterator().next()
            
            // Extract headers
            val headers = mutableMapOf<String, String>()
            lastRecord.headers().forEach { header ->
                headers[header.key()] = String(header.value())
            }
            
            return ResponseEntity.ok(mapOf(
                "value" to (lastRecord.value() ?: ""),
                "headers" to headers,
                "topic" to topic,
                "partition" to lastRecord.partition(),
                "offset" to lastRecord.offset()
            ))
        }
    }
}