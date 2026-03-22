package com.golftracker

import android.app.Application
import com.golftracker.domain.SgRecalculationUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GolfTrackerApp : Application() {

    @Inject
    lateinit var sgRecalculationUseCase: SgRecalculationUseCase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // On each new app version, recalculate SG for all finalized rounds so users
        // always see up-to-date values when reviewing historical rounds.
        applicationScope.launch {
            sgRecalculationUseCase.runIfNeeded()
        }
    }
}

