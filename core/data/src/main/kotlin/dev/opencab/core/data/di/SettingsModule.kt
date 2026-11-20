package dev.opencab.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.settings.DataStoreSettingsRepository
import dev.opencab.core.data.settings.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository
}
