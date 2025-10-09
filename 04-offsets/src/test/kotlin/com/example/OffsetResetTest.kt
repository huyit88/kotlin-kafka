package com.example

import kotlin.test.Test
import kotlin.test.assertTrue
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class OffsetResetTest{
    @Test
    fun `should see no old messages`(){
        val topic = "logs-reset"
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            KafkaUtil.createTopicWithPartition(KafkaUtil.getAdminProps(kafka.bootstrapServers), topic, 3)

            val producerProp = KafkaUtil.getProducerProps(kafka.bootstrapServers)
            KafkaProducer<String, String>(producerProp).use{p->
                repeat(5) { i ->
                    p.send(ProducerRecord(topic, "app $i", "log-$i")).get()
                }
            }

            val consumerProps1 = KafkaUtil.getConsumerProps(
                kafka.bootstrapServers, 
                "group-" + System.nanoTime()
                ).apply{
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            }


            val phase1 = mutableSetOf<Pair<Int, Long>>()
            val phase2 = mutableSetOf<Pair<Int, Long>>()

            // phase 1
            KafkaConsumer<String, String>(consumerProps1).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                c.seekToBeginning(c.assignment())
                val records = c.poll(Duration.ofMillis(1000))
                records.forEach { r -> phase1 += r.partition() to r.offset() }
            }

            // earliest group should see all 5 produced messages
            assertTrue(phase1.size == 5)

            // phase 2
            val consumerProps2 = KafkaUtil.getConsumerProps(
                kafka.bootstrapServers, 
                "group-" + System.nanoTime()
                ).apply{
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
            }
            KafkaConsumer<String, String>(consumerProps2).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                val records = c.poll(Duration.ofMillis(1000))
                records.forEach { r -> phase2 += r.partition() to r.offset() }
            }

            // Observe behavior
            val duplicates = phase1.intersect(phase2)
            println("phase1=${phase1.size} phase2=${phase2.size} duplicates=${duplicates.size} entries=$duplicates")
            assertTrue(phase2.isEmpty())
        }finally{
            kafka.stop()
        }
    }
}