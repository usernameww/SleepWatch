package com.sleepwatch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_records",
    indices = [Index(value = ["date"], unique = true)]
)
data class SleepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val monitorStartTime: Long,
    val firstAlertTime: Long? = null,
    val sleepTime: Long? = null,
    val totalAlertCount: Int = 0,
    val screenOnCheckCount: Int = 0,
    val sleepScore: Float? = null,
    val hasEmergency: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
