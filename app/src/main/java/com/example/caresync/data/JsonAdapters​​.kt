package com.example.caresync.data

import com.example.caresync.domain.EscalationPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// Convert objects to JSON string - handles specific types only
fun toJson(obj: Any): String {
    return when (obj) {
        is List<*> -> {
            if (obj.isEmpty()) {
                "[]"
            } else {
                when (obj.first()) {
                    is Int -> json.encodeToString(ListSerializer(Int.serializer()), obj.filterIsInstance<Int>())
                    is String -> json.encodeToString(ListSerializer(String.serializer()), obj.filterIsInstance<String>())
                    else -> "[]"
                }
            }
        }
        is Set<*> -> toJson(obj.toList())
        is EscalationPolicy -> json.encodeToString(
            EscalationPolicySurrogate(obj.afterSnoozes, obj.escalateToVoice)
        )
        is String -> "\"$obj\""
        is Int -> obj.toString()
        is Long -> obj.toString()
        is Float -> obj.toString()
        is Boolean -> obj.toString()
        else -> "null"
    }
}

fun parseIntList(src: String): List<Int> {
    return try {
        json.decodeFromString<List<Int>>(src)
    } catch (e: Exception) {
        emptyList()
    }
}

fun parseIntSet(src: String): Set<Int> {
    return parseIntList(src).toSet()
}

fun parseStringList(src: String): List<String> {
    return try {
        json.decodeFromString<List<String>>(src)
    } catch (e: Exception) {
        emptyList()
    }
}

fun <E> parseEnumSet(src: String, from: (String) -> E): Set<E> {
    return parseStringList(src).mapNotNull {
        try {
            from(it)
        } catch (e: Exception) {
            null
        }
    }.toSet()
}

fun parseEscalation(src: String?): EscalationPolicy? {
    if (src.isNullOrBlank()) return null
    return try {
        val surrogate = json.decodeFromString<EscalationPolicySurrogate>(src)
        EscalationPolicy(surrogate.afterSnoozes, surrogate.escalateToVoice)
    } catch (e: Exception) {
        null
    }
}

@Serializable
private data class EscalationPolicySurrogate(
    val afterSnoozes: Int,
    val escalateToVoice: Boolean
)
