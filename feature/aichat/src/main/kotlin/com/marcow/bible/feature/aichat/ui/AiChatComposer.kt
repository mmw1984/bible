package com.marcow.bible.feature.aichat.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii

/**
 * The dock at the foot of the chat: `_ChatComposer` in `legacy/flutter/lib/ai_chat_page.dart:939`, with
 * `_ChatComposerDock` above it. The question box, the send or stop control, and the attachment the
 * chapter arrived with.
 *
 * The corners are the part worth reading. Flutter took the device's own *bottom* corner radii and turned
 * them into the input's rounding, the button's rounding and the gutters either side, so on a phone with
 * a rounded display the composer sat in the screen's curve instead of in a fixed box inside it:
 * `max(10, max(radius, bottomInset) * .24)` for a gutter, `max(radius, bottomInset * .34)` clamped to
 * 10–24 for the input, and the same off the right corner for the button. Each side reads its own corner
 * because the two are not the same on a foldable.
 *
 * `MediaQuery.viewPaddingOf(context).bottom` is the bottom system bar rather than `padding`, which the
 * keyboard shrinks; [WindowInsets.navigationBars] is the same quantity on this side, and the screen
 * lifts the whole dock with the keyboard rather than this one padding itself above it a second time.
 *
 * The 760 is Flutter's own measure, so the box stops widening on a tablet instead of leaving a
 * question line stretched across it.
 */
@Suppress("LongParameterList")
@Composable
internal fun AiChatComposer(
    question: String,
    onQuestionChange: (String) -> Unit,
    error: String?,
    scriptureReference: String?,
    scriptureAttachment: String?,
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRemoveAttachment: () -> Unit,
    modifier: Modifier = Modifier,
    autofocus: Boolean = false,
) {
    val radii = appRadii
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val leftCorner = maxOf(radii.bottomLeft, bottomInset)
    val rightCorner = maxOf(radii.bottomRight, bottomInset)
    val leftPadding = maxOf(GUTTER_MIN, leftCorner * CORNER_PADDING_SCALE)
    val rightPadding = maxOf(GUTTER_MIN, rightCorner * CORNER_PADDING_SCALE)

    Column(
        modifier = modifier.padding(
            start = leftPadding,
            end = rightPadding,
            bottom = maxOf(leftPadding, rightPadding),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .padding(top = DOCK_TOP_PADDING)
                .fillMaxWidth()
                .widthIn(max = MEASURE_MAX),
            verticalAlignment = Alignment.Top,
        ) {
            AiChatInput(
                value = question,
                onValueChange = onQuestionChange,
                error = error,
                hint = scriptureReference
                    ?.let { stringResource(R.string.follow_up_hint, it) }
                    ?: stringResource(R.string.question_hint),
                header = scriptureAttachment?.let {
                    {
                        ScriptureAttachment(
                            reference = scriptureReference
                                ?: stringResource(R.string.explain_scripture),
                            text = it,
                            onRemove = onRemoveAttachment,
                        )
                    }
                },
                autofocus = autofocus,
                modifier = Modifier.weight(1f),
                radius = cornerRadius(radii.control, leftCorner),
            )
            Spacer(modifier = Modifier.width(ACTION_GAP))
            ComposerAction(
                generating = generating,
                enabled = generating || canSend,
                onClick = if (generating) onStop else onSend,
                radius = cornerRadius(radii.control, rightCorner),
            )
        }
    }
}

/**
 * The question box: `AppTextInput` from `legacy/flutter/lib/app_ui.dart:564` reduced to the slots the
 * chat filled — header, hint, error — with no prefix, helper, trailing widget or submit callback.
 *
 * `AppTextInput` is not in `core:design-system` and that module is outside this feature's scope, so this
 * is the same widget drawn here rather than beside `SearchQueryField`, which is the search sheet's copy
 * of it and says the same of itself. When the design-system module is next in scope both should be
 * replaced by it, rather than a third copy of it added.
 *
 * The box is controlled — [value] in, [onValueChange] out — where Flutter held a `TextEditingController`
 * and rebuilt from a listener. The screen already holds the question in its state, and a second copy
 * here would be a second thing to keep in step with it.
 */
@Composable
private fun AiChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    hint: String,
    header: (@Composable () -> Unit)?,
    autofocus: Boolean,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val message = error?.takeIf { it.isNotEmpty() }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Flutter's `FocusNode(autofocus: autofocus)`, asked for once rather than on every recomposition.
    LaunchedEffect(autofocus) {
        if (autofocus) focusRequester.requestFocus()
    }
    // The `AnimatedContainer(duration: Duration(milliseconds: 160))` around Flutter's outline. The error
    // wins over focus, so a failed send reads as a failure of the box rather than of the page above it.
    val outline by animateColorAsState(
        targetValue = when {
            message != null -> colors.danger
            focused -> colors.ink
            else -> colors.line
        },
        animationSpec = tween(OUTLINE_MILLIS),
        label = "chatInputOutline",
    )

    Column(modifier = modifier) {
        AppControlSurface(
            modifier = Modifier
                .testTag(COMPOSER_INPUT_TAG)
                .heightIn(min = BOX_MIN_HEIGHT),
            shape = RoundedCornerShape(radius),
            color = colors.surfaceRaised.copy(alpha = BOX_FILL_ALPHA),
            borderColor = outline,
            horizontalPadding = BOX_PADDING_HORIZONTAL,
            verticalPadding = BOX_PADDING_VERTICAL,
        ) {
            // Flutter's `mainAxisAlignment` was centre without a header and start with one, so a bare
            // question sits on the 48 in the middle rather than resting on its top edge. The 48 minus
            // the 5 either side is what makes that box the same height Flutter's constraint gave it.
            Column(
                modifier = Modifier.heightIn(min = BOX_MIN_HEIGHT - BOX_PADDING_VERTICAL * 2),
                verticalAlignment = if (header != null) Alignment.TopStart else Alignment.CenterStart,
                verticalArrangement = if (header != null) Arrangement.Top else Arrangement.Center,
            ) {
                if (header != null) {
                    header()
                    Spacer(modifier = Modifier.height(HEADER_GAP))
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        // The hint is drawn under the field and takes no touches, as Flutter's
                        // `IgnorePointer` made it; the field itself is what the reader touches.
                        Text(
                            text = hint,
                            maxLines = INPUT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            style = inputTextStyle(colors.muted),
                        )
                    }
                    // The selection colour is left to `AppTheme`, which pins the Flutter build's
                    // `0xFF376996` in `LocalTextSelectionColors`, where `BasicTextField` reads it from as
                    // Flutter's `EditableText` read `selectionColor` from the theme.
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused },
                        textStyle = inputTextStyle(colors.ink),
                        minLines = INPUT_MIN_LINES,
                        maxLines = INPUT_MAX_LINES,
                        cursorBrush = SolidColor(colors.ink),
                        // `TextInputAction.newline`: the return key writes a line break, so a question
                        // can be more than one line. Sending is the button's job, as it was in Dart.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                }
            }
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(ERROR_GAP))
            Text(
                text = message,
                color = colors.danger,
                fontFamily = AppFonts.OpenRunde,
                fontSize = ERROR_FONT_SIZE,
            )
        }
    }
}

/**
 * `_ScriptureAttachment` (`ai_chat_page.dart:1036`): the chapter the chat was opened on, as a chip above
 * the question, with the verse the reader tapped as its second line.
 *
 * The second line is the passage with its whitespace collapsed rather than as stored, because a chapter
 * runs to hundreds of characters and the chip shows one line of it. It is dropped altogether when it is
 * empty or when it is the reference itself, so the chip is never two lines saying the same thing.
 */
@Composable
private fun ScriptureAttachment(reference: String, text: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val preview = text.replace(WHITESPACE_RUN, " ").trim()
    AppControlSurface(
        modifier = modifier
            .testTag(ATTACHMENT_TAG)
            .widthIn(max = ATTACHMENT_MAX_WIDTH),
        shape = RoundedCornerShape(appRadii.compact),
        color = colors.surface,
        borderColor = colors.line,
    ) {
        Row(
            modifier = Modifier.padding(
                start = ATTACHMENT_PADDING_START,
                top = ATTACHMENT_PADDING_VERTICAL,
                end = ATTACHMENT_PADDING_END,
                bottom = ATTACHMENT_PADDING_VERTICAL,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyphView(glyph = AppGlyph.BOOK, color = colors.muted, size = ATTACHMENT_GLYPH_SIZE)
            Spacer(modifier = Modifier.width(ATTACHMENT_GLYPH_GAP))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reference,
                    fontFamily = AppFonts.OpenRunde,
                    fontSize = ATTACHMENT_REFERENCE_SIZE,
                    fontWeight = FontWeight.W600,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.isNotEmpty() && preview != reference) {
                    Text(
                        text = preview,
                        fontFamily = AppFonts.OpenRunde,
                        fontSize = ATTACHMENT_PREVIEW_SIZE,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AppControlSurface(
                shape = RoundedCornerShape(appRadii.compact),
                color = colors.surfaceRaised.copy(alpha = REMOVE_SURFACE_ALPHA),
            ) {
                // The name is on the glyph rather than dropped, for the same reason the copy and
                // regenerate actions put theirs there: the native `AppTap` has no label to hand.
                AppTap(
                    onClick = onRemove,
                    modifier = Modifier
                        .testTag(ATTACHMENT_REMOVE_TAG)
                        .size(REMOVE_SIZE),
                ) {
                    Box(
                        modifier = Modifier.size(REMOVE_SIZE),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppGlyphView(
                            glyph = AppGlyph.CLOSE,
                            color = colors.muted,
                            size = REMOVE_GLYPH_SIZE,
                            contentDescription = stringResource(R.string.remove_scripture_attachment),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `_ComposerAction` (`ai_chat_page.dart:1109`): the 48 square that sends the question and becomes the
 * stop while an answer is on its way.
 *
 * One control rather than two, because Dart had one: `onTap: generating ? onStop : onSend` with
 * `enabled: generating || canSend`, so it cannot be pressed to send nothing and is always pressable while
 * the answer can be stopped. It is the one control on the dock that is ink on a raised surface with an
 * ink border, which is what makes it read as the primary thing here.
 */
@Composable
private fun ComposerAction(generating: Boolean, enabled: Boolean, onClick: () -> Unit, radius: Dp) {
    val colors = appColors
    AppControlSurface(
        modifier = Modifier.testTag(if (generating) STOP_CONTROL_TAG else SEND_CONTROL_TAG),
        shape = RoundedCornerShape(radius),
        color = colors.surfaceRaised.copy(alpha = ACTION_SURFACE_ALPHA),
        borderColor = colors.ink.copy(alpha = ACTION_BORDER_ALPHA),
    ) {
        AppTap(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .testTag(if (generating) STOP_BUTTON_TAG else SEND_BUTTON_TAG)
                .size(ACTION_SIZE),
        ) {
            Box(
                modifier = Modifier.size(ACTION_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphView(
                    glyph = if (generating) AppGlyph.STOP else AppGlyph.SEND,
                    color = colors.ink,
                    size = if (generating) STOP_GLYPH_SIZE else SEND_GLYPH_SIZE,
                    contentDescription = stringResource(if (generating) R.string.stop else R.string.send),
                )
            }
        }
    }
}

/**
 * `math.max(radii.control, corner * .34).clamp(10.0, 24.0)` — the input's rounding and the button's, both
 * read off one of the device's bottom corners.
 */
private fun cornerRadius(control: Dp, corner: Dp): Dp =
    maxOf(minOf(maxOf(control, corner * CORNER_RADIUS_SCALE), CORNER_MAX), CORNER_MIN)

/** `fontFamily: 'OpenRunde', fontSize: 14, height: 1.45`, the two `TextStyle`s in Flutter's `AppTextInput`. */
private fun inputTextStyle(color: Color): TextStyle = TextStyle(
    color = color,
    fontFamily = AppFonts.OpenRunde,
    fontSize = INPUT_FONT_SIZE,
    lineHeight = INPUT_LINE_HEIGHT,
)

/** `math.max(radii.bottomLeft, bottomInset) * .24`, and the 10 under it. */
private const val CORNER_PADDING_SCALE = 0.24f
private val GUTTER_MIN = 10.dp

/** `math.max(radii.control, corner * .34).clamp(10.0, 24.0)`, the input's and the button's rounding. */
private const val CORNER_RADIUS_SCALE = 0.34f
private val CORNER_MIN = 10.dp
private val CORNER_MAX = 24.dp

/** The `ConstrainedBox(maxWidth: 760)` the page put around the dock. */
private val MEASURE_MAX = 760.dp

/** The `EdgeInsets.only(top: 8)` and the `SizedBox(width: 8)` beside it. */
private val DOCK_TOP_PADDING = 8.dp
private val ACTION_GAP = 8.dp

/** The `BoxConstraints(minHeight: 48)`, the `.72` fill and the `EdgeInsets.symmetric(horizontal: 13, vertical: 5)`. */
private val BOX_MIN_HEIGHT = 48.dp
private const val BOX_FILL_ALPHA = 0.72f
private val BOX_PADDING_HORIZONTAL = 13.dp
private val BOX_PADDING_VERTICAL = 5.dp

/** The `minLines: 1, maxLines: 5`, the `SizedBox(height: 5)` below the header, and the 14 at `height: 1.45`. */
private const val INPUT_MIN_LINES = 1
private const val INPUT_MAX_LINES = 5
private val HEADER_GAP = 5.dp
private val INPUT_FONT_SIZE = 14.sp
private val INPUT_LINE_HEIGHT = 20.3.sp

/** The `Duration(milliseconds: 160)` around the outline, and the 7 and 11 below it. */
private const val OUTLINE_MILLIS = 160
private val ERROR_GAP = 7.dp
private val ERROR_FONT_SIZE = 11.sp

/** The chip's `BoxConstraints(maxWidth: 360)` and its `EdgeInsets.fromLTRB(9, 7, 5, 7)`. */
private val ATTACHMENT_MAX_WIDTH = 360.dp
private val ATTACHMENT_PADDING_START = 9.dp
private val ATTACHMENT_PADDING_END = 5.dp
private val ATTACHMENT_PADDING_VERTICAL = 7.dp

/** The 17 book, the `SizedBox(width: 8)` after it, and the reference at 11 and the preview at 10. */
private val ATTACHMENT_GLYPH_SIZE = 17.dp
private val ATTACHMENT_GLYPH_GAP = 8.dp
private val ATTACHMENT_REFERENCE_SIZE = 11.sp
private val ATTACHMENT_PREVIEW_SIZE = 10.sp

/** `RegExp(r'\s+')`, which is what collapsed a chapter down to one line of the chip. */
private val WHITESPACE_RUN = Regex("""\s+""")

/** The remove button's 32 box, the `.7` surface it sits on and the 15 close inside it. */
private val REMOVE_SIZE = 32.dp
private const val REMOVE_SURFACE_ALPHA = 0.7f
private val REMOVE_GLYPH_SIZE = 15.dp

/** The 48 button, its `.86` surface, its `.72` border, and the 14 stop or the 19 send. */
private val ACTION_SIZE = 48.dp
private const val ACTION_SURFACE_ALPHA = 0.86f
private const val ACTION_BORDER_ALPHA = 0.72f
private val STOP_GLYPH_SIZE = 14.dp
private val SEND_GLYPH_SIZE = 19.dp

/** Flutter's `ValueKey`s, kept as tags so a test or a screenshot can find the same parts. */
private const val COMPOSER_INPUT_TAG = "ai-composer-input"
private const val ATTACHMENT_TAG = "ai-scripture-attachment"
private const val ATTACHMENT_REMOVE_TAG = "ai-scripture-attachment-remove"
private const val SEND_CONTROL_TAG = "ai-send-control"
private const val STOP_CONTROL_TAG = "ai-stop-control"
private const val SEND_BUTTON_TAG = "ai-send-button"
private const val STOP_BUTTON_TAG = "ai-stop-button"
