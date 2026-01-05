package com.example

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TxController(
    private val avroKafkaTemplate: KafkaTemplate<String, Transaction>
) {
    private val logger = LoggerFactory.getLogger(TxController::class.java)

    @PostMapping("/tx/avro")
    fun submitTxAvro(@RequestBody req: TxRequest) {
        // Convert TxRequest to specific Avro class (type-safe, no GenericRecord)
        val transaction = Transaction.newBuilder()
            .setTransactionId(req.transactionId)
            .setAmount(req.amount)
            .setCurrency(req.currency)
            .build()
        
        avroKafkaTemplate.send("transactions-avro", req.transactionId, transaction)
        logger.info("AVRO_PRODUCED id=${req.transactionId}")
    }
}