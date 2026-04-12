package com.ochotka.app.common.utils

import android.content.Context
import android.location.Location
import android.view.View
import android.widget.Toast
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

// ---- View ----

fun View.visible() { visibility = View.VISIBLE }
fun View.gone()    { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

// ---- Dish ----

fun Dish.formatPrice(): String = when {
    priceMin == priceMax -> "%.0f zł".format(priceMin)
    else                 -> "%.0f–%.0f zł".format(priceMin, priceMax)
}

// ---- Restaurant ----

fun Restaurant.distanceMeters(userLat: Double, userLng: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(userLat, userLng, lat, lng, results)
    return results[0]
}

fun Restaurant.formatDistance(userLat: Double, userLng: Double): String {
    val meters = distanceMeters(userLat, userLng)
    return if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000f)
}

/**
 * Sprawdza czy restauracja jest aktualnie otwarta na podstawie opening_hours.
 * Format godzin: "HH:mm-HH:mm" (np. "14:00-23:59").
 */
fun Restaurant.isOpenNow(): Boolean {
    val now = java.time.LocalDateTime.now()
    val polishDay = when (now.dayOfWeek) {
        DayOfWeek.MONDAY    -> "Poniedziałek"
        DayOfWeek.TUESDAY   -> "Wtorek"
        DayOfWeek.WEDNESDAY -> "Środa"
        DayOfWeek.THURSDAY  -> "Czwartek"
        DayOfWeek.FRIDAY    -> "Piątek"
        DayOfWeek.SATURDAY  -> "Sobota"
        DayOfWeek.SUNDAY    -> "Niedziela"
        else                -> return false
    }
    val ranges = openingHours[polishDay] ?: return false
    val currentTime = now.toLocalTime()
    return ranges.any { range ->
        val parts = range.split("-")
        if (parts.size != 2) return@any false
        runCatching {
            val from = LocalTime.parse(parts[0])
            val to   = LocalTime.parse(parts[1])
            if (to.isAfter(from)) {
                currentTime >= from && currentTime <= to
            } else {
                // Zakres przez północ
                currentTime >= from || currentTime <= to
            }
        }.getOrDefault(false)
    }
}

fun Restaurant.openStatus(): String = if (isOpenNow()) "OTWARTE" else "ZAMKNIĘTE"
