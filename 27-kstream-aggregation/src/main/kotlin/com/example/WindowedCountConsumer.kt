package com.example

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.kstream.WindowedSerdes
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Duration
import java.time.Instant
import java.util.Properties

@Component
class WindowedCountConsumer(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String
) {
    private var consumer: KafkaConsumer<Windowed<String>, Long>? = null
    private var running = true
    private val topic = "chat-user-counts-windowed"
    private val groupId = "windowed-count-consumer"

    @PostConstruct
    fun start() {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, WindowedStringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, LongDeserializer::class.java.name)
        }

        consumer = KafkaConsumer(props)
        consumer?.subscribe(listOf(topic))

        // Start consumer in background thread
        Thread {
            println("=== Windowed Count Consumer Started ===")
            println("Consuming from topic: $topic")
            println("Group ID: $groupId")
            println("Waiting for messages...")
            println()

            try {
                while (running) {
                    val records = consumer?.poll(java.time.Duration.ofMillis(1000))
                    records?.forEach { record ->
                        printWindowedCount(record)
                    }
                }
            } catch (e: Exception) {
                println("Error in consumer: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    private fun printWindowedCount(record: ConsumerRecord<Windowed<String>, Long>) {
        val windowedKey = record.key()
        val count = record.value()
        val userId = windowedKey.key()
        val window = windowedKey.window()
        
        val windowStart = Instant.ofEpochMilli(window.start())
        val windowEnd = Instant.ofEpochMilli(window.end())
        
        println("WINDOWED_COUNT user=$userId window=[${windowStart} - ${windowEnd}] count=$count [partition=${record.partition()}, offset=${record.offset()}]")
    }

    @PreDestroy
    fun stop() {
        running = false
        consumer?.close()
        println("Windowed Count Consumer stopped")
    }
}

/**
 * Custom deserializer for Windowed<String> keys
 * Uses the same WindowedSerdes that was used for serialization
 */
class WindowedStringDeserializer : Deserializer<Windowed<String>> {
    private val windowedSerde = WindowedSerdes.timeWindowedSerdeFrom(
        String::class.java,
        Duration.ofSeconds(10).toMillis()
    )

    override fun deserialize(topic: String?, data: ByteArray?): Windowed<String>? {
        if (data == null) return null
        return windowedSerde.deserializer().deserialize(topic, data)
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
        windowedSerde.deserializer().configure(configs, isKey)
    }

    override fun close() {
        windowedSerde.deserializer().close()
    }
}

