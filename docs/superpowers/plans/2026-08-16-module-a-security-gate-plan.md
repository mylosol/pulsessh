# Module A Security Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder `LockViewModel` with a real security gate — an AES-256-GCM master key in the Android KeyStore gated by BiometricPrompt Class 3, a random 32-byte passphrase wrapped by that key, and automatic re-lock on background/screen-off — per `docs/superpowers/specs/2026-08-14-module-a-security-gate-design.md`.

**Architecture:** New `core/crypto` (`MasterKeyGateway` interface + Android/fake implementations, `PassphraseVault`) and `core/security` (`AppLockController`, `ScreenOffReceiver`) packages, all constructor-injected via Hilt with no new DI modules except one (`CryptoModule`) since the rest self-bind. `LockViewModel` stays pure-JVM-testable; the actual `BiometricPrompt` call moves into `LockScreen`, round-tripping through `LockEffect`/`LockIntent`.

**Tech Stack:** Kotlin, Hilt, `androidx.biometric` and `androidx.datastore-preferences` (already in `gradle/libs.versions.toml` and already declared in `app/build.gradle.kts`, just unused until now), plus a new `androidx.fragment:fragment-ktx` dependency for `FragmentActivity`.

---

### Task 1: `MasterKeyGateway` — interface, fake, and real Android implementation

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/core/crypto/MasterKeyGateway.kt`
- Create: `app/src/main/java/com/pulsessh/app/core/crypto/AndroidMasterKeyGateway.kt`
- Create: `app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGateway.kt`
- Test: `app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGatewayTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGatewayTest.kt
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.crypto.FakeMasterKeyGatewayTest"`
Expected: FAIL — compile error, `Unresolved reference: FakeMasterKeyGateway`.

- [ ] **Step 3: Write `MasterKeyGateway` and `FakeMasterKeyGateway`**

```kotlin
// app/src/main/java/com/pulsessh/app/core/crypto/MasterKeyGateway.kt
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
```

```kotlin
// app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGateway.kt
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.crypto.FakeMasterKeyGatewayTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the real Android implementation**

This class cannot be exercised by a JVM unit test (it needs the real Android KeyStore and a device); it is created here so the interface has its production implementation and so `assembleDebug` proves it compiles.

```kotlin
// app/src/main/java/com/pulsessh/app/core/crypto/AndroidMasterKeyGateway.kt
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
```

- [ ] **Step 6: Confirm the module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (this is the only signal available for `AndroidMasterKeyGateway`, per the design doc's accepted testing gap).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/core/crypto/MasterKeyGateway.kt \
        app/src/main/java/com/pulsessh/app/core/crypto/AndroidMasterKeyGateway.kt \
        app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGateway.kt \
        app/src/test/java/com/pulsessh/app/core/crypto/FakeMasterKeyGatewayTest.kt
git commit -m "feat: add MasterKeyGateway (KeyStore master key access)"
```

---

### Task 2: `PassphraseVault`

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/core/crypto/PassphraseVault.kt`
- Test: `app/src/test/java/com/pulsessh/app/core/crypto/PassphraseVaultTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/pulsessh/app/core/crypto/PassphraseVaultTest.kt
package com.pulsessh.app.core.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val EXPECTED_PASSPHRASE_LENGTH_BYTES = 32

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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.crypto.PassphraseVaultTest"`
Expected: FAIL — compile error, `Unresolved reference: PassphraseVault`.

- [ ] **Step 3: Write `PassphraseVault`**

```kotlin
// app/src/main/java/com/pulsessh/app/core/crypto/PassphraseVault.kt
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
        fun prepareWrapCipher(): Cipher = masterKeyGateway.createEncryptCipher()

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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.crypto.PassphraseVaultTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/core/crypto/PassphraseVault.kt \
        app/src/test/java/com/pulsessh/app/core/crypto/PassphraseVaultTest.kt
git commit -m "feat: add PassphraseVault"
```

---

### Task 3: `AppLockController`

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/core/security/AppLockController.kt`
- Test: `app/src/test/java/com/pulsessh/app/core/security/AppLockControllerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/pulsessh/app/core/security/AppLockControllerTest.kt
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.security.AppLockControllerTest"`
Expected: FAIL — compile error, `Unresolved reference: AppLockController`.

- [ ] **Step 3: Write `AppLockController`**

```kotlin
// app/src/main/java/com/pulsessh/app/core/security/AppLockController.kt
package com.pulsessh.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the vault is unlocked, application-wide.
 *
 * Deliberately holds no reference to any live session or socket: backgrounding the app or
 * turning the screen off re-locks the vault, but the connection pool - once it exists - is
 * untouched. See `docs/architecture.md` section 7.2.
 *
 * Registered as a [DefaultLifecycleObserver] on `ProcessLifecycleOwner` by
 * [com.pulsessh.app.PulseSshApplication] so that backgrounding the whole app re-locks it; screen
 * off is handled separately by [ScreenOffReceiver], since it is not a lifecycle event.
 */
@Singleton
class AppLockController
    @Inject
    constructor() : DefaultLifecycleObserver {
        private val _isUnlocked = MutableStateFlow(false)

        /** True once a successful biometric unlock has completed; false whenever the vault re-locks. */
        val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

        /** Sets the unlock state. Called by [com.pulsessh.app.ui.lock.LockViewModel] on success. */
        fun setUnlocked(unlocked: Boolean) {
            _isUnlocked.value = unlocked
        }

        /** Re-locks when the app goes to the background. */
        override fun onStop(owner: LifecycleOwner) {
            _isUnlocked.value = false
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.security.AppLockControllerTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/core/security/AppLockController.kt \
        app/src/test/java/com/pulsessh/app/core/security/AppLockControllerTest.kt
git commit -m "feat: add AppLockController"
```

---

### Task 4: `ScreenOffReceiver`

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/core/security/ScreenOffReceiver.kt`
- Test: `app/src/test/java/com/pulsessh/app/core/security/ScreenOffReceiverTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/pulsessh/app/core/security/ScreenOffReceiverTest.kt
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.security.ScreenOffReceiverTest"`
Expected: FAIL — compile error, `Unresolved reference: ScreenOffReceiver`.

- [ ] **Step 3: Write `ScreenOffReceiver`**

```kotlin
// app/src/main/java/com/pulsessh/app/core/security/ScreenOffReceiver.kt
package com.pulsessh.app.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import javax.inject.Inject

/**
 * Re-locks the vault when the screen turns off.
 *
 * `Intent.ACTION_SCREEN_OFF` cannot be declared in the manifest on modern Android; it must be
 * registered with [Context.registerReceiver] at runtime, which
 * [com.pulsessh.app.PulseSshApplication] does for the lifetime of the process.
 */
class ScreenOffReceiver
    @Inject
    constructor(
        private val appLockController: AppLockController,
    ) : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                appLockController.setUnlocked(false)
            }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.core.security.ScreenOffReceiverTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/core/security/ScreenOffReceiver.kt \
        app/src/test/java/com/pulsessh/app/core/security/ScreenOffReceiverTest.kt
git commit -m "feat: add ScreenOffReceiver"
```

---

### Task 5: `CryptoModule` and application wiring

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/core/di/CryptoModule.kt`
- Modify: `app/src/main/java/com/pulsessh/app/PulseSshApplication.kt`

This task has no dedicated test: `CryptoModule` is pure Hilt wiring (a `@Binds`/`@Provides` module has nothing to unit test beyond "does the graph compile", which `assembleDebug` already proves), and `PulseSshApplication`'s two new lines register real Android framework objects that need a device to observe, consistent with the design doc's accepted gap for framework glue code.

- [ ] **Step 1: Write `CryptoModule`**

```kotlin
// app/src/main/java/com/pulsessh/app/core/di/CryptoModule.kt
package com.pulsessh.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pulsessh.app.core.crypto.AndroidMasterKeyGateway
import com.pulsessh.app.core.crypto.MasterKeyGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val CRYPTO_PREFERENCES_FILE_NAME = "pulsessh_crypto_prefs"

/** Bindings for the KeyStore master key and the DataStore that persists the wrapped passphrase. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    abstract fun bindMasterKeyGateway(impl: AndroidMasterKeyGateway): MasterKeyGateway

    companion object {
        @Provides
        @Singleton
        fun provideCryptoPreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile(CRYPTO_PREFERENCES_FILE_NAME) },
            )
    }
}
```

- [ ] **Step 2: Register the lifecycle observer and the screen-off receiver**

Replace the full contents of `app/src/main/java/com/pulsessh/app/PulseSshApplication.kt`:

```kotlin
// app/src/main/java/com/pulsessh/app/PulseSshApplication.kt
package com.pulsessh.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.pulsessh.app.core.security.AppLockController
import com.pulsessh.app.core.security.ScreenOffReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for PulseSSH.
 *
 * Beyond bootstrapping Hilt, this class registers the two triggers that re-lock the vault:
 * [AppLockController] as a `ProcessLifecycleOwner` observer for backgrounding, and
 * [ScreenOffReceiver] for the screen turning off. Both are process-wide and outlive any single
 * screen, which is why they are wired here rather than in a ViewModel or a composable.
 */
@HiltAndroidApp
class PulseSshApplication : Application() {
    @Inject
    lateinit var appLockController: AppLockController

    @Inject
    lateinit var screenOffReceiver: ScreenOffReceiver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockController)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
```

- [ ] **Step 3: Confirm the module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/core/di/CryptoModule.kt \
        app/src/main/java/com/pulsessh/app/PulseSshApplication.kt
git commit -m "feat: wire CryptoModule and register lock triggers in PulseSshApplication"
```

---

### Task 6: Real `LockViewModel`

**Files:**
- Modify: `app/src/main/java/com/pulsessh/app/ui/lock/LockViewModel.kt` (full replace)
- Modify: `app/src/test/java/com/pulsessh/app/ui/lock/LockViewModelTest.kt` (full replace)

- [ ] **Step 1: Replace the failing tests**

Replace the full contents of `app/src/test/java/com/pulsessh/app/ui/lock/LockViewModelTest.kt`:

```kotlin
// app/src/test/java/com/pulsessh/app/ui/lock/LockViewModelTest.kt
package com.pulsessh.app.ui.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.pulsessh.app.core.crypto.FakeMasterKeyGateway
import com.pulsessh.app.core.crypto.PassphraseVault
import com.pulsessh.app.core.security.AppLockController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Behaviour of the unlock gate.
 *
 * [LockViewModel] depends on the real [PassphraseVault], backed by a [FakeMasterKeyGateway] and
 * a temp-file-backed `DataStore`, so these tests exercise the whole unlock loop with only the
 * Android KeyStore boundary faked - matching the roadmap's "unit tests use a fake KeyStore
 * facade" instruction. Nothing here needs Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): Pair<LockViewModel, AppLockController> {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        val vault = PassphraseVault(dataStore, FakeMasterKeyGateway(), UnconfinedTestDispatcher())
        val appLockController = AppLockController()
        return LockViewModel(vault, appLockController) to appLockController
    }

    @Test
    fun `the gate starts idle and without an error`() =
        runTest {
            val (viewModel, _) = newViewModel()

            assertThat(viewModel.state.value).isEqualTo(LockUiState())
        }

    @Test
    fun `unlock on first run requests a wrap cipher`() =
        runTest {
            val (viewModel, _) = newViewModel()

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            viewModel.effects.test {
                val effect = awaitItem() as LockEffect.RequestBiometricAuth
                assertThat(effect.mode).isEqualTo(CipherAuthMode.WRAP)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a succeeded wrap unlocks and navigates to hosts`() =
        runTest {
            val (viewModel, appLockController) = newViewModel()
            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()
            val requested =
                viewModel.effects.test {
                    (awaitItem() as LockEffect.RequestBiometricAuth).also { cancelAndIgnoreRemainingEvents() }
                }

            viewModel.onIntent(LockIntent.BiometricAuthSucceeded(requested.cipher, requested.mode))
            advanceUntilIdle()

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(LockEffect.NavigateToHosts)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(appLockController.isUnlocked.value).isTrue()
            assertThat(viewModel.state.value.isUnlocking).isFalse()
        }

    @Test
    fun `a failed biometric attempt surfaces the error and stays locked`() =
        runTest {
            val (viewModel, appLockController) = newViewModel()

            viewModel.onIntent(LockIntent.BiometricAuthFailed("Fingerprint not recognised"))
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isEqualTo(LockError.Platform("Fingerprint not recognised"))
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(appLockController.isUnlocked.value).isFalse()
        }

    @Test
    fun `a permanently invalidated key resets the vault and surfaces a retryable error`() =
        runTest {
            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
            val fakeGateway = FakeMasterKeyGateway()
            val vault = PassphraseVault(dataStore, fakeGateway, UnconfinedTestDispatcher())
            val viewModel = LockViewModel(vault, AppLockController())
            fakeGateway.invalidateNextCipher = true

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isEqualTo(LockError.KeyInvalidated)
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(vault.hasStoredPassphrase.first()).isFalse()
        }

    @Test
    fun `dismissing an error clears it`() =
        runTest {
            val (viewModel, _) = newViewModel()

            viewModel.onIntent(LockIntent.DismissError)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isNull()
        }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.ui.lock.LockViewModelTest"`
Expected: FAIL — compile errors (`LockIntent.BiometricAuthSucceeded`, `CipherAuthMode`, and the new `LockViewModel` constructor do not exist yet).

- [ ] **Step 3: Replace `LockViewModel`**

Replace the full contents of `app/src/main/java/com/pulsessh/app/ui/lock/LockViewModel.kt`:

```kotlin
// app/src/main/java/com/pulsessh/app/ui/lock/LockViewModel.kt
package com.pulsessh.app.ui.lock

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.lifecycle.viewModelScope
import com.pulsessh.app.core.crypto.PassphraseVault
import com.pulsessh.app.core.mvi.MviViewModel
import com.pulsessh.app.core.mvi.UiEffect
import com.pulsessh.app.core.mvi.UiIntent
import com.pulsessh.app.core.mvi.UiState
import com.pulsessh.app.core.security.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/** Which operation an authenticated [Cipher] from [LockEffect.RequestBiometricAuth] will perform. */
enum class CipherAuthMode { WRAP, UNWRAP }

/**
 * A human-facing failure to show on the unlock gate.
 *
 * Kept out of [LockUiState.error] as a raw `String` on purpose: [LockViewModel] has no
 * `Context` and must stay that way for JVM testability, so it cannot resolve a string
 * resource itself (`CLAUDE.md` requires every user-visible string to live in
 * `res/values/strings.xml`). [Platform] carries text the OS already resolved and localised
 * (BiometricPrompt's own error text), which is reasonable to pass through as-is; [KeyInvalidated]
 * carries no text at all; [LockScreen] resolves it to `R.string.lock_error_key_invalidated`.
 */
sealed interface LockError {
    /** A message already resolved by the platform - BiometricPrompt's own error text. */
    data class Platform(val message: String) : LockError

    /** The KeyStore key was permanently invalidated (the user's enrolled biometrics changed). */
    data object KeyInvalidated : LockError
}

/**
 * State of the unlock gate.
 *
 * @property isUnlocking true while an unlock attempt is in flight; the button is disabled and a
 * progress indicator is shown.
 * @property error the current failure to show, or null when there is nothing to report. It is
 * cleared by [LockIntent.DismissError] and by starting a new attempt.
 */
data class LockUiState(
    val isUnlocking: Boolean = false,
    val error: LockError? = null,
) : UiState

/** Everything the user (or the platform, on the user's behalf) can ask the unlock gate to do. */
sealed interface LockIntent : UiIntent {
    /** Begin an unlock attempt. */
    data object Unlock : LockIntent

    /** Acknowledge and clear the current [LockUiState.error]. */
    data object DismissError : LockIntent

    /** BiometricPrompt authenticated [cipher] successfully; [mode] says what to do with it. */
    data class BiometricAuthSucceeded(val cipher: Cipher, val mode: CipherAuthMode) : LockIntent

    /** BiometricPrompt failed, was cancelled, or errored with a human-readable [message]. */
    data class BiometricAuthFailed(val message: String) : LockIntent
}

/** One-shot events the unlock gate sends to its host. */
sealed interface LockEffect : UiEffect {
    /** The vault is open; the host should navigate to the host list and drop the lock route. */
    data object NavigateToHosts : LockEffect

    /** Ask the host to run BiometricPrompt against [cipher], then report back via a [LockIntent]. */
    data class RequestBiometricAuth(val cipher: Cipher, val mode: CipherAuthMode) : LockEffect
}

/**
 * Drives the unlock gate.
 *
 * Depends only on [PassphraseVault] and [AppLockController] - both plain Kotlin beyond
 * `javax.crypto.Cipher` - so this class runs in ordinary JVM unit tests. The actual
 * `BiometricPrompt` call happens in [LockScreen], which owns the `FragmentActivity` it requires;
 * this class only prepares the [Cipher] to authenticate and reacts to the result.
 */
@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        private val passphraseVault: PassphraseVault,
        private val appLockController: AppLockController,
    ) : MviViewModel<LockUiState, LockIntent, LockEffect>(LockUiState()) {
        override fun onIntent(intent: LockIntent) {
            when (intent) {
                LockIntent.Unlock -> unlock()
                LockIntent.DismissError -> setState { copy(error = null) }
                is LockIntent.BiometricAuthSucceeded -> onBiometricSucceeded(intent.cipher, intent.mode)
                is LockIntent.BiometricAuthFailed -> onBiometricFailed(intent.message)
            }
        }

        private fun unlock() {
            setState { copy(isUnlocking = true, error = null) }
            viewModelScope.launch {
                try {
                    val hasStoredPassphrase = passphraseVault.hasStoredPassphrase.first()
                    val (cipher, mode) =
                        if (hasStoredPassphrase) {
                            passphraseVault.prepareUnwrapCipher() to CipherAuthMode.UNWRAP
                        } else {
                            passphraseVault.prepareWrapCipher() to CipherAuthMode.WRAP
                        }
                    emitEffect(LockEffect.RequestBiometricAuth(cipher, mode))
                } catch (_: KeyPermanentlyInvalidatedException) {
                    // The user's enrolled biometrics changed since the key was created; the old
                    // key (and anything wrapped with it) can never be used again. Wipe both and
                    // let the next Unlock attempt take the first-run wrap path instead.
                    passphraseVault.reset()
                    setState { copy(isUnlocking = false, error = LockError.KeyInvalidated) }
                }
            }
        }

        private fun onBiometricSucceeded(
            cipher: Cipher,
            mode: CipherAuthMode,
        ) {
            viewModelScope.launch {
                val passphrase =
                    when (mode) {
                        CipherAuthMode.WRAP -> {
                            passphraseVault.wrapAndStore(cipher)
                            null
                        }
                        CipherAuthMode.UNWRAP -> passphraseVault.unwrap(cipher)
                    }
                passphrase?.fill(0)
                appLockController.setUnlocked(true)
                setState { copy(isUnlocking = false) }
                emitEffect(LockEffect.NavigateToHosts)
            }
        }

        private fun onBiometricFailed(message: String) {
            setState { copy(isUnlocking = false, error = LockError.Platform(message)) }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.pulsessh.app.ui.lock.LockViewModelTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/ui/lock/LockViewModel.kt \
        app/src/test/java/com/pulsessh/app/ui/lock/LockViewModelTest.kt
git commit -m "feat: drive LockViewModel from PassphraseVault and biometric auth"
```

---

### Task 7: `MainActivity` becomes a `FragmentActivity`, and `LockScreen` calls `BiometricPrompt`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/pulsessh/app/MainActivity.kt`
- Modify: `app/src/main/java/com/pulsessh/app/ui/lock/LockScreen.kt`

No dedicated test: this task is Compose UI plus a real `BiometricPrompt` call, neither of which can run outside a device (the design doc's accepted gap). `assembleDebug` is the compile-time check; the exit criteria's manual device pass is the behavioural one.

- [ ] **Step 1: Add the `fragment-ktx` dependency**

In `gradle/libs.versions.toml`, add to `[versions]` (alphabetical among the `f`/`g` entries, next to `googleId`):

```toml
fragment = "1.8.6"
```

Add to `[libraries]`, next to the other `androidx-*` entries:

```toml
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragment" }
```

If `1.8.6` fails to resolve in Step 5 below, check the current stable version at
`https://maven.google.com/web/index.html#androidx.fragment:fragment-ktx` and update the version
string — this project has hit exactly this class of version-drift issue before (see
`SSH Spec.md` section 5.2) and the fix is always a version bump.

In `app/build.gradle.kts`, add the dependency next to `implementation(libs.androidx.activity.compose)`:

```kotlin
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
```

- [ ] **Step 2: Add the new strings**

In `app/src/main/res/values/strings.xml`, add inside the "Unlock gate" section, after `lock_action_dismiss_error`:

```xml
    <string name="lock_biometric_prompt_title">Unlock PulseSSH</string>
    <string name="lock_biometric_prompt_subtitle">Use your fingerprint, face, or screen lock</string>
    <string name="lock_biometric_prompt_cancel">Cancel</string>
    <string name="lock_error_no_biometric">Set up a screen lock or fingerprint in your device settings to use PulseSSH.</string>
    <string name="lock_error_not_recognised">Not recognised. Try again.</string>
    <string name="lock_error_generic">Something went wrong. Try again.</string>
    <string name="lock_error_key_invalidated">Your device\'s screen lock or biometrics changed. Unlock again to set up a new one.</string>
```

- [ ] **Step 3: Change `MainActivity` to extend `FragmentActivity`**

In `app/src/main/java/com/pulsessh/app/MainActivity.kt`, replace the import and the class declaration:

```kotlin
// Replace:
import androidx.activity.ComponentActivity
// With:
import androidx.fragment.app.FragmentActivity
```

```kotlin
// Replace:
class MainActivity : ComponentActivity() {
// With:
class MainActivity : FragmentActivity() {
```

The rest of `MainActivity.kt` (the `FLAG_SECURE` call, `enableEdgeToEdge()`, `setContent`) is unchanged — all three are inherited from `ComponentActivity`, which `FragmentActivity` extends.

- [ ] **Step 4: Wire `BiometricPrompt` into `LockScreen`**

Replace the full contents of `app/src/main/java/com/pulsessh/app/ui/lock/LockScreen.kt`:

```kotlin
// app/src/main/java/com/pulsessh/app/ui/lock/LockScreen.kt
package com.pulsessh.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsessh.app.R

/**
 * The unlock gate: the first screen of the app and the only one visible while the vault is locked.
 *
 * Stateful entry point. It observes [LockViewModel], forwards user actions as [LockIntent]s,
 * runs the real `BiometricPrompt` when asked to via [LockEffect.RequestBiometricAuth], and
 * translates [LockEffect.NavigateToHosts] into a call on [onUnlocked]. The layout itself lives in
 * the stateless [LockContent] so it can be previewed and tested without Hilt.
 *
 * @param onUnlocked invoked once the vault is open, so the caller can navigate away.
 * @param modifier applied to the root of the screen.
 * @param viewModel the screen's ViewModel; injected by Hilt, overridable in tests.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(viewModel, onUnlocked) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LockEffect.NavigateToHosts -> onUnlocked()
                is LockEffect.RequestBiometricAuth -> runBiometricPrompt(activity, viewModel, effect)
            }
        }
    }

    LockContent(
        state = state,
        onUnlockClick = { viewModel.onIntent(LockIntent.Unlock) },
        onDismissErrorClick = { viewModel.onIntent(LockIntent.DismissError) },
        modifier = modifier,
    )
}

/** Checks biometric availability, then runs `BiometricPrompt` against [effect]'s cipher. */
private fun runBiometricPrompt(
    activity: FragmentActivity,
    viewModel: LockViewModel,
    effect: LockEffect.RequestBiometricAuth,
) {
    val canAuthenticate =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_no_biometric)))
        return
    }

    val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.lock_biometric_prompt_cancel))
            .build()

    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            LockAuthenticationCallback(activity, viewModel, effect.mode),
        )
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(effect.cipher))
}

/** [BiometricPrompt.AuthenticationCallback] that reports back into [LockViewModel] via intents. */
private class LockAuthenticationCallback(
    private val activity: FragmentActivity,
    private val viewModel: LockViewModel,
    private val mode: CipherAuthMode,
) : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val authenticatedCipher = result.cryptoObject?.cipher
        if (authenticatedCipher != null) {
            viewModel.onIntent(LockIntent.BiometricAuthSucceeded(authenticatedCipher, mode))
        } else {
            viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_generic)))
        }
    }

    override fun onAuthenticationError(
        errorCode: Int,
        errString: CharSequence,
    ) {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(errString.toString()))
    }

    override fun onAuthenticationFailed() {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_not_recognised)))
    }
}

/**
 * Stateless layout of the unlock gate.
 *
 * @param state what to render.
 * @param onUnlockClick called when the user asks to unlock.
 * @param onDismissErrorClick called when the user dismisses the current error.
 * @param modifier applied to the root of the layout.
 */
@Composable
fun LockContent(
    state: LockUiState,
    onUnlockClick: () -> Unit,
    onDismissErrorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (state.isUnlocking) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_status_unlocking),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Button(onClick = onUnlockClick) {
                Text(text = stringResource(R.string.lock_action_unlock))
            }
        }
        if (state.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val errorText =
                when (val error = state.error) {
                    is LockError.Platform -> error.message
                    LockError.KeyInvalidated -> stringResource(R.string.lock_error_key_invalidated)
                }
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onDismissErrorClick) {
                Text(text = stringResource(R.string.lock_action_dismiss_error))
            }
        }
    }
}
```

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. This is the only automated check available for this task — verify manually on a device per the plan's final task before considering Module A done.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/strings.xml \
        app/src/main/java/com/pulsessh/app/MainActivity.kt \
        app/src/main/java/com/pulsessh/app/ui/lock/LockScreen.kt
git commit -m "feat: run real BiometricPrompt from LockScreen"
```

---

### Task 8: Re-lock navigation in `PulseSshApp`

**Files:**
- Create: `app/src/main/java/com/pulsessh/app/ui/AppLockViewModel.kt`
- Modify: `app/src/main/java/com/pulsessh/app/ui/PulseSshApp.kt`

No dedicated test: this is Compose navigation reacting to `AppLockController`, whose own state transitions are already covered by `AppLockControllerTest`; what's left here is wiring that only a device/manual pass (this plan's exit criteria) can confirm, matching `docs/architecture.md`'s existing acknowledgement that Compose navigation timing isn't unit-tested in this project.

- [ ] **Step 1: Write `AppLockViewModel`**

```kotlin
// app/src/main/java/com/pulsessh/app/ui/AppLockViewModel.kt
package com.pulsessh.app.ui

import androidx.lifecycle.ViewModel
import com.pulsessh.app.core.security.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin bridge from [AppLockController]'s process-wide state into the Compose navigation graph.
 *
 * A `ViewModel` rather than a direct [AppLockController] injection because Hilt's Compose
 * integration resolves singletons into composables through `hiltViewModel()`; this class holds
 * no state of its own beyond forwarding [AppLockController.isUnlocked].
 */
@HiltViewModel
class AppLockViewModel
    @Inject
    constructor(
        appLockController: AppLockController,
    ) : ViewModel() {
        val isUnlocked: StateFlow<Boolean> = appLockController.isUnlocked
    }
```

- [ ] **Step 2: Observe it from `PulseSshApp` and force-navigate to `Lock` when it flips false**

Replace the full contents of `app/src/main/java/com/pulsessh/app/ui/PulseSshApp.kt`:

```kotlin
// app/src/main/java/com/pulsessh/app/ui/PulseSshApp.kt
package com.pulsessh.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsessh.app.ui.hosts.HostsScreen
import com.pulsessh.app.ui.lock.LockScreen
import com.pulsessh.app.ui.theme.PulseSshTheme

/**
 * Root composable: theme, window surface and the navigation graph.
 *
 * The graph starts locked. Every route that can show host data therefore sits behind
 * [PulseSshRoute.Lock], and unlocking pops the lock route off the back stack so that the system
 * back gesture cannot return to a screen that has already served its purpose. Backgrounding the
 * app or turning the screen off flips [AppLockViewModel.isUnlocked] back to false - observed
 * here - which forces navigation straight back to [PulseSshRoute.Lock], regardless of which
 * screen was showing.
 *
 * @param navController hoisted so that tests and future deep-link handling can drive navigation.
 * @param appLockViewModel bridges [com.pulsessh.app.core.security.AppLockController]'s
 * process-wide state into this composable; injected by Hilt, overridable in tests.
 */
@Composable
fun PulseSshApp(
    navController: NavHostController = rememberNavController(),
    appLockViewModel: AppLockViewModel = hiltViewModel(),
) {
    val isUnlocked by appLockViewModel.isUnlocked.collectAsStateWithLifecycle()

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            navController.navigate(PulseSshRoute.Lock.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    PulseSshTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NavHost(
                navController = navController,
                startDestination = PulseSshRoute.Lock.route,
            ) {
                composable(PulseSshRoute.Lock.route) {
                    LockScreen(
                        onUnlocked = {
                            navController.navigate(PulseSshRoute.Hosts.route) {
                                // The lock screen must not be reachable by pressing back, and a
                                // duplicate unlock must not stack a second copy of the host list.
                                popUpTo(PulseSshRoute.Lock.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(PulseSshRoute.Hosts.route) {
                    HostsScreen()
                }
            }
        }
    }
}

/**
 * Every destination in the application graph.
 *
 * Routes are plain strings rather than the type-safe serializable destinations of Navigation 2.8
 * because the kotlinx-serialization plugin is not applied to this module. Keeping them in one
 * sealed interface still gives a single place to look, compile-time-checked references at call
 * sites, and an exhaustive `when` if navigation ever needs one.
 */
sealed interface PulseSshRoute {
    /** The path registered with the [NavHost]; unique across the graph. */
    val route: String

    /** Unlock gate. Start destination, and the only screen shown while the vault is locked. */
    data object Lock : PulseSshRoute {
        override val route: String = "lock"
    }

    /** The saved host list, and the landing screen after a successful unlock. */
    data object Hosts : PulseSshRoute {
        override val route: String = "hosts"
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pulsessh/app/ui/AppLockViewModel.kt \
        app/src/main/java/com/pulsessh/app/ui/PulseSshApp.kt
git commit -m "feat: re-navigate to Lock when AppLockController re-locks"
```

---

### Task 9: Full pipeline, push, and PR

**Files:** none — verification only.

- [ ] **Step 1: Run the full local pipeline**

Run: `./gradlew ktlintFormat ktlintCheck detekt testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tasks pass. If `ktlintFormat` changes any file, `git diff` it, and if the diff is more than import ordering, fold the fix into the relevant task's commit with `git commit --amend` before continuing; if it is only import ordering, commit it separately:

```bash
git add -u
git commit -m "style: ktlintFormat"
```

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/module-a-security-gate
```

- [ ] **Step 3: Open the pull request**

```bash
gh pr create --title "Module A: biometric security gate" --base main --head feat/module-a-security-gate --body "$(cat <<'EOF'
## Summary
- KeyStore-backed AES-256-GCM master key (`MasterKeyGateway`/`AndroidMasterKeyGateway`), StrongBox-then-TEE fallback, gated by BiometricPrompt Class 3.
- `PassphraseVault` generates, wraps, and persists a random 32-byte passphrase; unwraps it on return. No Room/SQLCipher wiring yet - that's the next PR per docs/roadmap.md.
- `AppLockController` + `ScreenOffReceiver` re-lock on background and on screen off, per docs/architecture.md section 7.2.
- `LockViewModel` stays pure-JVM-testable; the real `BiometricPrompt` call lives in `LockScreen`.
- Design: docs/superpowers/specs/2026-08-14-module-a-security-gate-design.md

## Test plan
- [x] `./gradlew ktlintFormat ktlintCheck detekt testDebugUnitTest assembleDebug` passes clean locally
- [ ] Manual: real biometric unlock on a device with enrolled biometrics
- [ ] Manual: backgrounding the app returns to the Lock screen
- [ ] Manual: turning the screen off returns to the Lock screen
- [ ] `ci-ok` green on this PR

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Watch `ci-ok`**

```bash
gh pr checks --watch
```

Expected: `static`, `unit`, `assemble`, and `ci-ok` all pass. If `gh pr merge` or `gh api` calls are blocked by a permission classifier (as happened in the Phase 0 PR), stop and report back rather than retrying workarounds — the user merges manually in that case.
