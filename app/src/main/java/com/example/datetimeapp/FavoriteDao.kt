package com.example.datetimeapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_cities ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteCity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FavoriteCity): Long

    @Delete
    suspend fun delete(item: FavoriteCity)

    @Update
    suspend fun update(item: FavoriteCity)

    @Update
    suspend fun updateAll(items: List<FavoriteCity>)  // 一次更新多筆

    @Query("SELECT * FROM favorite_cities WHERE zoneId = :zone LIMIT 1")
    suspend fun findByZone(zone: String): FavoriteCity?
}
