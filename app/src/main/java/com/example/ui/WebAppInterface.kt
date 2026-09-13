package com.example.ui

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.AppSQLiteHandler
import java.io.InputStream
import java.util.concurrent.Executors

class WebAppInterface(
    private val activity: Activity,
    private val webView: WebView,
    private val onTriggerFilePicker: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * JavaScript Bridge method exposed to WebView.
     * Call from JS: Android.importDatabase();
     */
    @JavascriptInterface
    fun importDatabase() {
        mainHandler.post {
            onTriggerFilePicker()
        }
    }

    /**
     * Called when the user picks a database file via SAF in the activity.
     */
    fun processImportStream(inputStream: InputStream?) {
        if (inputStream == null) {
            sendResultToJs(false, "ملف قاعدة البيانات غير صالح أو تعذر قراءته.")
            return
        }

        executor.execute {
            try {
                val sqliteHandler = AppSQLiteHandler.getInstance(activity)
                val success = sqliteHandler.importDatabaseFile(inputStream)

                mainHandler.post {
                    if (success) {
                        sendResultToJs(true, null)
                        // Refresh WebView UI automatically after successful import
                        webView.evaluateJavascript("if (typeof window.onDatabaseImported === 'function') { window.onDatabaseImported(); } else { window.location.reload(); }", null)
                    } else {
                        sendResultToJs(false, "فشل التحقق من سلامة قاعدة البيانات أو استبدال الملف.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    sendResultToJs(false, e.message ?: "خطأ غير معروف أثناء الاستيراد.")
                }
            }
        }
    }

    private fun sendResultToJs(success: Boolean, errorMessage: String?) {
        val jsonResult = if (success) {
            "{\"success\":true}"
        } else {
            val safeError = errorMessage?.replace("\"", "\\\"") ?: "Unknown error"
            "{\"success\":false,\"error\":\"$safeError\"}"
        }
        
        val script = "if (typeof window.handleImportResult === 'function') { window.handleImportResult($jsonResult); }"
        webView.evaluateJavascript(script, null)
    }
}
