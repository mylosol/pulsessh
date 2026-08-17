package com.pulsessh.app.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "pulsessh_master_key"
private const val TRANSFORMATION =
    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
private const val GCM_TAG_LENGTH_BITS = 128
private const val KEY_SIZE_BITS = 256

/**
 * [MasterKeyGateway] backed by the real Android KeyStore.
 *
 * The key is generated once, with StrongBox requested first (API 28+, with a TEE-backed fallback
 * if the device has no StrongBox module) per `docs/architecture.md` section 7.1. It requires a
 * fresh Class 3 biometric authentication for every use - the [Cipher] instances returned here are
 * unusable until a `BiometricPrompt` has authenticated them. The per-operation authentication
 * parameter API (`setUserAuthenticationParameters`) needs API 30; API 26-29 (this project's
 * `minSdk` is 26) use the older `setUserAuthenticationValidityDurationSeconds(-1)`, which has the
 * same "require authentication for every use" effect.
 *
 * [createEncryptCipher] creates the key on first use if it is absent (the normal first-run path).
 * [createDecryptCipher] never silently creates a key: if the alias is missing when a decrypt is
 * requested, it throws [KeyPermanentlyInvalidatedException] rather than decrypting stored
 * ciphertext against an unrelated, freshly generated key (which would fail with a confusing
 * `AEADBadTagException` instead of triggering the existing key-invalidation recovery path in
 * `LockViewModel`).
 */
@Singleton
class AndroidMasterKeyGateway
    @Inject
    constructor() : MasterKeyGateway {
        private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        override fun createEncryptCipher(): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, existingSecretKey() ?: generateSecretKey())
            }

        override fun createDecryptCipher(iv: ByteArray): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                val key =
                    existingSecretKey()
                        ?: throw KeyPermanentlyInvalidatedException(
                            "Master key alias '$MASTER_KEY_ALIAS' is missing; cannot decrypt with a new key.",
                        )
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }

        override fun deleteKey() {
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }

        private fun existingSecretKey(): SecretKey? = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey

        private fun generateSecretKey(): SecretKey {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    generator.init(baseSpecBuilder().setIsStrongBoxBacked(true).build())
                    return generator.generateKey()
                } catch (_: StrongBoxUnavailableException) {
                    // Fall through to the non-StrongBox spec below.
                }
            }
            generator.init(baseSpecBuilder().build())
            return generator.generateKey()
        }

        private fun baseSpecBuilder(): KeyGenParameterSpec.Builder {
            val builder =
                KeyGenParameterSpec
                    .Builder(MASTER_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(true)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }
    }
