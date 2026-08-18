package com.pulsessh.app.core.security

import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class AppLockControllerTest {
    @Test
    fun `starts locked`() {
        val controller = AppLockController()

        assertThat(controller.isUnlocked.value).isFalse()
    }

    @Test
    fun `setUnlocked true unlocks`() {
        val controller = AppLockController()

        controller.setUnlocked(true)

        assertThat(controller.isUnlocked.value).isTrue()
    }

    @Test
    fun `onStop re-locks`() {
        val controller = AppLockController()
        controller.setUnlocked(true)

        controller.onStop(mockk<LifecycleOwner>())

        assertThat(controller.isUnlocked.value).isFalse()
    }
}
