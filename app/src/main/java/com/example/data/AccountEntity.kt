package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String,
    val debtCeilingEnabled: Boolean = false,
    val debtCeilingAmount: Double = 0.0,
    val notes: String = "",
    val preferredMethod: String = "واتساب", // SMS أو واتساب
    val autoSendOption: String = "بعد التأكيد" // تلقائياً، بعد التأكيد، لا ترسل
)
