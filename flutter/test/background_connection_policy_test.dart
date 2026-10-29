import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/mobile/background_connection_policy.dart';

void main() {
  const dns = 'failed to lookup address information: no address associated with hostname';
  const timeout = 'Connection timed out';
  var now = DateTime.utc(2026, 10, 29, 12, 0);
  late AndroidBackgroundConnectionPolicy policy;

  setUp(() {
    now = DateTime.utc(2026, 10, 29, 12, 0);
    policy = AndroidBackgroundConnectionPolicy(clock: () => now);
  });

  BackgroundConnectionAction onError(String message, {
    String type = 'error',
    String title = 'Connection Error',
  }) => policy.onConnectionError(type: type, title: title, text: message);

  test('background and resume alone do not reconnect a healthy session', () {
    policy.onBackground();
    expect(policy.backgrounded, isTrue);
    expect(policy.onResume(), isFalse);
    expect(policy.pending, isFalse);
    expect(policy.onResume(), isFalse);
  });

  test('one transient DNS failure while backgrounded is deferred to resume', () {
    policy.onBackground();
    expect(onError(dns), BackgroundConnectionAction.deferUntilResume);
    expect(policy.pending, isTrue);
    expect(policy.onResume(), isTrue);
    expect(policy.onResume(), isFalse);
    expect(onError(timeout), BackgroundConnectionAction.showNormally);
  });

  test('delayed network failure immediately after background return retries once', () {
    policy.onBackground();
    now = now.add(const Duration(seconds: 8));
    expect(policy.onResume(), isFalse);
    now = now.add(const Duration(seconds: 2));
    expect(onError(timeout), BackgroundConnectionAction.reconnectOnce);
    expect(onError(timeout), BackgroundConnectionAction.showNormally);
  });

  test('unrelated errors and normal foreground errors remain untouched', () {
    expect(onError(dns), BackgroundConnectionAction.showNormally);
    policy.onBackground();
    expect(onError('Wrong password'), BackgroundConnectionAction.showNormally);
    expect(onError('Remote desktop is offline'), BackgroundConnectionAction.showNormally);
    expect(onError('Connection canceled'), BackgroundConnectionAction.showNormally);
    expect(onError('Peer close'), BackgroundConnectionAction.showNormally);
    expect(onError(timeout, title: 'Authentication Error'),
        BackgroundConnectionAction.showNormally);
    expect(onError(timeout, type: 'restarting'), BackgroundConnectionAction.showNormally);
    expect(policy.onResume(), isFalse);
  });

  test('grace period expires and future failures follow upstream behavior', () {
    policy.onBackground();
    expect(policy.onResume(), isFalse);
    now = now.add(const Duration(seconds: 16));
    expect(onError(dns), BackgroundConnectionAction.showNormally);
  });

  test('connection-ready and explicit teardown clear pending recovery', () {
    policy.onBackground();
    expect(onError(timeout), BackgroundConnectionAction.deferUntilResume);
    policy.onConnectionReady();
    expect(policy.onResume(), isFalse);

    policy.onBackground();
    expect(onError(timeout), BackgroundConnectionAction.deferUntilResume);
    policy.reset();
    expect(policy.onResume(), isFalse);
    expect(policy.pending, isFalse);
  });

  test('immediate transient error with no background transition is never retried', () {
    expect(onError(timeout), BackgroundConnectionAction.showNormally);
    policy.onBackground();
    policy.onResume();
    policy.reset();
    expect(onError(timeout), BackgroundConnectionAction.showNormally);
  });
}
