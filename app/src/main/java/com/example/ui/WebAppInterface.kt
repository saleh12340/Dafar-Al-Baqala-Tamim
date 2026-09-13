package com.example.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.AppSQLiteHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

class WebAppInterface(
    private val activity: Activity,
    private val webView: WebView,
    private val onTriggerFilePicker: () -> Unit,
    private val onTriggerFileSaver: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sqliteHandler = AppSQLiteHandler.getInstance(activity)

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
     * JavaScript Bridge method exposed to WebView.
     * Call from JS: Android.exportDatabase();
     */
    @JavascriptInterface
    fun exportDatabase() {
        mainHandler.post {
            onTriggerFileSaver()
        }
    }

    /**
     * JavaScript Bridge method to query customers list.
     * Call from JS: Android.getCustomers();
     */
    @JavascriptInterface
    fun getCustomers(): String {
        return try {
            val list = sqliteHandler.getAllCustomers()
            val array = JSONArray()
            list.forEach { c ->
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("accountDetails", c.accountDetails)
                    put("totalLah", c.totalLah)
                    put("totalAlayh", c.totalAlayh)
                    put("netBalance", c.netBalance)
                }
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * JavaScript Bridge method to query transactions for a customer.
     * Call from JS: Android.getTransactions(customerId);
     */
    @JavascriptInterface
    fun getTransactions(customerId: Long): String {
        return try {
            val list = sqliteHandler.getTransactionsForCustomer(customerId)
            val array = JSONArray()
            list.forEach { tx ->
                val obj = JSONObject().apply {
                    put("id", tx.id)
                    put("customerId", tx.customerId)
                    put("customerName", tx.customerName)
                    put("amount", tx.amount)
                    put("currencyName", tx.currencyName)
                    put("transactionType", tx.transactionType)
                    put("timestamp", tx.timestamp)
                    put("ledgerBalance", tx.ledgerBalance)
                    put("detailNote", tx.detailNote)
                }
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * Process SAF input stream when user picks a .db file
     */
    fun processImportStream(inputStream: InputStream?) {
        if (inputStream == null) {
            sendImportResultToJs(false, "ملف قاعدة البيانات غير صالح أو تعذر قراءته.")
            return
        }

        executor.execute {
            try {
                val success = sqliteHandler.importDatabaseFile(inputStream)
                mainHandler.post {
                    if (success) {
                        sendImportResultToJs(true, null)
                        webView.evaluateJavascript(
                            "if (typeof window.onDatabaseImported === 'function') { window.onDatabaseImported(); } else { window.location.reload(); }",
                            null
                        )
                    } else {
                        sendImportResultToJs(false, "فشل التحقق من سلامة قاعدة البيانات أو استبدال الملف.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    sendImportResultToJs(false, e.message ?: "خطأ أثناء استيراد قاعدة البيانات.")
                }
            }
        }
    }

    /**
     * Process SAF output stream when user exports a .db file
     */
    fun processExportStream(outputStream: OutputStream?) {
        if (outputStream == null) {
            sendExportResultToJs(false, "تعذر فتح مجرى كتابة النسخة الاحتياطية.")
            return
        }

        executor.execute {
            try {
                val success = sqliteHandler.exportDatabaseFile(outputStream)
                mainHandler.post {
                    if (success) {
                        sendExportResultToJs(true, null)
                    } else {
                        sendExportResultToJs(false, "فشل نسخ وتصدير ملف قاعدة البيانات.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    sendExportResultToJs(false, e.message ?: "خطأ أثناء تصدير قاعدة البيانات.")
                }
            }
        }
    }

    private fun sendImportResultToJs(success: Boolean, errorMessage: String?) {
        val safeError = errorMessage?.replace("\"", "\\\"") ?: "Unknown error"
        val jsonResult = if (success) "{\"success\":true}" else "{\"success\":false,\"error\":\"$safeError\"}"
        val script = "if (typeof window.handleImportResult === 'function') { window.handleImportResult($jsonResult); }"
        webView.evaluateJavascript(script, null)
    }

    private fun sendExportResultToJs(success: Boolean, errorMessage: String?) {
        val safeError = errorMessage?.replace("\"", "\\\"") ?: "Unknown error"
        val jsonResult = if (success) "{\"success\":true}" else "{\"success\":false,\"error\":\"$safeError\"}"
        val script = "if (typeof window.handleExportResult === 'function') { window.handleExportResult($jsonResult); }"
        webView.evaluateJavascript(script, null)
    }
}
