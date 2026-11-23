package dev.opencab.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.connection.DataStoreSavedServerStore
import dev.opencab.core.data.connection.DccClientFactory
import dev.opencab.core.data.connection.DefaultConnectionRepository
import dev.opencab.core.data.connection.DefaultDccClientFactory
import dev.opencab.core.data.connection.NsdServerDiscovery
import dev.opencab.core.data.connection.SavedServerStore
import dev.opencab.core.data.connection.ServerDiscovery
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectionModule {
    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        implementation: DefaultConnectionRepository,
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindSavedServerStore(
        implementation: DataStoreSavedServerStore,
    ): SavedServerStore

    @Binds
    @Singleton
    abstract fun bindServerDiscovery(
        implementation: NsdServerDiscovery,
    ): ServerDiscovery

    @Binds
    @Singleton
    abstract fun bindDccClientFactory(
        implementation: DefaultDccClientFactory,
    ): DccClientFactory
}
