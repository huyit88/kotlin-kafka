package com.example

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.ConsumerFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.*
import org.springframework.kafka.config.TopicBuilder
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import java.util.*



@Configuration
class KafkaConfig {
    @Bean
    fun producerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): ProducerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        props[ProducerConfig.LINGER_MS_CONFIG] = 20
        props[ProducerConfig.BATCH_SIZE_CONFIG] = 65536
        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        val factory = DefaultKafkaProducerFactory<String, String>(props)
        return factory
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun consumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${spring.kafka.consumer.group-id}") groupId: String
    ): ConsumerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 10000
        props[ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG] = "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
        return DefaultKafkaConsumerFactory<String, String>(props)
    }
}