package com.example

import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener
import org.slf4j.LoggerFactory
import org.apache.kafka.clients.consumer.Consumer


class MyRebalanceListener : ConsumerAwareRebalanceListener {
    private val logger = LoggerFactory.getLogger(MyRebalanceListener::class.java)
    
    override fun onPartitionsRevokedBeforeCommit(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>
    ) {
        logger.info("Partitions REVOKED (before commit): $partitions")
        // Note: With MANUAL_IMMEDIATE ack-mode, offsets are already committed
        // per record, so just log here (no manual commit needed)
    }
    
    override fun onPartitionsRevokedAfterCommit(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>
    ) {
        logger.info("Partitions REVOKED (after commit): $partitions")
    }
    
    override fun onPartitionsAssigned(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>
    ) {
        logger.info("Partitions ASSIGNED: $partitions")
    }
    
    override fun onPartitionsLost(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>
    ) {
        logger.info("Partitions LOST: $partitions")
    }
}