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
