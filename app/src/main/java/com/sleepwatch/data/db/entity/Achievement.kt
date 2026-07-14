package com.sleepwatch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val type: String,  // e.g. "first_early_sleep"
    val unlockedAt: Long? = null,
    val currentProgress: Int = 0
)
