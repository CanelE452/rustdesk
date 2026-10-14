import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/mobile/hardware_input_protocol.dart';

void main() {
  test('captured relative pointer starts at center and stays within canvas', () {
    final pointer = RemoteHardwarePointerState();
    expect(pointer.move(const Offset(5, -7), const Size(100, 60)),
        const Offset(55, 23));
    expect(pointer.move(const Offset(1000, -1000), const Size(100, 60)),
        const Offset(99, 0));
    expect(pointer.move(const Offset(double.nan, 1), const Size(100, 60)),
        const Offset(99, 0));
    expect(pointer.move(const Offset(1, 1), Size.zero), const Offset(99, 0));
  });

  test('mouse button transitions send releases before new presses', () {
    final pointer = RemoteHardwarePointerState();
    final a = pointer.changeButtons(1);
    expect(a.length, 1);
    expect(a.first.button, 1);
    expect(a.first.down, isTrue);
    expect(pointer.changeButtons(1), isEmpty);
    final b = pointer.changeButtons(2);
    expect(b.length, 2);
    expect(b[0].button, 1);
    expect(b[0].down, isFalse);
    expect(b[1].button, 2);
    expect(b[1].down, isTrue);
    final c = pointer.changeButtons(0);
    expect(c.length, 1);
    expect(c[0].button, 2);
    expect(c[0].down, isFalse);
  });

  test('wheel accumulates fractional scroll and rejects invalid values', () {
    final pointer = RemoteHardwarePointerState();
    expect(pointer.consumeWheel(const Offset(0.5, -0.3)), Offset.zero);
    expect(pointer.consumeWheel(const Offset(0.5, -0.8)),
        const Offset(1, -1));
    expect(pointer.consumeWheel(const Offset(double.infinity, 1)),
        Offset.zero);
  });

  test('only F1-F12 indices are accepted', () {
    expect(RemoteHardwarePointerState.functionIndex(1), 1);
    expect(RemoteHardwarePointerState.functionIndex(11), 11);
    expect(RemoteHardwarePointerState.functionIndex(12), 12);
    expect(RemoteHardwarePointerState.functionIndex(0), isNull);
    expect(RemoteHardwarePointerState.functionIndex(13), isNull);
    expect(RemoteHardwarePointerState.functionIndex('11'), isNull);
  });

  test('reset clears pressed mouse buttons and fractional wheel', () {
    final pointer = RemoteHardwarePointerState();
    pointer.changeButtons(7);
    pointer.consumeWheel(const Offset(0.5, 0.5));
    pointer.reset();
    expect(pointer.pressedButtons, 0);
    expect(pointer.consumeWheel(const Offset(0.5, 0.5)), Offset.zero);
  });
}
