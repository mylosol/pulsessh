# PulseSSH roadmap

Delivery order agreed with the project owner. A vertical slice comes first so there is a runnable app early,
then breadth. Each phase is a series of pull requests, each merged only when `ci-ok` is green.

## Phase 0: skeleton (done)

Gradle build, version catalog, CI and release workflows, Hilt application, Compose theme, navigation shell,
MVI base class, log redactor, placeholder lock and hosts screens, JVM unit tests for the base pieces.

## Phase 1: the vertical slice

1. **Module A, security gate.** AES 256 GCM master key in the Android KeyStore, StrongBox when the device
   offers it, BiometricPrompt Class 3 and CredentialManager passkey unlock, automatic lock on background and
   on screen off, derivation of the SQLCipher passphrase. Unit tests use a fake KeyStore façade.
2. **Data layer.** Room with SQLCipher for hosts, keys, snippets and log entries. Schema version 1 exported
   and committed. Repositories and domain models with use cases.
3. **Host management.** Compose host list, add and edit dialogs, Ed25519 and RSA 4096 key generation UI.
4. **SSH and terminal.** Apache MINA SSHD connection pool with public key, password, keyboard interactive and
   agent forwarding. Compose terminal view handling ANSI, xterm 256 colour, VT100 and VT220 control codes,
   plus the supplementary key toolbar and the multi tab session bar.

Exit criteria: connect to a real server, run commands, see correct colours, switch between two live tabs,
and have the database locked whenever the app is backgrounded.

## Phase 2: files and tunnels

SFTP dual pane browser with Storage Access Framework integration, transfer queue with progress, resume and
chmod. Local, remote and dynamic SOCKS forwarding with a tunnel manager screen.

## Phase 3: snippets and logs

Snippet drawer with template variable expansion, backed by a well tested parser. Session log viewer showing
handshake steps and tunnel state, everything passing through the redactor, exportable through the share sheet.

## Phase 4: Mosh and Telnet

CMake NDK wrapper around libmosh speaking the state synchronisation protocol over UDP, with roaming across a
Wi-Fi to mobile data switch. JNI bridge with tests. Telnet through Commons Net.

## Phase 5: release readiness

Version derivation from the git tag, production Environment with a required reviewer, signed AAB and APK,
accessibility pass on every screen, and a full run of `docs/qa/test_plan.md` by a human.
