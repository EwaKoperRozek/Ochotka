package com.ochotka.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ochotka.app.common.search.InterpretedQuery
import com.ochotka.app.common.search.QueryInterpreter
import com.ochotka.app.common.search.QueryMatchMode
import kotlinx.coroutines.tasks.await
import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.model.Variant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class RestaurantRepository private constructor(context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    private var restaurants: List<Restaurant> = emptyList()
    private var restaurantMap: Map<String, Restaurant> = emptyMap()
    private var searchIndex: List<SearchIndexEntry> = emptyList()

    private val dishesByRestaurantCache = mutableMapOf<String, List<Dish>>()
    private var restaurantsLoaded = false
    private var searchIndexLoaded = false

    private val restaurantsMutex = Mutex()
    private val searchIndexMutex = Mutex()

    private data class SearchIndexEntry(
        val dishId: String,
        val restaurantId: String,
        val dishName: String,
        val restaurantName: String,
        val category: String,
        val priceMin: Double,
        val priceMax: Double,
        val searchBlob: String,
        val tags: List<String>
    )

    private suspend fun ensureRestaurantsLoaded() {
        restaurantsMutex.withLock {
            if (restaurantsLoaded) return

            Log.d("RestaurantRepository", "Loading restaurants")

            withTimeout(15000) {
                val restaurantsSnapshot = firestore.collection("restaurants").get().await()
                restaurants = restaurantsSnapshot.documents.mapNotNull { it.toRestaurant() }
                restaurantMap = restaurants.associateBy { it.id }
            }

            restaurantsLoaded = true
            Log.d("RestaurantRepository", "Loaded restaurants=${restaurants.size}")
        }
    }

    private suspend fun ensureSearchIndexLoaded() {
        searchIndexMutex.withLock {
            if (searchIndexLoaded) return

            Log.d("RestaurantRepository", "Loading search index")

            withTimeout(20000) {
                val searchIndexSnapshot = firestore.collection("search_index").get().await()
                searchIndex = searchIndexSnapshot.documents.mapNotNull { it.toSearchIndexEntry() }
            }

            searchIndexLoaded = true
            Log.d("RestaurantRepository", "Loaded searchIndex=${searchIndex.size}")
        }
    }

    suspend fun getAllRestaurants(): List<Restaurant> {
        ensureRestaurantsLoaded()
        return restaurants
    }

    suspend fun getRestaurantById(id: String): Restaurant? {
        ensureRestaurantsLoaded()
        return restaurantMap[id]
    }

    suspend fun getRestaurantMap(): Map<String, Restaurant> {
        ensureRestaurantsLoaded()
        return restaurantMap
    }

    suspend fun getAllDishes(): List<Dish> {
        ensureSearchIndexLoaded()
        return searchIndex.map { it.toPreviewDish() }
    }

    suspend fun getFeaturedSearchResults(limit: Int = 6): List<SearchResultItem> {
        ensureRestaurantsLoaded()
        ensureSearchIndexLoaded()

        return searchIndex
            .shuffled()
            .mapNotNull { entry ->
                val restaurant = restaurantMap[entry.restaurantId] ?: return@mapNotNull null
                SearchResultItem(
                    dish = entry.toPreviewDish(),
                    restaurant = restaurant,
                    score = 1.0f
                )
            }
            .take(limit)
    }

    suspend fun searchDishes(query: String): List<SearchResultItem> {
        ensureRestaurantsLoaded()
        ensureSearchIndexLoaded()

        val interpreted = QueryInterpreter.interpret(query)
        if (interpreted.terms.isEmpty()) return emptyList()

        return when (interpreted.matchMode) {
            QueryMatchMode.ANY -> searchAny(interpreted)
            QueryMatchMode.ALL -> searchAll(interpreted)
        }
    }

    suspend fun getDishesByRestaurant(restaurantId: String): List<Dish> {
        dishesByRestaurantCache[restaurantId]?.let { return it }

        val subcollectionSnapshot = firestore
            .collection("restaurants")
            .document(restaurantId)
            .collection("dishes")
            .get()
            .await()
        var dishes = subcollectionSnapshot.documents.mapNotNull { it.toDish() }

        if (dishes.isEmpty()) {
            val fallbackSnapshot = firestore
                .collection("restaurant_dishes")
                .whereEqualTo("restaurant_id", restaurantId)
                .get()
                .await()
            dishes = fallbackSnapshot.documents.mapNotNull { it.toDish() }
        }

        if (dishes.isEmpty()) {
            ensureSearchIndexLoaded()
            dishes = searchIndex
                .asSequence()
                .filter { it.restaurantId == restaurantId }
                .map { it.toPreviewDish() }
                .distinctBy { it.id }
                .sortedBy { it.name }
                .toList()
        }

        dishesByRestaurantCache[restaurantId] = dishes
        return dishes
    }

    suspend fun getDishById(dishId: String): Dish? {
        ensureSearchIndexLoaded()

        val preview = searchIndex.find { it.dishId == dishId } ?: return null
        val restaurantId = preview.restaurantId

        val fullDishes = getDishesByRestaurant(restaurantId)
        fullDishes.find { it.id == dishId }?.let { return it.withFallbackVariants() }

        val collectionGroupHit = firestore.collectionGroup("dishes")
            .whereEqualTo("id", dishId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toDish()

        if (collectionGroupHit != null) {
            return collectionGroupHit.withFallbackVariants()
        }

        val topLevelHit = firestore.collection("restaurant_dishes")
            .whereEqualTo("id", dishId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toDish()

        return topLevelHit?.withFallbackVariants() ?: preview.toPreviewDish()
    }

    private fun SearchIndexEntry.toPreviewDish(): Dish {
        return Dish(
            id = dishId,
            restaurantId = restaurantId,
            category = category,
            name = dishName,
            description = if (restaurantName.isNotBlank()) {
                "Restauracja: $restaurantName"
            } else {
                "Sprawdź szczegóły dania"
            },
            ingredients = tags,
            priceMin = priceMin,
            priceMax = priceMax,
            variants = buildFallbackVariants(priceMin, priceMax),
            searchBlob = searchBlob
        )
    }

    private fun DocumentSnapshot.toRestaurant(): Restaurant? {
        val id = getString("id") ?: this.id.takeIf { it.isNotBlank() } ?: return null
        val name = getString("name").orEmpty()
        val url = getString("url").orEmpty()
        val address = getString("address").orEmpty()
        val postcode = getString("postcode").orEmpty()
        val city = getString("city").orEmpty()
        val lat = getNumberValue("lat")
        val lng = getNumberValue("lng")
        val description = getString("description").orEmpty()
        val allergenPhone = getString("allergen_phone")
            ?: getString("phone")
            ?: ""
        val openingHours = parseOpeningHours(get("opening_hours"))

        return Restaurant(
            id = id,
            name = name,
            url = url,
            address = address,
            postcode = postcode,
            city = city,
            lat = lat,
            lng = lng,
            description = description,
            allergenPhone = allergenPhone,
            openingHours = openingHours
        )
    }

    private fun searchAny(interpreted: InterpretedQuery): List<SearchResultItem> {
        return interpreted.terms
            .asSequence()
            .flatMap { term -> matchingEntriesForTerm(term).asSequence() }
            .distinctBy { it.dishId }
            .mapNotNull(::toSearchResultItem)
            .take(100)
            .toList()
    }

    private fun searchAll(interpreted: InterpretedQuery): List<SearchResultItem> {
        val matchesByTerm = interpreted.terms.associateWith { term ->
            matchingEntriesForTerm(term)
        }

        val restaurantIds = matchesByTerm.values
            .map { entries -> entries.map { it.restaurantId }.toSet() }
            .reduceOrNull { acc, ids -> acc intersect ids }
            .orEmpty()

        if (restaurantIds.isEmpty()) return emptyList()

        return interpreted.terms
            .asSequence()
            .flatMap { term ->
                matchesByTerm.getValue(term)
                    .asSequence()
                    .filter { it.restaurantId in restaurantIds }
            }
            .distinctBy { it.dishId }
            .mapNotNull(::toSearchResultItem)
            .take(100)
            .toList()
    }

    private fun matchingEntriesForTerm(term: String): List<SearchIndexEntry> {
        val queryWords = QueryInterpreter.normalizeQueryWords(term)
        if (queryWords.isEmpty()) return emptyList()

        return searchIndex.filter { entry ->
            val blobWords = entryBlobWords(entry)
            queryWords.all { wordMatches(it, blobWords) }
        }
    }

    private fun entryBlobWords(entry: SearchIndexEntry): Set<String> {
        val bucket = buildString {
            append(normalize(entry.dishName))
            append(" ")
            append(normalize(entry.category))
            append(" ")
            append(normalize(entry.searchBlob))
            append(" ")
            append(entry.tags.joinToString(" ") { normalize(it) })
        }
        return bucket.split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun wordMatches(queryWord: String, blobWords: Set<String>): Boolean {
        if (queryWord in blobWords) return true

        if (queryWord.length >= 4) {
            val candidate = queryWord.dropLast(1) + "a"
            if (candidate in blobWords) return true
        }

        return false
    }

    private fun toSearchResultItem(entry: SearchIndexEntry): SearchResultItem? {
        val restaurant = restaurantMap[entry.restaurantId] ?: return null
        return SearchResultItem(
            dish = entry.toPreviewDish(),
            restaurant = restaurant,
            score = 1.0f
        )
    }

    private fun DocumentSnapshot.toSearchIndexEntry(): SearchIndexEntry? {
        val dishId = getString("dish_id") ?: this.id.takeIf { it.isNotBlank() } ?: return null
        val restaurantId = getString("restaurant_id").orEmpty()
        val dishName = getString("dish_name").orEmpty()
        val restaurantName = getString("restaurant_name").orEmpty()
        val category = getString("category").orEmpty()
        val priceMin = getNumberValue("price_min")
        val priceMax = getNumberValue("price_max")
        val searchBlob = getString("search_blob").orEmpty()
        val tags = parseStringList(get("tags"))

        return SearchIndexEntry(
            dishId = dishId,
            restaurantId = restaurantId,
            dishName = dishName,
            restaurantName = restaurantName,
            category = category,
            priceMin = priceMin,
            priceMax = priceMax,
            searchBlob = searchBlob,
            tags = tags
        )
    }

    private fun DocumentSnapshot.toDish(): Dish? {
        val id = getString("id") ?: this.id.takeIf { it.isNotBlank() } ?: return null
        val restaurantId = getString("restaurant_id").orEmpty()
        val category = getString("category").orEmpty()
        val name = getString("name").orEmpty()
        val description = getString("description").orEmpty()
        val ingredients = parseStringList(get("ingredients"))
        val priceMin = getNumberValue("price_min")
        val priceMax = getNumberValue("price_max")
        val variants = parseVariants(get("variants"))
        val searchBlob = getString("search_blob").orEmpty()

        return Dish(
            id = id,
            restaurantId = restaurantId,
            category = category,
            name = name,
            description = description,
            ingredients = ingredients,
            priceMin = priceMin,
            priceMax = priceMax,
            variants = variants,
            searchBlob = searchBlob
        ).withFallbackVariants()
    }

    private fun parseOpeningHours(raw: Any?): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        val map = raw as? Map<*, *> ?: return emptyMap()

        map.forEach { (key, value) ->
            val day = key?.toString() ?: return@forEach
            result[day] = parseStringList(value)
        }

        return result
    }

    private fun parseVariants(raw: Any?): List<Variant> {
        val result = mutableListOf<Variant>()

        when (raw) {
            is List<*> -> {
                raw.forEach { item ->
                    val map = item as? Map<*, *> ?: return@forEach
                    val size = map["size"]?.toString()
                        ?: map["label"]?.toString()
                        ?: map["name"]?.toString()
                        ?: ""
                    val price = (map["price"] ?: map["amount"] ?: map["value"]).toDoubleSafe()
                    result.add(Variant(size = size, price = price))
                }
            }
            is Map<*, *> -> {
                raw.values.forEach { item ->
                    val map = item as? Map<*, *> ?: return@forEach
                    val size = map["size"]?.toString()
                        ?: map["label"]?.toString()
                        ?: map["name"]?.toString()
                        ?: ""
                    val price = (map["price"] ?: map["amount"] ?: map["value"]).toDoubleSafe()
                    result.add(Variant(size = size, price = price))
                }
            }
        }

        return result.filter { it.price > 0.0 }
    }

    private fun Dish.withFallbackVariants(): Dish {
        if (variants.isNotEmpty()) return this
        return copy(variants = buildFallbackVariants(priceMin, priceMax))
    }

    private fun buildFallbackVariants(priceMin: Double, priceMax: Double): List<Variant> {
        if (priceMin <= 0.0 && priceMax <= 0.0) return emptyList()
        if (priceMax <= 0.0 || priceMin == priceMax) {
            return listOf(Variant(size = "Cena", price = maxOf(priceMin, priceMax)))
        }
        return listOf(
            Variant(size = "Od", price = priceMin),
            Variant(size = "Do", price = priceMax)
        )
    }

    private fun parseStringList(raw: Any?): List<String> {
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            is Map<*, *> -> raw.values.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }

    private fun DocumentSnapshot.getNumberValue(field: String): Double {
        return (get(field) as? Number)?.toDouble()
            ?: getString(field)?.toDoubleOrNull()
            ?: 0.0
    }

    private fun Any?.toDoubleSafe(): Double {
        return when (this) {
            is Number -> this.toDouble()
            is String -> this.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace("ą", "a")
            .replace("ć", "c")
            .replace("ę", "e")
            .replace("ł", "l")
            .replace("ń", "n")
            .replace("ó", "o")
            .replace("ś", "s")
            .replace("ź", "z")
            .replace("ż", "z")
            .trim()
    }

    companion object {
        @Volatile
        private var instance: RestaurantRepository? = null

        fun getInstance(context: Context): RestaurantRepository =
            instance ?: synchronized(this) {
                instance ?: RestaurantRepository(context.applicationContext).also { instance = it }
            }
    }
}
