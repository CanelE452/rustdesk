/// Turns the previous soft-keyboard text into the current one on the remote
/// side: press Backspace [deleteCount] times, then type [insert].
class SoftKeyboardEdit {
  final int deleteCount;
  final String insert;

  const SoftKeyboardEdit(this.deleteCount, this.insert);
}

/// Korean and other IMEs rewrite the character being composed in place
/// ("아" -> "안"), so the length of the hidden text field alone cannot tell
/// what changed. Compare contents: everything after the common prefix is
/// deleted and retyped.
SoftKeyboardEdit softKeyboardEdit(String oldValue, String newValue) {
  final limit =
      oldValue.length < newValue.length ? oldValue.length : newValue.length;
  var common = 0;
  while (common < limit &&
      oldValue.codeUnitAt(common) == newValue.codeUnitAt(common)) {
    common++;
  }
  // Never split a surrogate pair; retype the whole character instead.
  if (common > 0 && _isHighSurrogate(oldValue.codeUnitAt(common - 1))) {
    common--;
  }
  return SoftKeyboardEdit(
    oldValue.substring(common).runes.length,
    newValue.substring(common),
  );
}

bool _isHighSurrogate(int codeUnit) => codeUnit >= 0xD800 && codeUnit <= 0xDBFF;
