package com.ahdi90.app.data

data class DailyTask(
    val id: Int,
    val emoji: String,
    val title: String,
    val isDone: Boolean = false,
    val alarmTime: String? = null
)
