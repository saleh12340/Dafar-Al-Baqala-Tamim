package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.AppSQLiteHandler
import com.example.data.CurrencyModel
import com.example.data.CustomerModel
import com.example.data.TransactionModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccountingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    init {
        val sqliteHandler = AppSQLiteHandler.getInstance(application)
        repository = AppRepository(sqliteHandler)
    }

    val customers: StateFlow<List<CustomerModel>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionModel>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected customer for Ledger Navigation View
    private val _selectedCustomer = MutableStateFlow<CustomerModel?>(null)
    val selectedCustomer: StateFlow<CustomerModel?> = _selectedCustomer.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerTransactions: StateFlow<List<TransactionModel>> = _selectedCustomer
        .flatMapLatest { customer ->
            if (customer != null) {
                repository.getTransactionsForCustomer(customer.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("الكل")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _sortDescending = MutableStateFlow(true)
    val sortDescending: StateFlow<Boolean> = _sortDescending.asStateFlow()

    fun selectCustomer(customer: CustomerModel?) {
        _selectedCustomer.value = customer
    }

    fun refreshSelectedCustomer() {
        val currentId = _selectedCustomer.value?.id ?: return
        viewModelScope.launch {
            val updated = repository.getCustomerById(currentId)
            _selectedCustomer.value = updated
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    fun toggleSortOrder() {
        _sortDescending.value = !_sortDescending.value
    }

    // Customer Operations
    fun insertCustomer(customer: CustomerModel, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCustomer(customer)
            onComplete(id)
        }
    }

    fun updateCustomer(customer: CustomerModel, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            refreshSelectedCustomer()
            onComplete()
        }
    }

    fun deleteCustomer(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(id)
            if (_selectedCustomer.value?.id == id) {
                _selectedCustomer.value = null
            }
            onComplete()
        }
    }

    // Transaction Operations
    fun insertTransaction(tx: TransactionModel, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertTransaction(tx)
            refreshSelectedCustomer()
            onComplete()
        }
    }

    fun updateTransaction(tx: TransactionModel, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTransaction(tx)
            refreshSelectedCustomer()
            onComplete()
        }
    }

    fun deleteTransaction(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            refreshSelectedCustomer()
            onComplete()
        }
    }

    fun closeAccount(customerId: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.closeAccount(customerId)
            refreshSelectedCustomer()
            onComplete(success)
        }
    }

    // Database Import and Export with ContentResolver
    fun importDatabase(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    onResult(false, "تعذر فتح ملف قاعدة البيانات")
                    return@launch
                }
                val success = repository.importDatabase(inputStream)
                if (success) {
                    refreshSelectedCustomer()
                    onResult(true, null)
                } else {
                    onResult(false, "فشل التحقق من سلامة الملف أو تلف قاعدة البيانات")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "خطأ أثناء استيراد قاعدة البيانات")
            }
        }
    }

    fun exportDatabase(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    onResult(false, "تعذر إنشاء ملف النسخة الاحتياطية")
                    return@launch
                }
                val success = repository.exportDatabase(outputStream)
                if (success) {
                    onResult(true, null)
                } else {
                    onResult(false, "فشل تصدير قاعدة البيانات")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "خطأ أثناء تصدير قاعدة البيانات")
            }
        }
    }
}
