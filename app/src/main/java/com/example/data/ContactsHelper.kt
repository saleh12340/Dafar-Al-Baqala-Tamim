package com.example.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

object ContactsHelper {
    private const val TAG = "ContactsHelper"

    /**
     * Extracts Name and Phone from a Contact Uri returned by ActivityResultContracts.PickContact()
     */
    fun extractContactDetails(context: Context, contactUri: Uri): Pair<String, String>? {
        var name = ""
        var phone = ""
        var contactId: String? = null

        try {
            // First attempt: try reading DISPLAY_NAME and ID or NUMBER directly
            context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = cursor.getString(nameIdx) ?: ""
                    }

                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIdx != -1) {
                        contactId = cursor.getString(idIdx)
                    }

                    // Check if number column exists directly (if CommonDataKinds.Phone was picked)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (phoneIdx != -1) {
                        phone = cursor.getString(phoneIdx) ?: ""
                    }
                }
            }

            // If phone is not found yet, query Phone table by contact ID
            if (phone.isBlank() && !contactId.isNullOrBlank()) {
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pCursor ->
                    if (pCursor.moveToFirst()) {
                        val pIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (pIdx != -1) {
                            phone = pCursor.getString(pIdx) ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact details from URI: $contactUri", e)
        }

        // Clean up phone number (remove spaces, dashes, parentheses)
        val cleanPhone = phone.replace("[\\s\\-\\(\\)]".toRegex(), "").trim()

        return if (name.isNotBlank() || cleanPhone.isNotBlank()) {
            Pair(name.trim(), cleanPhone)
        } else {
            null
        }
    }

    /**
     * Queries device contacts list if READ_CONTACTS permission is available
     */
    fun getDevicePhoneContacts(context: Context, query: String = ""): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val seenPhones = mutableSetOf<String>()

        try {
            val selection = if (query.isNotBlank()) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            } else {
                null
            }
            val selectionArgs = if (query.isNotBlank()) {
                arrayOf("%$query%", "%$query%")
            } else {
                null
            }

            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )

            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                    val rawPhone = if (phoneIdx != -1) c.getString(phoneIdx) ?: "" else ""
                    val phone = rawPhone.replace("[\\s\\-\\(\\)]".toRegex(), "").trim()

                    if (name.isNotBlank() && phone.isNotBlank() && !seenPhones.contains(phone)) {
                        seenPhones.add(phone)
                        result.add(Pair(name.trim(), phone))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device contacts", e)
        }

        return result
    }
}
