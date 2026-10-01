package com.marcow.bible.feature.library

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament

/**
 * Every measurement in the library, kept in one place for the same reason `ReaderLayout` keeps its
 * own: Flutter chose each of these against one width, and they were literals spread through three
 * widgets.
 *
 * The two surfaces that share these numbers are the sidebar and the panel, and they disagree about
 * width on purpose — the sidebar is a fixed column that shares the window with the reader, while the
 * panel is a sheet over it. What they must not disagree about is [libraryPanelWidth]'s cap, which is
 * the same 440 the Flutter panel used, and [SidebarChrome.chapterColumns], which is the five-column
 * grid a book of chapters is drawn in.
 */

/** `math.min(size.width * .92, 440)` — the panel covers most of the window, but never all of it. */
fun libraryPanelWidth(windowWidth: Dp): Dp = minOf(windowWidth * PanelWidthFraction, PanelWidthCap)

/** `SizedBox(width: 270)` in `_BibleHomeState` — the column the reader sits beside on a wide window. */
val SidebarWidth: Dp = 270.dp

/** The books in [testament], in canon order — `bibleBooks.indexed.where((entry) => entry.$2.old == old)`. */
fun booksInTestament(books: List<BibleBook>, testament: Testament): List<BibleBook> =
    books.filter { it.testament == testament }

/**
 * The two-digit canon number a book is listed under, `'${index + 1}'.padLeft(2, '0')`.
 *
 * It is the book's position in the whole Bible, not its position in its testament: Malachi is 39 on
 * a list that starts at Matthew, because the list is a countdown through the canon and the number is
 * there to say so. Two digits even for the first nine, so the column does not shuffle when a book
 * above them is read.
 */
fun libraryRowOrdinal(ordinal: Int): String = ordinal.toString().padStart(2, '0')

/** The 92% of the window the panel takes before its 440 cap applies. */
private const val PanelWidthFraction = 0.92f

/** The 440 the panel is never wider than, on a tablet or a landscape phone. */
private val PanelWidthCap = 440.dp

/**
 * The 420 ms `_BibleHomeState.transitionDuration` took the panel to arrive.
 *
 * `LibraryRoute` drives its own progress with this, and the navigation-compose destination drives it
 * through its slide in and out transitions, so both arrivals read the token here rather than each
 * repeating the number Flutter chose.
 */
const val LIBRARY_ARRIVE_MILLIS = 420

/** The 300 ms `reverseTransitionDuration` takes the panel back out again. */
const val LIBRARY_DISMISS_MILLIS = 300

/**
 * `Offset(-.12, 0)` — how far off the left edge the panel starts.
 *
 * A twelfth of the *panel*, not of the window, so the books travel with it and their columns never
 * shift under the reader while it moves. The Flutter build could say the same thing by measuring the
 * panel's own width rather than the page it was sliding across.
 */
const val LIBRARY_PANEL_SLIDE = 0.12f

/** `Color(0, 0, 0, .62)`, the scrim the `Container` drew over the reader behind the panel. */
const val LIBRARY_SCRIM_ALPHA = 0.62f

/** `Curves.easeInOutCubic` — the way the panel leaves, which is slower than the way it arrives. */
val LibraryDismissCurve: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

/**
 * How far to the left of its resting place the panel sits at [progress] on its way in, in pixels.
 *
 * Zero once it has arrived and [LIBRARY_PANEL_SLIDE] of [panelWidth] before it starts. Both arrival
 * paths go through this — the self-drawn sheet with its own measured width, the destination with the
 * width of the page it is sliding across — so the two can only ever differ in what they measure, and
 * never in how far they travel.
 */
fun libraryPanelSlideOffset(panelWidth: Float, progress: Float): Float =
    -LIBRARY_PANEL_SLIDE * panelWidth * (1f - progress)

/**
 * Where the panel starts on a page [pageWidth] pixels across, as a whole-pixel translation.
 *
 * [libraryPanelSlideOffset] is a float because a `graphicsLayer` wants one; a destination transition
 * wants an [androidx.compose.ui.unit.IntOffset], so the fraction is floored rather than rounded on
 * its way there.
 */
fun libraryPageSlideOffset(pageWidth: Int): Int = libraryPanelSlideOffset(pageWidth.toFloat(), 1f).toInt()

/**
 * Every measurement inside the sidebar that Flutter chose against its fixed width, rather than
 * against the window's.
 */
object SidebarChrome {
    val horizontalStart: Dp = 36.dp
    val horizontalEnd: Dp = 22.dp
    val bottom: Dp = 36.dp

    /** Below the status bar, which is where the reader's own top chrome begins. */
    val top: Dp = 102.dp

    /** The "Currently reading" label, and the gap under it before the book row. */
    val readingLabelSize = 9.sp
    val readingLabelSpacing = 2.sp
    val belowReadingLabel: Dp = 15.dp

    /** The book row: its own padding, the name beside the chevron, and the space under it. */
    val bookRowStart: Dp = 8.dp
    val bookRowTop: Dp = 8.dp
    val bookRowEnd: Dp = 8.dp
    val bookRowBottom: Dp = 16.dp
    val bookNameSize = 20.sp
    val bookChevronSize: Dp = 18.dp

    /** The rule under the book row, and the gap under the rule before the grid. */
    val ruleThickness: Dp = 1.dp
    val belowRule: Dp = 17.dp

    /**
     * The chapter grid: `SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 5)`, whose cells
     * are square because it defaults to a child aspect ratio of one.
     */
    val chapterColumns = 5
    val chapterSpacing: Dp = 5.dp
    val chapterNumberSize = 10.sp
    val chapterFillMillis = 220
}

/**
 * Every measurement inside the panel, the sheet that slides in over the reader.
 *
 * The panel's padding is asymmetric on purpose: `EdgeInsets.fromLTRB(28, 28, 28, 0)` left the bottom
 * to the list, which runs to the edge of the sheet. The last row therefore sits against the bottom of
 * the screen, which is where a list of books belongs, and the sheet's rounded corner stays visible
 * beside it.
 */
object PanelChrome {
    val horizontal: Dp = 28.dp
    val top: Dp = 28.dp

    /** The title, `Exposure` at 34 — the same face and size as the reader's chapter heading. */
    val titleSize = 34.sp

    /** Under the title, and under the segmented control. */
    val belowTitle: Dp = 26.dp
    val belowSegmented: Dp = 14.dp

    /** One book row, and the rule under it. */
    val rowVertical: Dp = 15.dp
    val rowHorizontal: Dp = 7.dp
    val rowNameSize = 15.sp
    val rowDetailSize = 10.sp

    /** The width of the canon number at the start of a row, `SizedBox(width: 42)`. */
    val rowOrdinalWidth: Dp = 42.dp
    val ruleThickness: Dp = 1.dp

    /** The close button at the start of the title row. */
    val closeSize: Dp = 40.dp
    val closeGlyphSize: Dp = 19.dp

    /** The list arriving: 430 in on the spring curve, 240 out on the way to the other testament. */
    val listEnterMillis = 430
    val listExitMillis = 240

    /** The fraction of its own height the list slides down as it arrives, `Offset(0, .025)`. */
    const val ListSlideFraction = 0.025f
}
