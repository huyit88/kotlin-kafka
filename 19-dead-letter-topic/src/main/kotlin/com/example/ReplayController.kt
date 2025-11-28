package com.example

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity

@RestController
class ReplayController(
    private val dltReplayService: DltReplayService
) {
    @PostMapping("/dlt/replay")
    fun replay(): ResponseEntity<String> {
        val count = dltReplayService.replay()
        return ResponseEntity.ok("Replayed $count message(s) from DLT")
    }
}