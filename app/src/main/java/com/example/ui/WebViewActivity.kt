package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var webAppInterface: WebAppInterface

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                webAppInterface.processImportStream(inputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في فتح الملف: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        webAppInterface = WebAppInterface(
            activity = this,
            webView = webView,
            onTriggerFilePicker = {
                // Launch SAF to pick .db or .sqlite files
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        )

        webView.addJavascriptInterface(webAppInterface, "Android")

        // Load local HTML showcasing database import bridge
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>استيراد قاعدة بيانات SQLite</title>
                <style>
                    body { font-family: sans-serif; background: #f4f6f9; margin: 0; padding: 20px; text-align: center; }
                    .card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); max-width: 400px; margin: 40px auto; }
                    h2 { color: #1a73e8; margin-top: 0; }
                    button { background: #1a73e8; color: white; border: none; padding: 12px 24px; font-size: 16px; border-radius: 8px; cursor: pointer; margin-top: 16px; font-weight: bold; width: 100%; }
                    button:active { background: #1557b0; }
                    #status { margin-top: 16px; font-weight: bold; font-size: 14px; }
                    .success { color: #2e7d32; }
                    .error { color: #c62828; }
                </style>
            </main>
            <body>
                <div class="card">
                    <h2>إدارة قواعد البيانات</h2>
                    <p>قم باختيار ملف نسخ احتياطي لقاعدة البيانات (.db أو .sqlite) لاستيراده وتحديث البيانات بأمان.</p>
                    <button onclick="triggerImport()">استيراد قاعدة البيانات (SQLite)</button>
                    <div id="status"></div>
                </div>

                <script>
                    function triggerImport() {
                        document.getElementById('status').innerText = "جاري اختيار الملف...";
                        document.getElementById('status').className = "";
                        if (window.Android && typeof window.Android.importDatabase === 'function') {
                            window.Android.importDatabase();
                        } else {
                            document.getElementById('status').innerText = "واجهة الجسر (Android Interface) غير متوفرة.";
                            document.getElementById('status').className = "error";
                        }
                    }

                    window.handleImportResult = function(result) {
                        var statusEl = document.getElementById('status');
                        if (result.success) {
                            statusEl.innerText = "تم استيراد قاعدة البيانات بنجاح!";
                            statusEl.className = "success";
                        } else {
                            statusEl.innerText = "فشل الاستيراد: " + (result.error || "خطأ غير معروف");
                            statusEl.className = "error";
                        }
                    };

                    window.onDatabaseImported = function() {
                        setTimeout(function() {
                            window.location.reload();
                        }, 1500);
                    };
                </script>
            </body>
            </html>
        """

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        setContentView(webView)
    }
}
