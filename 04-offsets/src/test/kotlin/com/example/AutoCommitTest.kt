package com.example

import kotlin.test.Test
import kotlin.test.assertTrue
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class AutoCommitTest{
    @Test
    fun `offsets increase per partition`(){
        val topic = "logs-auto"
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

            val groupId = "group-" + System.nanoTime()
            val consumerProps = KafkaUtil.getConsumerProps(kafka.bootstrapServers, groupId).apply{
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
                put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 10_000)
            }


            val phase1 = mutableSetOf<Pair<Int, Long>>()
            val phase2 = mutableSetOf<Pair<Int, Long>>()

            // phase 1
            KafkaConsumer<String, String>(consumerProps).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                c.seekToBeginning(c.assignment())
                val records = c.poll(Duration.ofMillis(1000)) // ensure we actually fetch
                records.forEach { r -> phase1 += r.partition() to r.offset() }
            }

            // phase 2
            KafkaConsumer<String, String>(consumerProps).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                c.seekToBeginning(c.assignment())
                val records = c.poll(Duration.ofMillis(1000))
                records.forEach { r -> phase2 += r.partition() to r.offset() }
            }

            // Observe behavior
            val duplicates = phase1.intersect(phase2)
            println("phase1=${phase1.size} phase2=${phase2.size} duplicates=${duplicates.size} entries=$duplicates")
            assertTrue(duplicates.isNotEmpty())
        }finally{
            kafka.stop()
        }
    }
}