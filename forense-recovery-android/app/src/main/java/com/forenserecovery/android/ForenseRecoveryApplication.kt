package com.forenserecovery.android

import android.app.Application
import androidx.room.Room
import com.forenserecovery.android.data.local.RecoveryDatabase
import com.forenserecovery.android.data.repository.RecoveryRepositoryImpl
import com.forenserecovery.android.domain.repository.RecoveryRepository

class ForenseRecoveryApplication : Application() {
    lateinit var database: RecoveryDatabase
        private set

    lateinit var recoveryRepository: RecoveryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            RecoveryDatabase::class.java,
            "forense_recovery.db"
        ).fallbackToDestructiveMigration().build()

        recoveryRepository = RecoveryRepositoryImpl(database.recoveryItemDao())
    }
}
