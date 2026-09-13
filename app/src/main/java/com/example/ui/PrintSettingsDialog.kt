package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrintSettingsDialog(onDismiss: () -> Unit, onTestPrint: () -> Unit) {
    var printerName by remember { mutableStateOf("طابعة حرارية 80mm (Bluetooth)") }
    var isConnected by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "إعدادات الطباعة الحرارية (80mm)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = printerName, fontWeight = FontWeight.SemiBold)
                }

                Text(
                    text = "الحالة: ${if (isConnected) "متصل وجاهز للطباعة" else "غير متصل"}",
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                OutlinedButton(
                    onClick = { isConnected = !isConnected },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isConnected) "إلغاء الاقتران" else "بحث عن أجهزة بلوتوث الاقتران")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTestPrint()
                    onDismiss()
                },
                modifier = Modifier.testTag("test_print_button")
            ) {
                Icon(imageVector = Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("طباعة تجريبية")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
