package com.drowningpool.androidclient.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Десериализатор для Boolean, который принимает:
 * - число (0 -> false, 1 -> true)
 * - boolean (true/false)
 * - null
 */
class BooleanNumberDeserializer : JsonDeserializer<Boolean?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Boolean? {
        if (json == null || json.isJsonNull) {
            return null
        }
        
        return when {
            json.isJsonPrimitive -> {
                val primitive = json.asJsonPrimitive
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> primitive.asInt != 0  // 0 -> false, любое другое число -> true
                    primitive.isString -> {
                        // На случай, если придет строка "true"/"false" или "0"/"1"
                        val str = primitive.asString.lowercase()
                        when (str) {
                            "true", "1" -> true
                            "false", "0" -> false
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
}

