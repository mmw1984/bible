import 'dart:async';
import 'dart:convert';
import 'dart:io';

class OAuthCallbackSession {
  OAuthCallbackSession(this._server, this._completer);
  final HttpServer _server;
  final Completer<Uri> _completer;

  String get callbackUrl => 'http://localhost:${_server.port}/callback';
  Future<Uri> get result => _completer.future;
  Future<void> close() => _server.close(force: true);
}

Future<OAuthCallbackSession> startOAuthCallback() async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  final completer = Completer<Uri>();
  late final OAuthCallbackSession session;
  session = OAuthCallbackSession(server, completer);
  server.listen((request) async {
    if (request.uri.path != '/callback') {
      request.response.statusCode = HttpStatus.notFound;
      await request.response.close();
      return;
    }
    final appCallback = Uri(
      scheme: 'bible',
      host: 'openrouter',
      path: '/callback',
      queryParameters: request.uri.queryParameters,
    );
    request.response
      ..statusCode = HttpStatus.ok
      ..headers.contentType = ContentType.html
      ..write(
        '''<!doctype html><meta name="viewport" content="width=device-width">
<body style="background:#090909;color:#f1efe9;font:16px sans-serif;display:grid;place-items:center;height:100vh;margin:0">
<div style="text-align:center"><strong>Authorization received</strong><p style="color:#96958f">Returning to Bible AI…</p></div>
<script>window.location.href=${jsonEncode(appCallback.toString())}</script></body>''',
      );
    await request.response.close();
    if (!completer.isCompleted) {
      completer.complete(
        Uri.parse('http://localhost:${server.port}${request.uri}'),
      );
    }
    await session.close();
  });
  return session;
}
