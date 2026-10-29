package com.yuivo9999.mybox123.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class RecoverableImportOperation(
    val operationId: String,
    val formatVersion: Int,
    val createdAt: Long,
    val status: String,
    val affectedKeyCount: Int,
    val affectedKeys: List<String>,
    val snapshotEntryCount: Int,
) {
    /** Rollback is offered only when the persisted snapshot appears complete and status is known. */
    val rollbackEligible: Boolean
        get() = status == "APPLIED" && affectedKeyCount > 0 && snapshotEntryCount == affectedKeyCount
}

/**
 * Small native persistence layer used during the React/WebView migration.
 * JSON payloads are preserved as-is so schema changes do not silently discard legacy fields.
 */
class NativeUserDataDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE user_data (
                data_key TEXT PRIMARY KEY NOT NULL,
                value_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE legacy_backup (
                data_key TEXT PRIMARY KEY NOT NULL,
                value_raw TEXT NOT NULL,
                backed_up_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX index_user_data_updated_at ON user_data(updated_at)")
        createRestoreTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion > newVersion) {
            throw IllegalStateException("USER_DATA_DATABASE_VERSION_ROLLBACK")
        }
        if (oldVersion < 2 && newVersion >= 2) {
            // Additive migration only: existing user_data and legacy_backup rows are untouched.
            createRestoreTables(db)
        }
    }

    private fun createRestoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE restore_operations (
                operation_id TEXT PRIMARY KEY NOT NULL,
                format_version INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('APPLIED', 'ROLLED_BACK')),
                affected_key_count INTEGER NOT NULL CHECK(affected_key_count > 0)
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE restore_snapshot_entries (
                operation_id TEXT NOT NULL,
                data_key TEXT NOT NULL,
                existed_before INTEGER NOT NULL CHECK(existed_before IN (0, 1)),
                prior_value_json TEXT,
                prior_updated_at INTEGER,
                applied_value_json TEXT NOT NULL,
                applied_updated_at INTEGER NOT NULL,
                PRIMARY KEY(operation_id, data_key),
                CHECK((existed_before = 1 AND prior_value_json IS NOT NULL AND prior_updated_at IS NOT NULL)
                    OR (existed_before = 0 AND prior_value_json IS NULL AND prior_updated_at IS NULL))
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX index_restore_operations_created_at ON restore_operations(created_at)")
    }

    fun readRaw(key: String): String? {
        readableDatabase.query(
            TABLE_USER_DATA,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun readAllRaw(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        readableDatabase.query(TABLE_USER_DATA, arrayOf(COLUMN_KEY, COLUMN_VALUE), null, null, null, null, "$COLUMN_KEY ASC")
            .use { cursor ->
                while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
            }
        return result
    }

    fun writeRaw(key: String, valueJson: String) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, valueJson)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        val rowId = writableDatabase.insertWithOnConflict(
            TABLE_USER_DATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        check(rowId != -1L) { "USER_DATA_WRITE_FAILED:$key" }
    }

    fun writeManyRaw(valuesByKey: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            valuesByKey.forEach { (key, raw) -> writeUserDataRow(db, key, raw, now) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Snapshot the exact previous row state and apply the chosen import values atomically.
     * expectedCurrentRaw must contain every changed key (null means that key was absent), so a
     * preview that became stale cannot overwrite intervening user changes.
     */
    fun applyRecoverableImport(
        operationId: String,
        formatVersion: Int,
        expectedCurrentRaw: Map<String, String?>,
        valuesByKey: Map<String, String>,
    ) {
        require(operationId.isNotBlank()) { "RESTORE_OPERATION_ID_REQUIRED" }
        require(valuesByKey.isNotEmpty()) { "RESTORE_NO_CHANGES" }
        require(expectedCurrentRaw.keys == valuesByKey.keys) { "RESTORE_EXPECTED_STATE_KEYS_MISMATCH" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val operation = ContentValues().apply {
                put(COLUMN_OPERATION_ID, operationId)
                put(COLUMN_FORMAT_VERSION, formatVersion)
                put(COLUMN_CREATED_AT, now)
                put(COLUMN_STATUS, STATUS_APPLIED)
                put(COLUMN_AFFECTED_KEY_COUNT, valuesByKey.size)
            }
            check(db.insertOrThrow(TABLE_RESTORE_OPERATIONS, null, operation) != -1L) {
                "RESTORE_OPERATION_INSERT_FAILED"
            }

            valuesByKey.toSortedMap().forEach { (key, newRaw) ->
                val previous = readUserDataRowWithinTransaction(db, key)
                if (previous?.valueJson != expectedCurrentRaw[key]) {
                    throw IllegalStateException("RESTORE_CURRENT_DATA_CHANGED:$key")
                }
                val snapshot = ContentValues().apply {
                    put(COLUMN_OPERATION_ID, operationId)
                    put(COLUMN_KEY, key)
                    put(COLUMN_EXISTED_BEFORE, if (previous == null) 0 else 1)
                    if (previous != null) {
                        put(COLUMN_PRIOR_VALUE, previous.valueJson)
                        put(COLUMN_PRIOR_UPDATED_AT, previous.updatedAt)
                    } else {
                        putNull(COLUMN_PRIOR_VALUE)
                        putNull(COLUMN_PRIOR_UPDATED_AT)
                    }
                    put(COLUMN_APPLIED_VALUE, newRaw)
                    put(COLUMN_APPLIED_UPDATED_AT, now)
                }
                check(db.insertOrThrow(TABLE_RESTORE_ENTRIES, null, snapshot) != -1L) {
                    "RESTORE_SNAPSHOT_INSERT_FAILED:$key"
                }
                writeUserDataRow(db, key, newRaw, now)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Restore exact prior JSON/timestamp and remove keys that did not exist before the operation. */
    fun rollbackRecoverableImport(operationId: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val operationMetadata = readRestoreOperationMetadata(db, operationId)
                ?: throw IllegalArgumentException("RESTORE_OPERATION_NOT_FOUND")
            val status = operationMetadata.first
            if (status == STATUS_ROLLED_BACK) {
                db.setTransactionSuccessful()
                return false
            }
            check(status == STATUS_APPLIED) { "RESTORE_OPERATION_STATE_INVALID" }
            val snapshots = mutableListOf<RestoreSnapshotRow>()
            db.query(
                TABLE_RESTORE_ENTRIES,
                arrayOf(COLUMN_KEY, COLUMN_EXISTED_BEFORE, COLUMN_PRIOR_VALUE, COLUMN_PRIOR_UPDATED_AT, COLUMN_APPLIED_VALUE, COLUMN_APPLIED_UPDATED_AT),
                "$COLUMN_OPERATION_ID = ?",
                arrayOf(operationId), null, null, "$COLUMN_KEY ASC",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    snapshots += RestoreSnapshotRow(
                        key = cursor.getString(0),
                        existedBefore = cursor.getInt(1) == 1,
                        valueJson = if (cursor.isNull(2)) null else cursor.getString(2),
                        updatedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                        appliedValueJson = cursor.getString(4),
                        appliedUpdatedAt = cursor.getLong(5),
                    )
                }
            }
            check(snapshots.isNotEmpty() && snapshots.size == operationMetadata.second) { "RESTORE_SNAPSHOT_INCOMPLETE" }
            snapshots.forEach { snapshot ->
                val current = readUserDataRowWithinTransaction(db, snapshot.key)
                check(current != null && current.valueJson == snapshot.appliedValueJson && current.updatedAt == snapshot.appliedUpdatedAt) {
                    "RESTORE_ROLLBACK_CONFLICT:${snapshot.key}"
                }
                if (snapshot.existedBefore) {
                    val values = ContentValues().apply {
                        put(COLUMN_KEY, snapshot.key)
                        put(COLUMN_VALUE, requireNotNull(snapshot.valueJson))
                        put(COLUMN_UPDATED_AT, requireNotNull(snapshot.updatedAt))
                    }
                    check(db.insertWithOnConflict(TABLE_USER_DATA, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                        "RESTORE_ROLLBACK_WRITE_FAILED:${snapshot.key}"
                    }
                } else {
                    db.delete(TABLE_USER_DATA, "$COLUMN_KEY = ?", arrayOf(snapshot.key))
                }
            }
            val statusValues = ContentValues().apply { put(COLUMN_STATUS, STATUS_ROLLED_BACK) }
            check(db.update(TABLE_RESTORE_OPERATIONS, statusValues, "$COLUMN_OPERATION_ID = ? AND $COLUMN_STATUS = ?", arrayOf(operationId, STATUS_APPLIED)) == 1) {
                "RESTORE_ROLLBACK_STATUS_UPDATE_FAILED"
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun readRestoreOperationStatus(operationId: String): String? = readRestoreOperationStatus(readableDatabase, operationId)

    /** Read-only, bounded history; snapshots are retained and never pruned by this query. */
    fun readRecoverableImportHistory(limit: Int = 20): List<RecoverableImportOperation> {
        val boundedLimit = limit.coerceIn(1, MAX_RESTORE_HISTORY_ROWS)
        val result = mutableListOf<RecoverableImportOperation>()
        readableDatabase.rawQuery(
            """SELECT o.operation_id, o.format_version, o.created_at, o.status,
                o.affected_key_count, COALESCE(GROUP_CONCAT(e.data_key, ','), '') AS affected_keys,
                COUNT(e.data_key) AS snapshot_entry_count
                FROM $TABLE_RESTORE_OPERATIONS o
                LEFT JOIN $TABLE_RESTORE_ENTRIES e ON e.operation_id = o.operation_id
                GROUP BY o.operation_id, o.format_version, o.created_at, o.status, o.affected_key_count
                ORDER BY o.created_at DESC, o.operation_id DESC LIMIT ?""".trimIndent(),
            arrayOf(boundedLimit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += RecoverableImportOperation(
                    operationId = cursor.getString(0),
                    formatVersion = cursor.getInt(1),
                    createdAt = cursor.getLong(2),
                    status = cursor.getString(3),
                    affectedKeyCount = cursor.getInt(4),
                    affectedKeys = cursor.getString(5).split(',').filter(String::isNotBlank).sorted(),
                    snapshotEntryCount = cursor.getInt(6),
                )
            }
        }
        return result
    }

    private fun readRestoreOperationStatus(db: SQLiteDatabase, operationId: String): String? =
        readRestoreOperationMetadata(db, operationId)?.first

    private fun readRestoreOperationMetadata(db: SQLiteDatabase, operationId: String): Pair<String, Int>? {
        db.query(TABLE_RESTORE_OPERATIONS, arrayOf(COLUMN_STATUS, COLUMN_AFFECTED_KEY_COUNT), "$COLUMN_OPERATION_ID = ?", arrayOf(operationId), null, null, null, "1")
            .use { cursor -> return if (cursor.moveToFirst()) cursor.getString(0) to cursor.getInt(1) else null }
    }

    private data class UserDataRow(val valueJson: String, val updatedAt: Long)
    private data class RestoreSnapshotRow(
        val key: String,
        val existedBefore: Boolean,
        val valueJson: String?,
        val updatedAt: Long?,
        val appliedValueJson: String,
        val appliedUpdatedAt: Long,
    )

    private fun readUserDataRowWithinTransaction(db: SQLiteDatabase, key: String): UserDataRow? {
        db.query(TABLE_USER_DATA, arrayOf(COLUMN_VALUE, COLUMN_UPDATED_AT), "$COLUMN_KEY = ?", arrayOf(key), null, null, null, "1")
            .use { cursor -> return if (cursor.moveToFirst()) UserDataRow(cursor.getString(0), cursor.getLong(1)) else null }
    }

    private fun writeUserDataRow(db: SQLiteDatabase, key: String, raw: String, updatedAt: Long) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, raw)
            put(COLUMN_UPDATED_AT, updatedAt)
        }
        check(db.insertWithOnConflict(TABLE_USER_DATA, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "USER_DATA_WRITE_FAILED:$key"
        }
    }

    /**
     * Copy the complete legacy SharedPreferences snapshot without deleting or changing its source.
     * Existing native DB values win, while every source value is kept in the backup table.
     * Returns false when another successful migration has already been recorded.
     */
    fun importLegacySnapshot(
        values: Map<String, String>,
        migrationMarkerKey: String,
        migrationMarkerJson: String,
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingMarker = readRawWithinTransaction(db, migrationMarkerKey)
            if (existingMarker != null) {
                db.setTransactionSuccessful()
                return false
            }

            val now = System.currentTimeMillis()
            values.forEach { (key, raw) ->
                val backup = ContentValues().apply {
                    put(COLUMN_KEY, key)
                    put(COLUMN_BACKUP_VALUE, raw)
                    put(COLUMN_BACKED_UP_AT, now)
                }
                db.insertWithOnConflict(TABLE_LEGACY_BACKUP, null, backup, SQLiteDatabase.CONFLICT_IGNORE)

                // Do not overwrite a value that a newer native flow may already have written.
                val native = ContentValues().apply {
                    put(COLUMN_KEY, key)
                    put(COLUMN_VALUE, raw)
                    put(COLUMN_UPDATED_AT, now)
                }
                db.insertWithOnConflict(TABLE_USER_DATA, null, native, SQLiteDatabase.CONFLICT_IGNORE)
            }

            val marker = ContentValues().apply {
                put(COLUMN_KEY, migrationMarkerKey)
                put(COLUMN_VALUE, migrationMarkerJson)
                put(COLUMN_UPDATED_AT, now)
            }
            check(db.insertWithOnConflict(TABLE_USER_DATA, null, marker, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                "USER_DATA_MIGRATION_MARKER_WRITE_FAILED"
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }


    fun readLegacyBackup(key: String): String? {
        readableDatabase.query(
            TABLE_LEGACY_BACKUP,
            arrayOf(COLUMN_BACKUP_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun readAllLegacyBackups(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        readableDatabase.query(TABLE_LEGACY_BACKUP, arrayOf(COLUMN_KEY, COLUMN_BACKUP_VALUE), null, null, null, null, "$COLUMN_KEY ASC")
            .use { cursor ->
                while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
            }
        return result
    }

    fun legacyBackupCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_LEGACY_BACKUP", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun readRawWithinTransaction(db: SQLiteDatabase, key: String): String? {
        db.query(TABLE_USER_DATA, arrayOf(COLUMN_VALUE), "$COLUMN_KEY = ?", arrayOf(key), null, null, null, "1")
            .use { cursor -> return if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    companion object {
        const val DATABASE_NAME = "mybox_native_user_data.db"
        private const val DATABASE_VERSION = 2
        private const val MAX_RESTORE_HISTORY_ROWS = 100
        private const val TABLE_USER_DATA = "user_data"
        private const val TABLE_LEGACY_BACKUP = "legacy_backup"
        private const val TABLE_RESTORE_OPERATIONS = "restore_operations"
        private const val TABLE_RESTORE_ENTRIES = "restore_snapshot_entries"
        private const val COLUMN_OPERATION_ID = "operation_id"
        private const val COLUMN_FORMAT_VERSION = "format_version"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_AFFECTED_KEY_COUNT = "affected_key_count"
        private const val COLUMN_EXISTED_BEFORE = "existed_before"
        private const val COLUMN_PRIOR_VALUE = "prior_value_json"
        private const val COLUMN_PRIOR_UPDATED_AT = "prior_updated_at"
        private const val COLUMN_APPLIED_VALUE = "applied_value_json"
        private const val COLUMN_APPLIED_UPDATED_AT = "applied_updated_at"
        private const val STATUS_APPLIED = "APPLIED"
        private const val STATUS_ROLLED_BACK = "ROLLED_BACK"
        private const val COLUMN_KEY = "data_key"
        private const val COLUMN_VALUE = "value_json"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_BACKUP_VALUE = "value_raw"
        private const val COLUMN_BACKED_UP_AT = "backed_up_at"
    }
}
