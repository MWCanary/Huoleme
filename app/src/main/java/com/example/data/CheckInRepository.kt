package com.example.data

import kotlinx.coroutines.flow.Flow

class CheckInRepository(private val checkInDao: CheckInDao) {
    val allRecords: Flow<List<CheckInRecord>> = checkInDao.getAllRecords()

    suspend fun getRecordByDate(date: String): CheckInRecord? = checkInDao.getRecordByDate(date)

    suspend fun getLatestRecord(): CheckInRecord? = checkInDao.getLatestRecord()

    suspend fun insertRecord(record: CheckInRecord) = checkInDao.insertRecord(record)

    suspend fun deleteRecordByDate(date: String) = checkInDao.deleteRecordByDate(date)

    suspend fun clearAll() = checkInDao.deleteAll()
}
