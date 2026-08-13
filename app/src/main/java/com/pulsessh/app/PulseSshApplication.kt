package com.pulsessh.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for PulseSSH.
 *
 * The only responsibility of this class is to bootstrap Hilt: [HiltAndroidApp] generates the
 * `SingletonComponent` that every `@AndroidEntryPoint` (currently only `MainActivity`) and every
 * `@HiltViewModel` resolves its dependencies from.
 *
 * Deliberately empty otherwise. Process-wide initialisation that needs a `Context` belongs in a
 * Hilt module or an `Initializer`, not here, so that it stays testable and so that start-up cost
 * is paid lazily rather than on every cold start.
 */
@HiltAndroidApp
class PulseSshApplication : Application()
