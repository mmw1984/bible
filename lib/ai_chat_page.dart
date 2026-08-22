import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:url_launcher/url_launcher.dart';

import 'ai_service.dart';
import 'app_markdown.dart';
import 'app_navbar.dart';
import 'app_settings.dart';
import 'app_theme.dart';
import 'app_ui.dart';
import 'bible_data.dart';
import 'localization.dart';

class AiChatPage extends StatefulWidget {
  const AiChatPage({
    super.key,
    this.initialQuestion,
    this.scriptureContext,
    this.scriptureReference,
    this.scriptureAttachment,
    this.autoSend = false,
    this.aiController,
    this.settingsController,
    this.embedded = false,
  });

  final String? initialQuestion;
  final String? scriptureContext;
  final String? scriptureReference;
  final String? scriptureAttachment;
  final bool autoSend;
  final BibleAiController? aiController;
  final AppSettingsController? settingsController;

  /// When hosted inside the bottom-navigation shell: no back button and the
  /// composer dock clears the floating bar.
  final bool embedded;

  @override
  State<AiChatPage> createState() => _AiChatPageState();
}

class _AiChatPageState extends State<AiChatPage> with WidgetsBindingObserver {
  static const launchChannel = MethodChannel('bible/ai_chat_launch');

  late final BibleAiController controller =
      widget.aiController ?? BibleAiController.instance;
  late final AppSettingsController settings =
      widget.settingsController ?? AppSettingsController.instance;
  final BibleRepository repository = BibleRepository();
  final TextEditingController input = TextEditingController();
  final ScrollController scroll = ScrollController();

  String? error;
  String? pendingQuestion;
  String pendingKind = 'chat';
  String? launchQuestion;
  String? launchScriptureContext;
  String? launchScriptureReference;
  String? launchScriptureAttachment;
  bool launchAutoSend = false;
  bool contextAttached = false;
  bool sentInitial = false;
  bool controllerReadyForLaunch = false;
  bool initialBottomPositioned = false;
  bool followLatest = true;
  bool scrollScheduled = false;
  double composerDockHeight = 116;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    launchQuestion = widget.initialQuestion;
    launchScriptureContext = widget.scriptureContext;
    launchScriptureReference = widget.scriptureReference;
    launchScriptureAttachment = widget.scriptureAttachment;
    launchAutoSend = widget.autoSend;
    contextAttached = launchScriptureContext?.trim().isNotEmpty == true;
    input.text = launchAutoSend ? '' : launchQuestion ?? '';
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      launchChannel.setMethodCallHandler((call) async {
        if (call.method == 'deliverLaunchPayload' && call.arguments is String) {
          _applyLaunchPayload(call.arguments as String);
        }
      });
      _loadNativeLaunchPayload();
    }
    controller.addListener(_controllerChanged);
    settings.addListener(_settingsChanged);
    controller.setResponseLocale(settings.state.locale);
    controller.initialize().then((_) {
      if (!mounted) return;
      controllerReadyForLaunch = true;
      _maybeSendInitial();
      _scheduleScroll(animated: false);
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    launchChannel.setMethodCallHandler(null);
    controller.removeListener(_controllerChanged);
    settings.removeListener(_settingsChanged);
    input.dispose();
    scroll.dispose();
    super.dispose();
  }

  Future<void> _loadNativeLaunchPayload() async {
    try {
      final payload = await launchChannel.invokeMethod<String>(
        'getLaunchPayload',
      );
      if (payload != null) _applyLaunchPayload(payload);
    } on PlatformException {
      // The page can also be hosted by the main Flutter activity.
    } on MissingPluginException {
      // Web and widget tests do not expose the Android launch handoff.
    }
  }

  void _applyLaunchPayload(String source) {
    if (!mounted) return;
    try {
      final payload = (jsonDecode(source) as Map).cast<String, dynamic>();
      final question = payload['initialQuestion'] as String?;
      setState(() {
        launchQuestion = question;
        launchScriptureContext = payload['scriptureContext'] as String?;
        launchScriptureReference = payload['scriptureReference'] as String?;
        launchScriptureAttachment = payload['scriptureAttachment'] as String?;
        launchAutoSend = payload['autoSend'] == true;
        contextAttached = launchScriptureContext?.trim().isNotEmpty == true;
        sentInitial = false;
        followLatest = true;
        if (!launchAutoSend) input.text = question ?? '';
      });
      _maybeSendInitial();
    } on FormatException {
      // Keep route-provided values if an external payload is malformed.
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      controller.refreshOpenRouterLogin();
    }
  }

  @override
  void didChangeMetrics() {
    _scheduleScroll(animated: false);
  }

  void _settingsChanged() {
    controller.setResponseLocale(settings.state.locale);
    if (mounted) setState(() {});
  }

  void _controllerChanged() {
    if (!mounted) return;
    setState(() {});
    if (followLatest) _scheduleScroll(animated: false);
    _maybeSendInitial();
    if (controller.openRouterSignedIn && pendingQuestion != null) {
      final question = pendingQuestion!;
      final kind = pendingKind;
      pendingQuestion = null;
      _send(supplied: question, kind: kind);
    }
  }

  void _scheduleScroll({required bool animated}) {
    if (scrollScheduled) return;
    scrollScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      scrollScheduled = false;
      if (!mounted || !scroll.hasClients || !followLatest) return;
      final target = scroll.position.minScrollExtent;
      if (!initialBottomPositioned || !animated) {
        scroll.jumpTo(target);
        initialBottomPositioned = true;
      } else if ((scroll.offset - target).abs() > 1) {
        scroll.animateTo(
          target,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  void _maybeSendInitial() {
    if (!mounted ||
        sentInitial ||
        !launchAutoSend ||
        !controllerReadyForLaunch ||
        controller.generating) {
      return;
    }
    final question = launchQuestion?.trim();
    if (question == null || question.isEmpty) return;
    sentInitial = true;
    _send(supplied: question, kind: 'explanation');
  }

  Future<void> _send({String? supplied, String kind = 'chat'}) async {
    final question = (supplied ?? input.text).trim();
    if (question.isEmpty || controller.generating) return;
    followLatest = true;
    if (!controller.initialized) {
      await controller.initialize();
      if (!controller.initialized) {
        if (mounted) setState(() => error = context.l10n.aiInitializingFailed);
        return;
      }
    }
    if (controller.requiresLogin) {
      pendingQuestion = question;
      pendingKind = kind;
      await _connectOpenRouter();
      return;
    }
    if (!controller.isReady) return;
    input.clear();
    setState(() => error = null);
    try {
      await controller.send(
        question: question,
        repository: repository,
        scriptureContext: contextAttached ? launchScriptureContext : null,
        scriptureReference: contextAttached ? launchScriptureReference : null,
        kind: kind,
      );
    } catch (_) {
      if (!mounted) return;
      setState(() {
        input.text = question;
        input.selection = TextSelection.collapsed(offset: input.text.length);
        error = controller.generationError ?? context.l10n.answerFailed;
      });
    }
  }

  Future<void> _connectOpenRouter() async {
    setState(() => error = null);
    try {
      await controller.beginOpenRouterLogin();
    } catch (_) {
      if (mounted) setState(() => error = context.l10n.openRouterLoginFailed);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    final dockOffset = keyboard > 0 || !widget.embedded
        ? keyboard
        : appNavBottomClearance(context);
    final scene = ColoredBox(
      color: colors.canvas,
        child: Stack(
          children: [
            Positioned.fill(
              child: _body(colors, bottomPadding: composerDockHeight + dockOffset),
            ),
            Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: _ChatHeader(
              controller: controller,
              showBack: !widget.embedded,
              onBack: _close,
              onSettings: () => Navigator.push(
                context,
                AppPageRoute<void>(
                  builder: (_) => AiSettingsPage(
                    aiController: controller,
                    settingsController: settings,
                  ),
                ),
              ),
              onClear: _confirmClear,
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: AnimatedPadding(
              duration: const Duration(milliseconds: 220),
              curve: Curves.easeOutCubic,
              padding: EdgeInsets.only(bottom: dockOffset),
              child: _SizeObserver(
                onSizeChanged: (size) {
                  if (!mounted ||
                      (composerDockHeight - size.height).abs() < .5) {
                    return;
                  }
                  setState(() => composerDockHeight = size.height);
                  if (followLatest) _scheduleScroll(animated: false);
                },
                child: _ChatComposerDock(
                  floating: !followLatest && controller.messages.isNotEmpty
                      ? AppGlyphButton(
                          glyph: AppGlyph.chevronDown,
                          label: context.l10n.goToLatest,
                          onTap: _scrollToLatest,
                          size: 42,
                          fill: colors.surfaceRaised,
                        )
                      : null,
                  child: _ChatComposer(
                    controller: input,
                    error: error,
                    scriptureReference: contextAttached
                        ? launchScriptureReference
                        : null,
                    scriptureAttachment: contextAttached
                        ? launchScriptureAttachment ?? launchScriptureReference
                        : null,
                    autofocus: !launchAutoSend,
                    generating: controller.generating,
                    canSend: controller.isReady || controller.requiresLogin,
                    onSend: _send,
                    onStop: controller.stopGeneration,
                    onRemoveAttachment: _removeScriptureContext,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
    return AppSettingsScope(
      controller: settings,
      child: AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          systemNavigationBarColor: colors.canvas,
          statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
          systemNavigationBarIconBrightness: dark
              ? Brightness.light
              : Brightness.dark,
          systemStatusBarContrastEnforced: false,
          systemNavigationBarContrastEnforced: false,
        ),
        child: scene,
      ),
    );
  }

  Widget _body(AppColors colors, {required double bottomPadding}) {
    if (!controller.initialized) {
      return _InitializationState(
        failed: controller.initializationError != null,
        onRetry: controller.initialize,
      );
    }
    return Column(
      children: [
        if (controller.requiresLogin)
          Padding(
            padding: EdgeInsets.only(
              top: MediaQuery.paddingOf(context).top + 64,
            ),
            child: _OpenRouterNotice(
              onConnect: _connectOpenRouter,
              error: controller.openRouterAuthError,
            ),
          ),
        Expanded(
          child: controller.messages.isEmpty
              ? _EmptyConversation(scriptureReference: launchScriptureReference)
              : NotificationListener<ScrollNotification>(
                  onNotification: (notification) {
                    if (notification is ScrollUpdateNotification ||
                        notification is UserScrollNotification) {
                      final next =
                          notification.metrics.pixels -
                              notification.metrics.minScrollExtent <
                          96;
                      if (followLatest != next) {
                        setState(() => followLatest = next);
                      }
                    }
                    return false;
                  },
                  child: _MessageList(
                    controller: scroll,
                    messages: controller.messages,
                    generating: controller.generating,
                    bottomPadding: bottomPadding,
                    onCopy: (message) =>
                        Clipboard.setData(ClipboardData(text: message.text)),
                    onRegenerate: _regenerate,
                  ),
                ),
        ),
      ],
    );
  }

  Future<void> _regenerate() async {
    if (controller.generating) return;
    followLatest = true;
    setState(() => error = null);
    try {
      await controller.regenerateLast(
        repository: repository,
        scriptureContext: contextAttached ? launchScriptureContext : null,
        scriptureReference: contextAttached ? launchScriptureReference : null,
      );
    } catch (_) {
      if (mounted) {
        setState(
          () => error =
              controller.generationError ?? context.l10n.regenerateFailed,
        );
      }
    }
  }

  void _removeScriptureContext() => setState(() => contextAttached = false);

  void _scrollToLatest() {
    if (!scroll.hasClients) return;
    setState(() => followLatest = true);
    scroll.animateTo(
      scroll.position.minScrollExtent,
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
    );
  }

  Future<void> _close() async {
    if (!await Navigator.maybePop(context)) await SystemNavigator.pop();
  }

  Future<void> _confirmClear() async {
    if (controller.messages.isEmpty || controller.generating) return;
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    final clear = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: true,
      barrierLabel: context.l10n.cancel,
      barrierColor: Colors.black.withValues(alpha: .7),
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (context, animation, secondaryAnimation) => Center(
        child: Container(
          width: 320,
          margin: const EdgeInsets.all(18),
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: colors.surface,
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(radii.surface),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                context.l10n.clearConversationQuestion,
                style: TextStyle(
                  color: colors.ink,
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                context.l10n.clearConversationBody,
                style: TextStyle(color: colors.muted, height: 1.45),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: _ActionButton(
                      label: context.l10n.cancel,
                      onTap: () => Navigator.pop(context, false),
                      emphasized: false,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _ActionButton(
                      label: context.l10n.clear,
                      onTap: () => Navigator.pop(context, true),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(opacity: animation, child: child),
    );
    if (clear == true) await controller.clearConversation();
  }
}

class _ChatHeader extends StatelessWidget {
  const _ChatHeader({
    required this.controller,
    required this.showBack,
    required this.onBack,
    required this.onSettings,
    required this.onClear,
  });

  final BibleAiController controller;
  final bool showBack;
  final VoidCallback onBack;
  final VoidCallback onSettings;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 4, 10, 8),
        child: Row(
          children: [
            Expanded(
              child: Row(
                key: const ValueKey('ai-header-left-group'),
                children: [
                  if (showBack) ...[
                    _ChatHeaderButton(
                      icon: LucideIcons.arrowLeft,
                      label: context.l10n.back,
                      onTap: onBack,
                    ),
                    const SizedBox(width: 4),
                  ],
                  const Spacer(),
                ],
              ),
            ),
            const SizedBox(width: 6),
            _ChatHeaderButton(
              icon: LucideIcons.settings,
              label: context.l10n.settings,
              onTap: onSettings,
            ),
          ],
        ),
      ),
    );
  }
}

class _ChatHeaderButton extends StatelessWidget {
  const _ChatHeaderButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppControlSurface(
      child: AppTap(
        label: label,
        onTap: onTap,
        child: SizedBox(
          width: 40,
          height: 40,
          child: Center(child: Icon(icon, color: colors.muted, size: 19)),
        ),
      ),
    );
  }
}

class _MessageList extends StatelessWidget {
  const _MessageList({
    required this.controller,
    required this.messages,
    required this.generating,
    required this.bottomPadding,
    required this.onCopy,
    required this.onRegenerate,
  });

  final ScrollController controller;
  final List<AiMessage> messages;
  final bool generating;
  final double bottomPadding;
  final ValueChanged<AiMessage> onCopy;
  final VoidCallback onRegenerate;

  @override
  Widget build(BuildContext context) {
    final maxWidth = math.min(MediaQuery.sizeOf(context).width, 760.0);
    final showThinking =
        generating && (messages.isEmpty || messages.last.role != 'assistant');
    final topSpacerIndex = messages.length + (showThinking ? 1 : 0);
    return ListView.builder(
      key: const ValueKey('ai-message-list'),
      controller: controller,
      reverse: true,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: EdgeInsets.fromLTRB(
        math.max(12, (MediaQuery.sizeOf(context).width - maxWidth) / 2 + 12),
        16,
        math.max(12, (MediaQuery.sizeOf(context).width - maxWidth) / 2 + 12),
        math.max(16, bottomPadding),
      ),
      itemCount: topSpacerIndex + 1,
      itemBuilder: (context, index) {
        if (index == topSpacerIndex) {
          return SizedBox(height: MediaQuery.paddingOf(context).top + 64);
        }
        if (showThinking && index == 0) return const _ThinkingIndicator();
        final offset = index - (showThinking ? 1 : 0);
        final messageIndex = messages.length - 1 - offset;
        final message = messages[messageIndex];
        final user = message.role == 'user';
        final latestAssistant =
            !user &&
            !messages
                .skip(messageIndex + 1)
                .any((item) => item.role == 'assistant');
        return _MessageRow(
          messageKey: ValueKey('ai-message-$messageIndex-${message.role}'),
          message: message,
          user: user,
          streaming: generating && latestAssistant,
          showRegenerate: latestAssistant && !generating,
          onCopy: () => onCopy(message),
          onRegenerate: onRegenerate,
        );
      },
    );
  }
}

class _MessageRow extends StatelessWidget {
  const _MessageRow({
    required this.messageKey,
    required this.message,
    required this.user,
    required this.streaming,
    required this.showRegenerate,
    required this.onCopy,
    required this.onRegenerate,
  });

  final Key messageKey;
  final AiMessage message;
  final bool user;
  final bool streaming;
  final bool showRegenerate;
  final VoidCallback onCopy;
  final VoidCallback onRegenerate;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    final reasoning = sanitizeReasoningForDisplay(message.reasoning ?? '');
    if (user) {
      final style = TextStyle(
        color: colors.canvas,
        fontFamily: 'OpenRunde',
        fontSize: 14,
        height: 1.4,
      );
      final maxWidth = math.min(MediaQuery.sizeOf(context).width * .76, 430.0);
      final textPainter = TextPainter(
        text: TextSpan(text: message.text, style: style),
        textDirection: Directionality.of(context),
        textScaler: MediaQuery.textScalerOf(context),
        maxLines: 1,
      )..layout();
      final availableWidth = maxWidth - 24;
      final fitsOneLine =
          !message.text.contains('\n') && textPainter.width <= availableWidth;
      final textWidth = fitsOneLine
          ? math.min(
              availableWidth,
              math.max(8.0, textPainter.width.ceilToDouble() + 8),
            )
          : availableWidth;
      return Align(
        alignment: Alignment.centerRight,
        child: Container(
          key: messageKey,
          margin: const EdgeInsets.only(bottom: 18),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: colors.ink,
            borderRadius: BorderRadius.circular(radii.surface),
          ),
          child: SizedBox(
            width: textWidth,
            child: SelectionArea(
              child: Text(
                message.text,
                style: style,
                softWrap: !fitsOneLine,
                maxLines: fitsOneLine ? 1 : null,
              ),
            ),
          ),
        ),
      );
    }
    return Align(
      key: messageKey,
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(right: 22, bottom: 18),
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 7),
        decoration: BoxDecoration(
          color: colors.surface,
          border: Border.all(color: colors.line.withValues(alpha: .75)),
          borderRadius: BorderRadius.circular(radii.surface),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (reasoning.isNotEmpty) ...[
              _ReasoningDisclosure(text: reasoning, streaming: streaming),
              const SizedBox(height: 10),
            ],
            if (message.text.isNotEmpty)
              Semantics(
                liveRegion: streaming,
                child: AppMarkdown(
                  data: message.text,
                  foreground: colors.ink,
                  secondary: colors.muted,
                  border: colors.line,
                  onLink: launchUrl,
                ),
              ),
            if (message.incomplete) ...[
              const SizedBox(height: 3),
              Text(
                context.l10n.answerIncomplete,
                style: TextStyle(color: colors.muted, fontSize: 10),
              ),
            ],
            const SizedBox(height: 4),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _InlineAction(
                  icon: LucideIcons.copy,
                  label: context.l10n.copyAnswer,
                  onTap: onCopy,
                ),
                if (showRegenerate) ...[
                  const SizedBox(width: 4),
                  _InlineAction(
                    icon: LucideIcons.refreshCw,
                    label: context.l10n.regenerate,
                    onTap: onRegenerate,
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ReasoningDisclosure extends StatefulWidget {
  const _ReasoningDisclosure({required this.text, required this.streaming});

  final String text;
  final bool streaming;

  @override
  State<_ReasoningDisclosure> createState() => _ReasoningDisclosureState();
}

class _ReasoningDisclosureState extends State<_ReasoningDisclosure> {
  late bool expanded = widget.streaming;

  @override
  void didUpdateWidget(covariant _ReasoningDisclosure oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.streaming && widget.streaming) expanded = true;
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppControlSurface(
          radius: AppRadii.of(context).compact,
          color: colors.surfaceRaised.withValues(alpha: .64),
          child: AppTap(
            label: expanded
                ? context.l10n.collapseThinking
                : context.l10n.expandThinking,
            onTap: () => setState(() => expanded = !expanded),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 4, 9, 4),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AnimatedRotation(
                    turns: expanded ? .25 : 0,
                    duration: const Duration(milliseconds: 180),
                    child: Icon(
                      LucideIcons.chevronRight,
                      color: colors.muted,
                      size: 15,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    widget.streaming
                        ? context.l10n.thinking
                        : context.l10n.thinkingContent,
                    style: TextStyle(
                      color: colors.muted,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (widget.streaming) ...[
                    const SizedBox(width: 7),
                    const _ThinkingDots(compact: true),
                  ],
                ],
              ),
            ),
          ),
        ),
        AnimatedSize(
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
          alignment: Alignment.topCenter,
          child: expanded
              ? Padding(
                  padding: const EdgeInsets.only(top: 5),
                  child: AppMarkdown(
                    data: widget.text,
                    foreground: colors.muted,
                    secondary: colors.muted,
                    border: colors.line,
                    compact: true,
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ],
    );
  }
}

class _ChatComposerDock extends StatelessWidget {
  const _ChatComposerDock({required this.child, this.floating});

  final Widget child;
  final Widget? floating;

  @override
  Widget build(BuildContext context) {
    final floatingHeight = floating == null ? 0.0 : 48.0;
    return Stack(
      children: [
        Padding(
          padding: EdgeInsets.only(top: floatingHeight),
          child: child,
        ),
        if (floating != null) Positioned(top: 0, right: 14, child: floating!),
      ],
    );
  }
}

class _SizeObserver extends SingleChildRenderObjectWidget {
  const _SizeObserver({required this.onSizeChanged, required super.child});

  final ValueChanged<Size> onSizeChanged;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _SizeObserverRenderObject(onSizeChanged);

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _SizeObserverRenderObject renderObject,
  ) {
    renderObject.onSizeChanged = onSizeChanged;
  }
}

class _SizeObserverRenderObject extends RenderProxyBox {
  _SizeObserverRenderObject(this.onSizeChanged);

  ValueChanged<Size> onSizeChanged;
  Size? _reportedSize;

  @override
  void performLayout() {
    super.performLayout();
    if (_reportedSize == size) return;
    _reportedSize = size;
    WidgetsBinding.instance.addPostFrameCallback((_) => onSizeChanged(size));
  }
}

class _ChatComposer extends StatelessWidget {
  const _ChatComposer({
    required this.controller,
    required this.error,
    required this.scriptureReference,
    required this.scriptureAttachment,
    required this.autofocus,
    required this.generating,
    required this.canSend,
    required this.onSend,
    required this.onStop,
    required this.onRemoveAttachment,
  });

  final TextEditingController controller;
  final String? error;
  final String? scriptureReference;
  final String? scriptureAttachment;
  final bool autofocus;
  final bool generating;
  final bool canSend;
  final VoidCallback onSend;
  final VoidCallback onStop;
  final VoidCallback onRemoveAttachment;

  @override
  Widget build(BuildContext context) {
    final radii = AppRadii.of(context);
    final bottomInset = MediaQuery.viewPaddingOf(context).bottom;
    final leftCorner = math.max(radii.bottomLeft, bottomInset);
    final rightCorner = math.max(radii.bottomRight, bottomInset);
    final leftPadding = math.max(10.0, leftCorner * .24);
    final rightPadding = math.max(10.0, rightCorner * .24);
    final inputRadius = math
        .max(radii.control, leftCorner * .34)
        .clamp(10.0, 24.0)
        .toDouble();
    final sendRadius = math
        .max(radii.control, rightCorner * .34)
        .clamp(10.0, 24.0)
        .toDouble();
    return SafeArea(
      top: false,
      minimum: EdgeInsets.fromLTRB(
        leftPadding,
        0,
        rightPadding,
        math.max(leftPadding, rightPadding),
      ),
      child: Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: AppTextInput(
                    key: const ValueKey('ai-composer-input'),
                    controller: controller,
                    radius: inputRadius,
                    autofocus: autofocus,
                    header: scriptureAttachment == null
                        ? null
                        : _ScriptureAttachment(
                            reference:
                                scriptureReference ??
                                context.l10n.explainScripture,
                            text: scriptureAttachment!,
                            onRemove: onRemoveAttachment,
                          ),
                    hint: scriptureReference == null
                        ? context.l10n.questionHint
                        : context.l10n.followUpHint(scriptureReference!),
                    minLines: 1,
                    maxLines: 5,
                    textInputAction: TextInputAction.newline,
                    error: error,
                  ),
                ),
                const SizedBox(width: 8),
                _ComposerAction(
                  generating: generating,
                  enabled: generating || canSend,
                  radius: sendRadius,
                  onTap: generating ? onStop : onSend,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ScriptureAttachment extends StatelessWidget {
  const _ScriptureAttachment({
    required this.reference,
    required this.text,
    required this.onRemove,
  });

  final String reference;
  final String text;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final preview = text.replaceAll(RegExp(r'\s+'), ' ').trim();
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 360),
      child: Container(
        key: const ValueKey('ai-scripture-attachment'),
        padding: const EdgeInsets.fromLTRB(9, 7, 5, 7),
        decoration: BoxDecoration(
          color: colors.surface,
          border: Border.all(color: colors.line),
          borderRadius: BorderRadius.circular(AppRadii.of(context).compact),
        ),
        child: Row(
          children: [
            AppGlyphView(AppGlyph.book, color: colors.muted, size: 17),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    reference,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: colors.ink,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (preview.isNotEmpty && preview != reference)
                    Text(
                      preview,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.muted, fontSize: 10),
                    ),
                ],
              ),
            ),
            AppControlSurface(
              radius: AppRadii.of(context).compact,
              color: colors.surfaceRaised.withValues(alpha: .7),
              child: AppTap(
                label: context.l10n.removeScriptureAttachment,
                onTap: onRemove,
                child: SizedBox(
                  width: 32,
                  height: 32,
                  child: Icon(LucideIcons.x, color: colors.muted, size: 15),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ComposerAction extends StatelessWidget {
  const _ComposerAction({
    required this.generating,
    required this.enabled,
    required this.radius,
    required this.onTap,
  });

  final bool generating;
  final bool enabled;
  final double radius;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppControlSurface(
      key: ValueKey(generating ? 'ai-stop-control' : 'ai-send-control'),
      radius: radius.clamp(10.0, 24.0).toDouble(),
      color: colors.surfaceRaised.withValues(alpha: .86),
      borderColor: colors.ink.withValues(alpha: .72),
      child: AppTap(
        label: generating ? context.l10n.stop : context.l10n.send,
        enabled: enabled,
        onTap: onTap,
        child: Container(
          key: ValueKey(generating ? 'ai-stop-button' : 'ai-send-button'),
          width: 48,
          height: 48,
          alignment: Alignment.center,
          child: AppGlyphView(
            generating ? AppGlyph.stop : AppGlyph.send,
            color: colors.ink,
            size: generating ? 14 : 19,
          ),
        ),
      ),
    );
  }
}

class _ThinkingIndicator extends StatefulWidget {
  const _ThinkingIndicator();

  @override
  State<_ThinkingIndicator> createState() => _ThinkingIndicatorState();
}

class _ThinkingIndicatorState extends State<_ThinkingIndicator> {
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const _ThinkingDots(),
          const SizedBox(width: 9),
          Text(
            context.l10n.thinking,
            style: TextStyle(color: colors.muted, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

class _ThinkingDots extends StatefulWidget {
  const _ThinkingDots({this.compact = false});

  final bool compact;

  @override
  State<_ThinkingDots> createState() => _ThinkingDotsState();
}

class _ThinkingDotsState extends State<_ThinkingDots>
    with SingleTickerProviderStateMixin {
  late final AnimationController animation = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1050),
  )..repeat();

  @override
  void dispose() {
    animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final dotSize = widget.compact ? 4.0 : 5.0;
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) => SizedBox(
        width: widget.compact ? 22 : 28,
        height: 14,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(3, (index) {
            final phase = (animation.value - index * .16) % 1;
            final pulse = (math.sin(phase * math.pi * 2) + 1) / 2;
            return Transform.translate(
              offset: Offset(0, -2 * pulse),
              child: Opacity(
                key: ValueKey('thinking-dot-$index'),
                opacity: .28 + pulse * .72,
                child: Container(
                  width: dotSize,
                  height: dotSize,
                  decoration: BoxDecoration(
                    color: colors.muted,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

class _InitializationState extends StatelessWidget {
  const _InitializationState({required this.failed, required this.onRetry});

  final bool failed;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: failed
            ? Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(LucideIcons.cloudOff, color: colors.muted, size: 28),
                  const SizedBox(height: 12),
                  Text(
                    context.l10n.aiInitializingFailed,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: colors.ink,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 14),
                  _ActionButton(
                    label: context.l10n.retry,
                    icon: LucideIcons.refreshCw,
                    onTap: onRetry,
                  ),
                ],
              )
            : AppSpinner(color: colors.ink),
      ),
    );
  }
}

class _EmptyConversation extends StatelessWidget {
  const _EmptyConversation({required this.scriptureReference});

  final String? scriptureReference;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(LucideIcons.bookOpenCheck, color: colors.faint, size: 26),
            const SizedBox(height: 12),
            Text(
              scriptureReference == null
                  ? context.l10n.noConversation
                  : scriptureReference!,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.muted,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OpenRouterNotice extends StatelessWidget {
  const _OpenRouterNotice({required this.onConnect, this.error});

  final VoidCallback onConnect;
  final String? error;

  @override
  Widget build(BuildContext context) => _NoticeBar(
    key: const ValueKey('openrouter-login-notice'),
    icon: LucideIcons.cloud,
    title: context.l10n.openRouterNotConnected,
    detail: error ?? context.l10n.openRouterConnectBody,
    action: context.l10n.login,
    onAction: onConnect,
  );
}

class _NoticeBar extends StatelessWidget {
  const _NoticeBar({
    super.key,
    required this.icon,
    required this.title,
    required this.detail,
    this.action,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String detail;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    return Container(
      margin: const EdgeInsets.fromLTRB(10, 10, 10, 0),
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(radii.control),
      ),
      child: Row(
        children: [
          Icon(icon, color: colors.ink, size: 17),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: colors.ink,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 1),
                Text(
                  detail,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: colors.muted,
                    fontSize: 10,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (action != null) _SmallAction(label: action!, onTap: onAction),
        ],
      ),
    );
  }
}

class _IconAction extends StatelessWidget {
  const _IconAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppButton(
      label: label,
      onTap: onTap,
      radius: AppRadii.of(context).compact,
      color: colors.surfaceRaised.withValues(alpha: .72),
      child: SizedBox(
        width: 40,
        height: 40,
        child: Center(child: Icon(icon, color: colors.ink, size: 19)),
      ),
    );
  }
}

class _InlineAction extends StatelessWidget {
  const _InlineAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppButton(
      label: label,
      onTap: onTap,
      radius: AppRadii.of(context).compact,
      color: colors.surfaceRaised.withValues(alpha: .66),
      child: SizedBox(
        width: 34,
        height: 32,
        child: Center(child: Icon(icon, color: colors.muted, size: 15)),
      ),
    );
  }
}

class AiSettingsPage extends StatefulWidget {
  const AiSettingsPage({super.key, this.aiController, this.settingsController});

  final BibleAiController? aiController;
  final AppSettingsController? settingsController;

  @override
  State<AiSettingsPage> createState() => _AiSettingsPageState();
}

class _AiSettingsPageState extends State<AiSettingsPage> {
  late final BibleAiController controller =
      widget.aiController ?? BibleAiController.instance;
  late final AppSettingsController settings =
      widget.settingsController ?? AppSettingsController.instance;
  late final TextEditingController model = TextEditingController(
    text: controller.modelId,
  );

  @override
  void initState() {
    super.initState();
    controller.addListener(_changed);
    settings.addListener(_changed);
    controller.setResponseLocale(settings.state.locale);
  }

  @override
  void dispose() {
    controller.removeListener(_changed);
    settings.removeListener(_changed);
    model.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final content = ColoredBox(
      color: colors.canvas,
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Container(
              constraints: const BoxConstraints(minHeight: 58),
              padding: const EdgeInsets.fromLTRB(10, 5, 12, 7),
              decoration: BoxDecoration(
                border: Border(bottom: BorderSide(color: colors.line)),
              ),
              child: Row(
                children: [
                  _IconAction(
                    icon: LucideIcons.arrowLeft,
                    label: context.l10n.back,
                    onTap: () => Navigator.pop(context),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    context.l10n.settings,
                    style: TextStyle(
                      color: colors.ink,
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: EdgeInsets.fromLTRB(
                  12,
                  22,
                  12,
                  MediaQuery.paddingOf(context).bottom + 24,
                ),
                children: [
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 680),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          _AppearanceSettings(settings: settings),
                          const SizedBox(height: 28),
                          _OpenRouterSettings(
                            key: const ValueKey('router-settings'),
                            controller: controller,
                            model: model,
                          ),
                          const SizedBox(height: 28),
                          _DangerZone(controller: controller),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
    return AppSettingsScope(controller: settings, child: content);
  }
}

class _AppearanceSettings extends StatelessWidget {
  const _AppearanceSettings({required this.settings});

  final AppSettingsController settings;

  @override
  Widget build(BuildContext context) {
    final state = settings.state;
    final colors = AppColors.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SettingsLabel(context.l10n.appearance),
        const SizedBox(height: 8),
        AppSegmented<ThemeMode>(
          choices: [
            AppChoice(value: ThemeMode.light, label: context.l10n.light),
            AppChoice(value: ThemeMode.dark, label: context.l10n.dark),
          ],
          selected: state.themeMode,
          onChanged: settings.setThemeMode,
        ),
        const SizedBox(height: 22),
        _SettingsLabel(context.l10n.navbarStyle),
        const SizedBox(height: 8),
        AppSegmented<AppNavBarStyle>(
          choices: [
            AppChoice(
              value: AppNavBarStyle.material,
              label: context.l10n.navbarStyleSolid,
            ),
            AppChoice(
              value: AppNavBarStyle.materialBlur,
              label: context.l10n.navbarStyleBlur,
            ),
          ],
          selected: state.navbarStyle,
          onChanged: settings.setNavbarStyle,
        ),
        const SizedBox(height: 22),
        _SettingsLabel(context.l10n.appLanguage),
        const SizedBox(height: 8),
        AppSegmented<AppLocale>(
          choices: [
            AppChoice(value: AppLocale.zhHant, label: '中文'),
            AppChoice(value: AppLocale.en, label: 'English'),
          ],
          selected: state.locale,
          onChanged: settings.setLocale,
        ),
        const SizedBox(height: 22),
        _SettingsLabel(context.l10n.layoutSection),
        const SizedBox(height: 8),
        _SettingsLabel(context.l10n.navbarVisibility),
        const SizedBox(height: 8),
        AppSegmented<bool>(
          choices: [
            AppChoice(value: true, label: context.l10n.optionShow),
            AppChoice(value: false, label: context.l10n.optionHide),
          ],
          selected: state.showNavbar,
          onChanged: settings.setShowNavbar,
        ),
        const SizedBox(height: 16),
        _SettingsLabel(context.l10n.devotionVisibility),
        const SizedBox(height: 8),
        AppSegmented<bool>(
          choices: [
            AppChoice(value: true, label: context.l10n.optionShow),
            AppChoice(value: false, label: context.l10n.optionHide),
          ],
          selected: state.showDevotion,
          onChanged: settings.setShowDevotion,
        ),
        const SizedBox(height: 24),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              context.l10n.appVersion,
              style: TextStyle(color: colors.faint, fontSize: 12),
            ),
            Text(
              '1.6.0',
              style: TextStyle(color: colors.muted, fontSize: 12),
            ),
          ],
        ),
      ],
    );
  }
}

class _OpenRouterSettings extends StatelessWidget {
  const _OpenRouterSettings({
    super.key,
    required this.controller,
    required this.model,
  });

  final BibleAiController controller;
  final TextEditingController model;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SettingsLabel(context.l10n.openRouterConnection),
        const SizedBox(height: 8),
        Container(
          key: const ValueKey('openrouter-connection-status'),
          height: 42,
          padding: const EdgeInsets.symmetric(horizontal: 11),
          decoration: BoxDecoration(
            color: colors.surface,
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(radii.control),
          ),
          child: Row(
            children: [
              Icon(
                controller.openRouterSignedIn
                    ? LucideIcons.circleCheckBig
                    : LucideIcons.logIn,
                color: colors.ink,
                size: 17,
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Text(
                  controller.openRouterSignedIn
                      ? context.l10n.connectedSecurely
                      : context.l10n.notSignedIn,
                  style: TextStyle(color: colors.ink, fontSize: 12),
                ),
              ),
              _SmallAction(
                onTap: controller.openRouterSignedIn
                    ? controller.signOutOpenRouter
                    : controller.beginOpenRouterLogin,
                label: controller.openRouterSignedIn
                    ? context.l10n.logout
                    : context.l10n.login,
              ),
            ],
          ),
        ),
        if (!controller.openRouterSignedIn &&
            controller.openRouterAuthError != null) ...[
          const SizedBox(height: 9),
          _ErrorPanel(message: controller.openRouterAuthError!),
        ],
        const SizedBox(height: 22),
        _SettingsLabel(context.l10n.modelId),
        const SizedBox(height: 8),
        AppTextInput(
          controller: model,
          hint: 'openrouter/free',
          helper: context.l10n.modelHelper,
          trailing: _IconAction(
            icon: LucideIcons.save,
            label: context.l10n.saveModel,
            onTap: () => controller.setModel(model.text),
          ),
          onSubmitted: controller.setModel,
        ),
      ],
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(11),
      decoration: BoxDecoration(
        color: colors.dangerSurface,
        border: Border.all(color: colors.danger.withValues(alpha: .55)),
        borderRadius: BorderRadius.circular(radii.control),
      ),
      child: SelectableText(
        message,
        style: TextStyle(
          color: colors.danger,
          fontFamily: 'OpenRunde',
          fontSize: 11,
          height: 1.45,
        ),
      ),
    );
  }
}

class _DangerZone extends StatelessWidget {
  const _DangerZone({required this.controller});

  final BibleAiController controller;

  Future<void> _confirmClear(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(ctx.l10n.clearConversationQuestion),
        content: Text(ctx.l10n.clearConversationBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(ctx.l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(ctx.l10n.clear)),
        ],
      ),
    );
    if (confirmed == true) await controller.clearConversation();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final enabled = controller.messages.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SettingsLabel(context.l10n.clearConversation),
        const SizedBox(height: 8),
        Semantics(
          label: context.l10n.clearConversation,
          button: true,
          child: Material(
            color: colors.surfaceRaised.withValues(alpha: .55),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadii.of(context).control),
              side: BorderSide(color: colors.line),
            ),
            child: ListTile(
              title: Text(context.l10n.clearConversation, style: TextStyle(color: enabled ? colors.danger : colors.muted, fontSize: 14, fontWeight: FontWeight.w600)),
              subtitle: Text(context.l10n.clearConversationBody, style: TextStyle(color: colors.faint, fontSize: 11)),
              trailing: Icon(LucideIcons.trash2, size: 18, color: enabled ? colors.danger : colors.faint),
              enabled: enabled,
              onTap: enabled ? () => _confirmClear(context) : null,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.of(context).control)),
            ),
          ),
        ),
      ],
    );
  }
}

class _SettingsLabel extends StatelessWidget {
  const _SettingsLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) => Text(
    label,
    style: TextStyle(
      color: AppColors.of(context).muted,
      fontSize: 10,
      fontWeight: FontWeight.w600,
    ),
  );
}

class _SmallAction extends StatelessWidget {
  const _SmallAction({required this.label, required this.onTap});

  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    return AppButton(
      label: label,
      onTap: onTap,
      enabled: onTap != null,
      radius: radii.compact,
      color: colors.surfaceRaised.withValues(alpha: .68),
      child: Container(
        constraints: const BoxConstraints(minHeight: 32),
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
        alignment: Alignment.center,
        child: Text(
          label,
          maxLines: 1,
          style: TextStyle(color: colors.ink, fontSize: 11),
        ),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.label,
    required this.onTap,
    this.icon,
    this.emphasized = true,
  });

  final String label;
  final VoidCallback onTap;
  final IconData? icon;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    return AppButton(
      label: label,
      onTap: onTap,
      radius: radii.control,
      emphasized: emphasized,
      color: emphasized
          ? colors.surfaceRaised.withValues(alpha: .86)
          : colors.surface.withValues(alpha: .68),
      borderColor: emphasized
          ? colors.ink.withValues(alpha: .74)
          : colors.line.withValues(alpha: .72),
      child: Container(
        constraints: const BoxConstraints(minHeight: 40),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        alignment: Alignment.center,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 15, color: colors.ink),
              const SizedBox(width: 7),
            ],
            Text(
              label,
              style: TextStyle(
                color: colors.ink,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
