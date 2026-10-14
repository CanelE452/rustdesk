import 'dart:ui';

import 'package:flutter/services.dart';

/// Android-native input bridge, active only for an outgoing remote session.
/// No keyboard text or passwords are recorded by the native event channel.
class PersonalRemoteInput {
  static const _commands =
      MethodChannel('rustdesk/personal_remote_input/control');
  static const _events =
      EventChannel('rustdesk/personal_remote_input/events');

  Stream<PersonalInputEvent> get events => _events
      .receiveBroadcastStream()
      .where((event) => event is Map)
      .map((event) => PersonalInputEvent.fromMap(event as Map));

  Future<bool> enable() async =>
      (await _commands.invokeMethod<bool>('enable')) ?? false;

  Future<void> disable() async {
    await _commands.invokeMethod<bool>('disable');
  }
}

/// Ignore unexpected native payloads instead of forwarding unknown keys or
/// pointer movements to a remote machine.
class PersonalInputEvent {
  PersonalInputEvent._(this.type, this.payload);

  final String type;
  final Map<dynamic, dynamic> payload;

  factory PersonalInputEvent.fromMap(Map<dynamic, dynamic> payload) =>
      PersonalInputEvent._(payload['type'] is String ? payload['type'] : '', payload);

  bool get enabled => payload['enabled'] == true;
  bool get captured => payload['captured'] == true;
  int? get functionNumber {
    final value = payload['number'];
    if (type != 'function' || value is! int || value < 1 || value > 12) {
      return null;
    }
    return value;
  }

  int? get observedKeyCode => type == 'key_observed' &&
          payload['keyCode'] is int
      ? payload['keyCode'] as int
      : null;
  int? get observedScanCode => type == 'key_observed' &&
          payload['scanCode'] is int
      ? payload['scanCode'] as int
      : null;
  String? get button {
    if (type != 'button') return null;
    const supported = {'left', 'right', 'wheel'};
    return supported.contains(payload['button']) ? payload['button'] as String : null;
  }
  bool? get buttonDown =>
      type == 'button' && payload['down'] is bool
          ? payload['down'] as bool
          : null;

  double get dx => _safeCoordinate(payload['dx']);
  double get dy => _safeCoordinate(payload['dy']);

  static double _safeCoordinate(dynamic value) {
    if (value is! num || !value.isFinite) return 0;
    return value.toDouble().clamp(-120.0, 120.0).toDouble();
  }
}

/// Mouse motion on an Android captured trackpad uses *relative* deltas.
/// RustDesk's ordinary mouse input takes canvas-space *absolute* coordinates.
/// This converter keeps those two coordinate spaces separate.
class PersonalRemoteCursor {
  Offset? _current;
  Offset? get current => _current;

  void reset() => _current = null;

  Offset? move(double dx, double dy, Size remoteCanvas) {
    if (!dx.isFinite ||
        !dy.isFinite ||
        !remoteCanvas.width.isFinite ||
        !remoteCanvas.height.isFinite ||
        remoteCanvas.width <= 1 ||
        remoteCanvas.height <= 1) {
      return null;
    }
    final maxX = remoteCanvas.width - 1;
    final maxY = remoteCanvas.height - 1;
    final current = _current ?? Offset(maxX / 2, maxY / 2);
    _current = Offset(
      (current.dx + dx.clamp(-120, 120)).clamp(0.0, maxX).toDouble(),
      (current.dy + dy.clamp(-120, 120)).clamp(0.0, maxY).toDouble(),
    );
    return _current;
  }
}

/// Keeps sub-tick scroll increments so that slow two-finger gestures are not
/// thrown away when converted to the integer wheel protocol.
class PersonalScrollAccumulator {
  double _x = 0;
  double _y = 0;

  void reset() {
    _x = 0;
    _y = 0;
  }

  (int, int) add(double x, double y) {
    if (!x.isFinite || !y.isFinite) return (0, 0);
    _x += x.clamp(-16, 16).toDouble();
    _y += y.clamp(-16, 16).toDouble();
    final sx = _x.truncate();
    final sy = _y.truncate();
    _x -= sx;
    _y -= sy;
    return (sx, sy);
  }
}
