package com.crazestudio.crazekeyboard

/**
 * Conservative local formatting only. This intentionally does not claim to be an AI rewrite model.
 * It fixes common spacing and sentence-capitalization issues without changing vocabulary or meaning.
 */
internal object LocalTextPolisher {
    fun polish(source: String): String {
        if (source.isBlank()) return source
        var value = source
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("[ ]+([,.;:!?])")) { it.groupValues[1] }
            .replace(Regex("([,;:!?])(?=\\S)")) { it.value + " " }
            .replace(Regex("\\.{4,}"), "...")
            .trim()

        value = Regex("(^|[.!?]\\s+)([a-z])").replace(value) { match ->
            match.groupValues[1] + match.groupValues[2].uppercase()
        }
        value = Regex("\\bi\\b").replace(value, "I")
        return value
    }
}
