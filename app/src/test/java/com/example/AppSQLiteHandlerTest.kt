package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSQLiteHandler
import com.example.data.CustomerModel
import com.example.data.TransactionModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSQLiteHandlerTest {

    private lateinit var context: Context
    private lateinit var sqliteHandler: AppSQLiteHandler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppSQLiteHandler.DATABASE_NAME)
        sqliteHandler = AppSQLiteHandler(context)
    }

    @Test
    fun testTablesAndSeedsCreation() {
        val currencies = sqliteHandler.getAllCurrencies()
        assertTrue("Currencies should have initial seed", currencies.isNotEmpty())

        val customers = sqliteHandler.getAllCustomers()
        assertNotNull(customers)
    }

    @Test
    fun testComprehensiveSchemaEntitiesAndView() {
        val db = sqliteHandler.writableDatabase

        // 1. Verify view transactions_tot_v exists and can be queried
        val viewCursor = db.rawQuery("SELECT * FROM transactions_tot_v", null)
        assertNotNull(viewCursor)
        viewCursor.close()

        // 2. Verify unique index transactions_share_ref_uq
        val indexCursor = db.rawQuery("PRAGMA index_list('transactions')", null)
        var foundShareRefIndex = false
        while (indexCursor.moveToNext()) {
            val idxName = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
            if (idxName == "transactions_share_ref_uq") {
                foundShareRefIndex = true
                break
            }
        }
        indexCursor.close()
        assertTrue("Index transactions_share_ref_uq should exist", foundShareRefIndex)

        // 3. Verify groups table
        db.execSQL("INSERT INTO groups (group_name) VALUES ('مجموعة كبار العملاء');")
        val grpCursor = db.rawQuery("SELECT id, group_name FROM groups WHERE group_name = 'مجموعة كبار العملاء'", null)
        assertTrue(grpCursor.moveToFirst())
        val grpId = grpCursor.getLong(0)
        grpCursor.close()

        // 4. Verify customers table with new fields: balance, group_id, notes
        val newCustId = sqliteHandler.insertCustomer(
            CustomerModel(
                name = "عميل مميز",
                phone = "770000000",
                balance = 1500.0,
                groupId = grpId,
                notes = "ملاحظات تفصيلية للعميل"
            )
        )
        assertTrue(newCustId > 0)
        val fetchedCust = sqliteHandler.getCustomerById(newCustId)
        assertNotNull(fetchedCust)
        assertEquals(grpId, fetchedCust?.groupId)
        assertEquals("ملاحظات تفصيلية للعميل", fetchedCust?.notes)

        // 5. Verify transactions and transactions_d with new columns
        val txId = sqliteHandler.insertTransaction(
            TransactionModel(
                customerId = newCustId,
                type = "CREDIT",
                transactionType = "له",
                amount = 2500.0,
                currency = "YER",
                date = "2026-09-13 14:00:00",
                note = "سداد نقدي",
                shareRef = "TEST-REF-999"
            )
        )
        assertTrue(txId > 0)

        // 6. Verify detail row in transactions_d
        val dCursor = db.rawQuery("SELECT item_name, total_price FROM transactions_d WHERE transaction_id = ?", arrayOf(txId.toString()))
        assertTrue("transactions_d should contain detail record", dCursor.moveToFirst())
        assertEquals("سداد نقدي", dCursor.getString(0))
        assertEquals(2500.0, dCursor.getDouble(1), 0.001)
        dCursor.close()

        // 7. Verify reminders table
        db.execSQL("INSERT INTO reminders (customer_id, reminder_date, note, is_completed) VALUES (?, ?, ?, ?)",
            arrayOf(newCustId, "2026-10-01", "موعد سداد الدفعة القادمة", 0)
        )
        val remCursor = db.rawQuery("SELECT customer_id, reminder_date, note, is_completed FROM reminders WHERE customer_id = ?", arrayOf(newCustId.toString()))
        assertTrue("reminders row should exist", remCursor.moveToFirst())
        assertEquals("2026-10-01", remCursor.getString(1))
        assertEquals("موعد سداد الدفعة القادمة", remCursor.getString(2))
        assertEquals(0, remCursor.getInt(3))
        remCursor.close()

        // 8. Verify transactions_tot_v reflects transaction
        val vCheckCursor = db.rawQuery("SELECT total_lah, total_amount FROM transactions_tot_v WHERE customer_id = ?", arrayOf(newCustId.toString()))
        assertTrue(vCheckCursor.moveToFirst())
        assertEquals(2500.0, vCheckCursor.getDouble(0), 0.001)
        assertEquals(2500.0, vCheckCursor.getDouble(1), 0.001)
        vCheckCursor.close()
    }

    @Test
    fun testCustomerCRUD() {
        val newCustomer = CustomerModel(
            name = "محمد سالم",
            phone = "771234567",
            accountDetails = "حساب عميل آجل"
        )
        val customerId = sqliteHandler.insertCustomer(newCustomer)
        assertTrue("Customer ID should be valid", customerId > 0)

        val fetched = sqliteHandler.getCustomerById(customerId)
        assertNotNull(fetched)
        assertEquals("محمد سالم", fetched?.name)
        assertEquals("771234567", fetched?.phone)

        val updated = fetched!!.copy(name = "محمد سالم معدل")
        val updateSuccess = sqliteHandler.updateCustomer(updated)
        assertTrue(updateSuccess)

        val fetchedAgain = sqliteHandler.getCustomerById(customerId)
        assertEquals("محمد سالم معدل", fetchedAgain?.name)
    }

    @Test
    fun testTransactionCreationAndRunningBalanceRecalculation() {
        val customerId = sqliteHandler.insertCustomer(
            CustomerModel(name = "علي حسن", phone = "772222222")
        )

        // 1. Insert "له" (Credit +1000)
        val tx1Id = sqliteHandler.insertTransaction(
            TransactionModel(
                customerId = customerId,
                amount = 1000.0,
                transactionType = "له",
                timestamp = 1000L,
                detailNote = "دفعة حساب أولية"
            )
        )
        assertTrue(tx1Id > 0)

        var customerTxs = sqliteHandler.getTransactionsForCustomer(customerId)
        assertEquals(1, customerTxs.size)
        assertEquals(1000.0, customerTxs[0].ledgerBalance, 0.001)

        // 2. Insert "عليه" (Debit -400)
        val tx2Id = sqliteHandler.insertTransaction(
            TransactionModel(
                customerId = customerId,
                amount = 400.0,
                transactionType = "عليه",
                timestamp = 2000L,
                detailNote = "مشتريات مواد غذائية"
            )
        )
        assertTrue(tx2Id > 0)

        customerTxs = sqliteHandler.getTransactionsForCustomer(customerId)
        assertEquals(2, customerTxs.size)
        // tx2 is latest (timestamp 2000)
        val tx2 = customerTxs.find { it.id == tx2Id }
        val tx1 = customerTxs.find { it.id == tx1Id }
        assertEquals(1000.0, tx1?.ledgerBalance ?: 0.0, 0.001)
        assertEquals(600.0, tx2?.ledgerBalance ?: 0.0, 0.001)

        // 3. Delete tx1, verify tx2 running balance recalculates
        val deleteSuccess = sqliteHandler.deleteTransaction(tx1Id)
        assertTrue(deleteSuccess)

        customerTxs = sqliteHandler.getTransactionsForCustomer(customerId)
        assertEquals(1, customerTxs.size)
        // Now only tx2 exists: 0 - 400 = -400
        assertEquals(-400.0, customerTxs[0].ledgerBalance, 0.001)
    }

    @Test
    fun testExportAndImportDatabase() {
        val customerId = sqliteHandler.insertCustomer(
            CustomerModel(name = "عميل التجربة", phone = "773333333")
        )
        sqliteHandler.insertTransaction(
            TransactionModel(
                customerId = customerId,
                amount = 500.0,
                transactionType = "له",
                detailNote = "حوالة بنكية"
            )
        )

        val outputStream = ByteArrayOutputStream()
        val exportSuccess = sqliteHandler.exportDatabaseFile(outputStream)
        assertTrue("Export should succeed", exportSuccess)
        val exportedBytes = outputStream.toByteArray()
        assertTrue("Exported bytes should be > 0", exportedBytes.isNotEmpty())

        val inputStream = ByteArrayInputStream(exportedBytes)
        val importSuccess = sqliteHandler.importDatabaseFile(inputStream)
        assertTrue("Import should succeed", importSuccess)

        val customersAfterImport = sqliteHandler.getAllCustomers()
        assertTrue(customersAfterImport.any { it.name == "عميل التجربة" })
    }
}
