package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CustomerModel
import com.example.data.TransactionModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    customers: List<CustomerModel>,
    preselectedCustomerId: Long? = null,
    transactionToEdit: TransactionModel? = null,
    onDismiss: () -> Unit,
    onSave: (TransactionModel) -> Unit
) {
    var selectedCustomerId by remember {
        mutableStateOf(
            transactionToEdit?.customerId
                ?: preselectedCustomerId
                ?: customers.firstOrNull()?.id
                ?: 0L
        )
    }
    var transactionType by remember {
        mutableStateOf(transactionToEdit?.transactionType ?: "له")
    }
    var amountStr by remember {
        mutableStateOf(if (transactionToEdit != null) transactionToEdit.amount.toString() else "")
    }
    var currencyName by remember {
        mutableStateOf(transactionToEdit?.currencyName ?: "ريال يمني (YER)")
    }
    var detailNote by remember {
        mutableStateOf(transactionToEdit?.detailNote ?: "")
    }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    val availableCurrencies = listOf(
        "ريال يمني (YER)",
        "ريال سعودي (SAR)",
        "دولار أمريكي (USD)"
    )

    val selectedCustomer = customers.find { it.id == selectedCustomerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (transactionToEdit == null) "تسجيل قيد مالي جديد" else "تعديل القيد المالي",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Customer Selector
                if (preselectedCustomerId == null && transactionToEdit == null) {
                    ExposedDropdownMenuBox(
                        expanded = customerDropdownExpanded,
                        onExpandedChange = { customerDropdownExpanded = !customerDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: "اختر العميل",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العميل") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("select_customer_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = customerDropdownExpanded,
                            onDismissRequest = { customerDropdownExpanded = false }
                        ) {
                            customers.forEach { cust ->
                                DropdownMenuItem(
                                    text = { Text(cust.name) },
                                    onClick = {
                                        selectedCustomerId = cust.id
                                        customerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "العميل: ${selectedCustomer?.name ?: ""}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_lah_button")
                    ) {
                        Text(
                            text = "له (دائن/تسديد)",
                            color = if (transactionType == "له") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { transactionType = "عليه" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == "عليه") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_alayh_button")
                    ) {
                        Text(
                            text = "عليه (مدين/مشتريات)",
                            color = if (transactionType == "عليه") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("المبلغ") },
                    placeholder = { Text("0.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input"),
                    singleLine = true
                )

                // Currency selector dropdown
                ExposedDropdownMenuBox(
                    expanded = currencyDropdownExpanded,
                    onExpandedChange = { currencyDropdownExpanded = !currencyDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = currencyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العملة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = currencyDropdownExpanded,
                        onDismissRequest = { currencyDropdownExpanded = false }
                    ) {
                        availableCurrencies.forEach { curr ->
                            DropdownMenuItem(
                                text = { Text(curr) },
                                onClick = {
                                    currencyName = curr
                                    currencyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Description / Note (Saved into transactions_d)
                OutlinedTextField(
                    value = detailNote,
                    onValueChange = { detailNote = it },
                    label = { Text("البيان / تفاصيل الفاتورة (transactions_d)") },
                    placeholder = { Text("مثال: شراء كيس رز وسكر، أو دفعة نقدية") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_note_input"),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
                    if (amountVal > 0 && selectedCustomerId > 0) {
                        val currencyId = when {
                            currencyName.contains("سعودي") -> 2L
                            currencyName.contains("دولار") -> 3L
                            else -> 1L
                        }
                        val result = TransactionModel(
                            id = transactionToEdit?.id ?: 0L,
                            customerId = selectedCustomerId,
                            customerName = selectedCustomer?.name ?: "",
                            customerPhone = selectedCustomer?.phone ?: "",
                            amount = amountVal,
                            currencyId = currencyId,
                            currencyName = currencyName,
                            transactionType = transactionType,
                            timestamp = transactionToEdit?.timestamp ?: System.currentTimeMillis(),
                            detailNote = detailNote.trim()
                        )
                        onSave(result)
                    }
                },
                enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0 && selectedCustomerId > 0,
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text(if (transactionToEdit == null) "حفظ القيد" else "تحديث القيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
