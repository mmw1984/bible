import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class AiMemoryStore {
  final List<Map<String, dynamic>> _messages = [];
  SharedPreferences? _preferences;
  static const _transcriptKey = 'bible_ai_web_transcript';

  Future<void> initialize() async {
    if (_preferences != null) return;
    _preferences = await SharedPreferences.getInstance();
    final saved = _preferences!.getStringList(_transcriptKey) ?? const [];
    _messages
      ..clear()
      ..addAll(
        saved
            .map((item) {
              try {
                return (jsonDecode(item) as Map).cast<String, dynamic>();
              } catch (_) {
                return <String, dynamic>{};
              }
            })
            .where((item) => item.isNotEmpty),
      );
  }

  Future<String> promptMemory({int maxCharacters = 7000}) async {
    await initialize();
    final content = _messages
        .map((item) {
          final scripture = item['scripture'];
          return '${item['role']}: ${item['text']}'
              '${scripture == null ? '' : '\nScripture: $scripture'}';
        })
        .join('\n\n');
    if (content.length <= maxCharacters) return content;
    return content.substring(content.length - maxCharacters);
  }

  Future<List<Map<String, dynamic>>> transcript({int limit = 24}) async =>
      (await _loadedMessages())
          .skip(_messages.length > limit ? _messages.length - limit : 0)
          .toList();
  Future<void> recordMessage({
    required String role,
    required String text,
    String? scripture,
    String kind = 'chat',
  }) async {
    await initialize();
    _messages.add({
      'role': role,
      'text': text,
      'kind': kind,
      'scripture': ?scripture,
    });
    await _persist();
  }

  Future<void> replaceTranscript(
    Iterable<Map<String, dynamic>> messages,
  ) async {
    await initialize();
    _messages
      ..clear()
      ..addAll(messages);
    await _persist();
  }

  Future<void> clear() async {
    await initialize();
    _messages.clear();
    await _preferences!.remove(_transcriptKey);
  }

  Future<List<Map<String, dynamic>>> _loadedMessages() async {
    await initialize();
    return _messages;
  }

  Future<void> _persist() async {
    if (_messages.length > 200) {
      _messages.removeRange(0, _messages.length - 200);
    }
    await _preferences!.setStringList(
      _transcriptKey,
      _messages.map(jsonEncode).toList(),
    );
  }
}
