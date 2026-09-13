package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.core.content.ContextCompat
import com.example.data.CustomerModel
import com.example.data.TransactionModel
import com.example.viewmodel.AccountingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AccountingViewModel) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val selectedCustomerTransactions by viewModel.selectedCustomerTransactions.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val sortDescending by viewModel.sortDescending.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transactions, 1: Accounts, 2: Settings
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerModel?>(null) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionModel?>(null) }
    var showHelpDialogState by remember { mutableStateOf(false) }
    var showPrintSettings by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Android Back Button Handler & Exit Confirmation
    BackHandler(enabled = true) {
        if (selectedCustomer != null) {
            // Close customer ledger view and return to accounts
            viewModel.selectCustomer(null)
        } else {
            // Prompt exit confirmation
            showExitDialog = true
        }
    }

    // SAF Launchers for Scoped Storage Backup and Restore
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportDatabase(uri) { success, error ->
                snackbarMessage = if (success) {
                    "تم تصدير النسخة الاحتياطية بنجاح إلى وحدة التخزين"
                } else {
                    "فشل التصدير: ${error ?: "خطأ غير معروف"}"
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importDatabase(uri) { success, error ->
                snackbarMessage = if (success) {
                    "تمت استعادة وتحديث قاعدة البيانات بنجاح!"
                } else {
                    "فشل الاستيراد: ${error ?: "الملف غير صالح"}"
                }
            }
        }
    }

    // Runtime Permissions Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        snackbarMessage = if (allGranted) {
            "تم منح كافة الأذونات المطلوبة بنجاح"
        } else {
            "تم رفض بعض الأذونات. قد تتأثر بعض الميزات مثل الطباعة الحرارية"
        }
    }

    // Filter & Sort Transactions
    val filteredTransactions = remember(transactions, searchQuery, selectedCurrency, sortDescending) {
        var list = transactions.filter { tx ->
            val matchesSearch = searchQuery.isBlank() ||
                    tx.customerName.contains(searchQuery, ignoreCase = true) ||
                    tx.detailNote.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)

            val matchesCurrency = selectedCurrency == "الكل" || tx.currencyName.contains(selectedCurrency, ignoreCase = true)
            matchesSearch && matchesCurrency
        }
        if (sortDescending) {
            list = list.sortedByDescending { it.timestamp }
        } else {
            list = list.sortedBy { it.timestamp }
        }
        list
    }

    // Filter Customers
    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) {
            customers
        } else {
            customers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.phone.contains(searchQuery) ||
                        it.accountDetails.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Calculate Global Totals
    val totalLah = filteredTransactions.filter { it.transactionType == "له" }.sumOf { it.amount }
    val totalAlayh = filteredTransactions.filter { it.transactionType == "عليه" }.sumOf { it.amount }
    val netBalance = totalLah - totalAlayh

    // If a customer is selected, show their full Ledger Detail View
    val activeCustomer = selectedCustomer
    if (activeCustomer != null) {
        CustomerLedgerScreen(
            customer = activeCustomer,
            transactions = selectedCustomerTransactions,
            onBack = { viewModel.selectCustomer(null) },
            onAddTransaction = {
                transactionToEdit = null
                showAddTransactionDialog = true
            },
            onEditTransaction = { tx ->
                transactionToEdit = tx
                showAddTransactionDialog = true
            },
            onDeleteTransaction = { txId ->
                viewModel.deleteTransaction(txId) {
                    snackbarMessage = "تم حذف القيد وإعادة احتساب الرصيد"
                }
            },
            onCloseAccount = {
                viewModel.closeAccount(activeCustomer.id) { success ->
                    snackbarMessage = if (success) {
                        "تم إغلاق وتصفية الحساب وترحيل الرصيد بنجاح"
                    } else {
                        "تعذر إغلاق الحساب"
                    }
                }
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "بقالة العزي - دفتر الحسابات",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "هاتف: 726425052",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showPrintSettings = true },
                                modifier = Modifier.testTag("print_settings_icon")
                            ) {
                                Icon(imageVector = Icons.Default.Print, contentDescription = "طباعة حرارية")
                            }
                            IconButton(
                                onClick = { showHelpDialogState = true },
                                modifier = Modifier.testTag("help_icon")
                            ) {
                                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "تعليمات النظام")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )

                    // Search & Filter Header (Tabs 0 and 1)
                    if (selectedTab == 0 || selectedTab == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(if (selectedTab == 0) "بحث بالعميل، المبلغ، أو البيان..." else "بحث في حسابات العملاء...")
                                },
                                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_bar"),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )

                            if (selectedTab == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = selectedCurrency == "الكل",
                                        onClick = { viewModel.setSelectedCurrency("الكل") },
                                        label = { Text("الكل") }
                                    )
                                    FilterChip(
                                        selected = selectedCurrency == "ريال يمني",
                                        onClick = { viewModel.setSelectedCurrency("ريال يمني") },
                                        label = { Text("يمني") }
                                    )
                                    FilterChip(
                                        selected = selectedCurrency == "ريال سعودي",
                                        onClick = { viewModel.setSelectedCurrency("ريال سعودي") },
                                        label = { Text("سعودي") }
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.toggleSortOrder() },
                                        modifier = Modifier.testTag("sort_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = "ترتيب العمليات"
                                        )
                                    }
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
                            tonalElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("إجمالي له", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("$totalLah", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("إجمالي عليه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("$totalAlayh", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("الصافي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            label = { Text("العمليات") },
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
                        onClick = {
                            transactionToEdit = null
                            showAddTransactionDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("fab_add_transaction")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة عملية")
                    }
                } else if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = {
                            customerToEdit = null
                            showAddCustomerDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("fab_add_account")
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "إضافة عميل")
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> AllTransactionsScreen(
                        transactions = filteredTransactions,
                        onEditTransaction = { tx ->
                            transactionToEdit = tx
                            showAddTransactionDialog = true
                        },
                        onDeleteTransaction = { txId ->
                            viewModel.deleteTransaction(txId) {
                                snackbarMessage = "تم حذف العملية بنجاح"
                            }
                        },
                        onSelectCustomer = { customerId ->
                            val c = customers.find { it.id == customerId }
                            if (c != null) {
                                viewModel.selectCustomer(c)
                            }
                        }
                    )
                    1 -> AccountsListScreen(
                        customers = filteredCustomers,
                        onSelectCustomer = { customer ->
                            viewModel.selectCustomer(customer)
                        },
                        onEditCustomer = { customer ->
                            customerToEdit = customer
                            showAddCustomerDialog = true
                        },
                        onDeleteCustomer = { customerId ->
                            viewModel.deleteCustomer(customerId) {
                                snackbarMessage = "تم حذف الحساب والقيود المرتبطة بنجاح"
                            }
                        }
                    )
                    2 -> SettingsPanelScreen(
                        onTriggerBackup = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            createBackupLauncher.launch("baqala_al_ezzi_backup_$timeStamp.db")
                        },
                        onTriggerRestore = {
                            restoreBackupLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenWebViewBridge = {
                            val intent = Intent(context, WebViewActivity::class.java)
                            context.startActivity(intent)
                        },
                        onOpenPrintSettings = {
                            showPrintSettings = true
                        },
                        onRequestPermissions = {
                            val permissionsToRequest = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH)
                                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADMIN)
                                permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }

                            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAddCustomerDialog) {
        AddEditCustomerDialog(
            customerToEdit = customerToEdit,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { cust ->
                if (customerToEdit == null) {
                    viewModel.insertCustomer(cust) {
                        snackbarMessage = "تمت إضافة الحساب بنجاح"
                    }
                } else {
                    viewModel.updateCustomer(cust) {
                        snackbarMessage = "تم تحديث بيانات الحساب بنجاح"
                    }
                }
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    if (showAddTransactionDialog) {
        AddEditTransactionDialog(
            customers = customers,
            preselectedCustomerId = selectedCustomer?.id,
            transactionToEdit = transactionToEdit,
            onDismiss = {
                showAddTransactionDialog = false
                transactionToEdit = null
            },
            onSave = { tx ->
                if (transactionToEdit == null) {
                    viewModel.insertTransaction(tx) {
                        snackbarMessage = "تم تسجيل القيد وإعادة احتساب الرصيد بنجاح"
                    }
                } else {
                    viewModel.updateTransaction(tx) {
                        snackbarMessage = "تم تحديث القيد وإعادة احتساب الرصيد"
                    }
                }
                showAddTransactionDialog = false
                transactionToEdit = null
            }
        )
    }

    if (showHelpDialogState) {
        HelpDialog(onDismiss = { showHelpDialogState = false })
    }

    if (showPrintSettings) {
        PrintSettingsDialog(
            onDismiss = { showPrintSettings = false },
            onTestPrint = {
                snackbarMessage = "تم إرسال أمر طباعة تجريبي لطابعة الفواتير (80mm)"
            }
        )
    }

    // Native Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null) },
            title = { Text("تأكيد الخروج") },
            text = { Text("هل أنت متأكد من رغبتك في الخروج من تطبيق بقالة العزي؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، خروج")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun AllTransactionsScreen(
    transactions: List<TransactionModel>,
    onEditTransaction: (TransactionModel) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onSelectCustomer: (Long) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")) }
    var txToDelete by remember { mutableStateOf<TransactionModel?>(null) }

    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("لا توجد عمليات مالية مسجلة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.id }) { tx ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_item_${tx.id}"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tx.customerName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onSelectCustomer(tx.customerId) }
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (tx.transactionType == "له") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (tx.transactionType == "له") "له (دائن)" else "عليه (مدين)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.transactionType == "له") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "المبلغ: ${tx.amount} ${tx.currencyName}", fontWeight = FontWeight.SemiBold)
                            Text(text = dateFormat.format(Date(tx.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (tx.detailNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "البيان: ${tx.detailNote}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "فتح كشف الحساب",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onSelectCustomer(tx.customerId) }
                            )

                            Row {
                                IconButton(
                                    onClick = {
                                        val text = """
                                            بقالة العزي - سند قيد
                                            العميل: ${tx.customerName}
                                            المبلغ: ${tx.amount} ${tx.currencyName} (${tx.transactionType})
                                            البيان: ${tx.detailNote}
                                            التاريخ: ${dateFormat.format(Date(tx.timestamp))}
                                            الرصيد التراكمي: ${tx.ledgerBalance}
                                        """.trimIndent()
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "مشاركة السند"))
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onEditTransaction(tx) }) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل")
                                }
                                IconButton(onClick = { txToDelete = tx }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (txToDelete != null) {
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = { Text("حذف العملية المالية") },
            text = { Text("هل أنت متأكد من حذف العملية بمبلغ ${txToDelete?.amount} ${txToDelete?.currencyName}؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        txToDelete?.id?.let { onDeleteTransaction(it) }
                        txToDelete = null
                    }
                ) {
                    Text("نعم، حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun AccountsListScreen(
    customers: List<CustomerModel>,
    onSelectCustomer: (CustomerModel) -> Unit,
    onEditCustomer: (CustomerModel) -> Unit,
    onDeleteCustomer: (Long) -> Unit
) {
    var customerToDelete by remember { mutableStateOf<CustomerModel?>(null) }
    var selectedFilter by remember { mutableStateOf("الكل") } // "الكل", "مدين", "دائن", "مصفى"

    val filteredList = remember(customers, selectedFilter) {
        when (selectedFilter) {
            "مدين" -> customers.filter { it.netBalance < -0.001 }
            "دائن" -> customers.filter { it.netBalance > 0.001 }
            "مصفى" -> customers.filter { Math.abs(it.netBalance) <= 0.001 }
            else -> customers
        }
    }

    val debtorsCount = remember(customers) { customers.count { it.netBalance < -0.001 } }
    val creditorsCount = remember(customers) { customers.count { it.netBalance > 0.001 } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Debt status filter chips (ميزات دفتر الحسابات)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "الكل",
                onClick = { selectedFilter = "الكل" },
                label = { Text("الكل (${customers.size})", fontSize = 12.sp) }
            )
            FilterChip(
                selected = selectedFilter == "مدين",
                onClick = { selectedFilter = "مدين" },
                label = { Text("مدينون (${debtorsCount})", fontSize = 12.sp, color = if (selectedFilter == "مدين") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error) }
            )
            FilterChip(
                selected = selectedFilter == "دائن",
                onClick = { selectedFilter = "دائن" },
                label = { Text("دائنون (${creditorsCount})", fontSize = 12.sp, color = if (selectedFilter == "دائن") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary) }
            )
            FilterChip(
                selected = selectedFilter == "مصفى",
                onClick = { selectedFilter = "مصفى" },
                label = { Text("مصفى", fontSize = 12.sp) }
            )
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد حسابات مطابقة لهذا الفلتر", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { cust ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCustomer(cust) }
                        .testTag("account_item_${cust.id}"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = cust.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (cust.phone.isNotBlank()) {
                                Text(
                                    text = cust.phone,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "له: ${cust.totalLah} | عليه: ${cust.totalAlayh}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "الصافي: ${cust.netBalance}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cust.netBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        if (cust.accountDetails.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cust.accountDetails,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onSelectCustomer(cust) }) {
                                Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("كشف الحساب والقيود")
                            }

                            Row {
                                TextButton(onClick = { onEditCustomer(cust) }) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تعديل")
                                }
                                TextButton(onClick = { customerToDelete = cust }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
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
    }

    if (customerToDelete != null) {
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("حذف الحساب") },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف حساب (${customerToDelete?.name})؟ سيتم حذف جميع قيوده وعملياته المالية تلقائياً.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customerToDelete?.id?.let { onDeleteCustomer(it) }
                        customerToDelete = null
                    }
                ) {
                    Text("نعم، حذف الحساب", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SettingsPanelScreen(
    onTriggerBackup: () -> Unit,
    onTriggerRestore: () -> Unit,
    onOpenWebViewBridge: () -> Unit,
    onOpenPrintSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current

    // Check current permissions status
    val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "لوحة إعدادات النظام وقواعد البيانات",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Database Backup & Restore Card (Scoped Storage / SAF)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("النسخ الاحتياطي والاستعادة (Scoped Storage)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "حفظ أو استعادة قاعدة البيانات (app_database.db) بأمان متوافق مع كافة إصدارات Android 10+ وفحص السلامة التلقائي.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTriggerBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير نسخة (.db)")
                        }

                        OutlinedButton(
                            onClick = onTriggerRestore,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد نسخة (.db)")
                        }
                    }
                }
            }
        }

        // WebView SQLite Import Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("استيراد قاعدة البيانات عبر جسر الويب (WebView Bridge)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "استيراد وتحديث قاعدة بيانات SQLite التلقائي مع واجهة Web تفاعلية وفحص دقيق للجداول والروابط.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenWebViewBridge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Web, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فتح واجهة استيراد قاعدة البيانات (WebView)")
                    }
                }
            }
        }

        // Thermal Printer Setup Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الطابعات الحرارية وإعدادات الفواتير", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "دعم الطباعة المباشرة على طابعات 80mm عبر تقنية Bluetooth و USB مع بيانات المحل (بقالة العزي - هاتف: 726425052).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenPrintSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إعدادات واختبار الطابعة الحرارية")
                    }
                }
            }
        }

        // Permissions Status & Request Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أذونات النظام (System Permissions)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الوصول للتخزين والوسائط:", fontSize = 13.sp)
                        Text(
                            text = if (hasStorage) "ممنوح ✓" else "مطلوب ✗",
                            fontWeight = FontWeight.Bold,
                            color = if (hasStorage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("البلوتوث وطابعات الفواتير:", fontSize = 13.sp)
                        Text(
                            text = if (hasBluetooth) "ممنوح ✓" else "مطلوب ✗",
                            fontWeight = FontWeight.Bold,
                            color = if (hasBluetooth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طلب وتحديث أذونات النظام")
                    }
                }
            }
        }

        // Store Information Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("معلومات المنشأة", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("المحل: بقالة العزي للتموينات الغذائية", fontSize = 14.sp)
                    Text("خدمة العملاء والحسابات: 726425052", fontSize = 14.sp)
                    Text("نظام إدارة الحسابات والديون والطباعة الحرارية v1.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
