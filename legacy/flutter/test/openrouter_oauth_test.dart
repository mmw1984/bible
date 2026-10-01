import 'package:bible/openrouter_oauth.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('creates the RFC 7636 S256 challenge', () {
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';

    expect(
      createOpenRouterPkceChallenge(verifier),
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    );
  });

  group('OpenRouter callback classification', () {
    final location = Uri.parse('http://127.0.0.1:54123/');

    test('accepts a marked web callback', () {
      expect(
        isOpenRouterCallback(
          Uri.parse(
            'http://127.0.0.1:54123/?oauth=openrouter&code=returned-code',
          ),
          isWeb: true,
          webLocation: location,
        ),
        isTrue,
      );
    });

    test('accepts a same-page code if the provider drops the marker', () {
      expect(
        isOpenRouterCallback(
          Uri.parse('http://127.0.0.1:54123/?code=returned-code'),
          isWeb: true,
          webLocation: location,
        ),
        isTrue,
      );
    });

    test('rejects codes from another origin or path', () {
      expect(
        isOpenRouterCallback(
          Uri.parse('https://example.com/?code=returned-code'),
          isWeb: true,
          webLocation: location,
        ),
        isFalse,
      );
      expect(
        isOpenRouterCallback(
          Uri.parse('http://127.0.0.1:54123/other?code=returned-code'),
          isWeb: true,
          webLocation: location,
        ),
        isFalse,
      );
    });

    test('accepts only the exact native callback path', () {
      expect(
        isOpenRouterCallback(
          Uri.parse('bible://openrouter/callback?code=returned-code'),
          isWeb: false,
          webLocation: location,
        ),
        isTrue,
      );
      expect(
        isOpenRouterCallback(
          Uri.parse('bible://openrouter/other?code=returned-code'),
          isWeb: false,
          webLocation: location,
        ),
        isFalse,
      );
    });
  });

  test('uses the canonical production callback origin', () {
    expect(
      openRouterWebCallbackUri(
        Uri.parse('http://bible.mmw1984.com/ignored?old=value'),
      ).toString(),
      'https://bible.mmw1984.com/?oauth=openrouter',
    );
  });

  test('builds a local callback without an empty fragment', () {
    expect(
      openRouterWebCallbackUri(
        Uri.parse('http://127.0.0.1:54123/chapter?old=value#reader'),
      ).toString(),
      'http://127.0.0.1:54123/?oauth=openrouter',
    );
  });
}
