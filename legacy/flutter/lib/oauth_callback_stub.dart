class OAuthCallbackSession {
  const OAuthCallbackSession();
  String get callbackUrl => 'bible://openrouter/callback';
  Future<Uri> get result => Future<Uri>.error(
    UnsupportedError('Loopback OAuth is unavailable on this platform.'),
  );
  Future<void> close() async {}
}

Future<OAuthCallbackSession> startOAuthCallback() async =>
    const OAuthCallbackSession();
