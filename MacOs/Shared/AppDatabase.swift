import Foundation
import SQLite3

/// Thin wrapper over the SQLite C library. Mirrors the Android `AppDatabase`
/// (SQLiteOpenHelper) exactly: same `hobby_log.db` filename, same schema, same
/// version-4 migration ladder, foreign keys on.
///
/// The database lives in the shared App Group container so the widget extension
/// reads the same file the app writes.
final class AppDatabase {

    static let shared = AppDatabase()

    /// SQLITE_TRANSIENT tells SQLite to copy bound text/blob values rather than
    /// assume they outlive the call — required when binding Swift String bytes.
    static let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private static let dbVersion: Int32 = 4

    private(set) var handle: OpaquePointer?

    /// Serialises all access. The C connection is not safe to use from multiple
    /// threads concurrently in the default threading mode, and both the app and
    /// repository hop across queues.
    let queue = DispatchQueue(label: "com.swarnkary.donezy.db")

    private init() {
        open()
    }

    private var dbURL: URL {
        AppGroup.containerURL.appendingPathComponent("hobby_log.db")
    }

    private func open() {
        let path = dbURL.path
        let existed = FileManager.default.fileExists(atPath: path)

        if sqlite3_open(path, &handle) != SQLITE_OK {
            fatalError("Unable to open database at \(path)")
        }

        exec("PRAGMA foreign_keys = ON;")

        // Establish or migrate the schema using SQLite's user_version, which plays
        // the role of SQLiteOpenHelper's onCreate/onUpgrade version tracking.
        let current = userVersion()
        if !existed || current == 0 {
            // Fresh database (or a pre-versioning legacy file with no tables yet).
            if tableExists("hobbies") {
                // Legacy file without user_version set — run the full migration ladder.
                migrate(from: 1, to: AppDatabase.dbVersion)
            } else {
                createSchema()
            }
            setUserVersion(AppDatabase.dbVersion)
        } else if current < AppDatabase.dbVersion {
            migrate(from: current, to: AppDatabase.dbVersion)
            setUserVersion(AppDatabase.dbVersion)
        }
    }

    // ── Schema creation (matches Android onCreate at version 4) ─────────────────

    private func createSchema() {
        exec("""
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
                weekly_goal             INTEGER NOT NULL DEFAULT 0
            )
            """)
        exec("""
            CREATE TABLE logs (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                hobby_id   INTEGER NOT NULL,
                entry      TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                rating     INTEGER,
                photo_uri  TEXT,
                FOREIGN KEY(hobby_id) REFERENCES hobbies(id) ON DELETE CASCADE
            )
            """)
    }

    // ── Migration ladder (matches Android onUpgrade) ────────────────────────────

    private func migrate(from oldVersion: Int32, to newVersion: Int32) {
        if oldVersion < 2 {
            exec("ALTER TABLE hobbies ADD COLUMN created_at              INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE hobbies ADD COLUMN is_pinned               INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE hobbies ADD COLUMN is_archived             INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE hobbies ADD COLUMN reminder_interval_hours INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE logs    ADD COLUMN rating                  INTEGER")
        }
        if oldVersion < 3 {
            exec("ALTER TABLE hobbies ADD COLUMN recurrence_type TEXT NOT NULL DEFAULT 'none'")
            exec("ALTER TABLE hobbies ADD COLUMN recurrence_data TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE logs    ADD COLUMN photo_uri       TEXT")
            exec("""
                UPDATE hobbies SET recurrence_type = 'hours',
                                   recurrence_data = CAST(reminder_interval_hours AS TEXT)
                WHERE reminder_interval_hours > 0
                """)
        }
        if oldVersion < 4 {
            exec("ALTER TABLE hobbies ADD COLUMN weekly_goal INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ── Low-level helpers ───────────────────────────────────────────────────────

    @discardableResult
    func exec(_ sql: String) -> Bool {
        sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK
    }

    private func userVersion() -> Int32 {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, "PRAGMA user_version;", -1, &stmt, nil) == SQLITE_OK else { return 0 }
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_ROW ? sqlite3_column_int(stmt, 0) : 0
    }

    private func setUserVersion(_ v: Int32) {
        exec("PRAGMA user_version = \(v);")
    }

    private func tableExists(_ name: String) -> Bool {
        var stmt: OpaquePointer?
        let sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?;"
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return false }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_text(stmt, 1, name, -1, AppDatabase.SQLITE_TRANSIENT)
        return sqlite3_step(stmt) == SQLITE_ROW
    }
}
