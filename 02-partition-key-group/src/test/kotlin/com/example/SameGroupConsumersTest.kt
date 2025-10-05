package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.testutils.KafkaUtil
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Callable

class SameGroupConsumersTest{
    @Test
    fun `should consume all expected logs in same group`(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            val adminProps = KafkaUtil.getAdminProps(kafka.bootstrapServers)
            KafkaUtil.createTopicWithPartition(adminProps, "logs", 3)
            
            val partitionedLogConsumer = PartitionedLogConsumer(kafka.bootstrapServers)
            val c1 = partitionedLogConsumer.addConsumer("logs", "partition-group")
            val c2 = partitionedLogConsumer.addConsumer("logs", "partition-group")

            val (a1, a2) = partitionedLogConsumer.waitForStableAssignments(c1, c2, "logs", expectedPartitions = 3)
            println("C1 assignment: $a1")
            println("C2 assignment: $a2")

            val producer = PartitionedLogProducer(kafka.bootstrapServers)

            repeat(10){i->
                arrayOf("a", "b", "c", "d", "e", "f").forEach{c->
                    producer.send("$c-app", "message app-$c $i")
                }                
            }
            producer.close()

            var total = 0
            var count1 = 0
            var count2 = 0
            val seen = mutableSetOf<String>()

            for(i in 1..20) {
                val r1 = c1.poll(Duration.ofMillis(100))
                val r2 = c2.poll(Duration.ofMillis(100))
                count1 += r1.count()
                count2 += r2.count()
                println("loop $i $count1 $count2")
                (r1 + r2).forEach { rec ->
                    val id = "${rec.partition()}@${rec.offset()}"
                    if (!seen.add(id)) error("Duplicate $id")
                    total++
                }
                if (total >= 60) break
            }
            println("count1: $count1")
            println("count2: $count2")
            assertEquals(60, total, "Expected 60 total across the group")
            c1.close()
            c2.close()
        }finally{
            kafka.stop()
        }    
    }
}