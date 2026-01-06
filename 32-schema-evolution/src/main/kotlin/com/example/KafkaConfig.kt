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
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.RETRIES_CONFIG] = Integer.MAX_VALUE
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

