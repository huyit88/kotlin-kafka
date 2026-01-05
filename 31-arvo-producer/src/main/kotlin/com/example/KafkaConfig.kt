package com.example

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaConfig {

    @Bean
    fun avroProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ProducerFactory<String, Transaction> {
        val props = mutableMapOf<String, Any>()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
        props["schema.registry.url"] = schemaRegistryUrl
        
        // Producer safety configs for payment/fraud pipelines
        // acks=all: Wait for all in-sync replicas to acknowledge before considering write successful.
        // Critical for financial transactions to ensure data durability and prevent data loss.
        props[ProducerConfig.ACKS_CONFIG] = "all"
        
        // enable.idempotence=true: Prevents duplicate messages even on retries by using producer ID and sequence numbers.
        // Essential for payment systems where duplicate transactions could cause financial discrepancies.
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        
        // retries=Integer.MAX_VALUE: Retry indefinitely until message is successfully written or delivery timeout expires.
        // Ensures no transaction data is lost due to transient network or broker issues in critical payment pipelines.
        props[ProducerConfig.RETRIES_CONFIG] = Integer.MAX_VALUE
        
        // max.in.flight.requests.per.connection=5: Allows up to 5 unacknowledged requests per connection.
        // With idempotence enabled, this maintains ordering guarantees while improving throughput for high-volume payment processing.
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        return DefaultKafkaProducerFactory<String, Transaction>(props)
    }

    @Bean
    fun avroKafkaTemplate(avroProducerFactory: ProducerFactory<String, Transaction>): KafkaTemplate<String, Transaction> {
        return KafkaTemplate<String, Transaction>(avroProducerFactory)
    }

    @Bean
    fun avroConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ConsumerFactory<String, Transaction> {
        val props = mutableMapOf<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
        props[ConsumerConfig.GROUP_ID_CONFIG] = "fraud-detector"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props["schema.registry.url"] = schemaRegistryUrl
        props["specific.avro.reader"] = true  // Required for specific classes
        return DefaultKafkaConsumerFactory<String, Transaction>(props)
    }

    @Bean
    fun avroKafkaListenerContainerFactory(
        avroConsumerFactory: ConsumerFactory<String, Transaction>
    ): ConcurrentKafkaListenerContainerFactory<String, Transaction> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Transaction>()
        factory.consumerFactory = avroConsumerFactory
        return factory
    }

}

