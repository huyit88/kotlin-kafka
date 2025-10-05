package com.example

import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchedLogProducerIT{
    @Test
    fun sendsThousandMessagesUnderTenSeconds(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            val lines = (1..1000).map { "log-line-$it" }
            val producer = BatchedLogProducer(kafka.bootstrapServers)

            val durationMs = measureTimeMillis {
                producer.sendBatch("app-batched", lines)
            }
            producer.close()

            val props = Properties().apply{
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.GROUP_ID_CONFIG, "batched-it-${System.nanoTime()}")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
            val consumer = KafkaConsumer<String,String>(props)
            consumer.subscribe(listOf("logs"))

            var total = 0
            val deadline = System.currentTimeMillis() + 10_000
            while(System.currentTimeMillis() < deadline && total < 1000){
                val records = consumer.poll(Duration.ofMillis(500))
                total += records.count()
            }
            consumer.close()

            assertEquals(1000, total)
            assert(durationMs < 10_000) { "Batch send took ${durationMs}ms, expected < 10000ms" }
        }finally{
            kafka.stop()
        }
    }
}


