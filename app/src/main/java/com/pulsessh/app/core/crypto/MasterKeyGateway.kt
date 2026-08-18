package com.pulsessh.app.core.crypto

import javax.crypto.Cipher

/**
 * Access to the AES-256-GCM master key that gates the database passphrase.
 *
 * The real implementation stores the key inside the Android KeyStore, hardware backed and
 * requiring a fresh biometric authentication for every use. Tests substitute a fake with no
 * authentication requirement so the wrap/unwrap contract can be exercised on the JVM.
 */
interface MasterKeyGateway {
    /**
     * Returns a [Cipher] ready for [Cipher.ENCRYPT_MODE] against the master key.
     *
     * The returned cipher has not been authenticated yet; a caller must run it through
     * biometric authentication (wrapping it in a `BiometricPrompt.CryptoObject`) before calling
     * [Cipher.doFinal] on it.
     */
    fun createEncryptCipher(): Cipher

    /**
     * Returns a [Cipher] ready for [Cipher.DECRYPT_MODE] against the master key, using [iv] as
     * the initialisation vector that was recorded when the ciphertext was produced.
     *
     * The returned cipher has not been authenticated yet; see [createEncryptCipher].
     */
    fun createDecryptCipher(iv: ByteArray): Cipher

    /**
     * Deletes the master key. Called when the key has been permanently invalidated (the user
     * changed their enrolled biometrics) so that a fresh key can be generated on the next wrap.
     */
    fun deleteKey()
}
