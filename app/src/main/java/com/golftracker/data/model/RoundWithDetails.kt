package com.golftracker.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Round

data class RoundWithDetails(
    @Embedded val round: Round,
    
    @Relation(
        parentColumn = "course_id",
        entityColumn = "id"
    )
    val course: Course,

    @Relation(
        parentColumn = "tee_set_id",
        entityColumn = "id"
    )
    val teeSet: com.golftracker.data.entity.TeeSet,
    
    @Relation(
        entity = HoleStat::class,
        parentColumn = "id",
        entityColumn = "round_id"
    )
    val holeStats: List<HoleStatWithHole>
)

data class HoleStatWithHole(
    @Embedded val holeStat: HoleStat,
    
    @Relation(
        parentColumn = "hole_id",
        entityColumn = "id"
    )
    val hole: Hole,

    @Relation(
        parentColumn = "id",
        entityColumn = "hole_stat_id"
    )
    val putts: List<Putt>,


    @Relation(
        parentColumn = "id",
        entityColumn = "hole_stat_id"
    )
    val penalties: List<com.golftracker.data.entity.Penalty>,

    @Relation(
        parentColumn = "id",
        entityColumn = "hole_stat_id"
    )
    val shots: List<com.golftracker.data.entity.Shot>
)
