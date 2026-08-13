## What changed

<!-- One paragraph, plain language. What does this PR do to the app? -->

## Why

<!-- The problem, bug, or spec requirement behind the change. Link the issue. -->

## Spec module

<!-- Tick the module(s) from the project spec that this PR touches. -->

- [ ] Module A
- [ ] Module B
- [ ] Module C
- [ ] Module D
- [ ] Module E
- [ ] None (tooling, CI, docs)

## Tests added

<!-- Which unit tests were added or changed, and what they actually prove.
     If no tests were added, say why. -->

## Manual QA notes

<!-- What you exercised on a device or emulator, and on which Android version.
     For example: connected to a host with a password, reconnected after the
     screen locked, imported a key, backgrounded the app mid session. -->

- Device / API level:
- Steps performed:
- Result:

## Checklist

- [ ] No destructive Room migration (no `fallbackToDestructiveMigration`, no
      dropped or recreated table that holds user data; a schema change ships
      with a real migration and a migration test)
- [ ] No secrets added to the repo (no keystore, no password, no token, no
      `local.properties`; secrets go in GitHub Actions secrets)
- [ ] CI green (the `ci-ok` check passes)
- [ ] Public behaviour changes are reflected in the docs
- [ ] Self reviewed the diff for stray debug logging and leftover TODOs
