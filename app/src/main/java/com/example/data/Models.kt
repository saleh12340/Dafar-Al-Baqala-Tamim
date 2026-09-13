package com.example.data

data class CustomerModel(
    val id: Long = 0L,
    val name: String,
    val phone: String = "",
    val accountDetails: String = "",
    val createdAt: String = "",
    val totalLah: Double = 0.0,
    val totalAlayh: Double = 0.0,
    val netBalance: Double = 0.0
)

data class TransactionModel(
    val id: Long = 0L,
    val customerId: Long,
    val customerName: String = "",
    val customerPhone: String = "",
    val amount: Double,
    val currencyId: Long = 1L,
    val currencyName: String = "ريال يمني (YER)",
    val transactionType: String, // "له" (Credit) أو "عليه" (Debit)
    val timestamp: Long = System.currentTimeMillis(),
    val ledgerBalance: Double = 0.0,
    val shareRef: String? = null,
    val detailNote: String = ""
)

data class CurrencyModel(
    val id: Long = 0L,
    val currencyName: String
)

data class GroupModel(
    val id: Long = 0L,
    val groupName: String
)

data class ReminderModel(
    val id: Long = 0L,
    val date: String,
    val note: String
)
