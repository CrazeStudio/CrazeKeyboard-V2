package com.crazestudio.crazekeyboard

import android.content.Context

object Alts {
    val map = mapOf(
        "a" to "àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīįì", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
        "c" to "çćč", "n" to "ñń", "s" to "ßśš", "y" to "ý", "z" to "žźż", "l" to "ł", "d" to "ð",
        "." to ",?!;:-…", "," to "'\"", "-" to "–—_"
    )
}

object Words {
    private val fallbackWords = (
        "the be to of and a in that have it for not on with he as you do at this but his by from they we " +
        "say her she or an will my one all would there their what so up out if about who get which go me when make can like time no " +
        "just him know take people into year your good some could them see other than then now look only come its over think also " +
        "back after use two how our work first well way even new want because any these give day most us hello hi hey thanks thank " +
        "please sorry yes okay ok great nice love really very much more many where why while today tomorrow tonight morning " +
        "evening night friend friends family home house school phone call message messages text send sent reply meet meeting lunch " +
        "dinner breakfast coffee happy birthday wish wishes best welcome sure maybe later soon never always sometimes still again " +
        "should would could might must need needs help helps find found keep left right going gone done doing being been were was " +
        "are is am did does had has having made makes making said says tell told ask asked want wanted feel feeling felt little big " +
        "long short high low old young next last every both each few those such own same another between before during without " +
        "through under further once here everything something anything nothing someone anyone everyone though although however " +
        "already almost enough early late quick quickly slow slowly easy hard important different possible " +
        "keyboard android phone game music video photo picture camera app apps code file files link online internet website"
    ).split(Regex("\\s+")).filter { it.isNotBlank() }

    @Volatile private var cachedDictionary: List<String>? = null
    @Volatile private var cachedSet: Set<String>? = null

    private fun dictionary(context: Context): List<String> {
        cachedDictionary?.let { return it }
        return synchronized(this) {
            cachedDictionary ?: run {
                val bundled = try {
                    context.assets.open("main_en-US.dict").bufferedReader().useLines { lines ->
                        lines.map { it.trim().lowercase() }
                            .filter { it.length in 1..40 && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '-' } }
                            .distinct()
                            .toList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                (bundled + fallbackWords).distinct().also {
                    cachedDictionary = it
                    cachedSet = it.toHashSet()
                }
            }
        }
    }

    fun known(w: String): Boolean {
        val word = w.trim().lowercase()
        return fallbackWords.contains(word) || cachedSet?.contains(word) == true || Prefs.isLearned(word)
    }

    private fun cased(candidate: String, typed: String) = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> candidate.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
        else -> candidate
    }

    fun suggest(context: Context, word: String): List<String> {
        if (word.isEmpty()) return listOf("I", "The", "Thanks")
        val lower = word.lowercase()
        val words = dictionary(context)
        val learned = Prefs.learned(context).asSequence().map { it.lowercase() }
        val candidates = (learned + words.asSequence())
            .filter { it.startsWith(lower) && it != lower }
            .distinct()
            .take(3)
            .map { cased(it, word) }
            .toList()
        return if (candidates.isEmpty()) listOf(word) else candidates
    }
}
