package com.example

import kotlin.test.Test
import kotlin.test.assertTrue
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class AtMostOnceDeliveryTest{
    @Test
    fun `might lost few messages`(){
        val topic = "logs-atmost"
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            KafkaUtil.createTopicWithPartition(KafkaUtil.getAdminProps(kafka.bootstrapServers), topic, 3)

            val producerProp = KafkaUtil.getProducerProps(kafka.bootstrapServers)
            KafkaProducer<String, String>(producerProp).use{p->
                repeat(10) { i ->
                    p.send(ProducerRecord(topic, "app $i", "log-$i")).get()
                }
            }

            val groupId = "group-" + System.nanoTime()
            val consumerProps = KafkaUtil.getConsumerProps(kafka.bootstrapServers, groupId).apply{
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            }

            val phase1 = mutableSetOf<Pair<Int, Long>>()
            KafkaConsumer<String, String>(consumerProps).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                c.seekToBeginning(c.assignment())
                val records = c.poll(Duration.ofMillis(500)) // ensure we actually fetch
                var committedBeforeProcessing = 0
                for (r in records) {
                    // Commit offset BEFORE processing to simulate at-most-once
                    c.commitSync(
                        mapOf(TopicPartition(r.topic(), r.partition()) to OffsetAndMetadata(r.offset() + 1))
                    )
                    committedBeforeProcessing++
                    // Simulate crash immediately after commit and BEFORE processing
                    if (committedBeforeProcessing >= 2) break
                    // If we were to process, we'd record it here
                    phase1 += r.partition() to r.offset()
                }
            }

            val phase2 = mutableSetOf<Pair<Int, Long>>()
            KafkaConsumer<String, String>(consumerProps).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                val records = c.poll(Duration.ofMillis(500))
                records.forEach { r -> phase2 += r.partition() to r.offset() }
            }

            // Observe behavior
            val duplicates = phase1.intersect(phase2)
            println("phase1=${phase1.size} phase2=${phase2.size} duplicates=${duplicates.size} entries=$duplicates")
            assertTrue((phase1.size + phase2.size) < 10)
        }finally{
            kafka.stop()
        }
    }
}