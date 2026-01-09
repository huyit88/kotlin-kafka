package com.example

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/benchmark")
class BenchmarkController(
    private val fastProducer: FastProducer,
    private val safeProducer: SafeProducer,
    private val idempotentProducer: IdempotentProducer
){
    @PostMapping("/fast")
    fun produceFast(@RequestParam n: Int){
        fastProducer.sendMessage(n)
    }

    @PostMapping("/safe")
    fun produceSafe(@RequestParam n: Int){
        safeProducer.sendMessage(n)
    }

    @PostMapping("/idempotent")
    fun produceIdempotent(@RequestParam n: Int){
        idempotentProducer.sendMessage(n)
    }
}