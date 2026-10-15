package com.egemenerdem.bookstash.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BookEntity::class, NoteEntity::class], version = 1, exportSchema = false)
abstract class BookStashDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: BookStashDatabase? = null

        fun getDatabase(context: Context): BookStashDatabase {
            // Return the existing instance if it exists, otherwise create it securely
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookStashDatabase::class.java,
                    "bookstash_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}