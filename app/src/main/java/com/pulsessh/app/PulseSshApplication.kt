package com.pulsessh.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.pulsessh.app.core.security.AppLockController
import com.pulsessh.app.core.security.ScreenOffReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for PulseSSH.
 *
 * Beyond bootstrapping Hilt, this class registers the two triggers that re-lock the vault:
 * [AppLockController] as a `ProcessLifecycleOwner` observer for backgrounding, and
 * [ScreenOffReceiver] for the screen turning off. Both are process-wide and outlive any single
 * screen, which is why they are wired here rather than in a ViewModel or a composable.
 */
@HiltAndroidApp
class PulseSshApplication : Application() {
    @Inject
    lateinit var appLockController: AppLockController

    @Inject
    lateinit var screenOffReceiver: ScreenOffReceiver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockController)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
