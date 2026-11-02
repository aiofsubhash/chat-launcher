package com.subhash.chatlauncher

/**
 * Deterministic message-intent parser. No AI, no network: the phrasings the owner
 * actually tries ("send a message to Sumit on WhatsApp", "send Sumit hello on whatsapp",
 * "send Avinash: running late", "message Sumit on telegram saying hi") resolve here.
 * Anything it cannot fully parse stays local - the caller asks instead of calling out.
 *
 * Heuristic (documented, tested): without a separator (":" or "saying"), a name before
 * the channel word is its first word and the rest is the message text.
 */
object MessageIntents {

    data class Parsed(
        val to: String,
        /** Channel word as spoken ("whatsapp"), null when not named. */
        val channel: String?,
        /** Resolved package for a known channel; null when unknown or unnamed. */
        val pkg: String?,
        /** Message body; null when not given yet - the caller asks for it inline. */
        val text: String?,
    )

    /** Known channel words -> package. Unknown channel words still parse, pkg stays null. */
    private val CHANNEL_PACKAGES = mapOf(
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "signal" to "org.thoughtcrime.securesms",
        "gmail" to "com.google.android.gm",
        "instagram" to "com.instagram.android",
    )

    private const val LEAD = "(?:let'?s\\s+|please\\s+|hey\\s*,?\\s+)*"
    private const val VERB = "(?:send|message|text)"
    private const val SEP = "(?:\\s*[:;]\\s*|\\s+saying\\s+|\\s+that\\s+)"

    // "send [a|this] message to NAME [on CHANNEL] : TEXT"
    private val MESSAGE_TO_SEP = Regex(
        """(?i)^$LEAD$VERB\s+(?:a\s+|this\s+)?message\s+to\s+(.+?)(?:\s+(?:on|via|using)\s+(\w+))?$SEP(.+)$"""
    )
    // "send [a|this] message to NAME [on CHANNEL [TEXT]]"
    private val MESSAGE_TO = Regex(
        """(?i)^$LEAD$VERB\s+(?:a\s+|this\s+)?message\s+to\s+(.+?)(?:\s+(?:on|via|using)\s+(\w+)(?:\s+(.+))?)?$"""
    )
    // "send NAME on CHANNEL [:|saying] TEXT" / "send NAME on CHANNEL TEXT"
    private val NAME_CHANNEL_TEXT = Regex(
        """(?i)^$LEAD$VERB\s+(.+?)\s+(?:on|via|using)\s+(\w+)(?:$SEP|\s+)(.+)$"""
    )
    // "send TEXT on CHANNEL" is ambiguous; channel-at-end splits first word as the name:
    // "send Sumit hello on whatsapp" -> to Sumit, text hello.
    private val NAME_TEXT_CHANNEL = Regex(
        """(?i)^$LEAD$VERB\s+(.+?)\s+(?:on|via|using)\s+(\w+)$"""
    )
    // "send NAME: TEXT"
    private val NAME_COLON_TEXT = Regex(
        """(?i)^$LEAD$VERB\s+(.+?)\s*[:;]\s*(.+)$"""
    )

    fun parse(raw: String): Parsed? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        MESSAGE_TO_SEP.find(input)?.let { m ->
            return build(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        NAME_CHANNEL_TEXT.find(input)?.let { m ->
            // Guard: "send message to ..." belongs to the MESSAGE_TO shapes above.
            if (!m.groupValues[1].startsWith("message ", true) && !m.groupValues[1].startsWith("a message", true)) {
                return build(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            }
        }
        NAME_TEXT_CHANNEL.find(input)?.let { m ->
            val before = m.groupValues[1].trim()
            if (!before.startsWith("message ", true) && !before.startsWith("a message", true)) {
                val words = before.split(Regex("\\s+"), limit = 2)
                val to = words[0]
                val text = words.getOrNull(1)
                return build(to, m.groupValues[2], text)
            }
        }
        NAME_COLON_TEXT.find(input)?.let { m ->
            if (!m.groupValues[1].contains("""\s(on|via|using)\s""".toRegex(RegexOption.IGNORE_CASE))) {
                return build(m.groupValues[1], null, m.groupValues[2])
            }
        }
        MESSAGE_TO.find(input)?.let { m ->
            return build(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifBlank { null })
        }
        return null
    }

    private fun build(to: String, channel: String?, text: String?): Parsed? {
        val name = to.trim().trimEnd(',', ':')
        if (name.isEmpty()) return null
        val ch = channel?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return Parsed(
            to = name,
            channel = ch,
            pkg = ch?.let { CHANNEL_PACKAGES[it] },
            text = text?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * True when the text reads like a messaging/action command. Used to keep FAILED parses
     * local: the app asks a clarifying question instead of sending private text to a cloud AI.
     */
    fun looksPrivateAction(raw: String): Boolean =
        Regex("""(?i)^(?:let'?s\s+|please\s+|hey\s*,?\s+)*(?:send|message|text|write|whatsapp|telegram|email|mail|call)\b""")
            .containsMatchIn(raw.trim())
}
