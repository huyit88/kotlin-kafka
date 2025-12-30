package com.example

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class LagMetricsService(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String,
    @Value("\${kafka.topic.notifications}") private val topicName: String
) {
    
    fun getLagMetrics(): LagMetrics {
        val props = Properties()
        props["bootstrap.servers"] = bootstrapServers
        props["key.deserializer"] = "org.apache.kafka.common.serialization.StringDeserializer"
        props["value.deserializer"] = "org.apache.kafka.common.serialization.StringDeserializer"
        props["group.id"] = groupId
        
        // Use consumer to get end offsets and committed offsets
        val consumer = KafkaConsumer<String, String>(props)
        
        try {
            // Get all partitions for the topic
            val partitions = consumer.partitionsFor(topicName)?.map { 
                TopicPartition(topicName, it.partition())
            } ?: emptyList()
            
            // Convert to Set for committed() method
            val partitionSet = partitions.toSet()
            
            // Get end offsets (latest available)
            val endOffsets = consumer.endOffsets(partitionSet)
            
            // Get committed offsets for the consumer group
            val committedOffsets = consumer.committed(partitionSet, Duration.ofSeconds(5))
            
            // Calculate lag for each partition
            val partitionLags = partitions.map { partition ->
                val endOffset = endOffsets[partition] ?: 0L
                val committedOffsetAndMetadata = committedOffsets[partition]
                val committedOffset = committedOffsetAndMetadata?.offset() ?: 0L
                val lag = (endOffset - committedOffset).coerceAtLeast(0)
                partition.partition() to lag
            }
            
            val totalLag = partitionLags.sumOf { it.second }
            val maxPartitionLag = partitionLags.maxOfOrNull { it.second } ?: 0L
            
            return LagMetrics(
                totalLag = totalLag,
                maxPartitionLag = maxPartitionLag,
                partitionLags = partitionLags.toMap()
            )
        } finally {
            consumer.close()
        }
    }
}

data class LagMetrics(
    val totalLag: Long,
    val maxPartitionLag: Long,
    val partitionLags: Map<Int, Long>
)

