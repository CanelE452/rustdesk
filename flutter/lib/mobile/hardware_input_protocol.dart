import 'package:flutter/painting.dart';

/// Platform-independent bookkeeping for captured Android mouse/touchpad data.
/// No remote packets are emitted here; the RemotePage owns the active session.
class RemoteHardwarePointerState {
  Offset cursor = Offset.zero;
  bool _hasCursor = false;
  int _pressedButtons = 0;
  double _wheelX = 0;
  double _wheelY = 0;

  int get pressedButtons => _pressedButtons;

  /// The absolute position is clamped to RustDesk's visible canvas.
  /// Pointer capture itself reports relative motion, not screen coordinates.
  Offset move(Offset delta, Size canvas) {
    if (canvas.width <= 1 ||
        canvas.height <= 1 ||
        !canvas.width.isFinite ||
        !canvas.height.isFinite ||
        !delta.dx.isFinite ||
        !delta.dy.isFinite) {
      return cursor;
    }
    if (!_hasCursor) {
      cursor = Offset(canvas.width / 2, canvas.height / 2);
      _hasCursor = true;
    }
    cursor = Offset(
      (cursor.dx + delta.dx).clamp(0.0, canvas.width - 1).toDouble(),
      (cursor.dy + delta.dy).clamp(0.0, canvas.height - 1).toDouble(),
    );
    return cursor;
  }

  /// Ensure the first physical click has a meaningful canvas position.
  Offset atCanvas(Size canvas) => move(Offset.zero, canvas);

  /// Releases are reported before presses, avoiding held-button leakage.
  List<HardwareButtonChange> changeButtons(int newButtons) {
    const mask = 0x7; // Android primary / secondary / tertiary mouse buttons.
    newButtons &= mask;
    final changes = <HardwareButtonChange>[];
    for (final button in const [1, 2, 4]) {
      if ((_pressedButtons & button) != 0 && (newButtons & button) == 0) {
        changes.add(HardwareButtonChange(button, false));
      }
    }
    for (final button in const [1, 2, 4]) {
      if ((_pressedButtons & button) == 0 && (newButtons & button) != 0) {
        changes.add(HardwareButtonChange(button, true));
      }
    }
    _pressedButtons = newButtons;
    return changes;
  }

  /// Accumulate high-resolution scroll so small movements do not disappear.
  Offset consumeWheel(Offset delta) {
    if (!delta.dx.isFinite || !delta.dy.isFinite) return Offset.zero;
    _wheelX += delta.dx;
    _wheelY += delta.dy;
    final x = _wheelX.truncate();
    final y = _wheelY.truncate();
    _wheelX -= x;
    _wheelY -= y;
    return Offset(x.toDouble(), y.toDouble());
  }

  void reset() {
    _pressedButtons = 0;
    _wheelX = 0;
    _wheelY = 0;
    _hasCursor = false;
    cursor = Offset.zero;
  }

  static int? functionIndex(dynamic value) =>
      value is int && value >= 1 && value <= 12 ? value : null;
}

class HardwareButtonChange {
  final int button;
  final bool down;
  const HardwareButtonChange(this.button, this.down);
}
