package com.drowningpool.androidclient.utils

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {
    // PreferencesManager и NotificationHelper уже помечены @Singleton, Hilt создаст их автоматически
}

