package com.example

import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.management.ManagementFactory
import java.util.*

fun main(){
    val bootstrap = "localhost:19092"
    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }

    val producer = KafkaProducer<String,String>(props)
    val osBean = ManagementFactory.getOperatingSystemMXBean()

    repeat(10){ i ->
        val cpuLoad = osBean.systemLoadAverage
        val key = if (i < 5) "host-A" else "host-B"
        val value = "ts=${System.currentTimeMillis()},load=$cpuLoad"
        val record = ProducerRecord("cpu-metrics-2", key, value)
        val metadata = producer.send(record).get()
        println("sent key=$key partition=${metadata.partition()} offset=${metadata.offset()} value=$value")
        Thread.sleep(200)
    }

    producer.close()
}
