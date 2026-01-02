package com.example

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.processor.TimestampExtractor
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.WindowStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import java.time.Duration
import java.util.Properties

@Configuration
class ChatAggregationTopology {
    
    // Custom timestamp extractor to extract timestamp from message value
    class ValueTimestampExtractor : TimestampExtractor {
        override fun extract(record: ConsumerRecord<Any?, Any?>, partitionTime: Long): Long {
            val value = record.value() as? String ?: return partitionTime
            val tsPart = value.split("|").find { it.startsWith("ts=") }
            return tsPart?.substringAfter("ts=")?.toLongOrNull() ?: partitionTime
        }
    }
    
    companion object {
        private const val APPLICATION_ID = "chat-aggregation"
        private const val CHAT_OUTPUT_TOPIC = "chat-output"
        private const val CHAT_USER_COUNT_TOPIC = "chat-user-counts"
        private const val CHAT_USER_COUNT_WINDOWED_TOPIC = "chat-user-counts-windowed"
        private const val REPARTITION_TOPIC_NAME = "user-rekey"
    }

    @Bean
    fun kStreamsProps(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): Properties =
        Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
            put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, ValueTimestampExtractor::class.java)
            // State directory for local state stores (used for state restoration)
            put(StreamsConfig.STATE_DIR_CONFIG, "/Users/hung/Documents/temp/kafka-streams/${APPLICATION_ID}")
            // Enable state restoration logging
            put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE)
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

    // Helper function to extract sender from message value
    private fun getSender(value: String): String = value.split("|").firstOrNull() ?: ""

    // Helper function to get input stream
    private fun getInputStream(streamsBuilder: StreamsBuilder): KStream<String, String> {
        return streamsBuilder.stream(
            CHAT_OUTPUT_TOPIC,
            Consumed.with(Serdes.String(), Serdes.String())
        ).peek { key, value ->
            val sender = getSender(value)
            println("IN key=$key (roomId) value=$value -> will re-key to: $sender")
        }
    }

    /**
     * Problem A: Basic aggregation with count()
     * Uses selectKey + groupByKey (implicit repartition)
     * State store: user-message-counts-store (backed by changelog topic for state restoration)
     */
    @Bean
    fun problemATopology(streamsBuilder: StreamsBuilder): KStream<String, Long> {
        println("=== Problem A: Initializing aggregation topology ===")
        println("State store: user-message-counts-store")
        println("Changelog topic will be created automatically for state restoration")
        val inputStream = getInputStream(streamsBuilder)
        
        val materialized = Materialized
            .`as`<String, Long, KeyValueStore<Bytes, ByteArray>>("user-message-counts-store")
            .withKeySerde(Serdes.String())
            .withValueSerde(Serdes.Long())
        
        val userCounts: KTable<String, Long> = inputStream
            .selectKey { _, value -> getSender(value) }
            .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
            .count(materialized)
        
        val userCountsStream = userCounts.toStream()
        
        userCountsStream
            .peek { userId, count -> 
                println("COUNT user=$userId count=$count")
            }
            .to(CHAT_USER_COUNT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()))
        
        return userCountsStream
    }

    /**
     * Problem B: Explicit repartition topic naming
     * Uses groupBy with explicit repartition topic name
     * State store: user-message-counts-store-b (backed by changelog topic for state restoration)
     */
    //@Bean
    fun problemBTopology(streamsBuilder: StreamsBuilder): KStream<String, Long> {
        println("=== Problem B: Initializing aggregation topology with explicit repartition ===")
        // Log expected repartition topic name
        val expectedRepartitionTopic = "$APPLICATION_ID-$REPARTITION_TOPIC_NAME-repartition"
        println("Expected internal repartition topic: $expectedRepartitionTopic")
        println("State store: user-message-counts-store-b")
        println("Changelog topic will be created automatically for state restoration")
        
        val inputStream = getInputStream(streamsBuilder)
        
        val materialized = Materialized
            .`as`<String, Long, KeyValueStore<Bytes, ByteArray>>("user-message-counts-store-b")
            .withKeySerde(Serdes.String())
            .withValueSerde(Serdes.Long())
        
        val userCounts: KTable<String, Long> = inputStream
            .groupBy(
                { oldKey, value -> 
                    val newKey = getSender(value)
                    println("REPARTITION: key changed from '$oldKey' (roomId) to '$newKey' (sender)")
                    newKey
                },
                Grouped.with(REPARTITION_TOPIC_NAME, Serdes.String(), Serdes.String())
            )
            .count(materialized)
        
        val userCountsStream = userCounts.toStream()
        
        userCountsStream
            .peek { userId, count -> 
                println("COUNT user=$userId count=$count")
            }
            .to(CHAT_USER_COUNT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()))
        
        return userCountsStream
    }

    /**
     * Problem C: Windowed aggregation
     * Counts messages per user per 10 seconds
     * State store: user-message-counts-windowed-store (backed by changelog topic for state restoration)
     */
    //@Bean
    fun problemCTopology(streamsBuilder: StreamsBuilder): KStream<Windowed<String>, Long> {
        println("=== Problem C: Initializing windowed aggregation topology ===")
        println("State store: user-message-counts-windowed-store")
        println("Changelog topic will be created automatically for state restoration")
        val inputStream = getInputStream(streamsBuilder)
        
        val materializedWindow = Materialized
            .`as`<String, Long, WindowStore<Bytes, ByteArray>>("user-message-counts-windowed-store")
            .withKeySerde(Serdes.String())
            .withValueSerde(Serdes.Long())
            .withRetention(Duration.ofSeconds(10))

        val windowedCounts: KTable<Windowed<String>, Long> = inputStream
            .groupBy(
                { oldKey, value -> 
                    val newKey = getSender(value)
                    println("REPARTITION: key changed from '$oldKey' (roomId) to '$newKey' (sender)")
                    newKey
                },
                Grouped.with(REPARTITION_TOPIC_NAME, Serdes.String(), Serdes.String())
            )
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
            .count(materializedWindow)
        
        val windowedCountsStream = windowedCounts.toStream()

        windowedCountsStream
            .peek { windowedKey, count -> 
                val userId = windowedKey.key()
                val window = windowedKey.window()
                println("WINDOW_COUNT user=$userId window=${window.start()}-${window.end()} count=$count")
            }
            .to(
                CHAT_USER_COUNT_WINDOWED_TOPIC,
                Produced.with(
                    org.apache.kafka.streams.kstream.WindowedSerdes.timeWindowedSerdeFrom(String::class.java, Duration.ofSeconds(10).toMillis()),
                    Serdes.Long()
                )
            )

        return windowedCountsStream
    }
}
