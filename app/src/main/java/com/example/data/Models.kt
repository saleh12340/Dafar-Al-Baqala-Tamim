package com.example.data

data class CustomerModel(
    val id: Long = 0L,
    val name: String,
    val phone: String = "",
    val balance: Double = 0.0,
    val groupId: Long? = null,
    val notes: String = "",
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
    val type: String = "DEBIT",  // 'DEBIT' or 'CREDIT'
    val currency: String = "YER",
    val date: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ledgerBalance: Double = 0.0,
    val shareRef: String? = null,
    val detailNote: String = ""
)

data class TransactionDetailModel(
    val id: Long = 0L,
    val transactionId: Long,
    val itemName: String? = null,
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val totalPrice: Double? = null,
    val detailNote: String? = null,
    val amount: Double? = null
)

data class CurrencyModel(
    val id: Long = 0L,
    val currencyName: String = "ريال يمني",
    val name: String = "ريال يمني",
    val symbol: String? = null
)

data class GroupModel(
    val id: Long = 0L,
    val groupName: String
)

data class ReminderModel(
    val id: Long = 0L,
    val customerId: Long? = null,
    val reminderDate: String = "",
    val date: String = "",
    val note: String = "",
    val isCompleted: Int = 0
)
