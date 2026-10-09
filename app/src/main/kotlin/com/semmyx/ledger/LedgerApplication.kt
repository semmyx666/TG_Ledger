package com.semmyx.ledger

import android.app.Application
import android.os.Process
import android.util.Log
import com.semmyx.ledger.diagnostics.CrashLogger
import com.semmyx.ledger.platform.RateRefreshWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LedgerApplication : Application() {
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        // Never log entry titles, PINs, purchase tokens, URLs with secrets, or exception messages.
        Log.e("Ledger", "Background task failed: ${error.javaClass.simpleName}")
    }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)
    val container: AppContainer by lazy { AppContainer(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        // Installed first so it wraps (and runs before) the fatal handler below, saving the
        // stack trace to app-internal storage. Settings -> Support can then show it, since we
        // otherwise have no way to see what actually crashed without adb/logcat access.
        CrashLogger.install(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Uncaught failures are NOT swallowed: continuing a corrupt process is unsafe.
            try { Log.e("Ledger", "Fatal failure: ${error.javaClass.simpleName}") }
            finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        RateRefreshWorker.schedule(this)
    }
}
