import 'package:flutter/material.dart';

/// Keeps Android IME composition entirely local until the user taps Send.
///
/// [onSend] returns true when the current remote session accepted the request
/// locally. It is not an acknowledgment that the remote application typed it.
class ComposedTextSheet extends StatelessWidget {
  const ComposedTextSheet({
    super.key,
    required this.controller,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool Function(String text) onSend;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('문장을 완성한 뒤 전송'),
            const SizedBox(height: 12),
            TextField(
              key: const Key('composed-text-field'),
              controller: controller,
              autofocus: true,
              minLines: 1,
              maxLines: 5,
              keyboardType: TextInputType.multiline,
              textInputAction: TextInputAction.newline,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: '한글, 영문, 기호를 입력해',
              ),
              // Do not send from onChanged/onSubmitted. IME pre-edit is local.
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('취소'),
                ),
                const SizedBox(width: 8),
                ValueListenableBuilder<TextEditingValue>(
                  valueListenable: controller,
                  builder: (context, draft, child) => ElevatedButton(
                    key: const Key('send-composed-text'),
                    onPressed: draft.text.isEmpty
                        ? null
                        : () {
                            final text = controller.text;
                            if (text.isEmpty || !onSend(text)) return;
                            controller.clear();
                            Navigator.of(context).pop();
                          },
                    child: const Text('전송'),
                  ),
                ),
              ],
            ),
            ],
          ),
        ),
      ),
    );
  }
}
