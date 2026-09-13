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
    onCloseAccount: () -> Unit = {},
    onQuickAddTransaction: (amount: Double, type: String, detailNote: String, currencyId: Long, dateMillis: Long) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")) }
    val displayDateFormat = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US) }

    var transactionToDelete by remember { mutableStateOf<TransactionModel?>(null) }
    var showCloseAccountDialog by remember { mutableStateOf(false) }

    // Search inside Customer Ledger
    var isSearchActive by remember { mutableStateOf(false) }
    var ledgerSearchQuery by remember { mutableStateOf("") }

    // Quick Entry Form States (مطابق لشاشة "إضافة مبلغ" في دفتر الحسابات)
    var quickAmountText by remember { mutableStateOf("") }
    var quickDetailsText by remember { mutableStateOf("") }
    var selectedCurrencyId by remember { mutableStateOf(1L) } // 1: يمني, 2: سعودي, 3: دولار
    var showQuickCalcChips by remember { mutableStateOf(false) }
    var lastAddedBanner by remember { mutableStateOf<Pair<String, TransactionModel?>?>(null) }

    // Filter transactions by ledger search query
    val displayedTransactions = remember(transactions, ledgerSearchQuery) {
        if (ledgerSearchQuery.isBlank()) {
            transactions
        } else {
            transactions.filter {
                it.detailNote.contains(ledgerSearchQuery, ignoreCase = true) ||
                it.amount.toString().contains(ledgerSearchQuery) ||
                it.transactionType.contains(ledgerSearchQuery)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = ledgerSearchQuery,
                            onValueChange = { ledgerSearchQuery = it },
                            placeholder = { Text("بحث في قيود العميل...", fontSize = 13.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    ledgerSearchQuery = ""
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "إلغاء البحث")
                                }
                            }
                        )
                    } else {
                        Column {
                            Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (customer.phone.isNotBlank()) {
                                Text(text = "هاتف: ${customer.phone}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ledger_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        // Search in ledger
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "بحث في القيود")
                        }

                        // PDF Print Statement (أيقونة PDF حمراء بارزة كما في المرجع)
                        IconButton(
                            onClick = { printCustomerStatementPdf(context, customer, transactions) },
                            modifier = Modifier.testTag("print_pdf_statement_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "تصدير / طباعة PDF",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة قيد مفصل")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Embedded Quick Add Form (تصميم مطابق للقطات شاشة المرجع)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Customer Tag & Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = customer.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Date badge
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(displayDateFormat.format(Date()), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row: Calculator Icon + Amount Field + Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { showQuickCalcChips = !showQuickCalcChips },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "آلة حاسبة واختصارات مبالغ",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = quickAmountText,
                            onValueChange = { quickAmountText = it },
                            placeholder = { Text("المبلغ", fontSize = 14.sp) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            trailingIcon = {
                                if (quickAmountText.isNotBlank()) {
                                    IconButton(onClick = { quickAmountText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("quick_amount_input"),
                            singleLine = true
                        )
                    }

                    // Quick increment suggestions
                    if (showQuickCalcChips) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("+100" to 100.0, "+500" to 500.0, "+1000" to 1000.0, "+5000" to 5000.0).forEach { (label, value) ->
                                SuggestionChip(
                                    onClick = {
                                        val current = quickAmountText.toDoubleOrNull() ?: 0.0
                                        val newAmount = current + value
                                        quickAmountText = if (newAmount % 1.0 == 0.0) newAmount.toLong().toString() else newAmount.toString()
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row: Details + Currency Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = quickDetailsText,
                            onValueChange = { quickDetailsText = it },
                            placeholder = { Text("التفاصيل (سكر، بر، موصل، تسديد...)", fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("quick_details_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Currencies selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedCurrencyId == 1L,
                            onClick = { selectedCurrencyId = 1L },
                            label = { Text("يمني", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCurrencyId == 2L,
                            onClick = { selectedCurrencyId = 2L },
                            label = { Text("سعودي", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedCurrencyId == 3L,
                            onClick = { selectedCurrencyId = 3L },
                            label = { Text("دولار", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Side-by-Side Large Action Buttons (مطابق للمرجع: ▲ له و ▼ عليه)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Green Button: له (تسديد / إيداع)
                        Button(
                            onClick = {
                                val amount = quickAmountText.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    val note = quickDetailsText.trim()
                                    onQuickAddTransaction(amount, "له", note, selectedCurrencyId, System.currentTimeMillis())
                                    lastAddedBanner = Pair("له $amount ${if (note.isNotBlank()) note else ""}".trim(), null)
                                    quickAmountText = ""
                                    quickDetailsText = ""
                                }
                            },
                            enabled = quickAmountText.toDoubleOrNull() != null && quickAmountText.toDoubleOrNull()!! > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_quick_lah"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("▲  له", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Red Button: عليه (دين / مشتريات)
                        Button(
                            onClick = {
                                val amount = quickAmountText.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    val note = quickDetailsText.trim()
                                    onQuickAddTransaction(amount, "عليه", note, selectedCurrencyId, System.currentTimeMillis())
                                    lastAddedBanner = Pair("عليه $amount ${if (note.isNotBlank()) note else ""}".trim(), null)
                                    quickAmountText = ""
                                    quickDetailsText = ""
                                }
                            },
                            enabled = quickAmountText.toDoubleOrNull() != null && quickAmountText.toDoubleOrNull()!! > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_quick_alayh"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("▼  عليه", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Quick Added Alert Notification Banner (مطابق للقطة الشاشة 4)
            lastAddedBanner?.let { banner ->
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner.first,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val textToShare = "سند قيد مسجل - بقالة العزي\nالعميل: ${customer.name}\n${banner.first}\nالرصيد المتبقي: ${customer.netBalance}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة السند عبر:"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "مشاركة",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                            TextButton(onClick = { lastAddedBanner = null }) {
                                Text("إخفاء", color = MaterialTheme.colorScheme.primaryContainer, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Balances Summary Strip
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("له", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${customer.totalLah}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("عليه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${customer.totalAlayh}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الرصيد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${customer.netBalance}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (customer.netBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
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
                    text = "سجل العمليات والقيود (${displayedTransactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (ledgerSearchQuery.isNotBlank()) "نتائج البحث" else "مرتبة بالتسلسل الزمني",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (displayedTransactions.isEmpty()) {
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
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (ledgerSearchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد قيود مسجلة لهذا الحساب حتى الآن",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedTransactions, key = { it.id }) { tx ->
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
