package com.swarnkary.donezy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "hobby_log.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE hobbies (
                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                name                    TEXT NOT NULL,
                category                TEXT NOT NULL,
                notes                   TEXT NOT NULL,
                next_reminder_at        INTEGER,
                created_at              INTEGER NOT NULL DEFAULT 0,
                is_pinned               INTEGER NOT NULL DEFAULT 0,
                is_archived             INTEGER NOT NULL DEFAULT 0,
                reminder_interval_hours INTEGER NOT NULL DEFAULT 0,
                recurrence_type         TEXT NOT NULL DEFAULT 'none',
                recurrence_data         TEXT NOT NULL DEFAULT '',
                weekly_goal             INTEGER NOT NULL DEFAULT 0,
                sort_order              INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE logs (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                hobby_id   INTEGER NOT NULL,
                entry      TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                rating     INTEGER,
                photo_uri  TEXT,
                FOREIGN KEY(hobby_id) REFERENCES hobbies(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE hobbies ADD COLUMN created_at              INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN is_pinned               INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN is_archived             INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN reminder_interval_hours INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE logs    ADD COLUMN rating                  INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE hobbies ADD COLUMN recurrence_type TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN recurrence_data TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE logs    ADD COLUMN photo_uri       TEXT")
            db.execSQL(
                """
                UPDATE hobbies SET recurrence_type = 'hours',
                                   recurrence_data = CAST(reminder_interval_hours AS TEXT)
                WHERE reminder_interval_hours > 0
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE hobbies ADD COLUMN weekly_goal INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE hobbies ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
    }
}
