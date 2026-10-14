import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/mobile/models/personal_remote_input.dart';

void main() {
  test('only legitimate F1-F12 numbers are exposed', () {
    expect(PersonalInputEvent.fromMap(
        {'type': 'function', 'number': 11}).functionNumber, 11);
    expect(PersonalInputEvent.fromMap(
        {'type': 'function', 'number': 13}).functionNumber, isNull);
    expect(PersonalInputEvent.fromMap(
        {'type': 'function', 'number': 0}).functionNumber, isNull);
    expect(PersonalInputEvent.fromMap(
        {'type': 'move', 'number': 1}).functionNumber, isNull);
  });

  test('only known mouse buttons can pass the parser', () {
    final left = PersonalInputEvent.fromMap(
        {'type': 'button', 'button': 'left', 'down': true});
    expect(left.button, 'left');
    expect(left.buttonDown, isTrue);
    final unknown = PersonalInputEvent.fromMap(
        {'type': 'button', 'button': 'extra', 'down': false});
    expect(unknown.button, isNull);
  });

  test('native move payload ignores invalid coordinates and clamps outliers', () {
    final invalid = PersonalInputEvent.fromMap(
        {'type': 'move', 'dx': double.infinity, 'dy': 'oops'});
    expect(invalid.dx, 0);
    expect(invalid.dy, 0);
    final extreme = PersonalInputEvent.fromMap(
        {'type': 'move', 'dx': 900, 'dy': -900});
    expect(extreme.dx, 120);
    expect(extreme.dy, -120);
  });

  test('captured pointer is bounded within the local remote canvas', () {
    final pointer = PersonalRemoteCursor();
    expect(pointer.move(0, 0, const Size(1000, 600)),
        const Offset(499.5, 299.5));
    expect(pointer.move(8000, 8000, const Size(1000, 600)),
        const Offset(619.5, 419.5));
    for (var i = 0; i < 8; i++) {
      pointer.move(120, 120, const Size(1000, 600));
    }
    expect(pointer.current, const Offset(999, 599));
    pointer.reset();
    expect(pointer.current, isNull);
    expect(pointer.move(3, 3, Size.zero), isNull);
  });

  test('fractional scroll is accumulated without phantom wheel events', () {
    final scroll = PersonalScrollAccumulator();
    expect(scroll.add(0.25, 0.25), (0, 0));
    expect(scroll.add(0.25, 0.25), (0, 0));
    expect(scroll.add(0.25, 0.6), (0, 1));
    expect(scroll.add(0.25, -0.1), (1, 0));
    scroll.reset();
    expect(scroll.add(double.nan, 2), (0, 0));
    expect(scroll.add(-1.5, 0), (-1, 0));
  });
}
