import 'dart:async';
import 'dart:convert';

import 'package:bible/openrouter_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

void main() {
  test(
    'research forces one server-tool search without deprecated plugins',
    () async {
      final transport = _RecordingClient([
        _jsonResponse({
          'choices': [
            {
              'message': {
                'content': 'Evidence brief',
                'annotations': [
                  {
                    'type': 'url_citation',
                    'url_citation': {
                      'url': 'https://one.test/a?utm_source=x&id=1#part',
                      'title': 'One A',
                      'content': 'A',
                    },
                  },
                  {
                    'url_citation': {
                      'url': 'https://one.test/b?fbclid=x',
                      'title': 'One B',
                      'content': 'B',
                    },
                  },
                  {
                    'url_citation': {
                      'url': 'https://one.test/c',
                      'title': 'One C',
                      'content': 'C',
                    },
                  },
                  {
                    'url_citation': {
                      'url': 'https://two.test/a?gclid=x',
                      'title': 'Two',
                      'content': 'D',
                    },
                  },
                ],
              },
            },
          ],
          'usage': {
            'server_tool_use': {'web_search_requests': 1},
          },
        }),
      ]);
      final client = OpenRouterClient(auth: _SignedInAuth(), client: transport);

      final result = await client.research(prompt: 'question', model: 'model');
      final body = transport.bodies.single;

      expect(body, isNot(contains('plugins')));
      expect(body['max_tokens'], 8192);
      expect(body['tool_choice'], {'type': 'openrouter:web_search'});
      expect(body['max_tool_calls'], 1);
      expect(body['tools'], [
        {
          'type': 'openrouter:web_search',
          'parameters': {
            'engine': 'auto',
            'max_results': 6,
            'max_uses': 1,
            'max_total_results': 6,
            'search_context_size': 'low',
          },
        },
      ]);
      expect(result.searchRequests, 1);
      expect(result.sources, hasLength(3));
      expect(result.sources.map((source) => source.url), [
        'https://one.test/a?id=1',
        'https://one.test/b',
        'https://two.test/a',
      ]);
    },
  );

  test('answer stream uses automatic server tool with strict limits', () async {
    final transport = _RecordingClient([
      _streamResponse([
        'data: ${jsonEncode({
          'choices': [
            {
              'delta': {
                'reasoning_details': [
                  {'type': 'reasoning.text', 'text': 'reasoning summary'},
                ],
                'content': 'answer',
              },
            },
          ],
        })}\n\n',
        'data: ${jsonEncode({
          'choices': [
            {'delta': {}, 'finish_reason': 'stop'},
          ],
        })}\n\n',
        'data: [DONE]\n\n',
      ]),
    ]);
    final client = OpenRouterClient(auth: _SignedInAuth(), client: transport);
    final reasoning = <String>[];
    String? finishReason;

    final answer = await client
        .generateStream(
          prompt: 'question',
          model: 'model',
          onReasoning: reasoning.add,
          onFinishReason: (value) => finishReason = value,
          options: const OpenRouterRequestOptions(
            webSearch: OpenRouterWebSearch.automatic,
            maxToolCalls: 2,
            maxWebSearchUses: 2,
            maxWebResults: 5,
            maxTotalWebResults: 10,
            webSearchContextSize: 'low',
          ),
        )
        .join();
    final body = transport.bodies.single;

    expect(answer, 'answer');
    expect(reasoning, ['reasoning summary']);
    expect(finishReason, 'stop');
    expect(body, isNot(contains('plugins')));
    expect(body, isNot(contains('max_tokens')));
    expect(body['reasoning'], {'max_tokens': 8192, 'exclude': false});
    expect(body['tool_choice'], 'auto');
    expect(body['max_tool_calls'], 2);
    expect((body['tools'] as List).single, {
      'type': 'openrouter:web_search',
      'parameters': {
        'engine': 'auto',
        'max_results': 5,
        'max_uses': 2,
        'max_total_results': 10,
        'search_context_size': 'low',
      },
    });
  });

  test('stream surfaces OpenRouter event errors', () async {
    final client = OpenRouterClient(
      auth: _SignedInAuth(),
      client: _RecordingClient([
        _streamResponse([
          'data: ${jsonEncode({
            'error': {'message': 'Selected model exhausted its token budget'},
          })}\n\n',
          'data: [DONE]\n\n',
        ]),
      ]),
    );

    await expectLater(
      client.generateStream(prompt: 'question', model: 'model').drain<void>(),
      throwsA(
        isA<StateError>().having(
          (error) => error.message,
          'message',
          contains('token budget'),
        ),
      ),
    );
  });

  test('research fails when the required server tool did not run', () async {
    final client = OpenRouterClient(
      auth: _SignedInAuth(),
      client: _RecordingClient([
        _jsonResponse({
          'choices': [
            {
              'message': {'content': 'unsupported fallback'},
            },
          ],
          'usage': {
            'server_tool_use': {'web_search_requests': 0},
          },
        }),
      ]),
    );

    await expectLater(
      client.research(prompt: 'question', model: 'model'),
      throwsA(isA<StateError>()),
    );
  });
}

class _SignedInAuth extends OpenRouterAuth {
  @override
  Future<String?> get apiKey async => 'test-key';
}

class _RecordingClient extends http.BaseClient {
  _RecordingClient(this.responses);

  final List<http.StreamedResponse> responses;
  final List<Map<String, dynamic>> bodies = [];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final body = await (request as http.Request).finalize().bytesToString();
    bodies.add((jsonDecode(body) as Map).cast<String, dynamic>());
    return responses.removeAt(0);
  }
}

http.StreamedResponse _jsonResponse(Map<String, dynamic> body) =>
    http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode(body))),
      200,
      headers: const {'content-type': 'application/json'},
    );

http.StreamedResponse _streamResponse(List<String> chunks) =>
    http.StreamedResponse(
      Stream.fromIterable(chunks.map(utf8.encode)),
      200,
      headers: const {'content-type': 'text/event-stream'},
    );
