package com.modernlghub.remote.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Lenient JSON accessors for hub responses.
 *
 * Why not plain optString/optInt: Android's bundled org.json returns the *text* "null" from
 * optString() for a JSON null, while the reference org.json used by the JVM unit tests returns
 * the fallback. These helpers treat null the same on both, and accept numbers sent as strings
 * (and vice versa), so a hub that changes a field's type degrades instead of breaking.
 */

/** String value, or [fallback] for missing / JSON null / objects and arrays. */
internal fun JSONObject.str(key: String, fallback: String = ""): String =
    when (val v = opt(key)) {
        null, JSONObject.NULL, is JSONObject, is JSONArray -> fallback
        is String -> v
        else -> v.toString()
    }

/** Int from a number or numeric string; null if missing, JSON null or not a finite number. */
internal fun JSONObject.intOrNull(key: String): Int? =
    when (val v = opt(key)) {
        is Int -> v
        is Number -> v.toDouble().takeIf { it.isFinite() }?.toInt()
        is String -> v.trim().toIntOrNull() ?: v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
        else -> null
    }

internal fun JSONObject.doubleOrNull(key: String): Double? =
    when (val v = opt(key)) {
        is Number -> v.toDouble().takeIf { it.isFinite() }
        is String -> v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

/** Boolean from true/false, "true"/"false", 1/0; [fallback] otherwise. */
internal fun JSONObject.bool(key: String, fallback: Boolean = false): Boolean =
    when (val v = opt(key)) {
        is Boolean -> v
        is String -> when (v.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> fallback
        }
        is Number -> v.toInt() != 0
        else -> fallback
    }

internal fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

/** Parses an object body; null for anything that is not a JSON object (HTML error page, array, …). */
internal fun parseObjectOrNull(body: String): JSONObject? =
    runCatching { JSONObject(body) }.getOrNull()
