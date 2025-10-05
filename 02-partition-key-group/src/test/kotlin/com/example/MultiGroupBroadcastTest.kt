package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Duration
import com.example.testutils.KafkaUtil

class MultiGroupBroadcastTest{
    @Test
    fun `each group should consume all messages`(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()
        try{
            val adminProps = KafkaUtil.getAdminProps(kafka.bootstrapServers)
            KafkaUtil.createTopicWithPartition(adminProps, "logs", 3)

            val producer = PartitionedLogProducer(kafka.bootstrapServers)
            repeat(10){i->
                arrayOf("a", "b", "c").forEach{
                    producer.send("app-$it", "message app-$it $i")
                }
            }
            producer.close()

            val logConsumer = PartitionedLogConsumer(kafka.bootstrapServers)
            val c1 = logConsumer.addConsumer("logs", "group-1")
            val c2 = logConsumer.addConsumer("logs", "group-2")
            var count1 = 0
            var count2 = 0
            for(i in 1..20) {
                val r1 = c1.poll(Duration.ofMillis(100))
                val r2 = c2.poll(Duration.ofMillis(100))
                count1 += r1.count()
                count2 += r2.count()
                println("loop $i $count1 $count2")
                if(count1 + count2 >= 60) break;
            }
            println("count1: $count1")
            println("count2: $count2")
            assertEquals(30, count1, "Expected 60 total across the group")
            assertEquals(30, count2, "Expected 60 total across the group")
            c1.close()
            c2.close()
        }finally{
            kafka.stop()
        }
    }
}