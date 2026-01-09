package com.example

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.util.*

@Configuration
class KafkaConfig {

    private val logger = LoggerFactory.getLogger(KafkaConfig::class.java)

    @Bean
    fun producerProps(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): Map<String, Any>{
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.RETRIES_CONFIG] = Integer.MAX_VALUE
        
        return props;
    }

    @Bean
    fun kafkaTemplateFast(producerProps: Map<String,Any>): KafkaTemplate<String, String> {
        val props = HashMap(producerProps)
        // Fast producer: No transactions, no idempotence, acks=1 for speed
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = false
        props[ProducerConfig.ACKS_CONFIG] = "1"  // Leader acknowledgment only (faster)
        val factory = DefaultKafkaProducerFactory<String, String>(props)
        val template = KafkaTemplate(factory)
        logger.info("FAST_PRODUCER_READY (no idempotence, no transactions, acks=1)")
        return template
    }

    @Bean
    fun kafkaTemplateSafe(producerProps: Map<String,Any>): KafkaTemplate<String, String> {
        val props = HashMap(producerProps)
        // Safe producer: EOS with idempotence and transactions
        // acks=all is required for transactions (inherited from producerProps)
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "eos-benchmark-tx"
        props[ProducerConfig.ACKS_CONFIG] = "all"  // Explicit: required for transactions
        val factory = DefaultKafkaProducerFactory<String, String>(props)
        val template = KafkaTemplate(factory)
        logger.info("SAFE_PRODUCER_READY (idempotence=true, transactional.id=eos-benchmark-tx, acks=all)")
        return template
    }

    @Bean
    fun kafkaTemplateIdempotence(producerProps: Map<String,Any>): KafkaTemplate<String, String> {
        val props = HashMap(producerProps)
        // Idempotent producer: idempotence only, no transactions
        // acks=all is required when enable.idempotence=true
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.ACKS_CONFIG] = "all"  // Required for idempotence
        // Optimize batching for better performance (no transaction overhead)
        props[ProducerConfig.LINGER_MS_CONFIG] = 10  // Wait up to 10ms to batch messages
        props[ProducerConfig.BATCH_SIZE_CONFIG] = 65536  // 64KB batch size
        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"  // Compress batches
        // No TRANSACTIONAL_ID_CONFIG - this is idempotent-only, not transactional
        val factory = DefaultKafkaProducerFactory<String, String>(props)
        val template = KafkaTemplate(factory)
        logger.info("IDEMPOTENT_PRODUCER_READY (idempotence=true, no transactions, acks=all, batching optimized)")
        return template
    }

    // Consumer factory for read_committed isolation level
    @Bean
    fun readCommittedConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String
    ): ConsumerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.GROUP_ID_CONFIG] = "safe-read-committed"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = "read_committed"
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
        return DefaultKafkaConsumerFactory<String, String>(props)
    }

    @Bean(name = ["readCommittedKafkaListenerContainerFactory"])
    fun readCommittedKafkaListenerContainerFactory(
        readCommittedConsumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = readCommittedConsumerFactory
        return factory
    }

    // Consumer factory for read_uncommitted isolation level
    @Bean
    fun readUncommittedConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String
    ): ConsumerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.GROUP_ID_CONFIG] = "safe-read-uncommitted"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = "read_uncommitted"
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
        return DefaultKafkaConsumerFactory<String, String>(props)
    }

    @Bean(name = ["readUncommittedKafkaListenerContainerFactory"])
    fun readUncommittedKafkaListenerContainerFactory(
        readUncommittedConsumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = readUncommittedConsumerFactory
        return factory
    }
}