package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactsHelper
import com.example.data.CustomerModel

@Composable
fun AddEditCustomerDialog(
    customerToEdit: CustomerModel? = null,
    onDismiss: () -> Unit,
    onSave: (CustomerModel) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(customerToEdit?.phone ?: "") }
    var accountDetails by remember { mutableStateOf(customerToEdit?.accountDetails ?: "") }

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val contactInfo = ContactsHelper.extractContactDetails(context, uri)
            if (contactInfo != null) {
                name = contactInfo.first
                if (contactInfo.second.isNotBlank()) {
                    phone = contactInfo.second
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (customerToEdit == null) "إضافة حساب عميل جديد" else "تعديل بيانات الحساب",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button to Pick from Phone Contacts
                OutlinedButton(
                    onClick = { contactPickerLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_contact_button")
                ) {
                    Icon(imageVector = Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد الاسم والرقم من جهات الاتصال", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العميل أو الحساب *") },
                    placeholder = { Text("مثال: محمد عبدالله") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    placeholder = { Text("مثال: 770000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = {
                        IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                            Icon(imageVector = Icons.Default.Contacts, contentDescription = "اختيار من جهات الاتصال")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountDetails,
                    onValueChange = { accountDetails = it },
                    label = { Text("تفاصيل الحساب / العنوان / ملاحظات") },
                    placeholder = { Text("مثال: عميل المحل بالشارع العام") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_details_input"),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val result = CustomerModel(
                            id = customerToEdit?.id ?: 0L,
                            name = name.trim(),
                            phone = phone.trim(),
                            accountDetails = accountDetails.trim()
                        )
                        onSave(result)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(if (customerToEdit == null) "إضافة الحساب" else "تحديث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
