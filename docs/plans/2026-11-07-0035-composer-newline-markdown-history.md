# Plan: composer newline, markdown reply rendering, History progressive disclosure

Device evidence: WhatsApp screen recording Nov 7 00:30:56 + voiceover + two follow-up texts.

## Findings (grounded in the recording)

1. **No newline in composer.** Voiceover: "I couldn't send a new line. Keyboard doesn't
   have a new line." Text: "I need a new line too." Cause: `BasicTextField` uses
   `KeyboardOptions(imeAction = ImeAction.Send)` + `KeyboardActions(onSend)`, so the
   keyboard offers Send instead of a return key.
2. **Reply rendering broke.** Voiceover: "Something broke very clearly." The cloud reply
   shows raw Markdown (`**...**`, `###`, `---`, ``` fences) and visibly changes size:
   while streaming it renders as plain `Text` (~16sp); on completion the loose
   `Regex("<[a-z]+[ >]")` HTML check flips code-heavy answers (generics like
   `Map<String, ...>`, comparisons) into the WebView `HtmlBubble`, which renders smaller
   and with different line handling. Same raw-Markdown issue in Settings > History.
3. **History needs progressive disclosure.** Text: "collapse everything expect, show my
   query I'll click to expand." Current History lists every entry fully expanded.

## Changes

- Composer: `ImeAction.Default`, drop `onSend` keyboard action (Send button unchanged),
  multi-line field with a max height so a long draft cannot eat the chat.
- New `MarkdownText.kt`: `parseMarkdown` (headings -> bold, `**bold**`, `*italic*`,
  `code`/fences -> monospace, `---` rules dropped, `- ` bullets -> `• `, newlines
  preserved) and `looksLikeHtml` requiring real HTML block tags. One renderer for
  streaming and settled replies: same size, no WebView flip for code.
- `MainActivity`: assistant text (chat + History expanded) renders through MarkdownText;
  `isHtml` uses `looksLikeHtml`.
- `HistoryStore.kt`: `groupHistoryTurns` groups entries into turns (a user line starts a
  turn; following assistant lines are its responses).
- Settings > History: collapsed rows show timestamp + only the user's query (2-line
  clamp); tap expands the full exchange (query + responses, markdown-rendered); tap again
  collapses. Assistant-only turns show the first response as the collapsed preview.

## Tests

- `MarkdownTextTest` (JVM): markers stripped, styles applied, newlines kept, rules/fences
  handled, `looksLikeHtml` false for generics/comparisons, true for real tags.
- `HistoryTurnsTest` (JVM): grouping, assistant-only turns, newest-first order.
- `HistoryDisclosureTest` (Robolectric): collapsed shows query and hides response; tap
  expands; tap collapses; markdown markers not shown; Roborazzi captures for visual check.

## Ship

versionCode 8 / 0.5.3, same signing identity, update-only Drive delivery. No CI secrets,
no enrollment ticket, FREE-only AI unchanged.
