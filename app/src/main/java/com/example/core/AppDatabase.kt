package com.example.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.clipboard.ClipboardDao
import com.example.clipboard.ClipboardItemEntity
import com.example.suggestions.PersonalDictionaryDao
import com.example.suggestions.PersonalDictionaryEntity

@Database(
    entities = [ClipboardItemEntity::class, PersonalDictionaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun personalDictionaryDao(): PersonalDictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crazekeyboard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
