package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AppSQLiteHandler(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        const val DATABASE_NAME = "app_database.db"
        const val BACKUP_DATABASE_NAME = "app_database_backup.db"
        const val DATABASE_VERSION = 1
        private const val TAG = "AppSQLiteHandler"

        @Volatile
        private var instance: AppSQLiteHandler? = null

        fun getInstance(context: Context): AppSQLiteHandler {
            return instance ?: synchronized(this) {
                instance ?: AppSQLiteHandler(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        
        // 1. customers table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                account_details TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        // 2. groups table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_name TEXT NOT NULL
            )
        """)

        // 3. currency table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS currency (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                currency_name TEXT NOT NULL
            )
        """)

        // 4. transactions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                amount REAL NOT NULL,
                currency_id INTEGER,
                transaction_type TEXT,
                timestamp INTEGER NOT NULL,
                ledger_balance REAL,
                share_ref TEXT,
                FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
                FOREIGN KEY (currency_id) REFERENCES currency(id)
            )
        """)

        // 5. transactions_d table (transaction details)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions_d (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER,
                detail_note TEXT,
                amount REAL,
                FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
            )
        """)

        // 6. reminders table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                note TEXT NOT NULL
            )
        """)

        // 7. View: transactions_tot_v
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS transactions_tot_v AS
            SELECT customer_id, currency_id, SUM(amount) as total_amount
            FROM transactions
            GROUP BY customer_id, currency_id
        """)

        // 8. Unique Index / Constraint: transactions_share_ref_uq
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS transactions_share_ref_uq 
            ON transactions(share_ref) WHERE share_ref IS NOT NULL
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        // Handle migrations if needed
        if (oldVersion < 2) {
            // Example migration statement if version increases
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON;")
        val versionCursor = db.rawQuery("PRAGMA user_version;", null)
        if (versionCursor.moveToFirst()) {
            val version = versionCursor.getInt(0)
            Log.d(TAG, "Current SQLite user_version: $version")
        }
        versionCursor.close()
    }

    /**
     * Safe backup and import protocol
     */
    @Synchronized
    fun importDatabaseFile(sourceStream: java.io.InputStream): Boolean {
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        val backupFile = appContext.getDatabasePath(BACKUP_DATABASE_NAME)

        // 1. Close active connections
        close()

        // 2. Create temporary backup
        try {
            if (dbFile.exists()) {
                copyFile(dbFile, backupFile)
                Log.d(TAG, "Temporary backup created at ${backupFile.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create backup", e)
            return false
        }

        // 3. Overwrite target file
        try {
            FileOutputStream(dbFile).use { output ->
                sourceStream.use { input ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "New database file copied successfully")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write database file, restoring backup", e)
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 4. Perform integrity check post-import
        if (!performIntegrityCheck(dbFile)) {
            Log.e(TAG, "Integrity check failed! Restoring backup.", null)
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 5. Verify PRAGMA user_version & foreign keys
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed post-import PRAGMA configuration", e)
            restoreBackup(backupFile, dbFile)
            return false
        }

        // Cleanup backup on success
        if (backupFile.exists()) {
            backupFile.delete()
        }

        return true
    }

    private fun performIntegrityCheck(dbFile: File): Boolean {
        var isOk = false
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("PRAGMA integrity_check;", null)
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                isOk = (result.equals("ok", ignoreCase = true))
                Log.d(TAG, "PRAGMA integrity_check result: $result")
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during integrity check", e)
            isOk = false
        } finally {
            db?.close()
        }
        return isOk
    }

    private fun restoreBackup(backupFile: File, targetFile: File) {
        try {
            if (backupFile.exists()) {
                copyFile(backupFile, targetFile)
                Log.d(TAG, "Database restored from backup successfully")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Critical error: Failed to restore backup", e)
        }
    }

    @Throws(IOException::class)
    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }
}
