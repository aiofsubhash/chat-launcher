package com.subhash.chatlauncher

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Real HTML block/inline tags. The old loose check (`<[a-z]+[ >]`) mistook code-heavy
 * answers - generics like `Map<String, Boolean>`, comparisons like `a < b` - for markup
 * and flipped the settled reply into the WebView, so the text visibly changed size and
 * layout the moment streaming finished (device recording Nov 7 00:30).
 */
private val HTML_BLOCK = Regex(
    """</?(html|head|body|div|p|span|ul|ol|li|dl|dt|dd|table|thead|tbody|tr|td|th|caption|h[1-6]|br|hr|section|article|aside|header|footer|nav|main|figure|figcaption|blockquote|pre|strong|em|ins|del|mark|small|sub|sup)(\s[^<>]*)?/?>""",
    RegexOption.IGNORE_CASE,
)

/** True only for answers that are actually HTML documents/fragments, not code text. */
fun looksLikeHtml(text: String): Boolean = HTML_BLOCK.containsMatchIn(text)

private val FENCE = Regex("""^\s*```""")
private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private val HEADING = Regex("""^\s{0,3}#{1,6}\s+(.*\S)\s*$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val INLINE_MD = Regex("""\*\*(.+?)\*\*|__([^_]+)__|\*([^*\n]+)\*|`([^`\n]+)`""")

private data class MdLine(val text: String, val code: Boolean, val heading: Boolean)

/**
 * Minimal calm Markdown -> AnnotatedString: headings bold, **bold**, *italic*, `code`
 * and fenced blocks monospace, horizontal rules and fence markers dropped, `- ` bullets
 * become `• `. Newlines are preserved so answers keep their paragraph structure.
 */
fun parseMarkdown(text: String): AnnotatedString {
    val out = ArrayList<MdLine>()
    var inCode = false
    for (raw0 in text.split('\n')) {
        val raw = raw0.trimEnd('\r')
        if (FENCE.containsMatchIn(raw)) { inCode = !inCode; continue }
        if (inCode) { out.add(MdLine(raw, code = true, heading = false)); continue }
        if (RULE.matches(raw)) continue
        val heading = HEADING.find(raw)
        val bullet = if (heading == null) BULLET.find(raw) else null
        when {
            heading != null -> out.add(MdLine(heading.groupValues[1], code = false, heading = true))
            bullet != null -> out.add(MdLine(bullet.groupValues[1] + "• " + bullet.groupValues[2], code = false, heading = false))
            else -> out.add(MdLine(raw, code = false, heading = false))
        }
    }
    // Dropped rules/fences can leave double blank lines - collapse 3+ newlines to 2.
    val collapsed = ArrayList<MdLine>(out.size)
    var blanks = 0
    for (l in out) {
        if (l.text.isBlank() && !l.code) { blanks++; if (blanks > 1) continue } else blanks = 0
        collapsed.add(l)
    }
    return buildAnnotatedString {
        collapsed.forEachIndexed { i, line ->
            if (i > 0) append('\n')
            when {
                line.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(line.text) }
                else -> appendInlineMarkdown(line.text, bold = line.heading)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendStyled(text: String, bold: Boolean, italic: Boolean = false, code: Boolean = false) {
    if (text.isEmpty()) return
    val style = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = if (code) FontFamily.Monospace else null,
    )
    if (style == SpanStyle()) append(text) else withStyle(style) { append(text) }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, bold: Boolean = false) {
    var last = 0
    for (m in INLINE_MD.findAll(text)) {
        appendStyled(text.substring(last, m.range.first), bold)
        when {
            m.groupValues[1].isNotEmpty() -> appendStyled(m.groupValues[1], bold = true)
            m.groupValues[2].isNotEmpty() -> appendStyled(m.groupValues[2], bold = true)
            m.groupValues[3].isNotEmpty() -> appendStyled(m.groupValues[3], bold, italic = true)
            m.groupValues[4].isNotEmpty() -> appendStyled(m.groupValues[4], bold, code = true)
        }
        last = m.range.last + 1
    }
    appendStyled(text.substring(last), bold)
}

/** Assistant reply body: Markdown rendered, one text size for streaming and settled text. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Unspecified,
    fontFamily: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val parsed = remember(text) { parseMarkdown(text) }
    Text(
        parsed,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
        fontFamily = fontFamily,
        maxLines = maxLines,
        overflow = overflow,
    )
}
