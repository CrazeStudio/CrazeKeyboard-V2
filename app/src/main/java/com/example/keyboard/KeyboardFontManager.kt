package com.example.keyboard

enum class FontStyle(val id: String, val displayName: String, val previewText: String) {
    NORMAL("normal", "Normal", "Default"),
    BOLD_SANS("bold_sans", "Bold", "𝗕𝗼𝗹𝗱"),
    ITALIC_SANS("italic_sans", "Italic", "𝘐𝘵𝘢𝘭𝘪𝘤"),
    SCRIPT("script", "Cursive", "𝓢𝓬𝓻𝓲𝓹𝓽"),
    MONOSPACE("monospace", "Mono", "𝙼𝚘𝚗𝚘"),
    DOUBLE_STRUCK("double_struck", "Blackboard", "𝔹𝕝𝕒𝕔𝕜"),
    CIRCLED("circled", "Bubble", "Ⓑⓤⓑⓑⓛⓔ"),
    GOTHIC("gothic", "Gothic", "𝔊𝔬𝔱𝔥𝔦𝔠"),
    FULLWIDTH("fullwidth", "Aesthetic", "Ｆｕｌｌ"),
    UNDERLINE("underline", "Underline", "U̲n̲d̲e̲r̲"),
    STRIKETHROUGH("strikethrough", "Strike", "S̶t̶r̶i̶k̶e̶"),
    SMALL_CAPS("small_caps", "Small Caps", "sᴍᴀʟʟ")
}

object KeyboardFontManager {
    val allStyles: List<FontStyle> = FontStyle.values().toList()

    fun transform(text: String, styleId: String): String {
        if (styleId.isEmpty() || styleId == "normal" || styleId == "NORMAL") return text
        val style = allStyles.firstOrNull { it.id == styleId } ?: FontStyle.NORMAL
        if (style == FontStyle.NORMAL) return text

        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            sb.append(transformChar(ch, style))
        }
        return sb.toString()
    }

    private fun transformChar(ch: Char, style: FontStyle): String {
        return when (style) {
            FontStyle.NORMAL -> ch.toString()

            FontStyle.BOLD_SANS -> {
                when (ch) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D5D4 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D5EE + (ch - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7EC + (ch - '0')))
                    else -> ch.toString()
                }
            }

            FontStyle.ITALIC_SANS -> {
                when (ch) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D608 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D622 + (ch - 'a')))
                    else -> ch.toString()
                }
            }

            FontStyle.SCRIPT -> {
                when (ch) {
                    // Mathematical script uppercase
                    in 'A'..'Z' -> String(Character.toChars(0x1D4D0 + (ch - 'A')))
                    // Mathematical script lowercase
                    in 'a'..'z' -> String(Character.toChars(0x1D4EA + (ch - 'a')))
                    else -> ch.toString()
                }
            }

            FontStyle.MONOSPACE -> {
                when (ch) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D670 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D68A + (ch - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7F6 + (ch - '0')))
                    else -> ch.toString()
                }
            }

            FontStyle.DOUBLE_STRUCK -> {
                when (ch) {
                    'C' -> "ℂ"
                    'H' -> "ℍ"
                    'N' -> "ℕ"
                    'P' -> "ℙ"
                    'Q' -> "ℚ"
                    'R' -> "ℝ"
                    'Z' -> "ℤ"
                    in 'A'..'Z' -> String(Character.toChars(0x1D538 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D552 + (ch - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7D8 + (ch - '0')))
                    else -> ch.toString()
                }
            }

            FontStyle.CIRCLED -> {
                when (ch) {
                    in 'A'..'Z' -> String(Character.toChars(0x24B6 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x24D0 + (ch - 'a')))
                    '0' -> "⓪"
                    in '1'..'9' -> String(Character.toChars(0x2460 + (ch - '1')))
                    else -> ch.toString()
                }
            }

            FontStyle.GOTHIC -> {
                when (ch) {
                    'C' -> "ℭ"
                    'H' -> "ℌ"
                    'I' -> "ℑ"
                    'R' -> "ℜ"
                    'Z' -> "ℨ"
                    in 'A'..'Z' -> String(Character.toChars(0x1D504 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D51E + (ch - 'a')))
                    else -> ch.toString()
                }
            }

            FontStyle.FULLWIDTH -> {
                when (ch) {
                    in 'A'..'Z' -> String(Character.toChars(0xFF21 + (ch - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0xFF41 + (ch - 'a')))
                    in '0'..'9' -> String(Character.toChars(0xFF10 + (ch - '0')))
                    ' ' -> "\u3000"
                    else -> ch.toString()
                }
            }

            FontStyle.UNDERLINE -> {
                if (ch.isWhitespace()) ch.toString() else "$ch\u0332"
            }

            FontStyle.STRIKETHROUGH -> {
                if (ch.isWhitespace()) ch.toString() else "$ch\u0336"
            }

            FontStyle.SMALL_CAPS -> {
                when (ch.lowercaseChar()) {
                    'a' -> "ᴀ"
                    'b' -> "ʙ"
                    'c' -> "ᴄ"
                    'd' -> "ᴅ"
                    'e' -> "ᴇ"
                    'f' -> "ꜰ"
                    'g' -> "ɢ"
                    'h' -> "ʜ"
                    'i' -> "ɪ"
                    'j' -> "ᴊ"
                    'k' -> "ᴋ"
                    'l' -> "ʟ"
                    'm' -> "ᴍ"
                    'n' -> "ɴ"
                    'o' -> "ᴏ"
                    'p' -> "ᴘ"
                    'q' -> "ǫ"
                    'r' -> "ʀ"
                    's' -> "s"
                    't' -> "ᴛ"
                    'u' -> "ᴜ"
                    'v' -> "ᴠ"
                    'w' -> "ᴡ"
                    'x' -> "x"
                    'y' -> "ʏ"
                    'z' -> "ᴢ"
                    else -> ch.toString()
                }
            }
        }
    }
}
