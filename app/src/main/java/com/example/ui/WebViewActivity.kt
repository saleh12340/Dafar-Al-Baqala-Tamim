package com.example.ui

import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var webAppInterface: WebAppInterface

    private val importPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                webAppInterface.processImportStream(inputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في قراءة ملف الاستيراد: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val exportPickerLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                webAppInterface.processExportStream(outputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في حفظ ملف التصدير: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.allowFileAccess = true
        }

        webAppInterface = WebAppInterface(
            activity = this,
            webView = webView,
            onTriggerFilePicker = {
                importPickerLauncher.launch(arrayOf("*/*"))
            },
            onTriggerFileSaver = {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportPickerLauncher.launch("app_database_backup_$timeStamp.db")
            }
        )

        webView.addJavascriptInterface(webAppInterface, "Android")

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>إدارة قاعدة بيانات SQLite - بقالة العزي</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: #f0f2f5;
                        margin: 0;
                        padding: 16px;
                        color: #1c1e21;
                    }
                    .container {
                        max-width: 500px;
                        margin: 0 auto;
                    }
                    .card {
                        background: #ffffff;
                        border-radius: 12px;
                        padding: 20px;
                        box-shadow: 0 4px 14px rgba(0,0,0,0.08);
                        margin-bottom: 16px;
                    }
                    h1 { font-size: 20px; margin-top: 0; color: #1a73e8; }
                    h2 { font-size: 16px; margin-top: 0; color: #333; }
                    p { font-size: 14px; line-height: 1.5; color: #555; }
                    .badge {
                        display: inline-block;
                        background: #e8f0fe;
                        color: #1a73e8;
                        padding: 4px 8px;
                        border-radius: 6px;
                        font-size: 12px;
                        font-weight: bold;
                        margin-bottom: 12px;
                    }
                    button {
                        background: #1a73e8;
                        color: white;
                        border: none;
                        padding: 12px 20px;
                        font-size: 15px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-weight: bold;
                        width: 100%;
                        margin-top: 8px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        transition: background 0.2s;
                    }
                    button:active { background: #1557b0; }
                    button.secondary {
                        background: #34a853;
                    }
                    button.secondary:active { background: #2d8e47; }
                    button.outline {
                        background: white;
                        color: #1a73e8;
                        border: 1.5px solid #1a73e8;
                    }
                    button.outline:active { background: #e8f0fe; }
                    #status {
                        margin-top: 14px;
                        padding: 10px;
                        border-radius: 8px;
                        font-size: 13px;
                        display: none;
                        font-weight: 500;
                    }
                    .status-success { background: #e6f4ea; color: #137333; display: block !important; }
                    .status-error { background: #fce8e6; color: #c5221f; display: block !important; }
                    .status-loading { background: #e8f0fe; color: #1a73e8; display: block !important; }
                    .table-preview {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 12px;
                        margin-top: 12px;
                    }
                    .table-preview th, .table-preview td {
                        border: 1px solid #e0e0e0;
                        padding: 6px 8px;
                        text-align: right;
                    }
                    .table-preview th {
                        background: #f8f9fa;
                        font-weight: bold;
                    }
                    .tag-info {
                        background: #f1f3f4;
                        padding: 8px;
                        border-radius: 6px;
                        font-size: 12px;
                        margin-top: 10px;
                        font-family: monospace;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="card">
                        <div class="badge">SQLite & WebView Bridge</div>
                        <h1>بقالة العزي - إدارة البيانات</h1>
                        <p>تتيح لك هذه الواجهة استيراد وتصدير قاعدة البيانات <code>app_database.db</code> والتحقق من سلامة الجداول والقيود عبر Android Storage Access Framework.</p>
                        
                        <div class="tag-info">
                            الجداول المدعومة: customers, groups, currency, transactions, transactions_d, reminders.<br>
                            المشاهد والفهارس: transactions_tot_v, transactions_share_ref_uq.
                        </div>

                        <div id="status"></div>

                        <button onclick="triggerImport()">
                            📥 استيراد واستبدال قاعدة البيانات (.db)
                        </button>
                        
                        <button class="secondary" onclick="triggerExport()">
                            📤 تصدير نسخة احتياطية (.db)
                        </button>

                        <button class="outline" onclick="loadCustomersPreview()">
                            👥 استعلام بيانات العملاء من SQLite
                        </button>
                    </div>

                    <div class="card" id="previewCard" style="display:none;">
                        <h2>بيانات العملاء المسترجعة:</h2>
                        <div id="customersContainer"></div>
                    </div>
                </div>

                <script>
                    function showStatus(msg, type) {
                        var el = document.getElementById('status');
                        el.innerText = msg;
                        el.className = 'status-' + type;
                    }

                    function triggerImport() {
                        showStatus('جاري فتح منتقي الملفات لاختيار قاعدة البيانات...', 'loading');
                        if (window.Android && typeof window.Android.importDatabase === 'function') {
                            window.Android.importDatabase();
                        } else {
                            showStatus('جسر الاتصال مع نظام أندرويد غير متوفر', 'error');
                        }
                    }

                    function triggerExport() {
                        showStatus('جاري فتح موجه حفظ ملف النسخة الاحتياطية...', 'loading');
                        if (window.Android && typeof window.Android.exportDatabase === 'function') {
                            window.Android.exportDatabase();
                        } else {
                            showStatus('جسر الاتصال مع نظام أندرويد غير متوفر', 'error');
                        }
                    }

                    function loadCustomersPreview() {
                        if (window.Android && typeof window.Android.getCustomers === 'function') {
                            try {
                                var raw = window.Android.getCustomers();
                                var customers = JSON.parse(raw);
                                var card = document.getElementById('previewCard');
                                var container = document.getElementById('customersContainer');
                                card.style.display = 'block';

                                if (customers.length === 0) {
                                    container.innerHTML = '<p>لا توجد بيانات عملاء مسجلة في قاعدة البيانات الحالية.</p>';
                                    return;
                                }

                                var html = '<table class="table-preview"><tr><th>العميل</th><th>الهاتف</th><th>الصافي</th></tr>';
                                for (var i = 0; i < customers.length; i++) {
                                    var c = customers[i];
                                    html += '<tr><td>' + c.name + '</td><td>' + (c.phone || '-') + '</td><td>' + c.netBalance + '</td></tr>';
                                }
                                html += '</table>';
                                container.innerHTML = html;
                                showStatus('تم استرجاع ' + customers.length + ' عميل بنجاح.', 'success');
                            } catch (e) {
                                showStatus('خطأ أثناء قراءة البيانات: ' + e.message, 'error');
                            }
                        } else {
                            showStatus('دالة استعلام العملاء غير متوفرة', 'error');
                        }
                    }

                    window.handleImportResult = function(result) {
                        if (result.success) {
                            showStatus('تم استيراد واختبار سلامة قاعدة البيانات بنجاح!', 'success');
                            loadCustomersPreview();
                        } else {
                            showStatus('فشل الاستيراد: ' + (result.error || 'ملف تالف أو غير صالح'), 'error');
                        }
                    };

                    window.handleExportResult = function(result) {
                        if (result.success) {
                            showStatus('تم حفظ وتصدير قاعدة البيانات بنجاح في الموقع المحدد!', 'success');
                        } else {
                            showStatus('فشل تصدير قاعدة البيانات: ' + (result.error || 'خطأ غير معروف'), 'error');
                        }
                    };

                    window.onDatabaseImported = function() {
                        showStatus('تم تحديث البيانات بنجاح!', 'success');
                    };
                </script>
            </body>
            </html>
        """

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        setContentView(webView)
    }
}
