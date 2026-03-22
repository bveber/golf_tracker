package com.golftracker.di

import android.content.Context
import androidx.room.Room
import com.golftracker.data.db.GolfDatabase
import com.golftracker.data.db.SeedDataCallback
import com.golftracker.data.db.dao.ClubDao
import com.golftracker.data.db.dao.CourseDao
import com.golftracker.data.db.dao.HoleStatDao
import com.golftracker.data.db.dao.PenaltyDao
import com.golftracker.data.db.dao.PuttDao
import com.golftracker.data.db.dao.RoundDao
import com.golftracker.data.db.dao.ShotDao
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
    fun provideGolfDatabase(@ApplicationContext context: Context): GolfDatabase {
        return Room.databaseBuilder(
            context,
            GolfDatabase::class.java,
            "golf_database"
        ).addMigrations(
            GolfDatabase.MIGRATION_6_7, 
            GolfDatabase.MIGRATION_7_8, 
            GolfDatabase.MIGRATION_8_9, 
            GolfDatabase.MIGRATION_9_10,
            GolfDatabase.MIGRATION_10_11,
            GolfDatabase.MIGRATION_11_12,
            GolfDatabase.MIGRATION_12_13,
            GolfDatabase.MIGRATION_13_14,
            GolfDatabase.MIGRATION_14_15,
            GolfDatabase.MIGRATION_15_16,
            GolfDatabase.MIGRATION_16_17,
            GolfDatabase.MIGRATION_17_18,
            GolfDatabase.MIGRATION_18_19,
            GolfDatabase.MIGRATION_19_20,
            GolfDatabase.MIGRATION_20_21,
            GolfDatabase.MIGRATION_21_22,
            GolfDatabase.MIGRATION_22_23,
            GolfDatabase.MIGRATION_23_24,
            GolfDatabase.MIGRATION_24_25
        )
         .fallbackToDestructiveMigration() // For MVP simplicity
         .addCallback(SeedDataCallback(context))
         .build()
    }

    @Provides
    @Singleton
    fun provideCourseDao(database: GolfDatabase): CourseDao {
        return database.courseDao()
    }

    @Provides
    @Singleton
    fun provideClubDao(database: GolfDatabase): ClubDao {
        return database.clubDao()
    }

    @Provides
    @Singleton
    fun provideRoundDao(database: GolfDatabase): RoundDao {
        return database.roundDao()
    }

    @Provides
    @Singleton
    fun provideHoleStatDao(database: GolfDatabase): HoleStatDao {
        return database.holeStatDao()
    }

    @Provides
    @Singleton
    fun providePuttDao(database: GolfDatabase): PuttDao {
        return database.puttDao()
    }

    @Provides
    @Singleton
    fun providePenaltyDao(database: GolfDatabase): PenaltyDao {
        return database.penaltyDao()
    }

    @Provides
    @Singleton
    fun provideShotDao(database: GolfDatabase): ShotDao {
        return database.shotDao()
    }
}
