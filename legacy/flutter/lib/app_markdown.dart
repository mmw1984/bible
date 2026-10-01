import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'app_ui.dart';

/// Small, app-owned renderer for the Markdown the assistant returns.
class AppMarkdown extends StatelessWidget {
  const AppMarkdown({
    super.key,
    required this.data,
    required this.foreground,
    required this.secondary,
    required this.border,
    this.compact = false,
    this.onLink,
  });

  final String data;
  final Color foreground;
  final Color secondary;
  final Color border;
  final bool compact;
  final ValueChanged<Uri>? onLink;

  @override
  Widget build(BuildContext context) => SelectionArea(
    child: Builder(
      builder: (selectionContext) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: _blocks(selectionContext),
      ),
    ),
  );

  List<Widget> _blocks(BuildContext context) {
    final lines = data.replaceAll('\r\n', '\n').split('\n');
    final blocks = <Widget>[];
    final paragraph = <String>[];

    void flushParagraph() {
      if (paragraph.isEmpty) return;
      blocks.add(_paragraph(context, paragraph.join('\n')));
      paragraph.clear();
    }

    for (var index = 0; index < lines.length; index++) {
      final line = lines[index];
      if (line.trim().isEmpty) {
        flushParagraph();
        continue;
      }
      if (line.trimLeft().startsWith('```')) {
        flushParagraph();
        final code = <String>[];
        while (++index < lines.length &&
            !lines[index].trimLeft().startsWith('```')) {
          code.add(lines[index]);
        }
        blocks.add(_codeBlock(context, code.join('\n')));
        continue;
      }
      final heading = RegExp(r'^(#{1,3})\s+(.+)$').firstMatch(line);
      if (heading != null) {
        flushParagraph();
        blocks.add(_heading(heading.group(2)!, heading.group(1)!.length));
        continue;
      }
      if (RegExp(r'^\s{0,3}([-*_])\1\1+\s*$').hasMatch(line)) {
        flushParagraph();
        blocks.add(Container(height: 1, margin: _margin, color: border));
        continue;
      }
      if (index + 1 < lines.length &&
          _tableCells(line).length > 1 &&
          _isTableDivider(lines[index + 1])) {
        flushParagraph();
        final rows = <List<String>>[_tableCells(line)];
        index += 2;
        while (index < lines.length && lines[index].trim().isNotEmpty) {
          final cells = _tableCells(lines[index]);
          if (cells.length <= 1) break;
          rows.add(cells);
          index++;
        }
        index--;
        blocks.add(_table(context, rows));
        continue;
      }
      if (line.startsWith('>')) {
        flushParagraph();
        final quote = <String>[line.replaceFirst(RegExp(r'^>\s?'), '')];
        while (index + 1 < lines.length && lines[index + 1].startsWith('>')) {
          quote.add(lines[++index].replaceFirst(RegExp(r'^>\s?'), ''));
        }
        blocks.add(_quote(context, quote.join('\n')));
        continue;
      }
      final bullet = RegExp(r'^\s*[-*+]\s+(.+)$').firstMatch(line);
      final ordered = RegExp(r'^\s*(\d+)\.\s+(.+)$').firstMatch(line);
      if (bullet != null || ordered != null) {
        flushParagraph();
        blocks.add(
          _listItem(
            context,
            bullet?.group(1) ?? ordered!.group(2)!,
            marker: ordered == null ? '—' : '${ordered.group(1)}.',
          ),
        );
        continue;
      }
      paragraph.add(line);
    }
    flushParagraph();
    return blocks.isEmpty ? const [SizedBox.shrink()] : blocks;
  }

  EdgeInsets get _margin => EdgeInsets.only(bottom: compact ? 7 : 11);

  Widget _heading(String value, int level) => Padding(
    padding: _margin,
    child: Text(
      _plain(value),
      style: TextStyle(
        color: foreground,
        fontSize: switch (level) {
          1 => compact ? 17 : 21,
          2 => compact ? 15 : 18,
          _ => compact ? 14 : 16,
        },
        height: 1.35,
        fontWeight: FontWeight.w600,
      ),
    ),
  );

  Widget _paragraph(BuildContext context, String value) => Padding(
    padding: _margin,
    child: _selectableRichText(
      context,
      text: TextSpan(
        style: TextStyle(
          color: foreground,
          fontFamily: 'OpenRunde',
          fontSize: compact ? 12 : 14,
          height: compact ? 1.5 : 1.58,
        ),
        children: _inline(value),
      ),
    ),
  );

  Widget _quote(BuildContext context, String value) => Container(
    margin: _margin,
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
    decoration: BoxDecoration(
      color: border.withValues(alpha: .28),
      border: Border(left: BorderSide(color: border, width: 3)),
    ),
    child: _selectableRichText(
      context,
      text: TextSpan(
        style: TextStyle(
          color: secondary,
          fontFamily: 'OpenRunde',
          fontSize: compact ? 12 : 14,
          height: 1.55,
        ),
        children: _inline(value),
      ),
    ),
  );

  Widget _codeBlock(BuildContext context, String value) => Container(
    width: double.infinity,
    margin: _margin,
    padding: const EdgeInsets.all(13),
    decoration: BoxDecoration(
      color: border.withValues(alpha: .22),
      border: Border.all(color: border),
      borderRadius: BorderRadius.circular(AppRadii.of(context).compact),
    ),
    child: SelectableText(
      value,
      style: TextStyle(
        color: foreground,
        fontFamily: 'monospace',
        fontSize: compact ? 11 : 12,
        height: 1.45,
      ),
    ),
  );

  Widget _table(BuildContext context, List<List<String>> rows) {
    final columnCount = rows.fold<int>(
      0,
      (largest, row) => row.length > largest ? row.length : largest,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final available = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : 320.0;
        final columnWidth = available / columnCount;
        final resolvedColumnWidth = columnWidth.clamp(132.0, 240.0);
        final tableWidth = available > resolvedColumnWidth * columnCount
            ? available
            : resolvedColumnWidth * columnCount;
        final widths = <int, TableColumnWidth>{
          for (var index = 0; index < columnCount; index++)
            index: FixedColumnWidth(tableWidth / columnCount),
        };
        return Container(
          margin: _margin,
          decoration: BoxDecoration(
            border: Border.all(color: border),
            borderRadius: BorderRadius.circular(AppRadii.of(context).compact),
          ),
          clipBehavior: Clip.antiAlias,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: tableWidth,
              child: Table(
                columnWidths: widths,
                defaultVerticalAlignment: TableCellVerticalAlignment.middle,
                border: TableBorder.symmetric(
                  inside: BorderSide(color: border),
                ),
                children: rows.indexed.map((entry) {
                  final rowIndex = entry.$1;
                  final cells = entry.$2;
                  return TableRow(
                    decoration: BoxDecoration(
                      color: rowIndex == 0
                          ? border.withValues(alpha: .3)
                          : rowIndex.isEven
                          ? border.withValues(alpha: .1)
                          : Colors.transparent,
                    ),
                    children: List.generate(columnCount, (columnIndex) {
                      final value = columnIndex < cells.length
                          ? cells[columnIndex]
                          : '';
                      return Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: compact ? 9 : 11,
                          vertical: compact ? 7 : 9,
                        ),
                        child: _selectableRichText(
                          context,
                          text: TextSpan(
                            style: TextStyle(
                              color: rowIndex == 0 ? foreground : secondary,
                              fontFamily: 'OpenRunde',
                              fontSize: compact ? 11 : 13,
                              height: 1.45,
                              fontWeight: rowIndex == 0
                                  ? FontWeight.w600
                                  : FontWeight.w400,
                            ),
                            children: _inline(value),
                          ),
                        ),
                      );
                    }),
                  );
                }).toList(),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _listItem(
    BuildContext context,
    String value, {
    required String marker,
  }) {
    final checked = RegExp(r'^\[([ xX])\]\s+').firstMatch(value);
    final text = checked == null ? value : value.substring(checked.end);
    return Padding(
      padding: EdgeInsets.only(bottom: compact ? 5 : 7),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: checked == null ? 22 : 24,
            child: checked == null
                ? Text(marker, style: TextStyle(color: secondary, height: 1.58))
                : _check(context, checked.group(1)!.toLowerCase() == 'x'),
          ),
          Expanded(
            child: _selectableRichText(
              context,
              text: TextSpan(
                style: TextStyle(
                  color: foreground,
                  fontFamily: 'OpenRunde',
                  fontSize: compact ? 12 : 14,
                  height: compact ? 1.5 : 1.58,
                ),
                children: _inline(text),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _check(BuildContext context, bool value) => Container(
    width: 13,
    height: 13,
    margin: const EdgeInsets.only(top: 4),
    decoration: BoxDecoration(
      color: value ? foreground : Colors.transparent,
      border: Border.all(color: foreground),
      borderRadius: BorderRadius.circular(AppRadii.of(context).compact / 4),
    ),
    child: value
        ? AppGlyphView(AppGlyph.check, color: AppColors.dark.canvas, size: 11)
        : null,
  );

  RichText _selectableRichText(
    BuildContext context, {
    required TextSpan text,
  }) => RichText(
    text: text,
    selectionRegistrar: SelectionContainer.maybeOf(context),
    selectionColor: foreground.withValues(alpha: .24),
  );

  List<InlineSpan> _inline(String source) {
    final parts = source
        .splitMapJoin(
          // An unmatched closing marker is expected while a response streams.
          RegExp(
            r'(`[^`\n]*(?:`|$)|\*\*[\s\S]*?(?:\*\*|$)|\*[^*\n]+(?:\*|$)|\[[^\]]+\]\([^\s)]+\))',
          ),
          onMatch: (match) => '\u0000${match.group(0)}\u0000',
          onNonMatch: (value) => value,
        )
        .split('\u0000');
    return parts.where((part) => part.isNotEmpty).map((part) {
      final link = RegExp(r'^\[([^\]]+)\]\(([^\s)]+)\)$').firstMatch(part);
      if (link != null) {
        final uri = Uri.tryParse(link.group(2)!);
        return WidgetSpan(
          alignment: PlaceholderAlignment.baseline,
          baseline: TextBaseline.alphabetic,
          child: AppTap(
            label: link.group(1)!,
            onTap: uri == null || onLink == null ? null : () => onLink!(uri),
            enabled: uri != null && onLink != null,
            child: Text(
              link.group(1)!,
              style: TextStyle(
                color: Color.alphaBlend(const Color(0xFF3F78A8), foreground),
                decoration: TextDecoration.underline,
                fontFamily: 'OpenRunde',
                fontSize: compact ? 12 : 14,
              ),
            ),
          ),
        );
      }
      if (part.startsWith('`')) {
        final closed = part.length > 1 && part.endsWith('`');
        return TextSpan(
          text: part.substring(1, closed ? part.length - 1 : part.length),
          style: TextStyle(
            color: foreground,
            backgroundColor: border.withValues(alpha: .35),
            fontFamily: 'monospace',
            fontSize: compact ? 11 : 12,
          ),
        );
      }
      if (part.startsWith('**')) {
        final closed = part.length > 3 && part.endsWith('**');
        return TextSpan(
          text: part.substring(2, closed ? part.length - 2 : part.length),
          style: const TextStyle(fontWeight: FontWeight.w700),
        );
      }
      if (part.startsWith('*')) {
        final closed = part.length > 1 && part.endsWith('*');
        return TextSpan(
          text: part.substring(1, closed ? part.length - 1 : part.length),
          style: const TextStyle(fontStyle: FontStyle.italic),
        );
      }
      return TextSpan(text: part);
    }).toList();
  }

  String _plain(String value) => value
      .replaceAll(RegExp(r'[`*_]+'), '')
      .replaceAllMapped(
        RegExp(r'\[([^\]]+)\]\([^)]*\)'),
        (match) => match.group(1)!,
      );

  bool _isTableDivider(String source) {
    final cells = _tableCells(source);
    return cells.length > 1 &&
        cells.every((cell) => RegExp(r'^:?-{3,}:?$').hasMatch(cell.trim()));
  }

  List<String> _tableCells(String source) {
    final trimmed = source.trim();
    if (!trimmed.contains('|')) return const [];
    final value = trimmed
        .replaceFirst(RegExp(r'^\|'), '')
        .replaceFirst(RegExp(r'\|$'), '');
    final cells = <String>[];
    final buffer = StringBuffer();
    var escaped = false;
    var inCode = false;
    for (final rune in value.runes) {
      final character = String.fromCharCode(rune);
      if (escaped) {
        buffer.write(character);
        escaped = false;
      } else if (character == r'\') {
        escaped = true;
      } else if (character == '`') {
        inCode = !inCode;
        buffer.write(character);
      } else if (character == '|' && !inCode) {
        cells.add(buffer.toString().trim());
        buffer.clear();
      } else {
        buffer.write(character);
      }
    }
    if (escaped) buffer.write(r'\');
    cells.add(buffer.toString().trim());
    return cells;
  }
}
