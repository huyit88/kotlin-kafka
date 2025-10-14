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

    repeat(10){
        val cpuLoad = osBean.systemLoadAverage
        val record = ProducerRecord("cpu-metrics", "host-A", "load=$cpuLoad,time=${System.currentTimeMillis()}")
        producer.send(record)
        println("Sent: $record")
        Thread.sleep(1000)
    }

    producer.close()
}
