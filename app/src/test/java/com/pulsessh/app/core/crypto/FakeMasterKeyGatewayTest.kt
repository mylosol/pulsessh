package com.pulsessh.app.core.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class FakeMasterKeyGatewayTest {
    @Test
    fun `a value encrypted with the wrap cipher decrypts back with the unwrap cipher`() {
        val gateway = FakeMasterKeyGateway()
        val plaintext = "pulsessh-test-passphrase".toByteArray()

        val encryptCipher = gateway.createEncryptCipher()
        val ciphertext = encryptCipher.doFinal(plaintext)
        val decryptCipher = gateway.createDecryptCipher(encryptCipher.iv)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `deleteKey invalidates ciphertext produced under the previous key`() {
        val gateway = FakeMasterKeyGateway()
        val encryptCipher = gateway.createEncryptCipher()
        val ciphertext = encryptCipher.doFinal("secret".toByteArray())
        val iv = encryptCipher.iv

        gateway.deleteKey()

        assertThrows(AEADBadTagException::class.java) {
            gateway.createDecryptCipher(iv).doFinal(ciphertext)
        }
    }

    @Test
    fun `invalidateNextCipher makes the next cipher creation throw once`() {
        val gateway = FakeMasterKeyGateway()
        gateway.invalidateNextCipher = true

        assertThrows(KeyPermanentlyInvalidatedException::class.java) { gateway.createEncryptCipher() }
        // The flag is one-shot: the call after the thrown one succeeds normally.
        val cipher = gateway.createEncryptCipher()
        assertThat(cipher).isNotNull()
    }
}
