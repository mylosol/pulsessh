package com.pulsessh.app.ui

import com.google.common.truth.Truth.assertThat
import com.pulsessh.app.core.security.AppLockController
import org.junit.Test

class AppLockViewModelTest {
    @Test
    fun `isUnlocked mirrors the controller's state`() {
        val appLockController = AppLockController()
        val viewModel = AppLockViewModel(appLockController)

        assertThat(viewModel.isUnlocked.value).isFalse()

        appLockController.setUnlocked(true)

        assertThat(viewModel.isUnlocked.value).isTrue()
    }
}
