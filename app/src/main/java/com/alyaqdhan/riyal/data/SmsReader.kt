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
 * receiver and no background work anywhere in this app: messages are read only when a
 * scan runs, and only rows within the chosen time range. The one other look is
 * [sendersAfter], when the app comes to the front, which reads no message's text.
 */
object SmsReader {

    /**
     * Who sent each message newer than [afterMillis], and nothing else: enough to tell
     * whether a scan has anything new to read, without reading a single body.
     */
    fun sendersAfter(context: Context, afterMillis: Long): List<String> {
        val out = ArrayList<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(afterMillis.toString()),
            null,
        )?.use { cursor ->
            val iAddress = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (cursor.moveToNext()) out += cursor.getString(iAddress) ?: "unknown"
        }
        return out
    }

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
