package com.drowningpool.androidclient.data.repository

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Репозитории уже помечены @Singleton, Hilt создаст их автоматически
}

