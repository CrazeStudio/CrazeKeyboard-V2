package com.example.keyboard

import com.example.R

data class KeyboardLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val layoutType: String
)

object KeyboardLanguages {
    val supportedLanguages = listOf(
        KeyboardLanguage("en", "English (US)", "English (US)", "QWERTY"),
        KeyboardLanguage("en_gb", "English (UK)", "English (UK)", "QWERTY"),
        KeyboardLanguage("es", "Spanish", "Español", "QWERTY"),
        KeyboardLanguage("fr", "French", "Français", "AZERTY"),
        KeyboardLanguage("de", "German", "Deutsch", "QWERTZ"),
        KeyboardLanguage("pt_br", "Portuguese (Brazil)", "Português (Brasil)", "QWERTY"),
        KeyboardLanguage("pt_pt", "Portuguese (Portugal)", "Português (Portugal)", "QWERTY"),
        KeyboardLanguage("it", "Italian", "Italiano", "QWERTY"),
        KeyboardLanguage("ru", "Russian", "Русский", "ЙЦУКЕН"),
        KeyboardLanguage("uk", "Ukrainian", "Українська", "ЙЦУКЕН"),
        KeyboardLanguage("bn", "Bengali", "বাংলা", "বাংলা"),
        KeyboardLanguage("hi", "Hindi", "हिन्दी", "देवनागरी"),
        KeyboardLanguage("ar", "Arabic", "العربية", "عربي"),
        KeyboardLanguage("fa", "Persian", "فارسی", "فارسی"),
        KeyboardLanguage("ur", "Urdu", "اردو", "اردو"),
        KeyboardLanguage("tr", "Turkish", "Türkçe", "Turkish Q"),
        KeyboardLanguage("pl", "Polish", "Polski", "QWERTY"),
        KeyboardLanguage("nl", "Dutch", "Nederlands", "QWERTY"),
        KeyboardLanguage("sv", "Swedish", "Svenska", "QWERTY"),
        KeyboardLanguage("no", "Norwegian", "Norsk", "QWERTY"),
        KeyboardLanguage("da", "Danish", "Dansk", "QWERTY"),
        KeyboardLanguage("fi", "Finnish", "Suomi", "QWERTY"),
        KeyboardLanguage("el", "Greek", "Ελληνικά", "Greek"),
        KeyboardLanguage("he", "Hebrew", "עברית", "Hebrew"),
        KeyboardLanguage("cs", "Czech", "Čeština", "QWERTZ"),
        KeyboardLanguage("ro", "Romanian", "Română", "QWERTY"),
        KeyboardLanguage("hu", "Hungarian", "Magyar", "QWERTZ"),
        KeyboardLanguage("vi", "Vietnamese", "Tiếng Việt", "QWERTY"),
        KeyboardLanguage("id", "Indonesian", "Bahasa Indonesia", "QWERTY"),
        KeyboardLanguage("ms", "Malay", "Bahasa Melayu", "QWERTY"),
        KeyboardLanguage("tl", "Filipino", "Tagalog", "QWERTY"),
        KeyboardLanguage("ja", "Japanese", "日本語 (Romaji)", "QWERTY"),
        KeyboardLanguage("ko", "Korean", "한국어 (Hangul)", "두벌식"),
        KeyboardLanguage("th", "Thai", "ไทย", "Kedmanee"),
        KeyboardLanguage("ta", "Tamil", "தமிழ்", "தமிழ்"),
        KeyboardLanguage("te", "Telugu", "తెలుగు", "తెలుగు"),
        KeyboardLanguage("mr", "Marathi", "मराठी", "देवनागरी"),
        KeyboardLanguage("gu", "Gujarati", "ગુજરાતી", "ગુજરાતી"),
        KeyboardLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ", "ਗੁਰਮੁਖੀ"),
        KeyboardLanguage("sw", "Swahili", "Kiswahili", "QWERTY"),
        KeyboardLanguage("hr", "Croatian", "Hrvatski", "QWERTZ"),
        KeyboardLanguage("sk", "Slovak", "Slovenčina", "QWERTZ"),
        KeyboardLanguage("bg", "Bulgarian", "Български", "БДС"),
        KeyboardLanguage("sr", "Serbian", "Српски", "Ћирилица"),
        KeyboardLanguage("ca", "Catalan", "Català", "QWERTY")
    )

    fun getLanguage(code: String): KeyboardLanguage {
        return supportedLanguages.firstOrNull { it.code == code } ?: supportedLanguages.first()
    }

    fun getAlphaRows(langCode: String): List<List<KeyModel>> {
        return when (langCode) {
            "es" -> spanishRows
            "fr" -> frenchRows
            "de" -> germanRows
            "pt_br", "pt_pt", "pt" -> portugueseRows
            "ru" -> russianRows
            "uk" -> ukrainianRows
            "it" -> italianRows
            "bn" -> bengaliRows
            "hi", "mr" -> hindiRows
            "ar" -> arabicRows
            "fa" -> persianRows
            "ur" -> urduRows
            "tr" -> turkishRows
            "pl" -> polishRows
            "sv", "fi" -> swedishRows
            "da", "no" -> danishRows
            "el" -> greekRows
            "he" -> hebrewRows
            "cs", "sk" -> czechRows
            "hu" -> hungarianRows
            "vi" -> vietnameseRows
            "ko" -> koreanRows
            "th" -> thaiRows
            "bg", "sr" -> bulgarianRows
            else -> KeyboardLayouts.qwertyRows
        }
    }

    private fun buildRow4(spaceLang: String, commaAlt: String = ",", dotAlt: String = "."): List<KeyModel> = listOf(
        KeyModel("?123", type = KeyType.MODE_CHANGE, weight = 1.5f),
        KeyModel(type = KeyType.EMOJI, weight = 1.0f, iconResId = R.drawable.ic_emoji),
        KeyModel(",", alternateText = commaAlt, weight = 1.0f),
        KeyModel(" ", type = KeyType.SPACE, weight = 4.0f),
        KeyModel(".", alternateText = dotAlt, weight = 1.0f),
        KeyModel(type = KeyType.ENTER, weight = 1.5f, iconResId = R.drawable.ic_return)
    )

    private val spanishRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú", "ü")), KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó")), KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á")), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("ñ", alternateText = "~")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "'"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Español", commaAlt = "¿", dotAlt = "¡")
    )

    private val frenchRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("a", alternateText = "1", longPressOptions = listOf("1", "à", "â", "æ")), KeyModel("z", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é", "è", "ê", "ë", "€")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ù", "û", "ü")), KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "î", "ï")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ô", "œ")), KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("q", alternateText = "@"), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("m", alternateText = "'")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("w", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "ç", longPressOptions = listOf("ç")),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Français")
    )

    private val germanRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "€")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("z", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú")), KeyModel("i", alternateText = "8"),
            KeyModel("o", alternateText = "9"), KeyModel("p", alternateText = "0"), KeyModel("ü", alternateText = "^")
        ),
        listOf(
            KeyModel("a", alternateText = "@"), KeyModel("s", alternateText = "ß", longPressOptions = listOf("ß", "$")), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("ö", alternateText = "="), KeyModel("ä", alternateText = "*")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("y", alternateText = "\""), KeyModel("x", alternateText = "'"), KeyModel("c", alternateText = "/"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Deutsch")
    )

    private val bengaliRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ক", alternateText = "১"), KeyModel("খ", alternateText = "২"), KeyModel("গ", alternateText = "৩"),
            KeyModel("ঘ", alternateText = "৪"), KeyModel("ঙ", alternateText = "৫"), KeyModel("চ", alternateText = "৬"),
            KeyModel("ছ", alternateText = "৭"), KeyModel("জ", alternateText = "৮"), KeyModel("ঝ", alternateText = "৯"),
            KeyModel("ঞ", alternateText = "০")
        ),
        listOf(
            KeyModel("ট", alternateText = "া"), KeyModel("ঠ", alternateText = "ি"), KeyModel("ড", alternateText = "ী"),
            KeyModel("ঢ", alternateText = "ু"), KeyModel("ণ", alternateText = "ূ"), KeyModel("ত", alternateText = "ৃ"),
            KeyModel("থ", alternateText = "ে"), KeyModel("দ", alternateText = "ৈ"), KeyModel("ধ", alternateText = "ো"),
            KeyModel("ন", alternateText = "ৌ")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("প", alternateText = "্"), KeyModel("ফ", alternateText = "ং"), KeyModel("ব", alternateText = "ঃ"),
            KeyModel("ভ", alternateText = "ঁ"), KeyModel("ম", alternateText = "ৎ"), KeyModel("য", alternateText = "র"),
            KeyModel("ল", alternateText = "শ"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("বাংলা", dotAlt = "।")
    )

    private val hindiRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("क", alternateText = "१"), KeyModel("ख", alternateText = "२"), KeyModel("ग", alternateText = "३"),
            KeyModel("घ", alternateText = "४"), KeyModel("ङ", alternateText = "५"), KeyModel("च", alternateText = "६"),
            KeyModel("छ", alternateText = "७"), KeyModel("ज", alternateText = "८"), KeyModel("झ", alternateText = "९"),
            KeyModel("ञ", alternateText = "०")
        ),
        listOf(
            KeyModel("ट", alternateText = "ा"), KeyModel("ठ", alternateText = "ि"), KeyModel("ड", alternateText = "ी"),
            KeyModel("ढ", alternateText = "ु"), KeyModel("ण", alternateText = "ू"), KeyModel("त", alternateText = "ृ"),
            KeyModel("थ", alternateText = "े"), KeyModel("द", alternateText = "ै"), KeyModel("ध", alternateText = "ो"),
            KeyModel("न", alternateText = "ौ")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("प", alternateText = "्"), KeyModel("फ", alternateText = "ं"), KeyModel("ब", alternateText = "ः"),
            KeyModel("भ", alternateText = "ँ"), KeyModel("म", alternateText = "़"), KeyModel("य", alternateText = "र"),
            KeyModel("ल", alternateText = "व"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("हिन्दी", dotAlt = "।")
    )

    private val arabicRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ض", alternateText = "١"), KeyModel("ص", alternateText = "٢"), KeyModel("ث", alternateText = "٣"),
            KeyModel("ق", alternateText = "٤"), KeyModel("ف", alternateText = "٥"), KeyModel("غ", alternateText = "٦"),
            KeyModel("ع", alternateText = "٧"), KeyModel("ه", alternateText = "٨"), KeyModel("خ", alternateText = "٩"),
            KeyModel("ح", alternateText = "٠"), KeyModel("ج", alternateText = "-"), KeyModel("د", alternateText = "=")
        ),
        listOf(
            KeyModel("ش", alternateText = "@"), KeyModel("س", alternateText = "#"), KeyModel("ي", alternateText = "$"),
            KeyModel("ب", alternateText = "%"), KeyModel("ل", alternateText = "&"), KeyModel("ا", alternateText = "*"),
            KeyModel("ت", alternateText = "("), KeyModel("ن", alternateText = ")"), KeyModel("م", alternateText = "؟"),
            KeyModel("ك", alternateText = "؛"), KeyModel("ط", alternateText = "!")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ئ", alternateText = "\""), KeyModel("ء", alternateText = "'"), KeyModel("ؤ", alternateText = "/"),
            KeyModel("ر", alternateText = ":"), KeyModel("لا", alternateText = "+"), KeyModel("ى", alternateText = "-"),
            KeyModel("ة", alternateText = "،"), KeyModel("و", alternateText = "."), KeyModel("ز", alternateText = "؟"),
            KeyModel("ظ", alternateText = "~"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("العربية", commaAlt = "،", dotAlt = "؟")
    )

    private val persianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ض", alternateText = "۱"), KeyModel("ص", alternateText = "۲"), KeyModel("ث", alternateText = "۳"),
            KeyModel("ق", alternateText = "۴"), KeyModel("ف", alternateText = "۵"), KeyModel("غ", alternateText = "۶"),
            KeyModel("ع", alternateText = "۷"), KeyModel("ه", alternateText = "۸"), KeyModel("خ", alternateText = "۹"),
            KeyModel("ح", alternateText = "۰"), KeyModel("ج", alternateText = "چ"), KeyModel("چ", alternateText = "پ")
        ),
        listOf(
            KeyModel("ش", alternateText = "@"), KeyModel("س", alternateText = "#"), KeyModel("ی", alternateText = "$"),
            KeyModel("ب", alternateText = "%"), KeyModel("ل", alternateText = "&"), KeyModel("ا", alternateText = "*"),
            KeyModel("ت", alternateText = "("), KeyModel("ن", alternateText = ")"), KeyModel("م", alternateText = "؟"),
            KeyModel("ک", alternateText = "گ"), KeyModel("گ", alternateText = "!")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ظ", alternateText = "ط"), KeyModel("ط", alternateText = "ژ"), KeyModel("ز", alternateText = "ر"),
            KeyModel("ر", alternateText = "ذ"), KeyModel("ذ", alternateText = "د"), KeyModel("د", alternateText = "پ"),
            KeyModel("پ", alternateText = "و"), KeyModel("و", alternateText = "."), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("فارسی", commaAlt = "،", dotAlt = "؟")
    )

    private val urduRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ٹ", alternateText = "۱"), KeyModel("پ", alternateText = "۲"), KeyModel("ت", alternateText = "۳"),
            KeyModel("ب", alternateText = "۴"), KeyModel("ا", alternateText = "۵"), KeyModel("د", alternateText = "۶"),
            KeyModel("ڈ", alternateText = "۷"), KeyModel("ر", alternateText = "۸"), KeyModel("ڑ", alternateText = "۹"),
            KeyModel("ز", alternateText = "۰")
        ),
        listOf(
            KeyModel("س", alternateText = "ش"), KeyModel("ص", alternateText = "ض"), KeyModel("ط", alternateText = "ظ"),
            KeyModel("ع", alternateText = "غ"), KeyModel("ف", alternateText = "ق"), KeyModel("ک", alternateText = "گ"),
            KeyModel("ل", alternateText = "م"), KeyModel("ن", alternateText = "ں"), KeyModel("و", alternateText = "ہ"),
            KeyModel("ھ", alternateText = "ء")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ی", alternateText = "ے"), KeyModel("ح", alternateText = "خ"), KeyModel("ج", alternateText = "چ"),
            KeyModel("ژ", alternateText = "خ"), KeyModel("ذ", alternateText = "ث"), KeyModel("ں", alternateText = "ء"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("اردو", commaAlt = "،", dotAlt = "۔")
    )

    private val turkishRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3"),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7"), KeyModel("ı", alternateText = "8"), KeyModel("o", alternateText = "9"),
            KeyModel("p", alternateText = "0"), KeyModel("ğ", alternateText = "-"), KeyModel("ü", alternateText = "=")
        ),
        listOf(
            KeyModel("a", alternateText = "@"), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "*"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("ş", alternateText = "i"), KeyModel("i", alternateText = "'")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "/"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel("ö", alternateText = "<"), KeyModel("ç", alternateText = ">"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Türkçe")
    )

    private val polishRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "ę")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7"), KeyModel("i", alternateText = "8"), KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó")),
            KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "ą")), KeyModel("s", alternateText = "#", longPressOptions = listOf("#", "ś")),
            KeyModel("d", alternateText = "$"), KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"),
            KeyModel("h", alternateText = "-"), KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("),
            KeyModel("l", alternateText = ")", longPressOptions = listOf(")", "ł"))
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*", longPressOptions = listOf("*", "ż", "ź")), KeyModel("x", alternateText = "\""),
            KeyModel("c", alternateText = "'", longPressOptions = listOf("'", "ć")), KeyModel("v", alternateText = ":"),
            KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!", longPressOptions = listOf("!", "ń")),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Polski")
    )

    private val swedishRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7"), KeyModel("i", alternateText = "8"), KeyModel("o", alternateText = "9"),
            KeyModel("p", alternateText = "0"), KeyModel("å", alternateText = "-")
        ),
        listOf(
            KeyModel("a", alternateText = "@"), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("ö", alternateText = "="), KeyModel("ä", alternateText = "*")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "'"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Svenska")
    )

    private val danishRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3"),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7"), KeyModel("i", alternateText = "8"), KeyModel("o", alternateText = "9"),
            KeyModel("p", alternateText = "0"), KeyModel("å", alternateText = "-")
        ),
        listOf(
            KeyModel("a", alternateText = "@"), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("æ", alternateText = "="), KeyModel("ø", alternateText = "*")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "'"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Dansk")
    )

    private val greekRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel(";", alternateText = "1"), KeyModel("ς", alternateText = "2"), KeyModel("ε", alternateText = "3", longPressOptions = listOf("3", "έ")),
            KeyModel("ρ", alternateText = "4"), KeyModel("τ", alternateText = "5"), KeyModel("υ", alternateText = "6", longPressOptions = listOf("6", "ύ", "ϋ", "ΰ")),
            KeyModel("θ", alternateText = "7"), KeyModel("ι", alternateText = "8", longPressOptions = listOf("8", "ί", "ϊ", "ΐ")),
            KeyModel("ο", alternateText = "9", longPressOptions = listOf("9", "ό")), KeyModel("π", alternateText = "0")
        ),
        listOf(
            KeyModel("α", alternateText = "@", longPressOptions = listOf("@", "ά")), KeyModel("σ", alternateText = "#"),
            KeyModel("δ", alternateText = "$"), KeyModel("φ", alternateText = "%"), KeyModel("γ", alternateText = "&"),
            KeyModel("η", alternateText = "-", longPressOptions = listOf("-", "ή")), KeyModel("ξ", alternateText = "+"),
            KeyModel("κ", alternateText = "("), KeyModel("λ", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ζ", alternateText = "*"), KeyModel("χ", alternateText = "\""), KeyModel("ψ", alternateText = "'"),
            KeyModel("ω", alternateText = ":", longPressOptions = listOf(":", "ώ")), KeyModel("β", alternateText = ";"),
            KeyModel("ν", alternateText = "!"), KeyModel("μ", alternateText = "?"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Ελληνικά")
    )

    private val hebrewRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ק", alternateText = "1"), KeyModel("ר", alternateText = "2"), KeyModel("א", alternateText = "3"),
            KeyModel("ט", alternateText = "4"), KeyModel("ו", alternateText = "5"), KeyModel("ן", alternateText = "6"),
            KeyModel("ם", alternateText = "7"), KeyModel("פ", alternateText = "8"), KeyModel("ש", alternateText = "9"),
            KeyModel("ד", alternateText = "0")
        ),
        listOf(
            KeyModel("ג", alternateText = "@"), KeyModel("כ", alternateText = "#"), KeyModel("ע", alternateText = "$"),
            KeyModel("י", alternateText = "%"), KeyModel("ח", alternateText = "&"), KeyModel("ל", alternateText = "-"),
            KeyModel("ך", alternateText = "+"), KeyModel("ף", alternateText = "("), KeyModel("ז", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ס", alternateText = "*"), KeyModel("ב", alternateText = "\""), KeyModel("ה", alternateText = "'"),
            KeyModel("נ", alternateText = ":"), KeyModel("מ", alternateText = ";"), KeyModel("צ", alternateText = "!"),
            KeyModel("ת", alternateText = "?"), KeyModel("ץ", alternateText = "~"),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("עברית")
    )

    private val czechRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é", "ě")),
            KeyModel("r", alternateText = "4", longPressOptions = listOf("4", "ř")), KeyModel("t", alternateText = "5", longPressOptions = listOf("5", "ť")),
            KeyModel("z", alternateText = "6", longPressOptions = listOf("6", "ž")), KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú", "ů")),
            KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í")), KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó")),
            KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á")), KeyModel("s", alternateText = "#", longPressOptions = listOf("#", "š")),
            KeyModel("d", alternateText = "$", longPressOptions = listOf("$", "ď")), KeyModel("f", alternateText = "%"),
            KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"), KeyModel("j", alternateText = "+"),
            KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")", longPressOptions = listOf(")", "ĺ", "ľ"))
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("y", alternateText = "*", longPressOptions = listOf("*", "ý")), KeyModel("x", alternateText = "\""),
            KeyModel("c", alternateText = "'", longPressOptions = listOf("'", "č")), KeyModel("v", alternateText = ":"),
            KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!", longPressOptions = listOf("!", "ň")),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Čeština")
    )

    private val hungarianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("z", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú", "ü", "ű")), KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó", "ö", "ő")), KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á")), KeyModel("s", alternateText = "#"),
            KeyModel("d", alternateText = "$"), KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"),
            KeyModel("h", alternateText = "-"), KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("),
            KeyModel("l", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("y", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "'"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Magyar")
    )

    private val vietnameseRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2", longPressOptions = listOf("2", "ư")),
            KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "ê", "é", "è", "ẻ", "ẽ", "ẹ")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6", longPressOptions = listOf("6", "ý", "ỳ", "ỷ", "ỹ", "ỵ")),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú", "ù", "ủ", "ũ", "ụ", "ư", "ứ", "ừ", "ử", "ữ", "ự")),
            KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í", "ì", "ỉ", "ĩ", "ị")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó", "ò", "ỏ", "õ", "ọ", "ô", "ố", "ồ", "ổ", "ỗ", "ộ", "ơ", "ớ", "ờ", "ở", "ỡ", "ợ")),
            KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á", "à", "ả", "ã", "ạ", "ă", "ắ", "ằ", "ẳ", "ẵ", "ặ", "â", "ấ", "ầ", "ẩ", "ẫ", "ậ")),
            KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$", longPressOptions = listOf("$", "đ")),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = "'"),
            KeyModel("v", alternateText = ":"), KeyModel("b", alternateText = ";"), KeyModel("n", alternateText = "!"),
            KeyModel("m", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Tiếng Việt")
    )

    private val koreanRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ㅂ", alternateText = "ㅃ"), KeyModel("ㅈ", alternateText = "ㅉ"), KeyModel("ㄷ", alternateText = "ㄸ"),
            KeyModel("ㄱ", alternateText = "ㄲ"), KeyModel("ㅅ", alternateText = "ㅆ"), KeyModel("ㅛ", alternateText = "6"),
            KeyModel("ㅕ", alternateText = "7"), KeyModel("ㅑ", alternateText = "8"), KeyModel("ㅐ", alternateText = "ㅒ"),
            KeyModel("ㅔ", alternateText = "ㅖ")
        ),
        listOf(
            KeyModel("ㅁ", alternateText = "@"), KeyModel("ㄴ", alternateText = "#"), KeyModel("ㅇ", alternateText = "$"),
            KeyModel("ㄹ", alternateText = "%"), KeyModel("ㅎ", alternateText = "&"), KeyModel("ㅗ", alternateText = "-"),
            KeyModel("ㅓ", alternateText = "+"), KeyModel("ㅏ", alternateText = "("), KeyModel("ㅣ", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ㅋ", alternateText = "*"), KeyModel("ㅌ", alternateText = "\""), KeyModel("ㅊ", alternateText = "'"),
            KeyModel("ㅍ", alternateText = ":"), KeyModel("ㅠ", alternateText = ";"), KeyModel("ㅜ", alternateText = "!"),
            KeyModel("ㅡ", alternateText = "?"), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("한국어")
    )

    private val thaiRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("ภ", alternateText = "๑"), KeyModel("ถ", alternateText = "๒"), KeyModel("ุ", alternateText = "๓"),
            KeyModel("ึ", alternateText = "๔"), KeyModel("ค", alternateText = "๕"), KeyModel("ต", alternateText = "๖"),
            KeyModel("จ", alternateText = "๗"), KeyModel("ข", alternateText = "๘"), KeyModel("ช", alternateText = "๙"),
            KeyModel("ๆ", alternateText = "๐")
        ),
        listOf(
            KeyModel("ง", alternateText = "@"), KeyModel("เ", alternateText = "#"), KeyModel("แ", alternateText = "$"),
            KeyModel("ั", alternateText = "%"), KeyModel("ี", alternateText = "&"), KeyModel("ด", alternateText = "-"),
            KeyModel("ะ", alternateText = "+"), KeyModel("า", alternateText = "("), KeyModel("น", alternateText = ")"),
            KeyModel("ย", alternateText = "ฯ")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ผ", alternateText = "*"), KeyModel("ป", alternateText = "\""), KeyModel("อ", alternateText = "'"),
            KeyModel("ิ", alternateText = ":"), KeyModel("บ", alternateText = ";"), KeyModel("ท", alternateText = "!"),
            KeyModel("ม", alternateText = "?"), KeyModel("ใ", alternateText = "."), KeyModel("ฝ", alternateText = ","),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("ไทย")
    )

    private val portugueseRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "é", "ê", "è")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ú")), KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "í")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ó", "ô", "õ")), KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "á", "â", "ã", "à")), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")"),
            KeyModel("ç", alternateText = "'")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = ":"),
            KeyModel("v", alternateText = ";"), KeyModel("b", alternateText = "!"), KeyModel("n", alternateText = "?"),
            KeyModel("m", alternateText = ","), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Português")
    )

    private val russianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("й", alternateText = "1"), KeyModel("ц", alternateText = "2"), KeyModel("у", alternateText = "3"),
            KeyModel("к", alternateText = "4"), KeyModel("е", alternateText = "5", longPressOptions = listOf("5", "ё")),
            KeyModel("н", alternateText = "6"), KeyModel("г", alternateText = "7"), KeyModel("ш", alternateText = "8"),
            KeyModel("щ", alternateText = "9"), KeyModel("з", alternateText = "0"), KeyModel("х", alternateText = "-"),
            KeyModel("ъ", alternateText = "=")
        ),
        listOf(
            KeyModel("ф", alternateText = "@"), KeyModel("ы", alternateText = "#"), KeyModel("в", alternateText = "$"),
            KeyModel("а", alternateText = "%"), KeyModel("п", alternateText = "&"), KeyModel("р", alternateText = "*"),
            KeyModel("о", alternateText = "("), KeyModel("л", alternateText = ")"), KeyModel("д", alternateText = "'"),
            KeyModel("ж", alternateText = "\""), KeyModel("э", alternateText = ";")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("я", alternateText = "?"), KeyModel("ч", alternateText = "!"), KeyModel("с", alternateText = ":"),
            KeyModel("м", alternateText = "/"), KeyModel("и", alternateText = "\\"), KeyModel("т", alternateText = "_"),
            KeyModel("ь", alternateText = "~", longPressOptions = listOf("~", "ъ")), KeyModel("б", alternateText = ","),
            KeyModel("ю", alternateText = "."), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Русский")
    )

    private val ukrainianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("й", alternateText = "1"), KeyModel("ц", alternateText = "2"), KeyModel("у", alternateText = "3"),
            KeyModel("к", alternateText = "4"), KeyModel("е", alternateText = "5"), KeyModel("н", alternateText = "6"),
            KeyModel("г", alternateText = "7", longPressOptions = listOf("7", "ґ")), KeyModel("ш", alternateText = "8"),
            KeyModel("щ", alternateText = "9"), KeyModel("з", alternateText = "0"), KeyModel("х", alternateText = "-"),
            KeyModel("ї", alternateText = "=")
        ),
        listOf(
            KeyModel("ф", alternateText = "@"), KeyModel("і", alternateText = "#"), KeyModel("в", alternateText = "$"),
            KeyModel("а", alternateText = "%"), KeyModel("п", alternateText = "&"), KeyModel("р", alternateText = "*"),
            KeyModel("о", alternateText = "("), KeyModel("л", alternateText = ")"), KeyModel("д", alternateText = "'"),
            KeyModel("ж", alternateText = "\""), KeyModel("є", alternateText = ";")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("я", alternateText = "?"), KeyModel("ч", alternateText = "!"), KeyModel("с", alternateText = ":"),
            KeyModel("м", alternateText = "/"), KeyModel("и", alternateText = "\\"), KeyModel("т", alternateText = "_"),
            KeyModel("ь", alternateText = "~"), KeyModel("б", alternateText = ","), KeyModel("ю", alternateText = "."),
            KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Українська")
    )

    private val bulgarianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("я", alternateText = "1"), KeyModel("в", alternateText = "2"), KeyModel("е", alternateText = "3"),
            KeyModel("р", alternateText = "4"), KeyModel("т", alternateText = "5"), KeyModel("ъ", alternateText = "6"),
            KeyModel("у", alternateText = "7"), KeyModel("и", alternateText = "8"), KeyModel("о", alternateText = "9"),
            KeyModel("п", alternateText = "0"), KeyModel("ш", alternateText = "-"), KeyModel("щ", alternateText = "=")
        ),
        listOf(
            KeyModel("а", alternateText = "@"), KeyModel("с", alternateText = "#"), KeyModel("д", alternateText = "$"),
            KeyModel("ф", alternateText = "%"), KeyModel("г", alternateText = "&"), KeyModel("х", alternateText = "*"),
            KeyModel("й", alternateText = "("), KeyModel("к", alternateText = ")"), KeyModel("л", alternateText = "'"),
            KeyModel("з", alternateText = "\""), KeyModel("ц", alternateText = ";")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("ж", alternateText = "?"), KeyModel("б", alternateText = "!"), KeyModel("н", alternateText = ":"),
            KeyModel("м", alternateText = "/"), KeyModel("ч", alternateText = "\\"), KeyModel("ь", alternateText = "_"),
            KeyModel("ю", alternateText = "."), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Български")
    )

    private val italianRows: List<List<KeyModel>> = listOf(
        listOf(
            KeyModel("q", alternateText = "1"), KeyModel("w", alternateText = "2"), KeyModel("e", alternateText = "3", longPressOptions = listOf("3", "è", "é")),
            KeyModel("r", alternateText = "4"), KeyModel("t", alternateText = "5"), KeyModel("y", alternateText = "6"),
            KeyModel("u", alternateText = "7", longPressOptions = listOf("7", "ù", "ú")), KeyModel("i", alternateText = "8", longPressOptions = listOf("8", "ì", "í")),
            KeyModel("o", alternateText = "9", longPressOptions = listOf("9", "ò", "ó")), KeyModel("p", alternateText = "0")
        ),
        listOf(
            KeyModel("a", alternateText = "@", longPressOptions = listOf("@", "à", "á")), KeyModel("s", alternateText = "#"), KeyModel("d", alternateText = "$"),
            KeyModel("f", alternateText = "%"), KeyModel("g", alternateText = "&"), KeyModel("h", alternateText = "-"),
            KeyModel("j", alternateText = "+"), KeyModel("k", alternateText = "("), KeyModel("l", alternateText = ")")
        ),
        listOf(
            KeyModel(type = KeyType.SHIFT, weight = 1.5f, iconResId = R.drawable.ic_shift),
            KeyModel("z", alternateText = "*"), KeyModel("x", alternateText = "\""), KeyModel("c", alternateText = ":"),
            KeyModel("v", alternateText = ";"), KeyModel("b", alternateText = "!"), KeyModel("n", alternateText = "?"),
            KeyModel("m", alternateText = ","), KeyModel(type = KeyType.BACKSPACE, weight = 1.5f, iconResId = R.drawable.ic_backspace)
        ),
        buildRow4("Italiano")
    )
}

