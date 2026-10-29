# Android outgoing connection: background/foreground recovery

## Scope
Test-only change in branch fix/android-background-recovery, incorporating
previous Samsung keyboard-cover and touchpad diagnostics. A phone/tablet
controls Windows or Ubuntu. No incoming Android control service, accessibility
permission, or new foreground service has been added.

## Report and evidence
The user reports that leaving RustDesk and returning cancels the remote
connection. The exact transport error and Android lifecycle trace have not
been captured, so the underlying cause is still unverified.

The existing Flutter RemotePage already observes lifecycle changes without
intentionally closing sessions on paused or inactive. FfiModel already provides
sessionReconnect. Upstream RustDesk issue #15060 and PR #15143 explain why
intentional page disposal must close its Rust session early to avoid a leaked
session and a stuck reconnect. That prior fix is preserved.

## Implemented fallback (on-device behavior unverified)
- On hidden or paused: note a real background transition; inactive alone
  (e.g., system permission dialog) is not considered a background session.
- Recognized DNS and transport timeout errors seen in the background are
  deferred instead of immediately being displayed as error dialogs.
- On return to resumed, one existing RustDesk reconnect attempt is made if
  a recognized transport error was actually observed.
- Similar network errors arriving within 15 seconds after returning can
  trigger one retry. This grace period is a development heuristic, not a
  measured optimal threshold.
- A successful connection-ready event clears pending recovery. A deliberate
  page close resets it and keeps the original sessionClose behavior.
- Password/authentication errors, remote rejection, intentional cancellation,
  and ordinary healthy app switches do not trigger automatic reconnect.

This does not keep the process permanently alive in the background. Android
may kill/suspend it; a foreground service would require a separate
user-visible notification and permission/policy design.

## Source and tests
- flutter/lib/mobile/background_connection_policy.dart
- flutter/lib/models/model.dart
- flutter/lib/mobile/pages/remote_page.dart
- flutter/test/background_connection_policy_test.dart

## Manual acceptance matrix (not performed)
1. Android smartphone -> Windows: connect; Home for 5 seconds and return;
   verify no login and unchanged session.
2. Android tablet -> Ubuntu: switch to another app for 30 seconds and return;
   verify remote screen and mouse still work.
3. Change Wi-Fi/mobile data while backgrounded; verify at most one automatic
   recovery for recognized transient DNS/timeout errors, not indefinite loops.
4. Tap Close on the remote toolbar; verify no automatic reconnect.
5. Wrong password or explicit remote rejection; verify the original error
   remains visible, no automatic retry.
6. Android process death: session persistence is not guaranteed; verify safe
   re-entry without a stuck loading state.
7. Keyboard-cover mouse capture is released on background and no remote mouse
   button remains held after resuming.
8. Keyboard text composer and other original input methods still work.

If the real issue persists, collect the visible connection error reason and
whether the app remained on its remote screen after returning; do not collect
passwords or private clipboard text. Do not claim fixed before device testing.
