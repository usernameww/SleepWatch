package com.sleepwatch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_records",
    indices = [Index(value = ["monitorStartTime"], unique = true)]
)
data class SleepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val monitorStartTime: Long,
    val monitorEndTime: Long? = null,
    val targetBedtimeTime: Long? = null,
    val firstAlertTime: Long? = null,
    val sleepTime: Long? = null,
    val firstInactiveCheckTime: Long? = null,
    val consecutiveInactiveChecks: Int = 0,
    val totalCheckCount: Int = 0,
    val activeCheckCount: Int = 0,
    val lastCheckTime: Long? = null,
    val nextCheckTime: Long? = null,
    val checkIntervalMinutes: Int = 10,
    val inactiveThreshold: Int = 3,
    val totalAlertCount: Int = 0,
    val sleepScore: Float? = null,
    val status: String = "INCOMPLETE",
    val createdAt: Long
)
