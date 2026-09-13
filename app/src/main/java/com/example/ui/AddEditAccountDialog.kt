package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AccountEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountDialog(
    accountToEdit: AccountEntity? = null,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onOpenHelp: () -> Unit,
    onImportContact: () -> Unit,
    onVoiceInput: (FieldType) -> Unit
) {
    var name by remember { mutableStateOf(accountToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(accountToEdit?.phone ?: "") }
    var debtCeilingEnabled by remember { mutableStateOf(accountToEdit?.debtCeilingEnabled ?: false) }
    var debtCeilingAmount by remember { mutableStateOf(accountToEdit?.debtCeilingAmount?.toString() ?: "0.0") }
    var notes by remember { mutableStateOf(accountToEdit?.notes ?: "") }
    var preferredMethod by remember { mutableStateOf(accountToEdit?.preferredMethod ?: "واتساب") }
    var autoSendOption by remember { mutableStateOf(accountToEdit?.autoSendOption ?: "بعد التأكيد") }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (accountToEdit == null) "إضافة حساب جديد" else "تعديل بيانات الحساب",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onOpenHelp, modifier = Modifier.testTag("help_button")) {
                    Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "شرح", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name Field with Voice & Contacts
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الحساب (العميل / المورد)") },
                    modifier = Modifier.fillMaxWidth().testTag("account_name_input"),
                    singleLine = true,
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { onVoiceInput(FieldType.NAME) }) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "إدخال صوتي")
                            }
                            IconButton(onClick = onImportContact, modifier = Modifier.testTag("import_contact_button")) {
                                Icon(imageVector = Icons.Default.Contacts, contentDescription = "استيراد جهة اتصال")
                            }
                        }
                    }
                )

                // Phone Field
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف (جوال)") },
                    modifier = Modifier.fillMaxWidth().testTag("account_phone_input"),
                    singleLine = true
                )

                // Debt Ceiling Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تفعيل سقف المديونية", fontWeight = FontWeight.Medium)
                            Switch(
                                checked = debtCeilingEnabled,
                                onCheckedChange = { debtCeilingEnabled = it },
                                modifier = Modifier.testTag("debt_ceiling_switch")
                            )
                        }
                        if (debtCeilingEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = debtCeilingAmount,
                                onValueChange = { debtCeilingAmount = it },
                                label = { Text("الحد الأقصى للدين") },
                                modifier = Modifier.fillMaxWidth().testTag("debt_ceiling_amount_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                // Notes Field with Voice
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات خاصة بالحساب") },
                    modifier = Modifier.fillMaxWidth().testTag("account_notes_input"),
                    trailingIcon = {
                        IconButton(onClick = { onVoiceInput(FieldType.NOTES) }) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "إدخال صوتي للملاحظات")
                        }
                    }
                )

                // Preferred Message Method
                Text("طريقة إرسال الرسائل المفضلة:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferredMethod == "واتساب",
                        onClick = { preferredMethod = "واتساب" },
                        label = { Text("واتساب WhatsApp") }
                    )
                    FilterChip(
                        selected = preferredMethod == "SMS",
                        onClick = { preferredMethod = "SMS" },
                        label = { Text("رسالة SMS") }
                    )
                }

                // Auto Send Option on Transaction
                Text("خيار إرسال الرسائل عند إضافة عملية:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Column {
                    listOf("تلقائياً", "بعد التأكيد", "لا ترسل").forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = autoSendOption == option,
                                onClick = { autoSendOption = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val ceiling = debtCeilingAmount.toDoubleOrNull() ?: 0.0
                        val updated = (accountToEdit ?: AccountEntity(name = "", phone = "")).copy(
                            name = name,
                            phone = phone,
                            debtCeilingEnabled = debtCeilingEnabled,
                            debtCeilingAmount = ceiling,
                            notes = notes,
                            preferredMethod = preferredMethod,
                            autoSendOption = autoSendOption
                        )
                        onSave(updated)
                    }
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_account_button")) {
                Text("إلغاء")
            }
        }
    )
}

enum class FieldType {
    NAME, NOTES
}
