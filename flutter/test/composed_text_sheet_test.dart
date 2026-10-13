import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/mobile/widgets/composed_text_sheet.dart';

Future<void> openComposer(
    WidgetTester tester, TextEditingController controller,
    bool Function(String) onSend) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => ElevatedButton(
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              builder: (context) =>
                  ComposedTextSheet(controller: controller, onSend: onSend),
            ),
            child: const Text('Open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('Korean IME text is not sent until explicit Send',
      (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final sent = <String>[];
    await openComposer(tester, controller, (text) {
      sent.add(text);
      return true;
    });

    await tester.enterText(
      find.byKey(const Key('composed-text-field')),
      '안녕하세요 ABC 123',
    );
    await tester.pump();
    expect(sent, isEmpty);

    await tester.tap(find.byKey(const Key('send-composed-text')));
    await tester.pumpAndSettle();

    expect(sent, ['안녕하세요 ABC 123']);
    expect(controller.text, isEmpty);
    expect(find.byType(ComposedTextSheet), findsNothing);
  });

  testWidgets('Empty text cannot be sent', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    var sends = 0;
    await openComposer(tester, controller, (_) {
      sends++;
      return true;
    });

    final send = tester.widget<ElevatedButton>(
        find.byKey(const Key('send-composed-text')));
    expect(send.onPressed, isNull);
    expect(sends, 0);
  });

  testWidgets('Disconnected session keeps the unsent draft', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final sent = <String>[];
    await openComposer(tester, controller, (text) {
      sent.add(text);
      return false;
    });

    await tester.enterText(
        find.byKey(const Key('composed-text-field')), '연결 해제 시 보존');
    await tester.tap(find.byKey(const Key('send-composed-text')));
    await tester.pumpAndSettle();

    expect(sent, ['연결 해제 시 보존']);
    expect(controller.text, '연결 해제 시 보존');
    expect(find.byType(ComposedTextSheet), findsOneWidget);
  });
}
