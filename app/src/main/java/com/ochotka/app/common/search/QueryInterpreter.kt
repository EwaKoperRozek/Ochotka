package com.ochotka.app.common.search

enum class QueryMatchMode {
    ANY,
    ALL,
}

data class InterpretedQuery(
    val originalQuery: String,
    val terms: List<String>,
    val matchMode: QueryMatchMode,
)

object QueryInterpreter {

    private val querySplitRegex = Regex(
        pattern = """\s+(?:i|oraz|do tego|a do tego|a także|plus|&|,)\s+|\s+a\s+(?=\w)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val intentPrefixes = listOf(
        "mam ochotę na ",
        "mam ochote na ",
        "chcę ",
        "chce ",
        "chciałbym ",
        "chcialbym ",
        "chciałabym ",
        "chcialabym ",
        "poproszę ",
        "poprosze ",
        "poproszę o ",
        "poprosze o ",
        "daj mi ",
        "dajcie mi ",
        "coś z ",
        "cos z ",
        "jakaś ",
        "jakas ",
        "jakiś ",
        "jakis ",
        "jakieś ",
        "jakies ",
        "może być ",
        "moze byc ",
        "na ",
    )

    private val polishSuffixes = listOf(
        Triple("ościom", "ość", 3),
        Triple("ościach", "ość", 3),
        Triple("ością", "ość", 3),
        Triple("owiemu", "owy", 3),
        Triple("owemu", "owy", 3),
        Triple("owego", "owy", 3),
        Triple("owej", "owy", 3),
        Triple("owym", "owy", 3),
        Triple("ową", "owa", 3),
        Triple("owych", "owy", 3),
        Triple("ami", "a", 5),
        Triple("ach", "a", 4),
        Triple("owi", "", 4),
        Triple("iem", "", 4),
        Triple("em", "", 3),
        Triple("om", "a", 4),
        Triple("ą", "a", 3),
        Triple("ę", "a", 3),
        Triple("aty", "ata", 3),
        Triple("ety", "eta", 3),
        Triple("ity", "ita", 3),
    )

    private val polishCorrections = mapOf(
        "czosnk" to "czosnek",
        "jajk" to "jajko",
        "koperk" to "koperek",
        "masełk" to "masełko",
        "ogórk" to "ogórek",
        "serk" to "serek",
        "szczypiork" to "szczypiorek",
        "łosos" to "łosoś",
        "mlek" to "mleko",
        "kalmara" to "kalmar",
        "orzecha" to "orzech",
        "sezamu" to "sezam",
        "soi" to "soja",
        "chilli" to "chili",
    )

    private val conceptExpansions = linkedMapOf(
        "kuchnia polska" to listOf("schabowy", "pierogi", "barszcz", "żurek", "bigos", "gołąbki", "rosół"),
        "kuchnia włoska" to listOf("pizza", "pasta", "risotto", "tiramisu", "lasagne", "gnocchi"),
        "kuchnia wloska" to listOf("pizza", "pasta", "risotto", "tiramisu", "lasagne", "gnocchi"),
        "kuchnia chińska" to listOf("makaron chiński", "ryż smażony", "kurczak po chińsku", "wołowina po chińsku", "spring rolls", "kaczka"),
        "kuchnia chinska" to listOf("makaron chiński", "ryż smażony", "kurczak po chińsku", "wołowina po chińsku", "spring rolls", "kaczka"),
        "kuchnia wietnamska" to listOf("pho", "bun bo", "bun cha", "sajgonki", "spring rolls", "pad thai"),
        "kuchnia vietnamska" to listOf("pho", "bun bo", "bun cha", "sajgonki", "spring rolls", "pad thai"),
        "polski obiad" to listOf("schabowy", "kotlet mielony", "pierogi", "barszcz", "żurek"),
        "domowy obiad" to listOf("schabowy", "kotlet mielony", "pierogi", "rosół", "bigos"),
        "coś słodkiego" to listOf("sernik", "szarlotka", "ciasto", "lody", "naleśniki", "beza"),
        "coś lekkiego" to listOf("sałatka", "zupa", "ryba", "kurczak", "wrap"),
        "lekki obiad" to listOf("sałatka", "zupa", "ryba", "kurczak", "grillowany"),
        "coś sytego" to listOf("schabowy", "pizza", "burger", "kebab", "pierogi"),
        "domowe jedzenie" to listOf("pierogi", "bigos", "gołąbki", "kopytka", "rosół", "żurek"),
        "śniadanie" to listOf("jajecznica", "owsianka", "naleśniki", "omlet", "kanapka", "granola"),
        "brunch" to listOf("jajka", "naleśniki", "omlet", "tost", "owsianka"),
        "włoskie" to listOf("pizza", "pasta", "risotto", "tiramisu", "lasagne"),
        "wloskie" to listOf("pizza", "pasta", "risotto", "tiramisu", "lasagne"),
        "azjatyckie" to listOf("sushi", "ramen", "pad thai", "spring rolls", "curry"),
        "japońskie" to listOf("sushi", "ramen", "miso", "edamame", "gyoza"),
        "japonskie" to listOf("sushi", "ramen", "miso", "edamame", "gyoza"),
        "chińskie" to listOf("makaron chiński", "ryż smażony", "kurczak po chińsku", "wołowina po chińsku", "spring rolls", "kaczka"),
        "chinskie" to listOf("makaron chiński", "ryż smażony", "kurczak po chińsku", "wołowina po chińsku", "spring rolls", "kaczka"),
        "wietnamskie" to listOf("pho", "bun bo", "bun cha", "sajgonki", "spring rolls", "pad thai"),
        "meksykańskie" to listOf("tacos", "burrito", "nachos", "quesadilla", "guacamole"),
        "meksykanskie" to listOf("tacos", "burrito", "nachos", "quesadilla", "guacamole"),
        "zupa" to listOf("rosół", "barszcz", "żurek", "zupa pomidorowa", "grochówka", "zupa dnia"),
        "tradycyjny" to listOf("schabowy", "kotlet mielony", "pierogi", "barszcz", "bigos"),
        "słodkie" to listOf("sernik", "szarlotka", "ciasto", "lody", "tiramisu", "beza"),
        "deser" to listOf("sernik", "szarlotka", "ciasto", "lody", "tiramisu", "beza"),
        "wegetariańskie" to listOf("pierogi ruskie", "sałatka", "pizza", "makaron", "falafel"),
        "wegańskie" to listOf("falafel", "sałatka", "hummus", "tofu", "bowl"),
        "na kaca" to listOf("rosół", "żurek", "zapiekanka", "pizza", "kebab"),
        "dla dzieci" to listOf("nuggets", "pizza", "makaron", "naleśniki", "frytki"),
        "z kurczakiem" to listOf("kurczak", "pierś z kurczaka", "skrzydełka"),
        "z łososiem" to listOf("łosoś", "sałatka z łososiem", "tatar z łososia"),
        "z wołowiną" to listOf("burger", "stek", "wołowina", "tatar"),
        "z serem" to listOf("pizza", "quesadilla", "makaron", "zapiekanka"),
        "z grzybami" to listOf("risotto", "makaron", "zupa grzybowa", "pierogi"),
    )

    fun interpret(query: String): InterpretedQuery {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return InterpretedQuery(query, emptyList(), QueryMatchMode.ANY)
        }

        val lowered = trimmed.lowercase()
        expandConcept(lowered)?.let { terms ->
            return InterpretedQuery(
                originalQuery = query,
                terms = terms.map(::normalizeQueryPhrase).distinct(),
                matchMode = QueryMatchMode.ANY
            )
        }

        val rawParts = querySplitRegex
            .split(trimmed)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val extractedTerms = rawParts
            .flatMap { part ->
                extractFoodTerm(part)
                    .split(Regex("""\s+z\s+"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            .distinct()

        return InterpretedQuery(
            originalQuery = query,
            terms = extractedTerms.ifEmpty { listOf(normalizeQueryPhrase(trimmed)) },
            matchMode = if (extractedTerms.size > 1) QueryMatchMode.ALL else QueryMatchMode.ANY
        )
    }

    fun normalizeQueryWords(query: String): List<String> {
        return normalizeIngredientLikePhrase(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 1 }
    }

    private fun extractFoodTerm(phrase: String): String {
        val cleaned = stripIntentPrefix(phrase.trim())
        return normalizeQueryPhrase(cleaned.ifBlank { phrase.trim() })
    }

    private fun normalizeQueryPhrase(phrase: String): String {
        val words = phrase.lowercase().trim().split(Regex("""\s+"""))
        if (words.isEmpty()) return phrase.trim()

        return words.joinToString(" ") { word ->
            var normalized = word
            for ((suffix, replacement, minStem) in polishSuffixes) {
                if (word.endsWith(suffix)) {
                    val stem = word.dropLast(suffix.length) + replacement
                    if (stem.length >= minStem) {
                        normalized = stem
                        break
                    }
                }
            }
            polishCorrections[normalized] ?: normalized
        }.trim()
    }

    private fun normalizeIngredientLikePhrase(phrase: String): String {
        val stopwords = setOf("z", "ze", "w", "we", "na", "do")
        val words = phrase.lowercase().trim().split(Regex("""\s+""")).toMutableList()
        if (words.isNotEmpty() && words.first() in stopwords) {
            words.removeAt(0)
        }
        return normalizeQueryPhrase(words.joinToString(" "))
    }

    private fun expandConcept(query: String): List<String>? {
        return conceptExpansions.entries
            .sortedByDescending { it.key.split(" ").size }
            .firstOrNull { (concept, _) ->
                concept.split(" ").all { token -> query.contains(token) }
            }
            ?.value
    }

    private fun stripIntentPrefix(text: String): String {
        val lowered = text.lowercase()

        intentPrefixes.firstOrNull { prefix -> lowered.startsWith(prefix) }?.let { prefix ->
            return text.drop(prefix.length).trim()
        }

        val wantsMatch = Regex("""^\w+\s+chce\s+""", RegexOption.IGNORE_CASE).find(text)
        if (wantsMatch != null) {
            return text.removeRange(wantsMatch.range).trim()
        }

        return text
    }
}
