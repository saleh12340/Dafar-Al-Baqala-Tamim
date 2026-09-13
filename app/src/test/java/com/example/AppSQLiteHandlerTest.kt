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
