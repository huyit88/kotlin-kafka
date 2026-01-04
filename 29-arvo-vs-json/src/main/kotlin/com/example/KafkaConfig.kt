package com.example

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
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
import java.io.InputStream

@Configuration
class KafkaConfig {

    @Bean
    fun jsonProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String
    ): ProducerFactory<String, String> {
        val props = mutableMapOf<String, Any>()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory<String, String>(props)
    }

    @Bean
    fun jsonKafkaTemplate(jsonProducerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(jsonProducerFactory)
    }

    @Bean
    fun avroProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ProducerFactory<String, GenericRecord> {
        val props = mutableMapOf<String, Any>()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
        props["schema.registry.url"] = schemaRegistryUrl
        return DefaultKafkaProducerFactory<String, GenericRecord>(props)
    }

    @Bean
    fun avroKafkaTemplate(avroProducerFactory: ProducerFactory<String, GenericRecord>): KafkaTemplate<String, GenericRecord> {
        return KafkaTemplate(avroProducerFactory)
    }

    @Bean
    fun avroConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ConsumerFactory<String, GenericRecord> {
        val props = mutableMapOf<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
        props[ConsumerConfig.GROUP_ID_CONFIG] = "fraud-avro"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props["schema.registry.url"] = schemaRegistryUrl
        props["specific.avro.reader"] = false  // Use GenericRecord instead of specific classes
        return DefaultKafkaConsumerFactory<String, GenericRecord>(props)
    }

    @Bean
    fun avroKafkaListenerContainerFactory(
        avroConsumerFactory: ConsumerFactory<String, GenericRecord>
    ): ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.consumerFactory = avroConsumerFactory
        return factory
    }

    @Bean
    fun transactionSchema(): Schema {
        val schemaStream: InputStream = javaClass.classLoader
            .getResourceAsStream("avro/com/example/TransactionV2.avsc")
            ?: throw IllegalStateException("Transaction.avsc not found in resources/avro/com/example/")
        return Schema.Parser().parse(schemaStream)
    }
}

