package com.example

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class BatchedLogProducer(val bootstrapServers: String){
    private val producer: KafkaProducer<String,String>

    init{
        val props = Properties().apply{
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768) // 32KB
            put(ProducerConfig.LINGER_MS_CONFIG, 20)
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
        }
        producer = KafkaProducer(props)
    }

    fun sendBatch(app: String, lines: List<String>){
        for(line in lines){
            sendLog(app, line)
        }
        producer.flush()
    }

    fun sendLog(app:String, line: String){
        val record = ProducerRecord("logs", app, line)
        producer.send(record)
    }

    fun close(){
        try{
            producer.flush()
        }catch(_: Throwable){}
        try{
            producer.close()
        }catch(_: Throwable){}
    }
}