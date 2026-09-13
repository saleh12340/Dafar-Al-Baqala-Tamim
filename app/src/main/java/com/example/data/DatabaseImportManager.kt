package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class DatabaseImportManager(private val context: Context) {

    private val sqlHandler = AppSQLiteHandler.getInstance(context)

    suspend fun importFromUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return@withContext Result.failure(Exception("تعذر فتح ملف قاعدة البيانات (InputStream is null)"))
            }

            val success = sqlHandler.importDatabaseFile(inputStream)
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("فشل اختبار سلامة قاعدة البيانات أو تلف الملف المُستورد"))
            }
        } catch (e: Exception) {
            Log.e("DatabaseImportManager", "Error importing database from Uri", e)
            Result.failure(e)
        }
    }
}
