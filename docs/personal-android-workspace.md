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


## Samsung keyboard cover and trackpad mode (Android -> Windows/Ubuntu)

STATUS: source implementation and Flutter unit tests complete; actual Samsung
device behavior, native Android Kotlin compilation and full APK installation
must be verified separately. No root, no Accessibility Service permission
escalation, no global keylogger.

### Usage with a newly built APK

1. From Android RustDesk, connect to a Windows or Ubuntu desktop.
2. Open the remote toolbar and tap the keyboard/mouse icon labeled
   "태블릿 원격 마우스·기능키 모드 켜기".
3. Android requests pointer capture. The tablet cursor may disappear while
   the remote computer receives mouse movement, click, drag and scroll.
4. The in-session overlay displays mouse event count, F-key event count and
   the last function key observed by Android. Press Fn+F11 in a browser to test.
5. Tap the same icon by touch to release capture. App focus loss, background
   or leaving the remote page also releases the capture.

The normal remote keyboard and text composer remain available.
Opening either text entry mode releases pointer capture.

### What was changed

- Native Android controller: RemoteHardwareInput.kt. Registers a separate
  MethodChannel, requests/relinquishes pointer capture, sends only captured
  mouse/touchpad events and F1-F12 hardware key events to Flutter.
- MainActivity.kt: installs/removes native controller and intercepts function
  keys **only** while the user enables remote hardware mode. All other keys
  use the ordinary Android/Flutter dispatch path.
- RemotePage: maps captured relative cursor motion to the selected RustDesk
  canvas through existing input code; forwards left/right/middle mouse buttons,
  fractional wheel movements and F1-F12. Releases pressed buttons on exit.
- hardware_input_protocol.dart: state isolation and clamped coordinates.
- hardware_input_protocol_test.dart: geometry, button transitions, scrolling,
  and F-key index acceptance.

### Hard limitations

Android, Samsung One UI and the physical keyboard firmware may consume
HOME/system keys before dispatch to MainActivity. Fn is a firmware modifier and
may never be delivered as its own key. If Fn+F11 produces **no F-key event**
in the overlay, native RustDesk cannot simply force a hardware F11 through.
The existing on-screen F1-F12 buttons are an alternative.

Android pointer capture is documented from API 26, but older Samsung
touchpads may not emit normal relative mouse events in captured mode.
A "capture requested" response does not prove actual movement/scroll events.
No device/model-specific support is asserted without testing.

### Real-device validation gates

| Action | Required observation |
|---|---|
| Fn+F11 on Samsung keyboard | Overlay increases F-key count; Chrome/Edge remote fullscreen toggles |
| Plain F1 triggers Samsung HOME | Verify whether Android keeps it in system (cannot intercept) |
| Move touchpad while enabled | Android cursor is captured and Windows/Ubuntu remote cursor moves |
| Left/right click, drag | Correct remote buttons; no stuck button after disconnect |
| Two-finger scroll | Remote page scrolls with correct direction |
| Normal tablet touch | Can disable capture without attaching another mouse |
| Exit/back/background | Pointer capture releases immediately; no remote keys/buttons remain held |
| Windows and Ubuntu | Repeat separately; ensure no cross-peer input |
| Text composer and F1-F12 on-screen | Both previous input pathways remain functional |

Failure classifications: no captured events => Android/OEM input routing;
events present but no remote motion => RustDesk canvas mapping/injection;
F-key overlay count rises but remote browser ignores => peer key injection or
focus mapping; no count => hardware/OS filtered before RustDesk.
