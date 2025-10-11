package com.example

import kotlin.test.Test
import kotlin.test.assertTrue

class DeliverySemanticsSummaryTest{
    @Test
    fun `print comparison table`() {
        val table = """
            | Mode          | Duplicates  | Loss        | Notes                               |
            | ------------- | ----------  | ----------  | ----------------------------------- |
            | at-most-once  | yes         | ✅ possible | commit before process               |
            | at-least-once | ✅ possible | no          | commit after process                |
            | exactly-once  | ❌          | ❌          | idempotent producer or transactions |
        """.trimIndent()
        println(table)
        assertTrue(table.isNotEmpty())
    }
}