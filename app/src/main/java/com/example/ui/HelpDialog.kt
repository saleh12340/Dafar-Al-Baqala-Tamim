package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(text = "تعليمات وشرح تطبيق بقالة العزي", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("مرحباً بك في دليل استخدام دفتر حسابات بقالة العزي:", fontWeight = FontWeight.SemiBold)
                Text("1. إضافة الحسابات: يمكنك إدخال اسم العميل أو استيراده مباشرة من جهات الاتصال أو عبر الإدخال الصوتي.")
                Text("2. سقف المديونية: يتيح لك تحديد حد أقصى لدين العميل مع تنبيهك فور تجاوزه.")
                Text("3. سجل العمليات والتقارير: عرض كافة العمليات المالية تنازلياً مع إمكانية الفلترة حسب الوقت والعملة والبحث الفوري.")
                Text("4. الطباعة الحرارية والتصدير: دعم طابعات الفواتير 80mm عبر البلوتوث وتصدير التقارير بصيغ PDF وإكسل متضمنة اسم بقالة العزي ورقم الهاتف (726425052).")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("close_help_button")) {
                Text("فهمت")
            }
        }
    )
}
