package com.forenserecovery.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecoveryItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun recoveryItemDao(): RecoveryItemDao
}
