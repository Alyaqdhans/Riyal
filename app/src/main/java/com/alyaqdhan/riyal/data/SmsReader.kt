package com.alyaqdhan.riyal.data

import android.content.Context
import android.provider.Telephony

data class RawSms(
    val id: Long,
    val sender: String,
    val body: String,
    val atMillis: Long,
)

/**
 * One-shot, read-only query of the SMS inbox. There is deliberately no broadcast
 * receiver and no background work anywhere in this app: messages are read only at
 * the moment the user taps Scan, and only rows within the chosen time range.
 */
object SmsReader {

    fun readInbox(context: Context, sinceMillis: Long): List<RawSms> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = if (sinceMillis > 0) "${Telephony.Sms.DATE} >= ?" else null
        val args = if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null

        val out = ArrayList<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val iId = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddress = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                out += RawSms(
                    id = cursor.getLong(iId),
                    sender = cursor.getString(iAddress) ?: "unknown",
                    body = cursor.getString(iBody) ?: "",
                    atMillis = cursor.getLong(iDate),
                )
            }
        }
        return out
    }
}
