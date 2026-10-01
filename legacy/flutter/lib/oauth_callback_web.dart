import 'dart:async';

import 'openrouter_oauth.dart';

class OAuthCallbackSession {
  const OAuthCallbackSession();

  String get callbackUrl => openRouterWebCallbackUri(Uri.base).toString();

  Future<Uri> get result => Completer<Uri>().future;
  Future<void> close() async {}
}

Future<OAuthCallbackSession> startOAuthCallback() async =>
    const OAuthCallbackSession();
