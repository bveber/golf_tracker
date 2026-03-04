package com.golftracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.golftracker.data.db.dao.ClubDao
import com.golftracker.data.db.dao.CourseDao
import com.golftracker.data.db.dao.HoleStatDao
import com.golftracker.data.db.dao.PenaltyDao
import com.golftracker.data.db.dao.PuttDao
import com.golftracker.data.db.dao.RoundDao
import com.golftracker.data.db.dao.ShotDao
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.Penalty
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.Shot
import com.golftracker.data.entity.TeeSet

@Database(
    entities = [
        Course::class,
        TeeSet::class,
        Hole::class,
        HoleTeeYardage::class,
        Club::class,
        Round::class,
        HoleStat::class,
        Putt::class,
        Penalty::class,
        Shot::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GolfDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun clubDao(): ClubDao
    abstract fun roundDao(): RoundDao
    abstract fun holeStatDao(): HoleStatDao
    abstract fun puttDao(): PuttDao
    abstract fun penaltyDao(): PenaltyDao
    abstract fun shotDao(): ShotDao

    companion object {
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN recovery_chip INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_lat REAL")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_lng REAL")
            }
        }
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shots ADD COLUMN distance_traveled INTEGER")
            }
        }
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_mishit INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
