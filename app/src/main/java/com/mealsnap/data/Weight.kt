package com.mealsnap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One weigh-in, typed by the person or read from Health Connect. */
@Entity(tableName = "weights", indices = [Index("timeMillis")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val kg: Double,
    /** "manual" or "health_connect". */
    val source: String = "manual",
)

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weights ORDER BY timeMillis ASC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights WHERE timeMillis = :timeMillis AND source = :source LIMIT 1")
    suspend fun find(timeMillis: Long, source: String): WeightEntry?
}
