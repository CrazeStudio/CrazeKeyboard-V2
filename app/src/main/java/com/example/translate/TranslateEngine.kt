package com.example.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class TranslateLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val symbol: String
)

object TranslateEngine {

    val supportedLanguages = listOf(
        TranslateLanguage("en", "English", "English", "EN"),
        TranslateLanguage("es", "Spanish", "Español", "ES"),
        TranslateLanguage("fr", "French", "Français", "FR"),
        TranslateLanguage("de", "German", "Deutsch", "DE"),
        TranslateLanguage("it", "Italian", "Italiano", "IT"),
        TranslateLanguage("pt", "Portuguese", "Português", "PT"),
        TranslateLanguage("ru", "Russian", "Русский", "RU"),
        TranslateLanguage("zh", "Chinese", "中文", "ZH"),
        TranslateLanguage("ja", "Japanese", "日本語", "JA"),
        TranslateLanguage("ko", "Korean", "한국어", "KO"),
        TranslateLanguage("ar", "Arabic", "العربية", "AR"),
        TranslateLanguage("hi", "Hindi", "हिन्दी", "HI"),
        TranslateLanguage("bn", "Bengali", "বাংলা", "BN"),
        TranslateLanguage("tr", "Turkish", "Türkçe", "TR"),
        TranslateLanguage("id", "Indonesian", "Bahasa Indonesia", "ID"),
        TranslateLanguage("vi", "Vietnamese", "Tiếng Việt", "VI"),
        TranslateLanguage("nl", "Dutch", "Nederlands", "NL"),
        TranslateLanguage("pl", "Polish", "Polski", "PL"),
        TranslateLanguage("sv", "Swedish", "Svenska", "SV"),
        TranslateLanguage("th", "Thai", "ไทย", "TH"),
        TranslateLanguage("el", "Greek", "Ελληνικά", "EL"),
        TranslateLanguage("he", "Hebrew", "עברית", "HE"),
        TranslateLanguage("uk", "Ukrainian", "Українська", "UK"),
        TranslateLanguage("ro", "Romanian", "Română", "RO"),
        TranslateLanguage("cs", "Czech", "Čeština", "CS"),
        TranslateLanguage("hu", "Hungarian", "Magyar", "HU"),
        TranslateLanguage("da", "Danish", "Dansk", "DA"),
        TranslateLanguage("fi", "Finnish", "Suomi", "FI"),
        TranslateLanguage("no", "Norwegian", "Norsk", "NO"),
        TranslateLanguage("fa", "Persian", "فارسی", "FA"),
        TranslateLanguage("ur", "Urdu", "اردو", "UR"),
        TranslateLanguage("ms", "Malay", "Bahasa Melayu", "MS"),
        TranslateLanguage("ta", "Tamil", "தமிழ்", "TA"),
        TranslateLanguage("te", "Telugu", "తెలుగు", "TE"),
        TranslateLanguage("fil", "Filipino", "Tagalog", "FIL")
    )

    fun getLanguage(code: String): TranslateLanguage {
        return supportedLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: supportedLanguages.first()
    }

    // High frequency common phrase dictionary (Key: English lowercase)
    private val phraseMap = mapOf(
        "hello" to mapOf(
            "es" to "Hola", "fr" to "Bonjour", "de" to "Hallo", "it" to "Ciao",
            "pt" to "Olá", "ru" to "Привет", "zh" to "你好", "ja" to "こんにちは",
            "ko" to "안녕하세요", "ar" to "مرحبا", "hi" to "नमस्ते", "bn" to "হ্যালো",
            "tr" to "Merhaba", "id" to "Halo", "vi" to "Xin chào", "nl" to "Hallo"
        ),
        "how are you" to mapOf(
            "es" to "¿Cómo estás?", "fr" to "Comment allez-vous?", "de" to "Wie geht es dir?",
            "it" to "Come stai?", "pt" to "Como você está?", "ru" to "Как дела?",
            "zh" to "你好吗？", "ja" to "お元気ですか？", "ko" to "어떻게 지내세요?",
            "ar" to "كيف حالك؟", "hi" to "आप कैसे हैं?", "bn" to "আপনি কেমন আছেন?",
            "tr" to "Nasılsın?", "id" to "Apa kabar?", "vi" to "Bạn khỏe không?"
        ),
        "good morning" to mapOf(
            "es" to "Buenos días", "fr" to "Bonjour", "de" to "Guten Morgen",
            "it" to "Buongiorno", "pt" to "Bom dia", "ru" to "Доброе утро",
            "zh" to "早上好", "ja" to "おはようございます", "ko" to "좋은 아침입니다",
            "ar" to "صباح الخير", "hi" to "सुप्रभात", "bn" to "শুভ সকাল",
            "tr" to "Günaydın", "id" to "Selamat pagi", "vi" to "Chào buổi sáng"
        ),
        "good night" to mapOf(
            "es" to "Buenas noches", "fr" to "Bonne nuit", "de" to "Gute Nacht",
            "it" to "Buonanotte", "pt" to "Boa noite", "ru" to "Спокойной ночи",
            "zh" to "晚安", "ja" to "おやすみなさい", "ko" to "안녕히 주무세요",
            "ar" to "تصبح على خير", "hi" to "शुभ रात्रि", "bn" to "শুভ রাত্রি",
            "tr" to "İyi geceler", "id" to "Selamat malam", "vi" to "Chúc ngủ ngon"
        ),
        "thank you" to mapOf(
            "es" to "Gracias", "fr" to "Merci", "de" to "Danke", "it" to "Grazie",
            "pt" to "Obrigado", "ru" to "Спасибо", "zh" to "谢谢", "ja" to "ありがとうございます",
            "ko" to "감사합니다", "ar" to "شكرا لك", "hi" to "धन्यवाद", "bn" to "ধন্যবাদ",
            "tr" to "Teşekkür ederim", "id" to "Terima kasih", "vi" to "Cảm ơn bạn"
        ),
        "thank you very much" to mapOf(
            "es" to "Muchas gracias", "fr" to "Merci beaucoup", "de" to "Vielen Dank",
            "it" to "Molte grazie", "pt" to "Muito obrigado", "ru" to "Большое спасибо",
            "zh" to "非常感谢", "ja" to "どうもありがとうございます", "ko" to "정말 감사합니다",
            "ar" to "شكرا جزيلا", "hi" to "बहुत बहुत धन्यवाद", "bn" to "অনেক ধন্যবাদ"
        ),
        "please" to mapOf(
            "es" to "Por favor", "fr" to "S'il vous plaît", "de" to "Bitte",
            "it" to "Per favore", "pt" to "Por favor", "ru" to "Пожалуйста",
            "zh" to "请", "ja" to "お願いします", "ko" to "부탁합니다",
            "ar" to "من فضلك", "hi" to "कृपया", "bn" to "দয়া করে",
            "tr" to "Lütfen", "id" to "Tolong / Silakan", "vi" to "Làm ơn"
        ),
        "you are welcome" to mapOf(
            "es" to "De nada", "fr" to "De rien", "de" to "Gern geschehen",
            "it" to "Prego", "pt" to "De nada", "ru" to "Не за что",
            "zh" to "不客气", "ja" to "どういたしまして", "ko" to "천만에요",
            "ar" to "عفوا", "hi" to "आपका स्वागत है", "bn" to "স্বাগতম"
        ),
        "yes" to mapOf(
            "es" to "Sí", "fr" to "Oui", "de" to "Ja", "it" to "Sì",
            "pt" to "Sim", "ru" to "Да", "zh" to "是的", "ja" to "はい",
            "ko" to "네", "ar" to "نعم", "hi" to "हाँ", "bn" to "হ্যাঁ",
            "tr" to "Evet", "id" to "Ya", "vi" to "Vâng"
        ),
        "no" to mapOf(
            "es" to "No", "fr" to "Non", "de" to "Nein", "it" to "No",
            "pt" to "Não", "ru" to "Нет", "zh" to "不", "ja" to "いいえ",
            "ko" to "아니요", "ar" to "لا", "hi" to "नहीं", "bn" to "না",
            "tr" to "Hayır", "id" to "Tidak", "vi" to "Không"
        ),
        "sorry" to mapOf(
            "es" to "Lo siento / Perdón", "fr" to "Désolé / Pardon", "de" to "Entschuldigung",
            "it" to "Scusa / Mi dispiace", "pt" to "Desculpe", "ru" to "Извините",
            "zh" to "对不起", "ja" to "ごめんなさい", "ko" to "죄송합니다",
            "ar" to "آسف", "hi" to "माफ़ कीजिए", "bn" to "দুঃখিত",
            "tr" to "Özür dilerim", "id" to "Maaf", "vi" to "Xin lỗi"
        ),
        "excuse me" to mapOf(
            "es" to "Disculpe", "fr" to "Excusez-moi", "de" to "Entschuldigen Sie",
            "it" to "Mi scusi", "pt" to "Com licença", "ru" to "Прошу прощения",
            "zh" to "打扰一下", "ja" to "すみません", "ko" to "실례합니다",
            "ar" to "معذرة", "hi" to "क्षमा करें", "bn" to "মাফ করবেন"
        ),
        "see you soon" to mapOf(
            "es" to "Hasta pronto", "fr" to "À bientôt", "de" to "Bis bald",
            "it" to "A presto", "pt" to "Até logo", "ru" to "До скорого",
            "zh" to "一会儿见", "ja" to "またね", "ko" to "곧 봐요",
            "ar" to "أراك قريبا", "hi" to "जल्द ही मिलेंगे", "bn" to "শীঘ্রই দেখা হবে"
        ),
        "see you tomorrow" to mapOf(
            "es" to "Hasta mañana", "fr" to "À demain", "de" to "Bis morgen",
            "it" to "A domani", "pt" to "Até amanhã", "ru" to "До завтра",
            "zh" to "明天见", "ja" to "また明日", "ko" to "내일 봐요",
            "ar" to "أراك غدا", "hi" to "कल मिलते हैं", "bn" to "আগামীকাল দেখা হবে"
        ),
        "goodbye" to mapOf(
            "es" to "Adiós", "fr" to "Au revoir", "de" to "Auf Wiedersehen",
            "it" to "Arrivederci", "pt" to "Adeus / Tchau", "ru" to "До свидания",
            "zh" to "再见", "ja" to "さようなら", "ko" to "안녕히 가세요",
            "ar" to "مع السلامة", "hi" to "अलविदा", "bn" to "বিদায়",
            "tr" to "Hoşça kal", "id" to "Selamat tinggal", "vi" to "Tạm biệt"
        ),
        "where is the bathroom" to mapOf(
            "es" to "¿Dónde está el baño?", "fr" to "Où sont les toilettes?",
            "de" to "Wo ist die Toilette?", "it" to "Dov'è il bagno?",
            "pt" to "Onde fica o banheiro?", "ru" to "Где находится туалет?",
            "zh" to "洗手间在哪里？", "ja" to "トイレはどこですか？",
            "ko" to "화장실이 어디에 있나요?", "ar" to "أين الحمام؟",
            "hi" to "शौचालय कहाँ है?", "bn" to "বাথরুম কোথায়?"
        ),
        "how much is this" to mapOf(
            "es" to "¿Cuánto cuesta esto?", "fr" to "Combien ça coûte?",
            "de" to "Wie viel kostet das?", "it" to "Quanto costa questo?",
            "pt" to "Quanto custa isto?", "ru" to "Сколько это стоит?",
            "zh" to "这个多少钱？", "ja" to "これはいくらですか？",
            "ko" to "이것은 얼마인가요?", "ar" to "بكم هذا؟",
            "hi" to "यह कितने का है?", "bn" to "এটার দাম কত?"
        ),
        "i love you" to mapOf(
            "es" to "Te quiero / Te amo", "fr" to "Je t'aime", "de" to "Ich liebe dich",
            "it" to "Ti amo", "pt" to "Eu te amo", "ru" to "Я тебя люблю",
            "zh" to "我爱你", "ja" to "愛してる", "ko" to "사랑해요",
            "ar" to "أحبك", "hi" to "मैं तुमसे प्यार करता हूँ", "bn" to "আমি তোমাকে ভালবাসি"
        ),
        "what is your name" to mapOf(
            "es" to "¿Cómo te llamas?", "fr" to "Comment vous appelez-vous?",
            "de" to "Wie heißt du?", "it" to "Come ti chiami?",
            "pt" to "Qual é o seu nome?", "ru" to "Как тебя зовут?",
            "zh" to "你叫什么名字？", "ja" to "お名前は何ですか？",
            "ko" to "이름이 무엇인가요?", "ar" to "ما اسمك؟",
            "hi" to "आपका नाम क्या है?", "bn" to "আপনার নাম কি?"
        ),
        "my name is" to mapOf(
            "es" to "Mi nombre es", "fr" to "Je m'appelle", "de" to "Mein Name ist",
            "it" to "Mi chiamo", "pt" to "Meu nome é", "ru" to "Меня зовут",
            "zh" to "我的名字是", "ja" to "私の名前は", "ko" to "제 이름은",
            "ar" to "اسمي", "hi" to "मेरा नाम है", "bn" to "আমার নাম"
        ),
        "i need help" to mapOf(
            "es" to "Necesito ayuda", "fr" to "J'ai besoin d'aide", "de" to "Ich brauche Hilfe",
            "it" to "Ho bisogno di aiuto", "pt" to "Preciso de ajuda", "ru" to "Мне нужна помощь",
            "zh" to "我需要帮助", "ja" to "助けてください", "ko" to "도움이 필요해요",
            "ar" to "أحتاج إلى مساعدة", "hi" to "मुझे मदद चाहिए", "bn" to "আমার সাহায্য প্রয়োজন"
        ),
        "nice to meet you" to mapOf(
            "es" to "Mucho gusto", "fr" to "Enchanté", "de" to "Freut mich, Sie kennenzulernen",
            "it" to "Piacere di conoscerti", "pt" to "Prazer em conhecê-lo", "ru" to "Приятно познакомиться",
            "zh" to "很高兴认识你", "ja" to "はじめまして", "ko" to "만나서 반갑습니다",
            "ar" to "تشرفت بمعرفتك", "hi" to "आपसे मिलकर खुशी हुई", "bn" to "আপনার সাথে দেখা করে ভালো লাগলো"
        ),
        "happy birthday" to mapOf(
            "es" to "¡Feliz cumpleaños!", "fr" to "Joyeux anniversaire!", "de" to "Alles Gute zum Geburtstag!",
            "it" to "Buon compleanno!", "pt" to "Feliz aniversário!", "ru" to "С днем рождения!",
            "zh" to "生日快乐！", "ja" to "お誕生日おめでとう！", "ko" to "생일 축하해요!",
            "ar" to "عيد ميلاد سعيد!", "hi" to "जन्मदिन मुबारक!", "bn" to "শুভ জন্মদিন!"
        ),
        "congratulations" to mapOf(
            "es" to "¡Felicidades!", "fr" to "Félicitations!", "de" to "Herzlichen Glückwunsch!",
            "it" to "Congratulazioni!", "pt" to "Parabéns!", "ru" to "Поздравляю!",
            "zh" to "恭喜！", "ja" to "おめでとうございます！", "ko" to "축하합니다!",
            "ar" to "تهانينا!", "hi" to "बधाई हो!", "bn" to "অভিনন্দন!"
        ),
        "i understand" to mapOf(
            "es" to "Entiendo", "fr" to "Je comprends", "de" to "Ich verstehe",
            "it" to "Capisco", "pt" to "Eu entendo", "ru" to "Я понимаю",
            "zh" to "我明白了", "ja" to "分かりました", "ko" to "이해합니다",
            "ar" to "أنا أفهم", "hi" to "मैं समझता हूँ", "bn" to "আমি বুঝতে পেরেছি"
        ),
        "i don't understand" to mapOf(
            "es" to "No entiendo", "fr" to "Je ne comprends pas", "de" to "Ich verstehe nicht",
            "it" to "Non capisco", "pt" to "Não entendo", "ru" to "Я не понимаю",
            "zh" to "我不明白", "ja" to "分かりません", "ko" to "이해가 안 가요",
            "ar" to "لا أفهم", "hi" to "मुझे समझ नहीं आया", "bn" to "আমি বুঝতে পারছি না"
        ),
        "do you speak english" to mapOf(
            "es" to "¿Habla inglés?", "fr" to "Parlez-vous anglais?", "de" to "Sprechen Sie Englisch?",
            "it" to "Parla inglese?", "pt" to "Você fala inglês?", "ru" to "Вы говорите по-английски?",
            "zh" to "你会说英语吗？", "ja" to "英語を話せますか？", "ko" to "영어를 할 수 있나요?",
            "ar" to "هل تتحدث الإنجليزية؟", "hi" to "क्या आप अंग्रेजी बोलते हैं?", "bn" to "আপনি কি ইংরেজি বলতে পারেন?"
        )
    )

    // Common single word vocabulary
    private val wordMap = mapOf(
        "yes" to mapOf("es" to "sí", "fr" to "oui", "de" to "ja", "it" to "sì", "ru" to "да", "zh" to "是", "ja" to "はい", "ar" to "نعم", "hi" to "हाँ"),
        "no" to mapOf("es" to "no", "fr" to "non", "de" to "nein", "it" to "no", "ru" to "нет", "zh" to "不", "ja" to "いいえ", "ar" to "لا", "hi" to "नहीं"),
        "good" to mapOf("es" to "bueno", "fr" to "bon", "de" to "gut", "it" to "buono", "ru" to "хорошо", "zh" to "好", "ja" to "良い", "ar" to "جيد", "hi" to "अच्छा"),
        "bad" to mapOf("es" to "malo", "fr" to "mauvais", "de" to "schlecht", "it" to "cattivo", "ru" to "плохо", "zh" to "坏", "ja" to "悪い", "ar" to "سيء", "hi" to "बुरा"),
        "friend" to mapOf("es" to "amigo", "fr" to "ami", "de" to "Freund", "it" to "amico", "ru" to "друг", "zh" to "朋友", "ja" to "友達", "ar" to "صديق", "hi" to "दोस्त"),
        "water" to mapOf("es" to "agua", "fr" to "eau", "de" to "Wasser", "it" to "acqua", "ru" to "вода", "zh" to "水", "ja" to "水", "ar" to "ماء", "hi" to "पानी"),
        "food" to mapOf("es" to "comida", "fr" to "nourriture", "de" to "Essen", "it" to "cibo", "ru" to "еда", "zh" to "食物", "ja" to "食べ物", "ar" to "طعام", "hi" to "खाना"),
        "love" to mapOf("es" to "amor", "fr" to "amour", "de" to "Liebe", "it" to "amore", "ru" to "любовь", "zh" to "爱", "ja" to "愛", "ar" to "حب", "hi" to "प्यार"),
        "time" to mapOf("es" to "tiempo", "fr" to "temps", "de" to "Zeit", "it" to "tempo", "ru" to "время", "zh" to "时间", "ja" to "時間", "ar" to "وقت", "hi" to "समय"),
        "today" to mapOf("es" to "hoy", "fr" to "aujourd'hui", "de" to "heute", "it" to "oggi", "ru" to "сегодня", "zh" to "今天", "ja" to "今日", "ar" to "اليوم", "hi" to "आज"),
        "tomorrow" to mapOf("es" to "mañana", "fr" to "demain", "de" to "morgen", "it" to "domani", "ru" to "завтра", "zh" to "明天", "ja" to "明日", "ar" to "غدا", "hi" to "कल"),
        "yesterday" to mapOf("es" to "ayer", "fr" to "hier", "de" to "gestern", "it" to "ieri", "ru" to "вчера", "zh" to "昨天", "ja" to "昨日", "ar" to "أمس", "hi" to "कल"),
        "work" to mapOf("es" to "trabajo", "fr" to "travail", "de" to "Arbeit", "it" to "lavoro", "ru" to "работа", "zh" to "工作", "ja" to "仕事", "ar" to "عمل", "hi" to "काम"),
        "money" to mapOf("es" to "dinero", "fr" to "argent", "de" to "Geld", "it" to "denaro", "ru" to "деньги", "zh" to "钱", "ja" to "お金", "ar" to "مال", "hi" to "पैसा"),
        "help" to mapOf("es" to "ayuda", "fr" to "aide", "de" to "Hilfe", "it" to "aiuto", "ru" to "помощь", "zh" to "帮助", "ja" to "助け", "ar" to "مساعدة", "hi" to "मदद"),
        "house" to mapOf("es" to "casa", "fr" to "maison", "de" to "Haus", "it" to "casa", "ru" to "дом", "zh" to "房子", "ja" to "家", "ar" to "بيت", "hi" to "घर"),
        "car" to mapOf("es" to "coche", "fr" to "voiture", "de" to "Auto", "it" to "macchina", "ru" to "машина", "zh" to "汽车", "ja" to "車", "ar" to "سيارة", "hi" to "गाड़ी")
    )

    /**
     * Translates input text using offline rule/dictionary engine immediately.
     */
    fun translateOffline(text: String, sourceLang: String = "en", targetLang: String = "es"): String {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        if (sourceLang.equals(targetLang, ignoreCase = true)) return text

        val cleanLower = clean.lowercase(Locale.ROOT).trimEnd('.', '!', '?', ',', ' ')

        // 1. Direct phrase lookup
        val phraseMatch = phraseMap[cleanLower]
        if (phraseMatch != null) {
            val res = phraseMatch[targetLang]
            if (res != null) return res
        }

        // Reverse lookup if source is not English
        if (!sourceLang.equals("en", ignoreCase = true)) {
            for ((engPhrase, translations) in phraseMap) {
                val matchSource = translations[sourceLang]
                if (matchSource != null && matchSource.equals(cleanLower, ignoreCase = true)) {
                    if (targetLang.equals("en", ignoreCase = true)) {
                        return engPhrase.replaceFirstChar { it.uppercase() }
                    }
                    val targetTranslation = translations[targetLang]
                    if (targetTranslation != null) return targetTranslation
                }
            }
        }

        // 2. Multi-word composite translation
        val words = clean.split("\\s+".toRegex())
        if (words.size > 1) {
            val translatedWords = words.map { word ->
                val punct = word.filter { !it.isLetterOrDigit() }
                val raw = word.filter { it.isLetterOrDigit() }.lowercase()
                val match = wordMap[raw]?.get(targetLang) ?: phraseMap[raw]?.get(targetLang) ?: raw
                if (word.firstOrNull()?.isUpperCase() == true) {
                    match.replaceFirstChar { it.uppercase() } + punct
                } else {
                    match + punct
                }
            }
            return translatedWords.joinToString(" ")
        }

        // 3. Single word lookup
        val singleWord = wordMap[cleanLower]?.get(targetLang)
        if (singleWord != null) {
            return if (clean.first().isUpperCase()) singleWord.replaceFirstChar { it.uppercase() } else singleWord
        }

        // Fallback: return original text
        return text
    }

    /**
     * Translates input text using LibreTranslate API (https://libretranslate.com/translate).
     * Automatically attempts LibreTranslate mirrors and MyMemory fallback on network timeout/rate-limit,
     * and gracefully falls back to the local on-device dictionary engine when offline.
     */
    suspend fun translateOnline(
        text: String,
        sourceLang: String = "en",
        targetLang: String = "es",
        endpoint: String = "https://libretranslate.com/translate",
        apiKey: String = ""
    ): String = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext ""
        if (sourceLang.equals(targetLang, ignoreCase = true)) return@withContext text

        val normSource = normalizeLangCode(sourceLang)
        val normTarget = normalizeLangCode(targetLang)

        // 1. Primary LibreTranslate Endpoint (default: https://libretranslate.com/translate)
        val primaryResult = callLibreTranslateApi(clean, normSource, normTarget, endpoint, apiKey)
        if (!primaryResult.isNullOrBlank()) {
            return@withContext primaryResult
        }

        // 2. LibreTranslate Public Mirrors Fallback
        val mirrors = listOf(
            "https://translate.terraprint.co/translate",
            "https://libretranslate.de/translate",
            "https://translate.argosopentech.com/translate"
        )
        for (mirror in mirrors) {
            if (mirror.equals(endpoint.trim(), ignoreCase = true)) continue
            val mirrorResult = callLibreTranslateApi(clean, normSource, normTarget, mirror, "")
            if (!mirrorResult.isNullOrBlank()) {
                return@withContext mirrorResult
            }
        }

        // 3. Fallback Open API (MyMemory)
        val myMemoryResult = callMyMemoryApi(clean, normSource, normTarget)
        if (!myMemoryResult.isNullOrBlank()) {
            return@withContext myMemoryResult
        }

        // 4. Offline Dictionary Fallback
        translateOffline(text, sourceLang, targetLang)
    }

    private fun callLibreTranslateApi(
        text: String,
        sourceLang: String,
        targetLang: String,
        endpoint: String,
        apiKey: String
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val targetUrl = endpoint.trim().ifBlank { "https://libretranslate.com/translate" }
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3500
                readTimeout = 3500
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Cboard-Keyboard/1.0 (Android)")
            }

            val payload = JSONObject().apply {
                put("q", text)
                put("source", sourceLang)
                put("target", targetLang)
                put("format", "text")
                if (apiKey.isNotBlank()) {
                    put("api_key", apiKey.trim())
                }
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(responseText)
                val translated = root.optString("translatedText", "")
                if (translated.isNotBlank()) {
                    return decodeHtmlEntities(translated)
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun callMyMemoryApi(text: String, sourceLang: String, targetLang: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val langPair = "${sourceLang}|${targetLang}"
            val endpoint = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "Cboard-Keyboard/1.0 (Android)")
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonString)
                val responseData = root.optJSONObject("responseData")
                val translated = responseData?.optString("translatedText")
                if (!translated.isNullOrBlank() && !translated.startsWith("MYMEMORY WARNING")) {
                    return decodeHtmlEntities(translated)
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizeLangCode(code: String): String {
        return when (code.lowercase(Locale.ROOT)) {
            "fil" -> "tl"
            else -> code.lowercase(Locale.ROOT)
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    /**
     * Unified translate function: Uses offline dictionary synchronously, or online if available.
     */
    fun translate(text: String, sourceLang: String = "en", targetLang: String = "es"): String {
        return translateOffline(text, sourceLang, targetLang)
    }

    data class PhrasebookCategory(
        val name: String,
        val iconResName: String,
        val phrases: List<String>
    )

    val phrasebookCategories = listOf(
        PhrasebookCategory("Greetings & Basics", "ic_sparkle", listOf(
            "Hello", "How are you", "Good morning", "Good night", "Thank you very much",
            "Please", "You are welcome", "Yes", "No", "Nice to meet you", "Goodbye"
        )),
        PhrasebookCategory("Travel & Places", "ic_globe", listOf(
            "Where is the bathroom", "How much is this", "Do you speak english",
            "I need help", "Where is the hotel", "Take me to the airport", "Excuse me"
        )),
        PhrasebookCategory("Polite & Social", "ic_emoji_smile", listOf(
            "Sorry", "I understand", "I don't understand", "Happy birthday",
            "Congratulations", "See you soon", "See you tomorrow", "I love you"
        )),
        PhrasebookCategory("Emergency & Help", "ic_shield", listOf(
            "I need help", "Please call a doctor", "Where is the hospital",
            "I lost my passport", "Call the police", "It is an emergency"
        ))
    )
}
