package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.kafka.core.KafkaTemplate

@RestController
@RequestMapping
class NotificationController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${kafka.topic.notifications}") private val notificationsTopic: String,
    private val lagMetricsService: LagMetricsService
) {
    @PostMapping("/notify")
    fun notify(@RequestBody req: NotificationRequest) {
        val value = "${req.userId},${req.seq},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, req.userId, value)
    }

    @PostMapping("/notify/hot")
    fun notifyHot(@RequestBody req: NotificationRequest) {
        val value = "HOT,${req.seq},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, "HOT", value)
    }

    @GetMapping("/debug/lag-explanation")
    fun lagExplanation(): Map<String, Any> {
        val metrics = lagMetricsService.getLagMetrics()
        
        val explanation = """
            Max partition lag is often more important than total lag because:
            
            1. **Bottleneck Detection**: A single partition with high lag indicates a bottleneck 
               (e.g., hot partition, slow consumer processing that partition). Total lag can 
               hide this - you might have low total lag but one partition severely behind.
            
            2. **Consumer Parallelism**: Kafka consumers process partitions in parallel. If one 
               partition has high lag, it becomes the bottleneck. Other partitions may be 
               caught up, but the slow partition limits overall progress.
            
            3. **Ordering Guarantees**: For keyed messages, all messages for a key go to the same 
               partition. High lag on one partition means some users/entities experience delays, 
               even if total lag looks acceptable.
            
            4. **Alerting**: Production systems typically alert on max partition lag, not total lag, 
               because it identifies the actual problem. Example: Total lag = 1000 (distributed), 
               vs Max partition lag = 950 (one partition) - the latter is more concerning.
            
            5. **Scaling Decisions**: If max partition lag is high, adding more consumers won't help 
               if that partition is already assigned. You need to address the root cause (hot key, 
               slow processing, etc.).
        """.trimIndent()
        
        return mapOf(
            "totalLag" to metrics.totalLag,
            "maxPartitionLag" to metrics.maxPartitionLag,
            "whyMaxMatters" to explanation
        )
    }
}
