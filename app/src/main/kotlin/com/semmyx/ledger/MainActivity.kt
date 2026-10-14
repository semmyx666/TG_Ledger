package com.semmyx.ledger

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.semmyx.ledger.platform.HighRefreshRate
import com.semmyx.ledger.navigation.AppRoot
import com.semmyx.ledger.navigation.AppViewModel
import com.semmyx.ledger.common.LedgerViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var appModel: AppViewModel
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { appModel.lock() }
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) appModel.lock()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = (application as LedgerApplication).container
        appModel = ViewModelProvider(this, LedgerViewModelFactory(AppViewModel::class.java) {
            AppViewModel(container.preferences, container.security)
        })[AppViewModel::class.java]
        splash.setKeepOnScreenCondition { !appModel.state.value.ready }
        splash.setOnExitAnimationListener { view ->
            if (ValueAnimator.areAnimatorsEnabled()) {
                view.view.animate().alpha(0f).setDuration(180).withEndAction { view.remove() }.start()
            } else view.remove()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { AppRoot(this, appModel, container) }
    }
    override fun onResume() {
        super.onResume()
        val app = application as LedgerApplication
        app.applicationScope.launch { app.container.billing.refreshIfNeeded() }
    }
    override fun onStop() {
        if (!isChangingConfigurations) appModel.lock()
        super.onStop()
    }
    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }
}
