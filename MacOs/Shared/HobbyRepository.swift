import Foundation
import SQLite3

/// Port of the Android `HobbyRepository`. Same public surface, in order:
///  - reactive lists: hobbies, archivedHobbies, logDaysByHobby, logSearchResults
///  - tracker CRUD (add/update/delete/togglePin/archive/restore/updateReminder)
///  - log CRUD (addLog/deleteLog/restoreLog)
///  - synchronous helpers used from the widget / notification handlers
///
/// All SQLite work hops onto `db.queue` so the connection is touched from one
/// thread at a time; the `@Published` lists are pushed back on the main queue.
final class HobbyRepository: ObservableObject {

    static let shared = HobbyRepository()

    private let db = AppDatabase.shared
    private var handle: OpaquePointer? { db.handle }

    @Published private(set) var hobbies: [Hobby] = []
    @Published private(set) var archivedHobbies: [Hobby] = []
    /// hobbyId → list of distinct epoch-millis at local midnight where a log exists.
    @Published private(set) var logDaysByHobby: [Int64: [Int64]] = [:]

    private init() {}

    // ── Refresh ─────────────────────────────────────────────────────────────────

    func refresh() {
        db.queue.async { self.refreshSyncLocked() }
    }

    /// Runs the three list queries and publishes. Call only on `db.queue`.
    private func refreshSyncLocked() {
        let active = queryHobbies(archived: false)
        let archived = queryHobbies(archived: true)
        let days = queryLogDays()
        DispatchQueue.main.async {
            self.hobbies = active
            self.archivedHobbies = archived
            self.logDaysByHobby = days
        }
    }

    /// Returns one row per (hobby, local day) so streaks can be computed in O(D)
    /// per tracker. SQLite's strftime handles the UTC→local conversion.
    private func queryLogDays() -> [Int64: [Int64]] {
        var map: [Int64: [Int64]] = [:]
        let sql = """
            SELECT hobby_id,
                   CAST(strftime('%s', created_at / 1000, 'unixepoch', 'localtime', 'start of day') AS INTEGER) * 1000 AS day_ms
            FROM logs
            GROUP BY hobby_id, day_ms
            ORDER BY hobby_id ASC, day_ms DESC
            """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return map }
        defer { sqlite3_finalize(stmt) }
        while sqlite3_step(stmt) == SQLITE_ROW {
            let hid = sqlite3_column_int64(stmt, 0)
            let day = sqlite3_column_int64(stmt, 1)
            map[hid, default: []].append(day)
        }
        return map
    }

    // ── Hobby queries ─────────────────────────────────────────────────────────

    private static let hobbyColumns = """
        id, name, category, notes, next_reminder_at,
        created_at, is_pinned, is_archived, reminder_interval_hours,
        recurrence_type, recurrence_data, weekly_goal
        """

    private func queryHobbies(archived: Bool) -> [Hobby] {
        let sql = """
            SELECT \(HobbyRepository.hobbyColumns)
            FROM hobbies
            WHERE is_archived = ?
            ORDER BY is_pinned DESC, next_reminder_at IS NULL, next_reminder_at ASC
            """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int(stmt, 1, archived ? 1 : 0)
        var out: [Hobby] = []
        while sqlite3_step(stmt) == SQLITE_ROW { out.append(hobbyFrom(stmt)) }
        return out
    }

    private func hobbyFrom(_ stmt: OpaquePointer?) -> Hobby {
        Hobby(
            id: sqlite3_column_int64(stmt, 0),
            name: columnText(stmt, 1),
            category: columnText(stmt, 2),
            notes: columnText(stmt, 3),
            nextReminderAt: columnInt64OrNil(stmt, 4),
            createdAt: sqlite3_column_int64(stmt, 5),
            isPinned: sqlite3_column_int(stmt, 6) != 0,
            isArchived: sqlite3_column_int(stmt, 7) != 0,
            reminderIntervalHours: sqlite3_column_int64(stmt, 8),
            recurrence: Recurrence.decode(type: columnText(stmt, 9), data: columnText(stmt, 10)),
            weeklyGoal: Int(sqlite3_column_int(stmt, 11))
        )
    }

    @discardableResult
    func addHobby(name: String, category: String, notes: String,
                  nextReminderAt: Int64?, recurrence: Recurrence, weeklyGoal: Int = 0) -> Int64 {
        db.queue.sync {
            let (type, data) = recurrence.encode()
            let intervalHours: Int64 = { if case .hourly(let h) = recurrence { return h } else { return 0 } }()
            let sql = """
                INSERT INTO hobbies
                (name, category, notes, next_reminder_at, created_at, is_pinned, is_archived,
                 reminder_interval_hours, recurrence_type, recurrence_data, weekly_goal)
                VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)
                """
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, name.trimmingCharacters(in: .whitespacesAndNewlines))
            bindText(stmt, 2, category.trimmingCharacters(in: .whitespacesAndNewlines))
            bindText(stmt, 3, notes.trimmingCharacters(in: .whitespacesAndNewlines))
            bindInt64OrNull(stmt, 4, nextReminderAt)
            sqlite3_bind_int64(stmt, 5, nowMillis())
            sqlite3_bind_int64(stmt, 6, intervalHours)
            bindText(stmt, 7, type)
            bindText(stmt, 8, data)
            sqlite3_bind_int(stmt, 9, Int32(max(0, weeklyGoal)))
            sqlite3_step(stmt)
            let id = sqlite3_last_insert_rowid(handle)
            refreshSyncLocked()
            return id
        }
    }

    func updateHobby(id: Int64, name: String, category: String, notes: String, weeklyGoal: Int) {
        db.queue.sync {
            let sql = "UPDATE hobbies SET name = ?, category = ?, notes = ?, weekly_goal = ? WHERE id = ?"
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, name.trimmingCharacters(in: .whitespacesAndNewlines))
            bindText(stmt, 2, category.trimmingCharacters(in: .whitespacesAndNewlines))
            bindText(stmt, 3, notes.trimmingCharacters(in: .whitespacesAndNewlines))
            sqlite3_bind_int(stmt, 4, Int32(max(0, weeklyGoal)))
            sqlite3_bind_int64(stmt, 5, id)
            sqlite3_step(stmt)
            refreshSyncLocked()
        }
    }

    func deleteHobby(id: Int64) {
        db.queue.sync {
            execBind("DELETE FROM hobbies WHERE id = ?", id)
            refreshSyncLocked()
        }
    }

    /// Snapshot a hobby with all its logs, for undo-after-delete.
    func snapshotHobby(id: Int64) -> HobbyDetail? { detail(id) }

    /// Re-insert a deleted hobby and its logs, preserving IDs so outstanding
    /// references (widget, reminders) line back up.
    func restoreHobbySnapshot(_ snapshot: HobbyDetail) {
        db.queue.sync {
            exec("BEGIN")
            let h = snapshot.hobby
            let (type, data) = h.recurrence.encode()
            let hSql = """
                INSERT OR REPLACE INTO hobbies
                (id, name, category, notes, next_reminder_at, created_at, is_pinned, is_archived,
                 reminder_interval_hours, recurrence_type, recurrence_data, weekly_goal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            var hStmt: OpaquePointer?
            sqlite3_prepare_v2(handle, hSql, -1, &hStmt, nil)
            sqlite3_bind_int64(hStmt, 1, h.id)
            bindText(hStmt, 2, h.name)
            bindText(hStmt, 3, h.category)
            bindText(hStmt, 4, h.notes)
            bindInt64OrNull(hStmt, 5, h.nextReminderAt)
            sqlite3_bind_int64(hStmt, 6, h.createdAt)
            sqlite3_bind_int(hStmt, 7, h.isPinned ? 1 : 0)
            sqlite3_bind_int(hStmt, 8, h.isArchived ? 1 : 0)
            sqlite3_bind_int64(hStmt, 9, h.reminderIntervalHours)
            bindText(hStmt, 10, type)
            bindText(hStmt, 11, data)
            sqlite3_bind_int(hStmt, 12, Int32(h.weeklyGoal))
            sqlite3_step(hStmt)
            sqlite3_finalize(hStmt)

            for l in snapshot.logs { insertLogLocked(l) }
            exec("COMMIT")
            refreshSyncLocked()
        }
    }

    func togglePin(id: Int64, current: Bool) {
        db.queue.sync {
            execBind("UPDATE hobbies SET is_pinned = \(current ? 0 : 1) WHERE id = ?", id)
            refreshSyncLocked()
        }
    }

    func archiveHobby(id: Int64) {
        db.queue.sync {
            execBind("UPDATE hobbies SET is_archived = 1 WHERE id = ?", id)
            refreshSyncLocked()
        }
    }

    func restoreHobby(id: Int64) {
        db.queue.sync {
            execBind("UPDATE hobbies SET is_archived = 0 WHERE id = ?", id)
            refreshSyncLocked()
        }
    }

    func updateReminder(hobbyId: Int64, nextReminderAt: Int64?, recurrence: Recurrence) {
        db.queue.sync {
            let (type, data) = recurrence.encode()
            let intervalHours: Int64 = { if case .hourly(let h) = recurrence { return h } else { return 0 } }()
            let sql = """
                UPDATE hobbies SET next_reminder_at = ?, reminder_interval_hours = ?,
                                   recurrence_type = ?, recurrence_data = ?
                WHERE id = ?
                """
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
            defer { sqlite3_finalize(stmt) }
            bindInt64OrNull(stmt, 1, nextReminderAt)
            sqlite3_bind_int64(stmt, 2, intervalHours)
            bindText(stmt, 3, type)
            bindText(stmt, 4, data)
            sqlite3_bind_int64(stmt, 5, hobbyId)
            sqlite3_step(stmt)
            refreshSyncLocked()
        }
    }

    // ── Log queries ───────────────────────────────────────────────────────────

    @discardableResult
    func addLog(hobbyId: Int64, entry: String, rating: Int?, photoUri: String?) -> Int64 {
        db.queue.sync {
            let sql = "INSERT INTO logs (hobby_id, entry, created_at, rating, photo_uri) VALUES (?, ?, ?, ?, ?)"
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, hobbyId)
            bindText(stmt, 2, entry.trimmingCharacters(in: .whitespacesAndNewlines))
            sqlite3_bind_int64(stmt, 3, nowMillis())
            bindIntOrNull(stmt, 4, rating)
            bindTextOrNull(stmt, 5, photoUri)
            sqlite3_step(stmt)
            return sqlite3_last_insert_rowid(handle)
        }
    }

    @discardableResult
    func insertLog(_ log: HobbyLog) -> Int64 {
        db.queue.sync {
            let id = insertLogLocked(log)
            return id
        }
    }

    /// Inserts a log row preserving its id. Call only on `db.queue`.
    @discardableResult
    private func insertLogLocked(_ log: HobbyLog) -> Int64 {
        let sql = "INSERT OR REPLACE INTO logs (id, hobby_id, entry, created_at, rating, photo_uri) VALUES (?, ?, ?, ?, ?, ?)"
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, log.id)
        sqlite3_bind_int64(stmt, 2, log.hobbyId)
        bindText(stmt, 3, log.entry)
        sqlite3_bind_int64(stmt, 4, log.createdAt)
        bindIntOrNull(stmt, 5, log.rating)
        bindTextOrNull(stmt, 6, log.photoUri)
        sqlite3_step(stmt)
        return sqlite3_last_insert_rowid(handle)
    }

    func deleteLog(id: Int64) {
        db.queue.sync { execBind("DELETE FROM logs WHERE id = ?", id) }
    }

    func fetchLog(id: Int64) -> HobbyLog? {
        db.queue.sync {
            let sql = "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs WHERE id = ?"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, id)
            guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
            return logFrom(stmt)
        }
    }

    func detail(_ hobbyId: Int64) -> HobbyDetail? {
        db.queue.sync { detailLocked(hobbyId) }
    }

    private func detailLocked(_ hobbyId: Int64) -> HobbyDetail? {
        let sql = "SELECT \(HobbyRepository.hobbyColumns) FROM hobbies WHERE id = ?"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, hobbyId)
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        let hobby = hobbyFrom(stmt)
        return HobbyDetail(hobby: hobby, logs: queryLogsLocked(hobbyId))
    }

    private func queryLogsLocked(_ hobbyId: Int64) -> [HobbyLog] {
        let sql = "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs WHERE hobby_id = ? ORDER BY created_at DESC"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, hobbyId)
        var out: [HobbyLog] = []
        while sqlite3_step(stmt) == SQLITE_ROW { out.append(logFrom(stmt)) }
        return out
    }

    private func logFrom(_ stmt: OpaquePointer?) -> HobbyLog {
        HobbyLog(
            id: sqlite3_column_int64(stmt, 0),
            hobbyId: sqlite3_column_int64(stmt, 1),
            entry: columnText(stmt, 2),
            createdAt: sqlite3_column_int64(stmt, 3),
            rating: columnIntOrNil(stmt, 4),
            photoUri: columnTextOrNil(stmt, 5)
        )
    }

    /// Search logs by entry text. Returns at most `limit` results joined to the hobby.
    func searchLogs(query: String, limit: Int = 50) -> [LogSearchHit] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return [] }
        return db.queue.sync {
            let sql = """
                SELECT l.id, l.hobby_id, l.entry, l.created_at, l.rating, l.photo_uri,
                       h.name, h.category
                FROM logs l JOIN hobbies h ON h.id = l.hobby_id
                WHERE h.is_archived = 0 AND l.entry LIKE ?
                ORDER BY l.created_at DESC
                LIMIT ?
                """
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, "%\(q)%")
            sqlite3_bind_int(stmt, 2, Int32(limit))
            var out: [LogSearchHit] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                out.append(LogSearchHit(
                    log: logFrom(stmt),
                    hobbyName: columnText(stmt, 6),
                    hobbyCategory: columnText(stmt, 7)
                ))
            }
            return out
        }
    }

    // ── Synchronous accessors for widget / notification handlers ───────────────

    func hobbyByIdSync(_ hobbyId: Int64) -> Hobby? {
        db.queue.sync {
            let sql = "SELECT \(HobbyRepository.hobbyColumns) FROM hobbies WHERE id = ?"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, hobbyId)
            return sqlite3_step(stmt) == SQLITE_ROW ? hobbyFrom(stmt) : nil
        }
    }

    func activeHobbiesWithReminderSync() -> [Hobby] {
        db.queue.sync {
            let sql = "SELECT \(HobbyRepository.hobbyColumns) FROM hobbies WHERE is_archived = 0 AND next_reminder_at IS NOT NULL"
            return collectHobbies(sql)
        }
    }

    func nextDueHobbySync() -> Hobby? {
        db.queue.sync {
            let sql = """
                SELECT \(HobbyRepository.hobbyColumns) FROM hobbies
                WHERE is_archived = 0
                ORDER BY is_pinned DESC, next_reminder_at IS NULL, next_reminder_at ASC
                LIMIT 1
                """
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
            defer { sqlite3_finalize(stmt) }
            return sqlite3_step(stmt) == SQLITE_ROW ? hobbyFrom(stmt) : nil
        }
    }

    func updateReminderSync(hobbyId: Int64, nextReminderAt: Int64?) {
        db.queue.sync {
            let sql = "UPDATE hobbies SET next_reminder_at = ? WHERE id = ?"
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
            defer { sqlite3_finalize(stmt) }
            bindInt64OrNull(stmt, 1, nextReminderAt)
            sqlite3_bind_int64(stmt, 2, hobbyId)
            sqlite3_step(stmt)
            refreshSyncLocked()
        }
    }

    @discardableResult
    func addLogSync(hobbyId: Int64, entry: String) -> Int64 {
        addLog(hobbyId: hobbyId, entry: entry, rating: nil, photoUri: nil)
    }

    /// Bulk reads used by Backup.
    func allHobbiesSync() -> [Hobby] {
        db.queue.sync { collectHobbies("SELECT \(HobbyRepository.hobbyColumns) FROM hobbies") }
    }

    func allLogsSync() -> [HobbyLog] {
        db.queue.sync {
            let sql = "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
            defer { sqlite3_finalize(stmt) }
            var out: [HobbyLog] = []
            while sqlite3_step(stmt) == SQLITE_ROW { out.append(logFrom(stmt)) }
            return out
        }
    }

    func replaceAll(hobbies: [Hobby], logs: [HobbyLog]) {
        db.queue.sync {
            exec("BEGIN")
            exec("DELETE FROM logs")
            exec("DELETE FROM hobbies")
            for h in hobbies {
                let (type, data) = h.recurrence.encode()
                let sql = """
                    INSERT OR REPLACE INTO hobbies
                    (id, name, category, notes, next_reminder_at, created_at, is_pinned, is_archived,
                     reminder_interval_hours, recurrence_type, recurrence_data, weekly_goal)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                var stmt: OpaquePointer?
                sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
                sqlite3_bind_int64(stmt, 1, h.id)
                bindText(stmt, 2, h.name)
                bindText(stmt, 3, h.category)
                bindText(stmt, 4, h.notes)
                bindInt64OrNull(stmt, 5, h.nextReminderAt)
                sqlite3_bind_int64(stmt, 6, h.createdAt)
                sqlite3_bind_int(stmt, 7, h.isPinned ? 1 : 0)
                sqlite3_bind_int(stmt, 8, h.isArchived ? 1 : 0)
                sqlite3_bind_int64(stmt, 9, h.reminderIntervalHours)
                bindText(stmt, 10, type)
                bindText(stmt, 11, data)
                sqlite3_bind_int(stmt, 12, Int32(h.weeklyGoal))
                sqlite3_step(stmt)
                sqlite3_finalize(stmt)
            }
            for l in logs { insertLogLocked(l) }
            exec("COMMIT")
            refreshSyncLocked()
        }
    }

    // ── Private helpers (call on db.queue) ──────────────────────────────────────

    private func collectHobbies(_ sql: String) -> [Hobby] {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }
        var out: [Hobby] = []
        while sqlite3_step(stmt) == SQLITE_ROW { out.append(hobbyFrom(stmt)) }
        return out
    }

    private func execBind(_ sql: String, _ id: Int64) {
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(handle, sql, -1, &stmt, nil)
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_int64(stmt, 1, id)
        sqlite3_step(stmt)
    }

    @discardableResult private func exec(_ sql: String) -> Bool { db.exec(sql) }
}

// ── Column / bind conveniences ──────────────────────────────────────────────────

private func columnText(_ stmt: OpaquePointer?, _ idx: Int32) -> String {
    guard let c = sqlite3_column_text(stmt, idx) else { return "" }
    return String(cString: c)
}

private func columnTextOrNil(_ stmt: OpaquePointer?, _ idx: Int32) -> String? {
    sqlite3_column_type(stmt, idx) == SQLITE_NULL ? nil : columnText(stmt, idx)
}

private func columnInt64OrNil(_ stmt: OpaquePointer?, _ idx: Int32) -> Int64? {
    sqlite3_column_type(stmt, idx) == SQLITE_NULL ? nil : sqlite3_column_int64(stmt, idx)
}

private func columnIntOrNil(_ stmt: OpaquePointer?, _ idx: Int32) -> Int? {
    sqlite3_column_type(stmt, idx) == SQLITE_NULL ? nil : Int(sqlite3_column_int(stmt, idx))
}

private func bindText(_ stmt: OpaquePointer?, _ idx: Int32, _ value: String) {
    sqlite3_bind_text(stmt, idx, value, -1, AppDatabase.SQLITE_TRANSIENT)
}

private func bindTextOrNull(_ stmt: OpaquePointer?, _ idx: Int32, _ value: String?) {
    if let value { sqlite3_bind_text(stmt, idx, value, -1, AppDatabase.SQLITE_TRANSIENT) }
    else { sqlite3_bind_null(stmt, idx) }
}

private func bindInt64OrNull(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int64?) {
    if let value { sqlite3_bind_int64(stmt, idx, value) } else { sqlite3_bind_null(stmt, idx) }
}

private func bindIntOrNull(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int?) {
    if let value { sqlite3_bind_int(stmt, idx, Int32(value)) } else { sqlite3_bind_null(stmt, idx) }
}
