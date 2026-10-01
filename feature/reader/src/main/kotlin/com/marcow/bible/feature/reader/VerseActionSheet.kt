package com.marcow.bible.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.SpringCurve
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.model.VersePair

/**
 * The action sheet a long press on a verse opens, replacing `_openVerseActions`
 * (`legacy/flutter/lib/main.dart:861`) and the `_ActionTile`s at `main.dart:2739`.
 *
 * Copy is always here: it is the one action the reader can complete on its own. Ask AI and Explain
 * are shown only when [onAction] is there, which is the same condition Flutter gated them on —
 * `if (ai.isSupported)` — read the other way round. The Ask feature arrives in Phase 3, and a sheet
 * with two dead rows in it is worse than a sheet with one row in it.
 *
 * A [Popup] rather than a `ModalBottomSheet`, for the same reason the chapter picker is one: this is
 * a hand-drawn dialog in Flutter, with its own surface, border and grabber, and Material's sheet
 * brings an inset, a drag handle and a scrim of its own that would have to be fought rather than
 * reproduced.
 */
@Composable
fun VerseActionSheet(
    reference: String,
    verse: VersePair,
    chapterContext: String,
    onAction: ((VerseAction, ScriptureRequest) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val explainQuestion = stringResource(R.string.explain_scripture_prompt, reference)
    val request = scriptureRequest(
        action = VerseAction.ASK_AI,
        reference = reference,
        verse = verse,
        chapterContext = chapterContext,
        explainQuestion = explainQuestion,
    )
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = EnterAnimationMillis, easing = SpringCurve))
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScrimColor)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Flutter slid the sheet up from 12% of the dialog's height, which is the
                        // window's, so the offset is a fraction of the screen rather than of the
                        // sheet — a tall sheet and a short one start from the same place.
                        translationY = (1f - progress.value) * EnterOffsetFraction * screenHeight.toPx()
                        alpha = progress.value
                    }
                    .padding(
                        start = SheetMargin,
                        end = SheetMargin,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            SheetMargin,
                    )
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(appRadii.surface))
                    .background(appColors.surfaceRaised)
                    .border(BorderStroke(1.dp, appColors.line), RoundedCornerShape(appRadii.surface))
                    .padding(start = SheetPadding, top = SheetPadding, end = SheetPadding, bottom = 12.dp),
            ) {
                // The grabber: 32 by 3, in `faint`, and nothing else — Flutter's `Container` with no
                // radius, so it is a bar rather than a dot.
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 3.dp)
                        .background(appColors.faint),
                )
                Spacer(Modifier.height(GrabberBelow))
                ActionTile(
                    glyph = AppGlyph.COPY,
                    label = stringResource(R.string.copy_scripture),
                    onClick = {
                        clipboard.setText(AnnotatedString(request.text))
                        onDismiss()
                    },
                )
                if (onAction != null) {
                    ActionTile(
                        glyph = AppGlyph.CHAT,
                        label = stringResource(R.string.ask_ai),
                        onClick = { onAction(VerseAction.ASK_AI, request); onDismiss() },
                    )
                    ActionTile(
                        glyph = AppGlyph.BOOK,
                        label = stringResource(R.string.explain_scripture),
                        onClick = {
                            onAction(VerseAction.EXPLAIN, request.copy(question = explainQuestion))
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** `_ActionTile`: a 54 dp row, a glyph, a gap of 13, and the label — 7 dp below each one. */
@Composable
private fun ActionTile(glyph: AppGlyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.surface)
    AppTap(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = TileBelow)
            .height(TileHeight)
            .clip(shape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.line), shape)
            .semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyphView(glyph = glyph, color = colors.ink, size = TileGlyphSize)
            Spacer(Modifier.width(TileGlyphGap))
            Text(text = label, color = colors.ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 200 ms on `springCurve`, the transition of Flutter's `showGeneralDialog`. */
private const val EnterAnimationMillis = 200

/** `.12`, how far down the screen the sheet starts. */
private const val EnterOffsetFraction = 0.12f

/** `Colors.black.withValues(alpha: .62)`, the barrier Flutter dimmed the reader by. */
private val ScrimColor = Color(0x9E000000)

/** `EdgeInsets.fromLTRB(10, 0, 10, 10)`, the sheet's own margin. */
private val SheetMargin = 10.dp

/** `EdgeInsets.fromLTRB(12, 10, 12, 12)`, the sheet's own padding. */
private val SheetPadding = 12.dp

private val GrabberBelow = 10.dp

private val TileHeight = 54.dp
private val TileBelow = 7.dp
private val TileGlyphSize = 19.dp
private val TileGlyphGap = 13.dp
