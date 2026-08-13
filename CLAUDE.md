# PulseSSH working agreement

This file is read automatically by Claude Code. Follow it for every task in this repository.

## What this project is

PulseSSH is a privacy focused Android client for SSH, Mosh, SFTP and Telnet. Kotlin, Jetpack Compose
Material3, Clean Architecture with MVI, Hilt, Room encrypted with SQLCipher, keys held in the Android
KeyStore, unlocking gated by BiometricPrompt or a passkey. Zero telemetry. The full specification lives
in `SSH Spec.md` in the parent workspace folder, and the phase order is recorded in `docs/roadmap.md`.

## Non negotiable rules

1. Never commit or push directly to `main`. Every change goes on a feature branch and merges through a
   pull request.
2. A branch is done when the `ci-ok` check is green. That check is the merge gate.
3. Never add `fallbackToDestructiveMigration` on any build type. Every schema change ships an explicit
   `Migration(x, y)` object, an exported schema JSON committed under `app/schemas`, and a migration test.
   Reject any change that would wipe a user database on upgrade.
4. Never regenerate or replace the release signing keystore. Losing it breaks updates for every existing
   install. The release workflow reads it from repository secrets and shreds the decoded copy afterwards.
5. Merging to `main` must never publish a release. Only a tag matching `v*.*.*` triggers the gated
   release workflow, and that workflow waits on the `production` GitHub Environment approval.
6. No secret, token, keystore or private key is ever committed. `local.properties` and `*.jks` stay ignored.
7. Nothing may reference `BuildConfig`: `android.defaults.buildfeatures.buildconfig=false` is set.
8. All user visible text lives in `res/values/strings.xml`, never inline in Kotlin.
9. Anything written to a log passes through `core/logging/Redactor` first.

## Architecture rules

* Layers: `core` (crypto, KeyStore, logging, MVI base, DI), `data` (Room, repositories, connection pools),
  `domain` (models and use cases, no Android imports at all), `ui` (Compose screens and ViewModels).
* Every ViewModel extends `MviViewModel<S, I, E>` from `core/mvi/Mvi.kt`. State is immutable, intents are
  the only input, effects are one shot.
* The domain layer must stay unit testable on the JVM. No Robolectric in `src/test`.
* Terminal output does not flow through `StateFlow` copies per byte. Use the buffered screen model
  described in `docs/architecture.md`.

## Testing expectations

Write JVM unit tests alongside every ViewModel, use case, crypto helper and parser. The command template
parser, the redactor and the migration set are the three places where regressions hurt most, so cover them
heavily. CI runs `ktlintCheck`, `detekt`, `testDebugUnitTest` and `assembleDebug` in that order, cheapest
first, so a lint failure never pays for a full build.

## Pipeline cost rules

Keep GitHub Action minutes low. Preserve the path filters that skip Android jobs for documentation only
changes, the concurrency group that cancels superseded runs, the Gradle cache, and the job chain that puts
static analysis before compilation. Do not add an emulator job to the pull request path without saying so
explicitly and explaining the cost.

## Style

ktlint official style, 4 space indent, 120 character lines, no wildcard imports, KDoc on public classes and
functions. Run `./gradlew ktlintFormat` before pushing. Compose functions are PascalCase and that is fine.

## Known issues to fix early

* `app/build.gradle.kts` registers `app/schemas` as an androidTest asset source while `room-testing` sits on
  the JVM test classpath. Pick one home for migration tests and make the two consistent.
* The `ci-ok` gate never reports on a documentation only pull request because the workflow is skipped by the
  path filter. Use a repository ruleset, which tolerates skipped workflows, or add an always running stub job.
* `versionCode` and `versionName` are hardcoded in `app/build.gradle.kts`. Derive them from the tag before
  the first real release, otherwise a second release collides on `versionCode`.
* The lock screen is a stub that unlocks immediately. Module A replaces it.
