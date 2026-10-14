# Android controller: current state and acceptance

## Goal
Control Windows and Ubuntu PCs from an Android phone, transmit complete Korean
text, and eventually keep both connections alive in separate mobile tabs.
Controlling the phone from Windows is out of scope.

## Implemented in this branch
- Edit-note toolbar icon on Android sessions whose peer is not Android.
- Android drafts are composed locally; text changes are not sent live.
- Tapping Send calls the existing sessionInputString API once for the selected peer.
- Empty drafts are blocked. Cancel preserves the draft.
- Disconnected, view-only or keyboard-blocked sessions reject the send locally.
- Existing RustDesk Wayland keyboard consent remains in force.
- The original live soft keyboard remains available.

Host-side acceptance is NOT validated yet. Sending a request does not prove
that Unicode input is injected successfully by a remote application.

## Widget test
Run from the flutter directory in a compatible Flutter SDK environment:
    flutter pub get
    flutter test test/composed_text_sheet_test.dart

The widget tests cover explicit Send, empty Send, and rejected Send.
Flutter and Dart were not available in the development runtime.

## Manual host checks — not executed
1. Android -> Windows 11 Notepad: type 안녕하세요 ABC 123!?. Nothing must arrive
   before Send; afterwards text must match exactly.
2. Android -> Ubuntu X11 text editor: same test.
3. Android -> Ubuntu Wayland text editor: require explicit keyboard consent,
   then repeat the exact-text test.
4. Android -> both hosts: view-only, disconnect during editing, existing live
   keyboard/mouse regression, newline and emoji input.
5. Test direct Tailscale IP and RustDesk public server connections separately.

## Multi-session pending: verified upstream constraints
Current upstream FFI(null) is global on mobile and defaults to a constant
session ID. RemotePage and child widgets use gFFI. RemotePage.dispose closes
its session. Naively switching pages would therefore risk cross-target input
and background disconnection.

### Next design [unverified]
1. Allocate an independent FFI and unique SessionID for each Android peer.
2. Inject the per-peer FFI through remote page, input, canvas, overlays,
   chat, keyboard helpers and event listeners; retain global account FFI.
3. Add mobile workspace tabs retaining inactive sessions and selected target.
4. Do not dispose sessions when switching tabs. Close only on explicit Close.
5. Keep only the selected peer video surface visible and inspect background
   decoding, resource load and Android lifecycle behavior.

### Gates before calling multi-session complete
- Two active peers with distinct session IDs and callbacks.
- Tabs switch without disconnecting or crossing video frames.
- Text targeted at Windows never reaches Ubuntu, and vice versa.
- Permission and consent remain per connection.
- Android rotation/background/reconnect do not leak remote sessions.

STATUS: Multi-session is NOT implemented on this branch. It remains separate
from the text-composer change until these conditions can be verified.


## Desktop: two remote windows side by side

Implemented on the combined feature branch for Windows and Ubuntu clients.
This is a controller-side convenience feature and does not require changes to
the remotely controlled Windows/Ubuntu RustDesk host.

### Using it

1. Open the regular RustDesk main window's remote-ID connection pane.
2. Enable "새 연결 별도 창" (new connections in separate windows). This
   persists the existing RustDesk option to disable tabbed new connections.
   Previously open tabs are not silently migrated.
3. Connect to computer A and B in separate remote-desktop windows.
4. Click "원격 화면 2개 나란히" (tile two remote screens). The two most
   recently created active remote windows are placed left and right on one
   monitor. Each remains an independent live session.

When there are fewer than two separate connected remote windows, the button
shows an explanation rather than changing sessions. With two tabs in one
window, use the upstream "Move tab to new window" action first, or reconnect
after switching to separate-window mode.

### Implementation

- flutter/lib/utils/multi_window_manager.dart: reuse the existing per-window
  RustDesk session manager. Query live remote windows, then request fullscreen
  exit and window frames. No change to compression, clipboard, networking,
  input events or remote sessions.
- flutter/lib/desktop/pages/connection_page.dart: preference switch and
  manual "tile" action in the desktop main window.
- flutter/lib/desktop/utils/two_window_layout.dart: pure work-area geometry
  calculation with margins and minimum pane size (heuristic).
- flutter/test/two_window_layout_test.dart: geometry for positive/negative
  coordinates, too-small screens and exact boundary dimensions.

The arrangement uses the monitor containing the first selected window.
The visible work area excludes toolbars and docks. The manager attempts to
restore original rectangles if a window API call fails.

### Required real-device checks (not executed)

| Controller | Remote A | Remote B | Expected |
|---|---|---|---|
| Windows | Windows | Ubuntu | Live independently controlled windows side by side |
| Ubuntu X11 | Windows | Ubuntu | Same, on one display |
| Ubuntu Wayland | Windows | Ubuntu | Check compositor restrictions on window positioning |
| Windows dual-monitor | Windows | Ubuntu | Both windows on first selected window's monitor |
| Either | Two tabs in one window | - | Explain separate windows; never drop sessions |
| Either | One connected remote window | - | No resize; show guidance |
| Either | 3+ remote windows connected | - | Tile two most recent; leave others untouched |

Validate independent keyboard focus, no cross-device input, >60 seconds of
both video streams, disconnect/reconnect, display scaling (100%, 150%, 200%),
maximize and restore, and window backend behavior. Some Linux Wayland
compositors may prevent programmatic positioning. Automated geometry tests
do not establish full Ubuntu Wayland support.

### Status
- Source changes committed to combined feature branch.
- Flutter tests/static analyzer: see feature branch GitHub Actions.
- Native Windows/Ubuntu builds and real-device input/display tests pending.
- Android simultaneous multi-session tabs remain a separate unimplemented
  architecture task; desktop windows do not solve mobile FFI isolation.


## Samsung tablet keyboard-cover input (Android controller)

Implemented in feature/android-samsung-input as an **opt-in experimental mode**.
This change affects the Android controller only; Windows and Ubuntu are remote
targets and do not need new protocol changes.

### Usage after installing a build

1. Connect the tablet's RustDesk app to Windows or Ubuntu with control permission.
2. In the remote bottom toolbar, tap the mouse icon to turn on
   **원격 키보드·터치패드 모드**.
3. The top overlay reports **원격 터치패드 연결됨** only after Android
   confirms pointer capture. If it says **포인터 캡처 대기**, the native
   request may not have been granted, so remote pointer control is unverified.
4. Move the cover trackpad, click, and use two-finger scrolling. The Android
   bridge converts recognized events to cursor positions, button presses and
   wheel increments. Actual touchpad event semantics must be checked on-device.
5. Press Fn+F11 with a remote browser selected. If Android sends KEYCODE_F11,
   the native bridge forwards it to the remote computer and the diagnostic
   overlay shows Android key code **141** (F1 is 131). If the tablet gives the
   app no key event, the overlay cannot force that hardware event through.
6. The overlay provides explicit F1-F12 buttons even when Samsung/Android
   consume the hardware F-row. To exit, use the overlay X button or leave the
   remote page. Focus loss and app pause automatically release capture.

### System limitation

Android reserves the HOME key and does not deliver KEYCODE_HOME to ordinary
apps. If the cover's bare F1 triggers HOME, this RustDesk source change
**cannot guarantee bare F1 is rerouted**. A keyboard firmware/Fn-layer or
One UI keyboard setting may be needed. Native code intercepts only F1-F12
when actual function-key events reach MainActivity; other keys remain on
the existing Flutter input path. Neither the text typed nor passwords are
logged by the diagnostic; only selected non-printing key codes are displayed.

### Source changes

- flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/PersonalRemoteInput.kt
  provides EventChannel events and a MethodChannel toggle, captures pointer
  input with Android View.requestPointerCapture(), forwards delivered F keys,
  and stops capture when the app loses focus.
- flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt
  installs and tears down the bridge.
- flutter/lib/mobile/models/personal_remote_input.dart implements typed event
  parsing, bounded cursor mapping and fractional scroll accumulation.
- flutter/lib/mobile/pages/remote_page.dart provides the control icon,
  keyboard/trackpad overlay, capture status, and existing session forwarding.
- flutter/test/personal_remote_input_test.dart tests parser and pure geometry.

### Validation still required

- Android native Kotlin compilation in GitHub Actions (separate workflow).
- Install an Android APK on the specific Samsung tablet and repeat Fn+F11,
  F1 alone, mouse move, click/drag, two-finger scroll, focus loss, and
  reconnect; compare actual recorded numeric key events only.
- Confirm the cursor location matches taps in both Windows and Ubuntu,
  including display scaling and different resolutions.
- Verify no inadvertent keystrokes in view-only or disconnected sessions.

STATUS: Source code committed, Flutter checks are separate, Android runtime
and hardware behavior **not yet proven**.