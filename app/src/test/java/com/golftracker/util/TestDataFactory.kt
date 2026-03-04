package com.golftracker.util

import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Penalty
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.HoleStatWithHole
import com.golftracker.data.model.RoundWithDetails
import com.golftracker.data.model.ShotOutcome
import java.util.Date

/**
 * Factory for constructing test data objects with sensible defaults.
 * Every parameter can be overridden on a per-test basis.
 */
object TestDataFactory {

    fun course(
        id: Int = 1,
        name: String = "Test Course",
        city: String = "TestCity",
        state: String = "TS",
        holeCount: Int = 18
    ) = Course(id = id, name = name, city = city, state = state, holeCount = holeCount)

    fun teeSet(
        id: Int = 1,
        courseId: Int = 1,
        name: String = "White",
        slope: Int = 113,
        rating: Double = 72.0
    ) = TeeSet(id = id, courseId = courseId, name = name, slope = slope, rating = rating)

    fun hole(
        id: Int = 1,
        courseId: Int = 1,
        holeNumber: Int = 1,
        par: Int = 4,
        handicapIndex: Int? = null
    ) = Hole(id = id, courseId = courseId, holeNumber = holeNumber, par = par, handicapIndex = handicapIndex)

    fun holeStat(
        id: Int = 1,
        roundId: Int = 1,
        holeId: Int = 1,
        teeOutcome: ShotOutcome? = null,
        teeInTrouble: Boolean = false,
        teeClubId: Int? = null,
        teeShotDistance: Int? = null,
        approachOutcome: ShotOutcome? = null,
        approachLie: ApproachLie? = null,
        approachClubId: Int? = null,
        approachShotDistance: Int? = null,
        gir: Boolean = false,
        nearGir: Boolean = false,
        chips: Int = 0,
        sandShots: Int = 0,
        score: Int = 0,
        putts: Int = 0
    ) = HoleStat(
        id = id,
        roundId = roundId,
        holeId = holeId,
        teeOutcome = teeOutcome,
        teeInTrouble = teeInTrouble,
        teeClubId = teeClubId,
        teeShotDistance = teeShotDistance,
        approachOutcome = approachOutcome,
        approachLie = approachLie,
        approachClubId = approachClubId,
        approachShotDistance = approachShotDistance,
        gir = gir,
        nearGir = nearGir,
        chips = chips,
        sandShots = sandShots,
        score = score,
        putts = putts
    )

    fun holeStatWithHole(
        holeStat: HoleStat = holeStat(),
        hole: Hole = hole(id = holeStat.holeId),
        shots: List<com.golftracker.data.entity.Shot> = emptyList(),
        putts: List<com.golftracker.data.entity.Putt> = emptyList(),
        penalties: List<Penalty> = emptyList()
    ) = HoleStatWithHole(holeStat = holeStat, hole = hole, shots = shots, putts = putts, penalties = penalties)

    fun round(
        id: Int = 1,
        courseId: Int = 1,
        teeSetId: Int = 1,
        date: Date = Date(),
        isFinalized: Boolean = true,
        holesPlayed: Int = 18
    ) = Round(id = id, courseId = courseId, teeSetId = teeSetId, date = date, isFinalized = isFinalized, holesPlayed = holesPlayed)

    /**
     * Creates a full RoundWithDetails with 18 holes, each scored at [scorePerHole] with [puttsPerHole] putts.
     * Par is set to [parPerHole] for every hole. Course slope/rating come from [teeSet].
     */
    fun roundWithDetails(
        roundId: Int = 1,
        courseId: Int = 1,
        teeSet: TeeSet = teeSet(),
        date: Date = Date(),
        holesPlayed: Int = 18,
        parPerHole: Int = 4,
        scorePerHole: Int = 4,
        puttsPerHole: Int = 2
    ): RoundWithDetails {
        val r = round(id = roundId, courseId = courseId, teeSetId = teeSet.id, date = date, holesPlayed = holesPlayed)
        val c = course(id = courseId, holeCount = holesPlayed)
        val holes = (1..holesPlayed).map { i ->
            holeStatWithHole(
                holeStat = holeStat(id = i + (roundId * 100), roundId = roundId, holeId = i, score = scorePerHole, putts = puttsPerHole),
                hole = hole(id = i, courseId = courseId, holeNumber = i, par = parPerHole)
            )
        }
        return RoundWithDetails(round = r, course = c, teeSet = teeSet, holeStats = holes)
    }
}
