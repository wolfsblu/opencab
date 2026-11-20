package dev.opencab.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.db.OpenCabDatabase
import dev.opencab.core.data.db.SavedLocoDao
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
        ).build()

    @Provides
    fun provideSavedLocoDao(database: OpenCabDatabase): SavedLocoDao = database.savedLocoDao()
}
