# CI and releases

Three workflows live in `.github/workflows`. This page explains what each one
does, how to configure GitHub so they behave, and how to cut a release.

## The three workflows

### `ci.yml` — every pull request, and every push to main

Runs four jobs in a chain so the cheap ones fail first:

1. **static** — `ktlintCheck` and `detekt`. Usually a couple of minutes.
2. **unit** — `testDebugUnitTest`. Only starts if `static` passed.
3. **assemble** — `assembleDebug`, and uploads the debug APK as an artifact
   called `pulsessh-preview-apk` (kept 7 days). You can download it from the
   run page and install it on a phone to try the branch.
4. **ci-ok** — does no work. It looks at the other three and fails if any of
   them failed, was cancelled, or was skipped. This is the check to require
   in branch protection.

If a test fails, the HTML and XML test reports are uploaded automatically, so
you can read the failure without rerunning anything.

Changes that only touch `**.md`, `docs/**`, `LICENSE`, or `.gitignore` skip
the workflow entirely — no Android build, no minutes spent.

Only `main` writes to the shared Gradle cache; branches read it. That keeps
the cache from ballooning with per branch entries.

### `release.yml` — production releases

**Merging to main never releases anything.** A release happens only when a
tag matching `v*.*.*` is pushed, or when you run the workflow manually and
type an existing tag.

- **verify** re-runs ktlint, detekt and the unit tests against the tagged
  commit. No artifacts, it is purely a gate.
- **release** waits for approval in the `production` Environment, decodes the
  upload keystore into a temp directory outside the workspace, runs
  `bundleRelease` and `assembleRelease`, attaches the signed APK and AAB to
  the GitHub Release, and then shreds the keystore file whether the build
  succeeded, failed, or was cancelled.

**Never regenerate the keystore secret.** Android ties app updates to the
signing key. A new key means existing users cannot install the update over
their current install — they would have to uninstall first and lose their
hosts and keys. Keep an offline backup of the keystore.

### `nightly.yml` — optional safety net

At 03:00 UTC Monday to Friday it re-runs the unit tests on `main`. A guard
job first checks `git log --since='24 hours ago'`; if nobody pushed, the run
stops immediately and costs nothing. Catches time dependent or flaky tests
that a PR run happened to pass.

## GitHub setup, once

### 1. Branch protection

Settings > Branches > Add branch ruleset (or classic branch protection) for
`main`:

- Require a pull request before merging.
- Require status checks to pass, and select **`ci-ok`** — that one name only.
  Do not add `static`, `unit` or `assemble` individually; `ci-ok` already
  covers them, and listing them separately breaks documentation only PRs.

Caveat worth knowing: because `ci.yml` uses `paths-ignore`, a PR that touches
only docs never starts the workflow, so `ci-ok` never reports and the PR can
sit as "Expected — waiting for status". If you hit that, either merge such PRs
with admin rights, or move `main`'s protection to a ruleset and rely on
"Require status checks" only when the workflow runs. GitHub's ruleset UI
handles skipped workflows better than classic protection does.

### 2. The `production` Environment (the approval gate)

Settings > Environments > **New environment** > name it exactly `production`.

Inside it, tick **Required reviewers** and add yourself (or the release
owners). This is what makes a release pause for a human. Optionally restrict
deployment branches to tags only.

### 3. Secrets

Settings > Secrets and variables > Actions > **New repository secret**. Add
four:

| Secret | Value |
| --- | --- |
| `PULSESSH_KEYSTORE_BASE64` | the upload keystore, base64 encoded |
| `PULSESSH_KEYSTORE_PASSWORD` | keystore password |
| `PULSESSH_KEY_ALIAS` | key alias inside the keystore |
| `PULSESSH_KEY_PASSWORD` | password for that key |

To produce the base64 blob from your keystore:

```bash
base64 -w0 pulsessh-upload.jks > keystore.b64   # macOS: base64 -i ... -o ...
```

Paste the contents of `keystore.b64`, then delete that file. If you prefer,
add the same four secrets under Settings > Environments > production >
Environment secrets instead — they are then only readable by the approved
release job, which is slightly safer.

`app/build.gradle.kts` reads `PULSESSH_KEYSTORE_PATH` (set by the workflow when
it decodes the keystore) plus the three password/alias variables straight from
the environment, and only creates the release signing config when all four are
present — locally, where none are set, the release variant just builds
unsigned. Because of that fallback the workflow refuses to continue if any of
the four is empty, and fails if an `*-unsigned.apk` is produced, so a missing
secret can never turn into an unsigned public release.

## Cutting a release

```bash
git checkout main
git pull
# make sure the versionName / versionCode in app/build.gradle.kts are updated
git tag -a v1.4.0 -m "PulseSSH 1.4.0"
git push origin v1.4.0
```

Then open the Actions tab. The `verify` job runs first; when it passes, the
`release` job shows "Waiting for review". Approve it, and the signed APK and
AAB appear on the GitHub Release for that tag.

To re-run a release for a tag that already exists (for example after fixing a
secret), use Actions > Release > **Run workflow** and type the tag.

To undo a mistaken tag before it is released:

```bash
git tag -d v1.4.0
git push origin :refs/tags/v1.4.0
```
