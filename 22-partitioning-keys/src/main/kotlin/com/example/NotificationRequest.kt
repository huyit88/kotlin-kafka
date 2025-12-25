package com.example

data class NotificationRequest(
    val userId: String? = null,
    val seq: Int,
    val channel: String,
    val message: String
)