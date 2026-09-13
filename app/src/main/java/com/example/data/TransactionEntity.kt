package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountId: Long,
    val accountName: String,
    val type: String, // "له" (Credit) أو "عليه" (Debit)
    val amount: Double,
    val currency: String = "ريال يمني (YER)",
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val remainingBalance: Double = 0.0
)
