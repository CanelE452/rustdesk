import 'package:flutter_hbb/mobile/soft_keyboard_diff.dart';
import 'package:flutter_test/flutter_test.dart';

/// Applies the edits the way a remote text box receives them.
String replay(List<String> snapshots) {
  var remote = <int>[];
  for (var i = 1; i < snapshots.length; i++) {
    final edit = softKeyboardEdit(snapshots[i - 1], snapshots[i]);
    remote = remote.sublist(0, remote.length - edit.deleteCount)
      ..addAll(edit.insert.runes);
  }
  return String.fromCharCodes(remote);
}

void main() {
  test('Korean composition reaches the remote intact', () {
    // Hidden text field snapshots while typing "안녕하세요" with Samsung
    // Keyboard (두벌식). The length-only diff sent "ㅇㄴㅎ세요".
    final snapshots = [
      '', 'ㅇ', '아', '안', '안ㄴ', '안녀', '안녕', '안녕ㅎ', '안녕하', '안녕핫', //
      '안녕하세', '안녕하셍', '안녕하세요',
    ];
    expect(replay(snapshots), '안녕하세요');
  });

  test('A shrinking change deletes every removed character', () {
    final edit = softKeyboardEdit('11 hello', '11 he');
    expect(edit.deleteCount, 3);
    expect(edit.insert, '');
  });

  test('A surrogate pair is never split', () {
    final edit = softKeyboardEdit('a😀', 'a😃');
    expect(edit.deleteCount, 1);
    expect(edit.insert, '😃');
  });
}
