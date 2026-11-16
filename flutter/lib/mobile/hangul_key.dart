// USB HID keyboard usage IDs.
const int _kUsbHidAltRight = 0xe6;
const int _kUsbHidLang1 = 0x90; // Hangul/English toggle

/// Korean keyboard covers (e.g. Samsung Book Cover Keyboard) send the
/// Hangul/English key as Right Alt. Windows hosts turn that into VK_HANGUL,
/// but Linux hosts treat it as a plain Alt, so send the Hangul key instead.
int hangulKeyUsbHid(int usbHid, {required bool androidToLinux}) =>
    androidToLinux && usbHid == _kUsbHidAltRight ? _kUsbHidLang1 : usbHid;
