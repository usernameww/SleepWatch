package com.sleepwatch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,           // yyyy-MM-dd
    val monitorStartTime: Long, // timestamp
    val firstAlertTime: Long? = null,
    val sleepTime: Long? = null,
    val totalAlertCount: Int = 0,
    val screenOnCheckCount: Int = 0,
    val sleepScore: Float? = null,
    val hasEmergency: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
