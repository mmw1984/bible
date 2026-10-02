package com.marcow.bible.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.platform.LocalInspectionMode
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
    val explainQuestion = stringResource(R.string.explain_scripture_prompt, reference)
    val request = scriptureRequest(
        action = VerseAction.ASK_AI,
        reference = reference,
        verse = verse,
        chapterContext = chapterContext,
        explainQuestion = explainQuestion,
    )
    // A preview and a golden render one frame, and `Animatable` anchors to the first frame it sees,
    // so a sheet that starts at 0 would be captured fully transparent. Start it open instead.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(progress, inspection) {
        if (inspection) return@LaunchedEffect
        // Linear, because Flutter's two channels were on two curves: the slide ran on
        // `Curves.easeOutCubic` and the fade on the bare route animation. The easing is applied at
        // the slide below, where Flutter applied it. `tween`'s own default is
        // `FastOutSlowInEasing`, so `LinearEasing` is named rather than left to the default.
        progress.animateTo(1f, tween(durationMillis = EnterAnimationMillis, easing = LinearEasing))
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        VerseActionSheetContent(
            request = request,
            progress = progress.value,
            onAction = onAction,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

/**
 * What the [Popup] above holds: the scrim, and the sheet rising to [progress].
 *
 * **It is split out because a golden cannot reach inside a `Popup`.** A `Popup` composes into its own
 * window, which is exactly what makes it float over the reader rather than displace it, and it is
 * also why layoutlib — which draws one view hierarchy into a bitmap — has no way to be asked to
 * include it in a snapshot. Snapshotting [VerseActionSheet] directly would therefore pin whether the
 * platform happens to composite popups into a render, which is a claim about the harness and not
 * about this sheet. Snapshotting this instead pins the sheet's own pixels: the grabber, the three
 * rows, the 12 dp of padding and the 54 dp of each tile.
 *
 * Nothing else changes. The `Popup` still owns the dismissal contract — the back press and the
 * outside tap reach [onDismiss] from here and from `onDismissRequest` above — and this stays
 * internal to the module because it is a seam for the golden rather than a second public sheet.
 */
@Composable
internal fun VerseActionSheetContent(
    request: ScriptureRequest,
    progress: Float,
    onAction: ((VerseAction, ScriptureRequest) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // Explain's opening question is the reference and a localised frame around it, and it is asked
    // for off the request rather than carried on it: Ask sends the same request with a null
    // question, so one request serves both rows and only Explain supplies the text.
    val explainQuestion = stringResource(R.string.explain_scripture_prompt, request.reference)
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
                    //
                    // The slide is the only channel `Curves.easeOutCubic` touched
                    // (`legacy/flutter/lib/main.dart:962`); the fade beside it was the bare route
                    // animation, so [progress] goes into [alpha] untransformed. Easing one value and
                    // spending it on both would have given the sheet a spring Flutter never ran.
                    val slide = EaseOutCubic.transform(progress)
                    translationY = (1f - slide) * EnterOffsetFraction * screenHeight.toPx()
                    alpha = progress
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
            verseActionRows(askSupported = onAction != null).forEach { row ->
                ActionTile(
                    glyph = verseActionGlyph(row),
                    label = stringResource(verseActionLabel(row)),
                    onClick = {
                        when (row) {
                            VerseActionRow.COPY -> clipboard.setText(AnnotatedString(request.text))
                            // A safe call because the row is only drawn when there is one: the list
                            // above was built from that same `onAction`. Flutter wrote two
                            // `if (ai.isSupported)`s that agreed, and this is the one flag.
                            VerseActionRow.ASK_AI -> onAction?.invoke(VerseAction.ASK_AI, request)
                            VerseActionRow.EXPLAIN -> onAction?.invoke(
                                VerseAction.EXPLAIN,
                                request.copy(question = explainQuestion),
                            )
                        }
                        onDismiss()
                    },
                )
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

/**
 * One row in the sheet, which is one more than there are [VerseAction]s.
 *
 * Copy is a row and not an action because it asks nobody for anything: it hands the verse to the
 * clipboard and the reader is finished. That is why it is the row that survives when there is no host
 * to send the other two to, and why [VerseAction] stays a two-value enum describing the two
 * `_openAiChat` calls Flutter made (`legacy/flutter/lib/main.dart:923`) rather than a third it did not.
 */
enum class VerseActionRow {
    COPY,
    ASK_AI,
    EXPLAIN,
}

/**
 * The rows, in the order Flutter listed its three `_ActionTile`s: `:910`, `:919`, `:932`.
 *
 * One row or three, and one is what the app ships today. Flutter gated the two question rows on
 * `ai.isSupported`, and Phase 2 has no Ask feature to be supported, so a long press opens a sheet with
 * a single row in it — the same shape the feature had before `ai` could be anything else, and the
 * reason [VerseActionSheet]'s own note calls a sheet with two dead rows in it worse than a sheet with
 * one.
 *
 * Copy is first either way and is never the row that goes. A sheet whose only row were the one that
 * needed nothing would be a sheet with nothing in it, which is what [askSupported] is for.
 */
fun verseActionRows(askSupported: Boolean): List<VerseActionRow> = if (askSupported) {
    listOf(VerseActionRow.COPY, VerseActionRow.ASK_AI, VerseActionRow.EXPLAIN)
} else {
    listOf(VerseActionRow.COPY)
}

/**
 * The string each row is named by, and the only sentence in the sheet.
 *
 * `context.l10n.copyScripture`, `askAi` and `explainScripture` at
 * `legacy/flutter/lib/main.dart:912`, `:921` and `:934` — Flutter's, and one per row, because
 * Flutter's `_ActionTile` drew the label twice: once as the `AppTap`'s `label` and once as the `Text`
 * inside it, so a row announced itself and then repeated itself. [ActionTile] here is one `AppTap`
 * whose `contentDescription` is the label it also draws, which is the same one announcement.
 */
fun verseActionLabel(row: VerseActionRow): Int = when (row) {
    VerseActionRow.COPY -> R.string.copy_scripture
    VerseActionRow.ASK_AI -> R.string.ask_ai
    VerseActionRow.EXPLAIN -> R.string.explain_scripture
}

/**
 * The glyph on each row — Flutter's own three, unchanged.
 *
 * `AppGlyph.copy`, `.chat` and `.book` at `:911`, `:920` and `:933`, so unlike the reader's Ask and
 * Devotions buttons there is nothing to decide here: a copy glyph on the row that copies is not the
 * place to introduce a second icon language.
 */
fun verseActionGlyph(row: VerseActionRow): AppGlyph = when (row) {
    VerseActionRow.COPY -> AppGlyph.COPY
    VerseActionRow.ASK_AI -> AppGlyph.CHAT
    VerseActionRow.EXPLAIN -> AppGlyph.BOOK
}

/** 200 ms, the transition duration of Flutter's `showGeneralDialog` for the sheet. */
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
