package com.example.hobbylog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "hobby_log.db", null, 2) {

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
                reminder_interval_hours INTEGER NOT NULL DEFAULT 0
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
                FOREIGN KEY(hobby_id) REFERENCES hobbies(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migrate v1 → v2: add new columns to existing tables
            db.execSQL("ALTER TABLE hobbies ADD COLUMN created_at              INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN is_pinned               INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN is_archived             INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE hobbies ADD COLUMN reminder_interval_hours INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE logs    ADD COLUMN rating                  INTEGER")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
