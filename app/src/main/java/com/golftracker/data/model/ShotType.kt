package com.golftracker.data.model

/**
 * Categories of golf shots that can be tracked via the GPS screen.
 *
 * The GPS shot-tracking panel presents [TEE], [APPROACH], and [CHIP]
 * as selectable types. [PUTT] is included for data-model completeness
 * but is tracked separately through the putting UI on the hole-tracking
 * screen rather than through GPS.
 */
enum class ShotType {
    /** Drive or tee shot — recorded on par 4s and par 5s. */
    TEE,
    /** Full approach shot toward the green. */
    APPROACH,
    /** Short game shot played near or around the green. */
    CHIP,
    /** Putt on the green (tracked via the putting UI, not GPS). */
    PUTT
}
