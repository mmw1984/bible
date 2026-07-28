import 'dart:convert';
import 'dart:ui';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'ai_service.dart';
import 'app_theme.dart';
import 'bible_data.dart';

MarkdownStyleSheet _chatMarkdownStyle(
  Color foreground,
  Color secondary,
  Color border,
) => MarkdownStyleSheet(
  p: TextStyle(color: foreground, height: 1.58, fontSize: 14),
  h1: TextStyle(
    color: foreground,
    fontSize: 21,
    height: 1.35,
    fontWeight: FontWeight.w600,
  ),
  h2: TextStyle(
    color: foreground,
    fontSize: 18,
    height: 1.4,
    fontWeight: FontWeight.w600,
  ),
  h3: TextStyle(
    color: foreground,
    fontSize: 16,
    height: 1.45,
    fontWeight: FontWeight.w600,
  ),
  strong: TextStyle(color: foreground, fontWeight: FontWeight.w700),
  em: TextStyle(color: foreground, fontStyle: FontStyle.italic),
  a: TextStyle(
    color: Color.alphaBlend(const Color(0xFF3F78A8), foreground),
    decoration: TextDecoration.underline,
  ),
  listBullet: TextStyle(color: secondary),
  blockquote: TextStyle(color: secondary, height: 1.55),
  blockquoteDecoration: BoxDecoration(
    color: border.withValues(alpha: .3),
    border: Border(left: BorderSide(color: border, width: 3)),
    borderRadius: BorderRadius.circular(10),
  ),
  code: TextStyle(
    color: foreground,
    backgroundColor: border.withValues(alpha: .35),
    fontFamily: 'monospace',
    fontSize: 12,
  ),
  codeblockPadding: const EdgeInsets.all(13),
  codeblockDecoration: BoxDecoration(
    color: border.withValues(alpha: .22),
    border: Border.all(color: border),
    borderRadius: BorderRadius.circular(12),
  ),
  horizontalRuleDecoration: BoxDecoration(
    border: Border(top: BorderSide(color: border)),
  ),
);

class AiChatPage extends StatefulWidget {
  const AiChatPage({
    super.key,
    this.initialQuestion,
    this.scriptureContext,
    this.scriptureReference,
    this.autoSend = false,
  });

  final String? initialQuestion;
  final String? scriptureContext;
  final String? scriptureReference;
  final bool autoSend;

  @override
  State<AiChatPage> createState() => _AiChatPageState();
}

class _AiChatPageState extends State<AiChatPage> with WidgetsBindingObserver {
  static const launchChannel = MethodChannel('bible/ai_chat_launch');
  final controller = BibleAiController.instance;
  final repository = BibleRepository();
  final input = TextEditingController();
  final scroll = ScrollController();
  String? error;
  bool sentInitial = false;
  String? pendingQuestion;
  String pendingKind = 'chat';
  bool initialBottomPositioned = false;
  String? launchQuestion;
  String? launchScriptureContext;
  String? launchScriptureReference;
  bool launchAutoSend = false;
  bool controllerReadyForLaunch = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    launchQuestion = widget.initialQuestion;
    launchScriptureContext = widget.scriptureContext;
    launchScriptureReference = widget.scriptureReference;
    launchAutoSend = widget.autoSend;
    input.text = launchAutoSend ? '' : launchQuestion ?? '';
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      launchChannel.setMethodCallHandler((call) async {
        if (call.method == 'deliverLaunchPayload' && call.arguments is String) {
          _applyLaunchPayload(call.arguments as String);
        }
      });
      _loadNativeLaunchPayload();
    }
    controller.addListener(_changed);
    controller.initialize().then((_) {
      controllerReadyForLaunch = true;
      _maybeSendInitial();
      _positionAtInitialBottom();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    launchChannel.setMethodCallHandler(null);
    controller.removeListener(_changed);
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
      // This page can also be hosted by the main activity.
    } on MissingPluginException {
      // No native launch handoff exists outside the dedicated activity.
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
        launchAutoSend = payload['autoSend'] == true;
        sentInitial = false;
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

  void _changed() {
    if (!mounted) return;
    setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToLatest(animated: true);
    });
    _maybeSendInitial();
    if (!initialBottomPositioned) _positionAtInitialBottom();
    if (controller.openRouterSignedIn && pendingQuestion != null) {
      final question = pendingQuestion!;
      final kind = pendingKind;
      pendingQuestion = null;
      _send(supplied: question, kind: kind);
    }
  }

  Future<void> _positionAtInitialBottom() async {
    if (initialBottomPositioned) return;
    for (var frame = 0; frame < 3; frame++) {
      await WidgetsBinding.instance.endOfFrame;
      if (!mounted) return;
    }
    if (!scroll.hasClients) return;
    scroll.jumpTo(scroll.position.maxScrollExtent);
    initialBottomPositioned = true;
  }

  void _scrollToLatest({bool animated = false}) {
    if (!scroll.hasClients) return;
    final target = scroll.position.maxScrollExtent;
    if (animated) {
      scroll.animateTo(
        target,
        duration: const Duration(milliseconds: 320),
        curve: Curves.easeOutCubic,
      );
    } else {
      scroll.jumpTo(target);
    }
  }

  void _maybeSendInitial() {
    if (!mounted ||
        sentInitial ||
        !launchAutoSend ||
        !controllerReadyForLaunch ||
        controller.generating) {
      return;
    }
    final question = launchQuestion;
    if (question == null || question.isEmpty) return;
    sentInitial = true;
    _send(supplied: question, kind: 'explanation');
  }

  Future<void> _send({String? supplied, String kind = 'chat'}) async {
    final question = (supplied ?? input.text).trim();
    if (question.isEmpty || controller.generating) return;
    if (controller.requiresLogin) {
      pendingQuestion = question;
      pendingKind = kind;
      try {
        await controller.beginOpenRouterLogin();
      } catch (_) {
        if (mounted) setState(() => error = '未能開啟 OpenRouter 登入。');
      }
      return;
    }
    if (!controller.isReady) return;
    input.clear();
    setState(() => error = null);
    try {
      await controller.send(
        question: question,
        repository: repository,
        scriptureContext: launchScriptureContext,
        scriptureReference: launchScriptureReference,
        kind: kind,
      );
    } catch (exception) {
      if (mounted) {
        setState(() {
          input.text = question;
          input.selection = TextSelection.collapsed(offset: input.text.length);
          error = '未能完成回覆，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final background = colors.canvas;
    final panel = colors.surfaceRaised;
    final foreground = colors.ink;
    final secondary = colors.muted;
    final border = colors.line;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Colors.transparent,
        statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        systemNavigationBarIconBrightness: dark
            ? Brightness.light
            : Brightness.dark,
        systemNavigationBarContrastEnforced: false,
      ),
      child: Scaffold(
        extendBody: true,
        backgroundColor: background,
        body: Stack(
          children: [
            Column(
              children: [
                Padding(
                  padding: EdgeInsets.fromLTRB(
                    12,
                    MediaQuery.paddingOf(context).top + 4,
                    12,
                    8,
                  ),
                  child: Row(
                    children: [
                      _RoundIcon(
                        icon: Icons.arrow_back_rounded,
                        label: '返回',
                        onTap: () async {
                          if (!await Navigator.maybePop(context)) {
                            await SystemNavigator.pop();
                          }
                        },
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Bible AI',
                              style: TextStyle(
                                color: foreground,
                                fontSize: 19,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            AnimatedSwitcher(
                              duration: const Duration(milliseconds: 280),
                              switchInCurve: Curves.easeOutCubic,
                              transitionBuilder: (child, animation) =>
                                  FadeTransition(
                                    opacity: animation,
                                    child: SlideTransition(
                                      position: Tween<Offset>(
                                        begin: const Offset(0, .25),
                                        end: Offset.zero,
                                      ).animate(animation),
                                      child: child,
                                    ),
                                  ),
                              child: Text(
                                '${controller.modelId} · Cloud',
                                key: ValueKey(controller.modelId),
                                style: TextStyle(
                                  color: secondary,
                                  fontSize: 10,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      _RoundIcon(
                        icon: Icons.settings_outlined,
                        label: 'AI 設定',
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute<void>(
                            builder: (_) => const AiSettingsPage(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      _RoundIcon(
                        icon: Icons.delete_outline_rounded,
                        label: '清除對話',
                        onTap: _confirmClear,
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 360),
                    reverseDuration: const Duration(milliseconds: 220),
                    switchInCurve: Curves.easeOutCubic,
                    switchOutCurve: Curves.easeInCubic,
                    transitionBuilder: (child, animation) => FadeTransition(
                      opacity: animation,
                      child: ScaleTransition(
                        alignment: Alignment.center,
                        scale: Tween(begin: .985, end: 1.0).animate(animation),
                        child: child,
                      ),
                    ),
                    child: KeyedSubtree(
                      key: ValueKey(
                        '${controller.provider.name}-${controller.availability.name}',
                      ),
                      child: _body(panel, foreground, secondary, border),
                    ),
                  ),
                ),
              ],
            ),
            if (controller.provider == AiProvider.openRouter ||
                controller.isReady)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: _composer(panel, foreground, secondary, border),
              ),
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: MediaQuery.paddingOf(context).top + 6,
              child: IgnorePointer(
                child: ClipRect(
                  child: ShaderMask(
                    blendMode: BlendMode.dstIn,
                    shaderCallback: (bounds) => const LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Colors.white, Colors.white, Colors.transparent],
                      stops: [0, .62, 1],
                    ).createShader(bounds),
                    child: BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
                      child: ColoredBox(
                        color: background.withValues(alpha: .58),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _body(Color panel, Color foreground, Color secondary, Color border) {
    final messages = controller.messages;
    return ListView.builder(
      controller: scroll,
      padding: EdgeInsets.fromLTRB(
        12,
        18,
        12,
        MediaQuery.paddingOf(context).bottom + 118,
      ),
      itemCount: messages.length + (controller.generating ? 1 : 0),
      itemBuilder: (_, index) {
        if (index == messages.length) {
          return Align(
            alignment: Alignment.centerLeft,
            child: _ThinkingIndicator(panel: panel, foreground: foreground),
          );
        }
        final message = messages[index];
        final user = message.role == 'user';
        final latestAssistant =
            !user &&
            !messages.skip(index + 1).any((item) => item.role == 'assistant');
        return TweenAnimationBuilder<double>(
          key: ValueKey('${message.role}-$index'),
          tween: Tween(begin: 0, end: 1),
          duration: const Duration(milliseconds: 420),
          curve: Curves.easeOutCubic,
          builder: (context, value, child) => Opacity(
            opacity: value,
            child: Transform.translate(
              offset: Offset(0, 12 * (1 - value)),
              child: child,
            ),
          ),
          child: Align(
            alignment: user ? Alignment.centerRight : Alignment.centerLeft,
            child: Container(
              constraints: const BoxConstraints(maxWidth: 560),
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
              decoration: BoxDecoration(
                color: user ? foreground : panel,
                border: Border.all(color: user ? foreground : border),
                borderRadius: BorderRadius.circular(18),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (user)
                    SelectableText(
                      message.text,
                      style: TextStyle(
                        color: AppColors.of(context).canvas,
                        height: 1.55,
                      ),
                    )
                  else ...[
                    if (message.reasoning?.trim().isNotEmpty == true) ...[
                      _ReasoningDisclosure(
                        text: message.reasoning!.trim(),
                        foreground: foreground,
                        secondary: secondary,
                        border: border,
                      ),
                      const SizedBox(height: 11),
                    ],
                    MarkdownBody(
                      data: message.text,
                      selectable: true,
                      softLineBreak: true,
                      onTapLink: (text, href, title) {
                        final uri = href == null ? null : Uri.tryParse(href);
                        if (uri != null) launchUrl(uri);
                      },
                      styleSheet: _chatMarkdownStyle(
                        foreground,
                        secondary,
                        border,
                      ),
                    ),
                    if (message.incomplete) ...[
                      const SizedBox(height: 10),
                      Text(
                        '回覆可能尚未完整，可使用下方重新生成。',
                        style: TextStyle(color: secondary, fontSize: 10),
                      ),
                    ],
                  ],
                  if (!user) ...[
                    const SizedBox(height: 9),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _MessageAction(
                          icon: Icons.copy_rounded,
                          label: '複製',
                          onTap: () => Clipboard.setData(
                            ClipboardData(text: message.text),
                          ),
                        ),
                        if (latestAssistant) ...[
                          const SizedBox(width: 6),
                          _MessageAction(
                            icon: Icons.refresh_rounded,
                            label: '重新生成',
                            onTap: () => controller.regenerateLast(
                              repository: repository,
                              scriptureContext: widget.scriptureContext,
                              scriptureReference: widget.scriptureReference,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _composer(
    Color panel,
    Color foreground,
    Color secondary,
    Color border,
  ) => ClipRect(
    child: Stack(
      alignment: Alignment.bottomCenter,
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: ShaderMask(
              blendMode: BlendMode.dstIn,
              shaderCallback: (bounds) => const LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.transparent, Colors.white, Colors.white],
                stops: [0, .48, 1],
              ).createShader(bounds),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(10, 18, 10, 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: TextField(
                    controller: input,
                    minLines: 1,
                    maxLines: 5,
                    textInputAction: TextInputAction.newline,
                    style: TextStyle(color: foreground),
                    decoration: InputDecoration(
                      hintText: widget.scriptureReference == null
                          ? '輸入你的問題'
                          : '追問 ${widget.scriptureReference}',
                      hintStyle: TextStyle(color: secondary),
                      filled: true,
                      fillColor: panel.withValues(alpha: .68),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 13,
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderSide: BorderSide(color: border),
                        borderRadius: BorderRadius.circular(18),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderSide: BorderSide(color: foreground),
                        borderRadius: BorderRadius.circular(18),
                      ),
                      errorText: error,
                    ),
                    onSubmitted: (_) => _send(),
                  ),
                ),
                const SizedBox(width: 8),
                _RoundIcon(
                  icon: Icons.arrow_upward_rounded,
                  label: '送出',
                  onTap: _send,
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );

  Future<void> _confirmClear() async {
    final colors = AppColors.of(context);
    final clear = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: true,
      barrierLabel: '取消',
      barrierColor: Colors.black.withValues(alpha: .72),
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (context, animation, secondaryAnimation) => Center(
        child: Material(
          color: Colors.transparent,
          child: Container(
            width: 320,
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: colors.surfaceRaised,
              border: Border.all(color: colors.line),
              borderRadius: BorderRadius.circular(22),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '清除對話？',
                  style: TextStyle(
                    color: colors.ink,
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '這會清除 transcript 與 memory.md。',
                  style: TextStyle(color: colors.muted, height: 1.5),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: _AppActionButton(
                        label: '取消',
                        inverted: false,
                        onTap: () => Navigator.pop(context, false),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _AppActionButton(
                        label: '清除',
                        onTap: () => Navigator.pop(context, true),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(
            opacity: animation,
            child: ScaleTransition(
              scale: Tween(begin: .97, end: 1.0).animate(animation),
              child: child,
            ),
          ),
    );
    if (clear == true) await controller.clearConversation();
  }
}

class _ThinkingIndicator extends StatefulWidget {
  const _ThinkingIndicator({required this.panel, required this.foreground});

  final Color panel;
  final Color foreground;

  @override
  State<_ThinkingIndicator> createState() => _ThinkingIndicatorState();
}

class _ReasoningDisclosure extends StatefulWidget {
  const _ReasoningDisclosure({
    required this.text,
    required this.foreground,
    required this.secondary,
    required this.border,
  });

  final String text;
  final Color foreground;
  final Color secondary;
  final Color border;

  @override
  State<_ReasoningDisclosure> createState() => _ReasoningDisclosureState();
}

class _ReasoningDisclosureState extends State<_ReasoningDisclosure> {
  bool expanded = true;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      InkWell(
        onTap: () => setState(() => expanded = !expanded),
        borderRadius: BorderRadius.circular(9),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedRotation(
                turns: expanded ? .25 : 0,
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOutCubic,
                child: Icon(
                  Icons.chevron_right_rounded,
                  color: widget.secondary,
                  size: 16,
                ),
              ),
              const SizedBox(width: 5),
              Text(
                '思考過程',
                style: TextStyle(
                  color: widget.secondary,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
      AnimatedSize(
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
        alignment: Alignment.topCenter,
        child: expanded
            ? Padding(
                padding: const EdgeInsets.fromLTRB(0, 5, 0, 4),
                child: MarkdownBody(
                  data: widget.text,
                  selectable: true,
                  softLineBreak: true,
                  styleSheet:
                      _chatMarkdownStyle(
                        widget.foreground,
                        widget.secondary,
                        widget.border,
                      ).copyWith(
                        p: TextStyle(
                          color: widget.secondary,
                          fontSize: 12,
                          height: 1.5,
                        ),
                      ),
                ),
              )
            : const SizedBox.shrink(),
      ),
    ],
  );
}

class _ThinkingIndicatorState extends State<_ThinkingIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1050),
  )..repeat();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(begin: 0, end: 1),
    duration: const Duration(milliseconds: 320),
    curve: Curves.easeOutCubic,
    builder: (context, value, child) => Opacity(
      opacity: value,
      child: Transform.translate(
        offset: Offset(0, 8 * (1 - value)),
        child: child,
      ),
    ),
    child: Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 12),
      decoration: BoxDecoration(
        color: widget.panel,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedBuilder(
            animation: controller,
            builder: (context, _) => Row(
              children: List.generate(3, (index) {
                final phase = (controller.value - index * .16) % 1;
                final lift =
                    math.sin(phase * math.pi).clamp(0, 1).toDouble() * 3;
                return Transform.translate(
                  offset: Offset(0, -lift),
                  child: Container(
                    width: 4,
                    height: 4,
                    margin: EdgeInsets.only(right: index == 2 ? 0 : 4),
                    decoration: BoxDecoration(
                      color: widget.foreground.withValues(
                        alpha: .35 + lift / 5,
                      ),
                      shape: BoxShape.circle,
                    ),
                  ),
                );
              }),
            ),
          ),
        ],
      ),
    ),
  );
}

class _MessageAction extends StatelessWidget {
  const _MessageAction({
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
    return Tooltip(
      message: label,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(9),
        child: Container(
          width: 30,
          height: 28,
          decoration: BoxDecoration(
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(9),
          ),
          child: Icon(icon, size: 15, color: colors.muted),
        ),
      ),
    );
  }
}

class _RoundIcon extends StatelessWidget {
  const _RoundIcon({
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
    return IconButton(
      tooltip: label,
      onPressed: onTap,
      style: IconButton.styleFrom(
        backgroundColor: colors.surface.withValues(alpha: .48),
        side: BorderSide(color: colors.line),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      icon: Icon(icon, color: colors.ink, size: 20),
    );
  }
}

class AiSettingsPage extends StatefulWidget {
  const AiSettingsPage({super.key});

  @override
  State<AiSettingsPage> createState() => _AiSettingsPageState();
}

class _AiSettingsPageState extends State<AiSettingsPage> {
  final controller = BibleAiController.instance;
  late final TextEditingController model = TextEditingController(
    text: controller.modelId,
  );

  @override
  void initState() {
    super.initState();
    controller.addListener(_changed);
  }

  @override
  void dispose() {
    controller.removeListener(_changed);
    model.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final background = colors.canvas;
    final foreground = colors.ink;
    final secondary = colors.muted;
    final panel = colors.surfaceRaised;
    final border = colors.line;
    return Scaffold(
      extendBody: true,
      backgroundColor: background,
      body: ListView(
        padding: EdgeInsets.fromLTRB(
          12,
          MediaQuery.paddingOf(context).top + 4,
          12,
          MediaQuery.paddingOf(context).bottom + 28,
        ),
        children: [
          Row(
            children: [
              _RoundIcon(
                icon: Icons.arrow_back_rounded,
                label: '返回',
                onTap: () => Navigator.pop(context),
              ),
              const SizedBox(width: 12),
              Text(
                'AI 設定',
                style: TextStyle(
                  color: foreground,
                  fontSize: 21,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 28),
          const _SettingsLabel('模型供應商'),
          const SizedBox(height: 9),
          Container(
            height: 48,
            decoration: BoxDecoration(
              color: foreground,
              borderRadius: BorderRadius.circular(16),
            ),
            alignment: Alignment.center,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.cloud_outlined, color: background, size: 17),
                const SizedBox(width: 7),
                Text(
                  'OpenRouter',
                  style: TextStyle(
                    color: background,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          ...[
            const _SettingsLabel('OpenRouter 登入'),
            const SizedBox(height: 9),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: panel,
                border: Border.all(color: border),
                borderRadius: BorderRadius.circular(18),
              ),
              child: Row(
                children: [
                  Icon(
                    controller.openRouterSignedIn
                        ? Icons.verified_user_outlined
                        : Icons.login_rounded,
                    color: foreground,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      controller.openRouterSignedIn ? '已安全連接' : '尚未登入',
                      style: TextStyle(color: foreground),
                    ),
                  ),
                  _SmallSettingsAction(
                    onTap: controller.openRouterSignedIn
                        ? controller.signOutOpenRouter
                        : controller.beginOpenRouterLogin,
                    label: controller.openRouterSignedIn ? '登出' : '登入',
                  ),
                ],
              ),
            ),
            if (!controller.openRouterSignedIn &&
                controller.openRouterAuthError != null) ...[
              const SizedBox(height: 9),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(13),
                decoration: BoxDecoration(
                  color: const Color(0xFF1D1515),
                  border: Border.all(color: const Color(0xFF523333)),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: SelectableText(
                  controller.openRouterAuthError!,
                  style: const TextStyle(
                    color: Color(0xFFD6A5A5),
                    fontSize: 11,
                    height: 1.45,
                  ),
                ),
              ),
            ],
            const SizedBox(height: 24),
            const _SettingsLabel('模型 ID'),
            const SizedBox(height: 9),
            TextField(
              controller: model,
              style: TextStyle(color: foreground),
              decoration: InputDecoration(
                hintText: 'openrouter/free',
                helperText:
                    '預設使用 Free Models Router，也可輸入其他 OpenRouter model ID。',
                helperStyle: TextStyle(color: secondary),
                filled: true,
                fillColor: panel,
                enabledBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: border),
                  borderRadius: BorderRadius.circular(16),
                ),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: foreground),
                  borderRadius: BorderRadius.circular(16),
                ),
                suffixIcon: IconButton(
                  tooltip: '儲存模型',
                  onPressed: () => controller.setModel(model.text),
                  icon: const Icon(Icons.check_rounded),
                ),
              ),
              onSubmitted: controller.setModel,
            ),
          ],
        ],
      ),
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

class _SmallSettingsAction extends StatelessWidget {
  const _SmallSettingsAction({required this.label, required this.onTap});
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(11),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(color: colors.line),
          borderRadius: BorderRadius.circular(11),
        ),
        child: Text(label, style: TextStyle(color: colors.ink, fontSize: 11)),
      ),
    );
  }
}

class _AppActionButton extends StatelessWidget {
  const _AppActionButton({
    required this.label,
    required this.onTap,
    this.inverted = true,
  });
  final String label;
  final VoidCallback? onTap;
  final bool inverted;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Opacity(
      opacity: onTap == null ? .48 : 1,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          height: 42,
          padding: const EdgeInsets.symmetric(horizontal: 15),
          decoration: BoxDecoration(
            color: inverted ? colors.ink : Colors.transparent,
            border: Border.all(color: inverted ? colors.ink : colors.line),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: TextStyle(
                  color: inverted ? colors.canvas : colors.ink,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
