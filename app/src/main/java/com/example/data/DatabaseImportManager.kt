package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DatabaseImportManager(private val context: Context) {

    private val sqlHandler = AppSQLiteHandler.getInstance(context)

    suspend fun importFromUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // 1. Try taking persistable URI permission on Android 10+
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Non-critical: some providers don't support persistable permissions
                Log.d("DatabaseImportManager", "Persistable permission notice: ${e.message}")
            }

            // 2. Stream directly into a local app cache file to avoid Scoped Storage timeouts or locking
            val cacheFile = File(context.cacheDir, "import_saf_cache_${System.currentTimeMillis()}.db")
            val copied = contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: 0L

            if (copied == 0L || !cacheFile.exists() || cacheFile.length() < 16) {
                cacheFile.delete()
                return@withContext Result.failure(Exception("تعذر قراءة بيانات ملف قاعدة البيانات أو أن الملف فارغ."))
            }

            // 3. Delegate to AppSQLiteHandler import logic
            val success = cacheFile.inputStream().use { input ->
                sqlHandler.importDatabaseFile(input)
            }
            cacheFile.delete()

            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("فشل استيراد قاعدة البيانات: تعذر التحقق من هيكل البيانات أو استبدال الملف."))
            }
        } catch (e: Exception) {
            Log.e("DatabaseImportManager", "Error importing database from Uri", e)
            Result.failure(e)
        }
    }
}
