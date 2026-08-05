package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HomeTask::class, HomeNote::class, Drawing::class],
    version = 4,
    exportSchema = false
)
abstract class HomeDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao

    companion object {
        @Volatile
        private var INSTANCE: HomeDatabase? = null

        fun getDatabase(context: Context): HomeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HomeDatabase::class.java,
                    "friendly_todo_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
