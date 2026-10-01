import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

import 'app_settings.dart';
import 'app_theme.dart';
import 'localization.dart';

enum AppFrostedEdge { top, bottom }

/// Height of the frosted fade strip, measured past the safe-area inset.
const kFrostedEdgeExtent = 48.0;

/// Frosted fade along a screen edge with a *progressive* blur.
///
/// Rather than one uniform sigma, 10 stacked [BackdropFilter]s with
/// decreasing sigma and increasing extent are layered. The strongest band
/// sits right at the screen edge, each subsequent band is taller and weaker
/// — so blur is heaviest at the edge and feathers out towards 0 over
/// [kFrostedEdgeExtent]. Steps are small enough that seams are imperceptible
/// (true per-pixel gradient blur would require a FragmentShader, but 10
/// layers with exponential falloff is visually continuous on 3x screens).
/// A canvas-coloured scrim on top keeps text legible.
class AppFrostedEdgeBackdrop extends StatelessWidget {
  const AppFrostedEdgeBackdrop({super.key, required this.edge});

  final AppFrostedEdge edge;

  // Strong → weak, extent is fraction of [kFrostedEdgeExtent] that the band
  // covers counted from the edge. Sigma steps are deliberately small;
  // max step is 4.0, dropping to 0.5 near the tail so the eye sees a
  // smooth gradient, not bands.
  static const _layers = [
    (sigma: 20.0, extent: 0.12),
    (sigma: 16.0, extent: 0.22),
    (sigma: 12.0, extent: 0.34),
    (sigma: 9.0, extent: 0.46),
    (sigma: 6.5, extent: 0.58),
    (sigma: 4.5, extent: 0.70),
    (sigma: 3.0, extent: 0.80),
    (sigma: 1.8, extent: 0.88),
    (sigma: 0.9, extent: 0.95),
    (sigma: 0.35, extent: 1.0),
  ];

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final isTop = edge == AppFrostedEdge.top;
    return IgnorePointer(
      child: ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Progressive blur: each layer is clipped to a different height
            // so the effective sigma tapers away from the edge. 10 layers
            // make the falloff look continuous.
            for (final layer in _layers)
              Positioned(
                top: isTop ? 0 : null,
                bottom: isTop ? null : 0,
                left: 0,
                right: 0,
                height: kFrostedEdgeExtent * layer.extent,
                child: ClipRect(
                  child: BackdropFilter(
                    filter: ImageFilter.blur(
                      sigmaX: layer.sigma,
                      sigmaY: layer.sigma,
                    ),
                    child: const SizedBox.expand(),
                  ),
                ),
              ),
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: isTop ? Alignment.topCenter : Alignment.bottomCenter,
                  end: isTop ? Alignment.bottomCenter : Alignment.topCenter,
                  colors: [
                    colors.canvas.withValues(alpha: .82),
                    colors.canvas.withValues(alpha: .45),
                    colors.canvas.withValues(alpha: .10),
                    colors.canvas.withValues(alpha: 0),
                  ],
                  stops: const [0, .38, .72, 1],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class AppControlSurface extends StatelessWidget {
  const AppControlSurface({
    super.key,
    required this.child,
    this.radius,
    this.borderRadius,
    this.color,
    this.borderColor,
    this.padding,
    this.selected = false,
    this.emphasized = false,
  }) : assert(radius == null || borderRadius == null);

  final Widget child;
  final double? radius;
  final BorderRadius? borderRadius;
  final Color? color;
  final Color? borderColor;
  final EdgeInsetsGeometry? padding;
  final bool selected;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final resolvedRadius = radius ?? AppRadii.of(context).control;
    final resolvedBorder =
        borderRadius ?? BorderRadius.circular(resolvedRadius);
    final bool useBlur =
        AppSettingsScope.maybeOf(context)?.state.navbarStyle ==
        AppNavBarStyle.materialBlur;
    final Color tint;
    final Color resolvedBorderColor;
    if (useBlur && !selected) {
      if (color != null && color != Colors.transparent) {
        tint = color!.withValues(alpha: .42);
      } else if (emphasized) {
        tint = colors.surfaceRaised.withValues(alpha: .55);
      } else {
        tint = colors.surface.withValues(alpha: .42);
      }
      resolvedBorderColor = (borderColor ?? colors.line).withValues(alpha: .72);
    } else {
      tint = _solidTint(
        colors,
        color,
        selected: selected,
        emphasized: emphasized,
      );
      resolvedBorderColor = borderColor ?? colors.line;
    }
    final content = Padding(padding: padding ?? EdgeInsets.zero, child: child);
    if (useBlur && !selected) {
      // The blurred backdrop sample is painted inside the clip region that
      // spans the full bounds, so a background border would be half-covered
      // by the child. Draw the stroke with a foreground-positioned
      // decoration so blur-mode buttons keep a crisp outline.
      return DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: BoxDecoration(
          border: Border.all(color: resolvedBorderColor),
          borderRadius: resolvedBorder,
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: tint,
            borderRadius: resolvedBorder,
          ),
          child: ClipRRect(
            borderRadius: resolvedBorder,
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
              child: content,
            ),
          ),
        ),
      );
    }
    return DecoratedBox(
      decoration: BoxDecoration(
        color: tint,
        border: Border.all(color: resolvedBorderColor),
        borderRadius: resolvedBorder,
      ),
      child: ClipRRect(borderRadius: resolvedBorder, child: content),
    );
  }
}

Color _solidTint(
  AppColors colors,
  Color? requested, {
  required bool selected,
  required bool emphasized,
}) {
  if (requested != null && requested != Colors.transparent) {
    return requested.withValues(alpha: 1);
  }
  if (selected) return colors.ink;
  if (emphasized) return colors.surfaceRaised;
  return colors.surface;
}

enum AppGlyph {
  menu,
  chat,
  search,
  sun,
  moon,
  copy,
  book,
  close,
  chevronDown,
  chevronRight,
  back,
  forward,
  settings,
  delete,
  cloud,
  cloudOff,
  memory,
  check,
  login,
  refresh,
  send,
  stop,
  verified,
}

/// A compact, shared icon language drawn by the app rather than an icon font.
class AppGlyphView extends StatelessWidget {
  const AppGlyphView(
    this.glyph, {
    super.key,
    required this.color,
    this.size = 20,
  });

  final AppGlyph glyph;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: size,
    height: size,
    child: CustomPaint(painter: _GlyphPainter(glyph, color)),
  );
}

class AppTap extends StatefulWidget {
  const AppTap({
    super.key,
    required this.child,
    required this.onTap,
    this.label,
    this.enabled = true,
    this.selected,
    this.inMutuallyExclusiveGroup = false,
  });

  final Widget child;
  final VoidCallback? onTap;
  final String? label;
  final bool enabled;
  final bool? selected;
  final bool inMutuallyExclusiveGroup;

  @override
  State<AppTap> createState() => _AppTapState();
}

class _AppTapState extends State<AppTap> {
  bool pressed = false;

  void _setPressed(bool value) {
    if (mounted && widget.enabled && widget.onTap != null) {
      setState(() => pressed = value);
    }
  }

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    excludeSemantics: widget.label != null,
    button: true,
    label: widget.label,
    enabled: widget.enabled && widget.onTap != null,
    selected: widget.selected,
    inMutuallyExclusiveGroup: widget.inMutuallyExclusiveGroup,
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: widget.enabled ? widget.onTap : null,
      onTapDown: (_) => _setPressed(true),
      onTapCancel: () => _setPressed(false),
      onTapUp: (_) => _setPressed(false),
      child: AnimatedScale(
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        scale: pressed ? .975 : 1,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 120),
          opacity: widget.enabled && widget.onTap != null
              ? (pressed ? .72 : 1)
              : .42,
          child: widget.child,
        ),
      ),
    ),
  );
}

class AppGlyphButton extends StatelessWidget {
  const AppGlyphButton({
    super.key,
    required this.glyph,
    required this.label,
    required this.onTap,
    this.size = 40,
    this.glyphSize = 19,
    this.fill,
    this.border,
    this.selected = false,
    this.emphasized = false,
  });

  final AppGlyph glyph;
  final String label;
  final VoidCallback? onTap;
  final double size;
  final double glyphSize;
  final Color? fill;
  final Color? border;
  final bool selected;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppButton(
      label: label,
      onTap: onTap,
      color: fill,
      borderColor: border,
      selected: selected,
      emphasized: emphasized,
      child: SizedBox(
        width: size,
        height: size,
        child: Center(
          child: AppGlyphView(
            glyph,
            color: onTap == null
                ? colors.muted
                : selected
                ? colors.canvas
                : colors.ink,
            size: glyphSize,
          ),
        ),
      ),
    );
  }
}

class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.child,
    required this.label,
    required this.onTap,
    this.enabled = true,
    this.selected = false,
    this.emphasized = false,
    this.radius,
    this.borderRadius,
    this.color,
    this.borderColor,
    this.padding,
  }) : assert(radius == null || borderRadius == null);

  final Widget child;
  final String label;
  final VoidCallback? onTap;
  final bool enabled;
  final bool selected;
  final bool emphasized;
  final double? radius;
  final BorderRadius? borderRadius;
  final Color? color;
  final Color? borderColor;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) => AppControlSurface(
    radius: radius,
    borderRadius: borderRadius,
    color: color,
    borderColor: borderColor,
    padding: padding,
    selected: selected,
    emphasized: emphasized,
    child: AppTap(
      label: label,
      enabled: enabled,
      onTap: onTap,
      selected: selected,
      child: child,
    ),
  );
}

class AppChoice<T> {
  const AppChoice({
    required this.value,
    required this.label,
    this.glyph,
    this.icon,
  }) : assert(glyph == null || icon == null);

  final T value;
  final String label;
  final AppGlyph? glyph;
  final IconData? icon;
}

class AppSegmented<T> extends StatelessWidget {
  const AppSegmented({
    super.key,
    required this.choices,
    required this.selected,
    required this.onChanged,
    this.height = 42,
  });

  final List<AppChoice<T>> choices;
  final T selected;
  final ValueChanged<T> onChanged;
  final double height;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    final selectedIndex = choices.indexWhere(
      (choice) => choice.value == selected,
    );
    return Semantics(
      label: context.l10n.options,
      explicitChildNodes: true,
      child: AppControlSurface(
        child: SizedBox(
          height: height,
          child: LayoutBuilder(
            builder: (context, constraints) {
              const inset = 3.0;
              final innerWidth =
                  (constraints.maxWidth - inset * 2) / choices.length;
              final hitWidth = constraints.maxWidth / choices.length;
              return Stack(
                children: [
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 260),
                    curve: const Cubic(0.16, 1, 0.3, 1),
                    left: inset + math.max(0, selectedIndex) * innerWidth,
                    top: inset,
                    bottom: inset,
                    width: innerWidth,
                    child: AppControlSurface(
                      radius: math.max(0, radii.control - 3),
                      color: colors.surfaceRaised,
                      borderColor: colors.line,
                      selected: true,
                      child: const SizedBox.expand(),
                    ),
                  ),
                  ...choices.indexed.map((entry) {
                    final index = entry.$1;
                    final choice = entry.$2;
                    final active = choice.value == selected;
                    return Positioned(
                      left: inset + index * innerWidth,
                      top: inset,
                      bottom: inset,
                      width: innerWidth,
                      child: ExcludeSemantics(
                        child: IgnorePointer(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 5),
                            child: Center(
                              child: FittedBox(
                                fit: BoxFit.scaleDown,
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    if (choice.glyph != null ||
                                        choice.icon != null) ...[
                                      choice.icon == null
                                          ? AppGlyphView(
                                              choice.glyph!,
                                              color: active
                                                  ? colors.ink
                                                  : colors.muted,
                                              size: 15,
                                            )
                                          : Icon(
                                              choice.icon,
                                              color: active
                                                  ? colors.ink
                                                  : colors.muted,
                                              size: 15,
                                            ),
                                      const SizedBox(width: 6),
                                    ],
                                    Text(
                                      choice.label,
                                      maxLines: 1,
                                      style: TextStyle(
                                        color: active
                                            ? colors.ink
                                            : colors.muted,
                                        fontSize: 11,
                                        fontWeight: active
                                            ? FontWeight.w600
                                            : FontWeight.w500,
                                      ),
                                    ),
                                    if (choice.glyph != null ||
                                        choice.icon != null)
                                      const SizedBox(width: 21),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  }),
                  ...choices.indexed.map((entry) {
                    final index = entry.$1;
                    final choice = entry.$2;
                    return Positioned(
                      left: index * hitWidth,
                      top: 0,
                      bottom: 0,
                      width: hitWidth,
                      child: AppTap(
                        label: choice.label,
                        onTap: () => onChanged(choice.value),
                        selected: choice.value == selected,
                        inMutuallyExclusiveGroup: true,
                        child: const SizedBox.expand(),
                      ),
                    );
                  }),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class AppTextInput extends StatefulWidget {
  const AppTextInput({
    super.key,
    required this.controller,
    required this.hint,
    this.prefix,
    this.header,
    this.trailing,
    this.helper,
    this.error,
    this.autofocus = false,
    this.minLines = 1,
    this.maxLines = 1,
    this.textInputAction,
    this.onSubmitted,
    this.radius,
  });

  final TextEditingController controller;
  final String hint;
  final AppGlyph? prefix;
  final Widget? header;
  final Widget? trailing;
  final String? helper;
  final String? error;
  final bool autofocus;
  final int minLines;
  final int maxLines;
  final TextInputAction? textInputAction;
  final ValueChanged<String>? onSubmitted;
  final double? radius;

  @override
  State<AppTextInput> createState() => _AppTextInputState();
}

class _AppTextInputState extends State<AppTextInput> {
  late final FocusNode focus = FocusNode()..addListener(_changed);

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    focus
      ..removeListener(_changed)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final hasError = widget.error?.isNotEmpty == true;
    final outline = hasError
        ? colors.danger
        : focus.hasFocus
        ? colors.ink
        : colors.line;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          constraints: const BoxConstraints(minHeight: 48),
          child: AppControlSurface(
            radius: widget.radius,
            color: colors.surfaceRaised.withValues(alpha: .72),
            borderColor: outline,
            padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 5),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: widget.header == null
                  ? MainAxisAlignment.center
                  : MainAxisAlignment.start,
              children: [
                if (widget.header != null) ...[
                  widget.header!,
                  const SizedBox(height: 5),
                ],
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    if (widget.prefix != null) ...[
                      AppGlyphView(
                        widget.prefix!,
                        color: colors.muted,
                        size: 18,
                      ),
                      const SizedBox(width: 10),
                    ],
                    Expanded(
                      child: Stack(
                        alignment: Alignment.centerLeft,
                        children: [
                          if (widget.controller.text.isEmpty)
                            IgnorePointer(
                              child: Text(
                                widget.hint,
                                maxLines: widget.maxLines,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: colors.muted,
                                  fontFamily: 'OpenRunde',
                                  fontSize: 14,
                                  height: 1.45,
                                ),
                              ),
                            ),
                          EditableText(
                            controller: widget.controller,
                            focusNode: focus,
                            autofocus: widget.autofocus,
                            minLines: widget.minLines,
                            maxLines: widget.maxLines,
                            textInputAction: widget.textInputAction,
                            style: TextStyle(
                              color: colors.ink,
                              fontFamily: 'OpenRunde',
                              fontSize: 14,
                              height: 1.45,
                            ),
                            cursorColor: colors.ink,
                            backgroundCursorColor: colors.muted,
                            selectionColor: const Color(0xFF376996),
                            keyboardAppearance: Theme.of(context).brightness,
                            onSubmitted: widget.onSubmitted,
                          ),
                        ],
                      ),
                    ),
                    if (widget.trailing != null) ...[
                      const SizedBox(width: 8),
                      widget.trailing!,
                    ],
                  ],
                ),
              ],
            ),
          ),
        ),
        if (widget.helper?.isNotEmpty == true) ...[
          const SizedBox(height: 7),
          Text(
            widget.helper!,
            style: TextStyle(color: colors.muted, fontSize: 11, height: 1.4),
          ),
        ],
        if (hasError) ...[
          const SizedBox(height: 7),
          Text(
            widget.error!,
            style: TextStyle(color: colors.danger, fontSize: 11),
          ),
        ],
      ],
    );
  }
}

class AppSpinner extends StatefulWidget {
  const AppSpinner({super.key, required this.color, this.size = 20});

  final Color color;
  final double size;

  @override
  State<AppSpinner> createState() => _AppSpinnerState();
}

class _AppSpinnerState extends State<AppSpinner>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 820),
  )..repeat();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RotationTransition(
    turns: controller,
    child: SizedBox(
      width: widget.size,
      height: widget.size,
      child: CustomPaint(painter: _SpinnerPainter(widget.color)),
    ),
  );
}

class AppProgressLine extends StatelessWidget {
  const AppProgressLine({super.key, required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) =>
      SizedBox(height: 3, child: _IndeterminateLine(color: color));
}

class _IndeterminateLine extends StatefulWidget {
  const _IndeterminateLine({required this.color});
  final Color color;

  @override
  State<_IndeterminateLine> createState() => _IndeterminateLineState();
}

class _IndeterminateLineState extends State<_IndeterminateLine>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1450),
  )..repeat();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: controller,
        builder: (context, _) => CustomPaint(
          painter: _ProgressLinePainter(
            progress: controller.value,
            track: colors.surfaceRaised,
            color: widget.color,
          ),
          child: const SizedBox.expand(),
        ),
      ),
    );
  }
}

class _ProgressLinePainter extends CustomPainter {
  const _ProgressLinePainter({
    required this.progress,
    required this.track,
    required this.color,
  });

  final double progress;
  final Color track;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final radius = Radius.circular(size.height / 2);
    canvas.drawRRect(
      RRect.fromRectAndRadius(Offset.zero & size, radius),
      Paint()..color = track,
    );
    canvas.save();
    canvas.clipRRect(RRect.fromRectAndRadius(Offset.zero & size, radius));
    _drawSegment(canvas, size, progress, .38, 1);
    _drawSegment(canvas, size, (progress + .54) % 1, .22, .62);
    canvas.restore();
  }

  void _drawSegment(
    Canvas canvas,
    Size size,
    double phase,
    double baseWidth,
    double opacity,
  ) {
    final eased = Curves.easeInOutCubic.transform(phase);
    final widthFactor = baseWidth + math.sin(phase * math.pi) * .16;
    final left = (-widthFactor + eased * (1 + widthFactor)) * size.width;
    final rect = Rect.fromLTWH(left, 0, widthFactor * size.width, size.height);
    canvas.drawRect(rect, Paint()..color = color.withValues(alpha: opacity));
  }

  @override
  bool shouldRepaint(covariant _ProgressLinePainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.track != track ||
      oldDelegate.color != color;
}

class AppPageRoute<T> extends PageRouteBuilder<T> {
  AppPageRoute({required WidgetBuilder builder})
    : super(
        pageBuilder: (context, animation, secondaryAnimation) =>
            builder(context),
        transitionDuration: const Duration(milliseconds: 320),
        reverseTransitionDuration: const Duration(milliseconds: 280),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          final tween = Tween<Offset>(
            begin: const Offset(1, 0),
            end: Offset.zero,
          ).chain(CurveTween(curve: Curves.easeOutCubic));
          // Predictive back: secondaryAnimation drives the outgoing page's
          // parallax, giving the native Android 14+ preview effect.
          return SlideTransition(
            position: animation.drive(tween),
            child: child,
          );
        },
      );
}

class _SpinnerPainter extends CustomPainter {
  const _SpinnerPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final stroke = math.max(1.6, size.shortestSide * .12);
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.square;
    final inset = stroke / 2;
    canvas.drawArc(
      (Offset.zero & size).deflate(inset),
      -.9,
      math.pi * 1.35,
      false,
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _SpinnerPainter oldDelegate) =>
      oldDelegate.color != color;
}

class _GlyphPainter extends CustomPainter {
  const _GlyphPainter(this.glyph, this.color);
  final AppGlyph glyph;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = math.max(1.45, size.shortestSide * .085)
      ..strokeCap = StrokeCap.square
      ..strokeJoin = StrokeJoin.round;
    final w = size.width;
    final h = size.height;
    Offset o(double x, double y) => Offset(w * x, h * y);
    void line(double x1, double y1, double x2, double y2) =>
        canvas.drawLine(o(x1, y1), o(x2, y2), p);
    void rect(double x, double y, double rw, double rh) =>
        canvas.drawRect(Rect.fromLTWH(w * x, h * y, w * rw, h * rh), p);
    void circle(double x, double y, double r) =>
        canvas.drawCircle(o(x, y), w * r, p);
    void path(List<Offset> points) {
      final value = Path()..moveTo(points.first.dx, points.first.dy);
      for (final point in points.skip(1)) {
        value.lineTo(point.dx, point.dy);
      }
      canvas.drawPath(value, p);
    }

    switch (glyph) {
      case AppGlyph.menu:
        line(.16, .27, .84, .27);
        line(.16, .5, .84, .5);
        line(.16, .73, .84, .73);
      case AppGlyph.chat:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(w * .14, h * .18, w * .72, h * .56),
            Radius.circular(w * .12),
          ),
          p,
        );
        path([o(.34, .74), o(.29, .9), o(.48, .74)]);
      case AppGlyph.search:
        circle(.44, .44, .27);
        line(.64, .64, .86, .86);
      case AppGlyph.sun:
        circle(.5, .5, .2);
        final center = o(.5, .5);
        final radius = size.shortestSide;
        final rayPaint = Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = p.strokeWidth
          ..strokeCap = StrokeCap.round;
        for (var index = 0; index < 8; index++) {
          final angle = (math.pi * 2 * index / 8) - math.pi / 2;
          final direction = Offset(math.cos(angle), math.sin(angle));
          canvas.drawLine(
            center + direction * (radius * .33),
            center + direction * (radius * .44),
            rayPaint,
          );
        }
      case AppGlyph.moon:
        final moon = Path()
          ..moveTo(w * .69, h * .16)
          ..cubicTo(w * .42, h * .18, w * .22, h * .39, w * .22, h * .62)
          ..cubicTo(w * .22, h * .84, w * .43, h * .91, w * .63, h * .83)
          ..cubicTo(w * .75, h * .78, w * .83, h * .68, w * .86, h * .57)
          ..cubicTo(w * .75, h * .65, w * .59, h * .66, w * .48, h * .56)
          ..cubicTo(w * .35, h * .43, w * .43, h * .23, w * .69, h * .16)
          ..close();
        canvas.drawPath(
          moon,
          Paint()
            ..color = color
            ..style = PaintingStyle.fill,
        );
      case AppGlyph.copy:
        rect(.32, .14, .48, .58);
        rect(.18, .28, .48, .58);
      case AppGlyph.book:
        path([o(.12, .2), o(.47, .13), o(.47, .83), o(.12, .9), o(.12, .2)]);
        path([o(.88, .2), o(.53, .13), o(.53, .83), o(.88, .9), o(.88, .2)]);
      case AppGlyph.close:
        line(.24, .24, .76, .76);
        line(.76, .24, .24, .76);
      case AppGlyph.chevronDown:
        path([o(.2, .36), o(.5, .66), o(.8, .36)]);
      case AppGlyph.chevronRight:
        path([o(.36, .2), o(.66, .5), o(.36, .8)]);
      case AppGlyph.back:
        line(.84, .5, .17, .5);
        path([o(.42, .2), o(.17, .5), o(.42, .8)]);
      case AppGlyph.forward:
        line(.16, .5, .83, .5);
        path([o(.58, .2), o(.83, .5), o(.58, .8)]);
      case AppGlyph.settings:
        circle(.5, .5, .12);
        circle(.5, .5, .29);
        line(.5, .1, .5, .22);
        line(.5, .78, .5, .9);
        line(.1, .5, .22, .5);
        line(.78, .5, .9, .5);
      case AppGlyph.delete:
        line(.18, .25, .82, .25);
        line(.4, .14, .6, .14);
        rect(.26, .25, .48, .61);
        line(.42, .4, .42, .72);
        line(.58, .4, .58, .72);
      case AppGlyph.cloud:
        path([
          o(.18, .7),
          o(.18, .57),
          o(.29, .47),
          o(.42, .47),
          o(.5, .31),
          o(.67, .31),
          o(.78, .45),
          o(.84, .46),
          o(.9, .57),
          o(.9, .7),
          o(.18, .7),
        ]);
      case AppGlyph.cloudOff:
        path([
          o(.18, .7),
          o(.18, .57),
          o(.29, .47),
          o(.42, .47),
          o(.5, .31),
          o(.67, .31),
          o(.78, .45),
          o(.84, .46),
          o(.9, .57),
          o(.9, .7),
          o(.18, .7),
        ]);
        line(.14, .14, .86, .86);
      case AppGlyph.memory:
        rect(.25, .25, .5, .5);
        circle(.5, .5, .1);
        for (final value in [.18, .42, .66]) {
          line(value, .1, value, .25);
          line(value, .75, value, .9);
          line(.1, value, .25, value);
          line(.75, value, .9, value);
        }
      case AppGlyph.check:
        path([o(.18, .52), o(.41, .75), o(.84, .24)]);
      case AppGlyph.login:
        rect(.14, .15, .42, .7);
        line(.43, .5, .88, .5);
        path([o(.66, .28), o(.88, .5), o(.66, .72)]);
      case AppGlyph.refresh:
        canvas.drawArc(
          Rect.fromLTWH(w * .18, h * .18, w * .64, h * .64),
          -.9,
          math.pi * 1.54,
          false,
          p,
        );
        path([o(.79, .18), o(.84, .44), o(.59, .35)]);
      case AppGlyph.send:
        path([o(.15, .16), o(.87, .5), o(.15, .84), o(.3, .5), o(.15, .16)]);
        line(.3, .5, .64, .5);
      case AppGlyph.stop:
        rect(.25, .25, .5, .5);
      case AppGlyph.verified:
        circle(.5, .5, .34);
        path([o(.31, .52), o(.45, .66), o(.7, .38)]);
    }
  }

  @override
  bool shouldRepaint(covariant _GlyphPainter oldDelegate) =>
      oldDelegate.glyph != glyph || oldDelegate.color != color;
}
