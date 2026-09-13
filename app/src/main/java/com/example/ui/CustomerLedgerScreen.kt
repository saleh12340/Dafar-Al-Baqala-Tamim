package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerModel
import com.example.data.TransactionModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(
    customer: CustomerModel,
    transactions: List<TransactionModel>,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (TransactionModel) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")) }

    var transactionToDelete by remember { mutableStateOf<TransactionModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (customer.phone.isNotBlank()) {
                            Text(text = "هاتف: ${customer.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ledger_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareCustomerStatement(context, customer, transactions) },
                        modifier = Modifier.testTag("share_statement_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة كشف الحساب")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("fab_add_customer_transaction")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة قيد للعميل")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Customer Balances Summary Header Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("customer_summary_card"),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("كشف حساب العميل", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(customer.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        if (customer.accountDetails.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = customer.accountDetails,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("إجمالي له", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${customer.totalLah}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("إجمالي عليه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${customer.totalAlayh}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الرصيد المتبقي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${customer.netBalance}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (customer.netBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل العمليات والقيود (${transactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "مرتبة بالتسلسل الزمني",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا توجد قيود مسجلة لهذا الحساب حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onAddTransaction) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة أول قيد مالي")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ledger_transaction_item_${tx.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (tx.transactionType == "له") {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        }
                                    ) {
                                        Text(
                                            text = if (tx.transactionType == "له") "له (دائن/تسديد)" else "عليه (مدين/مشتريات)",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.transactionType == "له") {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            }
                                        )
                                    }

                                    Text(
                                        text = "${tx.amount} ${tx.currencyName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (tx.transactionType == "له") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (tx.detailNote.isNotBlank()) {
                                    Text(
                                        text = "البيان: ${tx.detailNote}",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormat.format(Date(tx.timestamp)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "الرصيد التراكمي: ${tx.ledgerBalance}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { shareTransactionInvoice(context, customer, tx) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "مشاركة السند",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onEditTransaction(tx) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل العملية",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { transactionToDelete = tx },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف العملية",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("تأكيد حذف القيد المالي") },
            text = {
                Text(
                    "هل أنت متأكد من حذف هذه العملية بمبلغ ${transactionToDelete?.amount} ${transactionToDelete?.currencyName}؟\nسيتم إعادة احتساب الرصيد التراكمي تلقائياً."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionToDelete?.id?.let { onDeleteTransaction(it) }
                        transactionToDelete = null
                    }
                ) {
                    Text("نعم، حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun shareTransactionInvoice(context: Context, customer: CustomerModel, tx: TransactionModel) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
    val invoiceText = """
        بقالة العزي - هاتف: 726425052
        =============================
        سند عملية مالية رقم: #${tx.id}
        العميل: ${customer.name}
        رقم الهاتف: ${customer.phone.ifBlank { "غير متوفر" }}
        -----------------------------
        نوع العملية: ${if (tx.transactionType == "له") "له (تسديد / دفعة)" else "عليه (دين / مشتريات)"}
        المبلغ: ${tx.amount} ${tx.currencyName}
        البيان: ${tx.detailNote.ifBlank { "بدون بيان" }}
        التاريخ: ${dateFormat.format(Date(tx.timestamp))}
        -----------------------------
        الرصيد التراكمي الحالي: ${tx.ledgerBalance}
        الرصيد الصافي للعميل: ${customer.netBalance}
        =============================
        شكراً لتعاملكم مع بقالة العزي
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "سند عملية - ${customer.name}")
        putExtra(Intent.EXTRA_TEXT, invoiceText)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة سند العملية عبر:"))
}

private fun shareCustomerStatement(context: Context, customer: CustomerModel, transactions: List<TransactionModel>) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    val sb = StringBuilder()
    sb.appendLine("كشف حساب رسمي - بقالة العزي")
    sb.appendLine("هاتف: 726425052")
    sb.appendLine("=============================")
    sb.appendLine("العميل: ${customer.name}")
    if (customer.phone.isNotBlank()) sb.appendLine("الهاتف: ${customer.phone}")
    sb.appendLine("إجمالي له: ${customer.totalLah}")
    sb.appendLine("إجمالي عليه: ${customer.totalAlayh}")
    sb.appendLine("الرصيد الصافي: ${customer.netBalance}")
    sb.appendLine("-----------------------------")
    sb.appendLine("العمليات والقيود المسجلة:")
    transactions.forEachIndexed { index, tx ->
        val dateStr = dateFormat.format(Date(tx.timestamp))
        sb.appendLine("${index + 1}. $dateStr | ${tx.transactionType}: ${tx.amount} ${tx.currencyName} | رصيد: ${tx.ledgerBalance} | ${tx.detailNote}")
    }
    sb.appendLine("=============================")
    sb.appendLine("بقالة العزي - خدمة العملاء: 726425052")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "كشف حساب - ${customer.name}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب عبر:"))
}
