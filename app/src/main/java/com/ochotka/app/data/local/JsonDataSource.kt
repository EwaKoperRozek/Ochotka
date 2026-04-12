package com.ochotka.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.ochotka.app.data.model.OchotkaData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsonDataSource(private val context: Context) {

    private val gson = Gson()

    suspend fun loadData(): OchotkaData = withContext(Dispatchers.IO) {
        val json = context.assets.open("data.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        gson.fromJson(json, OchotkaData::class.java)
    }
}
