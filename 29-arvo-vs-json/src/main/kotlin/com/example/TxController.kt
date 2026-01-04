package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TxController(
    private val jsonKafkaTemplate: KafkaTemplate<String, String>,
    private val avroKafkaTemplate: KafkaTemplate<String, GenericRecord>,
    private val objectMapper: ObjectMapper,
    private val transactionSchema: Schema
) {
    private val logger = LoggerFactory.getLogger(TxController::class.java)

    @PostMapping("/tx/json")
    fun submitTxJson(@RequestBody req: TxRequest) {
        val jsonString = objectMapper.writeValueAsString(req)
        jsonKafkaTemplate.send("transactions-json", req.transactionId, jsonString)
    }

    @PostMapping("/tx/avro")
    fun submitTxAvro(@RequestBody req: TxRequest) {
        // ✅ Schema evolution: merchantId is optional, so v1 records (null) and v2 records (present) both work
        val avroRecord: GenericRecord = GenericData.Record(transactionSchema).apply {
            put("transactionId", req.transactionId)
            put("amount", req.amount)
            put("currency", req.currency)
            put("merchantId", req.merchantId)  // Can be null for v1 compatibility
        }
        avroKafkaTemplate.send("transactions-avro", req.transactionId, avroRecord)
        logger.info("AVRO_TX produced id=${req.transactionId} merchantId=${req.merchantId ?: "null"}")
    }
}