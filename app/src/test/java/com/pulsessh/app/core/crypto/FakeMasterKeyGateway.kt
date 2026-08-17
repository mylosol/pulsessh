package com.pulsessh.app.core.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val FAKE_KEY_SIZE_BITS = 256

/**
 * In-memory [MasterKeyGateway] for JVM unit tests.
 *
 * Uses a plain [SecretKey] with no authentication requirement, so tests can drive the full
 * [Cipher] lifecycle - including [deleteKey] and the resulting key-rotation case - without the
 * Android KeyStore or a device.
 */
class FakeMasterKeyGateway : MasterKeyGateway {
    private var secretKey: SecretKey? = null

    /**
     * When true, the next [createEncryptCipher] or [createDecryptCipher] call throws
     * [KeyPermanentlyInvalidatedException] instead of returning a cipher, simulating the user's
     * enrolled biometrics changing. Resets itself to false after throwing once.
     */
    var invalidateNextCipher: Boolean = false

    override fun createEncryptCipher(): Cipher {
        throwIfInvalidated()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKeyOrCreate())
        }
    }

    override fun createDecryptCipher(iv: ByteArray): Cipher {
        throwIfInvalidated()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKeyOrCreate(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
    }

    override fun deleteKey() {
        secretKey = null
    }

    private fun throwIfInvalidated() {
        if (invalidateNextCipher) {
            invalidateNextCipher = false
            throw KeyPermanentlyInvalidatedException()
        }
    }

    private fun secretKeyOrCreate(): SecretKey =
        secretKey ?: KeyGenerator.getInstance("AES").apply { init(FAKE_KEY_SIZE_BITS, SecureRandom()) }
            .generateKey()
            .also { secretKey = it }
}
