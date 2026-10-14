package com.mahin.hytune.data.local.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HyTuneDatabase {
        return Room.databaseBuilder(
            context,
            HyTuneDatabase::class.java,
            HyTuneDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideProfileDao(database: HyTuneDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideAppRuleDao(database: HyTuneDatabase): AppRuleDao = database.appRuleDao()

    @Provides
    fun provideChargingSessionDao(database: HyTuneDatabase): ChargingSessionDao = database.chargingSessionDao()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("hytune_settings") }
        )
    }
}
