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
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        createTables(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON;")
    }

    private fun createTables(db: SQLiteDatabase) {
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
                customer_id INTEGER NOT NULL,
                amount REAL NOT NULL,
                currency_id INTEGER DEFAULT 1,
                transaction_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                ledger_balance REAL DEFAULT 0.0,
                share_ref TEXT,
                FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
                FOREIGN KEY (currency_id) REFERENCES currency(id)
            )
        """)

        // 5. transactions_d table (transaction details)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions_d (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER NOT NULL,
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
            SELECT customer_id, currency_id,
                   SUM(CASE WHEN transaction_type = 'له' THEN amount ELSE 0.0 END) as total_lah,
                   SUM(CASE WHEN transaction_type = 'عليه' THEN amount ELSE 0.0 END) as total_alayh,
                   SUM(CASE WHEN transaction_type = 'له' THEN amount ELSE -amount END) as total_amount
            FROM transactions
            GROUP BY customer_id, currency_id
        """)

        // 8. Unique Index: transactions_share_ref_uq
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS transactions_share_ref_uq 
            ON transactions(share_ref) WHERE share_ref IS NOT NULL
        """)
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
                db.execSQL("INSERT INTO currency (currency_name) VALUES ('ريال يمني (YER)')")
                db.execSQL("INSERT INTO currency (currency_name) VALUES ('ريال سعودي (SAR)')")
                db.execSQL("INSERT INTO currency (currency_name) VALUES ('دولار أمريكي (USD)')")
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

        // 1. Close active database connections
        try {
            close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing db before import", e)
        }

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

        // 3. Write new database file safely
        val tempNewFile = appContext.getDatabasePath("temp_import.db")
        try {
            FileOutputStream(tempNewFile).use { output ->
                sourceStream.use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to stream incoming database file", e)
            tempNewFile.delete()
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 4. Verify integrity of newly written file
        if (!performIntegrityCheck(tempNewFile)) {
            Log.e(TAG, "PRAGMA integrity_check failed on imported database file!")
            tempNewFile.delete()
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 5. Replace database file atomically
        try {
            if (dbFile.exists()) {
                dbFile.delete()
            }
            if (!tempNewFile.renameTo(dbFile)) {
                copyFile(tempNewFile, dbFile)
                tempNewFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed replacing database file", e)
            restoreBackup(backupFile, dbFile)
            return false
        }

        // 6. Ensure required tables, views, and indexes exist in restored DB
        try {
            val db = writableDatabase
            createTables(db)
            seedInitialData(db)
            recalculateAllCustomersBalances(db)
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
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("PRAGMA integrity_check;", null)
            var ok = false
            if (cursor.moveToFirst()) {
                val res = cursor.getString(0)
                ok = res.equals("ok", ignoreCase = true)
            }
            cursor.close()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database integrity", e)
            false
        } finally {
            db?.close()
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
            SELECT c.id, c.name, c.phone, c.account_details, c.created_at,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'له' THEN t.amount ELSE 0.0 END), 0.0) as total_lah,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'عليه' THEN t.amount ELSE 0.0 END), 0.0) as total_alayh,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'له' THEN t.amount ELSE -t.amount END), 0.0) as net_balance
            FROM customers c
            LEFT JOIN transactions t ON c.id = t.customer_id
            GROUP BY c.id
            ORDER BY c.name COLLATE NOCASE ASC
        """
        val cursor = db.rawQuery(query, null)
        while (cursor.moveToNext()) {
            list.add(
                CustomerModel(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "",
                    phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")) ?: "",
                    accountDetails = cursor.getString(cursor.getColumnIndexOrThrow("account_details")) ?: "",
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")) ?: "",
                    totalLah = cursor.getDouble(cursor.getColumnIndexOrThrow("total_lah")),
                    totalAlayh = cursor.getDouble(cursor.getColumnIndexOrThrow("total_alayh")),
                    netBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("net_balance"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun getCustomerById(id: Long): CustomerModel? {
        val db = readableDatabase
        val query = """
            SELECT c.id, c.name, c.phone, c.account_details, c.created_at,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'له' THEN t.amount ELSE 0.0 END), 0.0) as total_lah,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'عليه' THEN t.amount ELSE 0.0 END), 0.0) as total_alayh,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'له' THEN t.amount ELSE -t.amount END), 0.0) as net_balance
            FROM customers c
            LEFT JOIN transactions t ON c.id = t.customer_id
            WHERE c.id = ?
            GROUP BY c.id
        """
        val cursor = db.rawQuery(query, arrayOf(id.toString()))
        var customer: CustomerModel? = null
        if (cursor.moveToFirst()) {
            customer = CustomerModel(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "",
                phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")) ?: "",
                accountDetails = cursor.getString(cursor.getColumnIndexOrThrow("account_details")) ?: "",
                createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")) ?: "",
                totalLah = cursor.getDouble(cursor.getColumnIndexOrThrow("total_lah")),
                totalAlayh = cursor.getDouble(cursor.getColumnIndexOrThrow("total_alayh")),
                netBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("net_balance"))
            )
        }
        cursor.close()
        return customer
    }

    fun insertCustomer(customer: CustomerModel): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", customer.name)
            put("phone", customer.phone)
            put("account_details", customer.accountDetails)
        }
        val id = db.insert("customers", null, values)
        if (id != -1L) {
            notifyDataChanged()
        }
        return id
    }

    fun updateCustomer(customer: CustomerModel): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", customer.name)
            put("phone", customer.phone)
            put("account_details", customer.accountDetails)
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
            SELECT t.id, t.customer_id, t.amount, t.currency_id, t.transaction_type, 
                   t.timestamp, t.ledger_balance, t.share_ref,
                   c.name as customer_name, c.phone as customer_phone,
                   COALESCE(cur.currency_name, 'ريال يمني (YER)') as currency_name,
                   COALESCE(td.detail_note, '') as detail_note
            FROM transactions t
            LEFT JOIN customers c ON t.customer_id = c.id
            LEFT JOIN currency cur ON t.currency_id = cur.id
            LEFT JOIN transactions_d td ON t.id = td.transaction_id
            ORDER BY t.timestamp DESC, t.id DESC
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
            SELECT t.id, t.customer_id, t.amount, t.currency_id, t.transaction_type, 
                   t.timestamp, t.ledger_balance, t.share_ref,
                   c.name as customer_name, c.phone as customer_phone,
                   COALESCE(cur.currency_name, 'ريال يمني (YER)') as currency_name,
                   COALESCE(td.detail_note, '') as detail_note
            FROM transactions t
            LEFT JOIN customers c ON t.customer_id = c.id
            LEFT JOIN currency cur ON t.currency_id = cur.id
            LEFT JOIN transactions_d td ON t.id = td.transaction_id
            WHERE t.customer_id = ?
            ORDER BY t.timestamp DESC, t.id DESC
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
            val txValues = ContentValues().apply {
                put("customer_id", tx.customerId)
                put("amount", tx.amount)
                put("currency_id", tx.currencyId)
                put("transaction_type", tx.transactionType)
                put("timestamp", tx.timestamp)
                put("ledger_balance", 0.0)
                put("share_ref", shareRef)
            }
            newId = db.insert("transactions", null, txValues)
            if (newId != -1L) {
                // Insert details into transactions_d
                val detailValues = ContentValues().apply {
                    put("transaction_id", newId)
                    put("detail_note", tx.detailNote)
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
            val txValues = ContentValues().apply {
                put("customer_id", tx.customerId)
                put("amount", tx.amount)
                put("currency_id", tx.currencyId)
                put("transaction_type", tx.transactionType)
                put("timestamp", tx.timestamp)
            }
            val rows = db.update("transactions", txValues, "id = ?", arrayOf(tx.id.toString()))
            if (rows > 0) {
                // Update transactions_d
                val detailValues = ContentValues().apply {
                    put("detail_note", tx.detailNote)
                    put("amount", tx.amount)
                }
                val dRows = db.update("transactions_d", detailValues, "transaction_id = ?", arrayOf(tx.id.toString()))
                if (dRows == 0) {
                    detailValues.put("transaction_id", tx.id)
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
            // Find customerId first
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
     * Recalculates the cumulative running ledger balance for a specific customer
     * across their entire transaction timeline (ordered by timestamp ASC, id ASC).
     */
    private fun recalculateCustomerBalances(db: SQLiteDatabase, customerId: Long) {
        val cursor = db.rawQuery(
            "SELECT id, amount, transaction_type FROM transactions WHERE customer_id = ? ORDER BY timestamp ASC, id ASC",
            arrayOf(customerId.toString())
        )

        var runningBalance = 0.0
        val updates = mutableListOf<Pair<Long, Double>>()

        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val amount = cursor.getDouble(1)
            val type = cursor.getString(2) ?: ""

            // له (لصالح العميل / دائن) = +
            // عليه (دين على العميل / مدين) = -
            if (type == "له") {
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
        return TransactionModel(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            customerId = cursor.getLong(cursor.getColumnIndexOrThrow("customer_id")),
            customerName = cursor.getString(cursor.getColumnIndexOrThrow("customer_name")) ?: "",
            customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("customer_phone")) ?: "",
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
            currencyId = cursor.getLong(cursor.getColumnIndexOrThrow("currency_id")),
            currencyName = cursor.getString(cursor.getColumnIndexOrThrow("currency_name")) ?: "ريال يمني (YER)",
            transactionType = cursor.getString(cursor.getColumnIndexOrThrow("transaction_type")) ?: "له",
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            ledgerBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("ledger_balance")),
            shareRef = cursor.getString(cursor.getColumnIndexOrThrow("share_ref")),
            detailNote = cursor.getString(cursor.getColumnIndexOrThrow("detail_note")) ?: ""
        )
    }

    // Helper for Currencies
    fun getAllCurrencies(): List<CurrencyModel> {
        val list = mutableListOf<CurrencyModel>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, currency_name FROM currency ORDER BY id ASC", null)
        while (cursor.moveToNext()) {
            list.add(
                CurrencyModel(
                    id = cursor.getLong(0),
                    currencyName = cursor.getString(1) ?: ""
                )
            )
        }
        cursor.close()
        return list
    }
}
