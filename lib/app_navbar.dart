import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import 'app_settings.dart';
import 'app_theme.dart';
import 'localization.dart';

/// Height of the floating bar pill itself (original vertical size restored).
const kAppNavBarHeight = 58.0;

/// Space between the pill and the bottom system-gesture inset.
const kAppNavBarBottomGap = 12.0;

/// Horizontal breathing room around the pill.
const kAppNavBarHorizontalInset = 16.0;

/// Full-width cap of the pill when all three tabs are shown.
const kAppNavBarMaxWidth = 260.0;

/// Total height the floating bar occupies, including its bottom gap.
double appNavBarOccupiedHeight(BuildContext context) =>
    MediaQuery.paddingOf(context).bottom + kAppNavBarBottomGap + kAppNavBarHeight;

/// Extra bottom padding pages must add so content clears the floating bar.
/// With the bar hidden in Settings only a small breathing gap remains.
double appNavBottomClearance(BuildContext context) {
  final navbarHidden =
      AppSettingsScope.maybeOf(context)?.state.showNavbar == false;
  if (navbarHidden) return MediaQuery.paddingOf(context).bottom + 16;
  return MediaQuery.paddingOf(context).bottom + kAppNavBarBottomGap + kAppNavBarHeight;
}

enum AppNavTab { bible, ask, devotion }

class NavBarItem {
  const NavBarItem({
    required this.tab,
    required this.label,
    required this.icon,
  });

  final AppNavTab tab;
  final String label;
  final IconData icon;
}

List<NavBarItem> navBarItems(BuildContext context) => [
  NavBarItem(
    tab: AppNavTab.bible,
    label: context.l10n.tabBible,
    icon: LucideIcons.bookOpen,
  ),
  NavBarItem(
    tab: AppNavTab.ask,
    label: context.l10n.tabAsk,
    icon: LucideIcons.sparkles,
  ),
  NavBarItem(
    tab: AppNavTab.devotion,
    label: context.l10n.tabDevotion,
    icon: LucideIcons.sunrise,
  ),
];

/// Floating bottom navigation bar — compact pill with draggable white indicator.
class AppNavBar extends StatefulWidget {
  const AppNavBar({
    super.key,
    required this.items,
    required this.index,
    required this.onChanged,
    required this.style,
  });

  final List<NavBarItem> items;
  final int index;
  final ValueChanged<int> onChanged;
  final AppNavBarStyle style;

  @override
  State<AppNavBar> createState() => _AppNavBarState();
}

class _AppNavBarState extends State<AppNavBar> {
  double _dragOffset = 0;
  bool _isDragging = false;
  double _dragStartX = 0;
  double _dragStartOffset = 0;

  @override
  void didUpdateWidget(covariant AppNavBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_isDragging && oldWidget.index != widget.index) {
      _dragOffset = widget.index.toDouble();
    }
  }

  @override
  void initState() {
    super.initState();
    _dragOffset = widget.index.toDouble();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);

    final bool useBlur = widget.style == AppNavBarStyle.materialBlur;
    final Color pillColor = widget.style == AppNavBarStyle.material
        ? colors.surfaceRaised
        : colors.surface.withValues(alpha: .42);

    // Size off local constraints, never MediaQuery: on the very first web
    // frames the viewport reports zero and a negative pill width crashes
    // the whole bar. Scale with the tab count so hiding Devotions shrinks
    // the pill instead of stretching the remaining segments wider.
    return Center(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final pillWidth = math.max(
            120.0,
            math.min(
              constraints.maxWidth,
              kAppNavBarMaxWidth * widget.items.length / 3,
            ),
          );
          Widget pill = Container(
            height: kAppNavBarHeight,
            decoration: BoxDecoration(
              color: pillColor,
              borderRadius: BorderRadius.circular(kAppNavBarHeight / 2),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(
                    alpha: Theme.of(context).brightness == Brightness.dark
                        ? .28
                        : .08,
                  ),
                  blurRadius: 14,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            // Foreground stroke: the blurred backdrop is sampled inside a clip
            // that spans the full bounds, so a background border would lose
            // its inner half. Painting it last keeps the outline crisp.
            foregroundDecoration: BoxDecoration(
              border: Border.all(color: colors.line.withValues(alpha: .72)),
              borderRadius: BorderRadius.circular(kAppNavBarHeight / 2),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(kAppNavBarHeight / 2),
              child: useBlur
                  ? BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                      child: _buildContent(colors),
                    )
                  : _buildContent(colors),
            ),
          );
          return SizedBox(width: pillWidth, child: pill);
        },
      ),
    );
  }

  Widget _buildContent(AppColors colors) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final totalWidth = constraints.maxWidth;
        const horizontalPadding = 4.0;
        final itemWidth = (totalWidth - horizontalPadding * 2) / widget.items.length;

        // Indicator position: when dragging, follow finger; otherwise snap to index
        final double position = _isDragging ? _dragOffset : widget.index.toDouble();
        final double clampedPosition = position.clamp(0, widget.items.length - 1).toDouble();

        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onHorizontalDragStart: (details) {
            setState(() {
              _isDragging = true;
              _dragStartX = details.localPosition.dx;
              _dragStartOffset = widget.index.toDouble();
              // Keep indicator at current index on drag start; delta will follow finger.
              _dragOffset = _dragStartOffset;
            });
          },
          onHorizontalDragUpdate: (details) {
            setState(() {
              final localX = details.localPosition.dx;
              final delta = localX - _dragStartX;
              _dragOffset = (_dragStartOffset + delta / itemWidth).clamp(
                0,
                widget.items.length - 1,
              ).toDouble();
            });
          },
          onHorizontalDragEnd: (details) {
            final targetIndex =
                clampedPosition.round().clamp(0, widget.items.length - 1);
            setState(() {
              _isDragging = false;
              _dragOffset = targetIndex.toDouble();
            });
            if (targetIndex != widget.index) {
              widget.onChanged(targetIndex);
            }
          },
          onHorizontalDragCancel: () {
            setState(() {
              _isDragging = false;
              _dragOffset = widget.index.toDouble();
            });
          },
          onTapUp: (details) {
            final localX = details.localPosition.dx;
            final tappedIndex = ((localX - horizontalPadding) / itemWidth)
                .floor()
                .clamp(0, widget.items.length - 1);
            if (tappedIndex != widget.index) {
              widget.onChanged(tappedIndex);
            }
          },
          child: Stack(
            children: [
              // Sliding white pill indicator
              AnimatedPositioned(
                duration: _isDragging ? Duration.zero : const Duration(milliseconds: 240),
                curve: Curves.easeOutCubic,
                left: horizontalPadding + clampedPosition * itemWidth + 4,
                top: 4,
                bottom: 4,
                width: itemWidth - 8,
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular((kAppNavBarHeight - 8) / 2),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: .08),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                ),
              ),
              // Tab items on top, vertically centered across the pill.
              Positioned(
                left: horizontalPadding,
                right: horizontalPadding,
                top: 0,
                bottom: 0,
                child: Row(
                  children: [
                    for (var i = 0; i < widget.items.length; i++)
                      Expanded(
                        child: Center(
                          child: _buildItem(
                            widget.items[i],
                            i,
                            colors,
                            clampedPosition,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildItem(NavBarItem item, int itemIndex, AppColors colors, double indicatorPosition) {
    // Calculate how close this item is to being selected (for color interpolation)
    final distance = (indicatorPosition - itemIndex).abs().clamp(0, 1).toDouble();
    final selectedness = 1 - distance; // 1 = fully selected, 0 = not at all

    final isSelected = widget.index == itemIndex && !_isDragging;
    // During drag, interpolate; otherwise use discrete selection
    final effectiveSelectedness = _isDragging ? selectedness : (isSelected ? 1.0 : 0.0);

    return Semantics(
      button: true,
      selected: isSelected,
      inMutuallyExclusiveGroup: true,
      label: item.label,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            item.icon,
            size: 19,
            color: Color.lerp(colors.muted, Colors.black87, effectiveSelectedness)!,
          ),
          const SizedBox(height: 3),
          Text(
            item.label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 10,
              fontWeight: effectiveSelectedness > 0.5 ? FontWeight.w600 : FontWeight.w500,
              color: Color.lerp(colors.muted, Colors.black87, effectiveSelectedness)!,
            ),
          ),
        ],
      ),
    );
  }
}
