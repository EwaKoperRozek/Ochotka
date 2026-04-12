package com.ochotka.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.model.Variant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RestaurantRepository private constructor(context: Context) {

    private val firebaseDb = FirebaseDatabase.getInstance(
        "https://ochotka-b2695-default-rtdb.europe-west1.firebasedatabase.app/"
    )

    private var restaurants: List<Restaurant> = emptyList()
    private var restaurantMap: Map<String, Restaurant> = emptyMap()
    private var searchIndex: List<SearchIndexEntry> = emptyList()

    private val dishesByRestaurantCache = mutableMapOf<String, List<Dish>>()
    private var loaded = false

    private val loadMutex = Mutex()

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

    private suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (loaded) return

            Log.d("RestaurantRepository", "ensureLoaded() start")

            withTimeout(15000) {
                val restaurantsSnapshot = awaitSnapshot("restaurants")
                Log.d(
                    "RestaurantRepository",
                    "Restaurants snapshot children=${restaurantsSnapshot.childrenCount}"
                )

                val searchIndexSnapshot = awaitSnapshot("search_index")
                Log.d(
                    "RestaurantRepository",
                    "Search index snapshot children=${searchIndexSnapshot.childrenCount}"
                )

                restaurants = restaurantsSnapshot.children.mapNotNull { it.toRestaurant() }
                restaurantMap = restaurants.associateBy { it.id }
                searchIndex = searchIndexSnapshot.children.mapNotNull { it.toSearchIndexEntry() }
            }

            loaded = true

            Log.d(
                "RestaurantRepository",
                "Loaded restaurants=${restaurants.size}, searchIndex=${searchIndex.size}"
            )
        }
    }

    private suspend fun awaitSnapshot(path: String): DataSnapshot =
        suspendCancellableCoroutine { cont ->
            val ref = firebaseDb.reference.child(path)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                }
            }

            ref.addListenerForSingleValueEvent(listener)
            cont.invokeOnCancellation { ref.removeEventListener(listener) }
        }

    suspend fun getAllRestaurants(): List<Restaurant> {
        ensureLoaded()
        return restaurants
    }

    suspend fun getRestaurantById(id: String): Restaurant? {
        ensureLoaded()
        return restaurantMap[id]
    }

    suspend fun getRestaurantMap(): Map<String, Restaurant> {
        ensureLoaded()
        return restaurantMap
    }

    suspend fun getAllDishes(): List<Dish> {
        ensureLoaded()
        return searchIndex.map { it.toPreviewDish() }
    }

    suspend fun getFeaturedSearchResults(limit: Int = 6): List<SearchResultItem> {
        ensureLoaded()

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
        ensureLoaded()

        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        return searchIndex
            .asSequence()
            .filter { entry ->
                val haystack = buildString {
                    append(normalize(entry.dishName))
                    append(" ")
                    append(normalize(entry.restaurantName))
                    append(" ")
                    append(normalize(entry.category))
                    append(" ")
                    append(normalize(entry.searchBlob))
                    append(" ")
                    append(entry.tags.joinToString(" ") { normalize(it) })
                }
                haystack.contains(normalizedQuery)
            }
            .mapNotNull { entry ->
                val restaurant = restaurantMap[entry.restaurantId] ?: return@mapNotNull null
                SearchResultItem(
                    dish = entry.toPreviewDish(),
                    restaurant = restaurant,
                    score = 1.0f
                )
            }
            .take(100)
            .toList()
    }

    suspend fun getDishesByRestaurant(restaurantId: String): List<Dish> {
        ensureLoaded()

        dishesByRestaurantCache[restaurantId]?.let { return it }

        val snapshot = awaitSnapshot("restaurant_dishes/$restaurantId")
        val dishes = snapshot.children.mapNotNull { it.toDish() }

        dishesByRestaurantCache[restaurantId] = dishes
        return dishes
    }

    suspend fun getDishById(dishId: String): Dish? {
        ensureLoaded()

        val preview = searchIndex.find { it.dishId == dishId } ?: return null
        val restaurantId = preview.restaurantId

        val fullDishes = getDishesByRestaurant(restaurantId)
        return fullDishes.find { it.id == dishId } ?: preview.toPreviewDish()
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
            variants = emptyList(),
            searchBlob = searchBlob
        )
    }

    private fun DataSnapshot.toRestaurant(): Restaurant? {
        val map = value as? Map<*, *> ?: return null

        val id = map["id"]?.toString() ?: key ?: return null
        val name = map["name"]?.toString().orEmpty()
        val url = map["url"]?.toString().orEmpty()
        val address = map["address"]?.toString().orEmpty()
        val postcode = map["postcode"]?.toString().orEmpty()
        val city = map["city"]?.toString().orEmpty()
        val lat = map["lat"].toDoubleSafe()
        val lng = map["lng"].toDoubleSafe()
        val description = map["description"]?.toString().orEmpty()
        val allergenPhone = map["allergen_phone"]?.toString()
            ?: map["phone"]?.toString()
            ?: ""
        val openingHours = parseOpeningHours(map["opening_hours"])

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

    private fun DataSnapshot.toSearchIndexEntry(): SearchIndexEntry? {
        val map = value as? Map<*, *> ?: return null

        val dishId = map["dish_id"]?.toString() ?: key ?: return null
        val restaurantId = map["restaurant_id"]?.toString().orEmpty()
        val dishName = map["dish_name"]?.toString().orEmpty()
        val restaurantName = map["restaurant_name"]?.toString().orEmpty()
        val category = map["category"]?.toString().orEmpty()
        val priceMin = map["price_min"].toDoubleSafe()
        val priceMax = map["price_max"].toDoubleSafe()
        val searchBlob = map["search_blob"]?.toString().orEmpty()
        val tags = parseStringList(map["tags"])

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

    private fun DataSnapshot.toDish(): Dish? {
        val map = value as? Map<*, *> ?: return null

        val id = map["id"]?.toString() ?: key ?: return null
        val restaurantId = map["restaurant_id"]?.toString().orEmpty()
        val category = map["category"]?.toString().orEmpty()
        val name = map["name"]?.toString().orEmpty()
        val description = map["description"]?.toString().orEmpty()
        val ingredients = parseStringList(map["ingredients"])
        val priceMin = map["price_min"].toDoubleSafe()
        val priceMax = map["price_max"].toDoubleSafe()
        val variants = parseVariants(map["variants"])
        val searchBlob = map["search_blob"]?.toString().orEmpty()

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
        )
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
                    val size = map["size"]?.toString().orEmpty()
                    val price = map["price"].toDoubleSafe()
                    result.add(Variant(size = size, price = price))
                }
            }
            is Map<*, *> -> {
                raw.values.forEach { item ->
                    val map = item as? Map<*, *> ?: return@forEach
                    val size = map["size"]?.toString().orEmpty()
                    val price = map["price"].toDoubleSafe()
                    result.add(Variant(size = size, price = price))
                }
            }
        }

        return result
    }

    private fun parseStringList(raw: Any?): List<String> {
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            is Map<*, *> -> raw.values.mapNotNull { it?.toString() }
            else -> emptyList()
        }
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