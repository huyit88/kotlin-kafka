package com.example

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import java.util.Properties

@Configuration
class ChatStatelessTopology {
    companion object {
        private const val APPLICATION_ID = "chat-forwarder"
        private const val CHAT_INPUT_TOPIC = "chat-input"
        private const val CHAT_OUTPUT_TOPIC = "chat-output"
        private const val CHAT_SYSTEM_TOPIC = "chat-system"
        private const val CHAT_MODERATION_TOPIC = "chat-moderation"
        private const val SYSTEM_SENDER = "system"

        private val TOXIC_WORDS = listOf("spam", "scam", "hack")
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
    fun chatTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
        val inputStream = streamsBuilder.stream(
            CHAT_INPUT_TOPIC,
            Consumed.with(Serdes.String(), Serdes.String())
        )

        inputStream
            .peek { key, value ->
                println("IN key=$key value=$value")
            }

        val getSender = { value: String -> value.split("|").firstOrNull() ?: "" }
        
        val isToxic = { _: String, value: String ->
            TOXIC_WORDS.any { toxicWord ->
                value.contains(toxicWord, ignoreCase = true)
            }
        }

        val isSystemSender = { _: String, value: String ->
            getSender(value) == SYSTEM_SENDER
        }

        val toxicMessages = inputStream.filter(isToxic)
        val nonToxicMessages = inputStream.filterNot(isToxic)

        val systemMessages = nonToxicMessages.filter(isSystemSender)
        val normalMessages = nonToxicMessages.filterNot(isSystemSender)

        toxicMessages
            .peek { key, value ->
                println("DROP key=$key value=$value reason=toxic")
            }
            .to(CHAT_MODERATION_TOPIC, Produced.with(Serdes.String(), Serdes.String()))

        systemMessages
            .peek { key, value ->
                println("ROUTE_SYSTEM key=$key value=$value")
            }
            .to(CHAT_SYSTEM_TOPIC, Produced.with(Serdes.String(), Serdes.String()))

        normalMessages
            .peek { key, value ->
                println("ROUTE_USER key=$key value=$value")
            }
            .mapValues { it -> "$it|ts=${System.currentTimeMillis()}" }
            .to(CHAT_OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()))
            
        return normalMessages
    }
}