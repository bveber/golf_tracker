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
    version = 32,
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
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN sand_shot_distance INTEGER")
            }
        }
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN adjusted_yardage INTEGER")
            }
        }
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_slope TEXT")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_stance TEXT")
                database.execSQL("ALTER TABLE shots ADD COLUMN slope TEXT")
                database.execSQL("ALTER TABLE shots ADD COLUMN stance TEXT")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN chip_slope TEXT")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN chip_stance TEXT")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN sand_shot_slope TEXT")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN sand_shot_stance TEXT")
            }
        }
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_dispersion_left INTEGER")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_dispersion_right INTEGER")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_dispersion_short INTEGER")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_dispersion_long INTEGER")
                
                database.execSQL("ALTER TABLE shots ADD COLUMN dispersion_left INTEGER")
                database.execSQL("ALTER TABLE shots ADD COLUMN dispersion_right INTEGER")
                database.execSQL("ALTER TABLE shots ADD COLUMN dispersion_short INTEGER")
                database.execSQL("ALTER TABLE shots ADD COLUMN dispersion_long INTEGER")
            }
        }
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_target_lat REAL")
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN tee_target_lng REAL")
                
                database.execSQL("ALTER TABLE shots ADD COLUMN target_lat REAL")
                database.execSQL("ALTER TABLE shots ADD COLUMN target_lng REAL")
            }
        }
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE holes ADD COLUMN tee_lat REAL")
                database.execSQL("ALTER TABLE holes ADD COLUMN tee_lng REAL")
                database.execSQL("ALTER TABLE holes ADD COLUMN green_lat REAL")
                database.execSQL("ALTER TABLE holes ADD COLUMN green_lng REAL")
            }
        }
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Check if columns exist before adding them to avoid duplicates
                val cursor = database.query("PRAGMA table_info(rounds)")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()

                if (!columns.contains("holes_played")) {
                    database.execSQL("ALTER TABLE rounds ADD COLUMN holes_played INTEGER NOT NULL DEFAULT 18")
                }
                if (!columns.contains("start_hole")) {
                    database.execSQL("ALTER TABLE rounds ADD COLUMN start_hole INTEGER NOT NULL DEFAULT 1")
                }
                if (!columns.contains("notes")) {
                    database.execSQL("ALTER TABLE rounds ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Check if old column exists
                val cursor = database.query("PRAGMA table_info(rounds)")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()

                if (columns.contains("holes_played") && !columns.contains("total_holes")) {
                    database.execSQL("ALTER TABLE rounds RENAME COLUMN holes_played TO total_holes")
                } else if (!columns.contains("total_holes")) {
                    database.execSQL("ALTER TABLE rounds ADD COLUMN total_holes INTEGER NOT NULL DEFAULT 18")
                }
            }
        }
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN is_scored INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shots ADD COLUMN penalty_attribution REAL NOT NULL DEFAULT 0.0")
            }
        }
        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN difficulty_adjustment REAL NOT NULL DEFAULT 0.0")
            }
        }
        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN sg_off_tee_expected REAL")
            }
        }
        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE penalties ADD COLUMN shot_number INTEGER")
            }
        }
        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN approach_mishit INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE shots ADD COLUMN is_mishit INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN score_manual INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE putts ADD COLUMN break_direction TEXT")
                database.execSQL("ALTER TABLE putts ADD COLUMN slope_direction TEXT")
                database.execSQL("ALTER TABLE putts ADD COLUMN pace_miss TEXT")
                database.execSQL("ALTER TABLE putts ADD COLUMN direction_miss TEXT")
            }
        }
        val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE rounds ADD COLUMN is_practice INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(shots)")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()
                if (!columns.contains("start_lat")) database.execSQL("ALTER TABLE shots ADD COLUMN start_lat REAL")
                if (!columns.contains("start_lng")) database.execSQL("ALTER TABLE shots ADD COLUMN start_lng REAL")
                if (!columns.contains("end_lat")) database.execSQL("ALTER TABLE shots ADD COLUMN end_lat REAL")
                if (!columns.contains("end_lng")) database.execSQL("ALTER TABLE shots ADD COLUMN end_lng REAL")
            }
        }
        val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(hole_stats)")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()
                if (!columns.contains("gir_override")) database.execSQL("ALTER TABLE hole_stats ADD COLUMN gir_override INTEGER NOT NULL DEFAULT 0")
                if (!columns.contains("near_gir")) database.execSQL("ALTER TABLE hole_stats ADD COLUMN near_gir INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Weather fields on rounds
                database.execSQL("ALTER TABLE rounds ADD COLUMN weather_condition TEXT")
                database.execSQL("ALTER TABLE rounds ADD COLUMN temperature_fahrenheit INTEGER")
                database.execSQL("ALTER TABLE rounds ADD COLUMN wind_speed_mph INTEGER")
                database.execSQL("ALTER TABLE rounds ADD COLUMN wind_direction TEXT")
                database.execSQL("ALTER TABLE rounds ADD COLUMN humidity_percent INTEGER")
                database.execSQL("ALTER TABLE rounds ADD COLUMN pressure_in_hg REAL")
                // Chip outcome on hole_stats
                database.execSQL("ALTER TABLE hole_stats ADD COLUMN chip_outcome TEXT")
            }
        }
        val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Backfill GPS tee coordinates for seeded Pebble Beach holes
                val coords = listOf(
                    Pair(1,  Pair(36.5682, -121.9472)),
                    Pair(2,  Pair(36.5673, -121.9430)),
                    Pair(3,  Pair(36.5659, -121.9395)),
                    Pair(4,  Pair(36.5650, -121.9413)),
                    Pair(5,  Pair(36.5635, -121.9435)),
                    Pair(6,  Pair(36.5621, -121.9460)),
                    Pair(7,  Pair(36.5618, -121.9497)),
                    Pair(8,  Pair(36.5628, -121.9515)),
                    Pair(9,  Pair(36.5645, -121.9543)),
                    Pair(10, Pair(36.5663, -121.9557)),
                    Pair(11, Pair(36.5682, -121.9541)),
                    Pair(12, Pair(36.5694, -121.9520)),
                    Pair(13, Pair(36.5710, -121.9502)),
                    Pair(14, Pair(36.5723, -121.9485)),
                    Pair(15, Pair(36.5715, -121.9462)),
                    Pair(16, Pair(36.5700, -121.9448)),
                    Pair(17, Pair(36.5690, -121.9467)),
                    Pair(18, Pair(36.5680, -121.9479))
                )
                for ((holeId, latLng) in coords) {
                    database.execSQL(
                        "UPDATE holes SET tee_lat = ${latLng.first}, tee_lng = ${latLng.second} WHERE id = $holeId AND course_id = 1"
                    )
                }
            }
        }
        val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE courses ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE courses ADD COLUMN longitude REAL")
            }
        }
    }
}
