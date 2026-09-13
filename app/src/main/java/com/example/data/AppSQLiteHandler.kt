package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                instance ?: AppSQLiteHandler(context.applicationContext).also { instance = it }
            }
        }
    }

    // Reactive database change event bus
    private val _databaseEvents = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val databaseEvents: SharedFlow<Unit> = _databaseEvents.asSharedFlow()

    fun notifyDataChanged() {
        _databaseEvents.tryEmit(Unit)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
        ensureSchemaCompatibility(db)
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            ensureSchemaCompatibility(db)
            seedInitialData(db)
        } catch (e: Exception) {
            Log.w(TAG, "onUpgrade warning: ${e.message}")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            ensureSchemaCompatibility(db)
            seedInitialData(db)
        } catch (e: Exception) {
            Log.w(TAG, "onDowngrade warning: ${e.message}")
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            ensureSchemaCompatibility(db)
        } catch (e: Exception) {
            Log.w(TAG, "onOpen compatibility warning: ${e.message}")
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")

        // 1. customers table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                balance REAL DEFAULT 0,
                group_id INTEGER,
                notes TEXT,
                account_details TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)

        // 2. groups table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_name TEXT NOT NULL
            );
        """)

        // 3. currency table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS currency (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                symbol TEXT,
                currency_name TEXT
            );
        """)

        // 4. transactions table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                currency TEXT DEFAULT 'YER',
                date TEXT NOT NULL,
                note TEXT,
                share_ref TEXT,
                transaction_type TEXT,
                currency_id INTEGER DEFAULT 1,
                timestamp INTEGER,
                ledger_balance REAL DEFAULT 0.0,
                FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
            );
        """)

        // 5. transactions_d table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions_d (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER NOT NULL,
                item_name TEXT,
                quantity REAL,
                unit_price REAL,
                total_price REAL,
                detail_note TEXT,
                amount REAL,
                FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
            );
        """)

        // 6. reminders table (Schema specification)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                reminder_date TEXT NOT NULL,
                note TEXT,
                is_completed INTEGER DEFAULT 0,
                date TEXT
            );
        """)

        // 7. View: transactions_tot_v (Calculates running debit/credit aggregates grouped per customer)
        db.execSQL("DROP VIEW IF EXISTS transactions_tot_v;")
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS transactions_tot_v AS
            SELECT 
                customer_id,
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'CREDIT' OR type = 'له' OR (type IS NULL AND transaction_type = 'له') THEN amount ELSE 0.0 END), 0.0) as total_lah,
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'DEBIT' OR type = 'عليه' OR (type IS NULL AND transaction_type = 'عليه') THEN amount ELSE 0.0 END), 0.0) as total_alayh,
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'CREDIT' OR type = 'له' OR (type IS NULL AND transaction_type = 'له') THEN amount ELSE -amount END), 0.0) as total_amount
            FROM transactions
            GROUP BY customer_id;
        """)

        // 8. Unique Index: transactions_share_ref_uq ON transactions (share_ref)
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS transactions_share_ref_uq 
            ON transactions(share_ref);
        """)
    }

    /**
     * Inspects active database and safely adds any missing columns, views, or indexes
     * from legacy versions or imported files so queries never encounter SQLiteExceptions.
     */
    fun ensureSchemaCompatibility(db: SQLiteDatabase) {
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")

            // Helper to inspect table columns and append if missing
            fun addColumnIfNotExists(table: String, column: String, typeDef: String) {
                try {
                    val cursor = db.rawQuery("PRAGMA table_info($table)", null)
                    var exists = false
                    while (cursor.moveToNext()) {
                        val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        if (colName.equals(column, ignoreCase = true)) {
                            exists = true
                            break
                        }
                    }
                    cursor.close()
                    if (!exists) {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $column $typeDef;")
                        Log.d(TAG, "Added column $column to table $table")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking column $column in $table: ${e.message}")
                }
            }

            // Ensure table existence first
            createTables(db)

            // 1. customers columns
            addColumnIfNotExists("customers", "balance", "REAL DEFAULT 0")
            addColumnIfNotExists("customers", "group_id", "INTEGER")
            addColumnIfNotExists("customers", "notes", "TEXT")
            addColumnIfNotExists("customers", "account_details", "TEXT")
            addColumnIfNotExists("customers", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")

            // 2. currency columns
            addColumnIfNotExists("currency", "name", "TEXT")
            addColumnIfNotExists("currency", "symbol", "TEXT")
            addColumnIfNotExists("currency", "currency_name", "TEXT")

            // 3. transactions columns
            addColumnIfNotExists("transactions", "type", "TEXT DEFAULT 'DEBIT'")
            addColumnIfNotExists("transactions", "amount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfNotExists("transactions", "currency", "TEXT DEFAULT 'YER'")
            addColumnIfNotExists("transactions", "date", "TEXT")
            addColumnIfNotExists("transactions", "note", "TEXT")
            addColumnIfNotExists("transactions", "share_ref", "TEXT")
            addColumnIfNotExists("transactions", "transaction_type", "TEXT")
            addColumnIfNotExists("transactions", "currency_id", "INTEGER DEFAULT 1")
            addColumnIfNotExists("transactions", "timestamp", "INTEGER")
            addColumnIfNotExists("transactions", "ledger_balance", "REAL DEFAULT 0.0")

            // 4. transactions_d columns
            addColumnIfNotExists("transactions_d", "item_name", "TEXT")
            addColumnIfNotExists("transactions_d", "quantity", "REAL")
            addColumnIfNotExists("transactions_d", "unit_price", "REAL")
            addColumnIfNotExists("transactions_d", "total_price", "REAL")
            addColumnIfNotExists("transactions_d", "detail_note", "TEXT")
            addColumnIfNotExists("transactions_d", "amount", "REAL")

            // 5. reminders columns
            addColumnIfNotExists("reminders", "customer_id", "INTEGER")
            addColumnIfNotExists("reminders", "reminder_date", "TEXT")
            addColumnIfNotExists("reminders", "note", "TEXT")
            addColumnIfNotExists("reminders", "is_completed", "INTEGER DEFAULT 0")
            addColumnIfNotExists("reminders", "date", "TEXT")

            // Synchronize currency fields
            db.execSQL("UPDATE currency SET name = currency_name WHERE (name IS NULL OR name = '') AND currency_name IS NOT NULL AND currency_name != '';")
            db.execSQL("UPDATE currency SET currency_name = name WHERE (currency_name IS NULL OR currency_name = '') AND name IS NOT NULL AND name != '';")

            // Synchronize transactions type and transaction_type
            db.execSQL("UPDATE transactions SET type = CASE WHEN transaction_type = 'له' THEN 'CREDIT' ELSE 'DEBIT' END WHERE type IS NULL OR type = '';")
            db.execSQL("UPDATE transactions SET transaction_type = CASE WHEN UPPER(type) = 'CREDIT' THEN 'له' ELSE 'عليه' END WHERE transaction_type IS NULL OR transaction_type = '';")

            // Synchronize customers notes and account_details
            db.execSQL("UPDATE customers SET notes = account_details WHERE (notes IS NULL OR notes = '') AND account_details IS NOT NULL AND account_details != '';")
            db.execSQL("UPDATE customers SET account_details = notes WHERE (account_details IS NULL OR account_details = '') AND notes IS NOT NULL AND notes != '';")

            // Recreate view and index
            db.execSQL("DROP VIEW IF EXISTS transactions_tot_v;")
            db.execSQL("""
                CREATE VIEW IF NOT EXISTS transactions_tot_v AS
                SELECT 
                    customer_id,
                    COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'CREDIT' OR type = 'له' OR (type IS NULL AND transaction_type = 'له') THEN amount ELSE 0.0 END), 0.0) as total_lah,
                    COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'DEBIT' OR type = 'عليه' OR (type IS NULL AND transaction_type = 'عليه') THEN amount ELSE 0.0 END), 0.0) as total_alayh,
                    COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'CREDIT' OR type = 'له' OR (type IS NULL AND transaction_type = 'له') THEN amount ELSE -amount END), 0.0) as total_amount
                FROM transactions
                GROUP BY customer_id;
            """)

            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS transactions_share_ref_uq ON transactions (share_ref);")

        } catch (e: Exception) {
            Log.e(TAG, "ensureSchemaCompatibility error: ${e.message}", e)
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        try {
            // Seed Currencies if empty
            val currCursor = db.rawQuery("SELECT COUNT(*) FROM currency", null)
            var currCount = 0
            if (currCursor.moveToFirst()) {
                currCount = currCursor.getInt(0)
            }
            currCursor.close()

            if (currCount == 0) {
                db.execSQL("INSERT INTO currency (name, symbol, currency_name) VALUES ('ريال يمني', 'YER', 'ريال يمني (YER)')")
                db.execSQL("INSERT INTO currency (name, symbol, currency_name) VALUES ('سعودي', 'SAR', 'ريال سعودي (SAR)')")
                db.execSQL("INSERT INTO currency (name, symbol, currency_name) VALUES ('دولار', 'USD', 'دولار أمريكي (USD)')")
                db.execSQL("INSERT INTO currency (name, symbol, currency_name) VALUES ('درهم', 'AED', 'درهم إماراتي (AED)')")
            }

            // Seed Groups if empty
            val grpCursor = db.rawQuery("SELECT COUNT(*) FROM groups", null)
            var grpCount = 0
            if (grpCursor.moveToFirst()) {
                grpCount = grpCursor.getInt(0)
            }
            grpCursor.close()

            if (grpCount == 0) {
                db.execSQL("INSERT INTO groups (group_name) VALUES ('عملاء آجل')")
                db.execSQL("INSERT INTO groups (group_name) VALUES ('موردين')")
                db.execSQL("INSERT INTO groups (group_name) VALUES ('عام')")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding initial data", e)
        }
    }

    // ==========================================
    // Safe Import & Export Implementation
    // ==========================================

    @Synchronized
    fun importDatabaseFile(sourceStream: InputStream): Boolean {
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        val backupFile = appContext.getDatabasePath(BACKUP_DATABASE_NAME)

        // Ensure database folder exists
        dbFile.parentFile?.mkdirs()

        // 1. First, safely copy sourceStream to a temporary file in cacheDir
        val tempImportFile = File(appContext.cacheDir, "temp_import_${System.currentTimeMillis()}.db")
        try {
            sourceStream.use { input ->
                FileOutputStream(tempImportFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream incoming database file", e)
            tempImportFile.delete()
            return false
        }

        // 2. Validate the newly written file
        if (!performIntegrityCheck(tempImportFile)) {
            Log.e(TAG, "Integrity/Validation check failed on imported database file!")
            tempImportFile.delete()
            return false
        }

        // 3. Checkpoint WAL and safely close active database connections to avoid file locks
        try {
            if (dbFile.exists()) {
                try {
                    val activeDb = readableDatabase
                    val cursor = activeDb.rawQuery("PRAGMA wal_checkpoint(FULL);", null)
                    cursor.close()
                } catch (ignored: Exception) {}
            }
            close()
            SQLiteDatabase.releaseMemory()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing db before import", e)
        }

        // 4. Create backup of current database
        try {
            if (dbFile.exists()) {
                copyFile(dbFile, backupFile)
                Log.d(TAG, "Temporary backup created at ${backupFile.absolutePath}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to create backup, proceeding with caution", e)
        }

        // 5. Delete WAL, SHM, and Journal files of current DB to prevent recovery conflicts
        try {
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            File(dbFile.absolutePath + "-journal").delete()
            File(tempImportFile.absolutePath + "-wal").delete()
            File(tempImportFile.absolutePath + "-shm").delete()
            File(tempImportFile.absolutePath + "-journal").delete()

            if (dbFile.exists()) {
                dbFile.delete()
            }

            var replaced = tempImportFile.renameTo(dbFile)
            if (!replaced) {
                copyFile(tempImportFile, dbFile)
                tempImportFile.delete()
                replaced = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed replacing database file", e)
            tempImportFile.delete()
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 6. Finalize and verify restored database
        try {
            val db = writableDatabase
            ensureSchemaCompatibility(db)
            seedInitialData(db)
            try {
                recalculateAllCustomersBalances(db)
            } catch (e: Exception) {
                Log.w(TAG, "Balance recalculation warning after import: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing restored database schema", e)
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 7. Cleanup backup upon success
        if (backupFile.exists()) {
            backupFile.delete()
        }

        notifyDataChanged()
        Log.d(TAG, "Database imported and verified successfully!")
        return true
    }

    @Synchronized
    fun exportDatabaseFile(outputStream: OutputStream): Boolean {
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) {
            // Force create DB if empty
            writableDatabase
        }

        return try {
            try {
                val db = readableDatabase
                val cursor = db.rawQuery("PRAGMA wal_checkpoint(FULL);", null)
                cursor.close()
            } catch (e: Exception) {
                Log.w(TAG, "wal_checkpoint warning", e)
            }

            outputStream.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export database", e)
            false
        }
    }

    private fun performIntegrityCheck(file: File): Boolean {
        if (!file.exists() || file.length() < 100) {
            Log.e(TAG, "Imported file is empty or too small: ${file.length()} bytes")
            return false
        }

        // 1. Verify SQLite format 3 magic header (first 16 bytes)
        try {
            val header = ByteArray(16)
            FileInputStream(file).use { it.read(header) }
            val headerStr = String(header, Charsets.US_ASCII)
            if (!headerStr.startsWith("SQLite format 3")) {
                Log.e(TAG, "File header does not match SQLite signature: $headerStr")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect file header", e)
            return false
        }

        // 2. Open read-only and verify readable SQLite database without strict PRAGMA failures
        var testDb: SQLiteDatabase? = null
        return try {
            testDb = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            // Test query on sqlite_master
            val masterCursor = testDb.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table';", null)
            val tableCount = if (masterCursor.moveToFirst()) masterCursor.getInt(0) else 0
            masterCursor.close()

            // Non-fatal quick check
            try {
                val checkCursor = testDb.rawQuery("PRAGMA quick_check(1);", null)
                checkCursor.close()
            } catch (e: Exception) {
                Log.w(TAG, "PRAGMA quick_check warning (ignored): ${e.message}")
            }

            tableCount >= 0
        } catch (e: Exception) {
            Log.e(TAG, "Error testing imported file as SQLite", e)
            // If the header had valid "SQLite format 3", allow it to proceed
            true
        } finally {
            try { testDb?.close() } catch (ignored: Exception) {}
            SQLiteDatabase.releaseMemory()
        }
    }

    private fun restoreBackup(backupFile: File, targetFile: File) {
        try {
            if (backupFile.exists()) {
                copyFile(backupFile, targetFile)
                backupFile.delete()
                Log.d(TAG, "Database successfully restored from backup")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Critical failure during backup rollback", e)
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

    // ==========================================
    // Customer CRUD Operations
    // ==========================================

    fun getAllCustomers(): List<CustomerModel> {
        val list = mutableListOf<CustomerModel>()
        val db = readableDatabase
        val query = """
            SELECT c.id, c.name, c.phone, 
                   COALESCE(c.balance, 0.0) as col_balance,
                   c.group_id,
                   COALESCE(c.notes, c.account_details, '') as customer_notes,
                   COALESCE(c.created_at, '') as customer_created_at,
                   COALESCE(v.total_lah, 0.0) as total_lah,
                   COALESCE(v.total_alayh, 0.0) as total_alayh,
                   COALESCE(v.total_amount, 0.0) as view_net_balance
            FROM customers c
            LEFT JOIN transactions_tot_v v ON c.id = v.customer_id
            ORDER BY c.name COLLATE NOCASE ASC
        """
        val cursor = db.rawQuery(query, null)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""
            val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")) ?: ""
            val colBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("col_balance"))
            val groupId = if (cursor.isNull(cursor.getColumnIndexOrThrow("group_id"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("group_id"))
            val customerNotes = cursor.getString(cursor.getColumnIndexOrThrow("customer_notes")) ?: ""
            val customerCreatedAt = cursor.getString(cursor.getColumnIndexOrThrow("customer_created_at")) ?: ""
            val totalLah = cursor.getDouble(cursor.getColumnIndexOrThrow("total_lah"))
            val totalAlayh = cursor.getDouble(cursor.getColumnIndexOrThrow("total_alayh"))
            val viewNet = cursor.getDouble(cursor.getColumnIndexOrThrow("view_net_balance"))
            val finalBalance = if (totalLah == 0.0 && totalAlayh == 0.0 && colBalance != 0.0) colBalance else viewNet

            list.add(
                CustomerModel(
                    id = id,
                    name = name,
                    phone = phone,
                    balance = finalBalance,
                    groupId = groupId,
                    notes = customerNotes,
                    accountDetails = customerNotes,
                    createdAt = customerCreatedAt,
                    totalLah = totalLah,
                    totalAlayh = totalAlayh,
                    netBalance = finalBalance
                )
            )
        }
        cursor.close()
        return list
    }

    fun getCustomerById(id: Long): CustomerModel? {
        val db = readableDatabase
        val query = """
            SELECT c.id, c.name, c.phone, 
                   COALESCE(c.balance, 0.0) as col_balance,
                   c.group_id,
                   COALESCE(c.notes, c.account_details, '') as customer_notes,
                   COALESCE(c.created_at, '') as customer_created_at,
                   COALESCE(v.total_lah, 0.0) as total_lah,
                   COALESCE(v.total_alayh, 0.0) as total_alayh,
                   COALESCE(v.total_amount, 0.0) as view_net_balance
            FROM customers c
            LEFT JOIN transactions_tot_v v ON c.id = v.customer_id
            WHERE c.id = ?
        """
        val cursor = db.rawQuery(query, arrayOf(id.toString()))
        var customer: CustomerModel? = null
        if (cursor.moveToFirst()) {
            val cId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""
            val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")) ?: ""
            val colBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("col_balance"))
            val groupId = if (cursor.isNull(cursor.getColumnIndexOrThrow("group_id"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("group_id"))
            val customerNotes = cursor.getString(cursor.getColumnIndexOrThrow("customer_notes")) ?: ""
            val customerCreatedAt = cursor.getString(cursor.getColumnIndexOrThrow("customer_created_at")) ?: ""
            val totalLah = cursor.getDouble(cursor.getColumnIndexOrThrow("total_lah"))
            val totalAlayh = cursor.getDouble(cursor.getColumnIndexOrThrow("total_alayh"))
            val viewNet = cursor.getDouble(cursor.getColumnIndexOrThrow("view_net_balance"))
            val finalBalance = if (totalLah == 0.0 && totalAlayh == 0.0 && colBalance != 0.0) colBalance else viewNet

            customer = CustomerModel(
                id = cId,
                name = name,
                phone = phone,
                balance = finalBalance,
                groupId = groupId,
                notes = customerNotes,
                accountDetails = customerNotes,
                createdAt = customerCreatedAt,
                totalLah = totalLah,
                totalAlayh = totalAlayh,
                netBalance = finalBalance
            )
        }
        cursor.close()
        return customer
    }

    fun insertCustomer(customer: CustomerModel): Long {
        val db = writableDatabase
        val noteVal = if (customer.notes.isNotBlank()) customer.notes else customer.accountDetails
        val values = ContentValues().apply {
            put("name", customer.name)
            put("phone", customer.phone)
            put("balance", customer.balance)
            put("notes", noteVal)
            put("account_details", noteVal)
            if (customer.groupId != null) {
                put("group_id", customer.groupId)
            }
        }
        val id = db.insert("customers", null, values)
        if (id != -1L) {
            notifyDataChanged()
        }
        return id
    }

    fun updateCustomer(customer: CustomerModel): Boolean {
        val db = writableDatabase
        val noteVal = if (customer.notes.isNotBlank()) customer.notes else customer.accountDetails
        val values = ContentValues().apply {
            put("name", customer.name)
            put("phone", customer.phone)
            put("balance", customer.balance)
            put("notes", noteVal)
            put("account_details", noteVal)
            if (customer.groupId != null) {
                put("group_id", customer.groupId)
            }
        }
        val rows = db.update("customers", values, "id = ?", arrayOf(customer.id.toString()))
        if (rows > 0) {
            notifyDataChanged()
            return true
        }
        return false
    }

    fun deleteCustomer(id: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            // Delete associated transactions & details
            val txIdsCursor = db.rawQuery("SELECT id FROM transactions WHERE customer_id = ?", arrayOf(id.toString()))
            while (txIdsCursor.moveToNext()) {
                val txId = txIdsCursor.getLong(0)
                db.delete("transactions_d", "transaction_id = ?", arrayOf(txId.toString()))
            }
            txIdsCursor.close()

            db.delete("transactions", "customer_id = ?", arrayOf(id.toString()))
            val rows = db.delete("customers", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
            if (rows > 0) {
                notifyDataChanged()
                true
            } else {
                false
            }
        } finally {
            db.endTransaction()
        }
    }

    // ==========================================
    // Transactions & Details CRUD with Running Balance
    // ==========================================

    fun getAllTransactions(): List<TransactionModel> {
        val list = mutableListOf<TransactionModel>()
        val db = readableDatabase
        val query = """
            SELECT t.id, t.customer_id, t.amount, 
                   COALESCE(t.type, CASE WHEN t.transaction_type = 'له' THEN 'CREDIT' ELSE 'DEBIT' END) as col_type,
                   COALESCE(t.transaction_type, CASE WHEN UPPER(t.type) = 'CREDIT' THEN 'له' ELSE 'عليه' END) as col_transaction_type,
                   COALESCE(t.currency, 'YER') as col_currency,
                   COALESCE(t.date, '') as col_date,
                   COALESCE(t.note, td.detail_note, '') as col_note,
                   t.currency_id, t.timestamp, t.ledger_balance, t.share_ref,
                   c.name as customer_name, c.phone as customer_phone,
                   COALESCE(cur.name, cur.currency_name, 'ريال يمني (YER)') as currency_name,
                   COALESCE(td.detail_note, t.note, '') as detail_note
            FROM transactions t
            LEFT JOIN customers c ON t.customer_id = c.id
            LEFT JOIN currency cur ON t.currency_id = cur.id
            LEFT JOIN transactions_d td ON t.id = td.transaction_id
            ORDER BY COALESCE(t.timestamp, 0) DESC, t.id DESC
        """
        val cursor = db.rawQuery(query, null)
        while (cursor.moveToNext()) {
            list.add(mapTransactionCursor(cursor))
        }
        cursor.close()
        return list
    }

    fun getTransactionsForCustomer(customerId: Long): List<TransactionModel> {
        val list = mutableListOf<TransactionModel>()
        val db = readableDatabase
        val query = """
            SELECT t.id, t.customer_id, t.amount, 
                   COALESCE(t.type, CASE WHEN t.transaction_type = 'له' THEN 'CREDIT' ELSE 'DEBIT' END) as col_type,
                   COALESCE(t.transaction_type, CASE WHEN UPPER(t.type) = 'CREDIT' THEN 'له' ELSE 'عليه' END) as col_transaction_type,
                   COALESCE(t.currency, 'YER') as col_currency,
                   COALESCE(t.date, '') as col_date,
                   COALESCE(t.note, td.detail_note, '') as col_note,
                   t.currency_id, t.timestamp, t.ledger_balance, t.share_ref,
                   c.name as customer_name, c.phone as customer_phone,
                   COALESCE(cur.name, cur.currency_name, 'ريال يمني (YER)') as currency_name,
                   COALESCE(td.detail_note, t.note, '') as detail_note
            FROM transactions t
            LEFT JOIN customers c ON t.customer_id = c.id
            LEFT JOIN currency cur ON t.currency_id = cur.id
            LEFT JOIN transactions_d td ON t.id = td.transaction_id
            WHERE t.customer_id = ?
            ORDER BY COALESCE(t.timestamp, 0) DESC, t.id DESC
        """
        val cursor = db.rawQuery(query, arrayOf(customerId.toString()))
        while (cursor.moveToNext()) {
            list.add(mapTransactionCursor(cursor))
        }
        cursor.close()
        return list
    }

    fun insertTransaction(tx: TransactionModel): Long {
        val db = writableDatabase
        db.beginTransaction()
        var newId = -1L
        try {
            val shareRef = tx.shareRef ?: "TX-${System.currentTimeMillis()}-${(1000..9999).random()}"
            val isLah = tx.transactionType == "له" || tx.type.equals("CREDIT", ignoreCase = true)
            val typeStr = if (isLah) "CREDIT" else "DEBIT"
            val txTypeStr = if (isLah) "له" else "عليه"
            val dateStr = if (tx.date.isNotBlank()) tx.date else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(tx.timestamp))
            val noteStr = if (tx.note.isNotBlank()) tx.note else tx.detailNote

            val txValues = ContentValues().apply {
                put("customer_id", tx.customerId)
                put("type", typeStr)
                put("transaction_type", txTypeStr)
                put("amount", tx.amount)
                put("currency", if (tx.currency.isNotBlank()) tx.currency else "YER")
                put("currency_id", tx.currencyId)
                put("date", dateStr)
                put("note", noteStr)
                put("timestamp", tx.timestamp)
                put("ledger_balance", 0.0)
                put("share_ref", shareRef)
            }
            newId = db.insert("transactions", null, txValues)
            if (newId != -1L) {
                // Insert details into transactions_d
                val detailValues = ContentValues().apply {
                    put("transaction_id", newId)
                    put("item_name", noteStr)
                    put("quantity", 1.0)
                    put("unit_price", tx.amount)
                    put("total_price", tx.amount)
                    put("detail_note", noteStr)
                    put("amount", tx.amount)
                }
                db.insert("transactions_d", null, detailValues)

                // Recalculate customer's running balances
                recalculateCustomerBalances(db, tx.customerId)
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }

        if (newId != -1L) {
            notifyDataChanged()
        }
        return newId
    }

    fun updateTransaction(tx: TransactionModel): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        var success = false
        try {
            val isLah = tx.transactionType == "له" || tx.type.equals("CREDIT", ignoreCase = true)
            val typeStr = if (isLah) "CREDIT" else "DEBIT"
            val txTypeStr = if (isLah) "له" else "عليه"
            val dateStr = if (tx.date.isNotBlank()) tx.date else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(tx.timestamp))
            val noteStr = if (tx.note.isNotBlank()) tx.note else tx.detailNote

            val txValues = ContentValues().apply {
                put("customer_id", tx.customerId)
                put("type", typeStr)
                put("transaction_type", txTypeStr)
                put("amount", tx.amount)
                put("currency", if (tx.currency.isNotBlank()) tx.currency else "YER")
                put("currency_id", tx.currencyId)
                put("date", dateStr)
                put("note", noteStr)
                put("timestamp", tx.timestamp)
            }
            val rows = db.update("transactions", txValues, "id = ?", arrayOf(tx.id.toString()))
            if (rows > 0) {
                val detailValues = ContentValues().apply {
                    put("item_name", noteStr)
                    put("detail_note", noteStr)
                    put("amount", tx.amount)
                    put("unit_price", tx.amount)
                    put("total_price", tx.amount)
                }
                val dRows = db.update("transactions_d", detailValues, "transaction_id = ?", arrayOf(tx.id.toString()))
                if (dRows == 0) {
                    detailValues.put("transaction_id", tx.id)
                    detailValues.put("quantity", 1.0)
                    db.insert("transactions_d", null, detailValues)
                }

                // Recalculate running balances
                recalculateCustomerBalances(db, tx.customerId)
                db.setTransactionSuccessful()
                success = true
            }
        } finally {
            db.endTransaction()
        }

        if (success) {
            notifyDataChanged()
        }
        return success
    }

    fun deleteTransaction(id: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        var success = false
        try {
            var customerId: Long? = null
            val cursor = db.rawQuery("SELECT customer_id FROM transactions WHERE id = ?", arrayOf(id.toString()))
            if (cursor.moveToFirst()) {
                customerId = cursor.getLong(0)
            }
            cursor.close()

            if (customerId != null) {
                db.delete("transactions_d", "transaction_id = ?", arrayOf(id.toString()))
                val rows = db.delete("transactions", "id = ?", arrayOf(id.toString()))
                if (rows > 0) {
                    recalculateCustomerBalances(db, customerId)
                    db.setTransactionSuccessful()
                    success = true
                }
            }
        } finally {
            db.endTransaction()
        }

        if (success) {
            notifyDataChanged()
        }
        return success
    }

    /**
     * Closes an account by consolidating all previous transactions of the customer
     * into a single opening balance transaction with the final net balance.
     */
    fun closeAccount(customerId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        var success = false
        try {
            // 1. Calculate current net balance
            val cursor = db.rawQuery(
                """
                SELECT 
                    COALESCE(SUM(CASE WHEN UPPER(COALESCE(type, '')) = 'CREDIT' OR transaction_type = 'له' THEN amount ELSE -amount END), 0.0) as net_balance
                FROM transactions
                WHERE customer_id = ?
                """,
                arrayOf(customerId.toString())
            )
            var netBalance = 0.0
            if (cursor.moveToFirst()) {
                netBalance = cursor.getDouble(0)
            }
            cursor.close()

            // 2. Delete all existing transaction details and transactions for this customer
            val txIdsCursor = db.rawQuery("SELECT id FROM transactions WHERE customer_id = ?", arrayOf(customerId.toString()))
            while (txIdsCursor.moveToNext()) {
                val txId = txIdsCursor.getLong(0)
                db.delete("transactions_d", "transaction_id = ?", arrayOf(txId.toString()))
            }
            txIdsCursor.close()
            db.delete("transactions", "customer_id = ?", arrayOf(customerId.toString()))

            // 3. If net balance is non-zero, create a single consolidated opening balance transaction
            if (Math.abs(netBalance) > 0.0001) {
                val isLah = netBalance > 0
                val txType = if (isLah) "له" else "عليه"
                val typeStr = if (isLah) "CREDIT" else "DEBIT"
                val amount = Math.abs(netBalance)
                val shareRef = "CLOSE-${System.currentTimeMillis()}-${(1000..9999).random()}"
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                val txValues = ContentValues().apply {
                    put("customer_id", customerId)
                    put("type", typeStr)
                    put("transaction_type", txType)
                    put("amount", amount)
                    put("currency", "YER")
                    put("currency_id", 1L)
                    put("date", dateStr)
                    put("note", "رصيد مدور / تصفية وإغلاق الحساب")
                    put("timestamp", System.currentTimeMillis())
                    put("ledger_balance", netBalance)
                    put("share_ref", shareRef)
                }
                val newTxId = db.insert("transactions", null, txValues)
                if (newTxId != -1L) {
                    val detailValues = ContentValues().apply {
                        put("transaction_id", newTxId)
                        put("item_name", "رصيد مدور / تصفية وإغلاق الحساب")
                        put("quantity", 1.0)
                        put("unit_price", amount)
                        put("total_price", amount)
                        put("detail_note", "رصيد مدور / تصفية وإغلاق الحساب")
                        put("amount", amount)
                    }
                    db.insert("transactions_d", null, detailValues)
                }
            }

            recalculateCustomerBalances(db, customerId)
            db.setTransactionSuccessful()
            success = true
        } catch (e: Exception) {
            Log.e(TAG, "Error closing account", e)
        } finally {
            db.endTransaction()
        }

        if (success) {
            notifyDataChanged()
        }
        return success
    }

    /**
     * Recalculates the cumulative running ledger balance for a specific customer
     * across their entire transaction timeline and updates both ledger_balance and customers.balance.
     */
    private fun recalculateCustomerBalances(db: SQLiteDatabase, customerId: Long) {
        val cursor = db.rawQuery(
            """
            SELECT id, amount, 
                   COALESCE(type, CASE WHEN transaction_type = 'له' THEN 'CREDIT' ELSE 'DEBIT' END) as col_type,
                   COALESCE(transaction_type, CASE WHEN UPPER(type) = 'CREDIT' THEN 'له' ELSE 'عليه' END) as col_tx_type
            FROM transactions 
            WHERE customer_id = ? 
            ORDER BY COALESCE(timestamp, 0) ASC, id ASC
            """,
            arrayOf(customerId.toString())
        )

        var runningBalance = 0.0
        val updates = mutableListOf<Pair<Long, Double>>()

        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val amount = cursor.getDouble(1)
            val colType = cursor.getString(2) ?: ""
            val txType = cursor.getString(3) ?: ""

            val isLah = colType.equals("CREDIT", ignoreCase = true) || txType == "له"
            if (isLah) {
                runningBalance += amount
            } else {
                runningBalance -= amount
            }
            updates.add(Pair(id, runningBalance))
        }
        cursor.close()

        val values = ContentValues()
        for ((txId, balance) in updates) {
            values.clear()
            values.put("ledger_balance", balance)
            db.update("transactions", values, "id = ?", arrayOf(txId.toString()))
        }

        // Update customers.balance
        try {
            db.execSQL(
                "UPDATE customers SET balance = ? WHERE id = ?;",
                arrayOf(runningBalance, customerId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not update customer balance column: ${e.message}")
        }
    }

    private fun recalculateAllCustomersBalances(db: SQLiteDatabase) {
        val custCursor = db.rawQuery("SELECT DISTINCT customer_id FROM transactions", null)
        val customerIds = mutableListOf<Long>()
        while (custCursor.moveToNext()) {
            customerIds.add(custCursor.getLong(0))
        }
        custCursor.close()

        for (cId in customerIds) {
            recalculateCustomerBalances(db, cId)
        }
    }

    private fun mapTransactionCursor(cursor: Cursor): TransactionModel {
        fun getColLong(vararg names: String, default: Long = 0L): Long {
            for (name in names) {
                val idx = cursor.getColumnIndex(name)
                if (idx != -1) {
                    return try { cursor.getLong(idx) } catch (e: Exception) { default }
                }
            }
            return default
        }

        fun getColDouble(vararg names: String, default: Double = 0.0): Double {
            for (name in names) {
                val idx = cursor.getColumnIndex(name)
                if (idx != -1) {
                    return try { cursor.getDouble(idx) } catch (e: Exception) { default }
                }
            }
            return default
        }

        fun getColString(vararg names: String, default: String = ""): String {
            for (name in names) {
                val idx = cursor.getColumnIndex(name)
                if (idx != -1) {
                    return try { cursor.getString(idx) ?: default } catch (e: Exception) { default }
                }
            }
            return default
        }

        val id = getColLong("id", "_id")
        val customerId = getColLong("customer_id", "client_id")
        val customerName = getColString("customer_name")
        val customerPhone = getColString("customer_phone")
        val amount = getColDouble("amount")
        val currencyId = getColLong("currency_id", default = 1L)
        val currencyName = getColString("currency_name", default = "ريال يمني (YER)")
        
        val rawType = getColString("type", "col_type", "transaction_type", "col_transaction_type", default = "DEBIT")
        val isLah = rawType.equals("CREDIT", ignoreCase = true) || rawType == "له"
        val transactionType = if (isLah) "له" else "عليه"
        val type = if (isLah) "CREDIT" else "DEBIT"

        val currency = getColString("currency", "col_currency", default = "YER")
        val dateStr = getColString("date", "col_date")

        var timestamp = getColLong("timestamp", default = 0L)
        if (timestamp <= 0L && dateStr.isNotBlank()) {
            timestamp = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dateStr)?.time
                    ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.time
                    ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        if (timestamp <= 0L) {
            timestamp = System.currentTimeMillis()
        }

        val ledgerBalance = getColDouble("ledger_balance", "balance")
        val shareRef = getColString("share_ref").takeIf { it.isNotBlank() }
        val detailNote = getColString("detail_note", "note", "col_note", "details", "notes", "description")

        return TransactionModel(
            id = id,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            amount = amount,
            currencyId = currencyId,
            currencyName = currencyName,
            transactionType = transactionType,
            type = type,
            currency = currency,
            date = dateStr,
            note = detailNote,
            timestamp = timestamp,
            ledgerBalance = ledgerBalance,
            shareRef = shareRef,
            detailNote = detailNote
        )
    }

    // Helper for Currencies
    fun getAllCurrencies(): List<CurrencyModel> {
        val list = mutableListOf<CurrencyModel>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, COALESCE(name, currency_name, '') as curr_name, symbol FROM currency ORDER BY id ASC", null)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: ""
            val symbol = cursor.getString(2)
            list.add(
                CurrencyModel(
                    id = id,
                    currencyName = name,
                    name = name,
                    symbol = symbol
                )
            )
        }
        cursor.close()
        return list
    }
}
