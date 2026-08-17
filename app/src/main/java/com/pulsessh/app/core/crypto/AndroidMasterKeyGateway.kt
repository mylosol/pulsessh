package com.pulsessh.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "pulsessh_master_key"
private const val TRANSFORMATION =
    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
private const val GCM_TAG_LENGTH_BITS = 128
private const val KEY_SIZE_BITS = 256

/**
 * [MasterKeyGateway] backed by the real Android KeyStore.
 *
 * The key is generated once, with StrongBox requested first and a TEE-backed fallback if the
 * device has no StrongBox module, per `docs/architecture.md` section 7.1. It requires a fresh
 * Class 3 biometric authentication for every use - the [Cipher] instances returned here are
 * unusable until a `BiometricPrompt` has authenticated them.
 */
class AndroidMasterKeyGateway
    @Inject
    constructor() : MasterKeyGateway {
        private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        override fun createEncryptCipher(): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            }

        override fun createDecryptCipher(iv: ByteArray): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }

        override fun deleteKey() {
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }

        private fun getOrCreateSecretKey(): SecretKey {
            (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return generateSecretKey()
        }

        private fun generateSecretKey(): SecretKey {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            return try {
                generator.init(baseSpecBuilder().setIsStrongBoxBacked(true).build())
                generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                generator.init(baseSpecBuilder().setIsStrongBoxBacked(false).build())
                generator.generateKey()
            }
        }

        private fun baseSpecBuilder(): KeyGenParameterSpec.Builder =
            KeyGenParameterSpec
                .Builder(MASTER_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    }
