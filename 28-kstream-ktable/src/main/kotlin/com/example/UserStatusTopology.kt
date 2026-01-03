package com.example

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

@Configuration
class UserStatusTopology {
    
    companion object {
        private const val APPLICATION_ID = "kstream-ktable"
        private const val USER_PRESENCE_TOPIC = "user-presence"
        private const val USER_STATUS_VIEW_TOPIC = "user-status-view"
        private const val USER_STATUS_STORE = "user-status-store"
    }

    @Bean
    fun kStreamsProps(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): Properties =
        Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
        }

    @Bean
    fun streamsBuilderFactoryBean(kStreamsProps: Properties): StreamsBuilderFactoryBean {
        val factoryBean = StreamsBuilderFactoryBean()
        factoryBean.setStreamsConfiguration(kStreamsProps)
        return factoryBean
    }

    @Bean
    @DependsOn("streamsBuilderFactoryBean")
    fun streamsBuilder(streamsBuilderFactoryBean: StreamsBuilderFactoryBean): StreamsBuilder {
        return streamsBuilderFactoryBean.getObject()
    }

    @Bean
    fun kafkaProducerFactory(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
        return DefaultKafkaProducerFactory<String, String>(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun statusTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
        val allEventsStream = streamsBuilder.stream(
            USER_PRESENCE_TOPIC,
            Consumed.with(Serdes.String(), Serdes.String())
        ).peek { userId, value ->
            println("EVENT user=$userId value=$value")  // Problem D: Log all events
        }
        
        // Now create KTable from the stream (Problem A)
        val materialized = Materialized
            .`as`<String, String, KeyValueStore<Bytes, ByteArray>>(USER_STATUS_STORE)
            .withKeySerde(Serdes.String())
            .withValueSerde(Serdes.String())
        
        val userStatus: KTable<String, String> = allEventsStream
            .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
            .reduce({ _, newValue -> newValue }, materialized)
        
        // Convert KTable to stream and log updates (only when value changes)
        val userStatusStream = userStatus.toStream()
        
        userStatusStream
            .peek { userId, status -> 
                println("STATUS_UPDATE user=$userId status=$status")  // Problem A: Log updates only
            }
            .to(USER_STATUS_VIEW_TOPIC, Produced.with(Serdes.String(), Serdes.String()))
        
        return userStatusStream
    }
}
