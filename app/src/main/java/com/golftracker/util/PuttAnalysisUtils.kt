package com.golftracker.util

import com.golftracker.data.entity.DirectionMiss
import com.golftracker.data.entity.PuttBreak
import com.golftracker.data.entity.SlideMiss

/**
 * Infers whether a missed putt went to the high side (pro side) or low side (amateur side)
 * based on the break direction and the direction miss.
 *
 * Returns null when inference is not applicable:
 * - Either input is null
 * - Break is STRAIGHT (no side for high/low)
 * - Direction miss is STRAIGHT (ball started on line — lip-out, bad bounce, or misread break)
 */
fun inferSlideMiss(break_: PuttBreak?, direction: DirectionMiss?): SlideMiss? {
    if (break_ == null || direction == null) return null
    if (break_ == PuttBreak.STRAIGHT) return null
    if (direction == DirectionMiss.STRAIGHT) return null

    val breakIsRight = break_ == PuttBreak.SMALL_RIGHT || break_ == PuttBreak.BIG_RIGHT
    val missIsRight = direction == DirectionMiss.RIGHT || direction == DirectionMiss.BIG_RIGHT
    return when {
        breakIsRight && !missIsRight -> SlideMiss.HIGH   // stayed above right-breaking putt
        breakIsRight && missIsRight -> SlideMiss.LOW     // went with the right break
        !breakIsRight && missIsRight -> SlideMiss.HIGH   // stayed above left-breaking putt
        !breakIsRight && !missIsRight -> SlideMiss.LOW   // went with the left break
        else -> null
    }
}
