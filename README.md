# PulseSSH

An Android SSH, Mosh, Telnet and SFTP client, written in Kotlin with Jetpack
Compose. No telemetry. All data stays on the device, in a locally encrypted
database.

**Status: build skeleton only. The app does not work yet.**

At the time of writing this repository contains the Gradle build, the CI and
release pipelines, the lint and static analysis configuration, and one
`AndroidManifest.xml`. It contains **no Kotlin source files and no `res/`
directory**. Nothing described in the project specification has been
implemented. See [State of the project](#state-of-the-project) below for the
module by module breakdown, and read that section before you assume any
feature exists.

---

## What PulseSSH is meant to be

The specification (`project_spec.md`, supplied separately) describes a client
with five functional modules:

- **A. Security and authentication gate.** A master key held in the Android
  KeyStore, biometric or passkey unlock, and a SQLCipher encrypted database
  for hosts, keys, passphrases, logs and snippets.
- **B. Multi tab terminal.** A top tab bar holding several live sessions at
  once, with a Compose terminal view that understands ANSI and VT100 control
  codes, plus an on screen key toolbar.
- **C. SSH, Mosh, Telnet and port forwarding engine.** Public key, password
  and keyboard interactive authentication, local, remote and dynamic SOCKS
  forwarding, native Mosh over UDP, and saved command snippets with template
  variables.
- **D. SFTP and device file access.** A two pane file manager, Storage Access
  Framework integration for the Download folder and removable storage, a
  background transfer queue with resume, and `chmod`.
- **E. Local logging.** A log viewer showing connection and handshake steps,
  with passwords and key material stripped before anything is shown or
  exported, and export as a `.log` file through the Android share sheet.

None of this is built yet.

---

## Technology stack

These are the versions actually pinned in `gradle/libs.versions.toml` and
`app/build.gradle.kts`. A library appearing here means the dependency is
declared, not that any code uses it.

### Build and language

| Item | Version | Source |
| --- | --- | --- |
| Gradle wrapper | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.7.3 | catalog `agp` |
| Kotlin | 2.0.21 | catalog `kotlin` |
| KSP | 2.0.21-1.0.28 | catalog `ksp` |
| Java / Kotlin target | 17 | `app/build.gradle.kts` |
| compileSdk / targetSdk | 35 | `app/build.gradle.kts` |
| minSdk | 26 (Android 8.0) | `app/build.gradle.kts` |
| applicationId | `com.pulsessh.app` | `app/build.gradle.kts` |
| versionName / versionCode | 0.1.0 / 1 | `app/build.gradle.kts` |

### Libraries

| Area | Library | Version |
| --- | --- | --- |
| UI | Compose BOM | 2024.12.01 |
| UI | Material3, material icons extended | from the BOM |
| UI | Navigation Compose | 2.8.5 |
| UI | Activity Compose | 1.9.3 |
| Lifecycle | lifecycle runtime, runtime-compose, process | 2.8.7 |
| DI | Hilt | 2.52 |
| DI | hilt-navigation-compose | 1.2.0 |
| Database | Room (runtime, ktx, compiler, testing) | 2.6.1 |
| Database | SQLCipher (`net.zetetic:sqlcipher-android`) | 4.6.1 |
| Database | androidx sqlite-ktx | 2.4.0 |
| Preferences | DataStore preferences | 1.1.1 |
| Crypto | androidx security-crypto | 1.1.0-alpha06 |
| Auth | androidx biometric-ktx | 1.2.0-alpha05 |
| Auth | androidx credentials (+ play-services-auth) | 1.3.0 |
| SSH / SFTP | Apache MINA SSHD (core, common, sftp) | 2.13.2 |
| Crypto primitives | BouncyCastle (bcprov, bcpkix, jdk18on) | 1.79 |
| Telnet | Apache Commons Net | 3.11.1 |
| Background work | WorkManager | 2.10.0 |
| Files | androidx documentfile | 1.0.1 |
| Concurrency | kotlinx-coroutines | 1.9.0 |
| Test | JUnit 4 / MockK / Turbine / Truth | 4.13.2 / 1.13.13 / 1.2.0 / 1.4.4 |
| Test | Robolectric | 4.14.1 |
| Test | androidx test junit / Espresso | 1.2.1 / 3.6.1 |
| Static analysis | ktlint Gradle plugin | 12.1.1 |
| Static analysis | detekt | 1.23.7 |

Two things worth knowing about this list:

- **Mosh is not represented.** The specification asks for a native `libmosh`
  wrapper built with CMake through the NDK. There is no `app/src/main/cpp`
  directory, no `CMakeLists.txt` and no `externalNativeBuild` block. Mosh
  support is entirely unstarted.
- **The Kotlin serialization plugin is declared in the catalog but not
  applied** in `app/build.gradle.kts`, and no serialization runtime is on the
  classpath. It is available if a future change needs it.

---

## Opening the project in Android Studio

1. Install Android Studio Ladybug (2024.2.1) or newer. Older versions do not
   understand AGP 8.7.
2. In Android Studio, open **SDK Manager** and install:
   - Android SDK Platform 35 (compileSdk and targetSdk)
   - Android SDK Build-Tools matching AGP 8.7
   - A JDK 17 (Android Studio bundles one; **File > Settings > Build,
     Execution, Deployment > Build Tools > Gradle > Gradle JDK** must be set
     to 17, not 11 and not 21)
3. Clone the repository and choose **File > Open**, then select the
   `pulsessh` directory itself. Do not use "Import project"; the project is
   already a Gradle project.
4. Let the initial Gradle sync finish. It downloads the Gradle 8.11.1
   distribution and all dependencies, so the first sync needs a network
   connection and several minutes.
5. `local.properties` is generated by Android Studio and is deliberately
   git ignored. It holds your SDK path and nothing else. Never commit it.

You do not need the NDK today. When Mosh work starts, that will change and
this section will need updating.

---

## Building a debug APK

From the repository root:

```bash
./gradlew assembleDebug
```

The output would land in `app/build/outputs/apk/debug/`. The debug variant
uses the application id `com.pulsessh.app.debug`, so it can sit alongside a
release build on the same phone.

**Expect this to fail on a clean checkout right now.** The manifest declares
`.PulseSshApplication` and `.MainActivity`, and references
`@string/app_name`, `@style/Theme.PulseSSH`, `@xml/data_extraction_rules` and
`@xml/backup_rules`. None of those classes or resources exist in the
repository. The build will only produce an APK once the first source and
resource files are added. This also means the `assemble` job in CI cannot
currently be green.

Other useful commands:

```bash
./gradlew ktlintCheck detekt     # formatting and static analysis
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew ktlintFormat           # fix formatting in place
```

Formatting rules come from `.editorconfig`, which is the single source of
truth for ktlint. Detekt rules come from `config/detekt/detekt.yml` and are
layered on top of detekt's defaults, with `maxIssues: 0`.

---

## Branch and pull request workflow

### Branches

`main` is protected. Nobody pushes to it directly. Every change starts as a
branch off `main`, for example `feature/biometric-gate` or `fix/tab-leak`,
and returns to `main` through a pull request.

### Pull requests

Open the PR against `main`. The template in
`.github/pull_request_template.md` asks for what changed, why, which spec
module it touches, which tests were added, and what you exercised manually on
a device. Fill it in properly; the manual QA notes are the only record that a
change was tried on real hardware.

The template also carries a checklist. Two items on it are hard rules, not
suggestions:

- no destructive Room migration (see `docs/migrations.md`);
- no secrets in the repository.

### The required check

CI is defined in `.github/workflows/ci.yml`. It runs four jobs in a chain, so
a cheap failure never pays for an expensive one:

| Job | What it runs |
| --- | --- |
| `static` | `ktlintCheck` and `detekt` |
| `unit` | `testDebugUnitTest` |
| `assemble` | `assembleDebug`, uploads the APK as `pulsessh-preview-apk` |
| `ci-ok` | does no work; fails if any of the three failed, was cancelled or was skipped |

**`ci-ok` is the only check branch protection should require.** Do not list
`static`, `unit` and `assemble` separately.

Changes that touch only `**.md`, `docs/**`, `LICENSE` or `.gitignore` skip
the workflow entirely through `paths-ignore`. That is intentional and saves
build minutes, but it has a side effect: on such a PR the workflow never
runs, so `ci-ok` never reports and classic branch protection can leave the PR
waiting forever. `docs/ci.md` explains the two ways round it.

Reports are uploaded on failure only, so you can read a broken test or a
lint violation without rerunning the job.

### Releases

Merging to `main` never releases anything. It only runs CI.

A release happens for one reason: an annotated tag matching `v*.*.*` is
pushed. That triggers `.github/workflows/release.yml`:

1. `verify` re runs ktlint, detekt and the unit tests against the tagged
   commit. It produces no artifacts and exists only to refuse a broken tag.
2. `release` targets the GitHub Environment named `production`. That
   Environment holds the required reviewer, so the job pauses for a human
   approval before the signing key is ever decoded. After approval it builds
   `bundleRelease` and `assembleRelease`, attaches the signed APK and AAB to
   the GitHub Release, and shreds the decoded keystore whatever the outcome.

```bash
git checkout main && git pull
# update versionName / versionCode in app/build.gradle.kts first
git tag -a v1.2.3 -m "PulseSSH 1.2.3"
git push origin v1.2.3
```

Signing is opt in and environment driven. `app/build.gradle.kts` creates the
release signing config only when all four of `PULSESSH_KEYSTORE_PATH`,
`PULSESSH_KEYSTORE_PASSWORD`, `PULSESSH_KEY_ALIAS` and
`PULSESSH_KEY_PASSWORD` are set. Locally none of them are, so the release
variant simply builds unsigned instead of failing. The release workflow
therefore checks explicitly that all four are present and fails if an
`*-unsigned.apk` appears, so a missing secret can never become an unsigned
public release.

The upload keystore secret must never be regenerated. Android identifies an
app update by its signing key; a new key means existing users cannot install
over their current install and would lose all local data. Losing the keystore
is an incident, not a rotation.

There is also `.github/workflows/nightly.yml`, which re runs the unit tests
on `main` at 03:00 UTC on weekdays and stops immediately if nobody pushed in
the previous 24 hours.

Full setup instructions for branch protection, the `production` Environment
and the four secrets are in `docs/ci.md`.

---

## State of the project

What exists today is a Phase 0 skeleton: an app shell that launches, plus the MVI
foundations, the theme, the log redactor and their unit tests. Everything in the
table below that is not marked "Done" is **not built**, and the lock screen is a
stub that unlocks immediately with no biometric call behind it.

| Spec module | State | What actually exists |
| --- | --- | --- |
| A. Security and authentication gate | **Not built** | Dependencies declared: biometric, credentials, security-crypto, Room, SQLCipher. `allowBackup="false"` in the manifest and backup rules that exclude everything. A placeholder `ui/lock` screen and ViewModel exist, but they perform no KeyStore or biometric work and there is no database yet. |
| B. Multi tab terminal | **Not built** | Compose theme, navigation shell and the `MviViewModel` base exist. No terminal view, no tab bar, no key toolbar. |
| C. SSH, Mosh, Telnet, forwarding | **Not built** | Apache MINA SSHD, BouncyCastle and Commons Net are on the classpath, and R8 keep rules for them exist. No connection code. Mosh has no NDK or CMake setup at all. |
| D. SFTP and device file access | **Not built** | `sshd-sftp`, `documentfile` and WorkManager are declared. No file browser, no transfer queue. |
| E. Local logging and redaction | **Partial** | `core/logging/Redactor` is implemented and unit tested. No logger, no viewer, no export yet. |

| Supporting work | State | Notes |
| --- | --- | --- |
| Gradle build and version catalog | **Done** | AGP 8.7.3, Kotlin 2.0.21, JVM 17, configuration cache on. |
| ktlint and detekt configuration | **Done** | `.editorconfig` and `config/detekt/detekt.yml`. |
| R8 / ProGuard keep rules | **Done** | `app/proguard-rules.pro`, with a comment explaining every block. |
| CI workflow | **Done** | Chained jobs plus the `ci-ok` aggregate check. Cannot pass until the app compiles. |
| Release workflow with approval gate | **Done** | Tag driven, `production` Environment, keystore shredded afterwards. |
| Nightly workflow | **Done** | Weekday unit test run with an idle guard. |
| Dependabot | **Done** | Weekly grouped minor and patch bumps for Gradle and Actions. |
| PR template | **Done** | Includes the destructive migration and secrets checks. |
| App shell | **Done** | Hilt application, `MainActivity` with FLAG_SECURE and edge to edge, navigation over Lock and Hosts routes, Material3 theme with dynamic colour, string and backup resources. |
| MVI foundation | **Done** | `core/mvi/Mvi.kt` with the state, intent and effect contracts and the `MviViewModel` base. |
| Log redactor | **Done** | `core/logging/Redactor.kt`, the sanitiser every log line must pass through. |
| Room schemas (`app/schemas`) | **Not created** | The export path is wired up in `app/build.gradle.kts`; the directory appears when the first `@Database` is compiled. |
| Unit tests | **Partial** | JVM tests cover the MVI base, the redactor and the placeholder lock ViewModel. Nothing else has tests because nothing else exists. |
| Instrumented tests | **None** | Also note the runner is the plain `AndroidJUnitRunner`, not a Hilt aware one, and CI runs no emulator job. |

---

## Documentation

| File | Contents |
| --- | --- |
| `docs/architecture.md` | Layering, package layout, MVI flow, threading, security model. |
| `docs/migrations.md` | Room migration policy and how to write and test one. |
| `docs/qa/test_plan.md` | Manual QA playbook for a non engineer. |
| `docs/ci.md` | Workflow reference and the one time GitHub setup. |

Because almost nothing is implemented, those documents describe the intended
design. Each of them marks planned material as planned. If you find a
statement that claims something works, and it does not, that is a bug in the
document and should be fixed in the same PR that discovers it.
