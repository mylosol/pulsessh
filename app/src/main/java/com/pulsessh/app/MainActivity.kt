package com.pulsessh.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.pulsessh.app.ui.PulseSshApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity that hosts the whole Compose navigation graph.
 *
 * Rotation, keyboard and UI-mode changes are handled by the activity itself (see
 * `android:configChanges` in the manifest) so that a live terminal session is never torn down by
 * a configuration change.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /**
     * Applies the window-level security policy, enables edge-to-edge drawing and installs the
     * Compose content.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE marks this window as containing secure content. The platform then:
        //   * refuses screenshots and screen recordings of the window, and
        //   * renders a blank placeholder instead of a live preview in the recents/overview list
        //     and in non-secure display mirroring.
        // Terminal output routinely contains credentials, tokens and remote file contents, so the
        // flag is set unconditionally and for the lifetime of the process. It is applied here in
        // code rather than in the manifest because the manifest has no equivalent attribute, and
        // it is set before the first frame so no unprotected frame can ever be captured.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Draw behind the system bars; the composables inset themselves via Scaffold.
        enableEdgeToEdge()

        setContent {
            PulseSshApp()
        }
    }
}
