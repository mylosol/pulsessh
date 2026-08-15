# Module A: security gate (biometric-only, no database yet) — design

Status: approved, not yet implemented.

## Scope

This is Phase 1, item 1 of `docs/roadmap.md`, scoped to exactly the security
gate: an AES-256-GCM master key in the Android KeyStore, BiometricPrompt
Class 3 unlock, automatic re-lock on background and screen off, and
generation/wrap/unwrap of a random database passphrase. It does **not**
include Room or SQLCipher — `docs/roadmap.md` lists "Data layer" as its own
numbered item, and the handoff notes say one pull request per numbered item.
CredentialManager passkey unlock is also out of scope for this PR; it is a
separate, larger API surface (registration ceremony, credential storage) and
will follow as its own PR once the biometric gate is proven.

Because there is no database yet, this PR proves the full
generate → wrap → unlock → unwrap → re-lock cycle in isolation. The unwrapped
passphrase is discarded immediately after a successful unwrap. The next PR
(Data layer) is what actually hands the passphrase to SQLCipher's open
helper.

## Components

All packages match the layout already documented in `docs/architecture.md`
section 2 and the module map in section 6.

### `core/crypto/MasterKeyGateway`

Interface, so the code that touches `AndroidKeyStore` can be swapped for a
fake in tests:

- `getOrCreateSecretKey(): SecretKey` — looks up the KeyStore alias
  `pulsessh_master_key`; if absent, generates one with
  `KeyGenParameterSpec`: AES/GCM, 256-bit, `setUserAuthenticationRequired(true)`,
  `setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)`,
  and `setIsStrongBoxBacked(true)` with a caught `StrongBoxUnavailableException`
  fallback to a non-StrongBox spec, per `docs/architecture.md` section 7.1.
- `createEncryptCipher(): Cipher` — a `Cipher` in `ENCRYPT_MODE` bound to the
  key, for wrapping.
- `createDecryptCipher(iv: ByteArray): Cipher` — a `Cipher` in `DECRYPT_MODE`
  bound to the key and a stored IV, for unwrapping.

`AndroidMasterKeyGateway` is the real implementation, using
`java.security.KeyStore.getInstance("AndroidKeyStore")`.

`FakeMasterKeyGateway` (test source set) uses a plain in-memory
`javax.crypto.KeyGenerator`-produced `SecretKey` with no authentication
requirement, so `Cipher` init/encrypt/decrypt behavior is fully exercisable
on the JVM without Robolectric or a device.

### `core/crypto/PassphraseVault`

Owns the random passphrase's lifecycle:

- `hasStoredPassphrase(): Flow<Boolean>` — whether a wrapped blob exists yet.
- `prepareWrapCipher(): Cipher` — for first run: returns an encrypt cipher
  from `MasterKeyGateway`, to be authenticated via BiometricPrompt before use.
- `wrapAndStore(authenticatedCipher: Cipher): Unit` — generates 32 random
  bytes via `SecureRandom`, encrypts them with the now-authenticated cipher,
  and persists `{wrapped bytes, IV}` in a dedicated DataStore file
  (`pulsessh_crypto_prefs`).
- `prepareUnwrapCipher(): Cipher` — for a returning user: reads the stored
  IV, returns a decrypt cipher from `MasterKeyGateway`, to be authenticated
  via BiometricPrompt before use.
- `unwrap(authenticatedCipher: Cipher): ByteArray` — reads the stored wrapped
  bytes, decrypts, returns the passphrase. Caller is responsible for zeroing
  the array immediately after use (in this PR: right away, since nothing
  consumes it yet).
- Catches `KeyPermanentlyInvalidatedException` (thrown when the user's
  enrolled biometrics change) in both wrap and unwrap paths: deletes the
  stored blob and the KeyStore key, so the app falls back cleanly to a fresh
  first-run flow instead of crash-looping on a key that can never work again.

All suspend functions run on the injected `@IoDispatcher`, per the threading
table in `docs/architecture.md` section 5.

### `core/security/AppLockController`

`@Singleton`, application-scoped. Holds `StateFlow<Boolean>` (`isUnlocked`).

- Set to `true` by `LockViewModel` after a successful unwrap (returning user)
  or a successful wrap-and-store (first run).
- Set to `false` by:
  - a `DefaultLifecycleObserver` registered on `ProcessLifecycleOwner`,
    reacting to `onStop` (the app went to the background);
  - a `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF`, registered in
    `PulseSshApplication.onCreate()` (screen-off is not a lifecycle event and
    needs its own receiver).
- Deliberately holds no reference to any session/socket state. This mirrors
  the explicit decision recorded in `docs/architecture.md` section 7.2: the
  database (and, in this PR, the unlocked gate) locks on background or screen
  off, but live connections are untouched — there are none yet, but the
  controller's shape must not need to change when they exist.

`PulseSshApp`'s `NavHost` collects `AppLockController.isUnlocked` and
force-navigates back to the `Lock` route whenever it flips to `false`,
mirroring the existing pop-up-to logic used for the forward unlock
navigation.

### `ui/lock` changes

`LockViewModel` remains free of any Android framework type beyond plain
`javax.crypto.Cipher`, so it stays a pure JVM unit test target. It depends on
`MasterKeyGateway` and `PassphraseVault` only.

Flow:

1. On `LockIntent.Unlock`: check `PassphraseVault.hasStoredPassphrase()`. If
   false, get a wrap cipher; if true, get an unwrap cipher.
2. Emit `LockEffect.RequestBiometricAuth(cipher, mode)` — architecture.md's
   own Effect examples list "biometric prompts" explicitly as this kind of
   one-shot event.
3. The Composable/Activity layer collects the effect and makes the actual
   `BiometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))`
   call — this cannot happen inside the ViewModel because `BiometricPrompt`
   requires a `FragmentActivity`.
4. On success, the authenticated cipher comes back through
   `LockIntent.BiometricAuthSucceeded(cipher, mode)` — intents remain the
   only way into the ViewModel, per `core/mvi/Mvi.kt`.
5. `LockViewModel` calls `PassphraseVault.wrapAndStore(cipher)` or
   `.unwrap(cipher)` depending on mode, zeroes any returned bytes
   immediately, sets `AppLockController.isUnlocked = true`, and emits
   `LockEffect.NavigateToHosts`.
6. On failure/cancel: `LockIntent.BiometricAuthFailed(reason)` sets a
   user-visible `error` in state, matching the existing `LockUiState.error`
   field.

Before offering the unlock button at all, the screen checks
`BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`. If the device has no
usable biometric enrolled, the screen shows a fixed message ("Set up a
screen lock or fingerprint to use PulseSSH") instead of a dead button — the
app is unusable without Class 3 biometric per the spec, so this must be
explicit rather than a silent failure.

`MainActivity` likely needs to change from `ComponentActivity` to
`androidx.fragment.app.FragmentActivity`, since `BiometricPrompt` requires
one. `FragmentActivity` extends `ComponentActivity`, so `setContent` and the
rest of the Compose setup are unaffected. The exact requirement is confirmed
against the pinned `biometric-ktx` 1.2.0-alpha05 API during implementation.

### Dependency injection

A new `core/di/SecurityModule` (alongside the already-documented slot for
`core/security` in the section-6 module map) provides `AppLockController` as
a singleton. The already-documented `CryptoModule` slot is extended to also
provide the `DataStore<Preferences>` instance backing `PassphraseVault`
(file name `pulsessh_crypto_prefs`), since it is crypto-adjacent persistence
rather than general app data — the Room database gets its own
`DatabaseModule` in `data/di` in the next PR.

### New dependencies

`androidx-biometric` and `androidx-datastore-preferences`, both already
pinned in `gradle/libs.versions.toml` since Phase 0 but not yet referenced
from `app/build.gradle.kts`.

## Error handling summary

- No biometric enrolled: caught up front via `BiometricManager`, screen shows
  guidance instead of a non-functional button.
- User cancels or fails authentication: `LockIntent.BiometricAuthFailed`,
  surfaced as `LockUiState.error`, retryable.
- `KeyPermanentlyInvalidatedException` (enrolled biometrics changed): handled
  inside `PassphraseVault` by wiping the stored blob and key, silently
  falling back to a fresh first-run flow on the next unlock attempt.

## Testing

`LockViewModelTest`, `PassphraseVaultTest` (against `FakeMasterKeyGateway`),
and `AppLockControllerTest` (against a fake lifecycle/receiver trigger) all
run as plain JVM unit tests — no Robolectric, no emulator, consistent with
`docs/ci.md`'s no-emulator-on-pull-requests rule and with the roadmap's own
instruction that "Unit tests use a fake KeyStore façade."

`AndroidMasterKeyGateway` and the actual `BiometricPrompt` call in the
Activity layer are the only pieces that cannot be exercised by a JVM unit
test. This is a known, accepted gap — the same shape as every other
Android-framework-touching class in this project today, and is manually
verified per `docs/qa/test_plan.md`.

## Exit criteria

- `LockScreen` performs a real biometric unlock on a physical device or an
  emulator with enrolled biometrics (manual verification; no emulator job in
  CI).
- Backgrounding the app or turning off the screen returns to the `Lock`
  route on next foreground.
- `./gradlew ktlintFormat ktlintCheck detekt testDebugUnitTest assembleDebug`
  passes clean, matching every prior PR in this repository.
