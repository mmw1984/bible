import 'dart:convert';

import 'package:crypto/crypto.dart';

const openRouterPkceMethod = 'S256';

Uri openRouterWebCallbackUri(Uri location) {
  final base = location.host == 'bible.mmw1984.com'
      ? Uri.https('bible.mmw1984.com', '/')
      : Uri(
          scheme: location.scheme,
          host: location.host,
          port: location.hasPort ? location.port : null,
          path: '/',
        );
  return base.replace(queryParameters: const {'oauth': 'openrouter'});
}

bool isOpenRouterCallback(
  Uri uri, {
  required bool isWeb,
  required Uri webLocation,
}) {
  final appLink =
      uri.scheme == 'bible' &&
      uri.host == 'openrouter' &&
      uri.path == '/callback';
  final loopback =
      uri.scheme == 'http' &&
      (uri.host == 'localhost' || uri.host == '127.0.0.1') &&
      uri.path == '/callback';
  if (appLink || loopback) return true;
  if (!isWeb || (uri.scheme != 'http' && uri.scheme != 'https')) return false;

  final expected = openRouterWebCallbackUri(webLocation);
  final samePage =
      uri.scheme == expected.scheme &&
      uri.host == expected.host &&
      uri.port == expected.port &&
      (uri.path.isEmpty ? '/' : uri.path) == expected.path;
  if (!samePage) return false;

  return uri.queryParameters['oauth'] == 'openrouter' ||
      uri.queryParameters.containsKey('code');
}

String createOpenRouterPkceChallenge(String verifier) => base64Url
    .encode(sha256.convert(utf8.encode(verifier)).bytes)
    .replaceAll('=', '');
