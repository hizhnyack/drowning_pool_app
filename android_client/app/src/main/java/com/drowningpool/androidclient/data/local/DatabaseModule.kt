package com.drowningpool.androidclient.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "drowning_pool_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideViolationDao(database: AppDatabase): ViolationDao {
        return database.violationDao()
    }
    
    @Provides
    fun provideClientDao(database: AppDatabase): ClientDao {
        return database.clientDao()
    }
    
    @Provides
    fun providePendingResponseDao(database: AppDatabase): PendingResponseDao {
        return database.pendingResponseDao()
    }
}

