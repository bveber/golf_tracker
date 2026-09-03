package com.golftracker.di

import android.content.Context
import com.golftracker.ui.gps.FusedLocationSource
import com.golftracker.ui.gps.LocationSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideLocationSource(@ApplicationContext context: Context): LocationSource =
        FusedLocationSource(context)
}
