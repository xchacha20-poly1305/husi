package fr.husi.ui.jsoneditor

import androidx.compose.ui.util.fastCoerceAtLeast

internal const val highlightChunkLines = 64

internal fun highlightedLineRange(
    scrollOffsetPx: Int,
    viewportHeightPx: Int,
    lineHeightPx: Int,
): IntRange {
    if (lineHeightPx <= 0) {
        return 0 until highlightChunkLines
    }
    val firstVisibleLine = (scrollOffsetPx / lineHeightPx).fastCoerceAtLeast(0)
    val lastVisibleLine = ((scrollOffsetPx + viewportHeightPx) / lineHeightPx)
        .fastCoerceAtLeast(firstVisibleLine)
    val firstChunk = firstVisibleLine / highlightChunkLines
    val lastChunk = lastVisibleLine / highlightChunkLines
    return firstChunk * highlightChunkLines until (lastChunk + 1) * highlightChunkLines
}
