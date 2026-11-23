package dev.opencab.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.throttle.ThrottleSession
import dev.opencab.core.data.throttle.ThrottleUiSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThrottleModule {
    @Binds
    @Singleton
    abstract fun bindThrottleUiSource(implementation: ThrottleSession): ThrottleUiSource
}
