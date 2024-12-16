package com.example.labels.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.labels.database.daos.ProductDao
import com.example.labels.database.entitties.Product

@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}