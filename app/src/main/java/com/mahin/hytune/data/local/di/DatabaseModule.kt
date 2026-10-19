package com.mahin.hytune.data.local.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.BenchmarkSessionDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.dao.ThermalThrottleDao
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
        )
            .addMigrations(
                HyTuneDatabase.MIGRATION_1_2,
                HyTuneDatabase.MIGRATION_2_3,
                HyTuneDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(database: HyTuneDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideAppRuleDao(database: HyTuneDatabase): AppRuleDao = database.appRuleDao()

    @Provides
    fun provideChargingSessionDao(database: HyTuneDatabase): ChargingSessionDao = database.chargingSessionDao()

    @Provides
    fun provideFreezeGroupDao(database: HyTuneDatabase): FreezeGroupDao = database.freezeGroupDao()

    @Provides
    fun provideThermalThrottleDao(database: HyTuneDatabase): ThermalThrottleDao = database.thermalThrottleDao()

    @Provides
    fun provideBenchmarkSessionDao(database: HyTuneDatabase): BenchmarkSessionDao = database.benchmarkSessionDao()

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
