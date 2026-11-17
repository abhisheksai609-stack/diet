package com.mealsnap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert
    suspend fun insert(meal: Meal): Long

    @Update
    suspend fun update(meal: Meal)

    @Delete
    suspend fun delete(meal: Meal)

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): Meal?

    @Query("SELECT * FROM meals WHERE createdAt >= :startMillis AND createdAt < :endMillis ORDER BY createdAt ASC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<Meal>>

    @Query("SELECT * FROM meals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Meal>>
}
