/// Conservative Android outgoing-session recovery for transient transport errors.
///
/// Keeping a Flutter widget alive does not guarantee that Android keeps its
/// process/socket alive while backgrounded. This policy only suppresses known
/// transport errors that occur in the background and permits a single
/// reconnect upon return. A late error immediately after return may also
/// trigger one reconnect. It never retries credential/permission failures.
enum BackgroundConnectionAction { showNormally, deferUntilResume, reconnectOnce }

class AndroidBackgroundConnectionPolicy {
  AndroidBackgroundConnectionPolicy({
    DateTime Function()? clock,
    this.returnGrace = const Duration(seconds: 15),
  }) : _clock = clock ?? DateTime.now;

  final DateTime Function() _clock;
  final Duration returnGrace;

  bool _backgrounded = false;
  bool _pending = false;
  bool _retried = false;
  DateTime? _lastReturned;

  bool get backgrounded => _backgrounded;
  bool get pending => _pending;

  void onBackground() {
    if (_backgrounded) return;
    _backgrounded = true;
    _pending = false;
    _retried = false;
    _lastReturned = null;
  }

  /// Returns true only when an observed background transport failure needs
  /// one reconnect. Ordinary app switching never restarts a healthy session.
  bool onResume() {
    if (!_backgrounded) return false;
    _backgrounded = false;
    _lastReturned = _clock();
    if (!_pending || _retried) return false;
    _pending = false;
    _retried = true;
    return true;
  }

  BackgroundConnectionAction onConnectionError({
    required String? type,
    required String? title,
    required String? text,
  }) {
    if (!_isTransientTransportError(type, title, text)) {
      return BackgroundConnectionAction.showNormally;
    }
    if (_backgrounded) {
      _pending = true;
      return BackgroundConnectionAction.deferUntilResume;
    }
    if (_retried || _lastReturned == null) {
      return BackgroundConnectionAction.showNormally;
    }
    final sinceResume = _clock().difference(_lastReturned!);
    if (sinceResume.isNegative || sinceResume > returnGrace) {
      return BackgroundConnectionAction.showNormally;
    }
    _retried = true;
    return BackgroundConnectionAction.reconnectOnce;
  }

  /// A successfully re-established session cancels background recovery.
  void onConnectionReady() {
    _pending = false;
    _retried = false;
    _lastReturned = null;
  }

  void reset() {
    _backgrounded = false;
    _pending = false;
    _retried = false;
    _lastReturned = null;
  }

  static bool _isTransientTransportError(
      String? type, String? title, String? text) {
    if (type != 'error' || title != 'Connection Error' || text == null) {
      return false;
    }
    final message = text.toLowerCase();
    // A network/DNS failure can occur while Android suspends the app.
    // Don't treat generic "peer close", credentials, explicit cancellation
    // or remote desktop offline as transport failures for this policy.
    const transient = <String>[
      'failed to lookup address information',
      'no address associated with hostname',
      'network is unreachable',
      'network unreachable',
      'connection timed out',
      'timed out',
      'timeout',
      'deadline has elapsed',
      'connection reset',
      'broken pipe',
      'temporary failure in name resolution',
      'unable to resolve host',
    ];
    return transient.any(message.contains);
  }
}
