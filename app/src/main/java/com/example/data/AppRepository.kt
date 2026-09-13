package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class AppRepository(private val sqliteHandler: AppSQLiteHandler) {

    val allCustomers: Flow<List<CustomerModel>> = flow {
        emit(sqliteHandler.getAllCustomers())
        sqliteHandler.databaseEvents.collect {
            emit(sqliteHandler.getAllCustomers())
        }
    }.flowOn(Dispatchers.IO)

    val allTransactions: Flow<List<TransactionModel>> = flow {
        emit(sqliteHandler.getAllTransactions())
        sqliteHandler.databaseEvents.collect {
            emit(sqliteHandler.getAllTransactions())
        }
    }.flowOn(Dispatchers.IO)

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionModel>> = flow {
        emit(sqliteHandler.getTransactionsForCustomer(customerId))
        sqliteHandler.databaseEvents.collect {
            emit(sqliteHandler.getTransactionsForCustomer(customerId))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun insertCustomer(customer: CustomerModel): Long = withContext(Dispatchers.IO) {
        sqliteHandler.insertCustomer(customer)
    }

    suspend fun updateCustomer(customer: CustomerModel): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.updateCustomer(customer)
    }

    suspend fun deleteCustomer(id: Long): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.deleteCustomer(id)
    }

    suspend fun getCustomerById(id: Long): CustomerModel? = withContext(Dispatchers.IO) {
        sqliteHandler.getCustomerById(id)
    }

    suspend fun insertTransaction(tx: TransactionModel): Long = withContext(Dispatchers.IO) {
        sqliteHandler.insertTransaction(tx)
    }

    suspend fun updateTransaction(tx: TransactionModel): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.updateTransaction(tx)
    }

    suspend fun deleteTransaction(id: Long): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.deleteTransaction(id)
    }

    suspend fun importDatabase(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.importDatabaseFile(inputStream)
    }

    suspend fun exportDatabase(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        sqliteHandler.exportDatabaseFile(outputStream)
    }

    suspend fun getAllCurrencies(): List<CurrencyModel> = withContext(Dispatchers.IO) {
        sqliteHandler.getAllCurrencies()
    }
}
