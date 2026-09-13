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
    onDeleteTransaction: (Long) -> Unit,
    onCloseAccount: () -> Unit = {}
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")) }

    var transactionToDelete by remember { mutableStateOf<TransactionModel?>(null) }
    var showCloseAccountDialog by remember { mutableStateOf(false) }

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
                    // PDF Print Statement (ميزة تصدير وطباعة PDF)
                    IconButton(
                        onClick = { printCustomerStatementPdf(context, customer, transactions) },
                        modifier = Modifier.testTag("print_pdf_statement_button")
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "طباعة / تصدير PDF")
                    }

                    // Share Statement
                    IconButton(
                        onClick = { shareCustomerStatement(context, customer, transactions) },
                        modifier = Modifier.testTag("share_statement_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة كشف الحساب")
                    }

                    // Close Account Action (ميزة إغلاق الحساب من دفتر الحسابات)
                    IconButton(
                        onClick = { showCloseAccountDialog = true },
                        modifier = Modifier.testTag("close_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockReset,
                            contentDescription = "إغلاق وتصفية الحساب",
                            tint = MaterialTheme.colorScheme.error
                        )
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

    // Close Account Confirmation Dialog (ميزة إغلاق الحساب في دفتر الحسابات)
    if (showCloseAccountDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAccountDialog = false },
            title = { Text("تأكيد إغلاق وتصفية الحساب") },
            text = {
                Text(
                    "هل تريد إغلاق هذا الحساب؟\n\nستقوم هذه العملية بحذف كافة العمليات القديمة وتجميعها في قيد افتتاحي واحد بالرصيد المتبقي الحالي (${customer.netBalance}).\n\nتُستخدم هذه الميزة لبدء صفحة جديدة في الدفتر مع الحفاظ على الرصيد الدقيق."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseAccountDialog = false
                        onCloseAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد إغلاق الحساب")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAccountDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun printCustomerStatementPdf(context: Context, customer: CustomerModel, transactions: List<TransactionModel>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
    val webView = android.webkit.WebView(context)
    val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))

    val rowsHtml = StringBuilder()
    transactions.forEachIndexed { idx, tx ->
        val typeBadge = if (tx.transactionType == "له") {
            "<span style='color: #2e7d32; font-weight: bold;'>له (دائن)</span>"
        } else {
            "<span style='color: #c62828; font-weight: bold;'>عليه (مدين)</span>"
        }
        val dateStr = dateFormat.format(Date(tx.timestamp))
        rowsHtml.append("""
            <tr>
                <td style='border: 1px solid #ddd; padding: 8px; text-align: center;'>${idx + 1}</td>
                <td style='border: 1px solid #ddd; padding: 8px;'>$dateStr</td>
                <td style='border: 1px solid #ddd; padding: 8px;'>$typeBadge</td>
                <td style='border: 1px solid #ddd; padding: 8px;'>${tx.detailNote.ifBlank { "-" }}</td>
                <td style='border: 1px solid #ddd; padding: 8px; font-weight: bold;'>${tx.amount} ${tx.currencyName}</td>
                <td style='border: 1px solid #ddd; padding: 8px;'>${tx.ledgerBalance}</td>
            </tr>
        """.trimIndent())
    }

    val htmlDocument = """
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: sans-serif; margin: 20px; color: #212121; }
                .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 12px; margin-bottom: 16px; }
                .title { font-size: 20px; font-weight: bold; margin: 0; }
                .subtitle { font-size: 14px; color: #555; margin-top: 4px; }
                .info-box { background-color: #f5f5f5; border-radius: 6px; padding: 12px; margin-bottom: 16px; }
                .info-row { margin-bottom: 6px; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
                th { background-color: #f0f0f0; border: 1px solid #ddd; padding: 8px; text-align: center; font-weight: bold; }
                .totals { margin-top: 20px; border-top: 2px solid #333; padding-top: 10px; }
                .total-line { font-size: 15px; font-weight: bold; margin-bottom: 4px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1 class="title">بقالة العزي للمواد الغذائية والتموينية</h1>
                <div class="subtitle">هاتف: 726425052 - كشف حساب تفصيلي</div>
            </div>
            <div class="info-box">
                <div class="info-row"><strong>اسم العميل:</strong> ${customer.name}</div>
                <div class="info-row"><strong>رقم الهاتف:</strong> ${customer.phone.ifBlank { "غير متوفر" }}</div>
                <div class="info-row"><strong>تاريخ التقرير:</strong> ${SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date())}</div>
            </div>
            <table>
                <thead>
                    <tr>
                        <th>م</th>
                        <th>التاريخ والوقت</th>
                        <th>النوع</th>
                        <th>البيان والتفاصيل</th>
                        <th>المبلغ</th>
                        <th>الرصيد التراكمي</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
            <div class="totals">
                <div class="total-line">إجمالي له (المدفوع): ${customer.totalLah}</div>
                <div class="total-line">إجمالي عليه (المشتريات والديون): ${customer.totalAlayh}</div>
                <div class="total-line" style="color: ${if (customer.netBalance >= 0) "#2e7d32" else "#c62828"}; font-size: 17px;">
                    الرصيد النهائي المتبقي: ${customer.netBalance}
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

    webView.loadDataWithBaseURL(null, htmlDocument, "text/html; charset=utf-8", "utf-8", null)
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter("كشف_حساب_${customer.name}")
            printManager.print(
                "كشف حساب - ${customer.name}",
                printAdapter,
                android.print.PrintAttributes.Builder().build()
            )
        }
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
