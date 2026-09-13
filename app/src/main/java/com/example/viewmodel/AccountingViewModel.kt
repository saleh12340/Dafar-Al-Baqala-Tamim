package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountEntity
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class AccountingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AppRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppRepository(db.accountDao(), db.transactionDao())
    }

    val accounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter & Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCurrency = MutableStateFlow("الكل")
    val selectedCurrency: StateFlow<String> = _selectedCurrency

    private val _timeFilter = MutableStateFlow("الكل") // الكل، يومي، شهري، سنوي
    val timeFilter: StateFlow<String> = _timeFilter

    private val _subTimeFilter = MutableStateFlow("الكل") // اليوم، الأمس، الأسبوع الماضي، الشهر الحالي، الكل
    val subTimeFilter: StateFlow<String> = _subTimeFilter

    private val _sortDescending = MutableStateFlow(true) // الأحدث أولاً
    val sortDescending: StateFlow<Boolean> = _sortDescending

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    fun setTimeFilter(filter: String) {
        _timeFilter.value = filter
    }

    fun setSubTimeFilter(filter: String) {
        _subTimeFilter.value = filter
    }

    fun toggleSortOrder() {
        _sortDescending.value = !_sortDescending.value
    }

    // Account Actions
    fun insertAccount(account: AccountEntity, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertAccount(account)
            onComplete(id)
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }

    // Transaction Actions
    fun insertTransaction(transaction: TransactionEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            onComplete()
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
