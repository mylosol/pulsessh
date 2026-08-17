package com.pulsessh.app.core.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pulsessh.app.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.inject.Inject

private val WRAPPED_PASSPHRASE_KEY = stringPreferencesKey("wrapped_passphrase")
private val WRAPPED_IV_KEY = stringPreferencesKey("wrapped_iv")
private const val PASSPHRASE_LENGTH_BYTES = 32

/**
 * Owns the lifecycle of the random passphrase that a later change will use to open the SQLCipher
 * database. The passphrase itself is never stored in the clear: it is generated once, wrapped
 * with a [MasterKeyGateway] cipher that has already been authenticated by BiometricPrompt, and
 * persisted only in its wrapped form. `java.util.Base64` (not `android.util.Base64`) is used
 * deliberately: it is pure JVM, available since API 26 (this project's `minSdk`), and is what
 * keeps this class testable without Robolectric.
 */
class PassphraseVault
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val masterKeyGateway: MasterKeyGateway,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** True once a wrapped passphrase has been stored by a previous [wrapAndStore] call. */
        val hasStoredPassphrase: Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[WRAPPED_PASSPHRASE_KEY] != null }

        /** An encrypt [Cipher] to authenticate via BiometricPrompt, for first-run wrapping. */
        suspend fun prepareWrapCipher(): Cipher =
            withContext(ioDispatcher) {
                masterKeyGateway.createEncryptCipher()
            }

        /**
         * Generates a fresh random passphrase and stores it wrapped with [authenticatedCipher].
         *
         * @param authenticatedCipher an encrypt cipher from [prepareWrapCipher] that has already
         * passed BiometricPrompt authentication.
         */
        suspend fun wrapAndStore(authenticatedCipher: Cipher) =
            withContext(ioDispatcher) {
                val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
                val wrapped = authenticatedCipher.doFinal(passphrase)
                passphrase.fill(0)
                dataStore.edit { prefs ->
                    prefs[WRAPPED_PASSPHRASE_KEY] = Base64.getEncoder().encodeToString(wrapped)
                    prefs[WRAPPED_IV_KEY] = Base64.getEncoder().encodeToString(authenticatedCipher.iv)
                }
                Unit
            }

        /**
         * A decrypt [Cipher] to authenticate via BiometricPrompt, for unwrapping the stored
         * passphrase.
         *
         * @throws IllegalStateException if no passphrase has been stored yet.
         */
        suspend fun prepareUnwrapCipher(): Cipher =
            withContext(ioDispatcher) {
                val iv = storedIv() ?: error("No wrapped passphrase stored yet")
                masterKeyGateway.createDecryptCipher(iv)
            }

        /**
         * Decrypts and returns the stored passphrase using [authenticatedCipher]. The caller is
         * responsible for zeroing the returned array as soon as it is done with it.
         *
         * @param authenticatedCipher a decrypt cipher from [prepareUnwrapCipher] that has already
         * passed BiometricPrompt authentication.
         * @throws IllegalStateException if no passphrase has been stored yet.
         */
        suspend fun unwrap(authenticatedCipher: Cipher): ByteArray =
            withContext(ioDispatcher) {
                val wrapped = storedWrappedBytes() ?: error("No wrapped passphrase stored yet")
                authenticatedCipher.doFinal(wrapped)
            }

        /** Wipes the stored wrapped passphrase and the master key, so the next unlock starts fresh. */
        suspend fun reset() =
            withContext(ioDispatcher) {
                dataStore.edit { prefs ->
                    prefs.remove(WRAPPED_PASSPHRASE_KEY)
                    prefs.remove(WRAPPED_IV_KEY)
                }
                masterKeyGateway.deleteKey()
            }

        private suspend fun storedIv(): ByteArray? =
            dataStore.data.first()[WRAPPED_IV_KEY]?.let { Base64.getDecoder().decode(it) }

        private suspend fun storedWrappedBytes(): ByteArray? =
            dataStore.data.first()[WRAPPED_PASSPHRASE_KEY]?.let { Base64.getDecoder().decode(it) }
    }
