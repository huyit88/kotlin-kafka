package com.example

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

import com.example.testutils.KafkaUtil
import java.time.Duration
import java.util.Properties

import kotlin.test.Test
import kotlin.test.assertEquals

class LogProducerIT{

    @Test
    fun `should send and consume 100 log line`(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try {
            val producer = LogProducer(kafka.bootstrapServers)
            repeat(100){
                producer.sendLog("app-1", "log line $it")
            }
            producer.close()

            val consumeProps = Properties().apply{
                put("bootstrap.servers", kafka.bootstrapServers)
                put("group.id", "test-group")
                put("key.deserializer", StringDeserializer::class.java.name)
                put("value.deserializer", StringDeserializer::class.java.name)
                put("auto.offset.reset", "earliest")
                put("enable.auto.commit", "true")
            }

            val consumer = KafkaConsumer<String,String>(consumeProps)
            consumer.subscribe(listOf("logs"))

            var total = 0
            val deadline = System.currentTimeMillis() + 30000
            val logs = mutableListOf<String>()
            while (total < 100 && System.currentTimeMillis() < deadline) {
                val polled = consumer.poll(Duration.ofMillis(500))
                total += polled.count()
            }

            assertEquals(100, total)
            consumer.close()
        } finally {
            kafka.stop()
        }
    }
}