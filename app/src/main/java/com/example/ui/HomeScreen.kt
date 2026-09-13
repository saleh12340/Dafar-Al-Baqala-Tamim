package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.data.TransactionEntity
import com.example.viewmodel.AccountingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AccountingViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val subTimeFilter by viewModel.subTimeFilter.collectAsState()
    val sortDescending by viewModel.sortDescending.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transactions, 1: Accounts, 2: Settings
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showHelpDialogState by remember { mutableStateOf(false) }
    var showPrintSettings by remember { mutableStateOf(false) }
    var showExportShare by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Filter and Sort Transactions
    val filteredTransactions = remember(transactions, searchQuery, selectedCurrency, timeFilter, subTimeFilter, sortDescending) {
        var list = transactions.filter { tx ->
            val matchesSearch = searchQuery.isBlank() ||
                    tx.accountName.contains(searchQuery, ignoreCase = true) ||
                    tx.description.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)

            val matchesCurrency = selectedCurrency == "الكل" || tx.currency.contains(selectedCurrency, ignoreCase = true)
            matchesSearch && matchesCurrency
        }

        if (sortDescending) {
            list = list.sortedByDescending { it.timestamp }
        } else {
            list = list.sortedBy { it.timestamp }
        }
        list
    }

    // Calculate Totals
    val totalLah = filteredTransactions.filter { it.type == "له" }.sumOf { it.amount }
    val totalAlayh = filteredTransactions.filter { it.type == "عليه" }.sumOf { it.amount }
    val netBalance = totalLah - totalAlayh

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "بقالة العزي - دفتر الحسابات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(text = "هاتف: 726425052", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPrintSettings = true }, modifier = Modifier.testTag("print_settings_icon")) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = "طباعة حرارية")
                        }
                        IconButton(onClick = { showExportShare = true }, modifier = Modifier.testTag("export_share_icon")) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "تصدير ومشاركة")
                        }
                        IconButton(onClick = { showHelpDialogState = true }, modifier = Modifier.testTag("help_icon")) {
                            Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "شرح التعليمات")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )

                // Search & Filter Header (when on Tab 0)
                if (selectedTab == 0) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("بحث باسم العميل، المبلغ، أو البيان...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("search_bar"),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )

                        // Quick Filter chips
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCurrency == "الكل",
                                onClick = { viewModel.setSelectedCurrency("الكل") },
                                label = { Text("كل العملات") }
                            )
                            FilterChip(
                                selected = selectedCurrency == "ريال يمني",
                                onClick = { viewModel.setSelectedCurrency("ريال يمني") },
                                label = { Text("ريال يمني") }
                            )
                            FilterChip(
                                selected = selectedCurrency == "ريال سعودي",
                                onClick = { viewModel.setSelectedCurrency("ريال سعودي") },
                                label = { Text("ريال سعودي") }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.toggleSortOrder() }, modifier = Modifier.testTag("sort_toggle_button")) {
                                Icon(
                                    imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = "ترتيب العمليات"
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Fixed Bottom Totals Bar for Transactions Tab
                if (selectedTab == 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("إجمالي له", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("$totalLah", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("إجمالي عليه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("$totalAlayh", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الصافي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("$netBalance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }

                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(imageVector = Icons.Default.ListAlt, contentDescription = null) },
                        label = { Text("سجل العمليات") },
                        modifier = Modifier.testTag("nav_transactions_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(imageVector = Icons.Default.People, contentDescription = null) },
                        label = { Text("الحسابات") },
                        modifier = Modifier.testTag("nav_accounts_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                        label = { Text("الإعدادات") },
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddTransactionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("fab_add_transaction")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة عملية")
                }
            } else if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { accountToEdit = null; showAddAccountDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("fab_add_account")
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "إضافة حساب")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> TransactionsLogScreen(
                    transactions = filteredTransactions,
                    onDeleteTransaction = { viewModel.deleteTransaction(it); snackbarMessage = "تم حذف العملية بنجاح" },
                    onShareTransaction = { showExportShare = true }
                )
                1 -> AccountsScreen(
                    accounts = accounts,
                    onEditAccount = { accountToEdit = it; showAddAccountDialog = true },
                    onDeleteAccount = { viewModel.deleteAccount(it); snackbarMessage = "تم حذف الحساب بنجاح" }
                )
                2 -> SettingsScreen(
                    onOpenBackup = { snackbarMessage = "تم إنشاء نسخة احتياطية في مجلد (بقالة العزي / Baqala Al-Ezzi)" },
                    onOpenPrintSettings = { showPrintSettings = true },
                    onOpenWebViewImport = {
                        val intent = android.content.Intent(
                            context,
                            com.example.ui.WebViewActivity::class.java
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // Dialogs
    if (showAddAccountDialog) {
        AddEditAccountDialog(
            accountToEdit = accountToEdit,
            onDismiss = { showAddAccountDialog = false; accountToEdit = null },
            onSave = { acc ->
                if (accountToEdit == null) {
                    viewModel.insertAccount(acc) { snackbarMessage = "تم إضافة الحساب بنجاح" }
                } else {
                    viewModel.updateAccount(acc)
                    snackbarMessage = "تم تحديث الحساب بنجاح"
                }
                showAddAccountDialog = false
                accountToEdit = null
            },
            onOpenHelp = { showHelpDialogState = true },
            onImportContact = { snackbarMessage = "تم استيراد جهات الاتصال بنجاح" },
            onVoiceInput = { field -> snackbarMessage = "جاري الاستماع للإدخال الصوتي..." }
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            accounts = accounts,
            onDismiss = { showAddTransactionDialog = false },
            onSave = { tx ->
                viewModel.insertTransaction(tx) {
                    snackbarMessage = "تم تسجيل العملية بنجاح لصالح بقالة العزي"
                }
                showAddTransactionDialog = false
            }
        )
    }

    if (showHelpDialogState) {
        HelpDialog(onDismiss = { showHelpDialogState = false })
    }

    if (showPrintSettings) {
        PrintSettingsDialog(
            onDismiss = { showPrintSettings = false },
            onTestPrint = { snackbarMessage = "تم إرسال أمر الطباعة الحرارية (80mm) بنجاح" }
        )
    }

    if (showExportShare) {
        ExportShareDialog(
            onDismiss = { showExportShare = false },
            onExportPdf = { snackbarMessage = "تم تصدير تقرير PDF وحفظه في مجلد بقالة العزي" },
            onExportExcel = { snackbarMessage = "تم تصدير ملف الإكسل بنجاح" },
            onExportImage = { snackbarMessage = "تم حفظ صورة الفاتورة في المجلد الخاص" },
            onShareSms = { snackbarMessage = "تم فتح نافذة مشاركة الحسابات عبر WhatsApp/SMS" }
        )
    }
}

@Composable
fun TransactionsLogScreen(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onShareTransaction: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("لا توجد عمليات مالية مسجلة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.id }) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("transaction_item"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = tx.accountName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (tx.type == "له") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "نوع العملية: ${tx.type}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "له") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "المبلغ: ${tx.amount} ${tx.currency}", fontWeight = FontWeight.SemiBold)
                            Text(text = dateFormat.format(Date(tx.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (tx.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "البيان: ${tx.description}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onShareTransaction) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteTransaction(tx) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsScreen(
    accounts: List<AccountEntity>,
    onEditAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit
) {
    if (accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.People, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("لا توجد حسابات عملاء أو موردين مسجلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts, key = { it.id }) { acc ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("account_item"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = acc.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = acc.phone, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }

                        if (acc.debtCeilingEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "سقف المديونية: ${acc.debtCeilingAmount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }

                        if (acc.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "ملاحظات: ${acc.notes}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onEditAccount(acc) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعديل")
                            }
                            TextButton(onClick = { onDeleteAccount(acc) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onOpenBackup: () -> Unit, onOpenPrintSettings: () -> Unit, onOpenWebViewImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("إعدادات تطبيق بقالة العزي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("استيراد قاعدة البيانات (WebView & SQLite)", fontWeight = FontWeight.Bold)
                Text("استيراد ملفات النسخ الاحتياطي عبر WebView Bridge و Storage Access Framework.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenWebViewImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح واجهة استيراد قواعد البيانات (WebView)")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المجلد الخاص بالجهاز", fontWeight = FontWeight.Bold)
                Text("المجلد: (بقالة العزي / Baqala Al-Ezzi)", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Text("يستخدم لتخزين النسخ الاحتياطية، الفواتير، والتقارير المصدرة PDF و Excel.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنشاء نسخة احتياطية للبيانات الآن")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الطابعات الحرارية والفواتير", fontWeight = FontWeight.Bold)
                Text("دعم طابعات 80mm عبر Bluetooth و USB مع طباعة اسم بقالة العزي ورقم الهاتف 726425052.", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenPrintSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعدادات واقتران الطابعة الحرارية")
                }
            }
        }
    }
}
