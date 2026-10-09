package com.semmyx.ledger.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the most recent uncaught crash to app-internal storage so it survives the process
 * dying, and can be viewed later from Settings -> Support without needing adb/logcat access.
 *
 * Only the exception class name, message, and stack trace (file/line/method names) are stored --
 * never entry titles, PINs, purchase tokens, or other user data, matching the existing logging
 * rule in LedgerApplication.
 */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    /** Wraps the app's existing uncaught-exception handler so every fatal crash is saved first. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(appContext, error)
            } catch (_: Throwable) {
                // The logger itself must never cause a second crash while handling the first.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(context: Context, error: Throwable) {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = "TG Ledger crash - $timestamp\n\n$writer"
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    /** Returns the saved crash report, if any, or null when the app hasn't crashed since it was last cleared. */
    fun readLastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
