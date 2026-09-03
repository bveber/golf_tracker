package com.golftracker.ui.gps

import com.golftracker.data.entity.Club
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Round
import com.golftracker.data.model.ShotType
import com.golftracker.testutil.MainDispatcherRule
import com.golftracker.testutil.fakeCourseRepository
import com.golftracker.testutil.fakeClubRepositoryWithFlow
import com.golftracker.testutil.fakeRoundRepository
import com.golftracker.testutil.fakeStatsRepository
import com.golftracker.testutil.fakeUserPrefsRepository
import com.golftracker.testutil.fakeWeatherRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class GpsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val locationFlow = MutableSharedFlow<LatLng>(replay = 1)
    private val fakeLocationSource = object : LocationSource {
        override fun startUpdates() = locationFlow.asSharedFlow()
    }

    private val driver = Club(id = 1, name = "Driver", type = "DRIVER", stockDistance = 270)
    private val tee = LatLng(33.0, -112.0)
    private val green = LatLng(33.004, -112.0)  // ~500 yards north

    private lateinit var vm: GpsViewModel

    @Before
    fun setup() {
        val (clubRepo, _) = fakeClubRepositoryWithFlow(listOf(driver))

        val round = Round(id = 1, courseId = 1, teeSetId = 1, date = Date())
        val hole = Hole(id = 10, courseId = 1, holeNumber = 1, par = 4,
            teeLat = tee.latitude, teeLng = tee.longitude,
            greenLat = green.latitude, greenLng = green.longitude)
        val holeStat = HoleStat(id = 100, roundId = 1, holeId = 10)

        val roundRepo = fakeRoundRepository(
            round = round,
            holeStats = listOf(holeStat)
        )
        val courseRepo = fakeCourseRepository(holes = listOf(hole))

        vm = GpsViewModel(
            locationSource = fakeLocationSource,
            clubRepository = clubRepo,
            roundRepository = roundRepo,
            courseRepository = courseRepo,
            statsRepository = fakeStatsRepository(),
            userPreferencesRepository = fakeUserPrefsRepository(),
            weatherRepository = fakeWeatherRepository()
        )
    }

    // ── Hole marker reset ────────────────────────────────────────────────

    @Test
    fun `opening mapped hole sets markers to stored tee and green`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()

        assertEquals(tee, vm.uiState.value.playerLocation)
        assertEquals(green, vm.uiState.value.greenAnchor)
        assertEquals(green, vm.uiState.value.flagLocation)
    }

    @Test
    fun `advancing to unmapped hole clears stale flag from previous hole`() = runTest {
        // Build a VM with two holes: hole 10 mapped, hole 11 unmapped
        val hole10 = Hole(id = 10, courseId = 1, holeNumber = 1, par = 4,
            teeLat = tee.latitude, teeLng = tee.longitude,
            greenLat = green.latitude, greenLng = green.longitude)
        val hole11 = Hole(id = 11, courseId = 1, holeNumber = 2, par = 4)
        val stat10 = HoleStat(id = 100, roundId = 1, holeId = 10)
        val stat11 = HoleStat(id = 101, roundId = 1, holeId = 11)
        val round = Round(id = 1, courseId = 1, teeSetId = 1, date = Date())

        val (clubRepo, _) = fakeClubRepositoryWithFlow(listOf(driver))
        val roundRepo = fakeRoundRepository(round = round, holeStats = listOf(stat10, stat11))
        val courseRepo = fakeCourseRepository(holes = listOf(hole10, hole11))
        val testVm = GpsViewModel(fakeLocationSource, clubRepo, roundRepo, courseRepo,
            fakeStatsRepository(), fakeUserPrefsRepository(), fakeWeatherRepository())

        // Open hole 10 (mapped)
        testVm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()
        assertEquals(green, testVm.uiState.value.flagLocation)

        // Advance to hole 11 (unmapped) — flag must NOT stay on hole 10's green
        testVm.setTrackingContext(roundId = 1, holeStatId = 101, holePar = 4)
        advanceUntilIdle()
        assertNotEquals(green, testVm.uiState.value.flagLocation)
        assertNull(testVm.uiState.value.flagLocation)
    }

    // ── GPS auto-follow ──────────────────────────────────────────────────

    @Test
    fun `player marker follows GPS when playerFollowsGps is true`() = runTest {
        vm.onPermissionResult(true)
        val fix1 = LatLng(33.0, -112.0)
        locationFlow.emit(fix1)
        advanceUntilIdle()
        assertEquals(fix1, vm.uiState.value.playerLocation)

        val fix2 = LatLng(33.0005, -112.0)
        locationFlow.emit(fix2)
        advanceUntilIdle()
        assertEquals(fix2, vm.uiState.value.playerLocation)
    }

    @Test
    fun `manual drag pauses GPS follow until next shot is tracked`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        vm.onPermissionResult(true)
        locationFlow.emit(LatLng(33.0, -112.0))
        advanceUntilIdle()

        val dragTarget = LatLng(33.0002, -112.0)
        vm.onPlayerDragged(dragTarget)
        locationFlow.emit(LatLng(33.0005, -112.0))  // GPS update must NOT move marker
        advanceUntilIdle()

        assertEquals(dragTarget, vm.uiState.value.playerLocation)
        assertEquals(false, vm.uiState.value.playerFollowsGps)

        // Track a shot — follow should resume
        vm.onTrackShot()
        val fix3 = LatLng(33.0007, -112.0)
        locationFlow.emit(fix3)
        advanceUntilIdle()
        assertEquals(fix3, vm.uiState.value.playerLocation)
    }

    @Test
    fun `snapToMe resumes GPS follow`() = runTest {
        vm.onPermissionResult(true)
        locationFlow.emit(LatLng(33.0, -112.0))
        advanceUntilIdle()

        vm.onPlayerDragged(LatLng(33.0002, -112.0))
        assertEquals(false, vm.uiState.value.playerFollowsGps)

        vm.snapToMe()
        assertEquals(true, vm.uiState.value.playerFollowsGps)
    }

    // ── greenAnchor vs flagLocation ──────────────────────────────────────

    @Test
    fun `distanceToPin uses greenAnchor not flagLocation after club selection moves flag`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()

        // Select driver: flagLocation moves to ~270y but greenAnchor stays at green (~500y)
        vm.onShotTypeSelected(ShotType.TEE)
        vm.onClubSelected(driver.id)
        advanceUntilIdle()

        // Walk to ~270y from tee (where driver lands)
        val ballLanding = LatLng(33.002, -112.0)
        vm.onPermissionResult(true)
        locationFlow.emit(ballLanding)
        advanceUntilIdle()

        vm.onTrackShot()
        advanceUntilIdle()

        val shot = vm.uiState.value.trackedShots.last()
        // distanceToPin should be distance from ball to green (~230+y), not to moved flag (~0y)
        assertTrue(
            "Expected distanceToPin > 200y (distance to green), got ${shot.distanceToPin}",
            (shot.distanceToPin ?: 0) > 200
        )
    }

    @Test
    fun `flag resets to greenAnchor after tracking a shot`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()

        // Select driver — flag moves away from green
        vm.onShotTypeSelected(ShotType.TEE)
        vm.onClubSelected(driver.id)
        advanceUntilIdle()
        assertNotEquals(green, vm.uiState.value.flagLocation)

        vm.onTrackShot()
        advanceUntilIdle()

        // Flag should snap back to greenAnchor
        assertEquals(green, vm.uiState.value.flagLocation)
    }

    // ── CHIP auto-suggestion ─────────────────────────────────────────────

    @Test
    fun `CHIP auto-suggestion is NOT triggered by proximity to moved flag target`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()
        vm.onPermissionResult(true)

        // Driver selection moves flagLocation to ~270y along bearing to green;
        // greenAnchor stays pinned at the real green (~500y from tee).
        vm.onClubSelected(driver.id)
        advanceUntilIdle()

        // Track the tee shot: advances pendingShotType TEE → APPROACH,
        // resets flagLocation back to greenAnchor, clears userChoseCurrentShotType.
        vm.onTrackShot()
        advanceUntilIdle()

        // Player walks to where ball lands (~270y from tee, same spot the moved flag was).
        // greenAnchor is still at 33.004 — ~220y away — so CHIP must NOT fire.
        locationFlow.emit(LatLng(33.002, -112.0))
        advanceUntilIdle()

        assertEquals(ShotType.APPROACH, vm.uiState.value.pendingShotType)
    }

    @Test
    fun `CHIP triggers when within 40 yards of greenAnchor`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()
        vm.onPermissionResult(true)

        // Track the tee shot first so pendingShotType advances to APPROACH
        // and userChoseCurrentShotType is false (auto-suggest enabled).
        vm.onTrackShot()
        advanceUntilIdle()

        // greenAnchor is at LatLng(33.004, -112.0); 35y south ≈ LatLng(33.00368, -112.0)
        locationFlow.emit(LatLng(33.00368, -112.0))
        advanceUntilIdle()

        assertEquals(ShotType.CHIP, vm.uiState.value.pendingShotType)
    }

    @Test
    fun `user-chosen APPROACH is not overridden by GPS proximity`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()
        vm.onPermissionResult(true)

        // Track the tee shot first to get to APPROACH auto-suggest mode
        vm.onTrackShot()
        advanceUntilIdle()

        // User explicitly picks APPROACH (sets userChoseCurrentShotType = true)
        vm.onShotTypeSelected(ShotType.APPROACH)

        // Walk within 40y of green — must NOT auto-flip to CHIP
        locationFlow.emit(LatLng(33.00368, -112.0))
        advanceUntilIdle()

        assertEquals(ShotType.APPROACH, vm.uiState.value.pendingShotType)
    }

    @Test
    fun `userChoseCurrentShotType resets to false after tracking`() = runTest {
        vm.setTrackingContext(roundId = 1, holeStatId = 100, holePar = 4)
        advanceUntilIdle()

        vm.onShotTypeSelected(ShotType.APPROACH)
        assertTrue(vm.uiState.value.userChoseCurrentShotType)

        vm.onTrackShot()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.userChoseCurrentShotType)
    }
}
