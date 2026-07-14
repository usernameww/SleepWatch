package com.sleepwatch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_messages")
data class AlertMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int,       // 1-based
    val title: String,
    val content: String,
    val healthTip: String,
    val isEnabled: Boolean = true
)
