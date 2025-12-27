package com.example

data class NotificationRequest(
    val userId: String,
    val channel: String,
    val message: String 
)