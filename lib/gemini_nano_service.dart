import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'ai_service.dart';

/// Status names the native AICore bridge reports. They mirror
/// `FeatureStatus` in the ML Kit GenAI Prompt SDK.
const _statusReady = 'ready';
const _statusDownloadable = 'downloadable';
const _statusDownloading = 'downloading';
const _statusUnsupported = 'unsupported';
const _statusTemporarilyUnavailable = 'temporarilyUnavailable';

AiAvailability _toAvailability(String? status) => switch (status) {
  _statusReady => AiAvailability.ready,
  _statusDownloadable => AiAvailability.downloadable,
  _statusDownloading => AiAvailability.downloading,
  _statusUnsupported => AiAvailability.unsupported,
  _statusTemporarilyUnavailable => AiAvailability.temporarilyUnavailable,
  _ => AiAvailability.temporarilyUnavailable,
};

/// One in-flight streamed answer, waiting for its terminal native event.
class _PendingStream {
  _PendingStream(this.id);

  final int id;
  final controller = StreamController<String>();
  bool settled = false;
}

/// Bible AI backed by Gemini Nano, which runs entirely on-device through the
/// AICore system service. It needs no account or API key and keeps working
/// offline, but it only exists on AICore-capable hardware (Pixel 9 and newer),
/// so callers must be ready for [AiAvailability.unsupported] and fall back to
/// another provider.
class GeminiNanoBibleModel
    implements
        BibleAiModel,
        ReasoningBibleAiModel,
        FinishReasonBibleAiModel {
  GeminiNanoBibleModel();

  static const _methods = MethodChannel('bible/gemini_nano');
  static const _events = EventChannel('bible/gemini_nano/stream');

  final Map<int, _PendingStream> _pending = {};
  StreamSubscription<dynamic>? _eventSubscription;
  int _nextRequestId = 0;
  String? _modelName;

  /// The model AICore resolved for this device, e.g. `gemini-nano`. Only known
  /// once [availability] has run.
  String? get modelName => _modelName;

  @override
  set onReasoning(void Function(String delta)? callback) =>
      _onReasoning = callback;
  void Function(String delta)? _onReasoning;

  @override
  set onFinishReason(void Function(String reason)? callback) =>
      _onFinishReason = callback;
  void Function(String reason)? _onFinishReason;

  bool get _isAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  @override
  Future<AiAvailability> availability() async {
    if (!_isAndroid) return AiAvailability.unsupported;
    try {
      final response = await _methods.invokeMethod<Object?>('checkAvailability');
      if (response is Map) {
        _modelName = response['modelName'] as String?;
        return _toAvailability(response['status'] as String?);
      }
      return _toAvailability(response as String?);
    } on MissingPluginException {
      // Web shells, and Android builds without the native bridge.
      return AiAvailability.unsupported;
    } on PlatformException {
      return AiAvailability.temporarilyUnavailable;
    }
  }

  @override
  Future<void> downloadModel() async {
    if (!_isAndroid) {
      throw UnsupportedError('Gemini Nano is only available on Android.');
    }
    // prepareFeature resolves once AICore finished fetching the model.
    await _methods.invokeMethod<void>('prepareFeature');
  }

  @override
  Future<String> generate(String prompt) async {
    if (!_isAndroid) {
      throw UnsupportedError('Gemini Nano is only available on Android.');
    }
    return await _methods.invokeMethod<String>('generate', {
          'prompt': prompt,
        }) ??
        '';
  }

  @override
  Stream<String> generateStream(String prompt) {
    final request = _PendingStream(_nextRequestId++);
    _pending[request.id] = request;
    _listen();
    request.controller.onCancel = () {
      // A cancelled subscription means the caller stopped generating.
      if (_pending.remove(request.id) == null) return;
      request.settled = true;
      unawaited(_invokeCancel());
    };
    if (!_isAndroid) {
      _settle(
        request,
        PlatformException(
          code: _statusUnsupported,
          message: 'Gemini Nano is only available on Android.',
        ),
      );
      return request.controller.stream;
    }
    _methods
        .invokeMethod<void>('generateStream', {
          'id': request.id,
          'prompt': prompt,
        })
        .then<void>((_) {}, onError: (Object error, StackTrace stack) {
          _settle(request, _asPlatformException(error));
        });
    return request.controller.stream;
  }

  /// Stops the running answer, mirroring how the controller stops an OpenRouter
  /// stream when the user presses stop.
  Future<void> cancel() async {
    final requests = _pending.values.toList(growable: false);
    _pending.clear();
    for (final request in requests) {
      _finish(request);
    }
    await _invokeCancel();
  }

  /// Subscribes once for the whole model instance: the native side keeps a
  /// single AICore job alive and tags every event with its request id.
  void _listen() {
    _eventSubscription ??= _events.receiveBroadcastStream().listen(
      _onEvent,
      onError: (Object error, StackTrace stack) {
        final requests = _pending.values.toList(growable: false);
        _pending.clear();
        for (final request in requests) {
          _settle(request, _asPlatformException(error));
        }
      },
      cancelOnError: true,
    );
  }

  void _onEvent(dynamic raw) {
    if (raw is! Map) return;
    final id = raw['id'];
    if (id is! int) return;
    final request = _pending[id];
    if (request == null || request.settled) return;
    final text = raw['text'];
    switch (raw['type']) {
      case 'text':
        if (text is String && text.isNotEmpty) request.controller.add(text);
        return;
      case 'thought':
        if (text is String && text.isNotEmpty) _onReasoning?.call(text);
        return;
      case 'done':
        final reason = raw['finishReason'];
        if (reason is String) _onFinishReason?.call(reason);
        _finish(request);
        return;
      case 'error':
        _settle(request, _fromEvent(raw));
        return;
      default:
        return;
    }
  }

  void _finish(_PendingStream request) {
    if (request.settled) return;
    request.settled = true;
    _pending.remove(request.id);
    if (!request.controller.isClosed) request.controller.close();
  }

  void _settle(_PendingStream request, PlatformException error) {
    if (request.settled) return;
    request.settled = true;
    _pending.remove(request.id);
    if (request.controller.isClosed) return;
    request.controller
      ..addError(error)
      ..close();
  }

  Future<void> _invokeCancel() async {
    if (!_isAndroid) return;
    try {
      await _methods.invokeMethod<void>('cancel');
    } on MissingPluginException {
      // Nothing to cancel without the native bridge.
    }
  }

  PlatformException _fromEvent(Map<dynamic, dynamic> event) {
    final message = event['message'];
    final code = event['code'];
    return PlatformException(
      code: code is String ? code : 'gemini_nano',
      message: message is String ? message : null,
    );
  }

  PlatformException _asPlatformException(Object error) {
    if (error is PlatformException) return error;
    final message = error.toString();
    return PlatformException(
      code: 'gemini_nano',
      message: message.length > 400 ? message.substring(0, 400) : message,
    );
  }
}
