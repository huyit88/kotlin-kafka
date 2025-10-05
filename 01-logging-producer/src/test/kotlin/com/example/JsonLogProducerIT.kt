package com.example

import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlinx.serialization.json.Json

import java.util.Properties
import java.time.Duration

import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOPIC = "logs"
private const val LEVEL_HEADER = "level"
private const val DEADLINE_MS = 10_000L
private const val POLL_MS = 500L

class JsonLogProducerIT{
    @Test
    fun sendsAndConsumesThreeJsonLogEvents_byLevelHeaders(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()        

        try{
            val producer = JsonLogProducer(kafka.bootstrapServers)
            val events = listOf(
                logEvent("error", "error message"),
                logEvent("warning", "warning message"),
                logEvent("info", "info message")
            )

            events.forEach { producer.sendLog(it) }
            producer.close()

            val consumeProps = Properties().apply{
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.GROUP_ID_CONFIG, "json-log-consumer-${System.nanoTime()}")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            }
            
            KafkaConsumer<String, String>(consumeProps).use { consumer ->
                consumer.subscribe(listOf(TOPIC))
                val collected = mutableMapOf<String, LogEvent>()
                val deadline = System.currentTimeMillis() + DEADLINE_MS

                while (System.currentTimeMillis() < deadline && collected.size < 3) {
                    val polled = consumer.poll(Duration.ofMillis(POLL_MS))
                    for (rec in polled) {
                        val header = rec.headers().lastHeader(LEVEL_HEADER)
                        val level = header?.value()?.toString(Charsets.UTF_8)
                        checkNotNull(level) { "Missing '$LEVEL_HEADER' header" }
                        val event = Json.decodeFromString<LogEvent>(rec.value())
                        collected[level] = event
                    }
                }
                consumer.commitSync()

                assertEquals(3, collected.size, "Expected 3 events by distinct level")
                assertEquals("warning message", collected["warning"]?.message)
                assertEquals("info message", collected["info"]?.message)
                assertEquals("error message", collected["error"]?.message)
            }

        }finally{
            kafka.stop()
        }
    }

    private fun logEvent(level: String, msg: String) = LogEvent(
        app = "app-2",
        level = level,
        message = msg,
        tsEpochMs = System.currentTimeMillis()
    )
}