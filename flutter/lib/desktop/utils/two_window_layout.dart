import 'package:flutter/widgets.dart';

/// Geometry for two independent desktop remote-control windows.
///
/// [workArea] must use the same logical coordinate system as the desktop
/// window plugin's setFrame API. The caller uses the screen's visibleFrame
/// to avoid covering taskbars and docks.
List<Rect>? twoWindowSideBySideFrames(
  Rect workArea, {
  double padding = 8,
  double gap = 8,
  double minPaneWidth = 420,
  double minPaneHeight = 280,
}) {
  if (!workArea.left.isFinite ||
      !workArea.top.isFinite ||
      !workArea.width.isFinite ||
      !workArea.height.isFinite ||
      padding < 0 ||
      gap < 0 ||
      minPaneWidth <= 0 ||
      minPaneHeight <= 0) {
    return null;
  }

  final usableWidth = workArea.width - 2 * padding - gap;
  final usableHeight = workArea.height - 2 * padding;
  final paneWidth = usableWidth / 2;
  if (paneWidth < minPaneWidth || usableHeight < minPaneHeight) {
    return null;
  }

  return [
    Rect.fromLTWH(
      workArea.left + padding,
      workArea.top + padding,
      paneWidth,
      usableHeight,
    ),
    Rect.fromLTWH(
      workArea.left + padding + paneWidth + gap,
      workArea.top + padding,
      paneWidth,
      usableHeight,
    ),
  ];
}

enum TwoRemoteWindowTileResult {
  arranged,
  fewerThanTwoWindows,
  noScreen,
  screenTooSmall,
  failed,
}
