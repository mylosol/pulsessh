package com.pulsessh.app.core.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val EXPECTED_PASSPHRASE_LENGTH_BYTES = 32

@OptIn(ExperimentalCoroutinesApi::class)
class PassphraseVaultTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newVault(): PassphraseVault {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        return PassphraseVault(dataStore, FakeMasterKeyGateway(), UnconfinedTestDispatcher())
    }

    @Test
    fun `no passphrase is stored before the first wrap`() =
        runTest {
            val vault = newVault()

            assertThat(vault.hasStoredPassphrase.first()).isFalse()
        }

    @Test
    fun `wrapAndStore records that a passphrase now exists`() =
        runTest {
            val vault = newVault()

            vault.wrapAndStore(vault.prepareWrapCipher())

            assertThat(vault.hasStoredPassphrase.first()).isTrue()
        }

    @Test
    fun `a wrapped passphrase unwraps back to 32 bytes`() =
        runTest {
            val vault = newVault()
            vault.wrapAndStore(vault.prepareWrapCipher())

            val passphrase = vault.unwrap(vault.prepareUnwrapCipher())

            assertThat(passphrase).hasLength(EXPECTED_PASSPHRASE_LENGTH_BYTES)
        }

    @Test
    fun `reset clears the stored passphrase`() =
        runTest {
            val vault = newVault()
            vault.wrapAndStore(vault.prepareWrapCipher())

            vault.reset()

            assertThat(vault.hasStoredPassphrase.first()).isFalse()
        }
}
