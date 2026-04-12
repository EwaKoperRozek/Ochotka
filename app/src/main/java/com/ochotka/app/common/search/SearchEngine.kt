package com.ochotka.app.common.search

import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant

/**
 * Kotlin port of semantic_search.py – hybrid keyword + concept-expansion search.
 * Bez embeddingów (model ML nie jest potrzebny dla projektu studenckiego):
 *   1. Rozpoznanie konceptu kulinarnego → rozwinięcie na konkretne dania
 *   2. Rozbicie zapytania złożonego (i / oraz / a)
 *   3. Keyword match po search_blob + name + description + ingredients
 *   4. Ranking po liczbie trafień / pozycji w tekście
 */
object SearchEngine {

    // -------------------------------------------------------------------------
    // Regex – rozbicie złożonych zamówień na pod-zapytania
    // -------------------------------------------------------------------------
    private val QUERY_SPLIT = Regex(
        """(?i)\s+(?:i|oraz|do\s+tego|a\s+do\s+tego|a\s+także|plus|&|,)\s+|\s+a\s+(?=\p{L})"""
    )

    // -------------------------------------------------------------------------
    // Regex – usunięcie prefixów intencji naturalnego języka
    // -------------------------------------------------------------------------
    private val INTENT_PREFIXES = Regex(
        """(?i)^(?:mam\s+ochot[ęe]\s+na\s+|mam\s+ochot[ęe]\s+na\s+|chc[eę]\s+|chcia[łl]a?bym\s+|poproszę\s+(?:o\s+)?|daj(?:cie)?\s+mi\s+|co[śs]\s+(?:z\s+)?|jak[aą][śs]\s+|jak[iI][śs]\s+|jakie[śs]\s+|może\s+być\s+|(?:\w+\s+)?chce?\s+|na\s+)"""
    )

    // -------------------------------------------------------------------------
    // Słownik konceptów kulinarnych (port z semantic_search.py)
    // -------------------------------------------------------------------------
    private val CONCEPT_EXPANSIONS: Map<String, List<String>> = mapOf(
        "tradycyjny"          to listOf("schabowy", "kotlet mielony", "pierogi", "barszcz", "bigos"),
        "kuchnia polska"      to listOf("schabowy", "pierogi", "barszcz", "żurek", "bigos", "gołąbki", "rosół"),
        "polski obiad"        to listOf("schabowy", "kotlet mielony", "pierogi", "barszcz", "żurek"),
        "domowe jedzenie"     to listOf("pierogi", "bigos", "gołąbki", "kopytka", "rosół", "żurek"),
        "domowy obiad"        to listOf("schabowy", "kotlet mielony", "pierogi", "rosół", "bigos"),
        "comfort food"        to listOf("schabowy", "pierogi", "kopytka", "rosół", "pizza"),
        "coś słodkiego"       to listOf("sernik", "szarlotka", "ciasto", "lody", "naleśniki", "beza", "tiramisu"),
        "słodkie"             to listOf("sernik", "szarlotka", "ciasto", "lody", "tiramisu", "beza"),
        "deser"               to listOf("sernik", "szarlotka", "ciasto", "lody", "tiramisu", "beza", "mochi"),
        "coś lekkiego"        to listOf("sałatka", "zupa", "ryba", "kurczak", "wrap", "miso"),
        "lekki obiad"         to listOf("sałatka", "zupa", "ryba", "kurczak", "grillowany"),
        "dietetyczne"         to listOf("sałatka", "kurczak", "ryba", "zupa", "wrap"),
        "fit"                 to listOf("sałatka", "kurczak grillowany", "ryba", "edamame"),
        "wegetariańskie"      to listOf("pierogi ruskie", "sałatka", "pizza margherita", "makaron", "falafel", "edamame"),
        "wegańskie"           to listOf("falafel", "sałatka", "edamame", "tofu", "pad thai"),
        "coś sytego"          to listOf("schabowy", "pizza", "burger", "kebab", "pierogi"),
        "mięsne"              to listOf("schabowy", "kotlet", "żeberka", "burger", "kebab", "kurczak"),
        "zupa"                to listOf("rosół", "barszcz", "żurek", "ramen", "miso", "wonton"),
        "na kaca"             to listOf("rosół", "żurek", "zapiekanka", "pizza", "ramen"),
        "dla dzieci"          to listOf("pizza", "makaron", "naleśniki", "nuggets"),
        "na imprezę"          to listOf("pizza", "skrzydełka", "burger", "pierogi", "spring rolls"),
        "włoskie"             to listOf("pizza", "pasta", "risotto", "tiramisu", "lasagne", "carbonara"),
        "azjatyckie"          to listOf("sushi", "ramen", "pad thai", "spring rolls", "curry", "gyoza"),
        "japońskie"           to listOf("sushi", "ramen", "miso", "edamame", "gyoza", "nigiri"),
        "z kurczakiem"        to listOf("kurczak", "bbq kurczak", "wonton", "kung pao"),
        "z łososiem"          to listOf("łosoś", "sake nigiri", "salmon roll"),
        "z wołowiną"          to listOf("burger", "wołowina", "chow mein"),
        "z serem"             to listOf("pizza", "quattro formaggi", "makaron"),
        "z grzybami"          to listOf("pizza z pieczarkami", "risotto", "makaron"),
        "pikantne"            to listOf("diavola", "spicy tuna", "kung pao", "chili", "pikantny"),
        "szybkie"             to listOf("pizza", "sushi", "burger", "wrap", "edamame")
    )

    // -------------------------------------------------------------------------
    // Uproszczone końcówki fleksyjne języka polskiego
    // -------------------------------------------------------------------------
    private val PL_SUFFIXES = listOf(
        "ością" to "ość", "ości" to "ość",
        "kami" to "", "ami" to "", "iem" to "",
        "ową" to "owy", "owego" to "owy", "owe" to "owy",
        "iego" to "i", "iej" to "i",
        "iem" to "", "ią" to "ia",
        "ych" to "y", "ich" to "i",
        "iem" to "", "ach" to "",
        "owi" to "", "om" to "",
        "ego" to "", "emu" to "",
        "niem" to "nie", "nie" to "nie",
        "ę" to "a", "ą" to "a",
        "ów" to "", "iem" to ""
    )

    // Ręczne korekty dla najczęstszych słów kulinarnych
    private val PL_CORRECTIONS = mapOf(
        "kawa" to "kawa", "kawę" to "kawa", "kawy" to "kawa", "kawki" to "kawa",
        "pizza" to "pizza", "pizzę" to "pizza", "pizzy" to "pizza",
        "burger" to "burger", "burgery" to "burger", "burgera" to "burger",
        "sushi" to "sushi",
        "ramen" to "ramen", "ramenu" to "ramen",
        "pierogi" to "pierogi", "pierogów" to "pierogi",
        "kurczak" to "kurczak", "kurczaka" to "kurczak", "kurczakiem" to "kurczak",
        "łosoś" to "łosoś", "łososia" to "łosoś", "łososiem" to "łosoś",
        "tiramisu" to "tiramisu",
        "makaron" to "makaron", "makaronu" to "makaron", "makaronem" to "makaron",
        "zupa" to "zupa", "zupy" to "zupa", "zupę" to "zupa",
        "sałatka" to "sałatka", "sałatki" to "sałatka", "sałatkę" to "sałatka",
        "ciasto" to "ciasto", "ciasta" to "ciasto", "ciastem" to "ciasto",
        "lody" to "lody", "lodów" to "lody", "lodami" to "lody",
        "gyoza" to "gyoza", "edamame" to "edamame"
    )

    // -------------------------------------------------------------------------
    // Główny interfejs
    // -------------------------------------------------------------------------

    /**
     * Wyszukuje dania pasujące do zapytania.
     * Zwraca posortowaną listę SearchResultItem (malejąco po score).
     */
    fun search(
        query: String,
        dishes: List<Dish>,
        restaurantMap: Map<String, Restaurant>
    ): List<SearchResultItem> {
        if (query.isBlank()) return emptyList()

        val trimmedQuery = query.trim().lowercase()

        // 1. Koncept kulinarny?
        val conceptTerms = expandConcept(trimmedQuery)
        if (conceptTerms != null) {
            return searchMulti(conceptTerms, dishes, restaurantMap)
        }

        // 2. Rozbij na pod-zapytania
        val rawParts = QUERY_SPLIT.split(trimmedQuery).map { it.trim() }.filter { it.isNotEmpty() }
        val subQueries = rawParts.flatMap { part ->
            val term = extractFoodTerm(part)
            term.split(Regex("""\s+z\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
        }

        return if (subQueries.size > 1) {
            searchMulti(subQueries, dishes, restaurantMap)
        } else {
            searchSingle(subQueries.firstOrNull() ?: trimmedQuery, dishes, restaurantMap)
        }
    }

    // -------------------------------------------------------------------------
    // Prywatne metody
    // -------------------------------------------------------------------------

    private fun expandConcept(query: String): List<String>? {
        val q = query.lowercase().trim()
        // Najdłuższe koncepty mają priorytet
        return CONCEPT_EXPANSIONS.entries
            .sortedByDescending { it.key.split(" ").size }
            .firstOrNull { (concept, _) ->
                concept.split(" ").all { word -> q.contains(word) }
            }?.value
    }

    private fun extractFoodTerm(phrase: String): String {
        val cleaned = INTENT_PREFIXES.replace(phrase.trim(), "").trim()
        return if (cleaned.isNotEmpty()) normalizePl(cleaned) else normalizePl(phrase.trim())
    }

    private fun normalizePl(text: String): String {
        return text.lowercase().split(" ").joinToString(" ") { word ->
            PL_CORRECTIONS[word] ?: run {
                var result = word
                for ((suffix, replacement) in PL_SUFFIXES) {
                    if (word.endsWith(suffix) && word.length - suffix.length >= 3) {
                        result = word.dropLast(suffix.length) + replacement
                        break
                    }
                }
                result
            }
        }
    }

    private fun normalizeQueryWords(query: String): List<String> {
        val normalized = normalizePl(query)
        return normalized.split(" ").filter { it.length > 1 }
    }

    private fun scoreWord(queryWord: String, blobWords: Set<String>): Boolean {
        if (queryWord in blobWords) return true
        // Fallback – brak ogonka: "beze" → "beza"
        if (queryWord.length >= 4) {
            val candidate = queryWord.dropLast(1) + "a"
            if (candidate in blobWords) return true
        }
        return false
    }

    private fun keywordScore(queryWords: List<String>, dish: Dish): Float {
        if (queryWords.isEmpty()) return 0f
        val blob = dish.searchBlob.lowercase()
        val blobWords = blob.split(" ").toSet()

        val exactNameMatch = dish.name.lowercase().contains(queryWords.joinToString(" "))
        if (exactNameMatch) return 1.0f

        val matchedWords = queryWords.count { qw -> scoreWord(qw, blobWords) }
        if (matchedWords == 0) {
            // Fallback: sprawdź czy choćby jedno słowo jest w opisie lub składnikach
            val combined = "${dish.name} ${dish.description} ${dish.ingredients.joinToString(" ")}".lowercase()
            val partialMatches = queryWords.count { qw -> combined.contains(qw) }
            return if (partialMatches > 0) 0.2f * partialMatches / queryWords.size else 0f
        }

        val fraction = matchedWords.toFloat() / queryWords.size
        // Bonus: czy nazwa dania zawiera pierwsze słowo zapytania?
        val nameBonus = if (dish.name.lowercase().contains(queryWords.first())) 0.2f else 0f
        return (0.6f * fraction + nameBonus).coerceAtMost(1.0f)
    }

    private fun searchSingle(
        query: String,
        dishes: List<Dish>,
        restaurantMap: Map<String, Restaurant>,
        minScore: Float = 0.15f
    ): List<SearchResultItem> {
        val queryWords = normalizeQueryWords(query)
        return dishes
            .mapNotNull { dish ->
                val restaurant = restaurantMap[dish.restaurantId] ?: return@mapNotNull null
                val score = keywordScore(queryWords, dish)
                if (score >= minScore) SearchResultItem(dish, restaurant, score) else null
            }
            .sortedByDescending { it.score }
    }

    private fun searchMulti(
        subQueries: List<String>,
        dishes: List<Dish>,
        restaurantMap: Map<String, Restaurant>
    ): List<SearchResultItem> {
        // Dla każdego pod-zapytania znajdź najlepsze danie per restauracja
        val coverageMap = mutableMapOf<String, MutableMap<String, SearchResultItem>>()

        for (sq in subQueries) {
            val qWords = normalizeQueryWords(sq)
            for (dish in dishes) {
                val restaurant = restaurantMap[dish.restaurantId] ?: continue
                val score = keywordScore(qWords, dish)
                if (score >= 0.15f) {
                    val restId = dish.restaurantId
                    val existing = coverageMap.getOrPut(restId) { mutableMapOf() }
                    val current = existing[sq]
                    if (current == null || score > current.score) {
                        existing[sq] = SearchResultItem(dish, restaurant, score)
                    }
                }
            }
        }

        // Pełne pokrycie → priorytet, potem częściowe
        val full = coverageMap.filter { it.value.size == subQueries.size }
        val partial = coverageMap.filter { it.value.size in 1 until subQueries.size }

        fun avgScore(m: Map<String, SearchResultItem>) = m.values.sumOf { it.score.toDouble() }.toFloat() / m.size

        val results = mutableListOf<SearchResultItem>()

        // Z pełnego pokrycia bierzemy danie o najwyższym score
        full.entries
            .sortedByDescending { avgScore(it.value) }
            .forEach { (_, matches) ->
                val best = matches.values.maxByOrNull { it.score }!!
                results.add(best)
            }

        // Z częściowego – dorzucamy pozostałe
        partial.entries
            .sortedByDescending { it.value.size }
            .forEach { (_, matches) ->
                val best = matches.values.maxByOrNull { it.score }!!
                if (results.none { it.dish.restaurantId == best.dish.restaurantId }) {
                    results.add(best)
                }
            }

        return results
    }
}
