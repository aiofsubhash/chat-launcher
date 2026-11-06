package com.subhash.chatlauncher

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown parsing and HTML detection behind the reply renderer (device Nov 7 00:30 recording). */
class MarkdownTextTest {

    @Test
    fun boldMarkersRenderAsBoldTextWithoutAsterisks() {
        val s = parseMarkdown("places where **logic depends** on it")
        assertEquals("places where logic depends on it", s.text)
        val bold = s.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("logic depends", s.text.substring(bold.start, bold.end))
    }

    @Test
    fun headingsLoseHashesAndStayBold() {
        val s = parseMarkdown("### 1. Search for Boolean Combinations")
        assertEquals("1. Search for Boolean Combinations", s.text)
        assertTrue(s.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun rulesAndFenceMarkersAreDroppedButCodeIsKeptMonospace() {
        val s = parseMarkdown("before\n---\n```javascript\nif (isLoading && !isError) { ... }\n```\nafter")
        assertEquals("before\nif (isLoading && !isError) { ... }\nafter", s.text)
        assertFalse(s.text.contains("```"))
        assertFalse(s.text.contains("---"))
        val mono = s.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("if (isLoading && !isError) { ... }", s.text.substring(mono.start, mono.end))
    }

    @Test
    fun newlinesAndParagraphStructureSurvive() {
        val s = parseMarkdown("one\n\ntwo")
        assertEquals("one\n\ntwo", s.text)
    }

    @Test
    fun italicAndInlineCodeAreStyled() {
        val s = parseMarkdown("say *hi* then `x++`")
        assertEquals("say hi then x++", s.text)
        assertTrue(s.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertTrue(s.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun bulletsBecomeCalmDots() {
        val s = parseMarkdown("* first point")
        assertEquals("• first point", s.text)
    }

    @Test
    fun codeGenericsAndComparisonsAreNotHtml() {
        assertFalse(looksLikeHtml("a Map<String, Boolean> of flags"))
        assertFalse(looksLikeHtml("if (a < b) { return }"))
        assertFalse(looksLikeHtml("List<Event> transitions<S, E>"))
        assertFalse(looksLikeHtml("when x < 3 && y > 2"))
    }

    @Test
    fun realHtmlDocumentsAreDetected() {
        assertTrue(looksLikeHtml("<div class=\"answer\">hi</div>"))
        assertTrue(looksLikeHtml("<p>Hello</p>"))
        assertTrue(looksLikeHtml("<html><body>hi</body></html>"))
        assertTrue(looksLikeHtml("text with <br> a break"))
    }

    @Test
    fun plainMarkdownIsNotHtml() {
        val md = "Here is a framework: --- ### 1. Search for \"Boolean Combinations\" **most common** type"
        assertFalse(looksLikeHtml(md))
    }
}
