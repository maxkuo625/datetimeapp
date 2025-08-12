package com.example.datetimeapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FavoriteCity::class], version = 2)
abstract class AppDb : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增 sortOrder 欄位，先給預設 2147483647
                db.execSQL("ALTER TABLE favorite_cities ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 2147483647")
                // 用 createdAt 當作初始排序值，讓舊資料維持原本順序（越舊越後）
                db.execSQL("UPDATE favorite_cities SET sortOrder = createdAt")
            }
        }

        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "datetime_app.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
