package com.raichess.data.diagnostics

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent on-device log of engine lifecycle events (init attempts,
 * failures with their reasons, fallback engagement, recovery), so "why
 * did my game fall back to RaiEngine?" is answerable after the fact —
 * logcat is gone by then. Ring-buffered in SharedPreferences and shown
 * by the Home screen's "Engine log" dialog.
 */
object EngineDiagnostics {

    private const val TAG = "EngineDiagnostics"
    private const val PREFS_NAME = "raichess_engine_log"
    private const val KEY_ENTRIES = "entries"
    const val MAX_ENTRIES = 100

    @Synchronized
    fun record(context: Context, message: String) {
        Log.i(TAG, message)
        try {
            val prefs = prefs(context)
            // Entries are newline-delimited in storage: a message containing
            // one would silently split into bogus entries
            val stamped = "${timestamp()}  ${message.replace('\n', ' ')}"
            val updated = appended(load(prefs.getString(KEY_ENTRIES, "")), stamped, MAX_ENTRIES)
            prefs.edit().putString(KEY_ENTRIES, updated.joinToString("\n")).apply()
        } catch (e: Exception) {
            // Diagnostics must never break the engine path
            Log.w(TAG, "failed to persist engine log entry", e)
        }
    }

    /** Stored entries, oldest first. */
    fun entries(context: Context): List<String> =
        load(prefs(context).getString(KEY_ENTRIES, ""))

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply()
    }

    /** Pure ring-buffer append: keeps the newest [max] entries. */
    fun appended(existing: List<String>, entry: String, max: Int): List<String> =
        (existing + entry).takeLast(max)

    private fun load(raw: String?): List<String> =
        raw?.takeIf { it.isNotEmpty() }?.split('\n') ?: emptyList()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun timestamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
}
