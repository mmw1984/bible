import 'package:web/web.dart' as web;

void clearOAuthCallbackUrl() {
  final clean = Uri.base.replace(queryParameters: const {}).toString();
  web.window.history.replaceState(null, '', clean);
}
