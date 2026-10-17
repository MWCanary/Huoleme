package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_records")
data class CheckInRecord(
    @PrimaryKey
    val date: String, // Date string in "yyyy-MM-dd" format to guarantee one check-in per day
    val timestamp: Long,
    val note: String = "",
    val location: String = "无位置"
)
