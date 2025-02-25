package com.gscube.smsbulker.data.local.converters

import androidx.room.TypeConverter
import org.json.JSONObject

class ContactConverters {
    @TypeConverter
    fun fromVariablesMap(value: Map<String, String>?): String {
        if (value == null) return "{}"
        val jsonObject = JSONObject()
        value.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    @TypeConverter
    fun toVariablesMap(value: String?): Map<String, String> {
        if (value == null) return emptyMap()
        return try {
            val jsonObject = JSONObject(value)
            val map = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
