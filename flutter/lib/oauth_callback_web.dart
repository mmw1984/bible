import 'dart:async';

class OAuthCallbackSession {
  const OAuthCallbackSession();

  String get callbackUrl {
    final base = Uri.base.host == 'bible.mmw1984.com'
        ? Uri.https('bible.mmw1984.com', '/')
        : Uri.base.replace(path: '/', fragment: '');
    return base
        .replace(queryParameters: const {'oauth': 'openrouter'})
        .toString();
  }

  Future<Uri> get result => Completer<Uri>().future;
  Future<void> close() async {}
}

Future<OAuthCallbackSession> startOAuthCallback() async =>
    const OAuthCallbackSession();
