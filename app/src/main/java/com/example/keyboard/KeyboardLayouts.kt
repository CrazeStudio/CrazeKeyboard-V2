package com.example.keyboard

import com.example.R

object KeyboardLayouts {
    // Number Row (Optional top row with full superscripts, subscripts, fractions)
    val numberRow: List<KeyModel> = listOf(
        KeyModel("1", alternateText = "¹", longPressOptions = listOf("1", "¹", "₁", "½", "⅓", "¼", "⅕", "⅙", "⅐", "⅛", "⅑", "⅒")),
        KeyModel("2", alternateText = "²", longPressOptions = listOf("2", "²", "₂", "⅔", "½")),
        KeyModel("3", alternateText = "³", longPressOptions = listOf("3", "³", "₃", "¾", "⅜")),
        KeyModel("4", alternateText = "⁴", longPressOptions = listOf("4", "⁴", "₄", "¢", "€", "£", "¥", "₹")),
        KeyModel("5", alternateText = "⁵", longPressOptions = listOf("5", "⁵", "₅", "⅝", "‰")),
        KeyModel("6", alternateText = "⁶", longPressOptions = listOf("6", "⁶", "₆")),
        KeyModel("7", alternateText = "⁷", longPressOptions = listOf("7", "⁷", "₇", "⅞")),
        KeyModel("8", alternateText = "⁸", longPressOptions = listOf("8", "⁸", "₈", "∞")),
        KeyModel("9", alternateText = "⁹", longPressOptions = listOf("9", "⁹", "₉")),
        KeyModel("0", alternateText = "⁰", longPressOptions = listOf("0", "⁰", "₀", "ⁿ", "∅", "°", "•"))
    )

    // QWERTY Layout with full Gboard-style international accents and characters
    val qwertyRows: List<List<KeyModel>> = listOf(
        // Row 1
        listOf(
            KeyModel("q", alternateText = "1", longPressOptions = listOf("1", "¹", "₁", "½")),
            KeyModel("w", alternateText = "2", longPressOptions = listOf("2", "²", "₂", "ŵ")),
            KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é", "è", "ê", "ë", "ē", "ė", "ę", "³", "€")),
            KeyModel("r", alternateText = "4", longPressOptions = listOf("4", "ř", "ŕ", "⁴", "₹")),
            KeyModel("t", alternateText = "5", longPressOptions = listOf("5", "ť", "ţ", "ț", "⁵", "þ")),
            KeyModel("y", alternateText = "6", longPressOptions = listOf("6", "ý", "ÿ", "ŷ", "⁶", "¥")),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú", "ù", "û", "ü", "ū", "ů", "ű", "ǔ", "⁷")),
            KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í", "ì", "î", "ï", "ī", "į", "ı", "⁸", "∞")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó", "ò", "ô", "ö", "õ", "œ", "ø", "ō", "º", "⁹")),
            KeyModel("p", alternateText = "0", longPressOptions = listOf("0", "π", "⁰", "°", "¶"))
        ),
        // Row 2
        listOf(
            KeyModel(type = KeyType.SPACER, weight = 0.5f),
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á", "à", "â", "ä", "æ", "ã", "å", "ā", "ą", "ª")),
            KeyModel("s", alternateText = "#", longPressOptions = listOf("#", "ß", "ś", "š", "ş", "ș", "$")),
            KeyModel("d", alternateText = "$", longPressOptions = listOf("$", "ð", "ď", "đ")),
            KeyModel("f", alternateText = "%", longPressOptions = listOf("%", "ƒ")),
            KeyModel("g", alternateText = "&", longPressOptions = listOf("&", "ğ", "ģ", "ĝ")),
            KeyModel("h", alternateText = "-", longPressOptions = listOf("-", "ħ", "ĥ")),
            KeyModel("j", alternateText = "+", longPressOptions = listOf("+", "ĵ")),
            KeyModel("k", alternateText = "(", longPressOptions = listOf("(", "ķ")),
            KeyModel("l", alternateText = ")", longPressOptions = listOf(")", "ł", "ľ", "ĺ", "ļ")),
            KeyModel(type = KeyType.SPACER, weight = 0.5f)
        ),
        // Row 3 (Shift, z-m, Backspace)
        listOf(
            KeyModel(
                type = KeyType.SHIFT,
                weight = 1.5f,
                iconResId = R.drawable.ic_shift
            ),
            KeyModel("z", alternateText = "*", longPressOptions = listOf("*", "ź", "ž", "ż")),
            KeyModel("x", alternateText = "\"", longPressOptions = listOf("\"", "×")),
            KeyModel("c", alternateText = "'", longPressOptions = listOf("'", "ç", "ć", "č", "¢")),
            KeyModel("v", alternateText = ":", longPressOptions = listOf(":", "√")),
            KeyModel("b", alternateText = ";", longPressOptions = listOf(";", "β")),
            KeyModel("n", alternateText = "!", longPressOptions = listOf("!", "ñ", "ń", "ň", "ņ")),
            KeyModel("m", alternateText = "?", longPressOptions = listOf("?", "µ")),
            KeyModel(
                type = KeyType.BACKSPACE,
                weight = 1.5f,
                iconResId = R.drawable.ic_backspace
            )
        ),
        // Row 4 (?123, Comma, Emoji, Space, Period, Enter)
        listOf(
            KeyModel("?123", type = KeyType.MODE_CHANGE, weight = 1.5f),
            KeyModel(",", alternateText = ";", longPressOptions = listOf(",", ";", ":", "'", "\"", "(", ")", "/")),
            KeyModel(type = KeyType.EMOJI, weight = 1.0f, iconResId = R.drawable.ic_emoji),
            KeyModel(" ", type = KeyType.SPACE, weight = 4.0f),
            KeyModel(".", alternateText = "...", longPressOptions = listOf(".", "...", "!", "?", ",", "-", ":", ";", "@", "/")),
            KeyModel(type = KeyType.ENTER, weight = 1.5f, iconResId = R.drawable.ic_return)
        )
    )

    // Numbers & Basic Symbols Layout (?123)
    val numberSymbolRows: List<List<KeyModel>> = listOf(
        // Row 1: 1 2 3 4 5 6 7 8 9 0
        listOf(
            KeyModel("1", alternateText = "¹", longPressOptions = listOf("1", "¹", "₁", "½", "⅓", "¼", "⅕", "⅙", "⅐", "⅛", "⅑", "⅒")), 
            KeyModel("2", alternateText = "²", longPressOptions = listOf("2", "²", "₂", "⅔", "½")), 
            KeyModel("3", alternateText = "³", longPressOptions = listOf("3", "³", "₃", "¾", "⅜")), 
            KeyModel("4", alternateText = "⁴", longPressOptions = listOf("4", "⁴", "₄", "¢", "€", "£", "¥", "₹")), 
            KeyModel("5", alternateText = "⁵", longPressOptions = listOf("5", "⁵", "₅", "⅝", "‰")),
            KeyModel("6", alternateText = "⁶", longPressOptions = listOf("6", "⁶", "₆")), 
            KeyModel("7", alternateText = "⁷", longPressOptions = listOf("7", "⁷", "₇", "⅞")), 
            KeyModel("8", alternateText = "⁸", longPressOptions = listOf("8", "⁸", "₈", "∞")), 
            KeyModel("9", alternateText = "⁹", longPressOptions = listOf("9", "⁹", "₉")), 
            KeyModel("0", alternateText = "⁰", longPressOptions = listOf("0", "⁰", "₀", "ⁿ", "∅", "°", "•"))
        ),
        // Row 2: @ # $ % & - + ( ) /
        listOf(
            KeyModel("@"), KeyModel("#"),
            KeyModel("$", longPressOptions = listOf("$", "€", "£", "¥", "¢", "₹", "₽", "₩", "₿", "฿", "₫")),
            KeyModel("_", longPressOptions = listOf("_", "-", "–", "—")),
            KeyModel("&", longPressOptions = listOf("&", "§")),
            KeyModel("-", longPressOptions = listOf("-", "–", "—", "_")),
            KeyModel("+", longPressOptions = listOf("+", "±", "×", "÷", "=")),
            KeyModel("(", longPressOptions = listOf("(", "[", "{", "<", "«")),
            KeyModel(")", longPressOptions = listOf(")", "]", "}", ">", "»")),
            KeyModel("/", longPressOptions = listOf("/", "\\", "|"))
        ),
        // Row 3: =< toggle, * " ' : ; ! ? Backspace
        listOf(
            KeyModel("=<", type = KeyType.MODE_CHANGE, weight = 1.5f),
            KeyModel("*", longPressOptions = listOf("*", "★", "☆", "†", "‡")),
            KeyModel("\"", longPressOptions = listOf("\"", "“", "”", "„", "«", "»")),
            KeyModel("'", longPressOptions = listOf("'", "‘", "’", "‚", "`")),
            KeyModel(":"),
            KeyModel(";"),
            KeyModel("!", longPressOptions = listOf("!", "¡", "‽")),
            KeyModel("?", longPressOptions = listOf("?", "¿", "‽")),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        // Row 4: ABC, Emoji, /, Space, ., Enter
        listOf(
            KeyModel("ABC", type = KeyType.MODE_CHANGE, weight = 1.5f),
            KeyModel(type = KeyType.EMOJI, weight = 1.0f, iconResId = R.drawable.ic_emoji),
            KeyModel("/", longPressOptions = listOf("/", "\\")),
            KeyModel(" ", type = KeyType.SPACE, weight = 4.0f),
            KeyModel(".", longPressOptions = listOf(".", "...", ",", "?", "!", "-")),
            KeyModel(type = KeyType.ENTER, weight = 1.5f, iconResId = R.drawable.ic_return)
        )
    )

    // Advanced Symbols Layout (=<)
    val advancedSymbolRows: List<List<KeyModel>> = listOf(
        // Row 1: ~ ` | • √ π ÷ × ¶ ∆
        listOf(
            KeyModel("~", longPressOptions = listOf("~", "≈")),
            KeyModel("`"),
            KeyModel("|", longPressOptions = listOf("|", "¦")),
            KeyModel("•", longPressOptions = listOf("•", "○", "●", "◆", "◇")),
            KeyModel("√"),
            KeyModel("π", longPressOptions = listOf("π", "Π")),
            KeyModel("÷", longPressOptions = listOf("÷", "/")),
            KeyModel("×", longPressOptions = listOf("×", "*")),
            KeyModel("¶", longPressOptions = listOf("¶", "§")),
            KeyModel("∆", longPressOptions = listOf("∆", "∇", "▲", "▼"))
        ),
        // Row 2: € £ ¥ ¢ ^ ° = { } \
        listOf(
            KeyModel("€", longPressOptions = listOf("€", "$", "£", "¥", "¢", "₹", "₿")),
            KeyModel("£"), KeyModel("¥"), KeyModel("¢"),
            KeyModel("^", longPressOptions = listOf("^", "↑", "↓", "←", "→")),
            KeyModel("°", longPressOptions = listOf("°", "′", "″")),
            KeyModel("=", longPressOptions = listOf("=", "≠", "≈", "≡", "≤", "≥", "±")),
            KeyModel("{", longPressOptions = listOf("{", "[", "(")),
            KeyModel("}", longPressOptions = listOf("}", "]", ")")),
            KeyModel("\\", longPressOptions = listOf("\\", "/", "|"))
        ),
        // Row 3: ?123, % < > [ ] _ - Backspace
        listOf(
            KeyModel("?123", type = KeyType.MODE_CHANGE, weight = 1.5f),
            KeyModel("%", longPressOptions = listOf("%", "‰")),
            KeyModel("<", longPressOptions = listOf("<", "≤", "«")),
            KeyModel(">", longPressOptions = listOf(">", "≥", "»")),
            KeyModel("[", longPressOptions = listOf("[", "{", "(")),
            KeyModel("]", longPressOptions = listOf("]", "}", ")")),
            KeyModel("_"), KeyModel("§"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        // Row 4: ABC, Emoji, ¡, Space, ¿, Enter
        listOf(
            KeyModel("ABC", type = KeyType.MODE_CHANGE, weight = 1.5f),
            KeyModel(type = KeyType.EMOJI, weight = 1.0f, iconResId = R.drawable.ic_emoji),
            KeyModel("¡"),
            KeyModel(" ", type = KeyType.SPACE, weight = 4.0f),
            KeyModel("¿"),
            KeyModel(type = KeyType.ENTER, weight = 1.5f, iconResId = R.drawable.ic_return)
        )
    )
}
