// ignore_for_file: avoid_print
// Network diagnostics for the devotion source.
//
// Runs on the real Dart VM (no flutter_test HTTP mocking).
// Usage: `dart run tool/devotion_probe.dart`
import 'dart:convert';

import 'package:http/http.dart' as http;

Future<void> main() async {
  final client = http.Client();
  final uris = {
    'REST pretty': 'https://devotion.wkphc.org/wp-json/wp/v2/posts'
        '?per_page=2&_fields=id',
    'REST rest_route': 'https://devotion.wkphc.org/index.php'
        '?rest_route=%2Fwp%2Fv2%2Fposts&per_page=2&_fields=id',
    'RSS feed': 'https://devotion.wkphc.org/feed',
    'Image (ASCII)': 'https://devotion.wkphc.org/wp-content/uploads/2026/08/'
        'Giovanni_Muzzioli_Abramo_e_Sara_nella_Reggia_del_Faraone_olio_su_tela_1875-1.jpg',
    'YouTube thumb': 'https://img.youtube.com/vi/9rJm0Nq6TB0/hqdefault.jpg',
  };
  for (final entry in uris.entries) {
    try {
      final r = await client.get(Uri.parse(entry.value)).timeout(
            const Duration(seconds: 20),
          );
      print('${entry.key.padRight(16)} -> ${r.statusCode} '
          'len=${r.bodyBytes.length}');
      if (r.statusCode != 200 && r.bodyBytes.isNotEmpty) {
        print('  head: ${utf8.decode(r.bodyBytes.take(200).toList())}');
      }
    } catch (e) {
      print('${entry.key.padRight(16)} -> EXCEPTION $e');
    }
  }
  client.close();
}
