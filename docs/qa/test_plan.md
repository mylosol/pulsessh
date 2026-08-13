# PulseSSH manual QA playbook

This is a checklist for testing PulseSSH by hand on a real phone. It is
written so that someone who is not a programmer can follow it.

## Read this before you start

**None of these scenarios can be run today.** At the time of writing the
repository contains the build setup only. There is no app to install. Every
scenario below is marked with the module it depends on, so this document is
usable as a plan now and as a checklist later, one scenario at a time, as each
module lands.

The modules referred to are:

| Module | What it covers |
| --- | --- |
| A | Security: master key, biometric lock, encrypted storage |
| B | Multi tab terminal screen and the on screen key toolbar |
| C | SSH, Mosh, Telnet connections, port forwarding, snippets |
| D | SFTP file browsing and transfers, device storage access |
| E | Log viewer, redaction and export |

Check `README.md` for what has actually been built before you start a run.

## Words this document uses

| Word | What it means here |
| --- | --- |
| **Host** | A saved entry for one server: its address, port, username and how to log in. |
| **SSH** | The standard way to get a text command line on a remote computer, encrypted. |
| **Mosh** | An alternative to SSH designed for bad connections. It survives the phone changing network. |
| **Telnet** | A very old, unencrypted way of getting a remote command line. Still used for switches and routers. |
| **SFTP** | Copying files to and from a server over an SSH connection. |
| **Terminal** | The black screen where you type commands and read the server's replies. |
| **Session** | One live connection to one server. |
| **Tab** | One session shown at the top of the screen, like browser tabs. |
| **Key** | A pair of files that log you in instead of a password. The public half goes on the server, the private half stays on the phone. |
| **Ed25519** | A modern type of key. Short and fast. |
| **Handshake** | The first few seconds of a connection, where the two sides agree how to encrypt and check each other's identity. |
| **Port forwarding / tunnel** | Making a network address on one machine reachable from another through the SSH connection. |
| **SOCKS proxy** | A setting that makes an app send all its traffic through a tunnel. |
| **SAF** | Storage Access Framework. Android's standard file picker, the one that shows "Recent", "Downloads" and your SD card. |
| **Redaction** | Removing secrets from text before it is shown or saved. |

## What you need for a full run

Get these ready before you begin. Most scenarios need at least some of them.

1. **A test Android phone.** Ideally two, one recent (Android 14 or 15) and
   one older (Android 8.0 is the oldest version the app supports). A phone
   with a working fingerprint reader is essential.
2. **A test server you control.** A cheap virtual machine running Linux is
   fine. You need:
   - SSH access on port 22;
   - a normal user account you can add keys to;
   - the `mosh-server` package installed, for the Mosh scenarios;
   - a Telnet server, for the Telnet scenario (a small router or an old
     switch also works; do not enable Telnet on anything important);
   - a small web page served on the server itself, for the port forwarding
     checks, for example `python3 -m http.server 8000` run on the server.
3. **A second computer on the same Wi-Fi**, for the remote forwarding check.
4. **A mobile data plan on the test phone** that actually works with Wi-Fi
   turned off, for the Mosh roaming test.
5. **Never use a production server or a real password.** Create throwaway
   accounts. Some of these scenarios deliberately look for secrets in log
   files.

## How to record results

For each scenario write down: pass or fail, the phone and Android version,
the date, and the app version (Settings, or the version shown on the app's
about screen). If it fails, follow the defect reporting section at the end.

Pass criteria are written as things you can see. If a criterion says "the tab
label shows `web-01`", then either it does or it does not. If you find
yourself deciding whether something "feels right", the criterion is badly
written and should be fixed.

---

# Scenarios

## 1. First launch and master key creation

> **Not testable until Module A is implemented.**

**What you are testing.** That a brand new install sets up its encrypted
storage and asks you to protect it, before it will hold any data.

**What you need.**
- A phone with a fingerprint already enrolled in Android settings.
- The app not previously installed, or its data cleared (Settings > Apps >
  PulseSSH > Storage > Clear storage).

**Steps.**
1. Install the app.
2. Open it for the first time.
3. Read whatever the first screen says. Follow it to completion.
4. Close the app fully (swipe it away from recent apps).
5. Open it again.

**Pass criteria.**
- On first open, the app shows a setup or welcome screen. It does not go
  straight to an empty host list with no protection.
- The setup explains, in plain words, that data will be encrypted and
  protected by your fingerprint or screen lock.
- Setup completes without any error message.
- On the second open, the app asks you to unlock. It does not go straight in.
- No screen at any point displays a password, a key, or a long string of
  random characters presented as something you should write down.

## 2. Unlocking with a fingerprint

> **Not testable until Module A is implemented.**

**What you are testing.** That the correct fingerprint opens the app.

**What you need.** Scenario 1 completed. A fingerprint enrolled.

**Steps.**
1. Close the app fully.
2. Open it.
3. When the fingerprint prompt appears, use the finger you enrolled.

**Pass criteria.**
- The prompt appears within two seconds of the app opening.
- The prompt is Android's own system prompt, not a custom looking one drawn
  by the app.
- After a successful fingerprint, the app opens to the host list.
- The host list shows any hosts you saved earlier. Nothing is missing.

## 3. Unlocking fails with the wrong fingerprint

> **Not testable until Module A is implemented.**

**What you are testing.** That a finger the phone does not recognise does not
get in, and that the app says so clearly rather than crashing or silently
opening.

**What you need.** Scenario 2 passing. A finger that is **not** enrolled, for
example your little finger.

**Steps.**
1. Close the app fully and open it.
2. At the fingerprint prompt, use the finger that is not enrolled.
3. Repeat with the wrong finger until Android stops accepting attempts
   (usually five tries).
4. Note what the app shows.
5. Cancel the prompt with the back gesture or the cancel button, without
   using any finger.
6. Open the app again and unlock properly with the right finger.

**Pass criteria.**
- Each wrong attempt shows a "not recognised" message and the app stays
  locked.
- No host, key, log or terminal content is visible at any point while locked.
- After too many failures, the app either offers the phone's PIN, pattern or
  password as an alternative, or shows a clear message saying to try again
  later. It does not open.
- Cancelling the prompt leaves the app locked, or closes it. It does not open
  the host list.
- The app does not crash or freeze at any step.
- After step 6 the app opens normally and all data is still there.

## 4. The app locks when you leave it

> **Not testable until Module A is implemented.**

**What you are testing.** That putting the app in the background or turning
the screen off relocks it, so somebody picking up the phone cannot read your
servers.

**What you need.** Scenario 2 passing. At least one saved host.

**Steps.**
1. Unlock the app and stay on the host list.
2. Press the home button.
3. Open the recent apps switcher and look at the PulseSSH thumbnail.
4. Return to PulseSSH.
5. Unlock it again.
6. Now unlock the app, press the power button to turn the screen off, wait
   ten seconds, turn the screen on and unlock the phone.
7. Look at PulseSSH.

**Pass criteria.**
- In step 3 the thumbnail in recent apps is blank, grey or shows the app icon.
  It does **not** show your host names or any terminal text. (This is the
  `FLAG_SECURE` behaviour.)
- In step 4 the app shows the lock screen, not the host list.
- In step 7 the app shows the lock screen.
- After unlocking again, all data is still present and correct.
- Try taking a screenshot while the app is open. Android should refuse and
  show a message such as "Can't take screenshot due to app policy".

## 5. Adding a host that uses a password

> **Not testable until Modules A and C are implemented.**

**What you are testing.** That you can save a server and log in with a
username and password.

**What you need.**
- Your test server's address, for example `192.168.1.50` or
  `test.example.com`.
- A username and password on that server.
- The server must allow password logins. In `/etc/ssh/sshd_config` that is
  `PasswordAuthentication yes`.

**Steps.**
1. Unlock the app.
2. Tap the button to add a host. It is usually a plus sign.
3. Fill in: a friendly name such as `test-box`, the address, port `22`, the
   username, and choose password as the login method.
4. Enter the password.
5. Save.
6. Look at the host list.
7. Tap the host to connect.
8. When asked to accept the server's fingerprint (a line of letters and
   numbers identifying the server), read what it says and accept.

**Pass criteria.**
- The password field hides what you type, showing dots or asterisks.
- After saving, the host appears in the list with the friendly name
  `test-box`.
- The host list does not display the password anywhere, in any form.
- On first connection the app asks you to confirm the server's identity, and
  explains in plain words what that means.
- The connection succeeds and you see a command prompt from the server.
- Disconnect and connect again. The second time it does **not** ask for the
  password again, and does **not** ask about the server's identity again.

## 6. Adding a host that uses a generated key

> **Not testable until Modules A and C are implemented.**

**What you are testing.** That the app can create an Ed25519 key, that you
can get the public half onto the server, and that logging in with it works.

**What you need.** Your test server, and a way to paste text into a file on
it (another SSH session from a laptop is easiest).

**Steps.**
1. Unlock the app and find the key management screen.
2. Choose to generate a new key. Pick Ed25519. Give it a name such as
   `phone-key`.
3. If it offers to protect the key with a passphrase, set one, and write it
   down.
4. When the key is created, find the option to copy or share the **public**
   key.
5. On your laptop, connect to the test server and paste that public key as a
   new line in `~/.ssh/authorized_keys` for your test user. Make sure the file
   permissions are right: `chmod 600 ~/.ssh/authorized_keys`.
6. Back in the app, add a new host pointing at the same server, but choose
   `phone-key` as the login method instead of a password.
7. Connect.

**Pass criteria.**
- The generated public key is one line, and starts with `ssh-ed25519 `.
- The app offers to copy or share the public key. It offers **no** way to
  copy, share or display the private key in normal use. If an export exists
  at all, it is clearly marked as dangerous and asks for confirmation.
- The connection in step 7 succeeds without asking for the account password.
- If you set a passphrase in step 3, the app asks for it (or for your
  fingerprint) when connecting, and connects after you give it.
- On the server, run `sudo journalctl -u ssh | tail -20` or check
  `/var/log/auth.log`. The successful login line mentions `ED25519`.

## 7. Connecting over SSH and running commands

> **Not testable until Modules B and C are implemented.**

**What you are testing.** That the terminal actually works: text goes in,
output comes back, and it looks right.

**What you need.** A host that connects, from scenario 5 or 6.

**Steps.**
1. Connect to the host.
2. Type `whoami` and press enter.
3. Type `pwd` and press enter.
4. Type `ls --color=always /etc` and press enter.
5. Type `top` and press enter. Watch it for ten seconds. Press `q` to quit.
6. Rotate the phone to landscape and back to portrait.
7. Type `echo "hello world"` and press enter.
8. Type `exit` and press enter.

**Pass criteria.**
- Step 2 prints your username.
- Step 3 prints your home directory, for example `/home/tester`.
- Step 4 prints a file listing **in colour**, with directories a different
  colour from files.
- Step 5 shows the `top` display filling the screen, updating roughly once a
  second, with the columns lined up straight. Pressing `q` returns you to the
  prompt with no leftover characters on screen.
- After rotating, the text reflows to the new width, the session is still
  connected, and you can still type. It does not disconnect.
- Step 7 prints `hello world`.
- Step 8 closes the session and the app says the session ended. It does not
  crash or leave a dead tab that looks alive.

## 8. The supplementary key toolbar

> **Not testable until Module B is implemented.**

**What you are testing.** The row of extra keys the app adds above the normal
keyboard, because Android keyboards have no Ctrl, Esc, Tab or arrow keys.

**What you need.** A live SSH session. `nano` installed on the server
(`sudo apt install nano`).

**Steps.**
1. Connect and find the extra key row.
2. Type `sleep 60` and press enter. Then press **CTRL** followed by **C**.
3. Press the **up arrow** key. Then press the up arrow again.
4. Type `ls /et` then press **TAB**.
5. Type `nano /tmp/qa-test.txt` and press enter. Type some text. Press
   **CTRL** then **X**, then `y`, then enter.
6. Back at the prompt, use the toolbar to type a pipe character: `ls |` then
   type ` wc -l` and press enter.
7. Use the toolbar to type a tilde: `ls ~` and press enter.
8. Press **ESC** somewhere it does something, for example inside `nano`'s
   search box.

**Pass criteria.**
- In step 2 the `sleep` stops immediately and `^C` appears on screen.
- In step 3 the previous command appears at the prompt. Pressing it again
  shows the one before that.
- In step 4 the text completes to `/etc` (or the server beeps or lists
  options, depending on the shell). Something visibly happens.
- In step 5 `nano` opens, accepts typing, and CTRL+X saves and exits. Run
  `cat /tmp/qa-test.txt` afterwards and your text is there.
- In step 6 a number is printed.
- In step 7 your home directory contents are listed.
- The toolbar stays visible and reachable while the normal keyboard is open.
  It is not hidden behind the keyboard.
- CTRL and ALT behave as modifiers: pressing CTRL then a letter sends a
  control character, and the CTRL key visibly shows whether it is active.

## 9. Two live sessions and switching tabs

> **Not testable until Modules B and C are implemented.**

**What you are testing.** That two connections can be open at once, that
switching between them does not disconnect either, and that each keeps its own
screen content.

**What you need.** Two hosts saved, or the same host added twice with
different names, for example `box-a` and `box-b`.

**Steps.**
1. Connect to the first host. Type `echo AAAA` and press enter.
2. Without disconnecting, open a second session to the other host.
3. In the second session, type `echo BBBB` and press enter.
4. In the second session, start something long running: `ping -c 100 8.8.8.8`.
5. Switch back to the first tab.
6. Type `echo CCCC` in the first tab and press enter.
7. Switch back to the second tab.
8. Switch between the two tabs five times quickly.
9. Close the second tab.

**Pass criteria.**
- After step 2 there are two tabs at the top, each labelled with its host
  name, and the active one is visibly marked.
- In step 5 the first tab still shows `AAAA` on screen and is still connected.
- In step 6 the command runs immediately, with no reconnection delay and no
  fingerprint prompt.
- In step 7 the ping is **still running** and has continued counting up while
  you were away. It did not restart from 1 and did not stop.
- After step 8 both tabs are still connected and neither shows garbled text.
- In step 9 the second tab closes, the first stays connected, and the first
  tab's content is unchanged.
- On the server, run `who`. The number of logged in sessions matches the
  number of tabs you have open.

## 10. Connecting over Mosh

> **Not testable until Module C is implemented.** Mosh needs native code that
> does not exist in the repository at all. Expect this to be the last thing
> ready.

**What you are testing.** That a Mosh connection can be made at all.

**What you need.**
- `mosh-server` installed on the test server (`sudo apt install mosh`).
- UDP ports 60000 to 61000 open in the server's firewall. On a cloud server
  this means a rule in the provider's console as well as `ufw`.

**Steps.**
1. Add or edit a host and choose Mosh as the connection type.
2. Connect.
3. Type `whoami` and press enter.
4. Type `top` and press enter, watch it update, press `q`.

**Pass criteria.**
- The connection succeeds and you reach a prompt.
- On the server, `ps aux | grep mosh-server` shows a running `mosh-server`
  process for your user.
- Typing feels immediate: characters appear as you type them, without waiting
  for the server.
- `top` renders correctly and updates.

## 11. Mosh survives changing network

> **Not testable until Module C is implemented.**

**What you are testing.** The main reason Mosh exists: the session must stay
alive when the phone moves from Wi-Fi to mobile data.

**What you need.**
- Scenario 10 passing.
- Working mobile data on the test phone, and a server reachable from the
  internet (not only from your home network).

**Steps.**
1. On Wi-Fi, connect over Mosh.
2. Run `while true; do date; sleep 1; done`. The screen now prints the time
   every second.
3. Watch it for ten seconds.
4. Turn Wi-Fi **off** in the phone's quick settings, so the phone falls back
   to mobile data.
5. Watch the screen for 30 seconds.
6. Type `echo STILL-HERE` and press enter.
7. Turn Wi-Fi back on. Wait 30 seconds. Type `echo BACK-ON-WIFI` and press
   enter.
8. Press CTRL+C to stop the loop.

**Pass criteria.**
- After step 4 the clock output pauses briefly, then continues on its own. It
  may show a "connecting" or similar indicator during the gap.
- The session does **not** disconnect, and you are **not** asked to log in
  again.
- Step 6 prints `STILL-HERE`.
- Step 7 prints `BACK-ON-WIFI`.
- The scrollback still contains the output from before the network change.
- On the server, `ps aux | grep mosh-server` shows the **same** process ID as
  before the switch. A new process ID means the session was recreated, which
  is a fail.
- For comparison, repeat steps 1 to 5 with an ordinary SSH connection. That
  one is expected to drop. If SSH survives too, the test is not doing what you
  think and the network did not actually change.

## 12. Connecting over Telnet

> **Not testable until Module C is implemented.**

**What you are testing.** That plain Telnet works, and that the app warns you
it is not encrypted.

**What you need.** A Telnet server. On a throwaway test machine only:
`sudo apt install telnetd`. Never enable Telnet on anything that matters.

**Steps.**
1. Add a host, choose Telnet, port `23`.
2. Connect.
3. Type the username and password at the prompts the server gives.
4. Run `whoami` and `ls`.
5. Disconnect.

**Pass criteria.**
- The app shows a visible warning, at some point before or during connecting,
  that Telnet sends everything including your password unencrypted.
- The login prompts come from the server and you can type into them.
- `whoami` prints your username.
- `ls` prints a listing.
- Disconnecting closes the tab cleanly.

## 13. Local port forwarding

> **Not testable until Module C is implemented.**

**What you are testing.** Local forwarding, written `-L` in normal SSH. It
makes something on the server reachable at an address on your phone.

**What you need.**
- A working SSH host.
- A web page running on the server. Log into the server and run
  `python3 -m http.server 8000`. Leave it running.
- A web browser on the phone.

**Steps.**
1. Confirm first that the phone **cannot** reach the page directly. In the
   phone's browser go to `http://<server address>:8000`. If it loads, block
   port 8000 in the server's firewall (`sudo ufw deny 8000`) and try again. It
   must fail before you continue, otherwise the test proves nothing.
2. In PulseSSH, connect to the host.
3. Add a local forward: local port `8080`, remote host `localhost`, remote
   port `8000`.
4. Start the forward.
5. In the phone's browser go to `http://127.0.0.1:8080`.

**Pass criteria.**
- Step 1 fails to load. This is required.
- Step 5 loads the directory listing served by `python3 -m http.server`.
- The app shows the forward in a list, marked as active.
- Stop the forward in the app, then reload the browser page. It now fails to
  load.
- Start it again, reload, it loads again.

## 14. Remote port forwarding

> **Not testable until Module C is implemented.**

**What you are testing.** Remote forwarding, written `-R`. It makes something
reachable from your phone available to people on the server.

**What you need.**
- A working SSH host.
- A second computer on the same Wi-Fi as the phone, running a web page:
  `python3 -m http.server 9000`. Note its address, for example
  `192.168.1.20`.
- The server's SSH config should have `GatewayPorts no` (the default). The
  forwarded port will then only be reachable from the server itself, which is
  fine for this test.

**Steps.**
1. From the server, check the page is **not** reachable:
   `curl -m 5 http://localhost:9000`. It must fail.
2. In PulseSSH, connect to the host.
3. Add a remote forward: remote port `9000`, target host `192.168.1.20`,
   target port `9000`.
4. Start the forward.
5. On the server, run `curl http://localhost:9000` again.

**Pass criteria.**
- Step 1 fails with "connection refused" or a timeout.
- Step 5 prints the HTML of the directory listing from your second computer.
- The second computer's `python3 -m http.server` window logs a request.
- Stop the forward, run the `curl` again on the server, and it fails.

## 15. Dynamic SOCKS forwarding

> **Not testable until Module C is implemented.**

**What you are testing.** Dynamic forwarding, written `-D`. It turns the SSH
connection into a proxy, so traffic from the phone comes out of the server.

**What you need.**
- A working SSH host on a machine with a different public IP address from
  your phone's network. A cloud server is ideal.
- An app on the phone that can be told to use a SOCKS proxy. Firefox for
  Android can, under Settings. Some terminal tools can too.

**Steps.**
1. In the phone's browser, visit `https://ifconfig.me`. Write down the IP
   address it shows. That is your phone's normal public address.
2. In PulseSSH, connect to the host.
3. Add a dynamic forward on local port `1080`. Start it.
4. Configure Firefox for Android to use a SOCKS v5 proxy at `127.0.0.1` port
   `1080`.
5. In Firefox, visit `https://ifconfig.me` again.
6. Stop the forward in PulseSSH, and reload the page in Firefox.

**Pass criteria.**
- The address in step 5 is **different** from step 1, and matches the test
  server's public address. You can confirm the server's address by running
  `curl ifconfig.me` on the server.
- In step 6, with the forward stopped but the browser still set to use the
  proxy, the page fails to load.
- The app lists the dynamic forward as active while it is running.

## 16. Browsing files over SFTP

> **Not testable until Module D is implemented.**

**What you are testing.** That you can see and move around the folders on the
server.

**What you need.** A working SSH host. On the server, create some test
content:
```
mkdir -p ~/qa/sub
echo hello > ~/qa/one.txt
head -c 5M /dev/urandom > ~/qa/big.bin
```

**Steps.**
1. Open the file manager for the host.
2. Navigate to your home directory, then into `qa`.
3. Look at the listing.
4. Go into `sub`, then back up one level.
5. Pull to refresh, or use the refresh control.
6. Create a new folder called `qa-new`.
7. Rename `one.txt` to `renamed.txt`.
8. Delete `qa-new`.

**Pass criteria.**
- The listing shows `one.txt`, `big.bin` and `sub`.
- `big.bin` shows a size of about 5 MB, not 5,000,000 bytes shown raw and not
  a blank.
- Folders are visually distinct from files and sort together.
- Going into `sub` shows an empty folder, and going back returns you to `qa`
  in the same scroll position.
- The new folder appears in the listing immediately after creating it, and
  `ls ~/qa` on the server confirms it exists.
- After the rename, the app shows `renamed.txt` and `ls ~/qa` on the server
  agrees.
- After the delete, `qa-new` is gone in both places.

## 17. Uploading a file

> **Not testable until Module D is implemented.**

**What you are testing.** Sending a file from the phone to the server.

**What you need.** Scenario 16 working. A file on the phone, for example a
photo, and a larger file of about 20 MB. You can make one on the phone by
downloading any large file in the browser.

**Steps.**
1. In the file manager, go to `~/qa` on the server.
2. Choose to upload, and pick the small photo from the phone.
3. Watch the progress.
4. Upload the 20 MB file.
5. While it is uploading, watch the progress indicator.
6. When it finishes, on the server run `ls -l ~/qa` and
   `md5sum ~/qa/<the file>`.
7. Compute the same checksum for the original on the phone if you have a tool
   for it, or compare file sizes exactly.

**Pass criteria.**
- A progress indicator appears and moves. It is not stuck at 0 percent and
  does not jump straight to 100.
- The progress shows something meaningful: a percentage, or bytes done out of
  total.
- The file appears in the remote listing when the transfer finishes.
- `ls -l` on the server shows exactly the same byte count as the original.
- The checksum matches, if you can compute both.
- Uploading a file with a space and a non English character in its name, for
  example `test file ü.txt`, also works and keeps the name intact.

## 18. Downloading a file

> **Not testable until Module D is implemented.**

**What you are testing.** Bringing a file from the server to the phone.

**What you need.** Scenario 16, and the `big.bin` file created there.

**Steps.**
1. In the file manager, go to `~/qa`.
2. Download `one.txt`.
3. Download `big.bin`.
4. Find both on the phone using Android's Files app.
5. Open `one.txt` on the phone.

**Pass criteria.**
- Both downloads complete with a visible progress indicator.
- The app tells you where the files were saved, or lets you choose.
- Both files are findable in the Android Files app in that location.
- `one.txt` opens and contains `hello`.
- `big.bin` on the phone is exactly the same size as on the server. Check the
  server's size with `ls -l ~/qa/big.bin`.

## 19. Resuming an interrupted transfer

> **Not testable until Module D is implemented.**

**What you are testing.** That a transfer broken by losing the network can
carry on rather than starting over.

**What you need.** A large file, at least 100 MB, so the transfer takes long
enough to interrupt. Create one on the server:
`head -c 200M /dev/urandom > ~/qa/huge.bin`.

**Steps.**
1. Start downloading `huge.bin`.
2. When the progress is somewhere between 20 and 50 percent, turn on
   aeroplane mode.
3. Watch what the app does for 30 seconds.
4. Turn aeroplane mode off and wait for the phone to reconnect.
5. Watch what the app does.
6. If it does not resume by itself, find and use the resume control.
7. Let it finish.
8. Compare `md5sum ~/qa/huge.bin` on the server with the downloaded file, or
   at minimum compare exact byte sizes.

**Pass criteria.**
- The app notices the interruption within 30 seconds and shows a clear
  message. It does not sit at the same percentage forever with no indication
  anything is wrong.
- The partly transferred data is kept. Progress does not reset to 0 percent.
- After the network returns, the transfer continues from roughly where it
  stopped, either automatically or after one tap.
- The finished file has exactly the same size as the original.
- The checksums match. This is the important one: a resume that stitches the
  file back together incorrectly produces the right size and the wrong
  contents.
- Repeat the whole scenario for an **upload** as well. Resume is often
  implemented for only one direction.

## 20. Changing file permissions

> **Not testable until Module D is implemented.**

**What you are testing.** The `chmod` feature, which controls who can read,
write and run a file.

**What you need.** Scenario 16, with `renamed.txt` in `~/qa`.

**Steps.**
1. On the server, run `chmod 644 ~/qa/renamed.txt` and then
   `ls -l ~/qa/renamed.txt`. Note the permissions shown, `-rw-r--r--`.
2. In the app, open the file's properties or permissions screen.
3. Note what it shows.
4. Change the permissions so the owner can execute the file as well, or type
   `755` if the app offers a numeric field.
5. Apply.
6. On the server, run `ls -l ~/qa/renamed.txt` again.

**Pass criteria.**
- In step 3 the app shows permissions matching what the server reported,
  either as `644`, or as tick boxes for read, write and execute across owner,
  group and others.
- After applying, the server shows `-rwxr-xr-x` (for 755).
- The app's own display refreshes to show the new permissions.
- Trying to change permissions on a file owned by another user, for example
  `/etc/passwd`, produces a clear error message from the server rather than
  appearing to succeed.

## 21. Saving to the Download folder or an SD card

> **Not testable until Module D is implemented.**

**What you are testing.** Android's standard file picker, so that files can
go somewhere you can find them later, including removable storage.

**What you need.** A phone with an SD card slot and a card in it, if
possible. If not, the Download folder alone still exercises most of it.

**Steps.**
1. In the app, start a download and choose the option to pick a destination
   folder.
2. The Android system picker should open. Use the menu at the top left to
   look at the available locations.
3. Choose the **Download** folder. Confirm.
4. Let the download finish.
5. Open the Android Files app and go to Download.
6. Repeat from step 1, this time choosing the SD card.
7. Also repeat, choosing a location, and check whether the app remembers it
   next time.

**Pass criteria.**
- The picker that opens is the Android system one, showing "Recent",
  "Downloads", the phone's internal storage and the SD card if present. It is
  not a file browser drawn by PulseSSH.
- The downloaded file is present in the Download folder, visible in the
  Android Files app, and openable.
- The same works for the SD card.
- Restart the app entirely and download again. The app still has permission
  to write to the folder you chose, and does not ask you to pick it again
  from scratch every time. (This is what a persisted permission means.)
- The file is readable by other apps, for example a text editor or an image
  viewer.

## 22. Using a snippet with a template variable

> **Not testable until Module C is implemented.**

**What you are testing.** Saved commands, and the placeholders inside them
that get filled in with details of the host you are connected to.

**What you need.** A live SSH session.

**Steps.**
1. Open the snippets drawer or screen.
2. Create a new snippet. Name it `whoami-here`. Set the command text to:
   ```
   echo "I am {{ user }} on {{ host.ip }}"
   ```
3. Save it.
4. Connect to a host, for example `test-box` at `192.168.1.50` as user
   `tester`.
5. Open the snippets drawer and select `whoami-here`.
6. Look at the terminal before pressing enter, if the app inserts rather than
   runs.
7. Run it.
8. Connect to a **different** host and run the same snippet there.

**Pass criteria.**
- The snippet is saved and appears in the list by name.
- In step 6 the text placed in the terminal reads
  `echo "I am tester on 192.168.1.50"`. The `{{ ... }}` placeholders are gone
  and the real values are in their place.
- Running it prints `I am tester on 192.168.1.50`.
- In step 8 the same snippet produces the other host's username and address.
  The values are not stuck from the first host.
- A snippet containing a placeholder the app does not recognise, for example
  `{{ nonsense }}`, does not crash the app. It either leaves the text alone or
  shows a clear message.
- Snippets survive closing and reopening the app.

## 23. The log viewer shows a connection handshake

> **Not testable until Module E is implemented.**

**What you are testing.** That the log screen records what happened during a
connection, in enough detail to diagnose a problem.

**What you need.** A working SSH host.

**Steps.**
1. Open the log viewer and clear it, if there is a clear option.
2. Connect to a host over SSH.
3. Return to the log viewer.
4. Read the entries from top to bottom.
5. Now try to connect to a host that does not exist, for example address
   `10.255.255.1`, and read the log again.
6. Try to connect with a deliberately wrong password and read the log again.

**Pass criteria.**
- After step 2 the log contains entries covering, at minimum: the connection
  being opened, the server's identity being checked, which encryption was
  agreed, which login method was used, and the session opening.
- Each entry has a timestamp.
- The entries are in time order, newest either consistently at the top or
  consistently at the bottom.
- After step 5 the log contains a clear failure entry saying the host could
  not be reached, with the address in it.
- After step 6 the log says authentication failed. It says which method
  failed.
- The log is readable on a phone screen: it does not require horizontal
  scrolling to understand each line.

## 24. No passwords or private keys appear in the logs

> **Not testable until Module E is implemented.**
>
> **This is the most important scenario in this document. If it fails,
> nothing ships.**

**What you are testing.** That secrets are stripped out before anything is
shown or saved. The rule in the codebase is that everything passes through a
component called the Redactor first.

**What you need.**
- A host that logs in with a password. Use a memorable, unusual test
  password, so it is easy to search for. For example `QaTestPassword12345`.
- A host that logs in with a passphrase protected key.
- A way to read the exported log file on a computer.

**Steps.**
1. Clear the log.
2. Connect using the password host. Enter the password.
3. Deliberately mistype the password once, then get it right.
4. Connect using the key based host. Enter the key passphrase.
5. In the terminal, deliberately type a secret where it will be echoed:
   `echo "MySecretString98765"` and press enter.
6. Also run a command that prints a private key:
   `cat ~/.ssh/id_ed25519` if one exists on the test server, or create a
   throwaway one with `ssh-keygen -t ed25519 -f /tmp/qa-key -N ""` and
   `cat /tmp/qa-key`.
7. Disconnect. Open the log viewer and read the whole thing.
8. Export the log (see scenario 25) and open the exported file on a computer.
9. Search the exported file for: `QaTestPassword12345`, the key passphrase,
   `BEGIN OPENSSH PRIVATE KEY`, and `MySecretString98765`.

**Pass criteria.**
- Searching the exported file finds **zero** occurrences of the test
  password.
- Searching finds **zero** occurrences of the key passphrase.
- Searching finds **zero** occurrences of `BEGIN OPENSSH PRIVATE KEY` or any
  line of base64 key material from step 6.
- The log viewer on screen shows the same. Scroll through the whole thing and
  read it.
- Where a secret was removed, the log shows a marker such as `[redacted]`, so
  it is obvious that something was there. A blank gap is acceptable; silently
  omitting the whole line is not, because then a reader cannot tell whether
  redaction happened or logging simply failed.
- Also check the Android system log. With the phone connected to a computer
  by USB and developer options on, run `adb logcat | grep -i pulsessh`, repeat
  steps 2 to 6, and search that output for the same strings. It must find
  nothing either.
- Repeat the whole scenario on a release build as well as a debug build.
  Debug builds sometimes log more.

## 25. Exporting the logs through the share sheet

> **Not testable until Module E is implemented.**

**What you are testing.** Getting the log off the phone so it can be attached
to a bug report.

**What you need.** Some log content, and at least one app that can receive a
file, such as Gmail, Drive or Files.

**Steps.**
1. Open the log viewer.
2. Use the export or share control.
3. The Android share sheet should appear. Note what apps it offers.
4. Choose "Save to Files" or Drive, and save it.
5. Open the saved file.
6. Repeat, this time sharing to an email app and attaching it to a draft.

**Pass criteria.**
- The Android share sheet opens. It is the system one, listing the apps
  installed on the phone.
- The shared item is a file whose name ends in `.log`, and whose name includes
  something identifying, such as a date.
- The saved file opens in a text viewer and contains readable plain text,
  matching what the log viewer showed.
- The file is not empty.
- Attaching it to an email works and the attachment size is not zero.
- Do scenario 24's searches on this exported file every time you run this
  scenario. The export path is the easiest place for redaction to be
  bypassed.

---

# Reporting a defect

If a pass criterion is not met, raise it. A vague report costs more time than
no report.

## What to capture

Capture all of this before you close the app, because some of it disappears
when you do.

| Item | How to find it |
| --- | --- |
| **Device model** | Settings > About phone. For example "Pixel 7a". |
| **Android version** | Settings > About phone > Android version. For example "14". |
| **App version** | The app's about or settings screen. Also acceptable: the file name of the APK you installed. |
| **Build type** | Debug or release. A debug install shows as a separate app because its package name ends in `.debug`. |
| **Which scenario** | The number and name from this document, for example "Scenario 19, resuming an interrupted transfer". |
| **Steps to reproduce** | Numbered, from opening the app. Include the values you typed. Assume the reader has never used the app. |
| **What you expected** | Quote the pass criterion that failed. |
| **What actually happened** | Exactly what you saw. Quote error messages word for word. |
| **How often** | Every time, or one time in five, or once and never again. Say which. Try it three times. |
| **Log export** | Export the log from the app (scenario 25) and attach the file. |
| **Screenshot or recording** | If the app allows it. Note that `FLAG_SECURE` blocks screenshots, which is deliberate. If you cannot screenshot, photograph the screen with another phone. |
| **Server details** | Operating system and version of the test server, and the SSH server version (`ssh -V` on the server). Many problems are specific to one server version. |
| **Network** | Wi-Fi, mobile data, or a VPN. Relevant for anything involving connections. |

## Before you attach a log

Read it first. Scenario 24 exists because logs are supposed to be safe to
share, but if redaction is the thing that is broken, the log may contain your
test password. Search it for your test password and passphrase before
attaching. If you find them, **that is a separate, high priority defect** and
the log must not be attached to a public issue.

Never use a real password on a test server, for exactly this reason.

## Where to file it

File a GitHub issue on the PulseSSH repository.

- Title: the scenario name plus what went wrong. For example: "Scenario 19:
  resumed download has correct size but wrong checksum".
- Body: the table of information above.
- Label it with the spec module (A to E) if you know which one it is.

If the defect involves a security problem, such as a secret appearing in a
log, an export, or the recent apps thumbnail, do **not** open a public issue.
Report it privately to the maintainers first.

## What makes a report good

A good report lets someone else reproduce the problem without asking you a
single question. Before you submit, reread it and ask: could I follow this on
a fresh phone and see the same thing? If a step says "connect to a server",
that is not enough. Say which server, which login method, and what you typed.
