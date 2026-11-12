package com.crazestudio.crazekeyboard

import android.content.Context

object Alts {
    val map = mapOf(
        "a" to "àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīįì", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
        "c" to "çćč", "n" to "ñń", "s" to "ßśš", "y" to "ý", "z" to "žźż", "l" to "ł", "d" to "ð",
        "." to ",?!;:-…", "," to "'\"", "-" to "–—_"
        "*" to "•×·", "\"" to "“”„", "'" to "‘’", ":" to "؛∶",
        ";" to "؛", "!" to "¡", "?" to "¿", "@" to "©®",
        "#" to "№", "$" to "₹€£¥", "%" to "‰", "&" to "§",
        "+" to "±", "=" to "≠≈", "/" to "\\", "(" to "[{",
        ")" to "]}", "~" to "≈≋", "`" to "´", "|" to "¦",
        "•" to "·", "√" to "∛∜", "π" to "τφ", "÷" to "⁄",
        "×" to "✕", "^" to "↑", "∆" to "∇", "£" to "₹₽",
        "¢" to "¤", "€" to "₿", "¥" to "元", "°" to "℃℉",
        "{" to "<", "}" to ">", "©" to "®™", "®" to "©™",
        "™" to "℠", "[" to "(", "]" to ")", "<" to "≤≪",
        ">" to "≥≫", "_" to "—–", "–" to "—_", "—" to "–_"
    )
}

object Words {
    // Roughly ordered by frequency, so earlier words are suggested first.
    private val dict: List<String> = ("the be to of and a in that have it for not on with he as you do at this but his by from they we " +
        "say her she or an will my one all would there their what so up out if about who get which go me when make can like time no " +
        "just him know take people into year your good some could them see other than then now look only come its over think also " +
        "back after use two how our work first well way even new want because any these give day most us hello hi hey thanks thank " +
        "please sorry yes okay ok great nice love really very much more many where why while today tomorrow tonight morning " +
        "evening night friend friends family home house school phone call message messages text send sent reply meet meeting lunch " +
        "dinner breakfast coffee happy birthday wish wishes best welcome sure maybe later soon never always sometimes still again " +
        "should might must need needs help helps find found keep left right going gone done doing being been were was " +
        "are is am did does had has having made makes making said says tell told ask asked wanted feel feeling felt little big " +
        "long short high low old young next last every both each few those such own same another between before during without " +
        "through under further once here everything something anything nothing someone anyone everyone though " +
        "although however already almost enough early late quick quickly slow slowly easy hard important different possible " +
        "keyboard android game music video photo picture camera app apps code file files link online internet website " +
        "going coming doing thinking looking working wanting getting making taking having being saying seeing knowing " +
        "tell talk speak watch play read write learn study start stop open close give take bring buy pay cost price free " +
        "money time week month year weekend monday tuesday wednesday thursday friday saturday sunday january february march " +
        "april june july august september october november december yesterday sorry excuse understand remember forget believe " +
        "believe maybe probably actually definitely absolutely exactly totally literally basically honestly seriously").split(" ").filter { it.isNotEmpty() }.distinct()

    private val dictSet = dict.toHashSet()
    private val byFirst: Map<Char, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dict.groupBy { it.first() }
    }
    private val byPrefix: Map<String, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dict.groupBy { it.take(2) }
    }
    private val starters = listOf("I", "The", "Thanks")

    fun known(w: String) = dictSet.contains(w.lowercase()) || VocabularyIndex.contains(w)

    fun learn(c: Context, w: String) {
        val lw = w.lowercase()
        if (lw in dictSet) return
        Prefs.learn(c, lw)
    }

    private fun cased(s: String, w: String) = when {
        w.length > 1 && w.all { it.isUpperCase() } -> s.uppercase()
        w.isNotEmpty() && w[0].isUpperCase() -> s.replaceFirstChar { it.uppercase() }
        else -> s
    }

    // True when a and b differ by exactly one insertion, deletion, substitution or adjacent swap.
    private fun near(a: String, b: String): Boolean {
        if (a == b) return false
        val la = a.length
        val lb = b.length
        if (Math.abs(la - lb) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < la && j < lb) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (la == lb && i + 1 < la && a[i] == b[j + 1] && a[i + 1] == b[j]) { i += 2; j += 2; continue }
            when {
                la > lb -> i++
                la < lb -> j++
                else -> { i++; j++ }
            }
        }
        edits += (la - i) + (lb - j)
        return edits <= 1
    }

    // Conservative auto-correction: only an unknown, plain-letter word with exactly one close dictionary match that
    // shares its first letter. Three-letter words are only fixed when they are a simple swap ("teh" -> "the").
    fun correction(c: Context, w: String): String? {
        if (w.length < 3 || w.length > 20 || w.any { !it.isLetter() }) return null
        if (w.drop(1).any { it.isUpperCase() }) return null
        val lower = w.lowercase()
        if (lower in dictSet || Prefs.isLearned(c, lower)) return null
        var found: String? = null
        val candidates = LinkedHashSet<String>()
        candidates.addAll(byFirst[lower[0]].orEmpty())
        candidates.addAll(Prefs.learned(c))
        candidates.addAll(VocabularyIndex.byFirstLetter(lower[0]))
        for (x in candidates) {
            if (!near(lower, x)) continue
            if (found != null) return null
            found = x
        }
        val f = found ?: return null
        if (lower.length == 3 && lower.toCharArray().sorted() != f.toCharArray().sorted()) return null
        return cased(f, w)
    }

    fun suggest(c: Context, w: String): List<String> {
        if (w.isEmpty()) return starters
        val lower = w.lowercase()
        val out = ArrayList<String>(3)
        for (x in Prefs.learned(c)) {
            if (out.size >= 3) break
            if (x.startsWith(lower) && x != lower) { val cx = cased(x, w); if (cx !in out) out.add(cx) }
        }
        val lexiconCandidates = VocabularyIndex.suggest(lower, 3)
        for (x in lexiconCandidates) {
            if (out.size >= 3) break
            val cx = cased(x, w)
            if (cx !in out) out.add(cx)
        }
        val prefixCandidates = if (lower.length >= 2) byPrefix[lower.take(2)].orEmpty()
            else byFirst[lower.first()].orEmpty()
        for (x in prefixCandidates) {
            if (out.size >= 3) break
            if (x.startsWith(lower) && x != lower) { val cx = cased(x, w); if (cx !in out) out.add(cx) }
        }
        // Typo fallback is uncommon; retain the full lexicon here so first-letter mistakes still match.
        if (out.size < 3 && lower.length >= 2) {
            for (x in dict) {
                if (out.size >= 3) break
                if (near(lower, x)) { val cx = cased(x, w); if (cx !in out) out.add(cx) }
            }
        }
        return if (out.isEmpty()) listOf(w) else out
    }
}
