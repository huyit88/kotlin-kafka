package com.example

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.ValueTransformerWithKey
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier
import org.apache.kafka.streams.processor.ProcessorContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import java.util.Properties

@Configuration
class KafkaStreamConfig {

  companion object {
    private const val APPLICATION_ID = "chat-forwarder"
    private const val INPUT_TOPIC = "chat-input"
    private const val OUTPUT_TOPIC = "chat-output"
    private const val MODERATION_TOPIC = "chat-moderation"
    private const val DEFAULT_INSTANCE_ID = "local-1"
    private const val INSTANCE_ID_ENV_VAR = "INSTANCE_ID"
    private const val LOG_PREFIX_IN = "IN"
    private const val LOG_PREFIX_OUT = "OUT"
    
    private val TOXIC_WORDS = listOf("spam", "hack", "scam")
  }

  @Bean
  fun kStreamsProps(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): Properties =
    Properties().apply {
      put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID)
      put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
      put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
      put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
    }

  private fun createLoggingTransformer(
    instanceId: String,
    logPrefix: String
  ): ValueTransformerWithKeySupplier<String, String, String> {
    return ValueTransformerWithKeySupplier { LoggingTransformer(instanceId, logPrefix) }
  }

  private class LoggingTransformer(
    private val instanceId: String,
    private val logPrefix: String
  ) : ValueTransformerWithKey<String, String, String> {
    
    private lateinit var context: ProcessorContext
    
    override fun init(context: ProcessorContext) {
      this.context = context
    }
    
    override fun transform(key: String, value: String): String {
      logRecord(key, value)
      return value
    }
    
    private fun logRecord(key: String, value: String) {
      val partition = context.partition()
      println("instance=$instanceId $logPrefix key=$key partition=$partition")
    }
    
    override fun close() {}
  }

  private fun enrichWithTimestamp(value: String): String {
    val timestamp = System.currentTimeMillis()
    return "$value|ts=$timestamp"
  }

  @Bean
  fun streamsBuilderFactoryBean(kStreamsProps: Properties): StreamsBuilderFactoryBean {
    val factoryBean = StreamsBuilderFactoryBean()
    factoryBean.setStreamsConfiguration(kStreamsProps)
    return factoryBean
  }

  @Bean
  @DependsOn("streamsBuilderFactoryBean")
  fun streamsBuilder(factoryBean: StreamsBuilderFactoryBean): StreamsBuilder {
    return factoryBean.getObject()
  }

  @Bean
  fun chatTopology(streamsBuilder: StreamsBuilder): KStream<String, String> {
    val inputStream = createInputStream(streamsBuilder)
    val instanceId = getInstanceId()
    val loggedInputStream = addInputLogging(inputStream, instanceId)
    val (toxicMessages, normalMessages) = splitIntoToxicAndNormal(loggedInputStream)
    
    forwardNormalMessages(normalMessages, instanceId)
    routeToxicMessages(toxicMessages)
    
    return normalMessages
  }

  private fun createInputStream(streamsBuilder: StreamsBuilder): KStream<String, String> {
    return streamsBuilder.stream(
      INPUT_TOPIC,
      Consumed.with(Serdes.String(), Serdes.String())
    )
  }

  private fun getInstanceId(): String {
    return System.getenv(INSTANCE_ID_ENV_VAR) ?: DEFAULT_INSTANCE_ID
  }

  private fun addInputLogging(
    stream: KStream<String, String>,
    instanceId: String
  ): KStream<String, String> {
    return stream.transformValues(
      createLoggingTransformer(instanceId, LOG_PREFIX_IN)
    )
  }

  private fun isToxicMessage(value: String): Boolean {
    return TOXIC_WORDS.any { toxicWord ->
      value.contains(toxicWord, ignoreCase = true)
    }
  }

  private fun splitIntoToxicAndNormal(
    stream: KStream<String, String>
  ): Pair<KStream<String, String>, KStream<String, String>> {
    val branches = stream.branch(
      { _, value -> isToxicMessage(value) },
      { _, _ -> true }
    )
    return Pair(branches[0], branches[1])
  }

  private fun forwardNormalMessages(
    normalMessages: KStream<String, String>,
    instanceId: String
  ) {
    normalMessages
      .mapValues { value -> enrichWithTimestamp(value) }
      .transformValues(
        createLoggingTransformer(instanceId, LOG_PREFIX_OUT)
      )
      .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()))
  }

  private fun routeToxicMessages(toxicMessages: KStream<String, String>) {
    toxicMessages.to(MODERATION_TOPIC, Produced.with(Serdes.String(), Serdes.String()))
  }
}