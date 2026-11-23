package dev.opencab.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.db.MIGRATION_1_2
import dev.opencab.core.data.db.OpenCabDatabase
import dev.opencab.core.data.db.RosterLocoDao
import dev.opencab.core.data.roster.DefaultRosterRepository
import dev.opencab.core.data.roster.EpochClock
import dev.opencab.core.data.roster.RosterRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): OpenCabDatabase = Room
        .databaseBuilder(
            context,
            OpenCabDatabase::class.java,
            OpenCabDatabase.NAME,
        ).addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    fun provideRosterLocoDao(database: OpenCabDatabase): RosterLocoDao = database.rosterLocoDao()

    @Provides
    @Singleton
    fun provideRosterRepository(
        dao: RosterLocoDao,
        clock: EpochClock,
    ): RosterRepository = DefaultRosterRepository(dao, clock)

    @Provides
    @Singleton
    fun provideEpochClock(): EpochClock = EpochClock { System.currentTimeMillis() }
}
