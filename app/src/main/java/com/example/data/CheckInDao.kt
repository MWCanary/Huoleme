package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_in_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<CheckInRecord>>

    @Query("SELECT * FROM check_in_records WHERE date = :date LIMIT 1")
    suspend fun getRecordByDate(date: String): CheckInRecord?

    @Query("SELECT * FROM check_in_records ORDER BY date DESC LIMIT 1")
    suspend fun getLatestRecord(): CheckInRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CheckInRecord)

    @Query("DELETE FROM check_in_records WHERE date = :date")
    suspend fun deleteRecordByDate(date: String)

    @Query("DELETE FROM check_in_records")
    suspend fun deleteAll()
}
