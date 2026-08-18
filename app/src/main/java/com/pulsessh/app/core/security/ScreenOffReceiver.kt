package com.pulsessh.app.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import javax.inject.Inject

/**
 * Re-locks the vault when the screen turns off.
 *
 * `Intent.ACTION_SCREEN_OFF` cannot be declared in the manifest on modern Android; it must be
 * registered with [Context.registerReceiver] at runtime, which
 * [com.pulsessh.app.PulseSshApplication] does for the lifetime of the process.
 */
class ScreenOffReceiver
    @Inject
    constructor(
        private val appLockController: AppLockController,
    ) : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                appLockController.setUnlocked(false)
            }
        }
    }
