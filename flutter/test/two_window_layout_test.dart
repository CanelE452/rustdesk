import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/desktop/utils/two_window_layout.dart';

void main() {
  test('positions two windows side by side inside screen work area', () {
    const desktop = Rect.fromLTWH(0, 0, 1920, 1040);
    final frames = twoWindowSideBySideFrames(desktop);
    expect(frames, isNotNull);
    expect(frames, hasLength(2));
    expect(frames![0], const Rect.fromLTWH(8, 8, 948, 1024));
    expect(frames[1], const Rect.fromLTWH(964, 8, 948, 1024));
    expect(frames[0].right < frames[1].left, isTrue);
    expect(desktop.contains(frames[0].topLeft), isTrue);
    expect(desktop.contains(frames[1].bottomRight), isTrue);
  });

  test('supports monitors left of the primary display', () {
    const desktop = Rect.fromLTWH(-1600, -100, 1600, 900);
    final frames = twoWindowSideBySideFrames(desktop);
    expect(frames, isNotNull);
    expect(frames![0].left, -1592);
    expect(frames[0].top, -92);
    expect(frames[1].right, -8);
    expect(frames[1].bottom, 792);
  });

  test('rejects unavailable dimensions instead of hiding remote windows', () {
    expect(twoWindowSideBySideFrames(Rect.zero), isNull);
    expect(twoWindowSideBySideFrames(const Rect.fromLTWH(0, 0, 800, 600)),
        isNull);
    expect(twoWindowSideBySideFrames(
        Rect.fromLTWH(double.nan, 0, 1920, 1080)), isNull);
    expect(twoWindowSideBySideFrames(const Rect.fromLTWH(0, 0, 1920, 1080),
        gap: -1), isNull);
  });

  test('exact minimum size produces two disjoint panes', () {
    const w = 420.0;
    const padding = 8.0;
    const gap = 8.0;
    final frames = twoWindowSideBySideFrames(
        Rect.fromLTWH(20, 40, 2 * w + 2 * padding + gap, 280 + 2 * padding));
    expect(frames, isNotNull);
    expect(frames![0].width, w);
    expect(frames[1].width, w);
    expect(frames[0].bottom, 328);
    expect(frames[1].left, frames[0].right + gap);
  });
}
