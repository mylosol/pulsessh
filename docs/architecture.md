# Architecture

This document describes how PulseSSH is meant to be structured.

**Read this first.** The repository currently contains no Kotlin source. There
is no `app/src/main/java` directory and no `res/` directory. Everything below
describes the intended design and the conventions a contributor must follow
when they add the first files. Where a statement is about something that
already exists in the repository, it says so. Everything else is planned.

---

## 1. Layering

PulseSSH follows Clean Architecture with three logical layers, and MVI inside
the presentation layer.

```
        ui  (Compose screens, ViewModels, State/Intent/Effect)
         |  depends on
      domain  (models, use cases, repository interfaces)
         ^  depends on
       data  (Room, SQLCipher, SSHD, Commons Net, repository implementations)
```

The arrows matter. `ui` and `data` both depend on `domain`. `domain` depends
on nothing inside the app.

- **domain** holds the vocabulary of the product: `Host`, `SshKey`, `Session`,
  `Snippet`, `PortForward`, `TransferJob`, `LogEntry`. It also holds the use
  cases (`ConnectToHostUseCase`, `GenerateKeyPairUseCase`, and so on) and the
  repository *interfaces* those use cases call.
- **data** implements those interfaces. Room entities, DAOs, the SQLCipher
  factory, the SSH connection pool, the Telnet client, the SFTP client and
  the Mosh bridge all live here. Data layer types are mapped to domain types
  at the repository boundary. A Room `@Entity` never leaves the data layer.
- **ui** renders domain models and sends intents. It never touches a DAO, a
  socket or the KeyStore directly.
- **core** sits beside these three and holds cross cutting utilities:
  crypto helpers, KeyStore access, the logger and redactor, dispatcher
  providers, result types. Anything in `core` must be usable by more than one
  layer, or it belongs in the layer that uses it.

### The rule about Android imports in domain

**No file under `com.pulsessh.app.domain` may import `android.*`,
`androidx.*`, or any third party framework type (Room, SSHD, Compose, Hilt
runtime annotations beyond `javax.inject`).**

Why: the domain layer is the part that describes what the app does, and it
should be testable with a plain JVM unit test in milliseconds, with no
Robolectric and no emulator. It should also survive a change of database or
SSH library without edits.

Practical consequences:

- Use `kotlinx.coroutines.flow.Flow`, not `LiveData`.
- Use `kotlin.time` or a plain `Long` epoch, not `android.text.format`.
- Use a domain `Uri`-like value class or a `String`, not `android.net.Uri`.
  Storage Access Framework URIs are converted at the data or ui boundary.
- Use `javax.inject.Inject` for constructor injection. That is a plain JVM
  annotation and is acceptable. Hilt module annotations are not.
- If a use case needs the current time or a random source, inject an
  interface for it. Do not call `System.currentTimeMillis()` in domain code
  you intend to test.

This is enforced by review today. A detekt `ForbiddenImport` rule scoped to
the domain package would enforce it mechanically and should be added once the
domain package exists. That rule is **not written yet**.

---

## 2. Package layout

All code lives under `com.pulsessh.app`. The layout below follows the
directory tree in the project specification.

```
app/src/main/
├── cpp/                        # planned: libmosh wrapper, CMakeLists.txt
├── java/com/pulsessh/app/
│   ├── PulseSshApplication.kt  # @HiltAndroidApp  (named in the manifest, not written)
│   ├── MainActivity.kt         # single activity, sets FLAG_SECURE (not written)
│   ├── core/
│   │   ├── crypto/             # KeyStore master key, AES-GCM wrap/unwrap, key generation
│   │   ├── security/           # lock state, biometric prompt wrapper, passkey flow
│   │   ├── logging/            # Logger, LogSink, Redactor
│   │   ├── concurrency/        # DispatcherProvider
│   │   └── di/                 # app wide Hilt modules
│   ├── data/
│   │   ├── db/                 # RoomDatabase, entities, DAOs, migrations, converters
│   │   ├── repository/         # repository implementations
│   │   ├── ssh/                # MINA SSHD session pool, auth, port forwarding
│   │   ├── mosh/               # JNI bridge to the native client
│   │   ├── telnet/             # Commons Net client
│   │   ├── sftp/               # SFTP client, transfer queue, WorkManager workers
│   │   ├── saf/                # Storage Access Framework document access
│   │   └── di/                 # data layer Hilt bindings
│   ├── domain/
│   │   ├── model/              # Host, SshKey, Session, Snippet, PortForward, LogEntry
│   │   ├── repository/         # repository interfaces
│   │   └── usecase/            # one class per use case
│   └── ui/
│       ├── theme/              # Material3 theme, dynamic colour
│       ├── navigation/         # Compose navigation graph
│       ├── components/         # TabBar, TerminalView, FileBrowser, CommandDrawer
│       ├── host/               # host list, host editor, connection dialogs
│       ├── terminal/           # terminal screen with the top tab layout
│       ├── sftp/               # two pane file transfer manager
│       ├── security/           # lock screen, key generator UI
│       └── log/                # log viewer and export
└── res/                        # planned: strings, theme, backup rules XML
```

Conventions:

- One feature package under `ui/` per screen area. Each holds the screen
  composable, its `ViewModel`, and its `State`, `Intent` and `Effect` types.
- Shared composables go in `ui/components/`. If only one screen uses it, it
  stays in that screen's package.
- `data/db/migrations/` holds one file per migration. See
  `docs/migrations.md`.

---

## 3. MVI: State, Intent and Effect

Each screen has exactly three types plus a ViewModel.

| Type | What it is | Rules |
| --- | --- | --- |
| **State** | An immutable `data class` describing everything the screen renders. | One instance is the whole truth for the screen. No nullable "loading or error or data" soup; model the states you actually have. Held in a `StateFlow`. |
| **Intent** | A `sealed interface` of things the user or system did. | Named after the user action, not the implementation. `HostClicked`, not `NavigateToTerminal`. |
| **Effect** | A `sealed interface` of one shot events. | Navigation, snackbars, share sheet launches, biometric prompts. Delivered through a `Channel`, consumed once. |

The flow is a loop:

```
Composable ---- Intent ----> ViewModel ---- calls ----> UseCase (domain)
     ^                            |                          |
     |                            |                          v
     +---- State (StateFlow) -----+                     Repository (data)
     |
     +---- Effect (Channel, consumed once) ----+
```

Rules that keep this honest:

1. **The composable never mutates state.** It receives a `State` and a
   `(Intent) -> Unit`. That makes every screen previewable and testable
   without a ViewModel.
2. **State is produced in one place.** The ViewModel holds a
   `MutableStateFlow<State>` privately and exposes `StateFlow<State>`. Updates
   use `update { it.copy(...) }` so concurrent updates cannot lose a write.
3. **Effects are not state.** A snackbar or a navigation event must not
   survive a configuration change and re fire. That is what the `Channel` is
   for. Collect it with `LaunchedEffect` plus
   `lifecycle.repeatOnLifecycle(STARTED)`.
4. **A ViewModel talks to use cases, not repositories**, whenever there is
   any logic at all. A pure pass through may call a repository interface
   directly, but the moment two calls need combining, that belongs in a use
   case.
5. **The terminal is the exception worth planning for.** A terminal emits
   thousands of small byte chunks a second. Putting each one through a
   `StateFlow<State>` copy would allocate heavily and drop frames. The plan is
   for the terminal screen's `State` to hold a stable reference to a mutable
   screen buffer object plus a revision counter, with the buffer written on a
   background dispatcher and the counter bumped at a capped rate (for example
   60 Hz). The MVI loop still owns everything else on that screen. This is a
   design decision, not implemented code, and it should be revisited with a
   real profiler once Module B exists.

---

## 4. Dependency injection

Hilt 2.52 is on the classpath and the plugin is applied. No modules exist yet.

Intended placement:

| Module | Package | Installed in | Provides |
| --- | --- | --- | --- |
| `AppModule` | `core/di` | `SingletonComponent` | Application `Context`, `DispatcherProvider`, `CoroutineScope` for app lifetime work |
| `CryptoModule` | `core/di` | `SingletonComponent` | KeyStore master key manager, cipher helpers |
| `LoggingModule` | `core/di` | `SingletonComponent` | `Logger`, `Redactor`, log sink |
| `DatabaseModule` | `data/di` | `SingletonComponent` | `PulseSshDatabase`, the SQLCipher `SupportSQLiteOpenHelper.Factory`, every DAO |
| `RepositoryModule` | `data/di` | `SingletonComponent` | `@Binds` from repository interfaces (domain) to implementations (data) |
| `NetworkModule` | `data/di` | `SingletonComponent` | SSH client factory, connection pool, Telnet and SFTP clients |

Conventions:

- Prefer constructor injection with `@Inject`. Modules exist for types you do
  not own or cannot construct directly.
- Use `@Binds` for interface to implementation, `@Provides` only when
  construction needs arguments.
- ViewModels are `@HiltViewModel` and obtained with `hiltViewModel()` from
  `hilt-navigation-compose`.
- The connection pool is a singleton. Sessions must outlive the screens that
  show them, which is the whole point of Module B's tabs.
- `PulseSshApplication` carries `@HiltAndroidApp`. It is named in the manifest
  but has not been written.

A note on testing: `app/build.gradle.kts` currently sets
`testInstrumentationRunner` to the plain `androidx.test.runner.AndroidJUnitRunner`,
with a comment explaining that a Hilt aware runner is preferable but naming a
class that does not exist breaks every `androidTest` task. When instrumented
tests start, write `com.pulsessh.app.HiltTestRunner` and switch to it.

---

## 5. Threading model

Everything is coroutines. There are no raw threads and no `AsyncTask`.

| Work | Where it runs | Scope |
| --- | --- | --- |
| Compose recomposition and all UI state reads | Main thread | Composition |
| ViewModel state updates | `viewModelScope`, main dispatcher by default | Cleared with the ViewModel |
| Room reads and writes | `Dispatchers.IO` (Room's own executor for suspend DAOs) | Caller's scope |
| KeyStore and crypto operations | `Dispatchers.Default` for CPU work, `IO` when they touch disk | Caller's scope |
| SSH, Mosh and Telnet socket I/O | `Dispatchers.IO`, one long lived coroutine per session channel | An application scoped `CoroutineScope` owned by the connection pool, **not** `viewModelScope` |
| Terminal byte parsing | `Dispatchers.Default`, feeding the screen buffer | Session scope |
| SFTP transfers | `WorkManager` workers, so they survive the app being backgrounded | WorkManager |

Rules:

1. **A live session must never be scoped to a ViewModel or a composable.**
   Switching tabs, rotating the phone, or backgrounding the app must not close
   a socket. Sessions belong to the pool, which is a singleton, and the pool
   holds its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
2. **Suspending functions are main safe.** A repository function switches
   dispatcher internally with `withContext`. Callers never have to know.
3. **Dispatchers are injected**, through a `DispatcherProvider` interface, so
   unit tests can substitute a test dispatcher. Never hard code
   `Dispatchers.IO` in a class you intend to test.
4. **`SupervisorJob` at every level that holds siblings.** One session
   failing must not cancel the other tabs.
5. **Cancellation is cooperative and must be honoured.** Long loops that read
   from a socket check `isActive`, and clean up in a `finally` block so
   sockets and file handles close.
6. Collect flows in Compose with `collectAsStateWithLifecycle()` from
   `lifecycle-runtime-compose`, so collection stops when the screen is not
   visible.

---

## 6. Module to package map

Every module in the specification, and the packages that will implement it.
Nothing in this table is implemented.

| Spec module | Primary packages | Supporting packages |
| --- | --- | --- |
| **A. Security and authentication gate** | `core/crypto`, `core/security`, `ui/security` | `data/db` (SQLCipher open helper), `core/di`, `data/di` |
| **B. Multi tab terminal** | `ui/terminal`, `ui/components` (TabBar, TerminalView, key toolbar) | `domain/model` (`Session`), `data/ssh` (session pool) |
| **C. SSH, Mosh, Telnet, port forwarding** | `data/ssh`, `data/mosh`, `data/telnet`, `cpp/` | `domain/usecase`, `domain/model` (`Host`, `PortForward`, `Snippet`), `ui/host`, `ui/components` (CommandDrawer) |
| **D. SFTP and device file access** | `data/sftp`, `data/saf`, `ui/sftp` | `ui/components` (FileBrowser), WorkManager workers in `data/sftp` |
| **E. Local logging** | `core/logging` (Logger, Redactor), `ui/log` | `data/db` (log entity and DAO), `domain/model` (`LogEntry`) |

Snippets and their template variables (`{{ host.ip }}`, `{{ user }}`) belong
to Module C in the specification. The expansion logic is pure string handling
with no Android dependency, so it goes in `domain` and is unit testable. The
drawer that shows them is `ui/components`.

---

## 7. Security model

This section states the rules. None of them are implemented yet; the only
part present in the repository today is `android:allowBackup="false"` in the
manifest and the R8 keep rules for BouncyCastle and SQLCipher.

### 7.1 KeyStore master key

- On first launch the app generates an AES-256-GCM key inside the Android
  KeyStore under the alias `pulsessh_master_key`. StrongBox is requested when
  the device advertises it, with a fallback to the TEE backed keystore.
- The key material never leaves the KeyStore. The app holds a handle, not
  bytes. Nothing exports it, nothing logs it, nothing backs it up.
- The key is created with user authentication required, so using it needs a
  successful biometric (Class 3 / Strong) or passkey authentication through
  `BiometricPrompt` or the Credential Manager API.
- The master key's only job is to wrap the database passphrase and any
  private key passphrases. It is not used to encrypt bulk data directly.

### 7.2 SQLCipher passphrase handling

- The database passphrase is a random 32 byte value generated once, at first
  launch, from a `SecureRandom`.
- It is stored only in its wrapped form: encrypted with the KeyStore master
  key, together with its GCM initialisation vector, in a small file or in
  DataStore. The plaintext passphrase is never written to disk, never put in
  `SharedPreferences`, never placed in `BuildConfig`, and never hard coded.
- Unwrapping requires the user authentication described above. That is what
  makes the app's lock real rather than cosmetic.
- In memory the passphrase is held as a `CharArray` or `ByteArray`, passed
  straight to the SQLCipher open helper, and zeroed immediately afterwards.
  It is never converted to a `String`, because `String` is immutable and
  cannot be wiped.
- When the app is backgrounded or the screen turns off, the database is
  closed and the in memory copy is cleared. Coming back to the foreground
  requires authenticating again. Losing an in flight terminal session to this
  would be unacceptable, so the plan is to keep sockets open in the
  connection pool while the *database* is locked; the user sees the lock
  screen, and the sessions are still there afterwards. This is an explicit
  design decision and should be checked against the specification's "lock
  database access immediately" requirement during Module A review.

### 7.3 Why `FLAG_SECURE` is set

`MainActivity` sets `WindowManager.LayoutParams.FLAG_SECURE` at runtime. The
manifest carries a comment saying so, deliberately, rather than trying to set
it declaratively.

The reason is that a terminal window routinely contains material that must not
be captured: command output with credentials or tokens in it, key
fingerprints, server hostnames and internal addresses, and anything the user
pastes. `FLAG_SECURE` does three things:

- it blocks screenshots and screen recording of the app;
- it blanks the window in the recent apps thumbnail, so credentials are not
  left visible on the task switcher after backgrounding;
- it stops the window appearing on non secure external displays and in most
  screen sharing sessions.

It is applied at runtime rather than in the manifest because that is where it
can be made conditional later, for example a documented, off by default
developer setting for recording demos. There is no such setting today, and
adding one needs a deliberate decision, not a quiet commit.

### 7.4 Why backups are disabled

`android:allowBackup="false"` is set in `AndroidManifest.xml`, and the
manifest also points at `@xml/data_extraction_rules` and `@xml/backup_rules`
(those resource files still need to be written).

The app's data directory contains the SQLCipher database with hosts,
credentials and private keys, plus the wrapped database passphrase. Android
backup would copy that to Google's servers or to an adb backup on a desktop.
Two problems:

- the wrapped passphrase is useless without the KeyStore key, and the KeyStore
  key is device bound and never backed up, so a restored backup would produce
  an app with an undecryptable database and a confusing failure;
- even encrypted, exporting a credential store off the device contradicts the
  product's stated privacy position of local only storage.

So backup and cloud restore are off, and device to device transfer is off. If
a migration or export feature is wanted later, it must be an explicit, user
initiated, separately encrypted export, not a silent platform backup.

### 7.5 Redaction

**Every log line passes through the `Redactor` before it is displayed,
persisted or exported. There is no path around it.**

The intended shape:

- `Logger` is the only public logging entry point. Code never calls
  `android.util.Log` directly. A detekt `ForbiddenMethodCall` rule for
  `android.util.Log` should enforce this once `core/logging` exists; it is
  **not written yet**.
- `Logger` passes every message through `Redactor` before handing it to any
  sink. The database sink, the in memory ring buffer used by the log viewer,
  and the export writer all sit behind that single point.
- `Redactor` removes, at minimum: password prompts and their responses,
  keyboard interactive answers, private key blocks (anything between
  `-----BEGIN` and `-----END`), passphrases, and the contents of any field
  the host record marks as secret. Redacted material is replaced with a fixed
  marker such as `[redacted]`, so the shape of the log stays readable.
- Redaction happens on the way *in*, not on the way out. A secret that
  reaches the database has already leaked, because the export path and the
  viewer both read from there, and because an attacker with the database has
  the plaintext regardless of what the UI chooses to show.
- The `Redactor` needs unit tests with adversarial inputs: a key split across
  chunk boundaries, a password echoed back by a server, base64 that merely
  looks like a key. Those tests are part of Module E, not an afterthought.

QA scenario 24 in `docs/qa/test_plan.md` exists to check this by hand as
well.
