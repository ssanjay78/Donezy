import Foundation

/// JSON envelope for full-database backup/restore. Wire-compatible with the Android
/// `BackupCodec` (version 4) so backups round-trip between platforms. Photo URIs are
/// kept as-is; on a fresh device the file targets won't exist but text/rating data
/// round-trips faithfully.
enum BackupCodec {

    private static let version = 4

    static func encode(hobbies: [Hobby], logs: [HobbyLog]) -> String {
        var root: [String: Any] = [:]
        root["version"] = version
        root["exportedAt"] = nowMillis()

        root["hobbies"] = hobbies.map { h -> [String: Any] in
            let (type, data) = h.recurrence.encode()
            var o: [String: Any] = [
                "id": h.id,
                "name": h.name,
                "category": h.category,
                "notes": h.notes,
                "createdAt": h.createdAt,
                "isPinned": h.isPinned,
                "isArchived": h.isArchived,
                "reminderIntervalHours": h.reminderIntervalHours,
                "recurrenceType": type,
                "recurrenceData": data,
                "weeklyGoal": h.weeklyGoal,
            ]
            if let r = h.nextReminderAt { o["nextReminderAt"] = r }
            return o
        }

        root["logs"] = logs.map { l -> [String: Any] in
            var o: [String: Any] = [
                "id": l.id,
                "hobbyId": l.hobbyId,
                "entry": l.entry,
                "createdAt": l.createdAt,
            ]
            if let r = l.rating { o["rating"] = r }
            if let p = l.photoUri { o["photoUri"] = p }
            return o
        }

        let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    static func decode(_ json: String) throws -> (hobbies: [Hobby], logs: [HobbyLog]) {
        guard let data = json.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "BackupCodec", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Malformed backup file"])
        }

        let hobbiesArr = root["hobbies"] as? [[String: Any]] ?? []
        let logsArr = root["logs"] as? [[String: Any]] ?? []

        let hobbies: [Hobby] = hobbiesArr.compactMap { o in
            guard let id = (o["id"] as? NSNumber)?.int64Value,
                  let name = o["name"] as? String else { return nil }
            let type = (o["recurrenceType"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "none"
            let dataStr = o["recurrenceData"] as? String ?? ""
            let intervalHours = (o["reminderIntervalHours"] as? NSNumber)?.int64Value ?? 0
            let recurrence: Recurrence = (type == "none" && intervalHours > 0)
                ? .hourly(hours: intervalHours)
                : Recurrence.decode(type: type, data: dataStr)
            return Hobby(
                id: id,
                name: name,
                category: o["category"] as? String ?? "General",
                notes: o["notes"] as? String ?? "",
                nextReminderAt: (o["nextReminderAt"] as? NSNumber)?.int64Value,
                createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0,
                isPinned: o["isPinned"] as? Bool ?? false,
                isArchived: o["isArchived"] as? Bool ?? false,
                reminderIntervalHours: intervalHours,
                recurrence: recurrence,
                weeklyGoal: (o["weeklyGoal"] as? NSNumber)?.intValue ?? 0
            )
        }

        let logs: [HobbyLog] = logsArr.compactMap { o in
            guard let id = (o["id"] as? NSNumber)?.int64Value,
                  let hobbyId = (o["hobbyId"] as? NSNumber)?.int64Value else { return nil }
            return HobbyLog(
                id: id,
                hobbyId: hobbyId,
                entry: o["entry"] as? String ?? "",
                createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0,
                rating: (o["rating"] as? NSNumber)?.intValue,
                photoUri: o["photoUri"] as? String
            )
        }

        return (hobbies, logs)
    }
}
