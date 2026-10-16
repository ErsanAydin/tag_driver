package com.tagtakip.pro.core.di

import android.content.Context
import androidx.room.Room
import com.tagtakip.pro.core.common.Constants
import com.tagtakip.pro.data.local.dao.EarningDao
import com.tagtakip.pro.data.local.dao.LocationPointDao
import com.tagtakip.pro.data.local.dao.ShiftDao
import com.tagtakip.pro.data.local.dao.TripDao
import com.tagtakip.pro.data.local.db.MIGRATION_1_2
import com.tagtakip.pro.data.local.db.MIGRATION_2_3
import com.tagtakip.pro.data.local.dao.VehicleInfoDao
import com.tagtakip.pro.data.local.db.TagTakipDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): TagTakipDatabase =
        Room.databaseBuilder(context, TagTakipDatabase::class.java, Constants.DATABASE_NAME)
            // Kullanıcı verisi korunarak şema yükseltilir
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideShiftDao(db: TagTakipDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideTripDao(db: TagTakipDatabase): TripDao = db.tripDao()

    @Provides
    fun provideEarningDao(db: TagTakipDatabase): EarningDao = db.earningDao()

    @Provides
    fun provideLocationPointDao(db: TagTakipDatabase): LocationPointDao = db.locationPointDao()

    @Provides
    fun provideVehicleInfoDao(db: TagTakipDatabase): VehicleInfoDao = db.vehicleInfoDao()
}
