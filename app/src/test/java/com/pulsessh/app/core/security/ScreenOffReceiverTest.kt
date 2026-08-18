package com.pulsessh.app.core.security

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ScreenOffReceiverTest {
    @Test
    fun `screen off locks the vault`() {
        val appLockController = AppLockController().apply { setUnlocked(true) }
        val receiver = ScreenOffReceiver(appLockController)
        val intent = mockk<Intent> { every { action } returns Intent.ACTION_SCREEN_OFF }

        receiver.onReceive(mockk(relaxed = true), intent)

        assertThat(appLockController.isUnlocked.value).isFalse()
    }

    @Test
    fun `other actions are ignored`() {
        val appLockController = AppLockController().apply { setUnlocked(true) }
        val receiver = ScreenOffReceiver(appLockController)
        val intent = mockk<Intent> { every { action } returns Intent.ACTION_USER_PRESENT }

        receiver.onReceive(mockk(relaxed = true), intent)

        assertThat(appLockController.isUnlocked.value).isTrue()
    }
}
