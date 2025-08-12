package com.example.datetimeapp

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_cities",
    indices = [Index(value = ["zoneId"], unique = true)] // 同一個時區避免重複
)
data class FavoriteCity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val zoneId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = Int.MAX_VALUE
)
