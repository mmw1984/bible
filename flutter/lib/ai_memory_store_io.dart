import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

class AiMemoryStore {
  File? _memoryFile;
  File? _transcriptFile;
  Future<void> _pendingWrite = Future<void>.value();

  Future<void> initialize() async {
    final directory = await getApplicationDocumentsDirectory();
    _memoryFile = File('${directory.path}/memory.md');
    _transcriptFile = File('${directory.path}/transcript.jsonl');
    if (!await _memoryFile!.exists()) {
      await _memoryFile!.writeAsString(
        '# Bible AI Memory\n\n'
        '## User preferences\n'
        '- Preferred response language: Traditional Chinese\n'
        '- Scripture reference language: Chinese and English\n\n'
        '## Important events and conversation\n\n'
        '## Explained scripture\n\n'
        '## Search history and conclusions\n\n',
        flush: true,
      );
    }
  }

  Future<String> promptMemory({int maxCharacters = 7000}) async {
    await initialize();
    final content = await _memoryFile!.readAsString();
    if (content.length <= maxCharacters) return content;
    return '# Bible AI Memory (latest entries)\n\n${content.substring(content.length - maxCharacters)}';
  }

  Future<List<Map<String, dynamic>>> transcript({int limit = 24}) async {
    await initialize();
    if (!await _transcriptFile!.exists()) return [];
    final lines = await _transcriptFile!.readAsLines();
    final messages = <Map<String, dynamic>>[];
    for (final line in lines.reversed) {
      if (messages.length >= limit) break;
      try {
        final decoded = jsonDecode(line);
        if (decoded is Map<String, dynamic>) messages.add(decoded);
      } on FormatException {
        // Preserve earlier valid history if a write was interrupted.
      }
    }
    return messages.reversed.toList();
  }

  Future<void> recordMessage({
    required String role,
    required String text,
    String? scripture,
    String kind = 'chat',
  }) async {
    await initialize();
    final timestamp = DateTime.now().toUtc().toIso8601String();
    final entry = <String, dynamic>{
      'timestamp': timestamp,
      'role': role,
      'kind': kind,
      'text': text,
      'scripture': ?scripture,
    };
    final heading = switch (kind) {
      'explanation' => 'Explained scripture',
      'search' => 'Search history and conclusions',
      _ => 'Important events and conversation',
    };
    final reference = scripture == null ? '' : '\n- Scripture: $scripture';
    Future<void> append() async {
      await _transcriptFile!.writeAsString(
        '${jsonEncode(entry)}\n',
        mode: FileMode.append,
        flush: true,
      );
      await _memoryFile!.writeAsString(
        '\n### $timestamp - $heading\n'
        '- Role: $role$reference\n'
        '- Content: ${text.replaceAll('\n', '\n  ')}\n',
        mode: FileMode.append,
        flush: true,
      );
    }

    _pendingWrite = _pendingWrite.then(
      (_) => append(),
      onError: (_) => append(),
    );
    await _pendingWrite;
  }

  Future<void> replaceTranscript(
    Iterable<Map<String, dynamic>> messages,
  ) async {
    await initialize();
    final snapshot = messages.map((message) => jsonEncode(message)).join('\n');
    Future<void> replace() => _transcriptFile!.writeAsString(
      snapshot.isEmpty ? '' : '$snapshot\n',
      flush: true,
    );
    _pendingWrite = _pendingWrite.then(
      (_) => replace(),
      onError: (_) => replace(),
    );
    await _pendingWrite;
  }

  Future<void> clear() async {
    await _pendingWrite;
    await initialize();
    if (await _memoryFile!.exists()) await _memoryFile!.delete();
    if (await _transcriptFile!.exists()) await _transcriptFile!.delete();
    _memoryFile = null;
    _transcriptFile = null;
    await initialize();
  }
}
