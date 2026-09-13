package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AccountEntity
import com.example.data.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    accounts: List<AccountEntity>,
    preselectedAccountId: Long? = null,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    var selectedAccountId by remember {
        mutableStateOf(preselectedAccountId ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var transactionType by remember { mutableStateOf("له") } // "له" or "عليه"
    var amountStr by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("ريال يمني (YER)") }
    var description by remember { mutableStateOf("") }

    val selectedAccount = accounts.find { it.id == selectedAccountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "تسجيل عملية مالية جديدة", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account selector dropdown or info
                if (accounts.isEmpty()) {
                    Text("لايوجد حسابات مسجلة. يرجى إضافة حساب أولاً.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("الحساب: ${selectedAccount?.name ?: ""}", fontWeight = FontWeight.SemiBold)
                }

                // Type selector: له / عليه
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { transactionType = "له" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == "له") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("type_lah_button")
                    ) {
                        Text("له (لصالح العميل)", color = if (transactionType == "له") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { transactionType = "عليه" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == "عليه") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("type_alayh_button")
                    ) {
                        Text("عليه (دين على العميل)", color = if (transactionType == "عليه") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("transaction_amount_input"),
                    singleLine = true
                )

                // Currency
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    label = { Text("العملة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Description / البيان
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("البيان / التفاصيل") },
                    modifier = Modifier.fillMaxWidth().testTag("transaction_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (selectedAccount != null && amt > 0) {
                        val transaction = TransactionEntity(
                            accountId = selectedAccount.id,
                            accountName = selectedAccount.name,
                            type = transactionType,
                            amount = amt,
                            currency = currency,
                            description = description.ifBlank { "عملية ${if (transactionType == "له") "له" else "عليه"}" },
                            timestamp = System.currentTimeMillis(),
                            remainingBalance = amt
                        )
                        onSave(transaction)
                    }
                },
                enabled = accounts.isNotEmpty() && amountStr.toDoubleOrNull() != null && amountStr.toDoubleOrNull()!! > 0,
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text("حفظ العملية")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
